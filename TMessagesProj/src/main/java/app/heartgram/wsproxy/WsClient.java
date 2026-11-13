package app.heartgram.wsproxy;

import android.util.Base64;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Минимальный клиент WebSocket поверх TLS для шлюзов Telegram (kws*.web.telegram.org/apiws).
 * Идея и протокол: tg-ws-proxy (Flowseal, MIT), https://github.com/Flowseal/tg-ws-proxy.
 * Подключаемся по IP, имя шлюза уходит в SNI и заголовок Host; при «фронтинге» в SNI подставляется другое имя.
 */
final class WsClient implements Closeable {

    static final int OP_CONT = 0x0, OP_TEXT = 0x1, OP_BINARY = 0x2, OP_CLOSE = 0x8, OP_PING = 0x9, OP_PONG = 0xA;
    private static final int MAX_MESSAGE = 16 * 1024 * 1024;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private volatile boolean closed;
    private final ByteArrayOutputStream fragments = new ByteArrayOutputStream();

    private WsClient(Socket socket, InputStream in, OutputStream out) {
        this.socket = socket;
        this.in = in;
        this.out = out;
    }

    /** Ошибка рукопожатия: код ответа сервера (например, 302 или 403). */
    static final class HandshakeException extends IOException {
        final int status;

        HandshakeException(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    /**
     * @param ip     адрес шлюза
     * @param domain имя шлюза: заголовок Host и (без фронтинга) SNI
     * @param sni    другое имя для SNI, если нужен фронтинг; тогда имя сертификата не проверяется
     */
    static WsClient connect(String ip, String domain, String sni, String path, int timeoutMs) throws IOException {
        Socket raw = new Socket();
        try {
            raw.setTcpNoDelay(true);
            raw.connect(new InetSocketAddress(ip, 443), timeoutMs);
            raw.setSoTimeout(timeoutMs);
            final boolean fronted = sni != null;
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            SSLSocket tls = (SSLSocket) factory.createSocket(raw, fronted ? sni : domain, 443, true);
            SSLParameters params = tls.getSSLParameters();
            params.setServerNames(Collections.singletonList(new SNIHostName(fronted ? sni : domain)));
            if (!fronted) {
                params.setEndpointIdentificationAlgorithm("HTTPS");
            }
            tls.setSSLParameters(params);
            tls.startHandshake();

            InputStream in = new BufferedInputStream(tls.getInputStream(), 32 * 1024);
            OutputStream out = tls.getOutputStream();

            byte[] keyBytes = new byte[16];
            RANDOM.nextBytes(keyBytes);
            String key = Base64.encodeToString(keyBytes, Base64.NO_WRAP);
            String request = "GET " + path + " HTTP/1.1\r\n"
                    + "Host: " + domain + "\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Sec-WebSocket-Key: " + key + "\r\n"
                    + "Sec-WebSocket-Version: 13\r\n"
                    + "Sec-WebSocket-Protocol: binary\r\n\r\n";
            out.write(request.getBytes(StandardCharsets.US_ASCII));
            out.flush();

            String status = readLine(in);
            if (status == null) {
                throw new HandshakeException(0, "empty response");
            }
            String[] parts = status.split(" ", 3);
            int code = 0;
            try {
                code = parts.length >= 2 ? Integer.parseInt(parts[1]) : 0;
            } catch (NumberFormatException ignored) {
            }
            String line;
            while ((line = readLine(in)) != null && !line.isEmpty()) {
                // заголовки ответа нам не нужны
            }
            if (code != 101) {
                throw new HandshakeException(code, status);
            }
            raw.setSoTimeout(0);
            return new WsClient(tls, in, out);
        } catch (IOException | RuntimeException e) {
            try {
                raw.close();
            } catch (IOException ignored) {
            }
            throw e instanceof IOException ? (IOException) e : new IOException(e);
        }
    }

    private static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') {
                break;
            }
            if (c != '\r') {
                sb.append((char) c);
            }
            if (sb.length() > 8192) {
                throw new IOException("header too long");
            }
        }
        if (c == -1 && sb.length() == 0) {
            return null;
        }
        return sb.toString();
    }

    void send(byte[] data) throws IOException {
        synchronized (out) {
            ensureOpen();
            out.write(frame(OP_BINARY, data));
            out.flush();
        }
    }

    void sendBatch(List<byte[]> parts) throws IOException {
        synchronized (out) {
            ensureOpen();
            for (byte[] part : parts) {
                out.write(frame(OP_BINARY, part));
            }
            out.flush();
        }
    }

    private void ensureOpen() throws IOException {
        if (closed) {
            throw new IOException("WebSocket closed");
        }
    }

    private static byte[] frame(int opcode, byte[] data) {
        int len = data.length;
        int header = len < 126 ? 2 : len < 65536 ? 4 : 10;
        byte[] frame = new byte[header + 4 + len];
        frame[0] = (byte) (0x80 | opcode);
        if (len < 126) {
            frame[1] = (byte) (0x80 | len);
        } else if (len < 65536) {
            frame[1] = (byte) (0x80 | 126);
            frame[2] = (byte) (len >> 8);
            frame[3] = (byte) len;
        } else {
            frame[1] = (byte) (0x80 | 127);
            long l = len;
            for (int i = 0; i < 8; i++) {
                frame[2 + i] = (byte) (l >> (56 - 8 * i));
            }
        }
        byte[] mask = new byte[4];
        RANDOM.nextBytes(mask);
        System.arraycopy(mask, 0, frame, header, 4);
        for (int i = 0; i < len; i++) {
            frame[header + 4 + i] = (byte) (data[i] ^ mask[i & 3]);
        }
        return frame;
    }

    /** Следующее сообщение от шлюза; null, если соединение закрыто. */
    byte[] recv() throws IOException {
        while (!closed) {
            int b0 = in.read();
            int b1 = in.read();
            if (b0 < 0 || b1 < 0) {
                return null;
            }
            boolean fin = (b0 & 0x80) != 0;
            int opcode = b0 & 0x0F;
            long length = b1 & 0x7F;
            if (length == 126) {
                length = ((long) readByte() << 8) | readByte();
            } else if (length == 127) {
                length = 0;
                for (int i = 0; i < 8; i++) {
                    length = (length << 8) | readByte();
                }
            }
            if (length > MAX_MESSAGE) {
                throw new IOException("WS frame too large: " + length);
            }
            byte[] mask = null;
            if ((b1 & 0x80) != 0) {
                mask = new byte[4];
                readFully(mask);
            }
            byte[] payload = new byte[(int) length];
            readFully(payload);
            if (mask != null) {
                for (int i = 0; i < payload.length; i++) {
                    payload[i] ^= mask[i & 3];
                }
            }
            if (opcode == OP_CLOSE) {
                closed = true;
                try {
                    synchronized (out) {
                        out.write(frame(OP_CLOSE, payload.length >= 2 ? new byte[]{payload[0], payload[1]} : new byte[0]));
                        out.flush();
                    }
                } catch (IOException ignored) {
                }
                return null;
            }
            if (opcode == OP_PING) {
                try {
                    synchronized (out) {
                        out.write(frame(OP_PONG, payload));
                        out.flush();
                    }
                } catch (IOException ignored) {
                }
                continue;
            }
            if (opcode == OP_PONG) {
                continue;
            }
            if (opcode == OP_CONT || opcode == OP_TEXT || opcode == OP_BINARY) {
                if (fin && fragments.size() == 0) {
                    return payload;
                }
                fragments.write(payload, 0, payload.length);
                if (fragments.size() > MAX_MESSAGE) {
                    throw new IOException("WS message too large");
                }
                if (!fin) {
                    continue;
                }
                byte[] message = fragments.toByteArray();
                fragments.reset();
                return message;
            }
        }
        return null;
    }

    private int readByte() throws IOException {
        int b = in.read();
        if (b < 0) {
            throw new IOException("unexpected EOF");
        }
        return b;
    }

    private void readFully(byte[] buf) throws IOException {
        int off = 0;
        while (off < buf.length) {
            int n = in.read(buf, off, buf.length - off);
            if (n < 0) {
                throw new IOException("unexpected EOF");
            }
            off += n;
        }
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            try {
                synchronized (out) {
                    out.write(frame(OP_CLOSE, new byte[0]));
                    out.flush();
                }
            } catch (IOException ignored) {
            }
        }
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
