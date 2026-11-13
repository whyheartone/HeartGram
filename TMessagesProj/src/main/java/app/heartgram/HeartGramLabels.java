package app.heartgram;

import org.telegram.messenger.LocaleController;

/** Короткие подписи на русском и английском для экранов HeartGram. */
public final class HeartGramLabels {

    private HeartGramLabels() {
    }

    public static boolean ru() {
        return "ru".equals(LocaleController.getInstance().getCurrentLocale().getLanguage());
    }

    public static String tr(String ru, String en) {
        return ru() ? ru : en;
    }

    public static String ayu() {
        return tr("AyuGram: призрак и сохранение", "AyuGram: ghost mode and saving");
    }

    public static String privacy() {
        return tr("Приватность", "Privacy");
    }
}
