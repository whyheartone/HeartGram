/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.player.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;
import androidx.core.widget.NestedScrollView;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.player.PlayerColors;
import com.exteragram.messenger.player.lyrics.LyricsProvider;
import com.exteragram.messenger.player.motion.CanvasRepository;
import com.exteragram.messenger.player.motion.YandexCanvasRepository;
import com.exteragram.messenger.utils.ui.material.ColorTones;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BotWebViewVibrationEffect;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import app.exteraless.appearance.AppearanceConfig;
import app.exteraless.player.PlayerIcon;

public class PlayerSettingsSheet extends BottomSheet {

    public interface Delegate {
        default void onCoverColorsChanged() {
        }

        default void onMotionCoversChanged() {
        }

        default void onCanvasChanged() {
        }

        default void onRhythmBackgroundChanged() {
        }

        default void onLyricsOnlineChanged() {
        }

        default void onLyricsProvidersChanged() {
        }

        default void onLyricsProgressChanged() {
        }

        default void onLyricsAnimStyleChanged() {
        }

        default void onLyricsSubtitleChanged() {
        }
    }

    private interface Part {
        void bind(SheetPalette palette);

        void refresh();
    }

    private interface BoolSupplier {
        boolean get();
    }

    private interface BoolConsumer {
        void accept(boolean value);
    }

    private static int selectedTab = PlayerSettingsPreviewView.MODE_APPEARANCE;

    private final Delegate delegate;
    private final SwitchingResourcesProvider colors;
    private final SheetPalette palette = new SheetPalette();
    private final ArrayList<Part> parts = new ArrayList<>();
    private final LinearLayout[] sections = new LinearLayout[3];

    private final LinearLayout rootLayout;
    private final FrameLayout headerLayout;
    private final TextView titleView;
    private final ImageView closeButton;
    private final PlayerIcon closeIcon = PlayerIcon.stroke(PlayerIcon.CLOSE, 20);
    private final NestedScrollView scrollView;
    private final LinearLayout contentContainer;
    private final PlayerSettingsPreviewView previewView;
    private Chip yandexLogoutChip;

    public PlayerSettingsSheet(Context context, BaseFragment fragment, Theme.ResourcesProvider resourcesProvider, Delegate delegate) {
        this(context, fragment, new SwitchingResourcesProvider(resourcesProvider), delegate);
    }

    private PlayerSettingsSheet(Context context, BaseFragment fragment, SwitchingResourcesProvider colors, Delegate delegate) {
        super(context, false, colors);
        this.colors = colors;
        this.delegate = delegate != null ? delegate : new Delegate() {};

        setApplyTopPadding(false);
        setApplyBottomPadding(false);
        occupyNavigationBar = true;
        allowNestedScroll = true;

        rootLayout = new LinearLayout(context);
        rootLayout.setOrientation(LinearLayout.VERTICAL);

        headerLayout = new FrameLayout(context);
        titleView = new TextView(context);
        titleView.setText(getString(R.string.PlayerSettings));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        headerLayout.addView(titleView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL | Gravity.START, 20, 0, 56, 0));

        closeButton = new ImageView(context);
        closeButton.setScaleType(ImageView.ScaleType.CENTER);
        closeButton.setImageDrawable(closeIcon);
        closeButton.setContentDescription(getString(R.string.Close));
        closeButton.setOnClickListener(v -> dismiss());
        headerLayout.addView(closeButton, LayoutHelper.createFrame(
                40, 40, Gravity.CENTER_VERTICAL | Gravity.END, 0, 0, 10, 0));
        rootLayout.addView(headerLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 52));

        Segmented tabs = new Segmented(context, new String[]{
                getString(R.string.Appearance),
                getString(R.string.PlayerLyrics),
                getString(R.string.OEPlayerStyleMini)
        }, () -> selectedTab, this::selectTab);
        parts.add(tabs);
        rootLayout.addView(tabs, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 40, 16, 2, 16, 8));

        scrollView = new NestedScrollView(context);
        scrollView.setClipToPadding(false);
        scrollView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        nestedScrollChild = scrollView;

        contentContainer = new LinearLayout(context);
        contentContainer.setOrientation(LinearLayout.VERTICAL);
        contentContainer.setPadding(0, dp(2), 0, dp(16) + AndroidUtilities.navigationBarHeight);

        previewView = new PlayerSettingsPreviewView(context, colors);
        contentContainer.addView(previewView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 4, 0, 4, 8));

        for (int i = 0; i < sections.length; i++) {
            sections[i] = new LinearLayout(context);
            sections[i].setOrientation(LinearLayout.VERTICAL);
            contentContainer.addView(sections[i], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        buildAppearanceSection(context);
        buildLyricsSection(context);
        buildMiniSection(context);

        scrollView.addView(contentContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        rootLayout.addView(scrollView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

        setCustomView(rootLayout);
        selectTab(selectedTab, false);
        applyColors();
    }

    private void buildAppearanceSection(Context context) {
        final Md3Card card = createCard(context, getString(R.string.Appearance), sections[0]);
        final ChipGroup chips = createChipGroup(context, card);

        addChip(chips, getString(R.string.PlayerCoverColors), ExteraConfig::getPlayerCoverColors, value -> {
            ExteraConfig.setPlayerCoverColors(value);
            onSettingChanged(previewView::refresh, this.delegate::onCoverColorsChanged);
        });
        addChip(chips, getString(R.string.PlayerRhythmBackground), ExteraConfig::getPlayerRhythmBackground, value -> {
            ExteraConfig.setPlayerRhythmBackground(value);
            onSettingChanged(previewView::refresh, this.delegate::onRhythmBackgroundChanged);
        });
        addChip(chips, getString(R.string.PlayerParticles), ExteraConfig::getPlayerParticles, value -> {
            ExteraConfig.setPlayerParticles(value);
            onSettingChanged(previewView::refresh, this.delegate::onRhythmBackgroundChanged);
        });
        addChip(chips, getString(R.string.PlayerMotionCovers), ExteraConfig::getPlayerMotionCovers, value -> {
            ExteraConfig.setPlayerMotionCovers(value);
            onSettingChanged(previewView::refresh, this.delegate::onMotionCoversChanged);
        });

        if (CanvasRepository.isSupported()) {
            addChip(chips, getString(R.string.PlayerYandexCanvas),
                    () -> YandexCanvasRepository.isLoggedIn() && ExteraConfig.getPlayerYandexCanvas(),
                    value -> {
                        if (value && !YandexCanvasRepository.isLoggedIn()) {
                            YandexLoginDialog.show(getContext(), resourcesProvider, success -> {
                                if (isDismissed()) {
                                    if (success) {
                                        ExteraConfig.setPlayerYandexCanvas(true);
                                    }
                                } else if (success) {
                                    ExteraConfig.setPlayerYandexCanvas(true);
                                    refreshParts();
                                    previewView.refresh();
                                    delegate.onCanvasChanged();
                                } else {
                                    BulletinFactory.of(container, resourcesProvider)
                                            .createErrorBulletin(getString(R.string.PlayerYandexLoginFailed)).show();
                                    refreshParts();
                                }
                            });
                            return;
                        }
                        ExteraConfig.setPlayerYandexCanvas(value);
                        onSettingChanged(previewView::refresh, this.delegate::onCanvasChanged);
                    });

            yandexLogoutChip = new Chip(context, getString(R.string.PlayerYandexLogout), () -> false, v -> {
                new AlertDialog.Builder(getContext(), resourcesProvider)
                        .setTitle(getString(R.string.PlayerYandexLogout))
                        .setMessage(getString(R.string.PlayerYandexLogoutInfo))
                        .setPositiveButton(getString(R.string.LogOut), (dialog, which) -> {
                            YandexCanvasRepository.logout();
                            ExteraConfig.setPlayerYandexCanvas(false);
                            onSettingChanged(previewView::refresh, this.delegate::onCanvasChanged);
                        })
                        .setNegativeButton(getString(R.string.Cancel), null)
                        .makeRed(AlertDialog.BUTTON_POSITIVE)
                        .show();
            });
            yandexLogoutChip.setDanger(true);
            yandexLogoutChip.setVisibility(YandexCanvasRepository.isLoggedIn() ? View.VISIBLE : View.GONE);
            parts.add(yandexLogoutChip);
            chips.addView(yandexLogoutChip);
        }

        addCardInfo(card, getString(R.string.PlayerRhythmBackgroundInfo) + "\n" + getString(R.string.PlayerMotionCoversEnabled));
    }

    private void buildLyricsSection(Context context) {
        final Md3Card providersCard = createCard(context, getString(R.string.PlayerLyricsOnline), sections[1]);
        final ChipGroup onlineGroup = createChipGroup(context, providersCard);
        addDynamicChip(onlineGroup,
                () -> {
                    final LyricsProvider[] all = LyricsProvider.values();
                    final Set<String> enabled = ExteraConfig.getLyricsProviders();
                    int count = 0;
                    for (LyricsProvider p : all) {
                        if (enabled.contains(p.id)) {
                            count++;
                        }
                    }
                    return getString(R.string.PlayerLyricsOnline) + " (" + count + "/" + all.length + ")";
                },
                ExteraConfig::getLyricsOnline,
                value -> {
                    ExteraConfig.setLyricsOnline(value);
                    onSettingChanged(previewView::refresh, this.delegate::onLyricsOnlineChanged);
                });

        final ChipGroup providersGroup = createChipGroup(context, providersCard);
        for (LyricsProvider provider : LyricsProvider.values()) {
            addChip(providersGroup, provider.title,
                    () -> ExteraConfig.getLyricsProviders().contains(provider.id),
                    value -> toggleLyricsProvider(provider));
        }
        addCardInfo(providersCard, getString(R.string.PlayerLyricsOnlineInfo));

        final Md3Card progressCard = createCard(context, getString(R.string.PlayerLyricsProgress), sections[1]);
        addSegmented(progressCard, new String[]{
                getString(R.string.BlurOff),
                getString(R.string.PlayerLyricsProgressHorizontal),
                getString(R.string.PlayerLyricsProgressVertical)
        }, ExteraConfig::getLyricsProgress, index -> {
            ExteraConfig.setLyricsProgress(index);
            onSettingChanged(previewView::refresh, this.delegate::onLyricsProgressChanged);
        });
        addCardInfo(progressCard, getString(R.string.PlayerLyricsProgressInfo));

        final Md3Card animCard = createCard(context, getString(R.string.PlayerLyricsAnimStyle), sections[1]);
        addSegmented(animCard, new String[]{
                getString(R.string.PlayerLyricsAnimStyleExtera),
                getString(R.string.PlayerLyricsAnimStyleHeartGram)
        }, ExteraConfig::getLyricsAnimStyle, index -> {
            ExteraConfig.setLyricsAnimStyle(index);
            onSettingChanged(previewView::refresh, this.delegate::onLyricsAnimStyleChanged);
        });
        addCardInfo(animCard, getString(R.string.PlayerLyricsAnimStyleInfo));

        final Md3Card subtitleCard = createCard(context, getString(R.string.PlayerLyricsSubtitle), sections[1]);
        addSegmented(subtitleCard, new String[]{
                getString(R.string.BlurOff),
                getString(R.string.PlayerLyricsSubtitleTranslation),
                getString(R.string.PlayerLyricsSubtitleRomanization)
        }, ExteraConfig::getLyricsSubtitle, index -> {
            ExteraConfig.setLyricsSubtitle(index);
            onSettingChanged(previewView::refresh, this.delegate::onLyricsSubtitleChanged);
        });
        addCardInfo(subtitleCard, getString(R.string.PlayerLyricsSubtitleInfo));
    }

    private void buildMiniSection(Context context) {
        final Md3Card card = createCard(context, getString(R.string.OEPlayerStyleMini), sections[2]);
        final ChipGroup chips = createChipGroup(context, card);

        addChip(chips, getString(R.string.PlayerCoverColorsMini), ExteraConfig::getPlayerMiniCoverColors, value -> {
            ExteraConfig.setPlayerMiniCoverColors(value);
            onSettingChanged(previewView::refresh, this.delegate::onCoverColorsChanged);
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.reloadInterface);
        });
        addChip(chips, getString(R.string.OEAppearanceMd3MiniPlayer), AppearanceConfig.md3MiniPlayer::Bool, value -> {
            AppearanceConfig.md3MiniPlayer.setConfigBool(value);
            previewView.refresh();
            refreshParts();
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.reloadInterface);
        });
        addChip(chips, getString(R.string.OEAppearanceMd3Player), AppearanceConfig.md3Player::Bool, value -> {
            AppearanceConfig.md3Player.setConfigBool(value);
            previewView.refresh();
            refreshParts();
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.reloadInterface);
        });

        addCardInfo(card, getString(R.string.OEPlayerMiniMoveHint));
    }

    private Md3Card createCard(Context context, String title, LinearLayout parent) {
        final Md3Card card = new Md3Card(context, title);
        parts.add(card);
        parent.addView(card, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16, 4, 16, 8));
        return card;
    }

    private ChipGroup createChipGroup(Context context, Md3Card card) {
        final ChipGroup group = new ChipGroup(context);
        card.body.addView(group, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 10, 0, 2));
        return group;
    }

    private Chip addChip(ChipGroup group, String label, BoolSupplier getter, BoolConsumer onChange) {
        final Chip chip = new Chip(group.getContext(), label, getter, onChange);
        parts.add(chip);
        group.addView(chip);
        return chip;
    }

    private void addDynamicChip(ChipGroup group, java.util.function.Supplier<String> labelSupplier, BoolSupplier getter, BoolConsumer onChange) {
        final Chip chip = new Chip(group.getContext(), labelSupplier.get(), getter, value -> {
            onChange.accept(value);
            refreshParts();
        }) {
            @Override
            public void refresh() {
                setText(labelSupplier.get());
                super.refresh();
            }
        };
        parts.add(chip);
        group.addView(chip);
    }

    private void addSegmented(Md3Card card, String[] options, IntSupplier getter, IntConsumer onSelect) {
        final Segmented seg = new Segmented(card.getContext(), options, getter, onSelect);
        seg.inset = true;
        parts.add(seg);
        card.body.addView(seg, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 40, 0, 10, 0, 2));
    }

    private void addCardInfo(Md3Card card, String text) {
        final CaptionView caption = new CaptionView(card.getContext(), text);
        parts.add(caption);
        card.body.addView(caption, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 10, 0, 0));
    }

    private void toggleLyricsProvider(LyricsProvider provider) {
        final Set<String> enabled = new HashSet<>(ExteraConfig.getLyricsProviders());
        if (enabled.contains(provider.id)) {
            if (enabled.size() <= 1) {
                BotWebViewVibrationEffect.APP_ERROR.vibrate();
                for (Part p : parts) {
                    if (p instanceof Chip && TextUtils.equals(((Chip) p).getText(), provider.title)) {
                        AndroidUtilities.shakeViewSpring((View) p, -3);
                        break;
                    }
                }
                return;
            }
            enabled.remove(provider.id);
        } else {
            enabled.add(provider.id);
        }
        ExteraConfig.setLyricsProviders(enabled);
        onSettingChanged(previewView::refresh, delegate::onLyricsProvidersChanged);
    }

    private void onSettingChanged(Runnable afterPreview, Runnable notifyDelegate) {
        if (yandexLogoutChip != null) {
            yandexLogoutChip.setVisibility(YandexCanvasRepository.isLoggedIn() ? View.VISIBLE : View.GONE);
        }
        refreshParts();
        afterPreview.run();
        notifyDelegate.run();
    }

    private void selectTab(int tab) {
        selectTab(tab, true);
    }

    private void selectTab(int tab, boolean animate) {
        selectedTab = Math.max(0, Math.min(sections.length - 1, tab));
        if (animate && rootLayout.isLaidOut()) {
            TransitionManager.beginDelayedTransition(rootLayout, new AutoTransition().setDuration(180));
        }
        for (int i = 0; i < sections.length; i++) {
            sections[i].setVisibility(i == selectedTab ? View.VISIBLE : View.GONE);
        }
        if (previewView != null) {
            previewView.setMode(selectedTab);
        }
        refreshParts();
    }

    public void setMiniPreviewOnly(boolean miniOnly) {
        if (miniOnly) {
            selectTab(PlayerSettingsPreviewView.MODE_MINI, false);
        }
    }

    public void updateColors(Theme.ResourcesProvider provider) {
        if (colors.current == provider) {
            return;
        }
        colors.current = provider;
        applyColors();
    }

    private void applyColors() {
        palette.update(colors);
        setBackgroundColor(palette.pageBg);
        fixNavigationBar(palette.pageBg);
        titleView.setTextColor(palette.onSurface);
        closeIcon.setColor(palette.onSurfaceVariant);
        closeButton.setBackground(Theme.createSelectorDrawable(palette.ripple(), 1, dp(20)));
        if (yandexLogoutChip != null) {
            yandexLogoutChip.setVisibility(CanvasRepository.isSupported() && YandexCanvasRepository.isLoggedIn() ? View.VISIBLE : View.GONE);
        }
        for (Part part : parts) {
            part.bind(palette);
        }
        if (previewView != null) {
            previewView.refresh();
        }
        if (containerView != null) {
            containerView.invalidate();
        }
    }

    private void refreshParts() {
        for (Part part : parts) {
            part.refresh();
        }
    }

    @Override
    protected boolean canDismissWithSwipe() {
        return scrollView == null || scrollView.getScrollY() == 0;
    }

    private static final class SheetPalette {
        int pageBg;
        int surfaceHigh;
        int surfaceContainer;
        int onSurface;
        int onSurfaceVariant;
        int primary;
        int onPrimary;
        int secondaryContainer;
        int onSecondaryContainer;
        int outlineVariant;
        int dangerContainer;
        int dangerText;
        boolean dark;

        void update(Theme.ResourcesProvider provider) {
            final PlayerColors base = PlayerColors.fromTheme(provider);
            pageBg = Theme.getColor(Theme.key_windowBackgroundGray, provider);
            surfaceHigh = Theme.getColor(Theme.key_windowBackgroundWhite, provider);
            dark = !ColorTones.isLightSurface(surfaceHigh);
            surfaceContainer = ColorUtils.blendARGB(surfaceHigh, pageBg, dark ? 0.55f : 0.45f);
            onSurface = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, provider);
            onSurfaceVariant = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, provider);
            primary = Theme.getColor(Theme.key_featuredStickers_addButton, provider);
            onPrimary = Theme.getColor(Theme.key_featuredStickers_buttonText, provider);
            secondaryContainer = base.tonal;
            onSecondaryContainer = base.onTonal;
            outlineVariant = ColorUtils.setAlphaComponent(onSurfaceVariant, 0x42);
            dangerContainer = ColorUtils.setAlphaComponent(0xFFE53935, dark ? 0x38 : 0x22);
            dangerText = dark ? 0xFFFF8A80 : 0xFFC62828;
        }

        int ripple() {
            return ColorUtils.setAlphaComponent(onSurface, 0x1F);
        }
    }

    private static final class Md3Card extends LinearLayout implements Part {
        private final TextView titleView;
        private final GradientDrawable background = new GradientDrawable();
        final LinearLayout body;

        Md3Card(Context context, String title) {
            super(context);
            setOrientation(VERTICAL);
            setPadding(dp(16), dp(14), dp(16), dp(14));
            background.setCornerRadius(dp(20));
            setBackground(background);

            titleView = new TextView(context);
            titleView.setText(title);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            titleView.setTypeface(AndroidUtilities.bold());
            titleView.setSingleLine(true);
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            body = new LinearLayout(context);
            body.setOrientation(VERTICAL);
            addView(body, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        @Override
        public void bind(SheetPalette p) {
            background.setColor(p.surfaceHigh);
            titleView.setTextColor(p.primary);
        }

        @Override
        public void refresh() {
        }
    }

    private static final class CaptionView extends TextView implements Part {
        CaptionView(Context context, String text) {
            super(context);
            setText(text);
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
            setLineSpacing(dp(2), 1f);
        }

        @Override
        public void bind(SheetPalette p) {
            setTextColor(p.onSurfaceVariant);
        }

        @Override
        public void refresh() {
        }
    }

    private static final class Segmented extends LinearLayout implements Part {
        private final TextView[] items;
        private final GradientDrawable[] backgrounds;
        private final IntSupplier getter;
        private SheetPalette palette;
        boolean inset;

        Segmented(Context context, String[] options, IntSupplier getter, IntConsumer onSelect) {
            super(context);
            this.getter = getter;
            setOrientation(HORIZONTAL);
            items = new TextView[options.length];
            backgrounds = new GradientDrawable[options.length];
            final float outer = dp(20);
            final float inner = dp(6);
            for (int i = 0; i < options.length; i++) {
                final int index = i;
                final TextView item = new TextView(context);
                item.setText(options[i]);
                item.setGravity(Gravity.CENTER);
                item.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
                item.setTypeface(AndroidUtilities.bold());
                item.setSingleLine(true);
                item.setEllipsize(TextUtils.TruncateAt.END);
                item.setPadding(dp(8), 0, dp(8), 0);
                final GradientDrawable bg = new GradientDrawable();
                final float l = i == 0 ? outer : inner;
                final float r = i == options.length - 1 ? outer : inner;
                bg.setCornerRadii(new float[]{l, l, r, r, r, r, l, l});
                backgrounds[i] = bg;
                item.setBackground(bg);
                item.setOnClickListener(v -> {
                    if (getter.getAsInt() != index) {
                        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                        onSelect.accept(index);
                        refresh();
                    }
                });
                items[i] = item;
                addView(item, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f, i == 0 ? 0 : 2, 0, 0, 0));
            }
        }

        @Override
        public void bind(SheetPalette p) {
            palette = p;
            refresh();
        }

        @Override
        public void refresh() {
            if (palette == null) {
                return;
            }
            final int selected = getter.getAsInt();
            for (int i = 0; i < items.length; i++) {
                final boolean on = i == selected;
                backgrounds[i].setColor(on ? palette.secondaryContainer : (inset ? palette.surfaceContainer : palette.surfaceHigh));
                items[i].setTextColor(on ? palette.onSecondaryContainer : palette.onSurfaceVariant);
                items[i].setSelected(on);
            }
        }
    }

    private static final class ChipGroup extends ViewGroup {
        ChipGroup(Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            final int width = MeasureSpec.getSize(widthMeasureSpec);
            final int gap = dp(8);
            int x = 0;
            int y = 0;
            int rowHeight = 0;
            for (int i = 0; i < getChildCount(); i++) {
                final View child = getChildAt(i);
                if (child.getVisibility() == GONE) {
                    continue;
                }
                child.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
                        MeasureSpec.makeMeasureSpec(dp(34), MeasureSpec.EXACTLY));
                final int childWidth = child.getMeasuredWidth();
                if (x > 0 && x + childWidth > width) {
                    x = 0;
                    y += rowHeight + gap;
                    rowHeight = 0;
                }
                x += childWidth + gap;
                rowHeight = Math.max(rowHeight, child.getMeasuredHeight());
            }
            setMeasuredDimension(width, y + rowHeight);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            final int width = r - l;
            final int gap = dp(8);
            int x = 0;
            int y = 0;
            int rowHeight = 0;
            for (int i = 0; i < getChildCount(); i++) {
                final View child = getChildAt(i);
                if (child.getVisibility() == GONE) {
                    continue;
                }
                final int childWidth = child.getMeasuredWidth();
                final int childHeight = child.getMeasuredHeight();
                if (x > 0 && x + childWidth > width) {
                    x = 0;
                    y += rowHeight + gap;
                    rowHeight = 0;
                }
                final int left = LocaleController.isRTL ? width - x - childWidth : x;
                child.layout(left, y, left + childWidth, y + childHeight);
                x += childWidth + gap;
                rowHeight = Math.max(rowHeight, childHeight);
            }
        }
    }

    private static class Chip extends TextView implements Part {
        private final BoolSupplier getter;
        private final GradientDrawable background = new GradientDrawable();
        private final PlayerIcon check = PlayerIcon.stroke(PlayerIcon.CHECK, 18);
        private SheetPalette palette;
        private boolean danger;

        Chip(Context context, String text, BoolSupplier getter, BoolConsumer onChange) {
            super(context);
            this.getter = getter;
            setText(text);
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13.5f);
            setTypeface(AndroidUtilities.bold());
            setGravity(Gravity.CENTER_VERTICAL);
            setSingleLine(true);
            setEllipsize(TextUtils.TruncateAt.END);
            setCompoundDrawablePadding(dp(6));
            background.setCornerRadius(dp(10));
            setOnClickListener(v -> {
                final boolean value = !getter.get();
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                onChange.accept(value);
                refresh();
            });
        }

        void setDanger(boolean danger) {
            this.danger = danger;
        }

        @Override
        public void bind(SheetPalette p) {
            palette = p;
            final GradientDrawable mask = new GradientDrawable();
            mask.setCornerRadius(dp(10));
            mask.setColor(0xffffffff);
            setBackground(new RippleDrawable(ColorStateList.valueOf(p.ripple()), background, mask));
            refresh();
        }

        @Override
        public void refresh() {
            if (palette == null) {
                return;
            }
            if (danger) {
                setSelected(false);
                background.setColor(palette.dangerContainer);
                background.setStroke(0, 0);
                setTextColor(palette.dangerText);
                setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null, null);
                setPaddingRelative(dp(14), 0, dp(14), 0);
                return;
            }
            final boolean on = getter.get();
            setSelected(on);
            background.setColor(on ? palette.secondaryContainer : 0);
            background.setStroke(on ? 0 : dp(1), palette.outlineVariant);
            final int textColor = on ? palette.onSecondaryContainer : palette.onSurfaceVariant;
            setTextColor(textColor);
            if (on) {
                check.setColor(textColor);
                setCompoundDrawablesRelativeWithIntrinsicBounds(check, null, null, null);
                setPaddingRelative(dp(10), 0, dp(14), 0);
            } else {
                setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null, null);
                setPaddingRelative(dp(14), 0, dp(14), 0);
            }
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setClassName("android.widget.CheckBox");
            info.setCheckable(true);
            info.setChecked(getter.get());
        }
    }

    private static final class SwitchingResourcesProvider implements Theme.ResourcesProvider {
        Theme.ResourcesProvider current;

        SwitchingResourcesProvider(Theme.ResourcesProvider current) {
            this.current = current;
        }

        @Override
        public int getColor(int key) {
            return current != null ? current.getColor(key) : Theme.getColor(key);
        }

        @Override
        public int getColorOrDefault(int key) {
            return current != null ? current.getColorOrDefault(key) : Theme.getColor(key);
        }

        @Override
        public int getCurrentColor(int key) {
            return current != null ? current.getCurrentColor(key) : Theme.getColor(key);
        }

        @Override
        public void setAnimatedColor(int key, int color) {
            if (current != null) {
                current.setAnimatedColor(key, color);
            }
        }

        @Override
        public Drawable getDrawable(String drawableKey) {
            return current != null ? current.getDrawable(drawableKey) : Theme.getThemeDrawable(drawableKey);
        }

        @Override
        public Paint getPaint(String paintKey) {
            return current != null ? current.getPaint(paintKey) : Theme.getThemePaint(paintKey);
        }

        @Override
        public boolean hasGradientService() {
            return current != null ? current.hasGradientService() : Theme.hasGradientService();
        }

        @Override
        public boolean isDark() {
            return current != null ? current.isDark() : Theme.isCurrentThemeDark();
        }

        @Override
        public boolean isMonet() {
            return current != null ? current.isMonet() : Theme.isCurrentThemeMonet();
        }

        @Override
        public void applyServiceShaderMatrix(int w, int h, float translationX, float translationY) {
            if (current != null) {
                current.applyServiceShaderMatrix(w, h, translationX, translationY);
            } else {
                Theme.applyServiceShaderMatrix(w, h, translationX, translationY);
            }
        }

        @Override
        public ColorFilter getAnimatedEmojiColorFilter() {
            return current != null ? current.getAnimatedEmojiColorFilter() : Theme.getAnimatedEmojiColorFilter(null);
        }
    }
}
