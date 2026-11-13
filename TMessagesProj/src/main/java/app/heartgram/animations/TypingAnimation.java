package app.heartgram.animations;

import android.widget.EditText;

/** Оставлен для совместимости: анимация набора теперь в {@link TextAnimation}. */
public final class TypingAnimation {

    private TypingAnimation() {
    }

    public static void attach(EditText edit) {
        TextAnimation.attach(edit);
    }
}
