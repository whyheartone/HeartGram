package app.heartgram.tiles;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;

import java.util.ArrayList;
import java.util.List;

/**
 * Живой предпросмотр плиток: рисует настоящие чаты человека (имя, последнее сообщение, время, аватарка) теми же
 * цветом, скруглением и зазором, что и список. Данные берутся только с устройства и никуда не уходят.
 */
public class TilesPreviewView extends View {

    private static final int ROWS = 4, PINNED = 2;

    private static final class Row {
        ImageReceiver avatar;
        String title = "", message = "", time = "";
        boolean pinned;
    }

    private final List<Row> rows = new ArrayList<>();
    private final Paint card = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint messagePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint timePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    public TilesPreviewView(Context context) {
        super(context);
        titlePaint.setTypeface(AndroidUtilities.bold());
        titlePaint.setTextSize(dp(16));
        messagePaint.setTextSize(dp(14));
        timePaint.setTextSize(dp(12));
        load();
    }

    /** Берёт до четырёх настоящих чатов (сначала закреплённые) и готовит аватарки. */
    private void load() {
        rows.clear();
        int account = UserConfig.selectedAccount;
        MessagesController controller = MessagesController.getInstance(account);
        ArrayList<TLRPC.Dialog> dialogs = controller.getAllDialogs();
        List<TLRPC.Dialog> pinned = new ArrayList<>(), other = new ArrayList<>();
        if (dialogs != null) {
            for (TLRPC.Dialog d : dialogs) {
                if (d == null || d.id == 0 || d.folder_id != 0) {
                    continue;
                }
                (d.pinned ? pinned : other).add(d);
            }
        }
        List<TLRPC.Dialog> picked = new ArrayList<>();
        for (int i = 0; i < pinned.size() && picked.size() < PINNED; i++) {
            picked.add(pinned.get(i));
        }
        for (int i = 0; i < other.size() && picked.size() < ROWS; i++) {
            picked.add(other.get(i));
        }
        for (TLRPC.Dialog d : picked) {
            Row row = new Row();
            row.pinned = d.pinned;
            TLObject object = null;
            if (d.id > 0) {
                TLRPC.User user = controller.getUser(d.id);
                if (user != null) {
                    object = user;
                    row.title = UserObject.getUserName(user);
                }
            } else {
                TLRPC.Chat chat = controller.getChat(-d.id);
                if (chat != null) {
                    object = chat;
                    row.title = chat.title == null ? "" : chat.title;
                }
            }
            ArrayList<MessageObject> list = controller.dialogMessage.get(d.id);
            MessageObject message = list == null || list.isEmpty() ? null : list.get(0);
            if (message != null) {
                CharSequence text = message.messageText;
                row.message = text == null ? "" : text.toString().replace('\n', ' ');
                row.time = LocaleController.stringForMessageListDate(message.messageOwner.date);
            }
            row.avatar = new ImageReceiver(this);
            row.avatar.setRoundRadius(dp(26));
            AvatarDrawable drawable = new AvatarDrawable();
            if (object instanceof TLRPC.User) {
                drawable.setInfo(account, (TLRPC.User) object);
                row.avatar.setForUserOrChat(object, drawable);
            } else if (object instanceof TLRPC.Chat) {
                drawable.setInfo(account, (TLRPC.Chat) object);
                row.avatar.setForUserOrChat(object, drawable);
            }
            rows.add(row);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        for (Row r : rows) {
            if (r.avatar != null) {
                r.avatar.onAttachedToWindow();
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        for (Row r : rows) {
            if (r.avatar != null) {
                r.avatar.onDetachedFromWindow();
            }
        }
    }

    private static int rowHeight() {
        return dp(70);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int n = Math.max(rows.size(), 1);
        int h = dp(12) + n * rowHeight() + (n - 1) * dp(ChatTiles.gap()) + dp(8);
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        if (rows.isEmpty()) {
            return;
        }
        boolean tiles = ChatTiles.enabled();
        card.setColor(ChatTiles.tileColor());
        titlePaint.setColor(Theme.getColor(Theme.key_chats_name));
        messagePaint.setColor(Theme.getColor(Theme.key_chats_message));
        timePaint.setColor(Theme.getColor(Theme.key_chats_date));
        float big = dp(ChatTiles.radius()), small = dp(Math.min(6, ChatTiles.radius()));
        int side = dp(8);
        int gap = dp(ChatTiles.gap());
        float y = dp(12);
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            boolean first = i == 0 || rows.get(i - 1).pinned != r.pinned;
            boolean last = i == rows.size() - 1 || rows.get(i + 1).pinned != r.pinned;
            float top = y + (first && i > 0 ? dp(3) : 0);
            float h = rowHeight();
            if (tiles) {
                float upper = first ? big : small, lower = last ? big : small;
                rect.set(side, top, getWidth() - side, top + h);
                path.reset();
                path.addRoundRect(rect, new float[]{upper, upper, upper, upper, lower, lower, lower, lower}, Path.Direction.CW);
                canvas.drawPath(path, card);
            }
            int ax = dp(tiles ? 18 : 14);
            if (r.avatar != null) {
                r.avatar.setImageCoords(ax, top + (h - dp(52)) / 2f, dp(52), dp(52));
                r.avatar.draw(canvas);
            }
            float tx = ax + dp(52) + dp(14);
            float timeW = timePaint.measureText(r.time);
            float right = getWidth() - side - dp(16);
            canvas.drawText(r.time, right - timeW, top + dp(28), timePaint);
            canvas.drawText(TextUtils.ellipsize(r.title, titlePaint, right - timeW - tx - dp(8), TextUtils.TruncateAt.END).toString(),
                    tx, top + dp(28), titlePaint);
            canvas.drawText(TextUtils.ellipsize(r.message, messagePaint, right - tx, TextUtils.TruncateAt.END).toString(),
                    tx, top + dp(52), messagePaint);
            y = top + h + gap;
        }
    }

    /** Перерисовать после смены настроек. */
    public void refresh() {
        requestLayout();
        invalidate();
    }
}
