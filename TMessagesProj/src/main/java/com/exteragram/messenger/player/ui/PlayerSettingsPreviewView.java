package com.exteragram.messenger.player.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.player.lyrics.LyricsProvider;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

import java.util.Locale;

/**
 * Интерактивное живое превью плеера и мини-плеера для {@link PlayerSettingsSheet}.
 * Показывает изменения цветов обложки, фона в такт, частиц, движущихся обложек,
 * текста песен, прогресса и стиля анимации в реальном времени.
 */
@SuppressLint("ViewConstructor")
public class PlayerSettingsPreviewView extends View {

    private static final int[][] PALETTES = {
            // Rose Quartz / Coral
            {0xFFE84A7F, 0xFF9C27B0, 0xFFFF7043, 0xFF4A148C},
            // Electric Indigo / Cyan
            {0xFF3F51B5, 0xFF00ACC1, 0xFF7C4DFF, 0xFF1A237E},
            // Emerald / Teal Glow
            {0xFF00BFA5, 0xFF1E88E5, 0xFF64DD17, 0xFF004D40}
    };

    private static final String[] SAMPLE_TITLES = {
            "Neon Heartbeat",
            "Midnight Aurora",
            "Velvet Horizon"
    };

    private static final String[] LYRICS_MAIN = {
            "Stars are drifting by",
            "Hearts beat in the rain",
            "Hold onto the light"
    };
    private static final String[] LYRICS_TRANS = {
            "Звёзды уплывают вдаль",
            "Сердца бьются под дождём",
            "Удержи этот свет"
    };
    private static final String[] LYRICS_ROMA = {
            "Zvyozdy uplyvayut vdal",
            "Serdtsa byutsya pod dozhdyom",
            "Uderzhi etot svet"
    };

    private static final float CARD_RADIUS_DP = 18f;
    private static final float MINI_RADIUS_DP = 14f;
    private static final float COVER_RADIUS_DP = 12f;
    private static final long LINE_DURATION_MS = 2300L;

    private final Theme.ResourcesProvider resourcesProvider;
    private final RhythmBackground rhythmBackground = new RhythmBackground();

    private final Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint miniPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint coverPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint waveTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint subtitlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint lyricActivePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint lyricFillPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint lyricDimPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint lyricSubPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint badgeTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    private final RectF miniRect = new RectF();
    private final RectF miniCoverRect = new RectF();
    private final RectF miniPlayRect = new RectF();
    private final RectF cardRect = new RectF();
    private final RectF coverRect = new RectF();
    private final RectF badgeRect = new RectF();
    private final Path wavePath = new Path();
    private final Path clipPath = new Path();

    private int paletteIndex = 0;
    private boolean playing = true;
    private long lastFrameMs;
    private float phaseTimeSec;
    private float lineProgress;
    private int activeLine = 1;
    private int shaderColor1;
    private int shaderColor2;
    private LinearGradient coverGradient;
    private LinearGradient cardGradient;
    private int lastGradientWidth;

    public PlayerSettingsPreviewView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        titlePaint.setTypeface(AndroidUtilities.bold());
        titlePaint.setTextSize(dp(13));
        subtitlePaint.setTextSize(dp(11));
        lyricActivePaint.setTypeface(AndroidUtilities.bold());
        lyricActivePaint.setTextSize(dp(13.5f));
        lyricFillPaint.setTypeface(AndroidUtilities.bold());
        lyricFillPaint.setTextSize(dp(13.5f));
        lyricDimPaint.setTypeface(AndroidUtilities.bold());
        lyricDimPaint.setTextSize(dp(12f));
        lyricSubPaint.setTextSize(dp(10.5f));
        badgeTextPaint.setTypeface(AndroidUtilities.bold());
        badgeTextPaint.setTextSize(dp(9.5f));

        wavePaint.setStyle(Paint.Style.STROKE);
        wavePaint.setStrokeCap(Paint.Cap.ROUND);
        wavePaint.setStrokeWidth(dpf2(2.6f));
        waveTrackPaint.setStyle(Paint.Style.STROKE);
        waveTrackPaint.setStrokeCap(Paint.Cap.ROUND);
        waveTrackPaint.setStrokeWidth(dpf2(2.6f));

        rhythmBackground.setActive(true);
        rhythmBackground.setAnimating(true);
        refresh();
    }

    public static int getPreferredHeightDp() {
        return 222;
    }

    public void refresh() {
        final int[] pal = PALETTES[paletteIndex];
        final boolean dark = Theme.isCurrentThemeDark();
        final boolean fullCoverColors = ExteraConfig.getPlayerCoverColors();
        final int cardBase = fullCoverColors
                ? ColorUtils.blendARGB(pal[3], dark ? 0xFF10131C : 0xFF1D2235, 0.38f)
                : Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider);
        rhythmBackground.setColors(pal, cardBase, fullCoverColors || dark);
        rhythmBackground.setActive(fullCoverColors && ExteraConfig.getPlayerRhythmBackground());
        rhythmBackground.setAnimating(playing && fullCoverColors && ExteraConfig.getPlayerRhythmBackground());
        rhythmBackground.setParticlesEnabled(playing && fullCoverColors && ExteraConfig.getPlayerParticles());

        if (shaderColor1 != pal[0] || shaderColor2 != pal[1]) {
            shaderColor1 = pal[0];
            shaderColor2 = pal[1];
            coverGradient = new LinearGradient(0f, 0f, dp(72), dp(72), pal[0], pal[1], Shader.TileMode.CLAMP);
            lastGradientWidth = 0;
        }
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(getPreferredHeightDp()));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        lastFrameMs = SystemClock.uptimeMillis();
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            final float x = event.getX();
            final float y = event.getY();
            if (coverRect.contains(x, y) || miniCoverRect.contains(x, y)) {
                paletteIndex = (paletteIndex + 1) % PALETTES.length;
                refresh();
                return true;
            }
            if (miniRect.contains(x, y) || cardRect.contains(x, y)) {
                playing = !playing;
                refresh();
                return true;
            }
        }
        return event.getAction() == MotionEvent.ACTION_DOWN || super.onTouchEvent(event);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        final int w = getWidth();
        final int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }

        final long now = SystemClock.uptimeMillis();
        final float dt = lastFrameMs == 0 ? 0.016f : Math.min(0.05f, (now - lastFrameMs) / 1000f);
        lastFrameMs = now;
        if (playing) {
            phaseTimeSec += dt;
            lineProgress += (dt * 1000f) / LINE_DURATION_MS;
            if (lineProgress >= 1f) {
                lineProgress -= 1f;
                activeLine = (activeLine + 1) % LYRICS_MAIN.length;
            }
        }

        final int sheetBg = Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider);
        canvas.drawColor(sheetBg);

        final int padX = dp(14);
        final int padTop = dp(8);
        final int[] pal = PALETTES[paletteIndex];
        final boolean dark = Theme.isCurrentThemeDark();

        // 1. Верхний мини-плеер (реагирует на MiniCoverColors)
        final float miniH = dp(46);
        miniRect.set(padX, padTop, w - padX, padTop + miniH);
        final boolean miniColors = ExteraConfig.getPlayerMiniCoverColors();
        final int miniBg = miniColors
                ? ColorUtils.blendARGB(Theme.getColor(Theme.key_dialogBackground, resourcesProvider), pal[0], dark ? 0.28f : 0.20f)
                : Theme.getColor(Theme.key_dialogBackground, resourcesProvider);
        miniPaint.setColor(miniBg);
        canvas.drawRoundRect(miniRect, dp(MINI_RADIUS_DP), dp(MINI_RADIUS_DP), miniPaint);

        final float mcSize = dp(30);
        miniCoverRect.set(miniRect.left + dp(8), miniRect.centerY() - mcSize / 2f, miniRect.left + dp(8) + mcSize, miniRect.centerY() + mcSize / 2f);
        drawCoverArtwork(canvas, miniCoverRect, dp(8), ExteraConfig.getPlayerMotionCovers(), false);

        final float textLeft = miniCoverRect.right + dp(10);
        final int miniTextColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
        final int miniSubColor = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider);
        titlePaint.setColor(miniTextColor);
        subtitlePaint.setColor(miniSubColor);
        canvas.drawText(SAMPLE_TITLES[paletteIndex], textLeft, miniRect.centerY() - dp(1), titlePaint);
        canvas.drawText("HeartGram Audio • Live Preview", textLeft, miniRect.centerY() + dp(13), subtitlePaint);

        // Индикатор воспроизведения в мини-плеере
        final float ringR = dp(13);
        miniPlayRect.set(miniRect.right - dp(24) - ringR, miniRect.centerY() - ringR, miniRect.right - dp(24) + ringR, miniRect.centerY() + ringR);
        waveTrackPaint.setColor(ColorUtils.setAlphaComponent(pal[0], 70));
        canvas.drawOval(miniPlayRect, waveTrackPaint);
        wavePaint.setColor(pal[0]);
        canvas.drawArc(miniPlayRect, -90f, 360f * ((activeLine + lineProgress) / LYRICS_MAIN.length), false, wavePaint);

        iconPaint.setColor(miniTextColor);
        drawPlayPauseIcon(canvas, miniPlayRect.centerX(), miniPlayRect.centerY(), dp(5), playing, iconPaint);

        // 2. Карточка полного плеера + текста песен
        final float cardTop = miniRect.bottom + dp(10);
        final float cardBottom = h - dp(12);
        cardRect.set(padX, cardTop, w - padX, cardBottom);
        final float cardR = dp(CARD_RADIUS_DP);

        final boolean coverColors = ExteraConfig.getPlayerCoverColors();
        final boolean rhythmOn = ExteraConfig.getPlayerRhythmBackground();
        final boolean particlesOn = ExteraConfig.getPlayerParticles();
        final boolean motionOn = ExteraConfig.getPlayerMotionCovers();
        final boolean canvasOn = ExteraConfig.getPlayerYandexCanvas();
        final boolean cardDarkSurface = coverColors || dark;

        if (coverColors) {
            final int cTop = ColorUtils.blendARGB(pal[3], 0xFF111522, 0.35f);
            final int cBot = ColorUtils.blendARGB(pal[1], 0xFF0A0D16, 0.72f);
            if (cardGradient == null || lastGradientWidth != (int) cardRect.width()) {
                lastGradientWidth = (int) cardRect.width();
                cardGradient = new LinearGradient(cardRect.left, cardRect.top, cardRect.right, cardRect.bottom, cTop, cBot, Shader.TileMode.CLAMP);
            }
            cardPaint.setShader(cardGradient);
        } else {
            cardPaint.setShader(null);
            cardPaint.setColor(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));
        }
        canvas.drawRoundRect(cardRect, cardR, cardR, cardPaint);

        // Обложка слева + волновой прогрессбар снизу под ней
        final float covSize = dp(76);
        coverRect.set(cardRect.left + dp(14), cardRect.top + dp(16), cardRect.left + dp(14) + covSize, cardRect.top + dp(16) + covSize);

        canvas.save();
        clipPath.rewind();
        clipPath.addRoundRect(cardRect, cardR, cardR, Path.Direction.CW);
        canvas.clipPath(clipPath);
        if (coverColors && rhythmOn) {
            rhythmBackground.setParticlesEnabled(playing && particlesOn);
            rhythmBackground.draw(canvas, cardRect, cardR, coverRect, dp(COVER_RADIUS_DP), 1f, 0f);
        } else if (particlesOn && coverColors) {
            drawAmbientParticles(canvas, cardRect, coverRect, pal);
        }
        canvas.restore();

        drawCoverArtwork(canvas, coverRect, dp(COVER_RADIUS_DP), motionOn, canvasOn);
        drawWavyBar(canvas, coverRect.left, coverRect.bottom + dp(14), coverRect.right, (activeLine + lineProgress) / LYRICS_MAIN.length, cardDarkSurface ? Color.WHITE : pal[0]);

        // Блок текста песен справа (LyricsOnline, Progress, AnimStyle, Subtitle)
        final float lyricsLeft = coverRect.right + dp(14);
        final float lyricsRight = cardRect.right - dp(12);
        drawLyricsPreview(canvas, lyricsLeft, cardRect.top + dp(14), lyricsRight, cardRect.bottom - dp(14), pal[0], cardDarkSurface);

        // Тонкий разделитель внизу превью
        dividerPaint.setColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_divider, resourcesProvider), 90));
        canvas.drawRect(0, h - 1, w, h, dividerPaint);

        if (playing) {
            postInvalidateOnAnimation();
        }
    }

    private void drawCoverArtwork(Canvas canvas, RectF rect, float radius, boolean motion, boolean yandexCanvas) {
        canvas.save();
        if (motion && playing) {
            final float scale = 1f + 0.03f * (float) Math.sin(phaseTimeSec * 2.4f);
            canvas.scale(scale, scale, rect.centerX(), rect.centerY());
        }
        coverPaint.setShader(coverGradient);
        canvas.drawRoundRect(rect, radius, radius, coverPaint);

        // Блик / движущаяся волна на обложке при Motion Covers
        if (motion) {
            final float sweep = (float) Math.sin(phaseTimeSec * 1.9f) * 0.5f + 0.5f;
            final float sx = rect.left + rect.width() * sweep;
            sheenPaint.setShader(new LinearGradient(sx - rect.width() * 0.5f, rect.top, sx + rect.width() * 0.5f, rect.bottom,
                    new int[]{0x00FFFFFF, 0x45FFFFFF, 0x00FFFFFF}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(rect, radius, radius, sheenPaint);
        }

        // Значок ноты в центре обложки
        iconPaint.setColor(0xE6FFFFFF);
        final float cx = rect.centerX();
        final float cy = rect.centerY();
        final float r = rect.width() * 0.13f;
        canvas.drawCircle(cx - r * 0.5f, cy + r * 0.8f, r, iconPaint);
        iconPaint.setStrokeWidth(Math.max(dpf2(1.5f), r * 0.45f));
        iconPaint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(cx + r * 0.35f, cy + r * 0.8f, cx + r * 0.35f, cy - r * 1.4f, iconPaint);
        iconPaint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(cx + r * 0.15f, cy - r * 1.6f, cx + r * 1.5f, cy - r * 0.85f, r * 0.3f, r * 0.3f, iconPaint);

        if (yandexCanvas && rect.width() >= dp(50)) {
            badgePaint.setColor(0x99000000);
            badgeRect.set(rect.left + dp(5), rect.top + dp(5), rect.left + dp(47), rect.top + dp(20));
            canvas.drawRoundRect(badgeRect, dp(7), dp(7), badgePaint);
            badgeTextPaint.setColor(Color.WHITE);
            canvas.drawText("CANVAS", badgeRect.left + dp(5), badgeRect.centerY() + dp(3.5f), badgeTextPaint);
        }
        canvas.restore();
    }

    private void drawAmbientParticles(Canvas canvas, RectF card, RectF cover, int[] pal) {
        for (int i = 0; i < 14; i++) {
            final float seed = i * 1.73f;
            final float t = (phaseTimeSec * 0.45f + seed) % 1f;
            final double ang = seed * 2.4 + t * 0.8;
            final float dist = dp(42) + t * dp(65);
            final float px = cover.centerX() + (float) Math.cos(ang) * dist;
            final float py = cover.centerY() + (float) Math.sin(ang) * dist;
            if (!card.contains(px, py)) {
                continue;
            }
            final float alpha = (float) Math.sin(t * Math.PI) * 0.55f;
            badgePaint.setColor(ColorUtils.setAlphaComponent(ColorUtils.blendARGB(Color.WHITE, pal[i % pal.length], 0.45f), (int) (255 * alpha)));
            canvas.drawCircle(px, py, dpf2(1.4f + (i % 3) * 0.6f), badgePaint);
        }
    }

    private void drawWavyBar(Canvas canvas, float left, float cy, float right, float progress, int color) {
        final float width = right - left;
        final float splitX = left + width * progress;
        waveTrackPaint.setColor(ColorUtils.setAlphaComponent(color, 65));
        canvas.drawLine(splitX, cy, right, cy, waveTrackPaint);

        wavePath.rewind();
        final int steps = 20;
        final float amp = playing ? dpf2(2.1f) : 0f;
        for (int i = 0; i <= steps; i++) {
            final float u = i / (float) steps;
            final float x = left + (splitX - left) * u;
            final float y = cy + amp * (float) Math.sin(u * Math.PI * 3.5f - phaseTimeSec * 7f);
            if (i == 0) {
                wavePath.moveTo(x, y);
            } else {
                wavePath.lineTo(x, y);
            }
        }
        wavePaint.setColor(color);
        canvas.drawPath(wavePath, wavePaint);
    }

    private void drawLyricsPreview(Canvas canvas, float left, float top, float right, float bottom, int accent, boolean darkSurface) {
        final boolean online = ExteraConfig.getLyricsOnline();
        final int progressMode = ExteraConfig.getLyricsProgress();
        final int animStyle = ExteraConfig.getLyricsAnimStyle();
        final int subtitleMode = ExteraConfig.getLyricsSubtitle();

        final int baseText = darkSurface ? Color.WHITE : Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
        final int dimAlpha = online ? 120 : 65;

        // Верхний чип онлайн-текста и активных провайдеров
        final String chipLabel = online
                ? String.format(Locale.US, "%s (%d/%d)", getString(R.string.PlayerLyrics), ExteraConfig.getLyricsProviders().size(), LyricsProvider.values().length)
                : getString(R.string.BlurOff);
        final float chipW = badgeTextPaint.measureText(chipLabel) + dp(12);
        badgeRect.set(left, top, Math.min(right, left + chipW), top + dp(16));
        badgePaint.setColor(ColorUtils.setAlphaComponent(online ? accent : baseText, online ? 180 : 40));
        canvas.drawRoundRect(badgeRect, dp(8), dp(8), badgePaint);
        badgeTextPaint.setColor(online ? Color.WHITE : ColorUtils.setAlphaComponent(baseText, 180));
        canvas.drawText(chipLabel, badgeRect.left + dp(6), badgeRect.centerY() + dp(3.2f), badgeTextPaint);

        final int prevIdx = (activeLine + LYRICS_MAIN.length - 1) % LYRICS_MAIN.length;
        final int nextIdx = (activeLine + 1) % LYRICS_MAIN.length;

        // Плавный сдвиг при переходе строки (exteraGram плавный vs HeartGram пружинистый)
        final float enter = Math.min(1f, lineProgress / 0.25f);
        final float bounce = animStyle == LyricsView.ANIM_HEARTGRAM
                ? 1f + 0.08f * (float) Math.sin(enter * Math.PI) * (1f - enter * 0.35f)
                : 0.96f + 0.04f * enter;
        final float shiftY = (1f - enter) * dp(animStyle == LyricsView.ANIM_HEARTGRAM ? 9f : 6f);

        final float line1Y = top + dp(34);
        final float activeY = top + dp(56) + shiftY;
        final float subY = activeY + dp(15);
        final float line3Y = subtitleMode == LyricsView.SUBTITLE_NONE ? activeY + dp(21) : subY + dp(19);

        lyricDimPaint.setColor(ColorUtils.setAlphaComponent(baseText, dimAlpha));
        canvas.drawText(LYRICS_MAIN[prevIdx], left, line1Y, lyricDimPaint);

        // Активная строка
        final String activeStr = LYRICS_MAIN[activeLine];
        canvas.save();
        canvas.scale(bounce, bounce, left, activeY);
        lyricActivePaint.setColor(ColorUtils.setAlphaComponent(baseText, online ? (progressMode == LyricsView.PROGRESS_NONE ? 255 : 135) : 90));
        canvas.drawText(activeStr, left, activeY, lyricActivePaint);

        if (online && progressMode != LyricsView.PROGRESS_NONE) {
            final float strW = lyricActivePaint.measureText(activeStr);
            canvas.save();
            if (progressMode == LyricsView.PROGRESS_HORIZONTAL) {
                canvas.clipRect(left, activeY - dp(16), left + strW * lineProgress, activeY + dp(6));
            } else {
                final float fillH = dp(16) * lineProgress;
                canvas.clipRect(left, activeY - dp(13), left + strW + dp(4), activeY - dp(13) + fillH);
            }
            lyricFillPaint.setColor(ColorUtils.blendARGB(baseText, Color.WHITE, 0.5f));
            canvas.drawText(activeStr, left, activeY, lyricFillPaint);
            canvas.restore();
        }
        canvas.restore();

        if (subtitleMode != LyricsView.SUBTITLE_NONE && subY < bottom - dp(14)) {
            final String sub = subtitleMode == LyricsView.SUBTITLE_TRANSLATION ? LYRICS_TRANS[activeLine] : LYRICS_ROMA[activeLine];
            lyricSubPaint.setColor(ColorUtils.setAlphaComponent(baseText, online ? 190 : 85));
            canvas.drawText(sub, left, subY + dp(1), lyricSubPaint);
        }

        if (line3Y < bottom - dp(2)) {
            lyricDimPaint.setColor(ColorUtils.setAlphaComponent(baseText, dimAlpha));
            canvas.drawText(LYRICS_MAIN[nextIdx], left, line3Y, lyricDimPaint);
        }
    }

    private static void drawPlayPauseIcon(Canvas canvas, float cx, float cy, float r, boolean playing, Paint paint) {
        if (playing) {
            final float barW = Math.max(dpf2(2f), r * 0.42f);
            final float gap = r * 0.34f;
            canvas.drawRoundRect(cx - gap - barW, cy - r, cx - gap, cy + r, barW * 0.4f, barW * 0.4f, paint);
            canvas.drawRoundRect(cx + gap, cy - r, cx + gap + barW, cy + r, barW * 0.4f, barW * 0.4f, paint);
        } else {
            final Path p = new Path();
            p.moveTo(cx - r * 0.55f, cy - r);
            p.lineTo(cx + r * 0.95f, cy);
            p.lineTo(cx - r * 0.55f, cy + r);
            p.close();
            canvas.drawPath(p, paint);
        }
    }
}
