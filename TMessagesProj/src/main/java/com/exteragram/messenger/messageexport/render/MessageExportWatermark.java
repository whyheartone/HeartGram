package com.exteragram.messenger.messageexport.render;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.TextPaint;

import com.exteragram.messenger.ExteraConfig;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

public final class MessageExportWatermark {

    private static final int TOP_GAP = 8;
    private static final int BOTTOM_GAP = 12;
    private static final int PILL_HEIGHT = 26;
    private static final int HEART_SIZE_DP = 13;
    private static final int HEART_GAP_DP = 5;
    private static final int PILL_H_PADDING_DP = 10;
    private static final int CONTENT_PADDING = 8;
    private static final String BRAND_TEXT = "HeartGram";

    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint heartPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path heartPath = new Path();
    private final RectF pill = new RectF();
    private final float textWidth;

    public MessageExportWatermark(Context context) {
        textPaint.setTypeface(AndroidUtilities.bold());
        textPaint.setTextSize(dp(12.5f));
        textWidth = textPaint.measureText(BRAND_TEXT);
        heartPaint.setStyle(Paint.Style.FILL);
    }

    public static boolean isEnabled() {
        return ExteraConfig.getMessageExportWatermark();
    }

    public static int getBottomInset() {
        return dp(isEnabled() ? TOP_GAP + PILL_HEIGHT + BOTTOM_GAP : CONTENT_PADDING);
    }

    public void draw(Canvas canvas, int width, float contentBottom, Theme.ResourcesProvider provider) {
        if (!isEnabled()) {
            pill.setEmpty();
            return;
        }
        float left = (width - getPillWidth()) / 2f;
        float top = Math.round(contentBottom) + dp(TOP_GAP);
        if (provider != null && provider.hasGradientService()) {
            canvas.save();
            canvas.translate(left, top);
            provider.applyServiceShaderMatrix(width, 0, left, top);
            drawPill(canvas, 0, 0, provider);
            canvas.restore();
            pill.offset(left, top);
        } else {
            drawPill(canvas, left, top, provider);
        }
    }

    public void drawPill(Canvas canvas, float left, float top, Theme.ResourcesProvider provider) {
        int color = Theme.getColor(Theme.key_chat_serviceText, provider);
        textPaint.setColor(color);
        heartPaint.setColor(color);

        int pillHeight = getPillHeight();
        pill.set(left, top, left + getPillWidth(), top + pillHeight);
        canvas.drawRoundRect(pill, pillHeight / 2f, pillHeight / 2f, Theme.getThemePaint(Theme.key_paint_chatActionBackground, provider));

        float heartSize = dp(HEART_SIZE_DP);
        float hx = pill.left + dp(PILL_H_PADDING_DP);
        float hy = pill.centerY() - heartSize / 2f;
        buildHeartPath(heartPath, hx, hy, heartSize);
        canvas.drawPath(heartPath, heartPaint);

        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float textY = pill.centerY() - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(BRAND_TEXT, hx + heartSize + dp(HEART_GAP_DP), textY, textPaint);
    }

    private static void buildHeartPath(Path path, float left, float top, float s) {
        path.reset();
        path.moveTo(left + s * 0.5f, top + s * 0.88f);
        path.cubicTo(left + s * 0.14f, top + s * 0.62f, left, top + s * 0.42f, left, top + s * 0.27f);
        path.cubicTo(left, top + s * 0.11f, left + s * 0.13f, top, left + s * 0.28f, top);
        path.cubicTo(left + s * 0.38f, top, left + s * 0.46f, top + s * 0.06f, left + s * 0.5f, top + s * 0.14f);
        path.cubicTo(left + s * 0.54f, top + s * 0.06f, left + s * 0.62f, top, left + s * 0.72f, top);
        path.cubicTo(left + s * 0.87f, top, left + s, top + s * 0.11f, left + s, top + s * 0.27f);
        path.cubicTo(left + s, top + s * 0.42f, left + s * 0.86f, top + s * 0.62f, left + s * 0.5f, top + s * 0.88f);
        path.close();
    }

    public RectF getBounds() {
        return pill;
    }

    public int getPillWidth() {
        return Math.round(dp(PILL_H_PADDING_DP * 2) + dp(HEART_SIZE_DP + HEART_GAP_DP) + textWidth);
    }

    public int getPillHeight() {
        return dp(PILL_HEIGHT);
    }
}
