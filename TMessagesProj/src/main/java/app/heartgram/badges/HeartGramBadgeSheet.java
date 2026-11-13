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
        art.addView(new StarsView(context), new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        art.addView(new EmojiView(context, document), new FrameLayout.LayoutParams(dp(112), dp(112), Gravity.CENTER));
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

        TextView title = text(context, titleFor(info, peer), 20, true, Theme.getColor(Theme.key_dialogTextBlack));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tl.topMargin = dp(14);
        root.addView(title, tl);

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

        final boolean channelButton = !"client".equals(info.kind);
        TextView button = text(context, channelButton
                ? LocaleController.formatString(R.string.HGBadgeChannel, info.project)
                : LocaleController.getString(R.string.HGBadgeOk), 16, true, Theme.getColor(Theme.key_featuredStickers_buttonText));
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
        private boolean attached;

        EmojiView(Context context, long documentId) {
            super(context);
            drawable = AnimatedEmojiDrawable.make(UserConfig.selectedAccount, AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW_LARGE_140, documentId);
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

    /** Мерцающие «плюсики» позади значка. */
    private static final class StarsView extends View {
        private final float[] x = new float[44], y = new float[44], size = new float[44], phase = new float[44], speed = new float[44];
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        StarsView(Context context) {
            super(context);
            Random random = new Random(7);
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
