package app.heartgram;

import org.telegram.messenger.ApplicationLoader;

import tw.nekomimi.nekogram.utils.AndroidUtil;

/**
 * Анонимная статистика (Firebase Analytics). По умолчанию выключена и в манифесте тоже: сбор включается
 * только если в сборке свой проект Firebase и человек сам включил переключатель в «Прочее».
 */
public final class HeartGramAnalytics {

    private HeartGramAnalytics() {
    }

    public static boolean enabled() {
        return AndroidUtil.hasOwnFirebase() && HeartGramPrefs.prefs().getBoolean("analytics", false);
    }

    /** Применяет текущее значение переключателя к Firebase Analytics. */
    public static void apply() {
        try {
            com.google.firebase.analytics.FirebaseAnalytics.getInstance(ApplicationLoader.applicationContext)
                    .setAnalyticsCollectionEnabled(enabled());
        } catch (Throwable ignore) {
        }
    }
}
