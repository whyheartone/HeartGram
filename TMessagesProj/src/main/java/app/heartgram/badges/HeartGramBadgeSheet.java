package app.heartgram.badges;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;

import java.util.Random;

/**
 * Окно значка: сердце на фоне мерцающих звёзд, номер, кто это, подпись владельца значка и кнопка.
 */
public final class HeartGramBadgeSheet {

    private static final String CHANNEL = "heartgramclient";

    private HeartGramBadgeSheet() {
    }

    public static boolean show(final BaseFragment fragment, TLObject peer) {
        android.util.Log.d("HeartGramSheet", "show " + (peer == null ? "null" : peer.getClass().getSimpleName()));
        Activity activity = fragment == null ? null : fragment.getParentActivity();
        HeartGramBadges.Info info = HeartGramBadges.infoFor(peer);
        long document = HeartGramBadges.documentFor(peer);
        if (activity == null || info == null || document == 0) {
            return false;
        }
        final Context context = activity;
        final BottomSheet sheet = new BottomSheet(context, false, fragment.getResourceProvider());

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(8), dp(20), dp(20));
        root.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground));

        FrameLayout art = new FrameLayout(context);
        // два слоя: звёзды Telegram внизу (в цвет эмодзи), само эмодзи сверху
        final StarsView stars = new StarsView(context);
        // звёзды появляются, когда цвет эмодзи уже известен (иначе они сначала в цвете темы, потом перекрашиваются)
        stars.setAlpha(0f);
        final Integer knownColor = EmojiColor.cachedFor(document);
        if (knownColor != null) {
            // цвет этого эмодзи уже считали (в списке чатов, профиле): звёзды сразу нужного цвета и без задержки
            stars.setColor(knownColor);
        } else {
            stars.postDelayed(stars::showNow, 900);
        }
        art.addView(stars, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        art.addView(new EmojiView(context, document, stars::setColor), new FrameLayout.LayoutParams(dp(112), dp(112), Gravity.CENTER));
        root.addView(art, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(200)));

        if (info.number > 0) {
            TextView pill = text(context, LocaleController.formatString(R.string.HGBadgeNumber, info.number), 14, true,
                    Theme.getColor(Theme.key_featuredStickers_buttonText));
            pill.setPadding(dp(14), dp(5), dp(14), dp(5));
            pill.setBackground(Theme.createRoundRectDrawable(dp(14), Theme.getColor(Theme.key_featuredStickers_addButton)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.gravity = Gravity.CENTER_HORIZONTAL;
            lp.topMargin = dp(4);
            root.addView(pill, lp);
        }

        // если у значка свой текст, строку «Разработчик …» и подобные не пишем: остаётся только его текст
        if (TextUtils.isEmpty(info.text)) {
            TextView title = text(context, titleFor(info, peer), 20, true, Theme.getColor(Theme.key_dialogTextBlack));
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            tl.topMargin = dp(14);
            root.addView(title, tl);
        }

        if (!TextUtils.isEmpty(info.text)) {
            TextView bubble = text(context, HeartGramMarkup.render(context, info.text), 15, false, Theme.getColor(Theme.key_dialogTextBlack));
            bubble.setMovementMethod(LinkMovementMethod.getInstance());
            bubble.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkText));
            bubble.setPadding(dp(16), dp(10), dp(16), dp(10));
            bubble.setBackground(Theme.createRoundRectDrawable(dp(16), Theme.getColor(Theme.key_windowBackgroundGray)));
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            bl.gravity = Gravity.CENTER_HORIZONTAL;
            bl.topMargin = dp(12);
            root.addView(bubble, bl);
        }

        for (final String[] extra : HeartGramBadges.buttonsFor(peer, info)) {
            TextView eb = text(context, extra[0], 16, true, Theme.getColor(Theme.key_featuredStickers_buttonText));
            eb.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(10), Theme.getColor(Theme.key_featuredStickers_addButton),
                    Theme.blendOver(Theme.getColor(Theme.key_featuredStickers_addButton), 0x33FFFFFF)));
            eb.setOnClickListener(v -> {
                sheet.dismiss();
                org.telegram.messenger.browser.Browser.openUrl(context, extra[1]);
            });
            LinearLayout.LayoutParams el = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
            el.topMargin = dp(12);
            root.addView(eb, el);
        }
        final boolean channelButton = false;
        TextView button = text(context, LocaleController.getString(R.string.HGBadgeOk), 16, true, Theme.getColor(Theme.key_featuredStickers_buttonText));
        button.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(10), Theme.getColor(Theme.key_featuredStickers_addButton),
                Theme.blendOver(Theme.getColor(Theme.key_featuredStickers_addButton), 0x33FFFFFF)));
        button.setOnClickListener(v -> {
            sheet.dismiss();
            if (channelButton) {
                fragment.getMessagesController().openByUserName(CHANNEL, fragment, 1);
            }
        });
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        ll.topMargin = dp(20);
        root.addView(button, ll);

        // свой значок можно изменить: владелец, разработчики и те, кому это разрешил кабинет
        if (peer instanceof TLRPC.User && ((TLRPC.User) peer).id == UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId()
                && !"client".equals(info.kind)) {
            TextView edit = text(context, LocaleController.getString(R.string.HGBadgeEdit), 15, true,
                    Theme.getColor(Theme.key_featuredStickers_addButton));
            edit.setPadding(dp(12), dp(12), dp(12), dp(4));
            edit.setOnClickListener(v -> {
                sheet.dismiss();
                HeartGramEditor.open(fragment);
            });
            root.addView(edit, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        sheet.setCustomView(root);
        fragment.showDialog(sheet);
        return true;
    }

    static CharSequence title(HeartGramBadges.Info info) {
        return titleFor(info, null);
    }

    private static CharSequence titleFor(HeartGramBadges.Info info, TLObject peer) {
        String project = info.project;
        switch (info.kind) {
            case "developer":
                return LocaleController.formatString(info.owner ? R.string.HGBadgeCreator : R.string.HGBadgeDeveloper, project);
            case "official":
                return LocaleController.formatString(R.string.HGBadgeOfficial, project);
            case "supporter":
                return LocaleController.formatString(R.string.HGBadgeSupporter, project);
            default:
                return LocaleController.formatString(R.string.HGBadgeClient, project);
        }
    }

    private static final android.graphics.Path starPath = new android.graphics.Path();

    /** Четырёхлучевая искорка (✦). */
    static void drawStar(Canvas canvas, float cx, float cy, float r, Paint paint) {
        android.graphics.Path p = starPath;
        p.reset();
        float k = r * 0.18f;
        p.moveTo(cx, cy - r);
        p.quadTo(cx + k, cy - k, cx + r, cy);
        p.quadTo(cx + k, cy + k, cx, cy + r);
        p.quadTo(cx - k, cy + k, cx - r, cy);
        p.quadTo(cx - k, cy - k, cx, cy - r);
        p.close();
        canvas.drawPath(p, paint);
    }

    private static TextView text(Context context, CharSequence value, int sp, boolean bold, int color) {
        TextView tv = new TextView(context);
        tv.setText(value);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setTextColor(color);
        tv.setGravity(Gravity.CENTER);
        if (bold) {
            tv.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return tv;
    }

    /** Эмодзи значка крупно, анимированное. */
    private static final class EmojiView extends View {
        private final AnimatedEmojiDrawable drawable;
        private final java.util.function.IntConsumer onColor;
        private boolean attached, tinted, sampling;
        private int frames;

        EmojiView(Context context, long documentId, java.util.function.IntConsumer onColor) {
            super(context);
            this.onColor = onColor;
            drawable = AnimatedEmojiDrawable.make(UserConfig.selectedAccount, AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW_LARGE_140, documentId);
        }

        /** Цвет звёзд по эмодзи (общий расчёт с кешем); false, пока кадр не загрузился. */
        private boolean computeColor() {
            Integer color = EmojiColor.of(drawable);
            if (color == null) {
                return false;
            }
            onColor.accept(color);
            return true;
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
            if (sampling) {
                drawable.draw(canvas);
                return;
            }
            // силуэт цвета фона под эмодзи: звёзды уходят за значок и не просвечивают сквозь прозрачные места
            int bg = Theme.getColor(Theme.key_dialogBackground);
            float[] m = {
                    0, 0, 0, 0, android.graphics.Color.red(bg),
                    0, 0, 0, 0, android.graphics.Color.green(bg),
                    0, 0, 0, 0, android.graphics.Color.blue(bg),
                    0, 0, 0, 12, 0};
            drawable.setColorFilter(new android.graphics.ColorMatrixColorFilter(m));
            drawable.draw(canvas);
            drawable.setColorFilter(null);
            boolean colorable = drawable.canOverrideColor();
            if (colorable) {
                drawable.setColorFilter(new android.graphics.PorterDuffColorFilter(
                        Theme.getColor(Theme.key_featuredStickers_addButton), android.graphics.PorterDuff.Mode.SRC_IN));
                // эмодзи подстраивается под тему телефона, звёзды берут тот же цвет
                if (!tinted) {
                    tinted = true;
                    onColor.accept(Theme.getColor(Theme.key_featuredStickers_addButton));
                }
            }
            drawable.draw(canvas);
            if (colorable) {
                drawable.setColorFilter(null);
            }
            if (!tinted && ++frames % 2 == 0 && frames < 2 * 400) {
                tinted = computeColor();
            }
        }
    }

    /** Звёзды Telegram позади значка: летают, мерцают; цвет подстраивается под эмодзи. */
    private static final class StarsView extends org.telegram.ui.Components.Premium.StarParticlesView {
        private final Paint tint = new Paint(Paint.ANTI_ALIAS_FLAG);

        StarsView(Context context) {
            super(context, 50);
            tint.setColorFilter(new android.graphics.PorterDuffColorFilter(
                    Theme.getColor(Theme.key_featuredStickers_addButton), android.graphics.PorterDuff.Mode.SRC_IN));
            drawable.overridePaint = tint;
            drawable.speedScale = 1.3f;
        }

        private boolean shown;

        void setColor(int color) {
            tint.setColorFilter(new android.graphics.PorterDuffColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN));
            invalidate();
            showNow();
        }

        /** Плавно показывает звёзды (один раз). */
        void showNow() {
            if (shown) {
                return;
            }
            shown = true;
            animate().alpha(1f).setDuration(350).start();
        }

        @Override
        protected int getStarsRectWidth() {
            return dp(150);
        }
    }
}
