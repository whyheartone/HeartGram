package app.heartgram.tiles;

import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Adapters.DialogsAdapter;
import org.telegram.ui.Cells.DialogCell;

import java.util.WeakHashMap;

import app.heartgram.HeartGramPrefs;

/**
 * MD3-плитки в списке чатов (перенос из старого мода): каждая группа чатов (закреплённые, остальные)
 * лежит блоком, у блока скруглены верх и низ, между плитками небольшой зазор.
 */
public final class ChatTiles {

    public static final int[] GAPS = {0, 1, 2, 4, 6, 8};
    public static final int[] RADII = {8, 12, 16, 20, 24, 28};
    private static final int SIDE_DP = 8, INNER_RADIUS_DP = 6, GROUP_GAP_DP = 3, LIST_TOP_DP = 6;

    private static final Paint card = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path path = new Path();
    private static final WeakHashMap<View, Shape> shapes = new WeakHashMap<>();

    private ChatTiles() {
    }

    public static boolean enabled() {
        return HeartGramPrefs.prefs().getBoolean("tiles", false);
    }

    public static int gap() {
        return HeartGramPrefs.prefs().getInt("tiles_gap2", 2);
    }

    public static int radius() {
        return HeartGramPrefs.prefs().getInt("tiles_radius", 20);
    }

    /** Яркость плиток в процентах: от -30 (темнее) до +30 (светлее), 0 - как задумано темой. */
    public static int brightness() {
        return HeartGramPrefs.prefs().getInt("tiles_bright", 0);
    }

    /** 0 - нейтральный цвет, 1 - с примесью цвета акцента темы. */
    public static int tint() {
        return HeartGramPrefs.prefs().getInt("tiles_tint", 0);
    }

    /** Цвет плитки: фон с примесью цвета текста, затем окраска и яркость из настроек MD3. */
    public static int tileColor() {
        int color = baseTileColor();
        if (tint() == 1) {
            color = ColorUtils.blendARGB(color, Theme.getColor(Theme.key_featuredStickers_addButton), 0.12f);
        }
        int b = brightness();
        if (b > 0) {
            color = ColorUtils.blendARGB(color, 0xFFFFFFFF, b / 100f * 0.5f);
        } else if (b < 0) {
            color = ColorUtils.blendARGB(color, 0xFF000000, -b / 100f * 0.5f);
        }
        return color;
    }

    private static int baseTileColor() {
        int surface = Theme.getColor(Theme.key_windowBackgroundWhite);
        int text = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
        double lum = ColorUtils.calculateLuminance(surface);
        boolean dark = lum < 0.5;
        if (dark && lum > 0.012) {
            // серая тёмная тема: плитка чуть темнее фона, а не светлее (на чёрном фоне темнить некуда, там светлее)
            return ColorUtils.blendARGB(surface, 0xFF000000, 0.10f);
        }
        return ColorUtils.blendARGB(surface, text, dark ? 0.06f : 0.035f);
    }

    private static final class Shape extends ViewOutlineProvider {
        float upper, lower;

        @Override
        public void getOutline(View view, Outline outline) {
            outline.setAlpha(0f);
            float w = view.getWidth(), h = view.getHeight();
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                Path p = new Path();
                roundPath(p, 0, 0, w, h, upper, lower);
                outline.setPath(p);
            } else {
                outline.setRoundRect(0, 0, (int) w, (int) h, Math.min(upper, lower));
            }
        }
    }

    private static void roundPath(Path p, float l, float t, float r, float b, float upper, float lower) {
        float h = b - t;
        upper = Math.min(upper, h / 2f);
        lower = Math.min(lower, h / 2f);
        p.addRoundRect(new RectF(l, t, r, b), new float[]{upper, upper, upper, upper, lower, lower, lower, lower}, Path.Direction.CW);
    }

    /** 1 закреплённый чат, 0 обычный, -1 не чат (заголовок, подсказка): разрывает блок. */
    private static int group(RecyclerView parent, int position) {
        RecyclerView.Adapter<?> adapter = parent.getAdapter();
        if (!(adapter instanceof DialogsAdapter) || position < 0 || position >= adapter.getItemCount()) {
            return -1;
        }
        try {
            Object item = ((DialogsAdapter) adapter).getItem(position);
            return item instanceof TLRPC.Dialog ? (((TLRPC.Dialog) item).pinned ? 1 : 0) : -1;
        } catch (Throwable t) {
            return -1;
        }
    }

    /** Пункты бокового меню плитками (те же цвет, скругление и зазор, что у чатов). */
    public static void styleDrawer(android.view.ViewGroup container) {
        if (!enabled()) {
            return;
        }
        int n = container.getChildCount();
        int tile = tileColor();
        int selector = Theme.getColor(Theme.key_listSelector);
        float big = AndroidUtilities.dp(radius()), small = AndroidUtilities.dp(Math.min(INNER_RADIUS_DP, radius()));
        for (int i = 0; i < n; i++) {
            View v = container.getChildAt(i);
            if (!(v instanceof app.exteraless.drawer.DrawerMenuItemView)) {
                v.setBackgroundColor(0); // разделитель групп: пустой зазор вместо линии
                continue;
            }
            boolean first = i == 0 || !(container.getChildAt(i - 1) instanceof app.exteraless.drawer.DrawerMenuItemView);
            boolean last = i == n - 1 || !(container.getChildAt(i + 1) instanceof app.exteraless.drawer.DrawerMenuItemView);
            float up = first ? big : small, low = last ? big : small;
            float[] radii = {up, up, up, up, low, low, low, low};
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(tile);
            bg.setCornerRadii(radii);
            android.graphics.drawable.ShapeDrawable mask = new android.graphics.drawable.ShapeDrawable(
                    new android.graphics.drawable.shapes.RoundRectShape(radii, null, null));
            v.setBackground(new android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(selector), bg, mask));
            android.view.ViewGroup.LayoutParams lp = v.getLayoutParams();
            if (lp instanceof android.view.ViewGroup.MarginLayoutParams) {
                android.view.ViewGroup.MarginLayoutParams m = (android.view.ViewGroup.MarginLayoutParams) lp;
                m.leftMargin = AndroidUtilities.dp(10);
                m.rightMargin = AndroidUtilities.dp(10);
                m.topMargin = first ? AndroidUtilities.dp(2) : AndroidUtilities.dp(1);
                m.bottomMargin = last ? AndroidUtilities.dp(2) : AndroidUtilities.dp(1);
                v.setLayoutParams(m);
            }
        }
    }

    /** Подключается к списку чатов при его создании. */
    public static void attach(RecyclerView list) {
        list.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(Rect out, View view, RecyclerView parent, RecyclerView.State state) {
                offsets(out, view, parent);
            }

            @Override
            public void onDraw(Canvas c, RecyclerView parent, RecyclerView.State state) {
                draw(c, parent);
            }
        });
    }

    private static void offsets(Rect out, View view, RecyclerView parent) {
        if (!(view instanceof DialogCell)) {
            return;
        }
        if (!enabled()) {
            out.set(0, 0, 0, 0);
            if (view.getOutlineProvider() instanceof Shape) {
                view.setClipToOutline(false);
                view.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
            }
            return;
        }
        int pos = parent.getChildAdapterPosition(view);
        int g = group(parent, pos);
        boolean first = g < 0 || group(parent, pos - 1) != g;
        boolean last = g < 0 || group(parent, pos + 1) != g;
        float inner = gap() / 2f;
        float top = first ? GROUP_GAP_DP + inner : inner;
        if (first) {
            boolean topmost = true;
            for (int i = pos - 1; i >= 0 && topmost; i--) {
                topmost = group(parent, i) < 0;
            }
            if (topmost) {
                top += LIST_TOP_DP;
            }
        }
        out.set(AndroidUtilities.dp(SIDE_DP), AndroidUtilities.dp(top), AndroidUtilities.dp(SIDE_DP),
                AndroidUtilities.dp(last ? GROUP_GAP_DP + inner : inner));
        Shape shape = shapes.get(view);
        if (shape == null) {
            shape = new Shape();
            shapes.put(view, shape);
        }
        float big = AndroidUtilities.dp(radius()), small = AndroidUtilities.dp(Math.min(INNER_RADIUS_DP, radius()));
        shape.upper = first ? big : small;
        shape.lower = last ? big : small;
        if (view.getOutlineProvider() != shape) {
            view.setOutlineProvider(shape);
            view.setClipToOutline(true);
        } else {
            view.invalidateOutline();
        }
    }

    private static void draw(Canvas c, RecyclerView parent) {
        if (!enabled()) {
            return;
        }
        card.setColor(tileColor());
        for (int i = 0; i < parent.getChildCount(); i++) {
            View v = parent.getChildAt(i);
            if (!(v instanceof DialogCell) || v.getVisibility() == View.GONE) {
                continue;
            }
            Shape shape = shapes.get(v);
            float upper = shape != null ? shape.upper : AndroidUtilities.dp(radius());
            float lower = shape != null ? shape.lower : AndroidUtilities.dp(radius());
            // при свайпе («прочитать», «закрепить») содержимое строки уезжает влево: плитка уезжает вместе с ним,
            // а справа остаётся цвет действия, который рисует сама строка
            float swipe = Math.min(0f, ((DialogCell) v).hgSwipeTranslationX());
            float x = v.getLeft() + swipe, y = v.getTop() + v.getTranslationY();
            path.reset();
            roundPath(path, x, y, x + v.getWidth(), y + v.getHeight(), upper, lower);
            card.setAlpha(Math.round(255 * v.getAlpha()));
            c.drawPath(path, card);
        }
    }
}
