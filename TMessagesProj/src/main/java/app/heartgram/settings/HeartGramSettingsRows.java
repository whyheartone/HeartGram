package app.heartgram.settings;

import android.content.SharedPreferences;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

import app.heartgram.HeartGramLabels;
import app.heartgram.HeartGramPrefs;
import app.heartgram.animations.MediaGlow;
import app.heartgram.animations.MessageAnimations;
import app.heartgram.animations.TextAnimation;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

/**
 * Строки HeartGram (анимация сообщений, анимация набора текста, свечение медиа), которые встраиваются
 * в экран «Чаты». Отдельного раздела нет: экран сам спрашивает у этого класса количество строк,
 * тип, содержимое и реакцию на нажатие.
 */
public final class HeartGramSettingsRows {

    private enum Kind {HEADER, TOGGLE, CHOICE, INFO}

    private static final class Row {
        final Kind kind;
        final String key;
        final String title;
        final String[] options;
        final int def;
        final boolean defBool;
        final IntConsumer onPick;
        final Runnable onToggle;

        Row(Kind kind, String key, String title, String[] options, int def, boolean defBool, IntConsumer onPick, Runnable onToggle) {
            this.kind = kind;
            this.key = key;
            this.title = title;
            this.options = options;
            this.def = def;
            this.defBool = defBool;
            this.onPick = onPick;
            this.onToggle = onToggle;
        }
    }

    private final List<Row> rows = new ArrayList<>();

    private static SharedPreferences p() {
        return HeartGramPrefs.prefs();
    }

    private static String tr(String ru, String en) {
        return HeartGramLabels.tr(ru, en);
    }

    private static Row header(String title) {
        return new Row(Kind.HEADER, null, title, null, 0, false, null, null);
    }

    private static Row info(String text) {
        return new Row(Kind.INFO, null, text, null, 0, false, null, null);
    }

    private static Row toggle(String key, String title, boolean def) {
        return new Row(Kind.TOGGLE, key, title, null, 0, def, null, null);
    }

    private static Row toggle(String key, String title, boolean def, Runnable onToggle) {
        return new Row(Kind.TOGGLE, key, title, null, 0, def, null, onToggle);
    }

    private static Row choice(String key, String title, String[] options, int def) {
        return new Row(Kind.CHOICE, key, title, options, def, false, null, null);
    }

    private static Row choice(String key, String title, String[] options, int def, IntConsumer onPick) {
        return new Row(Kind.CHOICE, key, title, options, def, false, onPick, null);
    }

    /** Пересобирает список строк по текущим настройкам. */
    public void build() {
        rows.clear();
        rows.add(header(tr("Анимация сообщений", "Message animation")));
        rows.add(toggle("am_enabled", tr("Анимация сообщений", "Message animation"), false));
        if (p().getBoolean("am_enabled", false)) {
            boolean follow = p().getBoolean("am_follow", true);
            rows.add(choice("am_preset", tr("Пресет", "Preset"), MessageAnimations.PRESETS, 1));
            rows.add(choice("am_intensity", tr("Интенсивность", "Intensity"), MessageAnimations.INTENSITIES, 1));
            rows.add(toggle("am_follow", tr("Пресет задаёт стиль", "Preset sets the style"), true));
            if (!follow) {
                rows.add(choice("am_style", tr("Стиль входящих", "Incoming style"), MessageAnimations.STYLES, 4));
                rows.add(choice("am_speed", tr("Скорость входящих", "Incoming speed"), MessageAnimations.SPEEDS, 1));
            }
            rows.add(toggle("am_out_separate", tr("Исходящие отдельно", "Outgoing separately"), true));
            if (p().getBoolean("am_out_separate", true)) {
                rows.add(choice("am_out_style", tr("Стиль исходящих", "Outgoing style"), MessageAnimations.STYLES, 1));
                rows.add(choice("am_out_speed", tr("Скорость исходящих", "Outgoing speed"), MessageAnimations.SPEEDS, 1));
                rows.add(toggle("am_out_mirror", tr("Исходящие зеркально", "Mirror outgoing"), true));
            }
            rows.add(toggle("am_dates", tr("Анимировать даты", "Animate dates"), true));
            if (p().getBoolean("am_dates", true) && !follow) {
                rows.add(choice("am_date_style", tr("Стиль дат", "Date style"), MessageAnimations.DATE_STYLES, 0));
                rows.add(choice("am_date_size", tr("Размер отскока", "Bounce size"), MessageAnimations.DATE_SIZES, 1));
                rows.add(choice("am_date_speed", tr("Скорость дат", "Date speed"), MessageAnimations.DATE_SPEEDS, 1));
            }
            rows.add(toggle("am_replay", tr("Повторять при прокрутке назад", "Replay when scrolling back"), false));
            rows.add(toggle("am_comfort", tr("Меньше движения", "Reduce motion"), false));
            rows.add(toggle("am_random", tr("Свой стиль в каждом чате", "A different style in each chat"), false));
            if (p().getBoolean("am_random", false)) {
                rows.add(choice("am_random_mode", tr("Что выбирать случайно", "What to randomize"), MessageAnimations.RANDOM_MODES, 1));
            }
        }
        rows.add(info(tr("Сообщения появляются выбранным стилем, когда попадают на экран. Без «Повторять при прокрутке назад» уже показанные сообщения второй раз не анимируются.",
                "Messages appear with the chosen style when they come into view. Without “Replay when scrolling back”, messages that were already shown are not animated again.")));

        rows.add(header(tr("Анимация текста", "Typing animation")));
        rows.add(toggle("ta_enabled", tr("Анимация набора текста", "Typing animation"), false));
        if (p().getBoolean("ta_enabled", false)) {
            rows.add(choice("ta_preset", tr("Пресет", "Preset"), TextAnimation.PRESETS, 1, TextAnimation::applyPreset));
            rows.add(choice("ta_character", tr("Характер появления", "Appearance"), TextAnimation.CHARACTERS, 3));
            rows.add(choice("ta_duration", tr("Длительность", "Duration"), TextAnimation.DURATIONS, 1));
            rows.add(toggle("ta_blur", tr("Из размытия", "From blur"), true));
            rows.add(toggle("ta_delete", tr("Анимация стирания", "Deletion animation"), true));
        }
        rows.add(info(tr("Каждая новая буква в поле ввода появляется плавно.", "Each new letter in the input field appears smoothly.")));

        rows.add(header(tr("Свечение медиа", "Media glow")));
        rows.add(toggle("media_glow", tr("Свечение медиа", "Media glow"), false, MediaGlow::refreshEnabled));
        if (p().getBoolean("media_glow", false)) {
            rows.add(choice("media_glow_size", tr("Размер свечения", "Glow size"), MediaGlow.SIZES, 2));
            rows.add(toggle("media_glow_video", tr("Свечение под видео и GIF", "Glow under video and GIF"), true));
        }
        rows.add(info(tr("За фото в просмотре появляется мягкий ореол его цветов.", "A soft halo of the photo's colors appears behind it in the viewer.")));
    }

    public int count() {
        return rows.size();
    }

    /** Тип строки в терминах BaseNekoSettingsActivity. */
    public int viewType(int i) {
        switch (rows.get(i).kind) {
            case HEADER:
                return BaseNekoSettingsActivity.TYPE_HEADER;
            case CHOICE:
                return BaseNekoSettingsActivity.TYPE_SETTINGS;
            case INFO:
                return BaseNekoSettingsActivity.TYPE_INFO_PRIVACY;
            default:
                return BaseNekoSettingsActivity.TYPE_CHECK;
        }
    }

    private boolean isLast(int i) {
        return i + 1 >= rows.size() || rows.get(i + 1).kind == Kind.HEADER || rows.get(i + 1).kind == Kind.INFO;
    }

    public void bind(RecyclerView.ViewHolder holder, int i) {
        if (i < 0 || i >= rows.size()) {
            return;
        }
        Row row = rows.get(i);
        View v = holder.itemView;
        if (v instanceof HeaderCell) {
            ((HeaderCell) v).setText(row.title);
        } else if (v instanceof TextCheckCell) {
            ((TextCheckCell) v).setTextAndCheck(row.title, p().getBoolean(row.key, row.defBool), !isLast(i));
        } else if (v instanceof TextSettingsCell) {
            int value = Math.max(0, Math.min(p().getInt(row.key, row.def), row.options.length - 1));
            ((TextSettingsCell) v).setTextAndValue(row.title, row.options[value], !isLast(i));
        } else if (v instanceof TextInfoPrivacyCell) {
            TextInfoPrivacyCell cell = (TextInfoPrivacyCell) v;
            cell.setText(row.title);
            cell.setBackground(Theme.getThemedDrawable(v.getContext(), R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
        }
    }

    /** Обрабатывает нажатие; rebuild вызывается, когда набор строк мог измениться. */
    public void click(BaseFragment fragment, View view, int i, Runnable rebuild) {
        if (i < 0 || i >= rows.size()) {
            return;
        }
        final Row row = rows.get(i);
        if (row.kind == Kind.TOGGLE) {
            boolean value = !p().getBoolean(row.key, row.defBool);
            p().edit().putBoolean(row.key, value).apply();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(value);
            }
            if (row.onToggle != null) {
                row.onToggle.run();
            }
            rebuild.run();
        } else if (row.kind == Kind.CHOICE && fragment.getParentActivity() != null) {
            new AlertDialog.Builder(fragment.getParentActivity())
                    .setTitle(row.title)
                    .setItems(row.options, (d, which) -> {
                        p().edit().putInt(row.key, which).apply();
                        if (row.onPick != null) {
                            row.onPick.accept(which);
                        }
                        rebuild.run();
                    })
                    .setNegativeButton(tr("Отмена", "Cancel"), null)
                    .show();
        }
    }
}
