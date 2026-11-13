package app.heartgram.badges;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import org.json.JSONObject;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Клиент HeartGram API для правки своего значка: HTTP, токены (по одному на аккаунт) и вход.
 * Адрес берётся из адреса списка значков, заданного при сборке, в коде его нет.
 */
final class HeartGramApi {

    private static final String TAG = "HeartGramApi";
    private static final String PREFS = "heartgram_badge_auth";

    /** Все сетевые вызовы идут сюда, не в поток интерфейса. */
    static final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "HeartGramApi");
        t.setDaemon(true);
        return t;
    });

    /** Ответ сервера: код (0 — нет связи) и тело. */
    static final class Response {
        final int code;
        final String body;

        Response(int code, String body) {
            this.code = code;
            this.body = body;
        }

        JSONObject json() {
            try {
                return new JSONObject(body);
            } catch (Throwable t) {
                return new JSONObject();
            }
        }

        boolean ok() {
            return code == 200;
        }
    }

    private HeartGramApi() {
    }

    static boolean configured() {
        return !TextUtils.isEmpty(base());
    }

    private static String base() {
        try {
            URL u = new URL(BuildConfig.HG_LIST_URL);
            return u.getProtocol() + "://" + u.getAuthority();
        } catch (Throwable t) {
            return "";
        }
    }

    static Response call(String method, String path, String token, JSONObject body) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(base() + path).openConnection();
            c.setRequestMethod(method);
            c.setConnectTimeout(10000);
            c.setReadTimeout(15000);
            c.setRequestProperty("Accept", "application/json");
            if (token != null) {
                c.setRequestProperty("Authorization", "Bearer " + token);
            }
            if (body != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                try (OutputStream o = c.getOutputStream()) {
                    o.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
            } else if ("POST".equals(method)) {
                c.setDoOutput(true);
                c.setFixedLengthStreamingMode(0);
            }
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            if (in != null) {
                try (InputStream i = in) {
                    byte[] chunk = new byte[4096];
                    for (int n; (n = i.read(chunk)) > 0; ) {
                        buf.write(chunk, 0, n);
                    }
                }
            }
            android.util.Log.d("HeartGramApi", method + " " + path + " -> " + code + " (" + buf.size() + " байт)"
                    + (code >= 400 ? ": " + buf.toString("UTF-8") : ""));
            return new Response(code, buf.toString("UTF-8"));
        } catch (Throwable t) {
            android.util.Log.w("HeartGramApi", method + " " + path + " не удалось: " + t);
            return new Response(0, t.getClass().getSimpleName());
        } finally {
            if (c != null) {
                c.disconnect();
            }
        }
    }

    // ── токены: по одному на аккаунт Telegram ───────────────────────────────────

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static long me() {
        return UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
    }

    static String token(Context c) {
        return prefs(c).getString("token_" + me(), null);
    }

    static void saveToken(Context c, String token) {
        prefs(c).edit().putString("token_" + me(), token).apply();
    }

    static void clearToken(Context c) {
        prefs(c).edit().remove("token_" + me()).apply();
    }

    /** Случайный секрет устройства: по нему сервер привязывает телефон. */
    static String hwid(Context c) {
        SharedPreferences p = prefs(c);
        String v = p.getString("hwid", null);
        if (v == null) {
            byte[] raw = new byte[24];
            new SecureRandom().nextBytes(raw);
            v = Base64.encodeToString(raw, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            p.edit().putString("hwid", v).apply();
        }
        return v;
    }

    // ── вход по подписи Telegram (мини-приложение бота без показа окна) ─────────

    /** Последняя ошибка Telegram и причина, по которой не сработал автовход (для экрана ключа). */
    static volatile String lastTlError = "";
    static volatile String initError = "";

    private static TLObject ask(TLObject request) throws Exception {
        final TLObject[] out = new TLObject[1];
        final CountDownLatch latch = new CountDownLatch(1);
        ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(request, (response, error) -> {
            if (error != null) {
                lastTlError = error.code + " " + error.text;
            }
            out[0] = response;
            latch.countDown();
        });
        if (!latch.await(20, TimeUnit.SECONDS)) {
            throw new RuntimeException("Telegram не ответил");
        }
        return out[0];
    }

    /** initData мини-приложения бота или null. */
    static String initData() {
        initError = "";
        lastTlError = "";
        try {
            Response cfg = call("GET", "/api/ext/auth/config", null, null);
            JSONObject c = cfg.json();
            String bot = c.optString("botUsername"), url = c.optString("webAppUrl");
            if (!cfg.ok() || bot.isEmpty() || url.isEmpty()) {
                initError = "сервер: " + cfg.code;
                return null;
            }
            TLRPC.TL_contacts_resolveUsername resolve = new TLRPC.TL_contacts_resolveUsername();
            resolve.username = bot;
            TLObject peer = ask(resolve);
            if (!(peer instanceof TLRPC.TL_contacts_resolvedPeer)) {
                initError = "бот не найден: " + lastTlError;
                return null;
            }
            TLRPC.TL_contacts_resolvedPeer resolved = (TLRPC.TL_contacts_resolvedPeer) peer;
            if (resolved.users == null || resolved.users.isEmpty()) {
                return null;
            }
            TLRPC.TL_messages_requestSimpleWebView web = new TLRPC.TL_messages_requestSimpleWebView();
            web.bot = MessagesController.getInstance(UserConfig.selectedAccount).getInputUser(resolved.users.get(0));
            web.url = url;
            web.platform = "android";
            web.flags |= 8;
            TLObject res = ask(web);
            if (!(res instanceof TLRPC.TL_webViewResultUrl) && !(res instanceof TLRPC.TL_simpleWebViewResultUrl)) {
                initError = "мини-приложение не открылось: " + lastTlError;
                return null;
            }
            String link = res instanceof TLRPC.TL_simpleWebViewResultUrl
                    ? ((TLRPC.TL_simpleWebViewResultUrl) res).url : ((TLRPC.TL_webViewResultUrl) res).url;
            int i = link == null ? -1 : link.indexOf("tgWebAppData=");
            if (i < 0) {
                initError = "в ответе нет подписи Telegram";
                return null;
            }
            String d = link.substring(i + "tgWebAppData=".length());
            int amp = d.indexOf("&tgWebApp");
            if (amp > 0) {
                d = d.substring(0, amp);
            }
            return URLDecoder.decode(d, "UTF-8");
        } catch (Throwable t) {
            Log.w(TAG, "initData: " + t.getClass().getSimpleName());
            return null;
        }
    }
}
