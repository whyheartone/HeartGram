package app.exteraless.updater;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;

/**
 * Плашка обновления внизу списка чатов. Пока обновление только найдено, это карточка «Доступно обновление»;
 * когда окно свернули во время скачивания, внутри карточки появляется индикатор прогресса в стиле Material Design 3.
 */
public class UpdatePillView extends LinearLayout {

    private final ImageView icon;
    private final TextView label;
    private final ProgressBar progress;

    public UpdatePillView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setBackground(Theme.createRoundRectDrawable(dp(18), Theme.getColor(Theme.key_featuredStickers_addButton)));
        setElevation(dp(4));
        setPadding(dp(16), dp(12), dp(16), dp(12));
        setClickable(true);

        final int onAccent = Theme.getColor(Theme.key_featuredStickers_buttonText);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        icon = new ImageView(context);
        icon.setImageResource(R.drawable.msg_download);
        icon.setColorFilter(new PorterDuffColorFilter(onAccent, PorterDuff.Mode.SRC_IN));
        row.addView(icon, LayoutHelper.createLinear(24, 24, Gravity.CENTER_VERTICAL, 0, 0, 12, 0));

        label = new TextView(context);
        label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        label.setTypeface(AndroidUtilities.bold());
        label.setTextColor(onAccent);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(label, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL));
        addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        progress = new ProgressBar(context, onAccent);
        progress.setVisibility(GONE);
        addView(progress, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 4, 0, 10, 0, 0));
    }

    /** percent < 0: скачивания нет. */
    public void set(CharSequence text, int percent) {
        label.setText(text);
        if (percent >= 0) {
            progress.setVisibility(VISIBLE);
            progress.setValue(percent / 100f);
        } else {
            progress.setVisibility(GONE);
            progress.reset();
        }
    }

    /** Индикатор Material Design 3: закруглённый трек, закруглённый заполненный отрезок и зазор между ними. */
    private static final class ProgressBar extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private float shown, target;
        private ValueAnimator animator;

        ProgressBar(Context context, int color) {
            super(context);
            fill.setColor(color);
            track.setColor((color & 0x00FFFFFF) | 0x4D000000);
        }

        void setValue(float value) {
            target = Math.max(0f, Math.min(1f, value));
            if (animator != null) {
                animator.cancel();
            }
            animator = ValueAnimator.ofFloat(shown, target);
            animator.setDuration(250);
            animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
            animator.addUpdateListener(a -> {
                shown = (float) a.getAnimatedValue();
                invalidate();
            });
            animator.start();
        }

        void reset() {
            if (animator != null) {
                animator.cancel();
            }
            shown = target = 0f;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final float w = getWidth(), h = getHeight(), r = h / 2f, gap = dp(4);
            final float split = w * shown;
            // заполненная часть
            if (split > 0) {
                rect.set(0, 0, Math.max(split - (shown < 1f ? gap / 2f : 0), h), h);
                canvas.drawRoundRect(rect, r, r, fill);
            }
            // остаток трека после зазора
            if (shown < 1f) {
                float start = Math.max(split + gap / 2f, h + gap);
                if (start < w - h) {
                    rect.set(start, 0, w, h);
                    canvas.drawRoundRect(rect, r, r, track);
                }
                // точка в конце трека, как в MD3
                canvas.drawCircle(w - r, r, r * 0.6f, fill);
            }
        }
    }
}
