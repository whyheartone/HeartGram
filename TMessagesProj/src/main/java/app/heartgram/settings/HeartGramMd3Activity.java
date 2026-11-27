package app.heartgram.settings;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.MainTabsLayout;

import app.exteraless.appearance.AppearanceConfig;
import app.exteraless.appearance.AvatarCornersSeekBar;
import app.heartgram.HeartGramLabels;
import app.heartgram.HeartGramPrefs;
import app.heartgram.tiles.ChatTiles;
import app.heartgram.tiles.TilesPreviewView;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;
import xyz.nextalone.nagram.NaConfig;

/**
 * Экран «Material Design 3»: предпросмотр, плитки чатов и переключатели стилей MD3.
 * Наследуется от {@link BaseNekoSettingsActivity}, поэтому использует карточки-секции MD3
 * и поддерживает копирование ссылок через t.me/HeartGramSettings/md3?r=... при долгом нажатии.
 */
public class HeartGramMd3Activity extends BaseNekoSettingsActivity {

    private static final int STYLE_MD3 = 2;
    private static final int SWITCH_STYLE_M3 = org.telegram.ui.Components.Switch.SWITCH_STYLE_M3;

    private static final int TYPE_PREVIEW = 50;
    private static final int TYPE_SLIDER_RADIUS = 51;
    private static final int TYPE_SLIDER_GAP = 52;
    private static final int TYPE_SLIDER_BRIGHT = 53;

    private int previewHeaderRow;
    private int previewRow;
    private int tilesHeaderRow;
    private int tilesEnabledRow;
    private int tilesRadiusRow;
    private int tilesGapRow;
    private int tilesBrightRow;
    private int tilesTintRow;
    private int tilesShadowRow;

    private int otherHeaderRow;
    private int md3MenusRow;
    private int md3LoadingRow;
    private int md3SliderRow;
    private int md3SwitchRow;
    private int md3ChatHeaderRow;
    private int md3NavBarRow;
    private int md3ListItemsRow;
    private int md3PlayerRow;
    private int md3MiniPlayerRow;
    private int md3ProfileActionsRow;
    private int md3NoGlassRow;
    private int otherShadowRow;

    private TilesPreviewView preview;
    private final Runnable notifyList = () ->
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.reloadInterface);

    private static String tr(String ru, String en) {
        return HeartGramLabels.tr(ru, en);
    }

    @Override
    protected String getKey() {
        return "exteraless_md3";
    }

    @Override
    protected String getActionBarTitle() {
        return "Material Design 3";
    }

    @Override
    protected void updateRows() {
        super.updateRows();
        previewHeaderRow = addRow();
        previewRow = addRow("preview");

        tilesHeaderRow = addRow();
        tilesEnabledRow = addRow("tiles");
        tilesRadiusRow = addRow("tiles_radius");
        tilesGapRow = addRow("tiles_gap");
        tilesBrightRow = addRow("tiles_bright");
        tilesTintRow = addRow("tiles_tint");
        tilesShadowRow = addRow();

        otherHeaderRow = addRow();
        md3MenusRow = addRow("md3_menus");
        md3LoadingRow = addRow("md3Loading");
        md3SliderRow = addRow("md3Slider");
        md3SwitchRow = addRow("md3Switch");
        md3ChatHeaderRow = addRow("md3ChatHeader");
        md3NavBarRow = addRow("md3NavBar");
        md3ListItemsRow = addRow("md3ListItems");
        md3PlayerRow = addRow("md3Player");
        md3MiniPlayerRow = addRow("md3MiniPlayer");
        md3ProfileActionsRow = addRow("md3ProfileActions");
        md3NoGlassRow = addRow("md3NoGlassBacking");
        otherShadowRow = addRow();
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == tilesEnabledRow) {
            boolean on = !ChatTiles.enabled();
            HeartGramPrefs.prefs().edit().putBoolean("tiles", on).apply();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(on);
            }
            afterTilesChanged();
            refreshTileControlAlphas();
        } else if (position == tilesTintRow) {
            boolean on = ChatTiles.tint() != 1;
            HeartGramPrefs.prefs().edit().putInt("tiles_tint", on ? 1 : 0).apply();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(on);
            }
            afterTilesChanged();
        } else if (position == md3MenusRow) {
            boolean on = !ChatTiles.menus();
            HeartGramPrefs.prefs().edit().putBoolean("md3_menus", on).apply();
            toggleCheck(view, on);
        } else if (position == md3LoadingRow) {
            boolean on = !AppearanceConfig.newLoadingStyle.Bool();
            AppearanceConfig.newLoadingStyle.setConfigBool(on);
            toggleCheck(view, on);
            rebuildOthers(false);
        } else if (position == md3SliderRow) {
            boolean on = !isMd3Value(NaConfig.INSTANCE.getSliderStyle().Int());
            NaConfig.INSTANCE.getSliderStyle().setConfigInt(on ? STYLE_MD3 : 0);
            toggleCheck(view, on);
            rebuildOthers(true);
        } else if (position == md3SwitchRow) {
            boolean on = !isMd3Switch(NaConfig.INSTANCE.getSwitchStyle().Int());
            NaConfig.INSTANCE.getSwitchStyle().setConfigInt(on ? SWITCH_STYLE_M3 : 0);
            toggleCheck(view, on);
            rebuildOthers(true);
        } else if (position == md3ChatHeaderRow) {
            boolean on = !AppearanceConfig.newChatHeaderStyle.Bool();
            AppearanceConfig.newChatHeaderStyle.setConfigBool(on);
            toggleCheck(view, on);
            rebuildOthers(false);
        } else if (position == md3NavBarRow) {
            boolean on = !AppearanceConfig.newNavigationBarStyle.Bool();
            AppearanceConfig.newNavigationBarStyle.setConfigBool(on);
            if (on) {
                AppearanceConfig.iosNavigationBarStyle.setConfigBool(false);
                if (MainTabsLayout.isBottomNavigationFloating()) {
                    MainTabsLayout.setBottomNavigationMode(MainTabsLayout.BOTTOM_NAVIGATION_MODE_SHOW);
                }
            }
            toggleCheck(view, on);
            rebuildOthers(false);
        } else if (position == md3ListItemsRow) {
            boolean on = !AppearanceConfig.m3ListItems.Bool();
            AppearanceConfig.m3ListItems.setConfigBool(on);
            AppearanceConfig.invalidateDividerStyle();
            Theme.applyCommonTheme();
            toggleCheck(view, on);
            rebuildOthers(true);
        } else if (position == md3PlayerRow) {
            boolean on = !AppearanceConfig.md3Player.Bool();
            AppearanceConfig.md3Player.setConfigBool(on);
            toggleCheck(view, on);
            rebuildOthers(false);
        } else if (position == md3MiniPlayerRow) {
            boolean on = !AppearanceConfig.md3MiniPlayer.Bool();
            AppearanceConfig.md3MiniPlayer.setConfigBool(on);
            toggleCheck(view, on);
            rebuildOthers(false);
        } else if (position == md3ProfileActionsRow) {
            boolean on = !AppearanceConfig.newProfileActionsStyle.Bool();
            AppearanceConfig.newProfileActionsStyle.setConfigBool(on);
            toggleCheck(view, on);
            rebuildOthers(false);
        } else if (position == md3NoGlassRow) {
            boolean on = !AppearanceConfig.md3NoGlassBacking.Bool();
            AppearanceConfig.md3NoGlassBacking.setConfigBool(on);
            toggleCheck(view, on);
            rebuildOthers(true);
        }
    }

    private static void toggleCheck(View view, boolean on) {
        if (view instanceof TextCheckCell) {
            ((TextCheckCell) view).setChecked(on);
        }
    }

    private static boolean isMd3Value(int value) {
        return value == STYLE_MD3;
    }

    private static boolean isMd3Switch(int value) {
        return value == STYLE_MD3 || value == SWITCH_STYLE_M3;
    }

    private void refreshTileControlAlphas() {
        if (listView == null) {
            return;
        }
        final float alpha = ChatTiles.enabled() ? 1f : 0.45f;
        for (int i = 0; i < listView.getChildCount(); i++) {
            View child = listView.getChildAt(i);
            int pos = listView.getChildAdapterPosition(child);
            if (pos == tilesRadiusRow || pos == tilesGapRow || pos == tilesBrightRow || pos == tilesTintRow) {
                child.animate().alpha(alpha).setDuration(180).start();
            }
        }
    }

    private void afterTilesChanged() {
        if (preview != null) {
            preview.refresh();
        }
        AndroidUtilities.cancelRunOnUIThread(notifyList);
        AndroidUtilities.runOnUIThread(notifyList, 120);
    }

    private void rebuildOthers(boolean recreateSelfCells) {
        if (listView != null) {
            listView.setSections(dp(12), dp(AppearanceConfig.sectionRadius()), true);
            if (recreateSelfCells && layoutManager != null && listAdapter != null) {
                int first = layoutManager.findFirstVisibleItemPosition();
                View topChild = first != RecyclerView.NO_POSITION ? layoutManager.findViewByPosition(first) : null;
                int topOffset = topChild != null ? topChild.getTop() - listView.getPaddingTop() : 0;
                listView.setAdapter(listAdapter);
                if (first != RecyclerView.NO_POSITION) {
                    layoutManager.scrollToPositionWithOffset(first, topOffset);
                }
            }
            listView.invalidateItemDecorations();
            for (int i = 0; i < listView.getChildCount(); i++) {
                listView.getChildAt(i).invalidate();
            }
        }
        if (parentLayout != null) {
            parentLayout.rebuildAllFragmentViews(false, false);
        }
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.reloadInterface);
    }

    @Override
    public void onFragmentDestroy() {
        AndroidUtilities.cancelRunOnUIThread(notifyList);
        super.onFragmentDestroy();
    }

    private class ListAdapter extends BaseListAdapter {

        ListAdapter(Context context) {
            super(context);
        }

        @Override
        protected boolean isSectionContent(int viewType) {
            if (viewType == TYPE_PREVIEW || viewType == TYPE_SLIDER_RADIUS
                    || viewType == TYPE_SLIDER_GAP || viewType == TYPE_SLIDER_BRIGHT) {
                return true;
            }
            return super.isSectionContent(viewType);
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int pos = holder.getAdapterPosition();
            if (pos == previewRow || pos == tilesRadiusRow || pos == tilesGapRow || pos == tilesBrightRow) {
                return false;
            }
            return super.isEnabled(holder);
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = null;
            if (viewType == TYPE_PREVIEW) {
                preview = new TilesPreviewView(mContext);
                view = preview;
            } else if (viewType == TYPE_SLIDER_RADIUS) {
                AvatarCornersSeekBar bar = new AvatarCornersSeekBar(mContext, v -> {
                    HeartGramPrefs.prefs().edit().putInt("tiles_radius", v).apply();
                    afterTilesChanged();
                }, 8, 28, tr("Скругление", "Corner radius"), "8", "28");
                bar.setValueSuffix("dp");
                bar.setValue(ChatTiles.radius());
                view = bar;
            } else if (viewType == TYPE_SLIDER_GAP) {
                AvatarCornersSeekBar bar = new AvatarCornersSeekBar(mContext, v -> {
                    HeartGramPrefs.prefs().edit().putInt("tiles_gap2", v).apply();
                    afterTilesChanged();
                }, 0, 8, tr("Зазор между плитками", "Gap between tiles"), tr("Нет", "None"), "8");
                bar.setValueSuffix("dp");
                bar.setValue(ChatTiles.gap());
                view = bar;
            } else if (viewType == TYPE_SLIDER_BRIGHT) {
                AvatarCornersSeekBar bar = new AvatarCornersSeekBar(mContext, v -> {
                    HeartGramPrefs.prefs().edit().putInt("tiles_bright", v).apply();
                    afterTilesChanged();
                }, -30, 30, tr("Яркость", "Brightness"), tr("Темнее", "Darker"), tr("Светлее", "Lighter"));
                bar.setValueSuffix("%");
                bar.setValue(ChatTiles.brightness());
                view = bar;
            }
            if (view != null) {
                view.setLayoutParams(new RecyclerView.LayoutParams(
                        RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
                return new RecyclerListView.Holder(view);
            }
            return super.onCreateViewHolder(parent, viewType);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean partial) {
            switch (holder.getItemViewType()) {
                case TYPE_HEADER: {
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == previewHeaderRow) {
                        headerCell.setText(tr("Предпросмотр", "Preview"));
                    } else if (position == tilesHeaderRow) {
                        headerCell.setText(tr("Чаты плитками", "Chat tiles"));
                    } else if (position == otherHeaderRow) {
                        headerCell.setText(tr("Остальные стили", "Other styles"));
                    }
                    break;
                }
                case TYPE_CHECK: {
                    TextCheckCell cell = (TextCheckCell) holder.itemView;
                    final float tilesAlpha = ChatTiles.enabled() ? 1f : 0.45f;
                    cell.setAlpha(1f);
                    if (position == tilesEnabledRow) {
                        cell.setTextAndCheck(tr("Показывать чаты плитками", "Show chats as tiles"), ChatTiles.enabled(), true);
                    } else if (position == tilesTintRow) {
                        cell.setAlpha(tilesAlpha);
                        cell.setTextAndCheck(tr("Оттенок акцентного цвета", "Accent color tint"), ChatTiles.tint() == 1, false);
                    } else if (position == md3MenusRow) {
                        cell.setTextAndCheck(tr("Меню блоками (ответить, ⋮)", "Menus as blocks (reply, ⋮)"), ChatTiles.menus(), true);
                    } else if (position == md3LoadingRow) {
                        cell.setTextAndCheck(getString(R.string.OEAppearanceNewLoadingStyle), AppearanceConfig.newLoadingStyle.Bool(), true);
                    } else if (position == md3SliderRow) {
                        cell.setTextAndCheck(getString(R.string.OEAppearanceSliderStyle), isMd3Value(NaConfig.INSTANCE.getSliderStyle().Int()), true);
                    } else if (position == md3SwitchRow) {
                        cell.setTextAndCheck(getString(R.string.OEAppearanceSwitchStyle), isMd3Switch(NaConfig.INSTANCE.getSwitchStyle().Int()), true);
                    } else if (position == md3ChatHeaderRow) {
                        cell.setTextAndCheck(getString(R.string.OEAppearanceNewChatHeaderStyle), AppearanceConfig.newChatHeaderStyle.Bool(), true);
                    } else if (position == md3NavBarRow) {
                        cell.setTextAndCheck(getString(R.string.OEAppearanceNewNavigationBarStyle), AppearanceConfig.newNavigationBarStyle.Bool(), true);
                    } else if (position == md3ListItemsRow) {
                        cell.setTextAndCheck(getString(R.string.OEAppearanceM3ListItems), AppearanceConfig.m3ListItems.Bool(), true);
                    } else if (position == md3PlayerRow) {
                        cell.setTextAndCheck(getString(R.string.OEAppearanceMd3Player), AppearanceConfig.md3Player.Bool(), true);
                    } else if (position == md3MiniPlayerRow) {
                        cell.setTextAndCheck(getString(R.string.OEAppearanceMd3MiniPlayer), AppearanceConfig.md3MiniPlayer.Bool(), true);
                    } else if (position == md3ProfileActionsRow) {
                        cell.setTextAndCheck(getString(R.string.NewProfileActionsStyle), AppearanceConfig.newProfileActionsStyle.Bool(), true);
                    } else if (position == md3NoGlassRow) {
                        cell.setTextAndCheck(tr("Убрать подложку жидкого стекла", "Remove liquid glass backing"), AppearanceConfig.md3NoGlassBacking.Bool(), false);
                    }
                    break;
                }
                case TYPE_PREVIEW:
                    if (preview != null) {
                        preview.refresh();
                    }
                    break;
                case TYPE_SLIDER_RADIUS:
                case TYPE_SLIDER_GAP:
                case TYPE_SLIDER_BRIGHT:
                    holder.itemView.setAlpha(ChatTiles.enabled() ? 1f : 0.45f);
                    break;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == previewHeaderRow || position == tilesHeaderRow || position == otherHeaderRow) {
                return TYPE_HEADER;
            } else if (position == previewRow) {
                return TYPE_PREVIEW;
            } else if (position == tilesRadiusRow) {
                return TYPE_SLIDER_RADIUS;
            } else if (position == tilesGapRow) {
                return TYPE_SLIDER_GAP;
            } else if (position == tilesBrightRow) {
                return TYPE_SLIDER_BRIGHT;
            } else if (position == tilesShadowRow || position == otherShadowRow) {
                return TYPE_SHADOW;
            }
            return TYPE_CHECK;
        }
    }
}
