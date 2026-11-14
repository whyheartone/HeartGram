package app.heartgram.wsproxy;

import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.SharedConfig;

import java.security.SecureRandom;
import java.util.ArrayList;

import app.heartgram.HeartGramPrefs;

/**
 * Включение и настройки обхода блокировок: локальный WebSocket-прокси (см. {@link WsProxy}) и запись о нём
 * в списке прокси клиента (127.0.0.1, свой секрет). Выключено, пока человек сам не включит.
 */
public final class WsProxyManager {

    private static final String TAG = "HeartGramWsProxy";
    private static final String K_ENABLED = "wsproxy_enabled", K_PORT = "wsproxy_port", K_SECRET = "wsproxy_secret",
            K_FRONTING = "wsproxy_fronting", K_TCP = "wsproxy_tcp", K_DC = "wsproxy_dc_ips", K_REGISTERED = "wsproxy_registered";
    public static final int DEFAULT_PORT = 14430;

    private WsProxyManager() {
    }

    private static SharedPreferences p() {
        return HeartGramPrefs.prefs();
    }

    public static boolean enabled() {
        return p().getBoolean(K_ENABLED, false);
    }

    /** Порт, на котором слушаем; если занят, подбирается другой и запоминается. */
    public static int port() {
        return p().getInt(K_PORT, DEFAULT_PORT);
    }

    public static boolean fronting() {
        return p().getBoolean(K_FRONTING, false);
    }

    public static boolean tcpFallback() {
        return p().getBoolean(K_TCP, false);
    }

    /** Ручные адреса шлюзов в виде «2:149.154.167.220,4:149.154.167.220»; для остальных ДЦ берутся стандартные. */
    public static String dcOverrides() {
        return p().getString(K_DC, "");
    }

    static String dcOverride(int dc) {
        String list = dcOverrides();
        if (TextUtils.isEmpty(list)) {
            return null;
        }
        for (String entry : list.split("[,;\\s]+")) {
            int i = entry.indexOf(':');
            if (i > 0) {
                try {
                    if (Integer.parseInt(entry.substring(0, i).trim()) == dc) {
                        String ip = entry.substring(i + 1).trim();
                        return ip.isEmpty() ? null : ip;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return null;
    }

    /** Секрет прокси: 16 случайных байт, создаются один раз на установку. */
    public static String secretHex() {
        String s = p().getString(K_SECRET, null);
        if (s == null || s.length() != 32) {
            byte[] b = new byte[16];
            new SecureRandom().nextBytes(b);
            s = toHex(b);
            p().edit().putString(K_SECRET, s).apply();
        }
        return s;
    }

    static byte[] secretBytes() {
        String h = secretHex();
        byte[] b = new byte[16];
        for (int i = 0; i < 16; i++) {
            b[i] = (byte) Integer.parseInt(h.substring(2 * i, 2 * i + 2), 16);
        }
        return b;
    }

    private static String toHex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) {
            sb.append(String.format("%02x", x & 0xFF));
        }
        return sb.toString();
    }

    /** Строка секрета для клиента: «dd» + секрет, режим с добавлением случайных байт. */
    private static String clientSecret() {
        return "dd" + secretHex();
    }

    /** При запуске приложения: если обход был включён, поднимаем прокси раньше, чем клиент начнёт подключаться. */
    public static void onAppStart() {
        if (!enabled()) {
            return;
        }
        try {
            int port = WsProxy.start(port());
            if (port > 0 && port != port()) {
                p().edit().putInt(K_PORT, port).apply();
            }
            if (port > 0) {
                register(port, true);
            }
        } catch (Throwable t) {
            Log.w(TAG, "запуск: " + t);
        }
    }

    /** Включает или выключает обход, меняет прокси клиента. */
    public static void setEnabled(boolean on) {
        p().edit().putBoolean(K_ENABLED, on).apply();
        if (on) {
            int port = WsProxy.start(port());
            if (port > 0) {
                p().edit().putInt(K_PORT, port).apply();
                register(port, true);
            } else {
                p().edit().putBoolean(K_ENABLED, false).apply();
            }
        } else {
            WsProxy.stop();
            register(port(), false);
        }
    }

    public static void restartIfNeeded() {
        if (enabled()) {
            WsProxy.stop();
            int port = WsProxy.start(port());
            if (port > 0) {
                p().edit().putInt(K_PORT, port).apply();
                register(port, true);
            }
        }
    }

    private static String entryKey(int port) {
        return "127.0.0.1:" + port + ":" + clientSecret();
    }

    /** Добавляет (и включает) или выключает наш прокси в списке прокси клиента. */
    private static void register(final int port, final boolean on) {
        AndroidUtilities.runOnUIThread(() -> {
            try {
                SharedConfig.loadProxyList();
                String previous = p().getString(K_REGISTERED, null);
                String current = entryKey(port);
                // убираем старые записи (другой порт или секрет)
                for (SharedConfig.ProxyInfo info : new ArrayList<>(SharedConfig.proxyList)) {
                    String key = info.address + ":" + info.port + ":" + info.secret;
                    if (previous != null && key.equals(previous) && !key.equals(current)) {
                        if (SharedConfig.currentProxy == info) {
                            SharedConfig.setProxyEnable(false);
                        }
                        SharedConfig.deleteProxy(info);
                    }
                }
                if (on) {
                    SharedConfig.ProxyInfo info = SharedConfig.addProxy(new SharedConfig.ProxyInfo("127.0.0.1", port, "", "", clientSecret()));
                    SharedConfig.setCurrentProxy(info);
                    p().edit().putString(K_REGISTERED, current).apply();
                } else {
                    SharedConfig.ProxyInfo cur = SharedConfig.currentProxy;
                    if (cur != null && current.equals(cur.address + ":" + cur.port + ":" + cur.secret)) {
                        SharedConfig.setProxyEnable(false);
                    }
                }
            } catch (Throwable t) {
                Log.w(TAG, "запись о прокси: " + t);
            }
        });
    }

    /** Ссылка для ручного добавления прокси в другой клиент. */
    public static String link() {
        return "tg://proxy?server=127.0.0.1&port=" + port() + "&secret=" + clientSecret();
    }
}
