package app.heartgram.animations;

import android.animation.TimeInterpolator;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import app.heartgram.HeartGramPrefs;

/**
 * Появление сообщения в чате: когда ячейка попадает на экран, она плавно «въезжает» выбранным стилем.
 * Если систему уже анимирует сама (новое сообщение, удаление), ячейку не трогаем.
 */
public final class MessageAnimations {

    public static final String[] STYLES_RU = {"Мягкое появление", "Справа", "Слева", "Снизу", "3D-поворот", "Пружинный подъём", "Приближение", "Покачивание"};
    public static final String[] STYLES_EN = {"Soft fade", "From the right", "From the left", "From the bottom", "3D turn", "Spring lift", "Zoom in", "Wobble"};
    public static final String[] SPEEDS_RU = {"Медленно", "Обычно", "Быстро", "Очень быстро"};
    public static final String[] SPEEDS_EN = {"Slow", "Normal", "Fast", "Very fast"};

    private static final float[] SPEED = {0.6f, 1f, 1.6f, 2.4f};
    private static final long BASE_MS = 320;

    private MessageAnimations() {
    }

    /** Вызывается из ChatMessageCell.onAttachedToWindow. */
    public static void onAttach(View cell) {
        if (cell == null || !HeartGramPrefs.animMessages()) {
            return;
        }
        // уже идёт анимация системы или ячейка сдвинута: не вмешиваемся
        if (cell.getAlpha() != 1f || cell.getTranslationX() != 0f || cell.getTranslationY() != 0f
                || cell.getScaleX() != 1f || cell.getScaleY() != 1f || cell.getRotation() != 0f || cell.getRotationY() != 0f) {
            return;
        }
        final int style = Math.max(0, Math.min(HeartGramPrefs.animStyle(), STYLES_EN.length - 1));
        final int speedIndex = Math.max(0, Math.min(HeartGramPrefs.animSpeed(), SPEED.length - 1));
        final long duration = (long) (BASE_MS / SPEED[speedIndex]);
        final float w = Math.max(cell.getWidth(), cell.getResources().getDisplayMetrics().widthPixels);
        final float h = Math.max(cell.getHeight(), 1);
        TimeInterpolator interpolator = new DecelerateInterpolator(1.6f);
        float dx = 0, dy = 0, scale = 1f, rotY = 0f, rot = 0f, alpha = 0f;
        switch (style) {
            case 1:
                dx = w * 0.35f;
                break;
            case 2:
                dx = -w * 0.35f;
                break;
            case 3:
                dy = h * 0.9f;
                break;
            case 4:
                rotY = -55f;
                cell.setCameraDistance(8000 * cell.getResources().getDisplayMetrics().density);
                break;
            case 5:
                dy = h * 0.8f;
                interpolator = new OvershootInterpolator(1.8f);
                break;
            case 6:
                scale = 0.6f;
                interpolator = new OvershootInterpolator(1.2f);
                break;
            case 7:
                rot = -4f;
                dx = w * 0.08f;
                interpolator = new OvershootInterpolator(3f);
                break;
            default:
                scale = 0.92f;
                interpolator = new AccelerateDecelerateInterpolator();
                break;
        }
        cell.setAlpha(alpha);
        cell.setTranslationX(dx);
        cell.setTranslationY(dy);
        cell.setScaleX(scale);
        cell.setScaleY(scale);
        cell.setRotationY(rotY);
        cell.setRotation(rot);
        cell.animate().cancel();
        cell.animate()
                .alpha(1f).translationX(0f).translationY(0f).scaleX(1f).scaleY(1f).rotationY(0f).rotation(0f)
                .setDuration(duration)
                .setInterpolator(interpolator)
                .withEndAction(() -> reset(cell))
                .start();
    }

    private static void reset(View cell) {
        cell.setAlpha(1f);
        cell.setTranslationX(0f);
        cell.setTranslationY(0f);
        cell.setScaleX(1f);
        cell.setScaleY(1f);
        cell.setRotationY(0f);
        cell.setRotation(0f);
    }
}
