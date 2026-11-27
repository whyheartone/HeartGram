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
        show(fragment, startCool, 0, false);
    }

    /** previewDocument: эмодзи того, чей значок нажали; 0 - свой значок или сердце. */
    public static void show(final BaseFragment fragment, boolean startCool, long previewDocument) {
        show(fragment, startCool, previewDocument, false);
    }

    /** canEdit: true, когда пользователь открыл свой собственный значок из профиля и может его изменить. */
    public static void show(final BaseFragment fragment, boolean startCool, long previewDocument, boolean canEdit) {
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

        // 3D-сердце HeartGram по умолчанию; если открыли значок поддержавшего с кастомным эмодзи — показываем его.
        // Когда previewDocument == 0 (окно открыто из «Другое»), всегда показываем наше сердце, а не чужой или свой эмодзи.
        FrameLayout art = new FrameLayout(context);
        long shown = previewDocument;
        if (shown != 0 && !HeartGramBadges.isDefaultHeartDocument(shown)) {
            art.addView(HeartGramBadgeSheet.createEmojiView(context, shown), new FrameLayout.LayoutParams(dp(84), dp(84), Gravity.CENTER));
        } else {
            art.addView(HeartGramBadgeSheet.createHeart3DView(context, null, fragment.getResourceProvider()), new FrameLayout.LayoutParams(dp(120), dp(120), Gravity.CENTER));
        }
        LinearLayout.LayoutParams artLp = new LinearLayout.LayoutParams(dp(120), dp(120));
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
        final TextView tierCool = chip(context, tr("Изменяемый значок", "Editable badge"));
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
        // переключение уровня: старое содержимое тает, новое появляется, высота окна меняется плавно
        final Runnable animatedFill = () -> {
            android.transition.TransitionSet set = new android.transition.TransitionSet()
                    .addTransition(new android.transition.Fade())
                    .addTransition(new android.transition.ChangeBounds())
                    .setOrdering(android.transition.TransitionSet.ORDERING_TOGETHER)
                    .setDuration(240);
            set.setInterpolator(org.telegram.ui.Components.CubicBezierInterpolator.EASE_OUT);
            android.transition.TransitionManager.beginDelayedTransition(root, set);
            fill.run();
        };
        tierBasic.setOnClickListener(v -> {
            if (cool[0]) {
                cool[0] = false;
                animatedFill.run();
            }
        });
        tierCool.setOnClickListener(v -> {
            if (!cool[0]) {
                cool[0] = true;
                animatedFill.run();
            }
        });
        fill.run();

        TextView close = text(context, tr("Поддержать", "Support"), 16, true, Theme.getColor(Theme.key_featuredStickers_buttonText), Gravity.CENTER);
        close.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(12), Theme.getColor(Theme.key_featuredStickers_addButton),
                Theme.blendOver(Theme.getColor(Theme.key_featuredStickers_addButton), 0x33FFFFFF)));
        close.setOnClickListener(v -> {
            sheet.dismiss();
            // способы поддержки лежат в «Другом»: открываем этот экран (если мы уже на нём, просто закрываем окно)
            if (!(fragment instanceof app.exteraless.settings.OpenExteraOtherActivity)) {
                fragment.presentFragment(new app.exteraless.settings.OpenExteraOtherActivity());
            }
        });
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(-1, dp(50));
        closeLp.topMargin = dp(18);
        root.addView(close, closeLp);

        org.telegram.tgnet.TLRPC.User selfUser = UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser();
        HeartGramBadges.Info selfInfo = HeartGramBadges.infoFor(selfUser);
        if ((canEdit || previewDocument == 0) && selfInfo != null && !"client".equals(selfInfo.kind)) {
            TextView edit = text(context, org.telegram.messenger.LocaleController.getString(R.string.HGBadgeEdit), 15, true,
                    Theme.getColor(Theme.key_featuredStickers_addButton), Gravity.CENTER);
            edit.setPadding(dp(12), dp(12), dp(12), dp(4));
            edit.setOnClickListener(v -> {
                sheet.dismiss();
                HeartGramEditor.open(fragment);
            });
            root.addView(edit, new LinearLayout.LayoutParams(-1, -2));
        }

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
                null, true));
        steps.addView(step(context, R.drawable.msg_photos, tr("Отправить чек", "Send the receipt"),
                tr("Пришлите скриншот перевода в личные сообщения %s. Должны быть видны сумма, дата и время.",
                        "Send a screenshot of the transfer in a private message to %s. The amount, date and time must be visible."),
                contact, false));
        steps.addView(step(context, R.drawable.msg_reactions2, tr("Получить значок", "Get the badge"),
                cool
                        ? tr("Когда платёж проверен, появится изменяемый значок. Его можно менять: выберите любое эмодзи и свою подпись (жирный текст, ссылки, @username). Другие пользователи HeartGram увидят его у вашего имени.",
                        "Once the payment is checked you get an editable badge. You can change it any time: pick any emoji and your own caption (bold text, links, @username). Other HeartGram users see it next to your name.")
                        : tr("Когда платёж проверен, значок появится в вашем профиле и у имени в чатах. Его увидят другие пользователи HeartGram.",
                        "Once the payment is checked the badge appears in your profile and next to your name in chats. Other HeartGram users will see it."),
                null, false));
    }

    private static View step(Context context, int iconRes, String title, String body, final String contact, boolean ton) {
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
        if (!TextUtils.isEmpty(contact)) {
            // ник стоит прямо в предложении: жирный и нажимается
            int at = body.indexOf("%s");
            android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder(body.replace("%s", contact));
            if (at >= 0) {
                sb.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), at, at + contact.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new android.text.style.ForegroundColorSpan(Theme.getColor(Theme.key_dialogTextBlack)), at, at + contact.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new android.text.style.ClickableSpan() {
                    @Override
                    public void onClick(View widget) {
                        org.telegram.messenger.browser.Browser.openUrl(context, "https://t.me/" + contact.replace("@", ""));
                    }

                    @Override
                    public void updateDrawState(android.text.TextPaint ds) {
                        ds.setUnderlineText(false);
                    }
                }, at, at + contact.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            text.setText(sb);
            text.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        } else if (ton) {
            // «≈ 3.68 TON» с иконкой TON, как на ПК
            text.setText(org.telegram.ui.ChannelMonetizationLayout.replaceTON(body, text.getPaint(), 1f, false));
        }
        col.addView(text);
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
