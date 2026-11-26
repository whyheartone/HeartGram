package com.exteragram.messenger.preferences;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.exteragram.messenger.utils.ui.PopupUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;

import java.util.ArrayList;

/**
 * Базовый экран настроек exteraGram — для dex-модулей плагинов.
 *
 * Плагины каталога подгружают собственный dex, скомпилированный против классов
 * exteraGram, и наследуют этот экран напрямую. Подстановка имён из
 * `extera_utils/class_aliases.py` работает только для Python, поэтому имя пакета
 * здесь настоящее, а не наше.
 */
public abstract class BasePreferencesActivity extends BaseFragment {

    protected UniversalRecyclerView listView;
    protected LinearLayoutManager layoutManager;
    @Nullable
    protected View pinnedHeader;

    public abstract String getTitle();

    public abstract void fillItems(ArrayList<UItem> items, UniversalAdapter adapter);

    public abstract void onClick(UItem item, View view, int position, float x, float y);

    public boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    @Nullable
    protected View createPinnedHeader(@NonNull Context context, @NonNull BlurredBackgroundDrawableViewFactory glassFactory) {
        return null;
    }

    protected int getPinnedHeaderOffset() {
        return pinnedHeader != null ? AndroidUtilities.dp(48f + 4f + 4.5f) : 0;
    }

    protected void showListDialog(
            UItem item,
            CharSequence[] options,
            String title,
            int currentValue,
            PopupUtils.OnItemClickListener onSelected
    ) {
        showListDialog(item, options, null, title, currentValue, onSelected);
    }

    protected void showListDialog(
            UItem item,
            CharSequence[] options,
            @Nullable int[] icons,
            String title,
            int currentValue,
            PopupUtils.OnItemClickListener onSelected
    ) {
        Context ctx = getContext() != null ? getContext() : getParentActivity();
        if (ctx == null) {
            return;
        }
        PopupUtils.showDialog(options, icons, title, currentValue, ctx, i -> {
            if (currentValue == i) {
                return;
            }
            onSelected.onClick(i);
            if (listView != null) {
                View view = listView.findViewByItemId(item.id);
                if (view instanceof TextCell) {
                    ((TextCell) view).setValue(options[i], true);
                }
                if (listView.adapter != null) {
                    listView.adapter.update(true);
                }
            }
        }, resourceProvider, icons == null);
    }

    public boolean hasWhiteActionBar() {
        return true;
    }

    @Override
    public boolean isLightStatusBar() {
        return !Theme.isCurrentThemeDark();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getTitle());
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        final FrameLayout contentView = new FrameLayout(context);
        contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        pinnedHeader = createPinnedHeader(context, getBaseSimpleGlass().factory);

        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, this::onLongClick);
        listView.setSections();
        listView.adapter.setApplyBackground(false);
        if (pinnedHeader != null) {
            listView.setClipToPadding(false);
            listView.setPadding(0, getPinnedHeaderOffset(), 0, 0);
        }
        layoutManager = (LinearLayoutManager) listView.getLayoutManager();
        contentView.addView(listView,
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        if (pinnedHeader != null) {
            contentView.addView(pinnedHeader,
                    LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.TOP, 7, 4, 7, 0));
        }
        actionBar.setAdaptiveBackground(listView);

        fragmentView = contentView;
        return fragmentView;
    }
}
