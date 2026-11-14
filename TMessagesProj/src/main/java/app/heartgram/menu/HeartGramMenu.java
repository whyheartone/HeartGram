package app.heartgram.menu;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.PopupSwipeBackLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * Подменю «HeartGram» в меню «⋮» чата: сюда собраны пункты Ayu и NagramX («К началу», «К сообщению», «Скрыть заголовок»,
 * «Просмотреть удалённые», «Очистить удалённые»), чтобы основное меню не разрасталось.
 * Нажатие передаётся обычному обработчику меню по номеру пункта, поэтому логика пунктов не меняется.
 */
public final class HeartGramMenu {

    public static final class Entry {
        public final int id;
        public final int icon;
        public final CharSequence text;

        public Entry(int id, int icon, CharSequence text) {
            this.id = id;
            this.icon = icon;
            this.text = text;
        }
    }

    private HeartGramMenu() {
    }

    public static void attach(final ActionBarMenuItem headerItem, final ActionBar actionBar,
                              final Theme.ResourcesProvider resourcesProvider, final List<Entry> source) {
        if (headerItem == null || source == null || source.isEmpty()) {
            return;
        }
        final PopupSwipeBackLayout swipeBackLayout = headerItem.getPopupLayout() == null ? null : headerItem.getPopupLayout().getSwipeBack();
        if (swipeBackLayout == null) {
            return;
        }
        final List<Entry> entries = new ArrayList<>(source);
        ActionBarMenuItem.Item item = headerItem.lazilyAddSwipeBackItem(R.drawable.exteraless_icon_menu, null, "HeartGram",
                () -> build(headerItem, actionBar, resourcesProvider, swipeBackLayout, entries));
        item.setOnClickListener(v -> item.openSwipeBack());
    }

    private static View build(final ActionBarMenuItem headerItem, final ActionBar actionBar,
                              Theme.ResourcesProvider resourcesProvider, final PopupSwipeBackLayout swipeBackLayout,
                              List<Entry> entries) {
        Context context = swipeBackLayout.getContext();
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);

        ActionBarMenuSubItem back = new ActionBarMenuSubItem(context, true, false, resourcesProvider);
        back.setItemHeight(44);
        back.setTextAndIcon(LocaleController.getString(R.string.Back), R.drawable.msg_arrow_back);
        back.getTextView().setPadding(LocaleController.isRTL ? 0 : AndroidUtilities.dp(40), 0,
                LocaleController.isRTL ? AndroidUtilities.dp(40) : 0, 0);
        back.setOnClickListener(v -> swipeBackLayout.closeForeground());
        root.addView(back, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        for (final Entry e : entries) {
            ActionBarMenuSubItem sub = new ActionBarMenuSubItem(context, false, false, resourcesProvider);
            sub.setTextAndIcon(e.text, e.icon);
            sub.setMinimumWidth(AndroidUtilities.dp(196));
            sub.setOnClickListener(v -> {
                headerItem.toggleSubMenu();
                if (actionBar != null && actionBar.getActionBarMenuOnItemClick() != null) {
                    actionBar.getActionBarMenuOnItemClick().onItemClick(e.id);
                }
            });
            root.addView(sub, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
        }
        return root;
    }
}
