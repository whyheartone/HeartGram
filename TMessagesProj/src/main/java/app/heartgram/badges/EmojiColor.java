package app.heartgram.badges;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.LongSparseArray;

import org.telegram.messenger.ImageReceiver;
import org.telegram.ui.Components.AnimatedEmojiDrawable;

/**
 * Основной цвет эмодзи по его кадру: нужен, чтобы звёздочки рядом с значком были в цвет эмодзи.
 * Считается один раз на эмодзи, пока кадр не загрузился, возвращает null.
 */
public final class EmojiColor {

    private static final LongSparseArray<Integer> CACHE = new LongSparseArray<>();
    private static final LongSparseArray<Long> LAST_TRY = new LongSparseArray<>();

    private EmojiColor() {
    }

    /** Цвет, уже посчитанный для эмодзи (в списке чатов, шапке и т.д.), или null. */
    public static Integer cachedFor(long documentId) {
        return CACHE.get(documentId);
    }

    /** Цвет эмодзи из drawable (в том числе обёртки SwapAnimatedEmojiDrawable) или null, если кадра ещё нет. */
    public static Integer of(Drawable drawable) {
        try {
            if (drawable instanceof AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable) {
                drawable = ((AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable) drawable).getDrawable();
            }
            if (drawable instanceof AnimatedEmojiDrawable.WrapSizeDrawable) {
                drawable = ((AnimatedEmojiDrawable.WrapSizeDrawable) drawable).getDrawable();
            }
            if (!(drawable instanceof AnimatedEmojiDrawable)) {
                return null;
            }
            AnimatedEmojiDrawable emoji = (AnimatedEmojiDrawable) drawable;
            long id = emoji.getDocumentId();
            Integer cached = CACHE.get(id);
            if (cached != null) {
                return cached;
            }
            // пока кадра нет, не пересчитываем чаще, чем раз в 250 мс (вызывается из отрисовки)
            final long now = android.os.SystemClock.elapsedRealtime();
            if (now - LAST_TRY.get(id, 0L) < 250) {
                return null;
            }
            LAST_TRY.put(id, now);
            ImageReceiver receiver = emoji.getImageReceiver();
            Bitmap bitmap = receiver == null ? null : receiver.getBitmap();
            Integer color = of(bitmap);
            if (color != null) {
                CACHE.put(id, color);
            }
            return color;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Яркий цвет по кадру: насыщенные пиксели весят больше серых и тёмных контуров. */
    public static Integer of(Bitmap src) {
        if (src == null || src.isRecycled()) {
            return null;
        }
        if (android.os.Build.VERSION.SDK_INT >= 26 && src.getConfig() == Bitmap.Config.HARDWARE) {
            src = src.copy(Bitmap.Config.ARGB_8888, false);
            if (src == null) {
                return null;
            }
        }
        final int size = 48;
        Bitmap bmp = Bitmap.createScaledBitmap(src, size, size, true);
        int[] px = new int[size * size];
        bmp.getPixels(px, 0, size, 0, 0, size, size);
        if (bmp != src) {
            bmp.recycle();
        }
        double r = 0, g = 0, b = 0, weight = 0;
        int opaque = 0;
        float[] hsv = new float[3];
        for (int p : px) {
            int a = p >>> 24;
            if (a < 120) {
                continue;
            }
            opaque++;
            Color.colorToHSV(p, hsv);
            double w = (0.08 + hsv[1] * hsv[1]) * (0.2 + hsv[2]) * (a / 255.0);
            r += Color.red(p) * w;
            g += Color.green(p) * w;
            b += Color.blue(p) * w;
            weight += w;
        }
        if (opaque < 40 || weight <= 0) {
            return null;
        }
        int color = Color.rgb((int) (r / weight), (int) (g / weight), (int) (b / weight));
        Color.colorToHSV(color, hsv);
        hsv[1] = Math.min(1f, hsv[1] * 1.15f);
        hsv[2] = Math.max(0.75f, Math.min(1f, hsv[2] * 1.15f));
        return Color.HSVToColor(hsv);
    }
}
