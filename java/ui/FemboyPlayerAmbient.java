package org.telegram.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;

/**
 * Подсветка как ambient mode на ютубе: за обложкой большое размытое пятно
 * её же цветов, которое растекается по фону и медленно дышит.
 *
 * Размытие считаем один раз на крошечной копии (64 точки), а растягивает
 * её видеокарта. Дыхание крутим свойствами вида (прозрачность и масштаб),
 * так что сама картинка заново не рисуется.
 */
public class FemboyPlayerAmbient extends View {

    private static final int SIZE = 64;
    private static final int INNER = 34;
    private static final long BREATH_MS = 7000;
    private static final long FADE_MS = 700;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF rect = new RectF();
    private final Rect anchorRect = new Rect();

    private View anchor;
    private Bitmap glow, oldGlow;
    private long fadeStart;
    private float strength = 1f;
    private float modeFactor = 1f;
    private boolean breathing;
    private int generation;

    private final Runnable breathTick = new Runnable() {
        @Override
        public void run() {
            applyBreath();
            // двадцать раз в секунду хватает: дыхание медленное
            if (breathing) AndroidUtilities.runOnUIThread(this, 50);
        }
    };

    public FemboyPlayerAmbient(Context context) {
        super(context);
    }

    public void setAnchor(View view) {
        anchor = view;
        view.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> invalidate());
    }

    /** Насколько ярко: в тёмной теме сильнее, в светлой мягче. */
    public void setStrength(float value) {
        strength = value;
        applyBreath();
    }

    /** Приглушить, пока вместо обложки текст или очередь. */
    public void setModeFactor(float value) {
        if (modeFactor == value) return;
        final float from = modeFactor;
        final android.animation.ValueAnimator animator = android.animation.ValueAnimator.ofFloat(0, 1);
        animator.setDuration(300);
        animator.addUpdateListener(a -> {
            modeFactor = from + (value - from) * (float) a.getAnimatedValue();
            applyBreath();
        });
        animator.start();
    }

    public void setBreathing(boolean on) {
        if (breathing == on) return;
        breathing = on;
        AndroidUtilities.cancelRunOnUIThread(breathTick);
        if (on && isAttachedToWindow()) breathTick.run();
    }

    private void applyBreath() {
        float t = 0.5f;
        if (breathing) {
            final double angle = 2 * Math.PI * (SystemClock.elapsedRealtime() % BREATH_MS) / BREATH_MS;
            t = (float) (Math.sin(angle) + 1) / 2f;
        }
        setAlpha(strength * modeFactor * (0.72f + 0.28f * t));
        final float scale = 1f + 0.06f * t;
        setScaleX(scale);
        setScaleY(scale);
    }

    /** Новая обложка (маленькая копия, её не трогаем). null — погасить. */
    public void setSource(Bitmap small) {
        final int gen = ++generation;
        if (small == null || small.isRecycled()) {
            swap(null);
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            Bitmap made = null;
            try {
                made = makeGlow(small);
            } catch (Throwable ignored) {
            }
            final Bitmap result = made;
            AndroidUtilities.runOnUIThread(() -> {
                if (gen == generation) swap(result);
            });
        });
    }

    private void swap(Bitmap next) {
        oldGlow = glow;
        glow = next;
        fadeStart = SystemClock.elapsedRealtime();
        invalidate();
    }

    /** Обложка в центре прозрачного квадрата, чуть сочнее, и сильно размыта: края сами уходят в ноль. */
    private static Bitmap makeGlow(Bitmap small) {
        final Bitmap out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(out);
        final Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
        final ColorMatrix matrix = new ColorMatrix();
        matrix.setSaturation(1.35f);
        p.setColorFilter(new ColorMatrixColorFilter(matrix));
        final int off = (SIZE - INNER) / 2;
        canvas.drawBitmap(small, null, new Rect(off, off, off + INNER, off + INNER), p);
        final int[] px = new int[SIZE * SIZE];
        out.getPixels(px, 0, SIZE, 0, 0, SIZE, SIZE);
        blur(px, SIZE, SIZE, 7);
        blur(px, SIZE, SIZE, 7);
        blur(px, SIZE, SIZE, 6);
        out.setPixels(px, 0, SIZE, 0, 0, SIZE, SIZE);
        return out;
    }

    /** Простой коробочный блюр с учётом прозрачности: цвет домножаем на альфу, иначе края темнеют. */
    private static void blur(int[] px, int w, int h, int r) {
        final float[] a = new float[w * h], rr = new float[w * h], g = new float[w * h], b = new float[w * h];
        for (int i = 0; i < px.length; i++) {
            final int c = px[i];
            final float al = (c >>> 24) / 255f;
            a[i] = al;
            rr[i] = ((c >> 16) & 0xff) * al;
            g[i] = ((c >> 8) & 0xff) * al;
            b[i] = (c & 0xff) * al;
        }
        pass(a, rr, g, b, w, h, r, true);
        pass(a, rr, g, b, w, h, r, false);
        for (int i = 0; i < px.length; i++) {
            final float al = a[i];
            if (al <= 0.001f) {
                px[i] = 0;
                continue;
            }
            final int ai = Math.min(255, Math.round(al * 255));
            final int ri = Math.min(255, Math.round(rr[i] / al));
            final int gi = Math.min(255, Math.round(g[i] / al));
            final int bi = Math.min(255, Math.round(b[i] / al));
            px[i] = (ai << 24) | (ri << 16) | (gi << 8) | bi;
        }
    }

    private static void pass(float[] a, float[] r, float[] g, float[] b, int w, int h, int radius, boolean horizontal) {
        final int len = horizontal ? w : h, lines = horizontal ? h : w;
        final float[] ta = new float[len], tr = new float[len], tg = new float[len], tb = new float[len];
        final float div = 2 * radius + 1;
        for (int line = 0; line < lines; line++) {
            for (int i = 0; i < len; i++) {
                float sa = 0, sr = 0, sg = 0, sb = 0;
                for (int k = -radius; k <= radius; k++) {
                    final int j = i + k;
                    if (j < 0 || j >= len) continue;
                    final int idx = horizontal ? line * w + j : j * w + line;
                    sa += a[idx];
                    sr += r[idx];
                    sg += g[idx];
                    sb += b[idx];
                }
                ta[i] = sa / div;
                tr[i] = sr / div;
                tg[i] = sg / div;
                tb[i] = sb / div;
            }
            for (int i = 0; i < len; i++) {
                final int idx = horizontal ? line * w + i : i * w + line;
                a[idx] = ta[i];
                r[idx] = tr[i];
                g[idx] = tg[i];
                b[idx] = tb[i];
            }
        }
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (breathing) {
            AndroidUtilities.cancelRunOnUIThread(breathTick);
            breathTick.run();
        } else {
            applyBreath();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        AndroidUtilities.cancelRunOnUIThread(breathTick);
    }

    private boolean findAnchor() {
        if (anchor == null || anchor.getWidth() == 0) return false;
        // место обложки внутри родителя: свой масштаб от дыхания сюда не примешиваем
        float x = 0, y = 0;
        View v = anchor;
        while (v != null && v != getParent()) {
            x += v.getLeft() + v.getTranslationX();
            y += v.getTop() + v.getTranslationY();
            v = v.getParent() instanceof View ? (View) v.getParent() : null;
        }
        if (v == null) return false;
        x -= getLeft();
        y -= getTop();
        anchorRect.set((int) x, (int) y, (int) x + anchor.getWidth(), (int) y + anchor.getHeight());
        return true;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (glow == null && oldGlow == null) return;
        if (!findAnchor()) return;
        final float side = Math.max(anchorRect.width(), anchorRect.height());
        // пятно заметно больше обложки: свет должен вылезать на фон
        final float grow = side * 0.62f;
        rect.set(anchorRect.left - grow, anchorRect.top - grow, anchorRect.right + grow, anchorRect.bottom + grow);
        if (getPivotX() != rect.centerX()) setPivotX(rect.centerX());
        if (getPivotY() != rect.centerY()) setPivotY(rect.centerY());
        final float fade = Math.min(1f, (SystemClock.elapsedRealtime() - fadeStart) / (float) FADE_MS);
        if (oldGlow != null && fade < 1f) {
            paint.setAlpha((int) (255 * (1f - fade)));
            canvas.drawBitmap(oldGlow, null, rect, paint);
        }
        if (glow != null) {
            paint.setAlpha((int) (255 * fade));
            canvas.drawBitmap(glow, null, rect, paint);
        }
        if (fade < 1f) {
            invalidate();
        } else {
            oldGlow = null;
        }
    }
}
