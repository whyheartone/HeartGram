package app.heartgram.wsproxy;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Локальный MTProto-прокси: клиент Telegram подключается к 127.0.0.1, а мы переносим его трафик к дата-центрам
 * через WebSocket (kws*.web.telegram.org). Это обходит блокировки по IP и по протоколу.
 *
 * Схема и порядок действий взяты из tg-ws-proxy (Flowseal, MIT): https://github.com/Flowseal/tg-ws-proxy.
 * Клиентский поток разбирается по секрету прокси (obfuscated2), номер дата-центра читается из заголовка,
 * дальше трафик перешифровывается новым ключом без секрета и уходит в WebSocket; пакеты MTProto идут по одному на кадр.
 * Слушаем только 127.0.0.1, наружу ничего не открыто, ходим только к адресам Telegram.
 */
public final class WsProxy {

    private static final String TAG = "HeartGramWsProxy";

    private static final int HANDSHAKE_LEN = 64, SKIP_LEN = 8, PREKEY_LEN = 32, IV_LEN = 16, PROTO_TAG_POS = 56, DC_IDX_POS = 60;
    private static final int PROTO_ABRIDGED = 0xEFEFEFEF, PROTO_INTERMEDIATE = 0xEEEEEEEE, PROTO_PADDED = 0xDDDDDDDD;

    static final Map<Integer, String> DC_DEFAULT_IPS = new HashMap<>();
    static final String FRONTING_SNI = "sprinthost.ru";

    static {
        DC_DEFAULT_IPS.put(1, "149.154.175.50");
        DC_DEFAULT_IPS.put(2, "149.154.167.51");
        DC_DEFAULT_IPS.put(3, "149.154.175.100");
        DC_DEFAULT_IPS.put(4, "149.154.167.91");
        DC_DEFAULT_IPS.put(5, "149.154.171.5");
        DC_DEFAULT_IPS.put(203, "91.105.192.100");
    }

    /** Адрес шлюза WebSocket для дата-центров 2 и 4 (так умеет tg-ws-proxy). */
    private static final String GATEWAY_IP = "149.154.167.220";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ExecutorService POOL = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "hg-wsproxy");
        t.setDaemon(true);
        return t;
    });

    public static final AtomicLong connectionsTotal = new AtomicLong(), connectionsWs = new AtomicLong(),
            connectionsTcp = new AtomicLong(), connectionsFailed = new AtomicLong(),
            bytesUp = new AtomicLong(), bytesDown = new AtomicLong();

    private static ServerSocket server;
    private static volatile boolean running;
    private static volatile boolean tryFrontingFirst;

    private WsProxy() {
    }

    public static synchronized boolean isRunning() {
        return running;
    }

    /** Запускает прокси на 127.0.0.1; если порт занят, берёт свободный. Возвращает фактический порт или -1. */
    public static synchronized int start(int port) {
        if (running && server != null) {
            return server.getLocalPort();
        }
        try {
            ServerSocket s;
            try {
                s = new ServerSocket(port, 64, InetAddress.getByName("127.0.0.1"));
            } catch (IOException busy) {
                Log.w(TAG, "порт " + port + " занят: " + busy + ", беру свободный");
                s = new ServerSocket(0, 64, InetAddress.getByName("127.0.0.1"));
            }
            server = s;
            running = true;
            final ServerSocket accept = s;
            Thread t = new Thread(() -> acceptLoop(accept), "hg-wsproxy-accept");
            t.setDaemon(true);
            t.start();
            Log.i(TAG, "запущен на 127.0.0.1:" + s.getLocalPort());
            return s.getLocalPort();
        } catch (IOException e) {
            Log.e(TAG, "не удалось запустить: " + e);
            running = false;
            return -1;
        }
    }

    public static synchronized void stop() {
        running = false;
        if (server != null) {
            try {
                server.close();
            } catch (IOException ignored) {
            }
            server = null;
        }
        Log.i(TAG, "остановлен");
    }

    private static void acceptLoop(ServerSocket s) {
        while (running && !s.isClosed()) {
            try {
                final Socket client = s.accept();
                POOL.execute(() -> handle(client));
            } catch (IOException e) {
                if (running && !s.isClosed()) {
                    Log.w(TAG, "accept: " + e);
                }
            }
        }
    }

    // ── шифры ────────────────────────────────────────────────────────────────

    private static Cipher ctr(byte[] key, byte[] iv) throws GeneralSecurityException {
        Cipher c = Cipher.getInstance("AES/CTR/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
        return c;
    }

    private static byte[] sha256(byte[] a, byte[] b) throws GeneralSecurityException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(a);
        md.update(b);
        return md.digest();
    }

    private static byte[] reversed(byte[] a) {
        byte[] r = new byte[a.length];
        for (int i = 0; i < a.length; i++) {
            r[i] = a[a.length - 1 - i];
        }
        return r;
    }

    private static final class Crypto {
        Cipher cltDec, cltEnc, tgEnc, tgDec;
    }

    /** Заголовок для дата-центра: случайные 64 байта, хвост (тип протокола, номер ДЦ) шифруется тем же ключом. */
    private static byte[] relayInit(byte[] protoTag, int dcIdx) throws GeneralSecurityException {
        byte[] rnd;
        while (true) {
            rnd = new byte[HANDSHAKE_LEN];
            RANDOM.nextBytes(rnd);
            int first = rnd[0] & 0xFF;
            if (first == 0xEF) {
                continue;
            }
            int start = ((rnd[0] & 0xFF) << 24) | ((rnd[1] & 0xFF) << 16) | ((rnd[2] & 0xFF) << 8) | (rnd[3] & 0xFF);
            if (start == 0x48454144 /* HEAD */ || start == 0x504F5354 /* POST */ || start == 0x47455420 /* GET  */
                    || start == 0xEEEEEEEE || start == 0xDDDDDDDD || start == 0x16030102 || start == 0x4F505449 /* OPTI */) {
                continue;
            }
            if (rnd[4] == 0 && rnd[5] == 0 && rnd[6] == 0 && rnd[7] == 0) {
                continue;
            }
            break;
        }
        byte[] encKey = Arrays.copyOfRange(rnd, SKIP_LEN, SKIP_LEN + PREKEY_LEN);
        byte[] encIv = Arrays.copyOfRange(rnd, SKIP_LEN + PREKEY_LEN, SKIP_LEN + PREKEY_LEN + IV_LEN);
        Cipher enc = ctr(encKey, encIv);
        byte[] full = enc.update(rnd);

        byte[] tail = new byte[8];
        System.arraycopy(protoTag, 0, tail, 0, 4);
        tail[4] = (byte) dcIdx;
        tail[5] = (byte) (dcIdx >> 8);
        byte[] extra = new byte[2];
        RANDOM.nextBytes(extra);
        tail[6] = extra[0];
        tail[7] = extra[1];

        byte[] result = rnd.clone();
        for (int i = 0; i < 8; i++) {
            byte keystream = (byte) (full[PROTO_TAG_POS + i] ^ rnd[PROTO_TAG_POS + i]);
            result[PROTO_TAG_POS + i] = (byte) (tail[i] ^ keystream);
        }
        return result;
    }

    // ── разбор пакетов MTProto для отправки по одному на кадр ─────────────────

    private static final class Buf {
        byte[] data = new byte[8192];
        int len;

        void append(byte[] src) {
            if (len + src.length > data.length) {
                data = Arrays.copyOf(data, Math.max(data.length * 2, len + src.length));
            }
            System.arraycopy(src, 0, data, len, src.length);
            len += src.length;
        }

        void consume(int n) {
            System.arraycopy(data, n, data, 0, len - n);
            len -= n;
        }
    }

    private static final class Splitter {
        private final int proto;
        private final Buf cipher = new Buf(), plain = new Buf();
        private boolean disabled;

        Splitter(int proto) {
            this.proto = proto;
        }

        List<byte[]> split(byte[] chunk, byte[] plainChunk) {
            List<byte[]> parts = new ArrayList<>();
            if (chunk.length == 0) {
                return parts;
            }
            if (disabled) {
                parts.add(chunk);
                return parts;
            }
            cipher.append(chunk);
            plain.append(plainChunk);
            int offset = 0;
            while (offset < cipher.len) {
                int packet = nextLen(offset, cipher.len - offset);
                if (packet == -1) {
                    break; // пакет пришёл не весь
                }
                if (packet <= 0) { // непонятный формат: дальше отдаём как есть
                    parts.add(Arrays.copyOfRange(cipher.data, offset, cipher.len));
                    offset = cipher.len;
                    disabled = true;
                    break;
                }
                parts.add(Arrays.copyOfRange(cipher.data, offset, offset + packet));
                offset += packet;
            }
            if (offset > 0) {
                cipher.consume(offset);
                plain.consume(offset);
            }
            return parts;
        }

        byte[] flush() {
            if (cipher.len == 0) {
                return null;
            }
            byte[] tail = Arrays.copyOf(cipher.data, cipher.len);
            cipher.len = 0;
            plain.len = 0;
            return tail;
        }

        /** -1: данных мало; 0: формат неизвестен; иначе длина пакета вместе с заголовком. */
        private int nextLen(int offset, int avail) {
            if (avail <= 0) {
                return -1;
            }
            if (proto == PROTO_ABRIDGED) {
                int first = plain.data[offset] & 0xFF;
                int payload, header;
                if (first == 0x7F || first == 0xFF) {
                    if (avail < 4) {
                        return -1;
                    }
                    payload = ((plain.data[offset + 1] & 0xFF) | ((plain.data[offset + 2] & 0xFF) << 8)
                            | ((plain.data[offset + 3] & 0xFF) << 16)) * 4;
                    header = 4;
                } else {
                    payload = (first & 0x7F) * 4;
                    header = 1;
                }
                if (payload <= 0) {
                    return 0;
                }
                int packet = header + payload;
                return avail < packet ? -1 : packet;
            }
            if (proto == PROTO_INTERMEDIATE || proto == PROTO_PADDED) {
                if (avail < 4) {
                    return -1;
                }
                int payload = ((plain.data[offset] & 0xFF) | ((plain.data[offset + 1] & 0xFF) << 8)
                        | ((plain.data[offset + 2] & 0xFF) << 16) | ((plain.data[offset + 3] & 0xFF) << 24)) & 0x7FFFFFFF;
                if (payload <= 0) {
                    return 0;
                }
                int packet = 4 + payload;
                return avail < packet ? -1 : packet;
            }
            return 0;
        }
    }

    // ── подключение клиента ──────────────────────────────────────────────────

    private static byte[] readFully(InputStream in, int n) throws IOException {
        byte[] buf = new byte[n];
        int off = 0;
        while (off < n) {
            int r = in.read(buf, off, n - off);
            if (r < 0) {
                throw new IOException("client closed");
            }
            off += r;
        }
        return buf;
    }

    private static void handle(Socket client) {
        connectionsTotal.incrementAndGet();
        WsClient ws = null;
        Socket direct = null;
        try {
            client.setTcpNoDelay(true);
            client.setSoTimeout(10000);
            final byte[] secret = WsProxyManager.secretBytes();
            InputStream in = client.getInputStream();
            OutputStream out = client.getOutputStream();

            byte[] handshake = readFully(in, HANDSHAKE_LEN);
            client.setSoTimeout(0);

            byte[] prekeyIv = Arrays.copyOfRange(handshake, SKIP_LEN, SKIP_LEN + PREKEY_LEN + IV_LEN);
            Cipher cltDec = ctr(sha256(Arrays.copyOf(prekeyIv, PREKEY_LEN), secret), Arrays.copyOfRange(prekeyIv, PREKEY_LEN, PREKEY_LEN + IV_LEN));
            byte[] decrypted = cltDec.update(handshake); // заодно сдвигает поток дешифратора за 64 байта заголовка

            byte[] protoTag = Arrays.copyOfRange(decrypted, PROTO_TAG_POS, PROTO_TAG_POS + 4);
            int proto = ((protoTag[0] & 0xFF) << 24) | ((protoTag[1] & 0xFF) << 16) | ((protoTag[2] & 0xFF) << 8) | (protoTag[3] & 0xFF);
            if (proto != PROTO_ABRIDGED && proto != PROTO_INTERMEDIATE && proto != PROTO_PADDED) {
                Log.w(TAG, "плохой заголовок (неверный секрет или протокол)");
                connectionsFailed.incrementAndGet();
                drain(in);
                return;
            }
            short dcIdx = (short) ((decrypted[DC_IDX_POS] & 0xFF) | ((decrypted[DC_IDX_POS + 1] & 0xFF) << 8));
            int dc = Math.abs((int) dcIdx);
            boolean media = dcIdx < 0;
            if (dc >= 10000) {
                dc -= 10000; // тестовые ДЦ ведём как боевые с тем же номером
            }

            byte[] relay = relayInit(protoTag, media ? -dc : dc);
            Crypto c = new Crypto();
            c.cltDec = cltDec;
            byte[] rev = reversed(prekeyIv);
            c.cltEnc = ctr(sha256(Arrays.copyOf(rev, PREKEY_LEN), secret), Arrays.copyOfRange(rev, PREKEY_LEN, PREKEY_LEN + IV_LEN));
            c.tgEnc = ctr(Arrays.copyOfRange(relay, SKIP_LEN, SKIP_LEN + PREKEY_LEN), Arrays.copyOfRange(relay, SKIP_LEN + PREKEY_LEN, SKIP_LEN + PREKEY_LEN + IV_LEN));
            byte[] relayRev = reversed(Arrays.copyOfRange(relay, SKIP_LEN, SKIP_LEN + PREKEY_LEN + IV_LEN));
            c.tgDec = ctr(Arrays.copyOf(relayRev, PREKEY_LEN), Arrays.copyOfRange(relayRev, PREKEY_LEN, PREKEY_LEN + IV_LEN));
            c.tgEnc.update(new byte[HANDSHAKE_LEN]); // заголовок мы отправляем открытым видом, поток шифра идёт дальше

            final int dcFinal = dc;
            ws = openWs(dcFinal, media);
            if (ws != null) {
                connectionsWs.incrementAndGet();
                Log.d(TAG, "ДЦ" + dcFinal + (media ? "m" : "") + " через WebSocket");
                ws.send(relay);
                bridgeWs(client, in, out, ws, c, new Splitter(proto));
                return;
            }
            if (WsProxyManager.tcpFallback()) {
                direct = openTcp(dcFinal);
                if (direct != null) {
                    connectionsTcp.incrementAndGet();
                    Log.d(TAG, "ДЦ" + dcFinal + (media ? "m" : "") + " напрямую по TCP");
                    direct.getOutputStream().write(relay);
                    direct.getOutputStream().flush();
                    bridgeTcp(client, in, out, direct, c);
                    return;
                }
            }
            connectionsFailed.incrementAndGet();
            Log.w(TAG, "ДЦ" + dcFinal + (media ? "m" : "") + ": не удалось подключиться");
        } catch (Throwable t) {
            connectionsFailed.incrementAndGet();
            Log.d(TAG, "соединение завершено: " + t);
        } finally {
            if (ws != null) {
                ws.close();
            }
            closeQuietly(direct);
            closeQuietly(client);
        }
    }

    private static void drain(InputStream in) {
        try {
            byte[] buf = new byte[1024];
            int total = 0;
            while (total < 4096 && in.read(buf) > 0) {
                total += buf.length;
            }
        } catch (IOException ignored) {
        }
    }

    private static void closeQuietly(Socket s) {
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static String targetIp(int dc) {
        String custom = WsProxyManager.dcOverride(dc);
        if (custom != null) {
            return custom;
        }
        if (dc == 2 || dc == 4) {
            return GATEWAY_IP;
        }
        return DC_DEFAULT_IPS.get(dc);
    }

    private static WsClient openWs(int dc, boolean media) {
        String ip = targetIp(dc);
        if (ip == null) {
            return null;
        }
        int gateway = dc == 203 ? 2 : dc;
        String[] domains = media
                ? new String[]{"kws" + gateway + "-1.web.telegram.org", "kws" + gateway + ".web.telegram.org"}
                : new String[]{"kws" + gateway + ".web.telegram.org"};
        boolean fronting = WsProxyManager.fronting();
        for (String domain : domains) {
            boolean[] modes = tryFrontingFirst ? new boolean[]{true, false} : new boolean[]{false, true};
            for (boolean fronted : modes) {
                if (fronted && !fronting) {
                    continue;
                }
                try {
                    WsClient ws = WsClient.connect(ip, domain, fronted ? FRONTING_SNI : null, "/apiws", fronted ? 7000 : 8000);
                    tryFrontingFirst = fronted;
                    return ws;
                } catch (IOException e) {
                    Log.d(TAG, "ДЦ" + dc + " " + domain + (fronted ? " (фронтинг)" : "") + ": " + e);
                }
            }
        }
        return null;
    }

    private static Socket openTcp(int dc) {
        String ip = DC_DEFAULT_IPS.get(dc == 203 ? 203 : dc);
        if (ip == null) {
            return null;
        }
        Socket s = new Socket();
        try {
            s.setTcpNoDelay(true);
            s.connect(new InetSocketAddress(ip, 443), 8000);
            return s;
        } catch (IOException e) {
            closeQuietly(s);
            return null;
        }
    }

    private static void bridgeWs(Socket client, InputStream in, OutputStream out, WsClient ws, Crypto c, Splitter splitter) {
        Thread down = new Thread(() -> {
            try {
                byte[] data;
                while ((data = ws.recv()) != null) {
                    bytesDown.addAndGet(data.length);
                    byte[] plain = c.tgDec.update(data);
                    out.write(c.cltEnc.update(plain));
                    out.flush();
                }
            } catch (IOException ignored) {
            } finally {
                closeQuietly(client);
                ws.close();
            }
        }, "hg-wsproxy-down");
        down.setDaemon(true);
        down.start();
        try {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                bytesUp.addAndGet(n);
                byte[] chunk = Arrays.copyOf(buf, n);
                byte[] plain = c.cltDec.update(chunk);
                byte[] enc = c.tgEnc.update(plain);
                List<byte[]> parts = splitter.split(enc, plain);
                if (parts.size() == 1) {
                    ws.send(parts.get(0));
                } else if (!parts.isEmpty()) {
                    ws.sendBatch(parts);
                }
            }
            byte[] tail = splitter.flush();
            if (tail != null) {
                ws.send(tail);
            }
        } catch (IOException ignored) {
        } finally {
            ws.close();
            closeQuietly(client);
        }
    }

    private static void bridgeTcp(Socket client, InputStream in, OutputStream out, Socket remote, Crypto c) throws IOException {
        final InputStream rin = remote.getInputStream();
        final OutputStream rout = remote.getOutputStream();
        Thread down = new Thread(() -> {
            try {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = rin.read(buf)) > 0) {
                    bytesDown.addAndGet(n);
                    byte[] plain = c.tgDec.update(Arrays.copyOf(buf, n));
                    out.write(c.cltEnc.update(plain));
                    out.flush();
                }
            } catch (IOException ignored) {
            } finally {
                closeQuietly(client);
                closeQuietly(remote);
            }
        }, "hg-wsproxy-tcp-down");
        down.setDaemon(true);
        down.start();
        try {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                bytesUp.addAndGet(n);
                byte[] plain = c.cltDec.update(Arrays.copyOf(buf, n));
                rout.write(c.tgEnc.update(plain));
                rout.flush();
            }
        } catch (IOException ignored) {
        } finally {
            closeQuietly(remote);
            closeQuietly(client);
        }
    }
}
