package app.heartgram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.MainTabsLayout;

import app.exteraless.appearance.AppearanceConfig;
import app.exteraless.appearance.AvatarCornersSeekBar;
import app.heartgram.HeartGramLabels;
import app.heartgram.HeartGramPrefs;
import app.heartgram.tiles.ChatTiles;
import app.heartgram.tiles.TilesPreviewView;
import xyz.nextalone.nagram.NaConfig;

/**
 * Экран «Material Design 3»: сверху живой предпросмотр плиток на настоящих чатах (только на устройстве),
 * ниже настройка плиток (скругление, зазор, яркость, оттенок) и переключатели остальных стилей MD3.
 */
public class HeartGramMd3Activity extends BaseFragment {

    private static final int STYLE_MD3 = 2;
    private static final int SWITCH_STYLE_M3 = org.telegram.ui.Components.Switch.SWITCH_STYLE_M3;

    private LinearLayout content;
    private LinearLayout tileControls;
    private TilesPreviewView preview;
    private final Runnable notifyList = () ->
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.reloadInterface);

    private static String tr(String ru, String en) {
        return HeartGramLabels.tr(ru, en);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Material Design 3");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout frame = new FrameLayout(context);
        frame.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        frame.addView(scroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        fragmentView = frame;

        build(context);
        return fragmentView;
    }

    private void build(Context context) {
        // --- предпросмотр ---
        content.addView(header(context, tr("Предпросмотр", "Preview")));
        preview = new TilesPreviewView(context);
        content.addView(preview, new LinearLayout.LayoutParams(-1, -2));
        content.addView(new ShadowSectionCell(context));

        // --- плитки ---
        content.addView(header(context, tr("Чаты плитками", "Chat tiles")));
        content.addView(check(context, tr("Показывать чаты плитками", "Show chats as tiles"), ChatTiles.enabled(), true, on -> {
            HeartGramPrefs.prefs().edit().putBoolean("tiles", on).apply();
            afterTilesChanged();
            updateTileControls();
        }));

        tileControls = new LinearLayout(context);
        tileControls.setOrientation(LinearLayout.VERTICAL);
        tileControls.addView(slider(context, tr("Скругление", "Corner radius"), 8, 28, ChatTiles.radius(),
                "8", "28", "dp", v -> {
                    HeartGramPrefs.prefs().edit().putInt("tiles_radius", v).apply();
                    afterTilesChanged();
                }));
        tileControls.addView(slider(context, tr("Зазор между плитками", "Gap between tiles"), 0, 8, ChatTiles.gap(),
                tr("Нет", "None"), "8", "dp", v -> {
                    HeartGramPrefs.prefs().edit().putInt("tiles_gap2", v).apply();
                    afterTilesChanged();
                }));
        tileControls.addView(slider(context, tr("Яркость", "Brightness"), -30, 30, ChatTiles.brightness(),
                tr("Темнее", "Darker"), tr("Светлее", "Lighter"), "%", v -> {
                    HeartGramPrefs.prefs().edit().putInt("tiles_bright", v).apply();
                    afterTilesChanged();
                }));
        tileControls.addView(check(context, tr("Оттенок акцентного цвета", "Accent color tint"), ChatTiles.tint() == 1, false, on -> {
            HeartGramPrefs.prefs().edit().putInt("tiles_tint", on ? 1 : 0).apply();
            afterTilesChanged();
        }));
        content.addView(tileControls, new LinearLayout.LayoutParams(-1, -2));
        updateTileControls();
        content.addView(new ShadowSectionCell(context));

        // --- остальные стили MD3 ---
        content.addView(header(context, tr("Остальные стили", "Other styles")));
        content.addView(check(context, tr("Меню блоками (ответить, ⋮)", "Menus as blocks (reply, ⋮)"), ChatTiles.menus(), true, on -> {
            HeartGramPrefs.prefs().edit().putBoolean("md3_menus", on).apply();
        }));
        content.addView(check(context, getString(R.string.OEAppearanceNewLoadingStyle), AppearanceConfig.newLoadingStyle.Bool(), true, on -> {
            AppearanceConfig.newLoadingStyle.setConfigBool(on);
            rebuildOthers();
        }));
        content.addView(check(context, getString(R.string.OEAppearanceSliderStyle),
                NaConfig.INSTANCE.getSliderStyle().Int() == STYLE_MD3, true, on -> {
                    NaConfig.INSTANCE.getSliderStyle().setConfigInt(on ? STYLE_MD3 : 0);
                    rebuildOthers();
                }));
        content.addView(check(context, getString(R.string.OEAppearanceSwitchStyle), isMd3Switch(NaConfig.INSTANCE.getSwitchStyle().Int()), true, on -> {
            NaConfig.INSTANCE.getSwitchStyle().setConfigInt(on ? SWITCH_STYLE_M3 : 0);
            rebuildOthers();
        }));
        content.addView(check(context, getString(R.string.OEAppearanceNewChatHeaderStyle), AppearanceConfig.newChatHeaderStyle.Bool(), true, on -> {
            AppearanceConfig.newChatHeaderStyle.setConfigBool(on);
            rebuildOthers();
        }));
        content.addView(check(context, getString(R.string.OEAppearanceNewNavigationBarStyle), AppearanceConfig.newNavigationBarStyle.Bool(), true, on -> {
            AppearanceConfig.newNavigationBarStyle.setConfigBool(on);
            if (on) {
                AppearanceConfig.iosNavigationBarStyle.setConfigBool(false);
                if (MainTabsLayout.isBottomNavigationFloating()) {
                    MainTabsLayout.setBottomNavigationMode(MainTabsLayout.BOTTOM_NAVIGATION_MODE_SHOW);
                }
            }
            rebuildOthers();
        }));
        content.addView(check(context, getString(R.string.OEAppearanceM3ListItems), AppearanceConfig.m3ListItems.Bool(), true, on -> {
            AppearanceConfig.m3ListItems.setConfigBool(on);
            AppearanceConfig.invalidateDividerStyle();
            Theme.applyCommonTheme();
            rebuildOthers();
        }));
        content.addView(check(context, getString(R.string.OEAppearanceMd3Player), AppearanceConfig.md3Player.Bool(), true, on -> {
            AppearanceConfig.md3Player.setConfigBool(on);
            rebuildOthers();
        }));
        content.addView(check(context, getString(R.string.OEAppearanceMd3MiniPlayer), AppearanceConfig.md3MiniPlayer.Bool(), false, on -> {
            AppearanceConfig.md3MiniPlayer.setConfigBool(on);
            rebuildOthers();
        }));
        content.addView(new ShadowSectionCell(context));
    }

    private static boolean isMd3Switch(int value) {
        return value == STYLE_MD3 || value == SWITCH_STYLE_M3;
    }

    private HeaderCell header(Context context, String text) {
        HeaderCell cell = new HeaderCell(context);
        cell.setText(text);
        cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        return cell;
    }

    private interface OnToggle {
        void run(boolean on);
    }

    private TextCheckCell check(Context context, String title, boolean checked, boolean divider, OnToggle toggle) {
        final TextCheckCell cell = new TextCheckCell(context);
        cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        cell.setTextAndCheck(title, checked, divider);
        cell.setOnClickListener(v -> {
            boolean on = !cell.isChecked();
            cell.setChecked(on);
            toggle.run(on);
        });
        return cell;
    }

    private AvatarCornersSeekBar slider(Context context, String title, int min, int max, int value, String left, String right,
                                        String suffix, AvatarCornersSeekBar.OnDrag drag) {
        AvatarCornersSeekBar bar = new AvatarCornersSeekBar(context, drag, min, max, title, left, right);
        bar.setValueSuffix(suffix);
        bar.setValue(value);
        bar.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        return bar;
    }

    private void updateTileControls() {
        if (tileControls != null) {
            tileControls.setAlpha(ChatTiles.enabled() ? 1f : 0.45f);
        }
    }

    /** Плитки изменились: обновить превью сразу, а список чатов под экраном пересчитать после паузы в перетаскивании. */
    private void afterTilesChanged() {
        if (preview != null) {
            preview.refresh();
        }
        AndroidUtilities.cancelRunOnUIThread(notifyList);
        AndroidUtilities.runOnUIThread(notifyList, 120);
    }

    /** Остальные стили читаются при отрисовке или создании вьюх: пересобираем чужие экраны, свои перерисовываем. */
    private void rebuildOthers() {
        if (parentLayout != null) {
            parentLayout.rebuildAllFragmentViews(false, false);
        }
        if (content != null) {
            invalidateAll(content);
        }
    }

    private static void invalidateAll(View view) {
        view.invalidate();
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                invalidateAll(group.getChildAt(i));
            }
        }
    }

    @Override
    public void onFragmentDestroy() {
        AndroidUtilities.cancelRunOnUIThread(notifyList);
        super.onFragmentDestroy();
    }
}
