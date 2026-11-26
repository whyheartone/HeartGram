package app.exteraless.utils;

import android.animation.StateListAnimator;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.BaseCell;
import org.telegram.ui.Components.ScaleStateListAnimator;

import java.util.WeakHashMap;
import java.util.function.Consumer;

/**
 * UI-утилиты, перенесённые из exteraGram
 */
public final class UIUtil {

    private static final float RIPPLE_PRESS_GAP = 4f;
    private static final WeakHashMap<View, View.OnLayoutChangeListener> scaleAnimatorRelayoutListeners = new WeakHashMap<>();

    private UIUtil() {
    }

    public static GradientDrawable createBottomFade(@ColorInt int color) {
        return new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{
                        ColorUtils.setAlphaComponent(color, 0),
                        ColorUtils.setAlphaComponent(color, 60),
                        color
                }
        );
    }

    public static void applyScaleStateListAnimator(View view, float scale, float tension) {
        applyScaleStateListAnimator(view, 12f, false, false, 3, scale, tension);
    }

    public static void applyScaleStateListAnimator(View view, float selectorRad, boolean top, boolean bottom, int basePadding, float scale, float tension) {
        if (view == null) {
            return;
        }
        final View.OnLayoutChangeListener previous = scaleAnimatorRelayoutListeners.remove(view);
        if (previous != null) {
            view.removeOnLayoutChangeListener(previous);
        }
        final StateListAnimator currentAnimator = view.getStateListAnimator();
        if (currentAnimator != null) {
            currentAnimator.jumpToCurrentState();
        }

        final int appliedWidth = view.getWidth();
        final int appliedHeight = view.getHeight();
        final View.OnLayoutChangeListener relayout = (v, left, t, right, b, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (v.getWidth() != appliedWidth || v.getHeight() != appliedHeight) {
                applyScaleStateListAnimator(v, selectorRad, top, bottom, basePadding, scale, tension);
            }
        };
        view.addOnLayoutChangeListener(relayout);
        scaleAnimatorRelayoutListeners.put(view, relayout);

        final float appliedLargestDp = Math.max(appliedWidth, appliedHeight) / AndroidUtilities.density;
        final float effectiveScale = appliedLargestDp > 0f
                ? Math.min(scale, 2f * RIPPLE_PRESS_GAP / appliedLargestDp)
                : scale;

        final Consumer<Float> updateRipple = aFloat -> {
            final Drawable bg = view.getBackground();
            if (bg instanceof BaseCell.RippleDrawableSafe) {
                final Drawable mask = ((BaseCell.RippleDrawableSafe) bg).mask;
                if (mask instanceof Theme.RippleRadMaskDrawable) {
                    final Theme.RippleRadMaskDrawable m = (Theme.RippleRadMaskDrawable) mask;
                    final float widthDp = view.getWidth() / AndroidUtilities.density;
                    final float heightDp = view.getHeight() / AndroidUtilities.density;

                    final float shrink = 1f - effectiveScale * aFloat;
                    final float padH = Math.max(0f, (RIPPLE_PRESS_GAP - widthDp * effectiveScale / 2f) * aFloat / shrink);
                    final float padV = Math.max(0f, (RIPPLE_PRESS_GAP - heightDp * effectiveScale / 2f) * aFloat / shrink);
                    m.setPadding(padH, padV, padH, padV);

                    final float outerRadius = (selectorRad - basePadding * aFloat) / shrink;
                    final float innerRadius = (selectorRad - basePadding) * aFloat / shrink;
                    m.setRadius(
                            top ? outerRadius : innerRadius,
                            bottom ? outerRadius : innerRadius
                    );
                }
            }
        };
        ScaleStateListAnimator.apply(view, effectiveScale, tension, updateRipple, updateRipple);
        final StateListAnimator applied = view.getStateListAnimator();
        if (applied != null) {
            applied.jumpToCurrentState();
        }
        updateRipple.accept(view.isPressed() ? 1f : 0f);
    }

    /**
     * Фон плавающей кнопки. Апстрим всегда рисует круг
     * ({@code Theme.createSimpleSelectorCircleDrawable}); exteraGram при включённом
     * squareFab рисует «squircle» с радиусом ceil(size * 16 / 56) dp
     *
     * @param size сторона кнопки в dp
     */
    public static Drawable createFabSelectorDrawable(int size, int color, int pressedColor) {
        if (size == 40) {
            final int background = org.telegram.ui.ActionBar.Theme.getColor(
                    org.telegram.ui.ActionBar.Theme.key_windowBackgroundWhite);
            color = androidx.core.graphics.ColorUtils.blendARGB(background, 0xFFFFFFFF, 0.1f);
            pressedColor = org.telegram.ui.ActionBar.Theme.blendOver(background,
                    org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_listSelector));
        }
        return org.telegram.ui.ActionBar.Theme.createSimpleSelectorRoundRectDrawable(
                org.telegram.messenger.AndroidUtilities.dp(
                        app.exteraless.appearance.AppearanceConfig.fabCornerRadius(size)),
                color, pressedColor);
    }
}
