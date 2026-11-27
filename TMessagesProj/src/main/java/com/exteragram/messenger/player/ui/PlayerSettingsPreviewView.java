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
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.player.CoverColorExtractor;
import com.exteragram.messenger.player.PlayerColors;
import com.exteragram.messenger.player.PlayerCovers;
import com.exteragram.messenger.player.lyrics.LyricsProvider;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.RecyclerListView;

import java.util.Locale;

/**
 * Живое превью для {@link PlayerSettingsSheet}: использует текущий играющий трек,
 * его реальную обложку и цвета (или цвета активной темы, если трек не играет).
 * При открытии поверх реального MusicPlayerSheet показывает компактную полоску мини-плеера.
 */
@SuppressLint("ViewConstructor")
public class PlayerSettingsPreviewView extends View {

    private static final String[] LYRICS_MAIN = {
            "Stars are drifting by",
            "Hold onto the light",
            "Fading in the night"
    };
    private static final String[] LYRICS_TRANS = {
            "Звёзды уплывают вдаль",
            "Удержи этот свет",
            "Тая в тишине ночи"
    };
    private static final String[] LYRICS_ROMA = {
            "Zvyozdy uplyvayut vdal",
            "Uderzhi etot svet",
            "Taya v tishine nochi"
    };

    private static final float CARD_RADIUS_DP = 18f;
    private static final float MINI_RADIUS_DP = 16f;
    private static final float COVER_RADIUS_DP = 12f;
    private static final long LINE_DURATION_MS = 2300L;

    private final Theme.ResourcesProvider resourcesProvider;
    private final RhythmBackground rhythmBackground = new RhythmBackground();
    private final ImageReceiver coverReceiver = new ImageReceiver(this);
    private final ImageReceiver miniCoverReceiver = new ImageReceiver(this);

    private final Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint miniPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint coverPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
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

    private boolean miniOnly;
    private boolean playing = true;
    private long lastFrameMs;
    private float phaseTimeSec;
    private float lineProgress;
    private int activeLine = 1;
    private MessageObject boundMessage;
    private boolean hasRealCover;
    private PlayerColors themeColors;
    private PlayerColors coverColors;
    private LinearGradient coverFallbackGradient;
    private int lastGradientAccent;

    public PlayerSettingsPreviewView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setTag(RecyclerListView.TAG_NOT_SECTION);

        titlePaint.setTypeface(AndroidUtilities.bold());
        titlePaint.setTextSize(dp(13.5f));
        subtitlePaint.setTextSize(dp(11.5f));
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

        coverReceiver.setRoundRadius(dp(COVER_RADIUS_DP));
        miniCoverReceiver.setRoundRadius(dp(8));
        coverReceiver.setDelegate((imageReceiver, set, thumb, memCache) -> {
            if (set) {
                CoverColorExtractor.extract(boundMessage, imageReceiver, seed -> refresh());
                invalidate();
            }
        });

        refresh();
    }

    public void setMiniOnly(boolean miniOnly) {
        if (this.miniOnly != miniOnly) {
            this.miniOnly = miniOnly;
            requestLayout();
            invalidate();
        }
    }

    public int getPreferredHeightDp() {
        return miniOnly ? 60 : 214;
    }

    public void refresh() {
        themeColors = PlayerColors.fromTheme(resourcesProvider);
        final MessageObject mo = MediaController.getInstance().getPlayingMessageObject();
        final MessageObject music = mo != null && mo.isMusic() ? mo : null;
        if (music != boundMessage) {
            boundMessage = music;
            if (music != null) {
                final boolean big = PlayerCovers.setCover(coverReceiver, music, 180, true);
                final boolean small = PlayerCovers.setCover(miniCoverReceiver, music, 72, false);
                hasRealCover = big || small;
            } else {
                coverReceiver.clearImage();
                miniCoverReceiver.clearImage();
                hasRealCover = false;
            }
        }
        final Integer seed = boundMessage != null ? CoverColorExtractor.getCachedSeed(boundMessage) : null;
        if (seed != null && seed != CoverColorExtractor.NO_SEED) {
            coverColors = PlayerColors.fromSeed(seed, themeColors.dark);
        } else {
            coverColors = themeColors;
        }

        final PlayerColors activeCard = ExteraConfig.getPlayerCoverColors() ? coverColors : themeColors;
        rhythmBackground.setColors(activeCard.glow, activeCard.background, activeCard.dark);
        rhythmBackground.setActive(ExteraConfig.getPlayerCoverColors() && ExteraConfig.getPlayerRhythmBackground());
        rhythmBackground.setAnimating(playing && ExteraConfig.getPlayerCoverColors() && ExteraConfig.getPlayerRhythmBackground());
        rhythmBackground.setParticlesEnabled(playing && ExteraConfig.getPlayerCoverColors() && ExteraConfig.getPlayerParticles());

        if (lastGradientAccent != coverColors.accent || coverFallbackGradient == null) {
            lastGradientAccent = coverColors.accent;
            coverFallbackGradient = new LinearGradient(0f, 0f, dp(76), dp(76),
                    coverColors.accent, coverColors.tonal, Shader.TileMode.CLAMP);
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
        coverReceiver.onAttachedToWindow();
        miniCoverReceiver.onAttachedToWindow();
        lastFrameMs = SystemClock.uptimeMillis();
        refresh();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        coverReceiver.onDetachedFromWindow();
        miniCoverReceiver.onDetachedFromWindow();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            playing = !playing;
            refresh();
            return true;
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

        final int padX = dp(12);
        final int padTop = dp(4);
        final String trackTitle = boundMessage != null && !TextUtils.isEmpty(boundMessage.getMusicTitle())
                ? boundMessage.getMusicTitle()
                : getString(R.string.AudioUnknownTitle);
        final String trackArtist = boundMessage != null && !TextUtils.isEmpty(boundMessage.getMusicAuthor())
                ? boundMessage.getMusicAuthor()
                : "HeartGram";

        // 1. Мини-плеер (показывает реальный трек и PlayerMiniCoverColors)
        final float miniH = dp(48);
        miniRect.set(padX, padTop, w - padX, padTop + miniH);
        final boolean miniCoverOn = ExteraConfig.getPlayerMiniCoverColors();
        final int miniAccent = PlayerColors.getMiniPlayerAccent(themeColors.accent, resourcesProvider);
        final int baseMiniSurface = Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider);
        final int miniBg = miniCoverOn
                ? ColorUtils.blendARGB(baseMiniSurface, coverColors.tonal, themeColors.dark ? 0.42f : 0.30f)
                : baseMiniSurface;
        miniPaint.setColor(miniBg);
        canvas.drawRoundRect(miniRect, dp(MINI_RADIUS_DP), dp(MINI_RADIUS_DP), miniPaint);

        final float mcSize = dp(32);
        miniCoverRect.set(miniRect.left + dp(8), miniRect.centerY() - mcSize / 2f, miniRect.left + dp(8) + mcSize, miniRect.centerY() + mcSize / 2f);
        drawCoverArtwork(canvas, miniCoverRect, miniCoverReceiver, dp(8), false, false);

        final float textLeft = miniCoverRect.right + dp(10);
        final float textMaxW = miniRect.right - dp(54) - textLeft;
        final int miniTitleColor = miniCoverOn
                ? PlayerColors.getMiniPlayerText(themeColors.text, resourcesProvider)
                : Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
        final int miniSubColor = miniCoverOn
                ? PlayerColors.getMiniPlayerSecondary(themeColors.textSecondary, resourcesProvider)
                : Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider);
        titlePaint.setColor(miniTitleColor);
        subtitlePaint.setColor(miniSubColor);
        canvas.drawText(TextUtils.ellipsize(trackTitle, titlePaint, Math.max(0f, textMaxW), TextUtils.TruncateAt.END).toString(),
                textLeft, miniRect.centerY() - dp(1.5f), titlePaint);
        canvas.drawText(TextUtils.ellipsize(trackArtist, subtitlePaint, Math.max(0f, textMaxW), TextUtils.TruncateAt.END).toString(),
                textLeft, miniRect.centerY() + dp(13f), subtitlePaint);

        final float ringR = dp(13);
        miniPlayRect.set(miniRect.right - dp(24) - ringR, miniRect.centerY() - ringR, miniRect.right - dp(24) + ringR, miniRect.centerY() + ringR);
        waveTrackPaint.setColor(ColorUtils.setAlphaComponent(miniAccent, 65));
        canvas.drawOval(miniPlayRect, waveTrackPaint);
        wavePaint.setColor(miniAccent);
        canvas.drawArc(miniPlayRect, -90f, 360f * ((activeLine + lineProgress) / LYRICS_MAIN.length), false, wavePaint);

        iconPaint.setColor(miniTitleColor);
        drawPlayPauseIcon(canvas, miniPlayRect.centerX(), miniPlayRect.centerY(), dp(4.5f), playing, iconPaint);

        if (miniOnly) {
            if (playing) {
                postInvalidateOnAnimation();
            }
            return;
        }

        // 2. Карточка полного плеера + текста песен (в цветах обложки или темы)
        final boolean coverOn = ExteraConfig.getPlayerCoverColors();
        final PlayerColors cardPalette = coverOn ? coverColors : themeColors;
        final float cardTop = miniRect.bottom + dp(8);
        final float cardBottom = h - dp(4);
        cardRect.set(padX, cardTop, w - padX, cardBottom);
        final float cardR = dp(CARD_RADIUS_DP);

        cardPaint.setShader(null);
        cardPaint.setColor(cardPalette.background);
        canvas.drawRoundRect(cardRect, cardR, cardR, cardPaint);

        final float covSize = dp(76);
        coverRect.set(cardRect.left + dp(14), cardRect.top + dp(16), cardRect.left + dp(14) + covSize, cardRect.top + dp(16) + covSize);

        final boolean rhythmOn = ExteraConfig.getPlayerRhythmBackground();
        final boolean particlesOn = ExteraConfig.getPlayerParticles();
        final boolean motionOn = ExteraConfig.getPlayerMotionCovers();
        final boolean canvasOn = ExteraConfig.getPlayerYandexCanvas();

        canvas.save();
        clipPath.rewind();
        clipPath.addRoundRect(cardRect, cardR, cardR, Path.Direction.CW);
        canvas.clipPath(clipPath);
        if (coverOn && rhythmOn) {
            rhythmBackground.setParticlesEnabled(playing && particlesOn);
            rhythmBackground.draw(canvas, cardRect, cardR, coverRect, dp(COVER_RADIUS_DP), 1f, 0f);
        } else if (particlesOn && coverOn) {
            drawAmbientParticles(canvas, cardRect, coverRect, cardPalette);
        }
        canvas.restore();

        drawCoverArtwork(canvas, coverRect, coverReceiver, dp(COVER_RADIUS_DP), motionOn, canvasOn);
        drawWavyBar(canvas, coverRect.left, coverRect.bottom + dp(14), coverRect.right, (activeLine + lineProgress) / LYRICS_MAIN.length, cardPalette.wave, cardPalette.track);

        final float lyricsLeft = coverRect.right + dp(14);
        final float lyricsRight = cardRect.right - dp(12);
        drawLyricsPreview(canvas, lyricsLeft, cardRect.top + dp(14), lyricsRight, cardRect.bottom - dp(12), cardPalette);

        if (playing) {
            postInvalidateOnAnimation();
        }
    }

    private void drawCoverArtwork(Canvas canvas, RectF rect, ImageReceiver receiver, float radius, boolean motion, boolean yandexCanvas) {
        canvas.save();
        if (motion && playing) {
            final float scale = 1f + 0.03f * (float) Math.sin(phaseTimeSec * 2.4f);
            canvas.scale(scale, scale, rect.centerX(), rect.centerY());
        }
        if (hasRealCover && receiver.hasBitmapImage()) {
            receiver.setImageCoords(rect.left, rect.top, rect.width(), rect.height());
            receiver.draw(canvas);
        } else {
            coverPaint.setShader(coverFallbackGradient);
            canvas.drawRoundRect(rect, radius, radius, coverPaint);
            iconPaint.setColor(coverColors.onAccent);
            final float cx = rect.centerX();
            final float cy = rect.centerY();
            final float r = rect.width() * 0.13f;
            canvas.drawCircle(cx - r * 0.5f, cy + r * 0.8f, r, iconPaint);
            iconPaint.setStrokeWidth(Math.max(dpf2(1.5f), r * 0.45f));
            iconPaint.setStyle(Paint.Style.STROKE);
            canvas.drawLine(cx + r * 0.35f, cy + r * 0.8f, cx + r * 0.35f, cy - r * 1.4f, iconPaint);
            iconPaint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(cx + r * 0.15f, cy - r * 1.6f, cx + r * 1.5f, cy - r * 0.85f, r * 0.3f, r * 0.3f, iconPaint);
        }

        if (motion) {
            final float sweep = (float) Math.sin(phaseTimeSec * 1.9f) * 0.5f + 0.5f;
            final float sx = rect.left + rect.width() * sweep;
            sheenPaint.setShader(new LinearGradient(sx - rect.width() * 0.5f, rect.top, sx + rect.width() * 0.5f, rect.bottom,
                    new int[]{0x00FFFFFF, 0x3CFFFFFF, 0x00FFFFFF}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(rect, radius, radius, sheenPaint);
        }

        if (yandexCanvas && rect.width() >= dp(50)) {
            badgePaint.setColor(0x99000000);
            badgeRect.set(rect.left + dp(5), rect.top + dp(5), rect.left + dp(47), rect.top + dp(20));
            canvas.drawRoundRect(badgeRect, dp(7), dp(7), badgePaint);
            badgeTextPaint.setColor(Color.WHITE);
            canvas.drawText("CANVAS", badgeRect.left + dp(5), badgeRect.centerY() + dp(3.5f), badgeTextPaint);
        }
        canvas.restore();
    }

    private void drawAmbientParticles(Canvas canvas, RectF card, RectF cover, PlayerColors pal) {
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
            badgePaint.setColor(ColorUtils.setAlphaComponent(pal.glow[i % pal.glow.length], (int) (255 * alpha)));
            canvas.drawCircle(px, py, dpf2(1.4f + (i % 3) * 0.6f), badgePaint);
        }
    }

    private void drawWavyBar(Canvas canvas, float left, float cy, float right, float progress, int waveColor, int trackColor) {
        final float width = right - left;
        final float splitX = left + width * progress;
        waveTrackPaint.setColor(trackColor);
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
        wavePaint.setColor(waveColor);
        canvas.drawPath(wavePath, wavePaint);
    }

    private void drawLyricsPreview(Canvas canvas, float left, float top, float right, float bottom, PlayerColors pal) {
        final boolean online = ExteraConfig.getLyricsOnline();
        final int progressMode = ExteraConfig.getLyricsProgress();
        final int animStyle = ExteraConfig.getLyricsAnimStyle();
        final int subtitleMode = ExteraConfig.getLyricsSubtitle();

        final int baseText = pal.text;
        final int dimAlpha = online ? 120 : 65;

        final String chipLabel = online
                ? String.format(Locale.US, "%s (%d/%d)", getString(R.string.PlayerLyrics), ExteraConfig.getLyricsProviders().size(), LyricsProvider.values().length)
                : getString(R.string.BlurOff);
        final float chipW = badgeTextPaint.measureText(chipLabel) + dp(12);
        badgeRect.set(left, top, Math.min(right, left + chipW), top + dp(17));
        badgePaint.setColor(online ? pal.accent : ColorUtils.setAlphaComponent(baseText, 40));
        canvas.drawRoundRect(badgeRect, dp(8.5f), dp(8.5f), badgePaint);
        badgeTextPaint.setColor(online ? pal.onAccent : ColorUtils.setAlphaComponent(baseText, 180));
        canvas.drawText(chipLabel, badgeRect.left + dp(6), badgeRect.centerY() + dp(3.2f), badgeTextPaint);

        final int prevIdx = (activeLine + LYRICS_MAIN.length - 1) % LYRICS_MAIN.length;
        final int nextIdx = (activeLine + 1) % LYRICS_MAIN.length;

        final float enter = Math.min(1f, lineProgress / 0.25f);
        final float scale = animStyle == LyricsView.ANIM_HEARTGRAM
                ? 1f + 0.06f * Math.min(1f, enter * 1.25f)
                : 0.96f + 0.04f * enter;
        final float shiftY = (1f - enter) * dp(animStyle == LyricsView.ANIM_HEARTGRAM ? 8f : 5f);

        final float line1Y = top + dp(34);
        final float activeY = top + dp(56) + shiftY;
        final float subY = activeY + dp(15);
        final float line3Y = subtitleMode == LyricsView.SUBTITLE_NONE ? activeY + dp(21) : subY + dp(19);

        lyricDimPaint.setColor(ColorUtils.setAlphaComponent(baseText, dimAlpha));
        canvas.drawText(LYRICS_MAIN[prevIdx], left, line1Y, lyricDimPaint);

        final String activeStr = LYRICS_MAIN[activeLine];
        canvas.save();
        canvas.scale(scale, scale, left, activeY);
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
            lyricFillPaint.setColor(baseText);
            canvas.drawText(activeStr, left, activeY, lyricFillPaint);
            canvas.restore();
        }
        canvas.restore();

        if (subtitleMode != LyricsView.SUBTITLE_NONE && subY < bottom - dp(12)) {
            final String sub = subtitleMode == LyricsView.SUBTITLE_TRANSLATION ? LYRICS_TRANS[activeLine] : LYRICS_ROMA[activeLine];
            lyricSubPaint.setColor(ColorUtils.setAlphaComponent(pal.textSecondary, online ? 210 : 95));
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
