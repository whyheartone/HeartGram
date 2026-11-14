package app.heartgram;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

/**
 * Настройки HeartGram: анимации и свечение медиа. Всё выключено, пока человек сам не включит.
 */
public final class HeartGramPrefs {

    private static final String FILE = "heartgram_ui";

    private HeartGramPrefs() {
    }

    public static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static boolean animMessages() {
        return prefs().getBoolean("anim_messages", false);
    }

    public static void setAnimMessages(boolean value) {
        prefs().edit().putBoolean("anim_messages", value).apply();
    }

    /** Номер стиля анимации появления сообщения, см. MessageAnimations.STYLES. */
    public static int animStyle() {
        return prefs().getInt("anim_style", 0);
    }

    public static void setAnimStyle(int value) {
        prefs().edit().putInt("anim_style", value).apply();
    }

    /** 0..3: медленно, обычно, быстро, очень быстро. */
    public static int animSpeed() {
        return prefs().getInt("anim_speed", 1);
    }

    public static void setAnimSpeed(int value) {
        prefs().edit().putInt("anim_speed", value).apply();
    }

    public static boolean animTyping() {
        return prefs().getBoolean("anim_typing", false);
    }

    public static void setAnimTyping(boolean value) {
        prefs().edit().putBoolean("anim_typing", value).apply();
    }

    public static boolean mediaGlow() {
        return prefs().getBoolean("media_glow", false);
    }

    public static void setMediaGlow(boolean value) {
        prefs().edit().putBoolean("media_glow", value).apply();
    }

    /** 0..3: маленькое, среднее, большое, огромное. */
    public static int glowSize() {
        return prefs().getInt("glow_size", 1);
    }

    public static void setGlowSize(int value) {
        prefs().edit().putInt("glow_size", value).apply();
    }

    /** Скрепка отдельной кнопкой над чатом при наборе текста (как в Telegram 13). По умолчанию выключена: скрепка остаётся в поле ввода. */
    public static boolean sideAttach() {
        return prefs().getBoolean("side_attach", false);
    }

    /** Вид папок: 0 как в Telegram, 1 вкладки с индикатором, 2 отдельные кнопки. */
    public static int folderStyle() {
        return Math.max(0, Math.min(2, prefs().getInt("folder_style", 0)));
    }

    public static void setFolderStyle(int value) {
        prefs().edit().putInt("folder_style", value).apply();
    }

    /** Высота кнопки папки в виде «Кнопки», dp. */
    public static int folderPillHeight() {
        return prefs().getInt("folder_pill_h", 32);
    }

    /** В режиме «только иконки» показывать название папки в заголовке. */
    public static boolean folderTitleInHeader() {
        return prefs().getBoolean("folder_title_header", true);
    }
}
