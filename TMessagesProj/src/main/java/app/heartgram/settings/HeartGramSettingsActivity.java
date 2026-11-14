package app.heartgram.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;

import java.util.ArrayList;
import java.util.List;

import app.heartgram.HeartGramLabels;
import app.heartgram.HeartGramPrefs;
import app.heartgram.wsproxy.WsProxy;
import app.heartgram.wsproxy.WsProxyManager;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

/**
 * Обход блокировок: локальный WebSocket-прокси (tg-ws-proxy, Flowseal, MIT).
 * Трафик клиента идёт через 127.0.0.1 к шлюзам kws*.web.telegram.org.
 */
public class HeartGramSettingsActivity extends BaseNekoSettingsActivity {

    private enum Kind {HEADER, TOGGLE, INFO}

    private static final class Row {
        final Kind kind;
        final String key;
        final String title;
        final boolean def;
        final Runnable onToggle;
        final String subtitle;

        Row(Kind kind, String key, String title, boolean def, Runnable onToggle) {
            this(kind, key, title, def, onToggle, null);
        }

        Row(Kind kind, String key, String title, boolean def, Runnable onToggle, String subtitle) {
            this.kind = kind;
            this.key = key;
            this.title = title;
            this.def = def;
            this.onToggle = onToggle;
            this.subtitle = subtitle;
        }
    }

    private final List<Row> rows = new ArrayList<>();

    private static SharedPreferences p() {
        return HeartGramPrefs.prefs();
    }

    private static String tr(String ru, String en) {
        return HeartGramLabels.tr(ru, en);
    }

    private void build() {
        rows.clear();
        rows.add(new Row(Kind.HEADER, null, tr("Обход блокировок", "Bypass blocking"), false, null));
        rows.add(new Row(Kind.TOGGLE, "wsproxy_enabled", tr("Обход блокировок (WS-прокси)", "Bypass blocking (WS proxy)"), false, () -> {
            WsProxyManager.setEnabled(p().getBoolean("wsproxy_enabled", false));
            AndroidUtilities.runOnUIThread(this::rebuild, 300);
        }));
        rows.add(new Row(Kind.INFO, null, tr(
                "Трафик Telegram идёт через встроенный локальный прокси и далее по WebSocket на серверы Telegram. "
                        + "Сообщения по-прежнему шифруются, прокси их не читает. Основа: tg-ws-proxy (Flowseal).",
                "Telegram traffic goes through a built-in local proxy and then over WebSocket to Telegram servers. "
                        + "Messages stay encrypted, the proxy cannot read them. Based on tg-ws-proxy (Flowseal)."), false, null));
        rows.add(new Row(Kind.TOGGLE, "wsproxy_fronting", tr("Маскировка соединения", "Connection masking"), false, null, tr(
                "В TLS-рукопожатии подставляется другое имя сервера, чтобы соединение труднее было опознать. Сертификат при этом не проверяется. Включайте, только если без неё не подключается.",
                "Another server name is sent in the TLS handshake so the connection is harder to recognize. The certificate is not checked. Enable only if it does not connect without it.")));
        rows.add(new Row(Kind.TOGGLE, "wsproxy_tcp", tr("Запасной прямой путь (TCP)", "Direct fallback (TCP)"), false, null, tr(
                "Если соединение по WebSocket не удалось, трафик идёт к серверам Telegram напрямую, без обхода. Нужен для дата-центров, которые шлюз не обслуживает (медиа, эмодзи). При блокировке может не работать.",
                "If the WebSocket connection fails, traffic goes to Telegram servers directly, without bypass. Needed for data centers the gateway does not serve (media, emoji). May not work under blocking.")));
        rows.add(new Row(Kind.INFO, null, status(), false, null));
    }

    private static String status() {
        if (!WsProxyManager.enabled()) {
            return tr("Выключено.", "Off.");
        }
        return tr("Работает на 127.0.0.1:", "Running on 127.0.0.1:") + WsProxyManager.port()
                + (WsProxy.isRunning() ? "" : tr(" (остановлен)", " (stopped)"))
                + "\n" + tr("Подключений: ", "Connections: ") + WsProxy.connectionsTotal.get()
                + " (WS " + WsProxy.connectionsWs.get() + ", TCP " + WsProxy.connectionsTcp.get()
                + tr(", ошибок ", ", failed ") + WsProxy.connectionsFailed.get() + ")"
                + "\n↑ " + AndroidUtilities.formatFileSize(WsProxy.bytesUp.get())
                + "  ↓ " + AndroidUtilities.formatFileSize(WsProxy.bytesDown.get());
    }

    @Override
    protected void updateRows() {
        super.updateRows();
        build();
        for (Row row : rows) {
            if (row.key != null) {
                addRow(row.key);
            } else {
                addRow();
            }
        }
    }

    @Override
    protected String getActionBarTitle() {
        return tr("Обход блокировок", "Bypass blocking");
    }

    @Override
    protected String getKey() {
        return "heartgram_wsproxy";
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    private void rebuild() {
        updateRows();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position < 0 || position >= rows.size()) {
            return;
        }
        final Row row = rows.get(position);
        if (row.kind == Kind.TOGGLE) {
            boolean value = !p().getBoolean(row.key, row.def);
            p().edit().putBoolean(row.key, value).apply();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(value);
            }
            if (row.onToggle != null) {
                row.onToggle.run();
            }
        }
    }

    private boolean isLast(int position) {
        return position + 1 >= rows.size() || rows.get(position + 1).kind != Kind.TOGGLE;
    }

    private class ListAdapter extends BaseListAdapter {

        ListAdapter(Context context) {
            super(context);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position, boolean partial) {
            if (position < 0 || position >= rows.size()) {
                return;
            }
            Row row = rows.get(position);
            switch (holder.getItemViewType()) {
                case TYPE_HEADER:
                    ((HeaderCell) holder.itemView).setText(row.title);
                    break;
                case TYPE_CHECK:
                    if (row.subtitle != null) {
                        ((TextCheckCell) holder.itemView).setTextAndValueAndCheck(row.title, row.subtitle, p().getBoolean(row.key, row.def), true, !isLast(position));
                    } else {
                        ((TextCheckCell) holder.itemView).setTextAndCheck(row.title, p().getBoolean(row.key, row.def), !isLast(position));
                    }
                    break;
                case TYPE_INFO_PRIVACY: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    cell.setText(row.title);
                    cell.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    break;
                }
                default:
                    break;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position < 0 || position >= rows.size()) {
                return TYPE_SHADOW;
            }
            switch (rows.get(position).kind) {
                case HEADER:
                    return TYPE_HEADER;
                case INFO:
                    return TYPE_INFO_PRIVACY;
                default:
                    return TYPE_CHECK;
            }
        }
    }
}
