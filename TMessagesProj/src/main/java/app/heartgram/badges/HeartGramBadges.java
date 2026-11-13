package app.heartgram.badges;

import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import com.exteragram.messenger.api.dto.BadgeDTO;
import com.exteragram.messenger.api.model.ProfileStatus;
import com.exteragram.messenger.badges.BadgesController;
import com.exteragram.messenger.badges.source.ApiBadgeSource;
import com.exteragram.messenger.badges.source.BadgeInfo;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.BulletinFactory;
import app.heartgram.HeartGramLabels;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Значки HeartGram: список приходит из нашего API (адрес и ключ закрытой части задаются при сборке,
 * в репозитории их нет) и кладётся в {@link ApiBadgeSource}, откуда значок берёт {@link BadgesController}.
 *
 * Если при сборке адрес не задан, модуль ничего не делает и клиент работает как обычно.
 */
public final class HeartGramBadges {

    private static final String TAG = "HeartGramBadges";
    private static final long REFRESH_MINUTES = 30;
    /** Эмодзи значка разработчика по умолчанию (тот же, что в моде). */
    private static final String DEV_EMOJI_ID = "5278611606756942667";
    /** «имя_набора/номер», номер с единицы. */
    private static final Pattern PACK_REF = Pattern.compile("([A-Za-z0-9_]{3,64})/(\\d{1,4})");

    private static final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "HeartGramBadges");
        t.setDaemon(true);
        return t;
    });

    private static volatile boolean started;
    private static volatile String etag = "";
    private static volatile Snapshot snapshot = Snapshot.EMPTY;
    private static volatile Set<Long> lastIds = new HashSet<>();
    /** «набор/номер» -> id документа; 0 — не нашли, -1 — загрузка идёт. */
    private static final Map<String, Long> packDocuments = new ConcurrentHashMap<>();

    private HeartGramBadges() {
    }

    /** Разобранный список. */
    private static final class Snapshot {
        static final Snapshot EMPTY = new Snapshot();

        final Set<Long> clients = new HashSet<>();
        final Set<Long> developers = new HashSet<>();
        final Set<Long> owners = new HashSet<>();
        final Set<Long> official = new HashSet<>();
        final List<Long> supporters = new ArrayList<>();
        final Map<Long, Integer> supporterNo = new HashMap<>();
        final Map<Long, String> customEmoji = new HashMap<>();
        final Map<Long, String> customText = new HashMap<>();
        final Map<String, String> emoji = new HashMap<>();
        String project = "HeartGram";
        /** Способы поддержать проект: {название, подробности, ссылка или адрес}. */
        final List<String[]> support = new ArrayList<>();
        String supportNote = "";
        final List<String[]> extraButtons = new ArrayList<>();
        /** Источники плагинов, которым доверяем: каналы, люди, разработчики HeartGram. */
        final Set<Long> trusted = new HashSet<>();
    }

    /** Эмодзи значка HeartGram? Такие крутятся без остановки, обычные статусы Telegram — два раза. */
    public static boolean isBadgeDocument(long documentId) {
        if (documentId == 0) {
            return false;
        }
        String id = String.valueOf(documentId);
        Snapshot s = snapshot;
        return s.emoji.containsValue(id) || s.customEmoji.containsValue(id);
    }

    /** Способы поддержки из API: {название, подробности, ссылка}. */
    public static List<String[]> supportItems() {
        return new ArrayList<>(snapshot.support);
    }

    public static String supportNote() {
        return snapshot.supportNote;
    }

    public static void start() {
        if (started || TextUtils.isEmpty(BuildConfig.HG_LIST_URL)) {
            return;
        }
        started = true;
        executor.scheduleWithFixedDelay(HeartGramBadges::refreshSafely, 3, REFRESH_MINUTES * 60, TimeUnit.SECONDS);
    }

    /** Значок для человека или канала; проверенным (галочка), мошенникам и поддельным второй значок не рисуем. */
    public static BadgeDTO badgeFor(TLObject object) {
        if (object instanceof TLRPC.User) {
            TLRPC.User user = (TLRPC.User) object;
            if (user.verified || user.scam || user.fake) {
                return null;
            }
        } else if (object instanceof TLRPC.Chat) {
            TLRPC.Chat chat = (TLRPC.Chat) object;
            if (chat.verified || chat.scam || chat.fake) {
                return null;
            }
        } else {
            return null;
        }
        return BadgesController.INSTANCE.getBadge(object);
    }

    /** Id документа эмодзи значка или 0. */
    public static long documentFor(TLObject object) {
        BadgeDTO badge = badgeFor(object);
        return badge == null ? 0 : badge.getDocumentId();
    }

    /** Эмодзи значка по умолчанию для человека (без его личного выбора). */
    public static long defaultDocument(long id) {
        Snapshot s = snapshot;
        long nid = normalize(id);
        String kind = kindOf(s, nid);
        String ref = s.emoji.get(kind);
        if (ref == null && "developer".equals(kind)) {
            ref = DEV_EMOJI_ID;
        }
        return ref == null || ref.isEmpty() ? 0 : documentId(ref);
    }

    /** Перечитать список сразу (после правки своего значка). */
    public static void refreshNow() {
        executor.execute(() -> {
            etag = "";
            refreshSafely();
        });
    }

    /** Источник плагина проверен: канал, человек или разработчик из списка. */
    public static boolean isTrustedSource(long id) {
        return id != 0 && snapshot.trusted.contains(normalize(id));
    }

    /**
     * Сообщение с плагином пришло из проверенного источника: пересланное (автор пересылки), пост канала
     * или личное и групповое сообщение от проверенного человека. Скрытый автор пересылки не доверяется.
     */
    public static boolean isTrustedMessage(MessageObject message) {
        if (message == null) {
            return false;
        }
        try {
            if (message.isForwarded()) {
                Long from = message.getForwardedFromId();
                return from != null && isTrustedSource(from);
            }
            if (message.isFromChannel() && !message.isFromChat()) {
                return isTrustedSource(message.getDialogId());
            }
            return isTrustedSource(message.getFromChatId());
        } catch (Throwable t) {
            return false;
        }
    }

    /** Сведения для окна значка. */
    public static final class Info {
        public String kind;
        public boolean owner;
        public int number;
        public String text;
        public String project;
    }

    /** {name}, {project} и {number} в тексте из API заменяются на имя владельца значка, проект и номер. */
    private static String substitute(String text, TLObject object, Info info) {
        if (text == null) {
            return null;
        }
        String name = "";
        if (object instanceof TLRPC.User) {
            name = org.telegram.messenger.UserObject.getUserName((TLRPC.User) object);
        } else if (object instanceof TLRPC.Chat) {
            name = ((TLRPC.Chat) object).title;
        }
        return text.replace("{name}", name == null ? "" : name)
                .replace("{project}", info.project == null ? "" : info.project)
                .replace("{number}", info.number > 0 ? String.valueOf(info.number) : "")
                .replace("{no}", info.number > 0 ? String.valueOf(info.number) : "");
    }

    /** Кнопки под значком из API (extras.buttons): {текст, ссылка}. */
    public static List<String[]> extraButtons() {
        return new ArrayList<>(snapshot.extraButtons);
    }

    public static Info infoFor(TLObject object) {
        long id;
        if (object instanceof TLRPC.User) {
            id = ((TLRPC.User) object).id;
        } else if (object instanceof TLRPC.Chat) {
            id = ((TLRPC.Chat) object).id;
        } else {
            return null;
        }
        Snapshot s = snapshot;
        id = normalize(id);
        Info info = new Info();
        info.kind = kindOf(s, id);
        info.owner = s.owners.contains(id);
        Integer n = s.supporterNo.get(id);
        info.number = n == null ? 0 : n;
        BadgeDTO badge = badgeFor(object);
        info.project = s.project;
        info.text = badge == null ? null : substitute(badge.getText(), object, info);
        return info;
    }

    /**
     * Нажатие на значок: всплывающая подпись с кнопкой «Подробнее», она открывает окно значка.
     * true, если обработали (значок наш).
     */
    public static boolean openBadge(final BaseFragment fragment, final TLObject object) {
        BadgeDTO badge = badgeFor(object);
        Info info = infoFor(object);
        Log.d(TAG, "нажатие на значок: badge=" + (badge != null) + " kind=" + (info == null ? "-" : info.kind));
        if (badge == null || info == null || fragment == null || fragment.getParentActivity() == null) {
            return false;
        }
        TLRPC.Document document = AnimatedEmojiDrawable.findDocument(UserConfig.selectedAccount, badge.getDocumentId());
        if (document == null) {
            HeartGramBadgeSheet.show(fragment, object);
            return true;
        }
        CharSequence text = TextUtils.isEmpty(info.text)
                ? HeartGramBadgeSheet.title(info)
                : HeartGramMarkup.render(fragment.getParentActivity(), info.text);
        if ("official".equals(info.kind)) {
            // официальный ресурс: только подпись, без «Подробнее»
            BulletinFactory.of(fragment).createEmojiBulletin(document, text).show();
            return true;
        }
        BulletinFactory.of(fragment)
                .createEmojiBulletin(document, text,
                        HeartGramLabels.tr("Подробнее", "Details"), () -> HeartGramBadgeSheet.show(fragment, object))
                .show();
        return true;
    }

    /** Старый вызов: открыть окно значка сразу. */
    public static void openSheet(BaseFragment fragment, TLObject object) {
        BadgeDTO badge = badgeFor(object);
        if (badge == null || fragment == null) {
            return;
        }
        if (HeartGramBadgeSheet.show(fragment, object)) {
            return;
        }
        int account = UserConfig.selectedAccount;
        if (object instanceof TLRPC.User) {
            BadgesController.INSTANCE.showBadgeBulletin(fragment, badge, (TLRPC.User) object, fragment.getResourceProvider(), account);
        } else if (object instanceof TLRPC.Chat) {
            BadgesController.INSTANCE.showBadgeBulletin(fragment, badge, (TLRPC.Chat) object, fragment.getResourceProvider(), account);
        }
    }

    /** Номер значка поддержавшего (1, 2, …) или 0, если номера нет. */
    public static int numberOf(long id) {
        Integer n = snapshot.supporterNo.get(normalize(id));
        return n == null ? 0 : n;
    }

    private static void refreshSafely() {
        try {
            refresh();
        } catch (Throwable t) {
            Log.w(TAG, "список не обновился: " + t.getClass().getSimpleName());
        }
    }

    private static void refresh() throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(BuildConfig.HG_LIST_URL).openConnection();
        try {
            c.setConnectTimeout(15000);
            c.setReadTimeout(15000);
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("User-Agent", "HeartGram/" + BuildConfig.VERSION_NAME + " (Android)");
            if (!etag.isEmpty()) {
                c.setRequestProperty("If-None-Match", etag);
            }
            int code = c.getResponseCode();
            if (code == 304) {
                return;
            }
            if (code != 200) {
                Log.w(TAG, "список: ответ " + code);
                return;
            }
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            try (InputStream in = c.getInputStream()) {
                byte[] chunk = new byte[8192];
                for (int n; (n = in.read(chunk)) > 0; ) {
                    buf.write(chunk, 0, n);
                }
            }
            String tag = c.getHeaderField("ETag");
            snapshot = parse(new JSONObject(buf.toString("UTF-8")));
            etag = tag == null ? "" : tag;
        } finally {
            c.disconnect();
        }
        rebuild();
    }

    // ── разбор ──────────────────────────────────────────────────────────────────

    private static Snapshot parse(JSONObject data) {
        Snapshot s = new Snapshot();
        fill(s.clients, data.optJSONArray("clients"));
        fill(s.developers, data.optJSONArray("developers"));
        fill(s.owners, data.optJSONArray("owners"));
        fill(s.official, data.optJSONArray("official"));
        JSONArray sup = data.optJSONArray("supporters");
        if (sup != null) {
            for (int i = 0; i < sup.length(); i++) {
                Long id = idOf(sup.opt(i));
                if (id != null) {
                    s.supporters.add(id);
                }
            }
        }
        JSONObject no = data.optJSONObject("supporterNo");
        if (no != null) {
            for (Iterator<String> it = no.keys(); it.hasNext(); ) {
                String k = it.next();
                Long id = idOf(k);
                if (id != null) {
                    s.supporterNo.put(id, no.optInt(k, 0));
                }
            }
        }
        readStrings(s.emoji, data.optJSONObject("emoji"));
        String project = data.optString("project", "").trim();
        if (!project.isEmpty()) {
            s.project = project;
        }
        readPeerStrings(s.customEmoji, data.optJSONObject("customEmoji"));
        JSONObject secret = unseal(data.optString("sealed", ""));
        JSONObject extras = secret.optJSONObject("extras");
        JSONArray buttons = extras == null ? null : extras.optJSONArray("buttons");
        for (int i = 0; buttons != null && i < buttons.length(); i++) {
            JSONObject b = buttons.optJSONObject(i);
            if (b == null) {
                continue;
            }
            String text = b.optString("text", b.optString("title", b.optString("name", "")));
            String url = b.optString("url", b.optString("link", ""));
            if (!text.isEmpty() && !url.isEmpty()) {
                s.extraButtons.add(new String[]{text, url});
            }
        }
        JSONObject support = secret.optJSONObject("support");
        if (support != null) {
            s.supportNote = support.optString("note", "");
            JSONArray items = support.optJSONArray("items");
            for (int i = 0; items != null && i < items.length(); i++) {
                JSONObject it = items.optJSONObject(i);
                if (it == null) {
                    continue;
                }
                String name = it.optString("name", it.optString("title", ""));
                String details = it.optString("details", it.optString("value", it.optString("address", "")));
                String url = it.optString("url", it.optString("link", ""));
                String emoji = it.optString("emoji", it.optString("emojiId", it.optString("icon", "")));
                if (!name.isEmpty()) {
                    s.support.add(new String[]{name, details, url, emoji});
                }
            }
        }
        JSONObject trust = secret.optJSONObject("trust");
        JSONArray sources = trust == null ? null : trust.optJSONArray("sources");
        if (sources != null) {
            for (int i = 0; i < sources.length(); i++) {
                Object o = sources.opt(i);
                Long id = idOf(o instanceof JSONObject ? ((JSONObject) o).opt("id") : o);
                if (id != null) {
                    s.trusted.add(id);
                }
            }
        }
        s.trusted.addAll(s.official);
        s.trusted.addAll(s.developers);
        s.trusted.addAll(s.owners);
        JSONObject custom = secret.has("custom") ? secret.optJSONObject("custom") : data.optJSONObject("custom");
        readPeerStrings(s.customText, custom);
        return s;
    }

    private static void fill(Set<Long> out, JSONArray arr) {
        if (arr == null) {
            return;
        }
        for (int i = 0; i < arr.length(); i++) {
            Long id = idOf(arr.opt(i));
            if (id != null) {
                out.add(id);
            }
        }
    }

    private static void readStrings(Map<String, String> out, JSONObject obj) {
        if (obj == null) {
            return;
        }
        for (Iterator<String> it = obj.keys(); it.hasNext(); ) {
            String k = it.next();
            String v = obj.optString(k, "").trim();
            if (!v.isEmpty()) {
                out.put(k, v);
            }
        }
    }

    private static void readPeerStrings(Map<Long, String> out, JSONObject obj) {
        if (obj == null) {
            return;
        }
        for (Iterator<String> it = obj.keys(); it.hasNext(); ) {
            String k = it.next();
            Long id = idOf(k);
            String v = obj.optString(k, "").trim();
            if (id != null && !v.isEmpty()) {
                out.put(id, v);
            }
        }
    }

    /** Id из списка: у людей положительный, у каналов и чатов отрицательный (иногда с префиксом -100). */
    private static Long idOf(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return normalize(Long.parseLong(String.valueOf(o).trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Один и тот же канал: -100123… и -123… и 123…; ключ — положительный id объекта в Telegram. */
    private static long normalize(long id) {
        long abs = Math.abs(id);
        if (abs > 1_000_000_000_000L) {
            abs -= 1_000_000_000_000L;
        }
        return abs;
    }

    /** AES-256-GCM: base64(iv 12 байт | шифртекст | тег 16 байт); ключ 32 байта в hex задаётся при сборке. */
    private static JSONObject unseal(String sealed) {
        String hex = BuildConfig.HG_SEAL_KEY;
        if (TextUtils.isEmpty(sealed) || TextUtils.isEmpty(hex) || hex.length() != 64) {
            return new JSONObject();
        }
        byte[] key = new byte[32];
        try {
            byte[] raw = Base64.decode(sealed, Base64.DEFAULT);
            if (raw.length < 12 + 16) {
                return new JSONObject();
            }
            for (int i = 0; i < 32; i++) {
                key[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, raw, 0, 12));
            byte[] plain = cipher.doFinal(raw, 12, raw.length - 12);
            return new JSONObject(new String(plain, StandardCharsets.UTF_8));
        } catch (Throwable t) {
            Log.w(TAG, "закрытая часть не расшифровалась: " + t.getClass().getSimpleName());
            return new JSONObject();
        } finally {
            java.util.Arrays.fill(key, (byte) 0);
        }
    }

    // ── сборка значков ──────────────────────────────────────────────────────────

    /** Кладёт значки в {@link ApiBadgeSource}. Значки, чьё эмодзи ещё грузится, появятся после загрузки. */
    private static void rebuild() {
        Snapshot s = snapshot;
        ApiBadgeSource source = BadgesController.INSTANCE.getApiBadgeSource();
        Map<Long, BadgeInfo> next = new HashMap<>();
        boolean waiting = false;

        List<Long> all = new ArrayList<>();
        all.addAll(s.clients);
        all.addAll(s.supporters);
        all.addAll(s.official);
        all.addAll(s.developers);
        all.addAll(s.owners);
        for (Long id : all) {
            String kind = kindOf(s, id);
            String ref = emojiRef(s, kind, id);
            if (ref.isEmpty()) {
                continue;
            }
            long doc = documentId(ref);
            if (doc == 0) {
                waiting = waiting || packDocuments.get(ref) != null && packDocuments.get(ref) == -1L;
                continue;
            }
            String text = s.customText.get(id);
            ProfileStatus status = "developer".equals(kind) ? ProfileStatus.DEVELOPER
                    : "supporter".equals(kind) ? ProfileStatus.SUPPORTER : ProfileStatus.DEFAULT;
            next.put(id, new BadgeInfo(new BadgeDTO(doc, TextUtils.isEmpty(text) ? null : text), status, false));
        }

        // сначала новые значки, потом убираем пропавшие: список не мигает пустым
        for (Map.Entry<Long, BadgeInfo> e : next.entrySet()) {
            source.put(e.getKey(), e.getValue());
        }
        for (Long old : lastIds) {
            if (!next.containsKey(old)) {
                source.put(old, null);
            }
        }
        lastIds = new HashSet<>(next.keySet());
        redraw();
        if (waiting) {
            // эмодзи из наборов ещё приходят: пересоберём, когда придут
            executor.schedule(HeartGramBadges::rebuild, 3, TimeUnit.SECONDS);
        }
    }

    /** Старший значок важнее: создатель и разработчик, затем официальный ресурс, поддержавший, клиент. */
    private static String kindOf(Snapshot s, long id) {
        if (s.developers.contains(id) || s.owners.contains(id)) {
            return "developer";
        }
        if (s.official.contains(id)) {
            return "official";
        }
        if (s.supporters.contains(id)) {
            return "supporter";
        }
        return "client";
    }

    private static String emojiRef(Snapshot s, String kind, long id) {
        String own = s.customEmoji.get(id);
        if (own != null && !own.isEmpty()) {
            return own;
        }
        String owner = s.emoji.get("owner");
        if (owner != null && "developer".equals(kind) && s.owners.contains(id)) {
            return owner;
        }
        String v = s.emoji.get(kind);
        if (v == null && "developer".equals(kind)) {
            v = DEV_EMOJI_ID;
        }
        return v == null ? "" : v.trim();
    }

    /** Цифры — id документа, «набор/номер» — документ из набора (грузится один раз). 0 — пока неизвестно. */
    private static long documentId(String ref) {
        if (ref.matches("\\d{5,20}")) {
            return Long.parseLong(ref);
        }
        Long cached = packDocuments.get(ref);
        if (cached != null) {
            return Math.max(cached, 0L);
        }
        Matcher m = PACK_REF.matcher(ref);
        if (!m.matches()) {
            packDocuments.put(ref, 0L);
            return 0;
        }
        packDocuments.put(ref, -1L);
        final int number = Integer.parseInt(m.group(2));
        TLRPC.TL_messages_getStickerSet req = new TLRPC.TL_messages_getStickerSet();
        TLRPC.TL_inputStickerSetShortName shortName = new TLRPC.TL_inputStickerSetShortName();
        shortName.short_name = m.group(1);
        req.stickerset = shortName;
        req.hash = 0;
        ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(req, (response, error) -> {
            long found = 0;
            if (response instanceof TLRPC.messages_StickerSet) {
                List<TLRPC.Document> docs = ((TLRPC.messages_StickerSet) response).documents;
                if (docs != null && number >= 1 && number <= docs.size()) {
                    found = docs.get(number - 1).id;
                }
            }
            packDocuments.put(ref, found);
        });
        return 0;
    }

    private static void redraw() {
        AndroidUtilities.runOnUIThread(() -> {
            try {
                NotificationCenter.getInstance(UserConfig.selectedAccount)
                        .postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_ALL);
                // список чатов пересобирает ячейки только по этому событию: без него значок появлялся после входа в чат
                NotificationCenter.getInstance(UserConfig.selectedAccount).postNotificationName(NotificationCenter.dialogsNeedReload, true);
                Log.d(TAG, "перерисовка: clients=" + snapshot.clients.size() + " dev=" + snapshot.developers.size()
                        + " official=" + snapshot.official.size() + " supporters=" + snapshot.supporters.size());
            } catch (Throwable t) {
                Log.w(TAG, "перерисовка: " + t.getClass().getSimpleName());
            }
        });
    }
}
