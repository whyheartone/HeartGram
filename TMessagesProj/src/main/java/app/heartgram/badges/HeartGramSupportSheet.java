package app.heartgram.badges;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;

import app.heartgram.HeartGramLabels;

/**
 * Окно «Поддержать HeartGram» (как на ПК): сердце, подпись, три шага (донат, чек, значок) и выбор уровня:
 * обычный значок или крутой (своё эмодзи и подпись). Суммы, курс TON и адресат чека берутся из API.
 */
public final class HeartGramSupportSheet {

    private HeartGramSupportSheet() {
    }

    private static String tr(String ru, String en) {
        return HeartGramLabels.tr(ru, en);
    }

    public static void show(final BaseFragment fragment, boolean startCool) {
        Activity activity = fragment == null ? null : fragment.getParentActivity();
        if (activity == null) {
            return;
        }
        final Context context = activity;
        final BottomSheet sheet = new BottomSheet(context, true, fragment.getResourceProvider());

        final LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(12), dp(20), dp(16));
        root.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground));

        // сердце на тёмном круге; у поддержавшего со своим эмодзи вместо сердца его эмодзи
        FrameLayout art = new FrameLayout(context);
        GradientDrawable circle = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF4A1F2B, 0xFF1E0E14});
        circle.setShape(GradientDrawable.OVAL);
        art.setBackground(circle);
        long mine = HeartGramBadges.documentFor(UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser());
        if (mine != 0) {
            art.addView(HeartGramBadgeSheet.createEmojiView(context, mine), new FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER));
        } else {
            ImageView heart = new ImageView(context);
            heart.setImageResource(R.drawable.exteraless_icon_menu);
            heart.setColorFilter(new PorterDuffColorFilter(0xFFF7889E, PorterDuff.Mode.SRC_IN));
            art.addView(heart, new FrameLayout.LayoutParams(dp(58), dp(58), Gravity.CENTER));
        }
        LinearLayout.LayoutParams artLp = new LinearLayout.LayoutParams(dp(96), dp(96));
        artLp.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(art, artLp);

        TextView title = text(context, tr("Поддержать ", "Support ") + HeartGramBadges.projectName(), 20, true,
                Theme.getColor(Theme.key_dialogTextBlack), Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.topMargin = dp(14);
        root.addView(title, titleLp);

        TextView about = text(context, tr("Донат идёт на развитие клиента. Тем, кто поддержал, выдаётся уникальный значок рядом с именем.",
                "Donations go to the client's development. Supporters get a unique badge next to their name."),
                14, false, Theme.getColor(Theme.key_dialogTextGray2), Gravity.CENTER);
        LinearLayout.LayoutParams aboutLp = new LinearLayout.LayoutParams(-1, -2);
        aboutLp.topMargin = dp(4);
        root.addView(about, aboutLp);

        // переключатель уровня
        final boolean[] cool = {startCool};
        final LinearLayout tiers = new LinearLayout(context);
        tiers.setOrientation(LinearLayout.HORIZONTAL);
        final TextView tierBasic = chip(context, tr("Значок", "Badge"));
        final TextView tierCool = chip(context, tr("Крутой значок", "Cool badge"));
        tiers.addView(tierBasic, new LinearLayout.LayoutParams(0, dp(36), 1f));
        LinearLayout.LayoutParams coolLp = new LinearLayout.LayoutParams(0, dp(36), 1f);
        coolLp.leftMargin = dp(8);
        tiers.addView(tierCool, coolLp);
        LinearLayout.LayoutParams tiersLp = new LinearLayout.LayoutParams(-1, -2);
        tiersLp.topMargin = dp(16);
        root.addView(tiers, tiersLp);

        final LinearLayout steps = new LinearLayout(context);
        steps.setOrientation(LinearLayout.VERTICAL);
        root.addView(steps, new LinearLayout.LayoutParams(-1, -2));

        final Runnable fill = () -> {
            setChip(tierBasic, !cool[0]);
            setChip(tierCool, cool[0]);
            steps.removeAllViews();
            fillSteps(context, steps, cool[0]);
        };
        tierBasic.setOnClickListener(v -> {
            cool[0] = false;
            fill.run();
        });
        tierCool.setOnClickListener(v -> {
            cool[0] = true;
            fill.run();
        });
        fill.run();

        TextView close = text(context, tr("Закрыть", "Close"), 16, true, Theme.getColor(Theme.key_featuredStickers_buttonText), Gravity.CENTER);
        close.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(12), Theme.getColor(Theme.key_featuredStickers_addButton),
                Theme.blendOver(Theme.getColor(Theme.key_featuredStickers_addButton), 0x33FFFFFF)));
        close.setOnClickListener(v -> sheet.dismiss());
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(-1, dp(50));
        closeLp.topMargin = dp(18);
        root.addView(close, closeLp);

        android.widget.ScrollView scroll = new android.widget.ScrollView(context);
        scroll.addView(root);
        sheet.setCustomView(scroll);
        fragment.showDialog(sheet);
    }

    private static void fillSteps(Context context, LinearLayout steps, boolean cool) {
        final String contact = HeartGramBadges.supportContactOrDefault();
        steps.addView(step(context, R.drawable.filter_money, tr("Сделать донат", "Make a donation"),
                tr("Переведите " + HeartGramBadges.priceText(cool) + " на любой из реквизитов: они в настройках HeartGram, раздел «Другое».",
                        "Send " + HeartGramBadges.priceText(cool) + " to any of the options: they are in HeartGram settings, section “Other”."),
                null));
        steps.addView(step(context, R.drawable.msg_photos, tr("Отправить чек", "Send the receipt"),
                tr("Пришлите скриншот перевода в личные сообщения. Должны быть видны сумма, дата и время.",
                        "Send a screenshot of the transfer in a private message. The amount, date and time must be visible."),
                contact));
        steps.addView(step(context, R.drawable.msg_reactions2, tr("Получить значок", "Get the badge"),
                cool
                        ? tr("Когда платёж проверен, появится крутой значок. Его можно изменить: выберите любое эмодзи и свою подпись (жирный текст, ссылки, @username). Другие пользователи HeartGram увидят его у вашего имени.",
                        "Once the payment is checked you get a cool badge. You can change it: pick any emoji and your own caption (bold text, links, @username). Other HeartGram users see it next to your name.")
                        : tr("Когда платёж проверен, значок появится в вашем профиле и у имени в чатах. Его увидят другие пользователи HeartGram.",
                        "Once the payment is checked the badge appears in your profile and next to your name in chats. Other HeartGram users will see it."),
                null));
    }

    private static View step(Context context, int iconRes, String title, String body, final String contact) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(14), 0, 0);
        ImageView icon = new ImageView(context);
        icon.setImageResource(iconRes);
        icon.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogTextBlack), PorterDuff.Mode.SRC_IN));
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(28), dp(28));
        iconLp.topMargin = dp(2);
        iconLp.rightMargin = dp(14);
        row.addView(icon, iconLp);

        LinearLayout col = new LinearLayout(context);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(text(context, title, 16, true, Theme.getColor(Theme.key_dialogTextBlack), Gravity.START));
        TextView text = text(context, body, 14, false, Theme.getColor(Theme.key_dialogTextGray2), Gravity.START);
        text.setPadding(0, dp(2), 0, 0);
        col.addView(text);
        if (!TextUtils.isEmpty(contact)) {
            TextView nick = text(context, contact, 15, true, Theme.getColor(Theme.key_dialogTextBlack), Gravity.START);
            nick.setPadding(0, dp(6), 0, 0);
            nick.setOnClickListener(v -> org.telegram.messenger.browser.Browser.openUrl(context, "https://t.me/" + contact.replace("@", "")));
            col.addView(nick);
        }
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        return row;
    }

    private static TextView text(Context context, CharSequence value, int sp, boolean bold, int color, int gravity) {
        TextView tv = new TextView(context);
        tv.setText(value);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        tv.setGravity(gravity);
        if (bold) {
            tv.setTypeface(AndroidUtilities.bold());
        }
        return tv;
    }

    private static TextView chip(Context context, String label) {
        TextView tv = text(context, label, 14, true, Theme.getColor(Theme.key_dialogTextBlack), Gravity.CENTER);
        return tv;
    }

    private static void setChip(TextView chip, boolean selected) {
        int accent = Theme.getColor(Theme.key_featuredStickers_addButton);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(18));
        if (selected) {
            bg.setColor(accent);
            chip.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        } else {
            bg.setColor(Color.argb(40, Color.red(accent), Color.green(accent), Color.blue(accent)));
            chip.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        }
        chip.setBackground(bg);
    }
}
