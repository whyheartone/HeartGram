package app.heartgram;

import android.app.Activity;
import android.app.ActivityManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.core.content.ContextCompat;

import org.telegram.ui.LauncherIconController;

/**
 * Иконка в списке «Недавние»: система берёт значок главной активности, а не выбранной иконки приложения.
 * Собираем её сами из слоёв выбранной иконки и отдаём вместе с цветом шапки.
 */
public final class TaskIcon {

    private static Bitmap cached;
    private static LauncherIconController.LauncherIcon cachedFor;

    private TaskIcon() {
    }

    private static LauncherIconController.LauncherIcon selected() {
        for (LauncherIconController.LauncherIcon icon : LauncherIconController.LauncherIcon.values()) {
            if (LauncherIconController.isEnabled(icon)) {
                return icon;
            }
        }
        return LauncherIconController.LauncherIcon.EXTERALESS;
    }

    private static Bitmap bitmap(Activity activity) {
        LauncherIconController.LauncherIcon icon = selected();
        if (cached != null && cachedFor == icon) {
            return cached;
        }
        try {
            Drawable bg = ContextCompat.getDrawable(activity, icon.background);
            Drawable fg = ContextCompat.getDrawable(activity, icon.foreground);
            if (bg == null) {
                return null;
            }
            final int size = 192;
            // слои адаптивной иконки 108 dp, видна центральная часть 72 dp: растягиваем так, чтобы она заняла весь холст
            final int full = Math.round(size * 108f / 72f);
            final int off = (size - full) / 2;
            Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(out);
            Path clip = new Path();
            clip.addRoundRect(new RectF(0, 0, size, size), size * 0.22f, size * 0.22f, Path.Direction.CW);
            canvas.clipPath(clip);
            bg.setBounds(off, off, off + full, off + full);
            bg.draw(canvas);
            if (fg != null) {
                fg.setBounds(off, off, off + full, off + full);
                fg.draw(canvas);
            }
            cached = out;
            cachedFor = icon;
            return out;
        } catch (Throwable t) {
            android.util.Log.w("HeartGramTaskIcon", "иконка недавних: " + t);
            return null;
        }
    }

    @SuppressWarnings("deprecation")
    public static void apply(Activity activity, int color) {
        try {
            activity.setTaskDescription(new ActivityManager.TaskDescription(null, bitmap(activity), color));
        } catch (Throwable t) {
            android.util.Log.w("HeartGramTaskIcon", "setTaskDescription: " + t);
        }
    }
}
