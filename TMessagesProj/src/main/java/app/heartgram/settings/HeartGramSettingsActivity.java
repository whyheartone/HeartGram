package app.heartgram.settings;

import android.content.Context;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;

import app.heartgram.HeartGramPrefs;
import app.heartgram.animations.MediaGlow;
import app.heartgram.animations.MessageAnimations;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

/**
 * Раздел HeartGram: анимации сообщений и набора текста, свечение медиа. Всё по умолчанию выключено.
 */
public class HeartGramSettingsActivity extends BaseNekoSettingsActivity {

    private int animHeaderRow;
    private int animMessagesRow;
    private int animStyleRow;
    private int animSpeedRow;
    private int animTypingRow;
    private int animDividerRow;
    private int glowHeaderRow;
    private int glowRow;
    private int glowSizeRow;
    private int glowDividerRow;

    private static boolean ru() {
        return "ru".equals(LocaleController.getInstance().getCurrentLocale().getLanguage());
    }

    private static String tr(String ru, String en) {
        return ru() ? ru : en;
    }

    @Override
    protected void updateRows() {
        super.updateRows();
        animHeaderRow = addRow("hgAnimHeader");
        animMessagesRow = addRow("hgAnimMessages");
        animStyleRow = addRow("hgAnimStyle");
        animSpeedRow = addRow("hgAnimSpeed");
        animTypingRow = addRow("hgAnimTyping");
        animDividerRow = addRow();
        glowHeaderRow = addRow("hgGlowHeader");
        glowRow = addRow("hgGlow");
        glowSizeRow = addRow("hgGlowSize");
        glowDividerRow = addRow();
    }

    @Override
    protected String getActionBarTitle() {
        return "HeartGram";
    }

    @Override
    protected String getKey() {
        return "heartgram_settings";
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    private void choose(String title, String[] items, int current, java.util.function.IntConsumer onSelected) {
        if (getParentActivity() == null) {
            return;
        }
        new AlertDialog.Builder(getParentActivity())
                .setTitle(title)
                .setItems(items, (d, which) -> {
                    onSelected.accept(which);
                    if (listAdapter != null) {
                        listAdapter.notifyDataSetChanged();
                    }
                })
                .setNegativeButton(tr("Отмена", "Cancel"), null)
                .show();
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == animMessagesRow) {
            boolean value = !HeartGramPrefs.animMessages();
            HeartGramPrefs.setAnimMessages(value);
            ((TextCheckCell) view).setChecked(value);
        } else if (position == animTypingRow) {
            boolean value = !HeartGramPrefs.animTyping();
            HeartGramPrefs.setAnimTyping(value);
            ((TextCheckCell) view).setChecked(value);
        } else if (position == glowRow) {
            boolean value = !HeartGramPrefs.mediaGlow();
            HeartGramPrefs.setMediaGlow(value);
            ((TextCheckCell) view).setChecked(value);
        } else if (position == animStyleRow) {
            choose(tr("Стиль анимации", "Animation style"), ru() ? MessageAnimations.STYLES_RU : MessageAnimations.STYLES_EN,
                    HeartGramPrefs.animStyle(), HeartGramPrefs::setAnimStyle);
        } else if (position == animSpeedRow) {
            choose(tr("Скорость", "Speed"), ru() ? MessageAnimations.SPEEDS_RU : MessageAnimations.SPEEDS_EN,
                    HeartGramPrefs.animSpeed(), HeartGramPrefs::setAnimSpeed);
        } else if (position == glowSizeRow) {
            choose(tr("Размер свечения", "Glow size"), ru() ? MediaGlow.SIZES_RU : MediaGlow.SIZES_EN,
                    HeartGramPrefs.glowSize(), HeartGramPrefs::setGlowSize);
        }
    }

    private class ListAdapter extends BaseListAdapter {

        ListAdapter(Context context) {
            super(context);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position, boolean partial) {
            switch (holder.getItemViewType()) {
                case TYPE_HEADER: {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    cell.setText(position == animHeaderRow ? tr("Анимации", "Animations") : tr("Просмотр медиа", "Media viewer"));
                    break;
                }
                case TYPE_CHECK: {
                    TextCheckCell cell = (TextCheckCell) holder.itemView;
                    if (position == animMessagesRow) {
                        cell.setTextAndCheck(tr("Анимация сообщений", "Message animation"), HeartGramPrefs.animMessages(), true);
                    } else if (position == animTypingRow) {
                        cell.setTextAndCheck(tr("Анимация набора текста", "Typing animation"), HeartGramPrefs.animTyping(), false);
                    } else if (position == glowRow) {
                        cell.setTextAndCheck(tr("Свечение медиа", "Media glow"), HeartGramPrefs.mediaGlow(), true);
                    }
                    break;
                }
                case TYPE_SETTINGS: {
                    TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                    if (position == animStyleRow) {
                        String[] list = ru() ? MessageAnimations.STYLES_RU : MessageAnimations.STYLES_EN;
                        cell.setTextAndValue(tr("Стиль", "Style"), list[Math.min(HeartGramPrefs.animStyle(), list.length - 1)], true);
                    } else if (position == animSpeedRow) {
                        String[] list = ru() ? MessageAnimations.SPEEDS_RU : MessageAnimations.SPEEDS_EN;
                        cell.setTextAndValue(tr("Скорость", "Speed"), list[Math.min(HeartGramPrefs.animSpeed(), list.length - 1)], true);
                    } else if (position == glowSizeRow) {
                        String[] list = ru() ? MediaGlow.SIZES_RU : MediaGlow.SIZES_EN;
                        cell.setTextAndValue(tr("Размер", "Size"), list[Math.min(HeartGramPrefs.glowSize(), list.length - 1)], false);
                    }
                    break;
                }
                case TYPE_INFO_PRIVACY: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    cell.setText(position == animDividerRow
                            ? tr("Сообщения появляются выбранным стилем, когда попадают на экран.", "Messages appear with the chosen style when they come into view.")
                            : tr("За фото в просмотре появляется мягкий ореол его цветов.", "A soft halo of the photo's colors appears behind it in the viewer."));
                    cell.setBackground(Theme.getThemedDrawable(mContext, org.telegram.messenger.R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == animHeaderRow || position == glowHeaderRow) {
                return TYPE_HEADER;
            } else if (position == animStyleRow || position == animSpeedRow || position == glowSizeRow) {
                return TYPE_SETTINGS;
            } else if (position == animDividerRow || position == glowDividerRow) {
                return TYPE_INFO_PRIVACY;
            }
            return TYPE_CHECK;
        }
    }
}
