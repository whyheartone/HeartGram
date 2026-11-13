package app.heartgram.badges;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.SelectAnimatedEmojiDialog;

import java.util.Random;

/**
 * Редактор своего значка: эмодзи из пикера Telegram, подпись с форматированием, «Сохранить» и «Сбросить».
 * Права и вход проверяет HeartGram API; правка видна в кабинете и у всех, кто нажмёт на значок.
 */
public final class HeartGramEditor {

    /** Один повторный вход после 401, чтобы не зациклиться. */
    private static boolean retried;

    private HeartGramEditor() {
    }

    private static String tr(String ru, String en) {
        return "ru".equals(LocaleController.getInstance().getCurrentLocale().getLanguage()) ? ru : en;
    }

    private static void toast(final Context context, final String text) {
        AndroidUtilities.runOnUIThread(() -> Toast.makeText(context, text, Toast.LENGTH_LONG).show());
    }

    // ── вход ────────────────────────────────────────────────────────────────────

    public static void open(final BaseFragment fragment) {
        final Activity activity = fragment == null ? null : fragment.getParentActivity();
        if (activity == null) {
            return;
        }
        if (!HeartGramApi.configured()) {
            toast(activity, tr("Редактор значка недоступен в этой сборке", "Badge editor is not available in this build"));
            return;
        }
        final String token = HeartGramApi.token(activity);
        if (token != null) {
            load(fragment, token);
        } else {
            authenticate(fragment);
        }
    }

    private static void authenticate(final BaseFragment fragment) {
        final Activity activity = fragment.getParentActivity();
        final JSONObject device = new JSONObject();
        try {
            device.put("id", String.valueOf(HeartGramApi.me()));
            device.put("hwid", HeartGramApi.hwid(activity));
            device.put("label", Build.MODEL);
        } catch (Throwable ignored) {
        }
        HeartGramApi.io.execute(() -> {
            String data = HeartGramApi.initData();
            if (data != null) {
                JSONObject web = new JSONObject();
                try {
                    web.put("initData", data);
                } catch (Throwable ignored) {
                }
                HeartGramApi.Response w = HeartGramApi.call("POST", "/api/ext/auth/webapp", null, web);
                String token = w.json().optString("token");
                if (w.ok() && !token.isEmpty()) {
                    HeartGramApi.saveToken(activity, token);
                    AndroidUtilities.runOnUIThread(() -> load(fragment, token));
                    return;
                }
                if (w.code == 403) {
                    toast(activity, tr("Правка значка вам недоступна", "Badge editing is not available to you"));
                    return;
                }
            }
            HeartGramApi.Response r = HeartGramApi.call("POST", "/api/ext/auth/device", null, device);
            String token = r.json().optString("token");
            String error = r.json().optString("error");
            if (r.ok() && !token.isEmpty()) {
                HeartGramApi.saveToken(activity, token);
                AndroidUtilities.runOnUIThread(() -> load(fragment, token));
            } else if (r.code == 403 && "forbidden".equals(error)) {
                toast(activity, tr("Правка значка вам недоступна", "Badge editing is not available to you"));
            } else if (r.code == 403 && "not_bound".equals(error)) {
                AndroidUtilities.runOnUIThread(() -> codeDialog(fragment, device));
            } else {
                AndroidUtilities.runOnUIThread(() -> keyDialog(fragment));
            }
        });
    }

    private static EditText codeField(Context context, String hint) {
        EditText field = new EditText(context);
        field.setHint(hint);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        field.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        field.setHintTextColor(Theme.getColor(Theme.key_dialogTextGray2));
        return field;
    }

    private static void codeDialog(final BaseFragment fragment, final JSONObject device) {
        final Activity activity = fragment.getParentActivity();
        if (activity == null) {
            return;
        }
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        final EditText code = codeField(activity, "K7QM2-9XPLA");
        box.addView(code);
        new AlertDialog.Builder(activity)
                .setTitle(tr("Привязка телефона", "Link this phone"))
                .setMessage(tr("Этот аккаунт привязан к другому телефону. Откройте «Изменить значок» там, нажмите «Код для нового телефона» и введите код.",
                        "This account is linked to another phone. Open the badge editor there, tap “Code for a new phone” and enter the code."))
                .setView(box)
                .setNegativeButton(tr("Отмена", "Cancel"), null)
                .setPositiveButton(tr("Привязать", "Link"), (d, w) -> {
                    JSONObject body = new JSONObject();
                    try {
                        body.put("id", device.optString("id"));
                        body.put("hwid", device.optString("hwid"));
                        body.put("label", device.optString("label"));
                        body.put("code", code.getText().toString().trim());
                    } catch (Throwable ignored) {
                    }
                    HeartGramApi.io.execute(() -> {
                        HeartGramApi.Response r = HeartGramApi.call("POST", "/api/ext/auth/device", null, body);
                        String token = r.json().optString("token");
                        if (r.ok() && !token.isEmpty()) {
                            HeartGramApi.saveToken(activity, token);
                            AndroidUtilities.runOnUIThread(() -> load(fragment, token));
                        } else if (r.code == 403 && "device_limit".equals(r.json().optString("error"))) {
                            toast(activity, tr("Уже привязано 3 устройства", "Already 3 devices linked"));
                        } else {
                            toast(activity, tr("Код не подошёл или устарел", "The code is wrong or expired"));
                        }
                    });
                })
                .show();
    }

    private static void keyDialog(final BaseFragment fragment) {
        final Activity activity = fragment.getParentActivity();
        if (activity == null) {
            return;
        }
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        final EditText key = codeField(activity, "K7QM2-9XPLA-T4VNC-8RDJE");
        box.addView(key);
        new AlertDialog.Builder(activity)
                .setTitle(tr("Ключ правки", "Edit key"))
                .setMessage(tr("Ключ выдаёт админ HeartGram. Вводится один раз, дальше вход запоминается на 30 дней.",
                        "The key is issued by a HeartGram admin. Enter it once, the login is remembered for 30 days."))
                .setView(box)
                .setNegativeButton(tr("Отмена", "Cancel"), null)
                .setPositiveButton(tr("Войти", "Sign in"), (d, w) -> {
                    final String k = key.getText().toString().trim();
                    if (k.isEmpty()) {
                        return;
                    }
                    JSONObject body = new JSONObject();
                    try {
                        body.put("id", String.valueOf(HeartGramApi.me()));
                        body.put("key", k);
                    } catch (Throwable ignored) {
                    }
                    HeartGramApi.io.execute(() -> {
                        HeartGramApi.Response r = HeartGramApi.call("POST", "/api/ext/auth/code", null, body);
                        String token = r.json().optString("token");
                        if (r.ok() && !token.isEmpty()) {
                            HeartGramApi.saveToken(activity, token);
                            AndroidUtilities.runOnUIThread(() -> load(fragment, token));
                        } else if (r.code == 403) {
                            toast(activity, tr("Ключ не подошёл", "The key was rejected"));
                        } else if (r.code == 429) {
                            toast(activity, tr("Слишком много попыток, подождите", "Too many attempts, please wait"));
                        } else {
                            toast(activity, tr("Не удалось войти", "Sign-in failed") + " (" + r.code + ")");
                        }
                    });
                })
                .show();
    }

    // ── данные и окно ───────────────────────────────────────────────────────────

    private static void load(final BaseFragment fragment, final String token) {
        final Activity activity = fragment.getParentActivity();
        if (activity == null) {
            return;
        }
        HeartGramApi.io.execute(() -> {
            HeartGramApi.Response r = HeartGramApi.call("GET", "/api/ext/self", token, null);
            if (r.code == 401 && !retried) {
                retried = true;
                HeartGramApi.clearToken(activity);
                AndroidUtilities.runOnUIThread(() -> authenticate(fragment));
                return;
            }
            retried = false;
            if (r.code == 403) {
                toast(activity, tr("Правка значка вам пока недоступна", "Badge editing is not available to you yet"));
                return;
            }
            if (!r.ok()) {
                toast(activity, tr("Не удалось загрузить значок", "Could not load the badge") + " (" + r.code + ")");
                return;
            }
            final JSONObject self = r.json();
            AndroidUtilities.runOnUIThread(() -> showSheet(fragment, token, self));
        });
    }

    private static void showSheet(final BaseFragment fragment, final String token, JSONObject self) {
        final Activity activity = fragment.getParentActivity();
        if (activity == null) {
            return;
        }
        JSONObject edit = self.optJSONObject("edit");
        JSONObject def = self.optJSONObject("default");
        JSONObject limits = self.optJSONObject("limits");
        final int captionMax = limits == null ? 200 : limits.optInt("captionMax", 200);
        String caption = edit != null ? edit.optString("caption") : def != null ? def.optString("caption") : "";
        final long me = HeartGramApi.me();
        final long defaultDocument = HeartGramBadges.defaultDocument(me);
        String emojiRef = edit != null ? edit.optString("emoji") : "";
        long start = emojiRef.matches("\\d{5,20}") ? Long.parseLong(emojiRef) : 0;
        final long[] selected = {start != 0 ? start : (HeartGramBadges.documentFor(currentUser(me)) != 0
                ? HeartGramBadges.documentFor(currentUser(me)) : defaultDocument)};

        final BottomSheet sheet = new BottomSheet(activity, true, fragment.getResourceProvider());
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(8), dp(20), dp(16));
        root.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground));

        final Preview preview = new Preview(activity, selected[0]);
        root.addView(preview, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(170)));

        TextView hint = label(activity, tr("Нажмите на значок, чтобы выбрать любое эмодзи", "Tap the badge to choose any emoji"), 13);
        root.addView(hint, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        final EditText field = new EditText(activity);
        field.setText(caption);
        field.setHint(tr("Подпись под значком", "Caption under the badge"));
        field.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        field.setHintTextColor(Theme.getColor(Theme.key_dialogTextGray2));
        field.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        field.setMinLines(2);
        field.setMaxLines(5);
        field.setGravity(Gravity.TOP | Gravity.START);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(captionMax)});
        field.setBackground(Theme.createRoundRectDrawable(dp(12), Theme.getColor(Theme.key_windowBackgroundGray)));
        field.setPadding(dp(14), dp(10), dp(14), dp(10));
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        fl.topMargin = dp(12);
        root.addView(field, fl);

        final TextView counter = label(activity, String.valueOf(captionMax - field.getText().length()), 12);
        counter.setGravity(Gravity.END);
        root.addView(counter, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        field.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                counter.setText(String.valueOf(captionMax - s.length()));
            }
        });

        LinearLayout tools = new LinearLayout(activity);
        tools.addView(tool(activity, "Ж", v -> wrap(field, "**", "**")));
        tools.addView(tool(activity, tr("Ссылка", "Link"), v -> linkDialog(activity, field)));
        tools.addView(tool(activity, "@", v -> insert(field, "@")));
        root.addView(tools, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        root.addView(label(activity, tr("**жирный**, [текст](https://…), @username. До ", "**bold**, [text](https://…), @username. Up to ")
                + captionMax + tr(" символов, до 3 ссылок. Можно {name} и {project}.", " characters, up to 3 links. {name} and {project} work too."), 12),
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout buttons = new LinearLayout(activity);
        TextView reset = button(activity, tr("Сбросить", "Reset"), false);
        TextView save = button(activity, tr("Сохранить", "Save"), true);
        buttons.addView(reset, new LinearLayout.LayoutParams(0, dp(48), 1f));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(0, dp(48), 1f);
        sl.leftMargin = dp(10);
        buttons.addView(save, sl);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bl.topMargin = dp(14);
        root.addView(buttons, bl);

        TextView phone = label(activity, tr("Код для нового телефона", "Code for a new phone"), 14);
        phone.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton));
        phone.setPadding(0, dp(12), 0, 0);
        phone.setOnClickListener(v -> issueCode(activity, token));
        root.addView(phone, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        preview.setOnClickListener(v -> pickEmoji(fragment, preview, selected[0], doc -> {
            selected[0] = doc == 0 ? defaultDocument : doc;
            preview.set(selected[0]);
        }));

        reset.setOnClickListener(v -> send(activity, token, "DELETE", null, sheet));
        save.setOnClickListener(v -> {
            String text = field.getText().toString().trim();
            if (text.isEmpty() && selected[0] == defaultDocument) {
                send(activity, token, "DELETE", null, sheet);
                return;
            }
            JSONObject body = new JSONObject();
            try {
                body.put("caption", text);
                body.put("emoji", selected[0] == defaultDocument ? "" : String.valueOf(selected[0]));
            } catch (Throwable ignored) {
            }
            send(activity, token, "PUT", body, sheet);
        });

        sheet.setCustomView(root);
        fragment.showDialog(sheet);
    }

    private static TLRPC.User currentUser(long id) {
        return UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser();
    }

    private static void send(final Activity activity, final String token, final String method, final JSONObject body, final BottomSheet sheet) {
        HeartGramApi.io.execute(() -> {
            HeartGramApi.Response r = HeartGramApi.call(method, "/api/ext/self", token, body);
            if (r.ok()) {
                HeartGramBadges.refreshNow();
                AndroidUtilities.runOnUIThread(sheet::dismiss);
                toast(activity, "DELETE".equals(method) ? tr("Сброшено к умолчанию", "Reset to default") : tr("Сохранено", "Saved"));
            } else if (r.code == 401) {
                HeartGramApi.clearToken(activity);
                toast(activity, tr("Вход устарел, откройте редактор снова", "Session expired, open the editor again"));
            } else {
                toast(activity, tr("Не удалось сохранить", "Could not save") + " (" + r.code + ")");
            }
        });
    }

    private static void issueCode(final Activity activity, final String token) {
        HeartGramApi.io.execute(() -> {
            HeartGramApi.Response r = HeartGramApi.call("POST", "/api/ext/device/link", token, null);
            final String code = r.json().optString("code");
            if (!r.ok() || code.isEmpty()) {
                toast(activity, tr("Не удалось получить код", "Could not get a code") + " (" + r.code + ")");
                return;
            }
            AndroidUtilities.runOnUIThread(() -> new AlertDialog.Builder(activity)
                    .setTitle(tr("Код для нового телефона", "Code for a new phone"))
                    .setMessage(code + "\n\n" + tr("Введите на новом телефоне в течение 5 минут. Код одноразовый.",
                            "Enter it on the new phone within 5 minutes. The code works once."))
                    .setPositiveButton("OK", null)
                    .show());
        });
    }

    // ── пикер эмодзи ────────────────────────────────────────────────────────────

    private interface DocumentCallback {
        void onSelected(long documentId);
    }

    private static void pickEmoji(final BaseFragment fragment, final View anchor, long current, final DocumentCallback callback) {
        final SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow[] popup = new SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow[1];
        SelectAnimatedEmojiDialog dialog = new SelectAnimatedEmojiDialog(fragment, anchor.getContext(), true, null,
                SelectAnimatedEmojiDialog.TYPE_EMOJI_STATUS, true, fragment.getResourceProvider(), 24,
                Theme.getColor(Theme.key_featuredStickers_addButton)) {
            @Override
            protected void onEmojiSelected(View emojiView, Long documentId, TLRPC.Document document, TL_stars.TL_starGiftUnique gift, Integer until) {
                callback.onSelected(documentId == null ? 0 : documentId);
                if (popup[0] != null) {
                    popup[0].dismiss();
                }
            }

            @Override
            protected float getScrimDrawableTranslationY() {
                return 0;
            }
        };
        dialog.setSelected(current == 0 ? null : current);
        dialog.setSaveState(3);
        popup[0] = new SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow(dialog, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
        popup[0].showAsDropDown(anchor, 0, -dp(40), Gravity.CENTER_HORIZONTAL);
        popup[0].dimBehind();
    }

    // ── разметка подписи ────────────────────────────────────────────────────────

    private static void wrap(EditText e, String left, String right) {
        int a = Math.max(0, Math.min(e.getSelectionStart(), e.getSelectionEnd()));
        int b = Math.max(0, Math.max(e.getSelectionStart(), e.getSelectionEnd()));
        Editable text = e.getText();
        text.insert(b, right);
        text.insert(a, left);
        e.setSelection(a + left.length(), b + left.length());
    }

    private static void insert(EditText e, String s) {
        int a = Math.max(0, Math.min(e.getSelectionStart(), e.getSelectionEnd()));
        int b = Math.max(0, Math.max(e.getSelectionStart(), e.getSelectionEnd()));
        e.getText().replace(a, b, s);
    }

    private static void linkDialog(final Activity activity, final EditText caption) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        final EditText url = codeField(activity, "https://…");
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        box.addView(url);
        new AlertDialog.Builder(activity)
                .setTitle(tr("Ссылка", "Link"))
                .setView(box)
                .setNegativeButton(tr("Отмена", "Cancel"), null)
                .setPositiveButton("OK", (d, w) -> {
                    String link = url.getText().toString().trim();
                    if (link.isEmpty()) {
                        return;
                    }
                    int a = Math.max(0, Math.min(caption.getSelectionStart(), caption.getSelectionEnd()));
                    int b = Math.max(0, Math.max(caption.getSelectionStart(), caption.getSelectionEnd()));
                    String selected = a == b ? tr("ссылка", "link") : caption.getText().subSequence(a, b).toString();
                    caption.getText().replace(a, b, "[" + selected + "](" + link + ")");
                })
                .show();
    }

    // ── мелкие виджеты ──────────────────────────────────────────────────────────

    private static TextView label(Context context, CharSequence text, int sp) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setTextColor(Theme.getColor(Theme.key_dialogTextGray2));
        tv.setPadding(0, dp(4), 0, 0);
        return tv;
    }

    private static TextView tool(Context context, String text, View.OnClickListener click) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton));
        tv.setPadding(dp(14), dp(8), dp(14), dp(8));
        tv.setOnClickListener(click);
        return tv;
    }

    private static TextView button(Context context, String text, boolean primary) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setGravity(Gravity.CENTER);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        int accent = Theme.getColor(Theme.key_featuredStickers_addButton);
        tv.setTextColor(primary ? Theme.getColor(Theme.key_featuredStickers_buttonText) : accent);
        tv.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(10), primary ? accent : Theme.getColor(Theme.key_windowBackgroundGray),
                Theme.blendOver(primary ? accent : Theme.getColor(Theme.key_windowBackgroundGray), 0x33FFFFFF)));
        return tv;
    }

    /** Предпросмотр значка на мерцающих звёздах; нажатие открывает пикер эмодзи. */
    private static final class Preview extends FrameLayout {
        private final Stars stars;
        private final Emoji emoji;

        Preview(Context context, long documentId) {
            super(context);
            stars = new Stars(context);
            emoji = new Emoji(context, documentId);
            addView(stars, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            addView(emoji, new LayoutParams(dp(104), dp(104), Gravity.CENTER));
        }

        void set(long documentId) {
            emoji.set(documentId);
        }
    }

    private static final class Emoji extends View {
        private AnimatedEmojiDrawable drawable;
        private boolean attached;

        Emoji(Context context, long documentId) {
            super(context);
            set(documentId);
        }

        void set(long documentId) {
            if (drawable != null && attached) {
                drawable.removeView(this);
            }
            drawable = AnimatedEmojiDrawable.make(UserConfig.selectedAccount, AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW_LARGE_140, documentId);
            if (attached) {
                drawable.addView(this);
            }
            invalidate();
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            attached = true;
            drawable.addView(this);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            attached = false;
            drawable.removeView(this);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            drawable.setBounds(0, 0, getWidth(), getHeight());
            drawable.draw(canvas);
        }
    }

    private static final class Stars extends View {
        private final float[] x = new float[40], y = new float[40], size = new float[40], phase = new float[40], speed = new float[40];
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Stars(Context context) {
            super(context);
            Random random = new Random(11);
            for (int i = 0; i < x.length; i++) {
                x[i] = random.nextFloat();
                y[i] = random.nextFloat();
                size[i] = 2f + random.nextFloat() * 5f;
                phase[i] = random.nextFloat() * 6.28f;
                speed[i] = 0.6f + random.nextFloat() * 1.4f;
            }
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(dp(1.4f));
            paint.setColor(0xFFFFFFFF);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float t = (android.os.SystemClock.uptimeMillis() % 100000L) / 1000f;
            int w = getWidth(), h = getHeight();
            for (int i = 0; i < x.length; i++) {
                float a = 0.15f + 0.65f * (0.5f + 0.5f * (float) Math.sin(phase[i] + t * speed[i]));
                paint.setAlpha((int) (a * 255));
                float cx = x[i] * w, cy = y[i] * h, r = dp(size[i]);
                canvas.drawLine(cx - r, cy, cx + r, cy, paint);
                canvas.drawLine(cx, cy - r, cx, cy + r, paint);
            }
            postInvalidateOnAnimation();
        }
    }
}
