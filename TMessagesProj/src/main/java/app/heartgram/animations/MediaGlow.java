package app.heartgram.animations;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.Utilities;

import java.lang.ref.WeakReference;

import app.heartgram.HeartGramPrefs;

/**
 * Свечение медиа: при просмотре фото за ним рисуется мягкий ореол его же цветов.
 * Берётся уменьшенная копия картинки, размывается и растягивается чуть шире самой картинки.
 */
public final class MediaGlow {

    public static final String[] SIZES_RU = {"Маленькое", "Среднее", "Большое", "Огромное"};
    public static final String[] SIZES_EN = {"Small", "Medium", "Large", "Huge"};

    /** Насколько ореол выходит за картинку, dp. */
    private static final int[] PAD_DP = {14, 26, 42, 64};
    private static final int SMALL = 36;

    private static final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private static final RectF rect = new RectF();

    private static WeakReference<Bitmap> sourceRef = new WeakReference<>(null);
    private static Bitmap glow;

    private MediaGlow() {
    }

    /** Вызывается из PhotoViewer прямо перед рисованием основной картинки, холст уже в координатах картинки. */
    public static void draw(Canvas canvas, ImageReceiver image, float alpha) {
        if (!HeartGramPrefs.mediaGlow() || image == null || alpha <= 0.01f) {
            return;
        }
        try {
            Bitmap source = image.getBitmap();
            if (source == null || source.isRecycled() || source.getWidth() < 2 || source.getHeight() < 2) {
                return;
            }
            if (sourceRef.get() != source || glow == null || glow.isRecycled()) {
                sourceRef = new WeakReference<>(source);
                glow = blurred(source);
            }
            if (glow == null) {
                return;
            }
            int size = Math.max(0, Math.min(HeartGramPrefs.glowSize(), PAD_DP.length - 1));
            float pad = dp(PAD_DP[size]);
            float x = image.getImageX(), y = image.getImageY(), w = image.getImageWidth(), h = image.getImageHeight();
            rect.set(x - pad, y - pad, x + w + pad, y + h + pad);
            paint.setAlpha((int) (255 * Math.min(1f, alpha) * 0.85f));
            canvas.drawBitmap(glow, null, rect, paint);
        } catch (Throwable ignored) {
        }
    }

    private static Bitmap blurred(Bitmap source) {
        int w = SMALL;
        int h = Math.max(8, Math.round(SMALL * source.getHeight() / (float) source.getWidth()));
        if (h > SMALL * 3) {
            h = SMALL * 3;
        }
        Bitmap small = Bitmap.createScaledBitmap(source, w, h, true);
        if (small == source) {
            small = source.copy(Bitmap.Config.ARGB_8888, true);
        }
        if (small == null) {
            return null;
        }
        Utilities.stackBlurBitmap(small, 5);
        return small;
    }
}
