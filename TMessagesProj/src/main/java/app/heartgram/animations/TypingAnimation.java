package app.heartgram.animations;

import android.animation.ValueAnimator;
import android.text.Editable;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.text.style.CharacterStyle;
import android.text.style.UpdateAppearance;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;

import app.heartgram.HeartGramPrefs;

/**
 * Анимация набираемого текста: каждая новая буква плавно проявляется, а не появляется рывком.
 * Работает через временный span прозрачности, который снимается после анимации; сам текст не меняется.
 */
public final class TypingAnimation {

    private static final long DURATION_MS = 170;

    private TypingAnimation() {
    }

    public static void attach(final EditText edit) {
        if (edit == null) {
            return;
        }
        edit.addTextChangedListener(new TextWatcher() {
            private int start = -1;
            private int count;

            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int before, int c) {
                // только набор по буквам: вставку целого текста и удаление не анимируем
                if (HeartGramPrefs.animTyping() && before == 0 && c > 0 && c <= 4) {
                    start = a;
                    count = c;
                } else {
                    start = -1;
                }
            }

            @Override
            public void afterTextChanged(Editable e) {
                if (start < 0 || start + count > e.length()) {
                    start = -1;
                    return;
                }
                final Fade span = new Fade();
                final Editable text = e;
                text.setSpan(span, start, start + count, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                start = -1;
                ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
                animator.setDuration(DURATION_MS);
                animator.setInterpolator(new DecelerateInterpolator());
                animator.addUpdateListener(a -> {
                    span.alpha = (float) a.getAnimatedValue();
                    edit.invalidate();
                });
                animator.addListener(new android.animation.AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(android.animation.Animator animation) {
                        done();
                    }

                    @Override
                    public void onAnimationCancel(android.animation.Animator animation) {
                        done();
                    }

                    private void done() {
                        try {
                            text.removeSpan(span);
                        } catch (Throwable ignored) {
                        }
                        edit.invalidate();
                    }
                });
                animator.start();
            }
        });
    }

    /** Прозрачность буквы от 0 до 1. */
    private static final class Fade extends CharacterStyle implements UpdateAppearance {
        float alpha;

        @Override
        public void updateDrawState(TextPaint paint) {
            paint.setAlpha((int) (paint.getAlpha() * alpha));
        }
    }
}
