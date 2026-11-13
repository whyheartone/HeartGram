package app.heartgram.folders;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

import app.heartgram.HeartGramPrefs;

/**
 * Выбор вида папок (как в opexGram): карточки с превью «Telegram», «Вкладки», «Кнопки», «Классика».
 * Нажатие сразу сохраняет выбор и вызывает onChange.
 */
public final class FolderStyleView extends View {
    static final String[] NAMES = {"Telegram", "Вкладки", "Кнопки"};
    private static final int N = NAMES.length;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Runnable onChange;

    public FolderStyleView(Context context, Runnable onChange) {
        super(context);
        this.onChange = onChange;
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(dp(13));
    }

    private static float dp(float v) {
        return AndroidUtilities.dp(v);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), (int) dp(112));
    }

    private float cardWidth() {
        return (getWidth() - dp(16) * 2 - dp(8) * (N - 1)) / (float) N;
    }

    private float cardLeft(int i) {
        return dp(16) + i * (cardWidth() + dp(8));
    }

    @Override
    protected void onDraw(Canvas c) {
        int tile = Theme.getColor(Theme.key_windowBackgroundWhite);
        int accent = Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader);
        int textColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
        int grey = ColorUtils.blendARGB(tile, textColor, 0.35f);
        int strip = ColorUtils.blendARGB(tile, textColor, 0.10f);
        int selected = HeartGramPrefs.folderStyle();
        float w = cardWidth();
        float top = dp(10), h = dp(64);
        for (int i = 0; i < N; i++) {
            float left = cardLeft(i);
            r.set(left, top, left + w, top + h);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(ColorUtils.blendARGB(tile, textColor, 0.04f));
            c.drawRoundRect(r, dp(12), dp(12), paint);
            if (i == selected) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(2));
                paint.setColor(accent);
                c.drawRoundRect(r, dp(12), dp(12), paint);
            }
            // мини-панель вкладок: три «подписи», первая выбрана
            float cy = top + h / 2f + dp(8);
            paint.setStyle(Paint.Style.FILL);
            c.save();
            c.clipRect(left, top, left + w, top + h);
            if (i == 0) {
                paint.setColor(strip); // Telegram: общая капсула
                c.drawRoundRect(new RectF(left + dp(5), cy - dp(10), left + w - dp(5), cy + dp(10)), dp(10), dp(10), paint);
            } else if (i == 1 || i == 3) {
                paint.setColor(strip); // ровная полоса во всю ширину
                c.drawRect(left, cy - dp(10), left + w, cy + dp(10), paint);
                if (i == 1) {
                    paint.setColor(grey);
                    c.drawRect(left, cy + dp(10), left + w, cy + dp(10) + Math.max(1, dp(0.5f)), paint);
                }
            }
            float pad = i == 1 || i == 3 ? 0 : dp(7);
            float seg = (w - pad * 2) / 3f;
            for (int t = 0; t < 3; t++) {
                float sx = left + pad + t * seg;
                float cx = sx + seg / 2f;
                RectF bar = new RectF(cx - dp(5), cy - dp(1.5f), cx + dp(5), cy + dp(1.5f));
                if (t == 0 && i == 0) {
                    paint.setColor((accent & 0x00FFFFFF) | 0x40000000);
                    c.drawRoundRect(new RectF(sx + dp(1), cy - dp(8), sx + seg - dp(1), cy + dp(8)), dp(8), dp(8), paint);
                } else if (t == 0 && i == 1) {
                    paint.setColor(accent);
                    c.drawRoundRect(new RectF(cx - dp(6), cy + dp(7), cx + dp(6), cy + dp(10)), dp(2), dp(2), paint);
                } else if (t == 0 && i == 3) {
                    paint.setColor(accent);
                    c.drawRoundRect(new RectF(sx + dp(3), cy + dp(7), sx + seg - dp(3), cy + dp(10)), dp(2), dp(2), paint);
                } else if (i == 2) {
                    paint.setColor(t == 0 ? accent : strip);
                    c.drawRoundRect(new RectF(sx + dp(1.5f), cy - dp(9), sx + seg - dp(1.5f), cy + dp(9)), dp(9), dp(9), paint);
                }
                paint.setColor(i == 2 && t == 0 ? tile : (t == 0 ? accent : grey));
                c.drawRoundRect(bar, dp(2), dp(2), paint);
            }
            c.restore();
            // кружок выбора
            float rx = left + w - dp(11), ry = top + dp(11);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(i == selected ? accent : grey);
            c.drawCircle(rx, ry, dp(5.5f), paint);
            if (i == selected) {
                paint.setStyle(Paint.Style.FILL);
                c.drawCircle(rx, ry, dp(2.5f), paint);
            }
            text.setColor(textColor);
            c.drawText(NAMES[i], left + w / 2f, top + h + dp(22), text);
        }
    }

    private float downX, downY;
    private boolean tapCandidate;

    private int cardAt(float x, float y) {
        if (y < dp(4) || y > getHeight()) {
            return -1;
        }
        float w = cardWidth();
        for (int i = 0; i < N; i++) {
            float left = cardLeft(i);
            if (x >= left - dp(4) && x <= left + w + dp(4)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int slop = android.view.ViewConfiguration.get(getContext()).getScaledTouchSlop();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                tapCandidate = cardAt(downX, downY) >= 0;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (Math.abs(e.getX() - downX) > slop || Math.abs(e.getY() - downY) > slop) {
                    tapCandidate = false; // это прокрутка, а не нажатие
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                tapCandidate = false;
                return true;
            default:
                break;
        }
        if (e.getActionMasked() == MotionEvent.ACTION_UP) {
            int i = tapCandidate ? cardAt(e.getX(), e.getY()) : -1;
            tapCandidate = false;
            if (i >= 0 && i == cardAt(downX, downY) && i != HeartGramPrefs.folderStyle()) {
                HeartGramPrefs.setFolderStyle(i);
                performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                invalidate();
                if (onChange != null) {
                    onChange.run();
                }
            }
            return true;
        }
        return super.onTouchEvent(e);
    }
}
