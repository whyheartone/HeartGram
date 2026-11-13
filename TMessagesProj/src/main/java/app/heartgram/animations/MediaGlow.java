package app.heartgram.animations;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;
import org.telegram.messenger.ImageReceiver;
import org.telegram.ui.Components.ClippingImageView;
import org.telegram.ui.PhotoViewer;

import app.heartgram.HeartGramPrefs;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * Свечение медиа (как в opexGram): при просмотре фото за ним мягкий ореол его же цветов.
 * Под картинкой PhotoViewer (и под её копией во время анимации открытия) рисуется
 * сильно размытая уменьшенная копия, чуть больше самой картинки.
 *
 * Фото: ореол считается сразу по тому, что уже есть (миниатюра, потом полное фото), и заранее для
 * соседних фото, чтобы при листании он был на месте с первого кадра.
 * Видео и GIF: кадр берётся несколько раз в секунду, ореол плавно перетекает за картинкой.
 */
public final class MediaGlow {
    public static final String[] SIZES = {"Маленький", "Средний", "Большой", "Огромный"};
    /** Поля размытой копии в px копии: чем больше, тем дальше ореол выходит за картинку. */
    private static final int[] PADS = {12, 22, 36, 56};
    private static final int CORE = 48;
    /** Как часто брать кадр видео; за сколько ореол «дотекает» до нового кадра; как часто перерисовывать, мс. */
    private static final long SAMPLE_MS = 160, IDLE_MS = 500, FLOW_MS = 260, FRAME_MS = 33;

    private static final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private static final RectF rect = new RectF();
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static ExecutorService worker;

    // ── статичные картинки: несколько готовых ореолов (текущее фото и соседние) ──
    private static final int CACHE = 4;
    private static final Object[] cacheKey = new Object[CACHE]; // WeakReference<Object>
    private static final Bitmap[] cacheGlow = new Bitmap[CACHE];
    private static final int[] cachePad = new int[CACHE];
    private static int cacheNext;

    // ── движущаяся картинка (видео, GIF): показанный ореол плавно подтягивается к последнему кадру ──
    private static int[] dynShown, dynTarget;
    private static Bitmap dynBmp;
    private static int dynW, dynH, dynPad;
    private static long dynStepAt, dynLastSample;
    private static boolean dynBusy, dynSettled = true, pausedSampled;
    private static WeakReference<Object> dynOwner = new WeakReference<>(null);
    private static WeakReference<View> videoHost = new WeakReference<>(null);
    private static boolean tickerOn;

    private MediaGlow() {
    }

    private static volatile int enabledCache = -1;

    public static boolean enabled() {
        int c = enabledCache;
        if (c < 0) {
            c = HeartGramPrefs.prefs().getBoolean("media_glow", false) ? 1 : 0;
            enabledCache = c;
        }
        return c == 1;
    }

    /** Вызывается из настроек при переключении. */
    public static void refreshEnabled() {
        enabledCache = -1;
    }

    /** Свечение под видео тратит батарею (кадр берётся постоянно), поэтому отдельный переключатель. */
    static boolean videoEnabled() {
        return HeartGramPrefs.prefs().getBoolean("media_glow_video", true);
    }

    public static int size() {
        return Math.max(0, Math.min(SIZES.length - 1, HeartGramPrefs.prefs().getInt("media_glow_size", 2)));
    }

    private static PhotoViewer viewer() {
        return PhotoViewer.hasInstance() ? PhotoViewer.getInstance() : null;
    }

    /**
     * Из PhotoViewer прямо перед рисованием картинки (центральной или соседней при листании):
     * под ней рисуется ореол. Соседние фото готовим заранее, чтобы при листании ореол не опаздывал.
     */
    public static void beforeImageDraw(Canvas canvas, ImageReceiver self, ImageReceiver c, ImageReceiver l, ImageReceiver r) {
        if (!enabled() || self == null || (self != c && self != l && self != r)) {
            return;
        }
        try {
            RectF reg = self.getDrawRegion();
            // не getCurrentAlpha: он идёт от 0 при подмене миниатюры полным фото, и ореол «проседал»
            float a = self.getAlpha();
            Bitmap b = still(self);
            if (b != null) {
                drawStill(canvas, b, reg, a);
            } else {
                android.graphics.drawable.Drawable d = self.getDrawable(); // GIF, превью видео и т.п.
                if (d != null && self == c) {
                    drawMoving(canvas, d, reg, a);
                }
            }
            if (self == c) {
                prepare(l);
                prepare(r);
            }
        } catch (Throwable t) {
            android.util.Log.w("HeartGramGlow", "свечение медиа: " + t);
        }
    }

    /** Из ClippingImageView.onDraw: анимация открытия и закрытия, картинку рисует копия. */
    public static void beforeClippingDraw(Canvas canvas, ClippingImageView view, Bitmap b, RectF r) {
        if (!enabled()) {
            return;
        }
        try {
            PhotoViewer v = viewer();
            if (v == null || !v.hgIsAnimatingView(view) || b == null || b.isRecycled() || r == null) {
                return;
            }
            drawStill(canvas, b, r, 1f);
        } catch (Throwable t) {
            android.util.Log.w("HeartGramGlow", "свечение при открытии: " + t);
        }
    }

    /** Готовая картинка приёмника: полное фото, а пока оно грузится — миниатюра. */
    private static Bitmap still(ImageReceiver receiver) {
        if (receiver == null) {
            return null;
        }
        Bitmap b = receiver.getBitmap();
        if (b == null || b.isRecycled()) {
            b = receiver.getThumbBitmap();
        }
        return b == null || b.isRecycled() ? null : b;
    }

    private static void prepare(ImageReceiver receiver) {
        try {
            Bitmap b = still(receiver);
            if (b != null) {
                glowFor(b, PADS[size()]);
            }
        } catch (Throwable ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    private static Bitmap glowFor(Bitmap b, int pad) {
        for (int i = 0; i < CACHE; i++) {
            WeakReference<Object> k = (WeakReference<Object>) cacheKey[i];
            if (k != null && k.get() == b && cachePad[i] == pad && cacheGlow[i] != null) {
                return cacheGlow[i];
            }
        }
        Bitmap g = blurred(b, pad);
        if (g != null) {
            int i = cacheNext;
            cacheNext = (cacheNext + 1) % CACHE;
            cacheKey[i] = new WeakReference<Object>(b);
            cacheGlow[i] = g;
            cachePad[i] = pad;
        }
        return g;
    }

    private static void drawStill(Canvas canvas, Bitmap b, RectF r, float alpha) {
        int pad = PADS[size()];
        blit(canvas, glowFor(b, pad), pad, r, alpha);
    }

    private static android.graphics.ColorMatrixColorFilter boostFilter;

    /** Тёмные фото давали еле заметный ореол: слегка поднимаем яркость и насыщенность. */
    private static android.graphics.ColorMatrixColorFilter boost() {
        if (boostFilter == null) {
            android.graphics.ColorMatrix m = new android.graphics.ColorMatrix();
            m.setSaturation(1.5f);
            android.graphics.ColorMatrix b = new android.graphics.ColorMatrix(new float[]{
                    1.8f, 0, 0, 0, 24, 0, 1.8f, 0, 0, 24, 0, 0, 1.8f, 0, 24, 0, 0, 0, 1, 0});
            m.postConcat(b);
            boostFilter = new android.graphics.ColorMatrixColorFilter(m);
        }
        return boostFilter;
    }

    /** Ядро копии совпадает с картинкой, поля уходят наружу. */
    private static void blit(Canvas canvas, Bitmap glow, int pad, RectF r, float alpha) {
        if (glow == null || r == null || r.width() <= 0 || r.height() <= 0 || alpha <= 0f) {
            return;
        }
        float gx = r.width() * pad / Math.max(1, glow.getWidth() - 2 * pad);
        float gy = r.height() * pad / Math.max(1, glow.getHeight() - 2 * pad);
        rect.set(r.left - gx, r.top - gy, r.right + gx, r.bottom + gy);
        paint.setColorFilter(boost());
        paint.setAlpha((int) (255 * Math.max(0f, Math.min(1f, alpha))));
        canvas.drawBitmap(glow, null, rect, paint);
    }

    // ── движущаяся картинка ─────────────────────────────────────────────

    private static void ensureWorker() {
        if (worker == null) {
            worker = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "hg-glow");
                t.setPriority(Thread.MIN_PRIORITY);
                return t;
            });
        }
    }

    /** Новый кадр: размываем в фоне, результат становится целью, к которой плавно идёт показанный ореол. */
    private static void push(final Object owner, final Bitmap frame, final View toInvalidate) {
        if (frame == null || dynBusy) {
            return;
        }
        dynBusy = true;
        final int pad = PADS[size()];
        ensureWorker();
        worker.execute(() -> {
            final int[] dims = new int[2];
            final int[] px = blurredPixels(frame, pad, dims);
            main.post(() -> {
                dynBusy = false;
                if (px != null) {
                    accept(owner, px, dims[0], dims[1], pad);
                    if (toInvalidate != null) {
                        toInvalidate.invalidate();
                    }
                }
            });
        });
    }

    private static void accept(Object owner, int[] px, int w, int h, int pad) {
        boolean same = dynOwner.get() == owner && dynBmp != null && dynW == w && dynH == h && dynPad == pad;
        dynTarget = px;
        if (!same) { // другое видео или размер: показываем сразу, без перетекания
            dynShown = px.clone();
            dynW = w;
            dynH = h;
            dynPad = pad;
            dynBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            dynBmp.setPixels(dynShown, 0, w, 0, 0, w, h);
            dynOwner = new WeakReference<>(owner);
            dynSettled = true;
        } else {
            dynSettled = false;
        }
        dynStepAt = SystemClock.uptimeMillis();
    }

    /** Шаг перетекания: показанные цвета идут к цели по экспоненте. true — ещё не дошли. */
    private static boolean step() {
        if (dynSettled || dynShown == null || dynTarget == null || dynShown.length != dynTarget.length) {
            return false;
        }
        long now = SystemClock.uptimeMillis();
        float dt = Math.max(1, Math.min(100, now - dynStepAt));
        dynStepAt = now;
        int k = Math.max(1, Math.min(256, Math.round(256f * (1f - (float) Math.exp(-dt / FLOW_MS)))));
        int far = 0;
        for (int i = 0; i < dynShown.length; i++) {
            int s = dynShown[i], t = dynTarget[i];
            if (s == t) {
                continue;
            }
            int sa = s >>> 24, sr = (s >> 16) & 255, sg = (s >> 8) & 255, sb = s & 255;
            int ta = t >>> 24, tr = (t >> 16) & 255, tg = (t >> 8) & 255, tb = t & 255;
            int da = ta - sa, dr = tr - sr, dg = tg - sg, db = tb - sb;
            int m = Math.max(Math.max(Math.abs(da), Math.abs(dr)), Math.max(Math.abs(dg), Math.abs(db)));
            if (m <= 2) {
                dynShown[i] = t;
                continue;
            }
            if (m > far) {
                far = m;
            }
            dynShown[i] = ((sa + ((da * k) >> 8)) << 24) | ((sr + ((dr * k) >> 8)) << 16)
                    | ((sg + ((dg * k) >> 8)) << 8) | (sb + ((db * k) >> 8));
        }
        dynBmp.setPixels(dynShown, 0, dynW, 0, 0, dynW, dynH);
        if (far == 0) {
            dynSettled = true;
        }
        return !dynSettled;
    }

    private static void drawDynamic(Canvas canvas, Object owner, RectF r, float alpha, View host) {
        if (dynOwner.get() != owner || dynBmp == null) {
            return;
        }
        boolean more = step();
        blit(canvas, dynBmp, dynPad, r, alpha);
        if (more && host != null) {
            host.postInvalidateDelayed(FRAME_MS);
        }
    }

    /** GIF и видео, которые рисует сам ImageReceiver. */
    private static void drawMoving(Canvas canvas, android.graphics.drawable.Drawable d, RectF r, float alpha) {
        long now = SystemClock.uptimeMillis();
        boolean first = dynOwner.get() != d || dynBmp == null;
        if (first || (videoEnabled() && now - dynLastSample >= SAMPLE_MS)) {
            dynLastSample = now;
            Bitmap frame = frameOf(d);
            if (first && frame != null) { // первый кадр — сразу, без ожидания фона
                int pad = PADS[size()];
                int[] dims = new int[2];
                int[] px = blurredPixels(frame, pad, dims);
                if (px != null) {
                    accept(d, px, dims[0], dims[1], pad);
                }
            } else {
                push(d, frame, null);
            }
        }
        drawDynamic(canvas, d, r, alpha, null);
    }

    /**
     * Видео в просмотрщике: PhotoViewer.onDraw рисует слой с видео сам, конвейер вставляет этот вызов
     * прямо перед ним (холст уже сдвинут и растянут так, что слой занимает 0,0 – ширина,высота).
     */
    public static void video(View host, Canvas canvas) {
        if (host == null || canvas == null || !enabled() || !videoEnabled()) {
            return;
        }
        if (videoHost.get() != host) {
            videoHost = new WeakReference<>(host);
            pausedSampled = false;
        }
        startTicker();
        RectF r = new RectF(0, 0, host.getWidth(), host.getHeight());
        View parent = host.getParent() instanceof View ? (View) host.getParent() : null;
        drawDynamic(canvas, host, r, 1f, parent);
    }

    private static void startTicker() {
        if (tickerOn) {
            return;
        }
        tickerOn = true;
        main.post(MediaGlow::tick);
    }

    private static void tick() {
        View host = videoHost.get();
        if (host == null || !host.isAttachedToWindow() || !host.isShown() || !enabled() || !videoEnabled()) {
            tickerOn = false;
            return;
        }
        boolean playing = true;
        try {
            PhotoViewer v = viewer();
            if (v != null) {
                playing = v.hgIsPlaying();
            }
        } catch (Throwable ignored) {
        }
        try {
            if (playing) {
                pausedSampled = false;
                sample(host);
            } else if (!pausedSampled && !dynBusy) {
                pausedSampled = true; // на паузе: один кадр, чтобы ореол совпал со стоп-кадром, и больше не считаем
                sample(host);
            }
        } catch (Throwable t) {
            android.util.Log.w("HeartGramGlow", "свечение видео: " + t);
        }
        main.postDelayed(MediaGlow::tick, playing ? SAMPLE_MS : IDLE_MS);
    }

    private static View find(View v, Class<?> type) {
        if (type.isInstance(v) && v.getVisibility() == View.VISIBLE && v.getWidth() > 0) {
            return v;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View r = find(g.getChildAt(i), type);
                if (r != null) {
                    return r;
                }
            }
        }
        return null;
    }

    /** Маленький кадр видео: TextureView отдаёт его сразу, SurfaceView — через PixelCopy. */
    private static void sample(final View host) {
        if (dynBusy || host.getWidth() <= 0 || host.getHeight() <= 0) {
            return;
        }
        int w = 64, h = Math.max(8, Math.min(128, Math.round(64f * host.getHeight() / host.getWidth())));
        final View invalidate = host.getParent() instanceof View ? (View) host.getParent() : host;
        View tv = find(host, TextureView.class);
        if (tv != null) {
            if (!((TextureView) tv).isAvailable()) {
                return;
            }
            Bitmap frame = ((TextureView) tv).getBitmap(Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888));
            push(host, frame, invalidate);
            return;
        }
        View sv = find(host, SurfaceView.class);
        if (sv == null || android.os.Build.VERSION.SDK_INT < 24) {
            return;
        }
        android.view.Surface surface = ((SurfaceView) sv).getHolder().getSurface();
        if (surface == null || !surface.isValid()) {
            return;
        }
        final Bitmap frame = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        android.view.PixelCopy.request((SurfaceView) sv, frame, result -> {
            if (result == android.view.PixelCopy.SUCCESS) {
                push(host, frame, invalidate);
            }
        }, main);
    }

    /** Текущий кадр Drawable в маленький битмап (границы Drawable возвращаются как были). */
    private static Bitmap frameOf(android.graphics.drawable.Drawable d) {
        try {
            int iw = d.getIntrinsicWidth(), ih = d.getIntrinsicHeight();
            if (iw <= 0 || ih <= 0) {
                iw = d.getBounds().width();
                ih = d.getBounds().height();
            }
            if (iw <= 0 || ih <= 0) {
                return null;
            }
            int w = 96, h = Math.max(8, Math.round(96f * ih / iw));
            Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            android.graphics.Rect old = new android.graphics.Rect(d.getBounds());
            d.setBounds(0, 0, w, h);
            d.draw(new Canvas(out));
            d.setBounds(old);
            return out;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Маленькая сильно размытая копия с прозрачными полями pad по краям. */
    private static Bitmap blurred(Bitmap src, int pad) {
        int[] dims = new int[2];
        int[] px = blurredPixels(src, pad, dims);
        return px == null ? null : Bitmap.createBitmap(px, dims[0], dims[1], Bitmap.Config.ARGB_8888);
    }

    /**
     * Пиксели размытой копии; dims получает {ширина, высота}. Три прохода box blur радиусом pad/3 дают
     * мягкий (почти гауссов) спад, который доходит ровно до края копии: у границы ореол уже прозрачный,
     * без обрезанной кромки. Дополнительно прозрачность сглаживается, чтобы хвост гас плавнее.
     */
    private static int[] blurredPixels(Bitmap src, int pad, int[] dims) {
        try {
            int w = CORE, h = Math.max(8, Math.round((float) CORE * src.getHeight() / Math.max(1, src.getWidth())));
            if (h > CORE * 2) {
                h = CORE * 2;
                w = Math.max(8, Math.round(CORE * 2f * src.getWidth() / Math.max(1, src.getHeight())));
            }
            Bitmap s = src.getConfig() == Bitmap.Config.HARDWARE ? src.copy(Bitmap.Config.ARGB_8888, false) : src;
            Bitmap small = Bitmap.createScaledBitmap(s, w, h, true);
            int W = w + pad * 2, H = h + pad * 2;
            int[] px = new int[W * H];
            int[] in = new int[w * h];
            small.getPixels(in, 0, w, 0, 0, w, h);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    px[(y + pad) * W + x + pad] = in[y * w + x] | 0xFF000000;
                }
            }
            int radius = Math.max(2, pad / 3);
            for (int pass = 0; pass < 3; pass++) {
                px = boxBlur(px, W, H, radius);
            }
            for (int i = 0; i < px.length; i++) { // smoothstep по прозрачности: мягче у самого края
                int c = px[i], a = c >>> 24;
                if (a != 0 && a != 255) {
                    float t = a / 255f;
                    int na = Math.round(255f * t * t * (3f - 2f * t));
                    px[i] = (na << 24) | (c & 0x00FFFFFF);
                }
            }
            dims[0] = W;
            dims[1] = H;
            return px;
        } catch (Throwable t) {
            android.util.Log.w("HeartGramGlow", "размытие для свечения: " + t);
            return null;
        }
    }

    private static int[] boxBlur(int[] src, int w, int h, int r) {
        int[] tmp = new int[src.length];
        int[] dst = new int[src.length];
        blurLine(src, tmp, w, h, r, true);
        blurLine(tmp, dst, w, h, r, false);
        return dst;
    }

    private static void blurLine(int[] in, int[] out, int w, int h, int r, boolean horizontal) {
        int lines = horizontal ? h : w, len = horizontal ? w : h;
        for (int l = 0; l < lines; l++) {
            for (int i = 0; i < len; i++) {
                long a = 0, rr = 0, g = 0, b = 0;
                int n = 0;
                for (int k = -r; k <= r; k++) {
                    int j = i + k;
                    n++;
                    if (j < 0 || j >= len) {
                        continue; // за краем прозрачно
                    }
                    int c = horizontal ? in[l * w + j] : in[j * w + l];
                    int ca = c >>> 24;
                    a += ca;
                    rr += ((c >> 16) & 255) * ca;
                    g += ((c >> 8) & 255) * ca;
                    b += (c & 255) * ca;
                }
                int idx = horizontal ? l * w + i : i * w + l;
                out[idx] = a == 0 ? 0 : ((int) (a / n) << 24) | ((int) (rr / a) << 16) | ((int) (g / a) << 8) | (int) (b / a);
            }
        }
    }
}
