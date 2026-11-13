package app.heartgram.animations;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.content.SharedPreferences;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import org.telegram.messenger.MessageObject;
import org.telegram.ui.Cells.ChatMessageCell;

import app.heartgram.HeartGramPrefs;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;


/**
 * Перенос плагина «Animated Messages» (Natu, v2.6): сообщения и плашки дат анимируются
 * при первом появлении на экране (onAttachedToWindow), те же стили, пресеты и формулы.
 */
public final class MessageAnimations {
    public static final String[] STYLES = {"Мягкое (появление + масштаб)", "Справа", "Снизу", "3D-поворот",
            "Пружинный подъём", "Вертолёт", "Наклонный хлопок", "Удар", "Слева", "Прыгающий поворот",
            "Приближение", "Покачивание"};
    public static final String[] DATE_STYLES = {"Хлопок (подъём + отскок)", "Сдвиг + появление", "Переворот", "Пульс"};
    public static final String[] SPEEDS = {"Медленно (x0.5)", "Обычно (x1.0)", "Быстро (x1.5)", "Ультра (x2.0)"};
    public static final String[] DATE_SIZES = {"Обычный", "Большой", "Огромный", "Ультра"};
    public static final String[] DATE_SPEEDS = {"Спокойно", "Обычно", "Резко", "Мгновенно"};
    public static final String[] PRESETS = {"Минимальный", "Сбалансированный", "Динамичный", "Безумный"};
    public static final String[] INTENSITIES = {"Низкая (50%)", "Обычная (100%)", "Высокая (140%)", "Безумная (180%)"};
    public static final String[] RANDOM_MODES = {"Выключено", "Только стиль", "Стиль и скорость", "Всё, включая даты"};

    private static final float[] SPEED = {0.5f, 1f, 1.5f, 2f};
    private static final float[] DATE_TENSIONS = {2f, 4f, 6f, 8f};
    private static final int[] DATE_DURATIONS = {820, 600, 420, 260};
    private static final float[] INTENSITY = {0.5f, 1f, 1.4f, 1.8f};
    private static final long RECENT_ACTION_WINDOW_MS = 350;

    private static final HashSet<Integer> animatedIds = new HashSet<>();
    private static final HashMap<Integer, Long> recentAction = new HashMap<>();
    private static final HashMap<String, int[]> chatRandom = new HashMap<>();

    private MessageAnimations() {
    }

    static SharedPreferences prefs() {
        return HeartGramPrefs.prefs();
    }

    private static boolean b(String k, boolean d) {
        return prefs().getBoolean(k, d);
    }

    private static int idx(String k, int d, int n) {
        return clamp(prefs().getInt(k, d), 0, n - 1);
    }

    public static boolean enabled() {
        return b("am_enabled", false); // по умолчанию как в Telegram: без анимации
    }

    static int preset() {
        return idx("am_preset", 1, PRESETS.length);
    }

    static boolean followPreset() {
        return b("am_follow", true);
    }

    static int intensityIdx() {
        return idx("am_intensity", 1, INTENSITIES.length);
    }

    static boolean comfort() {
        return b("am_comfort", false);
    }

    static boolean darkSafe() {
        return b("am_dark_safe", true);
    }

    // ── пресеты: стиль, скорость, стиль дат, скорость дат, размер дат, отскок ──
    private static float[] profile() {
        switch (preset()) {
            case 0:
                return new float[]{0, 1, 1, 2, 0, 1.10f};
            case 1:
                return new float[]{4, 1, 0, 1, 1, 1.70f};
            case 2:
                return new float[]{6, 2, 0, 1, 2, 2.10f};
            default:
                return new float[]{9, 3, 2, 3, 3, 2.70f};
        }
    }

    private static int hash(String s) {
        long v = 2166136261L;
        for (int i = 0; i < s.length(); i++) {
            v = ((v ^ s.charAt(i)) * 16777619L) & 0xFFFFFFFFL;
        }
        return (int) v;
    }

    private static int pick(int seed, int n) {
        long s = seed & 0xFFFFFFFFL;
        s = (s * 1103515245L + 12345L) & 0x7FFFFFFFL;
        return (int) (s % n);
    }

    private static int[] perChat(long dialog, String dir) {
        String key = dialog + ":" + dir;
        int[] r = chatRandom.get(key);
        if (r != null) {
            return r;
        }
        if (chatRandom.size() > 500) {
            chatRandom.clear();
        }
        int seed = hash(dialog + ":" + dir + ":" + preset());
        r = new int[]{pick(seed ^ 0xA53, STYLES.length), pick(seed ^ 0xB19, SPEEDS.length),
                pick(seed ^ 0xC77, DATE_STYLES.length), pick(seed ^ 0xD31, DATE_SPEEDS.length),
                pick(seed ^ 0xE11, DATE_SIZES.length)};
        chatRandom.put(key, r);
        return r;
    }

    /** {стиль, скорость, стиль дат, скорость дат, размер дат}, интенсивность, исходящее. */
    private static final class Eff {
        int ms, sp, ds, dsp, dsz;
        float it;
        boolean out;
    }

    private static Eff effective(View cell) {
        float[] p = profile();
        Eff e = new Eff();
        if (followPreset()) {
            e.ms = (int) p[0];
            e.sp = (int) p[1];
            e.ds = (int) p[2];
            e.dsp = (int) p[3];
            e.dsz = (int) p[4];
        } else {
            e.ms = prefs().getInt("am_style", (int) p[0]);
            e.sp = prefs().getInt("am_speed", (int) p[1]);
            e.ds = prefs().getInt("am_date_style", (int) p[2]);
            e.dsp = prefs().getInt("am_date_speed", (int) p[3]);
            e.dsz = prefs().getInt("am_date_size", (int) p[4]);
        }
        e.it = INTENSITY[intensityIdx()];
        if (cell != null) {
            e.out = outgoing(cell);
            if (e.out && b("am_out_separate", true)) {
                e.ms = prefs().getInt("am_out_style", e.ms);
                e.sp = prefs().getInt("am_out_speed", e.sp);
                e.it *= 0.85f;
            }
            if (b("am_random", false)) {
                int[] r = perChat(dialogId(cell), e.out ? "out" : "in");
                int mode = idx("am_random_mode", 1, RANDOM_MODES.length);
                if (mode >= 1) {
                    e.ms = r[0];
                }
                if (mode >= 2) {
                    e.sp = r[1];
                }
                if (mode >= 3) {
                    e.ds = r[2];
                    e.dsp = r[3];
                    e.dsz = r[4];
                }
            }
        }
        e.ms = clamp(e.ms, 0, STYLES.length - 1);
        e.sp = clamp(e.sp, 0, SPEEDS.length - 1);
        e.ds = clamp(e.ds, 0, DATE_STYLES.length - 1);
        e.dsp = clamp(e.dsp, 0, DATE_SPEEDS.length - 1);
        e.dsz = clamp(e.dsz, 0, DATE_SIZES.length - 1);
        boolean rm = comfort();
        if (rm && (e.ms == 3 || e.ms == 5 || e.ms == 9 || e.ms == 11)) {
            e.ms = followPreset() ? 0 : 4;
        }
        if (rm && e.ds == 2) {
            e.ds = 1;
        }
        return e;
    }

    private static MessageObject messageObject(View cell) {
        return cell instanceof ChatMessageCell ? ((ChatMessageCell) cell).getMessageObject() : null;
    }

    private static boolean outgoing(View cell) {
        MessageObject m = messageObject(cell);
        return m != null && m.isOutOwner();
    }

    private static long dialogId(View cell) {
        MessageObject m = messageObject(cell);
        return m == null ? 0 : m.getDialogId();
    }

    /** Из ChatMessageCell.onAttachedToWindow. */
    public static void onAttachMessage(ChatMessageCell v) {
        if (!enabled() || !b("am_msgs", true)) {
            return;
        }
        try {
            MessageObject m = v.getMessageObject();
            int id = m == null ? 0 : m.getId();
            if (id != 0 && !b("am_replay", false)) {
                if (animatedIds.contains(id)) {
                    return;
                }
                if (animatedIds.size() > 600) {
                    animatedIds.clear();
                }
                animatedIds.add(id);
            }
            whenMeasured(v, () -> animMessage(v), 6);
        } catch (Throwable t) {
            android.util.Log.w("HeartGramAnim", "анимация сообщения: " + t);
        }
    }

    /** Из ChatActionCell.onAttachedToWindow (плашки дат и служебные). */
    public static void onAttachDate(View v) {
        if (!enabled() || !b("am_dates", true)) {
            return;
        }
        whenMeasured(v, () -> animDate(v), 6);
    }

    private static void whenMeasured(View v, Runnable fn, int tries) {
        if (!b("am_measured", true) || (v.getWidth() > 2 && v.getHeight() > 2) || tries <= 0) {
            v.post(fn);
            return;
        }
        v.postDelayed(() -> whenMeasured(v, fn, tries - 1), 16);
    }

    private static int clamp(int v, int a, int b) {
        return Math.max(a, Math.min(b, v));
    }

    private static float clampf(float v, float a, float b) {
        return Math.max(a, Math.min(b, v));
    }

    private static void reset(View v) {
        v.setAlpha(1f);
        v.setTranslationX(0f);
        v.setTranslationY(0f);
        v.setScaleX(1f);
        v.setScaleY(1f);
        v.setRotation(0f);
        v.setRotationX(0f);
        v.setRotationY(0f);
    }

    private static ObjectAnimator of(View v, String prop, float... values) {
        return ObjectAnimator.ofFloat(v, prop, values);
    }

    private static void animMessage(View v) {
        try {
            Eff e = effective(v);
            boolean rm = comfort(), dsafe = darkSafe();
            boolean mir = b("am_out_mirror", true);
            int dur = (int) (620 / SPEED[e.sp]);
            float itc = clampf(e.it, 0.5f, 2f);
            dur = (int) (dur * (1f / Math.max(0.75f, Math.min(itc, 1.6f))));
            dur = clamp(dur, 170, 920);
            v.animate().cancel();
            reset(v);
            float w = v.getWidth() > 0 ? v.getWidth() : 540, h = v.getHeight() > 0 ? v.getHeight() : 220;
            float sign = e.out && mir ? -1f : 1f;
            float a0 = dsafe ? 0.05f : 0f;
            List<Animator> anims = new ArrayList<>();
            anims.add(of(v, "alpha", a0, 1f));
            TimeInterpolator interp = new DecelerateInterpolator();
            switch (e.ms) {
                case 0: {
                    float s0 = rm || dsafe ? 0.90f : (0.86f - 0.08f * (itc - 1f));
                    s0 = clampf(s0, dsafe ? 0.78f : 0.65f, 0.94f);
                    v.setScaleX(s0);
                    v.setScaleY(s0);
                    anims.add(of(v, "scaleX", s0, 1f));
                    anims.add(of(v, "scaleY", s0, 1f));
                    break;
                }
                case 1: {
                    float dx = w * 0.55f * itc * sign * (rm ? 0.55f : 1f);
                    v.setTranslationX(dx);
                    anims.add(of(v, "translationX", dx, 0f));
                    break;
                }
                case 2: {
                    float dy = h * 1.05f * itc * (rm ? 0.55f : 1f);
                    v.setTranslationY(dy);
                    anims.add(of(v, "translationY", dy, 0f));
                    break;
                }
                case 3: {
                    float ang = (dsafe ? 55f : 90f) * itc * sign * (rm ? 0.55f : 1f);
                    float s0 = dsafe ? 0.96f : 0.90f;
                    v.setScaleX(s0);
                    v.setScaleY(s0);
                    v.setRotationY(ang);
                    anims.add(of(v, "rotationY", ang, 0f));
                    anims.add(of(v, "scaleX", s0, 1f));
                    anims.add(of(v, "scaleY", s0, 1f));
                    interp = new AccelerateDecelerateInterpolator();
                    break;
                }
                case 4: {
                    float dy = h * 0.32f * itc * (rm ? 0.55f : 1f);
                    v.setTranslationY(dy);
                    anims.add(of(v, "translationY", dy, 0f));
                    float ov = rm || dsafe ? 1.15f : 1.8f * (0.85f + 0.22f * itc);
                    interp = new OvershootInterpolator(ov);
                    break;
                }
                case 5: {
                    if (rm || dsafe) {
                        v.setScaleX(0.90f);
                        v.setScaleY(0.90f);
                        anims.add(of(v, "scaleX", 0.90f, 1f));
                        anims.add(of(v, "scaleY", 0.90f, 1f));
                    } else {
                        float rot = 200f * itc * sign;
                        v.setRotation(rot);
                        v.setScaleX(0.55f);
                        v.setScaleY(0.55f);
                        anims.add(of(v, "rotation", rot, 0f));
                        anims.add(of(v, "scaleX", 0.55f, 1f));
                        anims.add(of(v, "scaleY", 0.55f, 1f));
                        interp = new AccelerateDecelerateInterpolator();
                    }
                    break;
                }
                case 6: {
                    float ang = (10f + 8f * itc) * sign * (rm ? 0.55f : 1f);
                    float s0 = dsafe ? 0.92f : 0.86f;
                    v.setRotation(ang);
                    v.setScaleX(s0);
                    v.setScaleY(s0);
                    anims.add(of(v, "rotation", ang, 0f));
                    anims.add(of(v, "scaleX", s0, 1f));
                    anims.add(of(v, "scaleY", s0, 1f));
                    interp = new OvershootInterpolator(rm || dsafe ? 1.05f : 1.6f);
                    break;
                }
                case 7: {
                    float s0 = dsafe ? 0.90f : 0.78f;
                    v.setScaleX(s0);
                    v.setScaleY(s0);
                    anims.add(of(v, "scaleX", s0, 1f));
                    anims.add(of(v, "scaleY", s0, 1f));
                    interp = new OvershootInterpolator(rm || dsafe ? 1.1f : 2.2f);
                    break;
                }
                case 8: {
                    float dx = -w * 0.55f * itc * sign * (rm ? 0.55f : 1f);
                    v.setTranslationX(dx);
                    anims.add(of(v, "translationX", dx, 0f));
                    break;
                }
                case 9: {
                    float ang = (dsafe ? 35f : 60f) * sign * (rm ? 0.55f : 1f);
                    float s0 = dsafe ? 0.92f : 0.72f;
                    v.setRotationX(ang);
                    v.setScaleX(s0);
                    v.setScaleY(s0);
                    anims.add(of(v, "rotationX", ang, 0f));
                    anims.add(of(v, "scaleX", s0, 1f));
                    anims.add(of(v, "scaleY", s0, 1f));
                    interp = new OvershootInterpolator(rm || dsafe ? 1.1f : 2.1f);
                    break;
                }
                case 10: {
                    float s0 = dsafe ? 0.92f : 0.70f;
                    float dy = h * 0.16f * itc * (rm ? 0.55f : 1f);
                    v.setScaleX(s0);
                    v.setScaleY(s0);
                    v.setTranslationY(dy);
                    anims.add(of(v, "scaleX", s0, 1f));
                    anims.add(of(v, "scaleY", s0, 1f));
                    anims.add(of(v, "translationY", dy, 0f));
                    break;
                }
                default: {
                    float dx = w * 0.20f * itc * sign * (rm ? 0.55f : 1f);
                    float a1 = (dsafe ? 5f : 8f) * sign * (rm ? 0.55f : 1f);
                    v.setTranslationX(dx);
                    v.setRotation(a1);
                    anims.add(of(v, "translationX", dx, 0f));
                    anims.add(of(v, "rotation", a1, -a1, a1 * 0.5f, 0f));
                    interp = new AccelerateDecelerateInterpolator();
                    break;
                }
            }
            AnimatorSet s = new AnimatorSet();
            s.playTogether(anims);
            s.setDuration(dur);
            s.setInterpolator(interp);
            s.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    reset(v);
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    reset(v);
                }
            });
            s.start();
        } catch (Throwable t) {
            android.util.Log.w("HeartGramAnim", "анимация сообщения: " + t);
        }
    }

    private static void animDate(View v) {
        try {
            int key = System.identityHashCode(v);
            long now = System.currentTimeMillis();
            Long last = recentAction.get(key);
            if (last != null && now - last < RECENT_ACTION_WINDOW_MS) {
                return;
            }
            recentAction.put(key, now);
            if (recentAction.size() > 800) {
                recentAction.clear();
            }
            Eff e = effective(null);
            float pOv = profile()[5];
            boolean rm = comfort(), dsafe = darkSafe();
            v.animate().cancel();
            reset(v);
            float a0 = dsafe ? 0.05f : 0f;
            int dur = clamp(DATE_DURATIONS[e.dsp], 180, 1200);
            float[] extras = {0f, 0.12f, 0.25f, 0.45f};
            float extra = extras[e.dsz] * (0.85f + 0.25f * e.it) * (rm ? 0.45f : 1f);
            float pop = 1f + extra;
            float w = v.getWidth() > 0 ? v.getWidth() : 520, h = v.getHeight() > 0 ? v.getHeight() : 180;
            if (e.ds == 0) {
                float startY = rm ? 40f : 90f * (0.85f + 0.25f * e.it);
                float startS = rm || dsafe ? 0.92f : 0.65f;
                v.setAlpha(a0);
                v.setScaleX(startS);
                v.setScaleY(startS);
                v.setTranslationY(startY);
                AnimatorSet popSet = new AnimatorSet();
                popSet.playTogether(of(v, "translationY", startY, 0f), of(v, "scaleX", startS, pop),
                        of(v, "scaleY", startS, pop), of(v, "alpha", a0, 1f));
                popSet.setDuration(dur);
                float ov = DATE_TENSIONS[e.dsz] * (0.90f + 0.18f * e.it) * (pOv / 1.7f);
                popSet.setInterpolator(new OvershootInterpolator(rm || dsafe ? 1.05f : ov));
                AnimatorSet settle = new AnimatorSet();
                settle.playTogether(of(v, "scaleX", pop, 1f), of(v, "scaleY", pop, 1f));
                settle.setDuration(rm ? 120 : 160);
                settle.setInterpolator(new DecelerateInterpolator());
                AnimatorSet seq = new AnimatorSet();
                seq.playSequentially(popSet, settle);
                seq.start();
            } else if (e.ds == 1) {
                float startY = h * 0.35f * (0.85f + 0.25f * e.it) * (rm ? 0.55f : 1f);
                v.setAlpha(a0);
                v.setTranslationY(startY);
                AnimatorSet s = new AnimatorSet();
                s.playTogether(of(v, "translationY", startY, 0f), of(v, "alpha", a0, 1f));
                s.setDuration(dur);
                s.setInterpolator(new DecelerateInterpolator());
                s.start();
            } else if (e.ds == 2) {
                float ang = (dsafe ? 55f : 75f) * (0.85f + 0.20f * e.it) * (rm ? 0.55f : 1f);
                float dx = w * 0.10f * (0.85f + 0.20f * e.it) * (rm ? 0.6f : 1f);
                float s0 = dsafe ? 0.96f : 0.90f;
                v.setAlpha(a0);
                v.setRotationY(ang);
                v.setTranslationX(dx);
                v.setScaleX(s0);
                v.setScaleY(s0);
                AnimatorSet s = new AnimatorSet();
                s.playTogether(of(v, "rotationY", ang, 0f), of(v, "translationX", dx, 0f),
                        of(v, "scaleX", s0, 1f), of(v, "scaleY", s0, 1f), of(v, "alpha", a0, 1f));
                s.setDuration(dur);
                s.setInterpolator(new AccelerateDecelerateInterpolator());
                s.start();
            } else {
                float startS = dsafe ? 0.98f : 0.92f;
                v.setAlpha(a0);
                v.setScaleX(startS);
                v.setScaleY(startS);
                AnimatorSet in = new AnimatorSet();
                in.playTogether(of(v, "alpha", a0, 1f), of(v, "scaleX", startS, 1f), of(v, "scaleY", startS, 1f));
                in.setDuration(clamp((int) (dur * 0.75f), 140, 520));
                in.setInterpolator(new DecelerateInterpolator());
                AnimatorSet pulse = new AnimatorSet();
                pulse.playTogether(of(v, "scaleX", 1f, 1f + extra * 0.35f, 1f), of(v, "scaleY", 1f, 1f + extra * 0.35f, 1f));
                pulse.setDuration(clamp((int) (dur * 0.85f), 180, 700));
                pulse.setInterpolator(new AccelerateDecelerateInterpolator());
                AnimatorSet seq = new AnimatorSet();
                seq.playSequentially(in, pulse);
                seq.start();
            }
        } catch (Throwable t) {
            android.util.Log.w("HeartGramAnim", "анимация даты: " + t);
        }
    }
}
