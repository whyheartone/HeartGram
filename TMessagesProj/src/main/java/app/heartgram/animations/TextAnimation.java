package app.heartgram.animations;

import android.animation.ValueAnimator;
import android.graphics.BlurMaskFilter;
import android.graphics.Color;
import android.text.Editable;
import android.text.Spannable;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.text.style.CharacterStyle;
import android.text.style.UpdateAppearance;
import android.widget.EditText;

import app.heartgram.HeartGramPrefs;


/**
 * Анимация набираемого текста в поле ввода (как в opexGram): каждая новая буква
 * появляется плавно, с выбранным характером и по желанию из размытия.
 * Буква анимируется своим span (только отрисовка, без перестройки текста),
 * после анимации span снимается, в отправку ничего не попадает.
 */
public final class TextAnimation {
    public static final String[] PRESETS = {"Спокойный", "Сбалансированный", "Максимальный"};
    public static final String[] CHARACTERS = {"Плавно", "Снизу", "Сверху", "С перелётом"};
    public static final String[] DURATIONS = {"160 мс", "260 мс", "380 мс", "520 мс"};
    private static final int[] DURATION_MS = {160, 260, 380, 520};

    private TextAnimation() {
    }

    public static boolean enabled() {
        return HeartGramPrefs.prefs().getBoolean("ta_enabled", false);
    }

    static int preset() {
        return clamp(HeartGramPrefs.prefs().getInt("ta_preset", 1), 0, 2);
    }

    static int character() {
        return clamp(HeartGramPrefs.prefs().getInt("ta_character", 3), 0, 3);
    }

    static int duration() {
        return clamp(HeartGramPrefs.prefs().getInt("ta_duration", 1), 0, 3);
    }

    static boolean deleteAnim() {
        return HeartGramPrefs.prefs().getBoolean("ta_delete", true);
    }

    static boolean blur() {
        return HeartGramPrefs.prefs().getBoolean("ta_blur", true);
    }

    /** Пресет перезаписывает всё ниже. */
    public static void applyPreset(int p) {
        android.content.SharedPreferences.Editor e = HeartGramPrefs.prefs().edit().putInt("ta_preset", p);
        if (p == 0) {
            e.putInt("ta_character", 0).putInt("ta_duration", 2).putBoolean("ta_blur", false);
        } else if (p == 1) {
            e.putInt("ta_character", 3).putInt("ta_duration", 1).putBoolean("ta_blur", true);
        } else {
            e.putInt("ta_character", 3).putInt("ta_duration", 3).putBoolean("ta_blur", true);
        }
        e.apply();
    }

    private static int clamp(int v, int a, int b) {
        return Math.max(a, Math.min(b, v));
    }

    /** Вешается на поле ввода сообщения. */
    public static void attach(EditText et) {
        if (et != null) {
            et.addTextChangedListener(new Watcher(et));
        }
    }

    private static final class Watcher implements TextWatcher {
        private final EditText view;

        Watcher(EditText view) {
            this.view = view;
        }

        /** Стираемые буквы (до изменения): текст и позиции в координатах поля. */
        private Ghost pending;

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            pending = null;
            if (!enabled() || !deleteAnim() || count <= after || isPassword()) {
                return;
            }
            // убирается [start + after, start + count)
            int from = start + after, to = Math.min(start + count, from + 160);
            android.text.Layout layout = view.getLayout();
            if (layout == null || to > s.length() || from >= to) {
                return;
            }
            try {
                Ghost g = new Ghost(view);
                float dx = view.getTotalPaddingLeft() - view.getScrollX();
                float dy = view.getTotalPaddingTop() - view.getScrollY();
                for (int i = from; i < to; i++) {
                    char c = s.charAt(i);
                    if (Character.isWhitespace(c) || Character.isLowSurrogate(c)) {
                        continue;
                    }
                    int end = Character.isHighSurrogate(c) && i + 1 < s.length() ? i + 2 : i + 1;
                    int line = layout.getLineForOffset(i);
                    g.add(s.subSequence(i, end).toString(), layout.getPrimaryHorizontal(i) + dx,
                            layout.getLineBaseline(line) + dy);
                }
                pending = g.isEmpty() ? null : g;
            } catch (Throwable t) {
                pending = null;
            }
        }

        private boolean isPassword() {
            return view.getInputType() != 0
                    && (view.getInputType() & android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD) != 0;
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            if (pending != null) {
                Ghost g = pending;
                pending = null;
                g.start(DURATION_MS[duration()], blur());
            }
            // набор, свайп-ввод целым словом и подсказки клавиатуры: анимируются только новые символы
            if (!enabled() || count <= before || count - before > 80 || !(s instanceof Spannable)) {
                return;
            }
            if (view.getInputType() != 0 && (view.getInputType() & android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD) != 0) {
                return;
            }
            Spannable sp = (Spannable) s;
            int from = start + before;
            int to = start + count;
            if (from >= to || to > sp.length()) {
                return;
            }
            AppearSpan span = new AppearSpan(character(), blur(), view.getTextSize());
            sp.setSpan(span, from, to, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
            a.setDuration(DURATION_MS[duration()]);
            a.addUpdateListener(v -> {
                span.progress = (float) v.getAnimatedValue();
                Editable e = view.getText();
                int s0 = e == null ? -1 : e.getSpanStart(span);
                if (s0 >= 0) {
                    // повторный setSpan сбрасывает кэш отрисовки блока текста у EditText
                    e.setSpan(span, s0, e.getSpanEnd(span), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else {
                    v.cancel();
                }
            });
            a.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    Editable e = view.getText();
                    if (e != null) {
                        e.removeSpan(span);
                    }
                    view.invalidate();
                }
            });
            a.start();
        }

        @Override
        public void afterTextChanged(Editable s) {
        }
    }

    /** Стёртые буквы: рисуются поверх поля и тают (вверх, в прозрачность, в размытие). */
    static final class Ghost extends android.graphics.drawable.Drawable {
        private final EditText view;
        private final TextPaint paint;
        private final java.util.ArrayList<String> chars = new java.util.ArrayList<>();
        private final java.util.ArrayList<float[]> pos = new java.util.ArrayList<>();
        private float progress;
        private boolean blur;

        Ghost(EditText view) {
            this.view = view;
            this.paint = new TextPaint(view.getPaint());
            paint.setColor(view.getCurrentTextColor());
        }

        void add(String c, float x, float y) {
            chars.add(c);
            pos.add(new float[]{x, y});
        }

        boolean isEmpty() {
            return chars.isEmpty();
        }

        void start(int duration, boolean blur) {
            this.blur = blur;
            setBounds(0, 0, view.getWidth(), view.getHeight());
            view.getOverlay().add(this);
            ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
            // много букв (свайп по Backspace): волна дольше, но не затягивается
            int n = chars.size();
            a.setDuration(Math.max(120, (int) (duration * 0.55f)) + Math.min(n, 40) * 5); // коротко: буква не «висит»
            a.addUpdateListener(v -> {
                progress = (float) v.getAnimatedValue();
                invalidateSelf();
                view.invalidate();
            });
            a.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    view.getOverlay().remove(Ghost.this);
                }
            });
            a.start();
        }

        @Override
        public void draw(android.graphics.Canvas canvas) {
            int n = chars.size();
            float size = view.getTextSize();
            int baseAlpha = Color.alpha(view.getCurrentTextColor());
            // волна справа налево: последняя буква уходит первой, как их стирает палец
            float spread = n > 1 ? Math.min(0.45f, 0.025f * n) : 0f;
            float window = 1f - spread;
            float lastBlur = -1f;
            for (int i = 0; i < n; i++) {
                float delay = n > 1 ? spread * (n - 1 - i) / (n - 1) : 0f;
                float p = Math.max(0f, Math.min(1f, (progress - delay) / window));
                float eased = 1f - (1f - p) * (1f - p) * (1f - p); // сразу тает, к концу замедляется
                int alpha = (int) (baseAlpha * (1f - eased));
                if (alpha <= 0) {
                    continue;
                }
                paint.setAlpha(alpha);
                // размытие квантуется, чтобы не создавать фильтр на каждую букву
                float r = blur ? Math.round(size * 0.14f * eased * 4f) / 4f : 0f;
                if (r != lastBlur) {
                    paint.setMaskFilter(r > 0.5f ? new BlurMaskFilter(r, BlurMaskFilter.Blur.NORMAL) : null);
                    lastBlur = r;
                }
                float[] xy = pos.get(i);
                float lift = -size * 0.30f * eased;
                float drift = size * 0.12f * eased;
                float scale = 1f - 0.30f * eased;
                canvas.save();
                canvas.scale(scale, scale, xy[0], xy[1] - size * 0.35f);
                canvas.drawText(chars.get(i), xy[0] + drift, xy[1] + lift, paint);
                canvas.restore();
            }
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(android.graphics.ColorFilter colorFilter) {
        }

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.TRANSLUCENT;
        }
    }

    /** Буква в процессе появления: прозрачность, сдвиг и размытие по прогрессу. */
    static final class AppearSpan extends CharacterStyle implements UpdateAppearance {
        float progress;
        private final int character;
        private final boolean blur;
        private final float size;

        AppearSpan(int character, boolean blur, float size) {
            this.character = character;
            this.blur = blur;
            this.size = size;
        }

        @Override
        public void updateDrawState(TextPaint tp) {
            float p = Math.max(0f, Math.min(1f, progress));
            float eased = 1f - (1f - p) * (1f - p);
            tp.setAlpha((int) (tp.getAlpha() * eased));
            float shift;
            switch (character) {
                case 1:
                    shift = -size * 0.45f * (1f - eased); // снизу вверх
                    break;
                case 2:
                    shift = size * 0.45f * (1f - eased); // сверху вниз
                    break;
                case 3: {
                    float t = p - 1f;
                    float over = t * t * (2.6f * t + 1.6f) + 1f; // перелёт
                    shift = -size * 0.55f * (1f - over);
                    break;
                }
                default:
                    shift = 0;
            }
            tp.baselineShift -= (int) shift;
            if (blur && p < 1f) {
                float r = size * 0.18f * (1f - eased);
                if (r > 0.5f) {
                    tp.setMaskFilter(new BlurMaskFilter(r, BlurMaskFilter.Blur.NORMAL));
                }
            }
        }
    }
}
