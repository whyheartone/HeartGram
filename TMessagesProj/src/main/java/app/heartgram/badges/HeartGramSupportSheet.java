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

        // 3D-сердце HeartGram без блёсток; если открыли значок поддержавшего с кастомным эмодзи — показываем его.
        final FrameLayout art = new FrameLayout(context);
        final int accentColor = Theme.getColor(Theme.key_featuredStickers_addButton, fragment.getResourceProvider());
        long shown = previewDocument;
        if (shown != 0 && !HeartGramBadges.isDefaultHeartDocument(shown)) {
            final HeartGramBadgeSheet.StarsView stars = HeartGramBadgeSheet.createStarsView(context, accentColor, 44, 108);
            art.addView(stars, new FrameLayout.LayoutParams(-1, -1));
            art.addView(HeartGramBadgeSheet.createEmojiView(context, shown, stars::setColor), new FrameLayout.LayoutParams(dp(84), dp(84), Gravity.CENTER));
        } else {
            art.addView(HeartGramBadgeSheet.createHeart3DView(context, null, fragment.getResourceProvider()), new FrameLayout.LayoutParams(dp(128), dp(128), Gravity.CENTER));
        }
        LinearLayout.LayoutParams artLp = new LinearLayout.LayoutParams(dp(230), dp(146));
        artLp.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(art, artLp);

        TextView title = text(context, tr("Поддержать ", "Support ") + HeartGramBadges.projectName(), 20, true,
                Theme.getColor(Theme.key_dialogTextBlack), Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.topMargin = dp(8);
        root.addView(title, titleLp);

        TextView about = text(context, tr("Донат идёт на развитие клиента. Тем, кто поддержал, выдаётся уникальный значок рядом с именем.",
                "Donations go to the client's development. Supporters get a unique badge next to your name."),
                14, false, Theme.getColor(Theme.key_dialogTextGray2), Gravity.CENTER);
        LinearLayout.LayoutParams aboutLp = new LinearLayout.LayoutParams(-1, -2);
        aboutLp.topMargin = dp(4);
        root.addView(about, aboutLp);

        // переключатель уровня со скользящей пилюлей и плавной сменой текстов на месте
        final boolean[] cool = {startCool};
        final FrameLayout tiersTrack = new FrameLayout(context);
        GradientDrawable trackBg = new GradientDrawable();
        trackBg.setCornerRadius(dp(20));
        trackBg.setColor(Color.argb(34, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)));
        tiersTrack.setBackground(trackBg);
        tiersTrack.setPadding(dp(3), dp(3), dp(3), dp(3));

        final View tierIndicator = new View(context);
        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setCornerRadius(dp(17));
        pillBg.setColor(accentColor);
        tierIndicator.setBackground(pillBg);
        tiersTrack.addView(tierIndicator, new FrameLayout.LayoutParams(0, dp(34), Gravity.START | Gravity.CENTER_VERTICAL));

        final LinearLayout tiers = new LinearLayout(context);
        tiers.setOrientation(LinearLayout.HORIZONTAL);
        final TextView tierBasic = chip(context, tr("Значок", "Badge"));
        final TextView tierCool = chip(context, tr("Изменяемый значок", "Editable badge"));
        tiers.addView(tierBasic, new LinearLayout.LayoutParams(0, dp(34), 1f));
        tiers.addView(tierCool, new LinearLayout.LayoutParams(0, dp(34), 1f));
        tiersTrack.addView(tiers, new FrameLayout.LayoutParams(-1, dp(34)));

        LinearLayout.LayoutParams tiersLp = new LinearLayout.LayoutParams(-1, dp(40));
        tiersLp.topMargin = dp(16);
        root.addView(tiersTrack, tiersLp);

        final LinearLayout steps = new LinearLayout(context);
        steps.setOrientation(LinearLayout.VERTICAL);
        root.addView(steps, new LinearLayout.LayoutParams(-1, -2));

        final String contact = HeartGramBadges.supportContactOrDefault();
        final String step1Basic = tr("Переведите " + HeartGramBadges.priceText(false) + " на любой из реквизитов: они в настройках HeartGram, раздел «Другое».",
                "Send " + HeartGramBadges.priceText(false) + " to any of the options: they are in HeartGram settings, section “Other”.");
        final String step1Cool = tr("Переведите " + HeartGramBadges.priceText(true) + " на любой из реквизитов: они в настройках HeartGram, раздел «Другое».",
                "Send " + HeartGramBadges.priceText(true) + " to any of the options: they are in HeartGram settings, section “Other”.");
        final String step3Basic = tr("Когда платёж проверен, значок появится в вашем профиле и у имени в чатах. Его увидят другие пользователи HeartGram.",
                "Once the payment is checked the badge appears in your profile and next to your name in chats. Other HeartGram users will see it.");
        final String step3Cool = tr("Когда платёж проверен, появится изменяемый значок. Его можно менять: выберите любое эмодзи и свою подпись (жирный текст, ссылки, @username). Другие пользователи HeartGram увидят его у вашего имени.",
                "Once the payment is checked you get an editable badge. You can change it any time: pick any emoji and your own caption (bold text, links, @username). Other HeartGram users see it next to your name.");

        final StepHolder step1 = createStepHolder(context, R.drawable.filter_money, tr("Сделать донат", "Make a donation"), step1Basic, step1Cool, null, true);
        final StepHolder step2 = createStepHolder(context, R.drawable.msg_photos, tr("Отправить чек", "Send the receipt"),
                tr("Пришлите скриншот перевода в личные сообщения %s. Должны быть видны сумма, дата и время.",
                        "Send a screenshot of the transfer in a private message to %s. The amount, date and time must be visible."),
                null, contact, false);
        final StepHolder step3 = createStepHolder(context, R.drawable.msg_reactions2, tr("Получить значок", "Get the badge"), step3Basic, step3Cool, null, false);
        steps.addView(step1.row);
        steps.addView(step2.row);
        steps.addView(step3.row);

        final float[] indicatorPos = {startCool ? 1f : 0f};
        final android.animation.ValueAnimator[] chipAnim = {null};
        final Runnable updateIndicatorGeometry = () -> {
            final float t = indicatorPos[0];
            final int innerW = tiersTrack.getWidth() - tiersTrack.getPaddingLeft() - tiersTrack.getPaddingRight();
            if (innerW > 0) {
                final int halfW = innerW / 2;
                FrameLayout.LayoutParams ilp = (FrameLayout.LayoutParams) tierIndicator.getLayoutParams();
                if (ilp.width != halfW) {
                    ilp.width = halfW;
                    tierIndicator.setLayoutParams(ilp);
                }
                tierIndicator.setTranslationX(t * halfW);
            }
            final int activeText = Theme.getColor(Theme.key_featuredStickers_buttonText);
            final int inactiveText = Theme.getColor(Theme.key_dialogTextBlack);
            tierBasic.setTextColor(androidx.core.graphics.ColorUtils.blendARGB(activeText, inactiveText, t));
            tierCool.setTextColor(androidx.core.graphics.ColorUtils.blendARGB(inactiveText, activeText, t));
            step1.setMorphProgress(t);
            step3.setMorphProgress(t);
        };
        tiersTrack.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or_, ob) -> updateIndicatorGeometry.run());
        updateIndicatorGeometry.run();

        final Runnable animateSwitch = () -> {
            if (chipAnim[0] != null) {
                chipAnim[0].cancel();
            }
            chipAnim[0] = android.animation.ValueAnimator.ofFloat(indicatorPos[0], cool[0] ? 1f : 0f);
            chipAnim[0].setDuration(280);
            chipAnim[0].setInterpolator(org.telegram.ui.Components.CubicBezierInterpolator.EASE_OUT_QUINT);
            chipAnim[0].addUpdateListener(a -> {
                indicatorPos[0] = (float) a.getAnimatedValue();
                updateIndicatorGeometry.run();
            });
            chipAnim[0].start();

            art.animate().cancel();
            art.animate().scaleX(1.05f).scaleY(1.05f).setDuration(130)
                    .withEndAction(() -> art.animate().scaleX(1f).scaleY(1f).setDuration(210)
                            .setInterpolator(org.telegram.ui.Components.CubicBezierInterpolator.EASE_OUT_BACK).start())
                    .start();
        };
        tierBasic.setOnClickListener(v -> {
            if (cool[0]) {
                cool[0] = false;
                animateSwitch.run();
            }
        });
        tierCool.setOnClickListener(v -> {
            if (!cool[0]) {
                cool[0] = true;
                animateSwitch.run();
            }
        });

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

    private static final class StepHolder {
        final LinearLayout row;
        final TextView basicBody;
        final TextView coolBody;

        StepHolder(LinearLayout row, TextView basicBody, TextView coolBody) {
            this.row = row;
            this.basicBody = basicBody;
            this.coolBody = coolBody;
        }

        void setMorphProgress(float t) {
            if (coolBody == null) {
                return;
            }
            float shift = dp(6);
            basicBody.setAlpha(1f - t);
            basicBody.setTranslationY(-shift * t);
            coolBody.setAlpha(t);
            coolBody.setTranslationY(shift * (1f - t));
        }
    }

    private static StepHolder createStepHolder(Context context, int iconRes, String title, String basicText, String coolText, String contact, boolean ton) {
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

        // Оба текста лежат в одном FrameLayout фиксированной максимальной высоты, чтобы нижние кнопки не прыгали
        FrameLayout bodyStack = new FrameLayout(context);
        TextView basicTv = text(context, "", 14, false, Theme.getColor(Theme.key_dialogTextGray2), Gravity.START);
        basicTv.setPadding(0, dp(2), 0, 0);
        applyBodyText(context, basicTv, basicText, contact, ton);
        bodyStack.addView(basicTv, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        TextView coolTv = null;
        if (coolText != null) {
            coolTv = text(context, "", 14, false, Theme.getColor(Theme.key_dialogTextGray2), Gravity.START);
            coolTv.setPadding(0, dp(2), 0, 0);
            applyBodyText(context, coolTv, coolText, contact, ton);
            coolTv.setAlpha(0f);
            bodyStack.addView(coolTv, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        }
        col.addView(bodyStack, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        return new StepHolder(row, basicTv, coolTv);
    }

    private static void applyBodyText(Context context, TextView text, String body, final String contact, boolean ton) {
        if (!TextUtils.isEmpty(contact)) {
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
            text.setText(org.telegram.ui.ChannelMonetizationLayout.replaceTON(body, text.getPaint(), 1f, false));
            text.setMovementMethod(null);
        } else {
            text.setText(body);
            text.setMovementMethod(null);
        }
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
        return text(context, label, 14, true, Theme.getColor(Theme.key_dialogTextBlack), Gravity.CENTER);
    }
}
