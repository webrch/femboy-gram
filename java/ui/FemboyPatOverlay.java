package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import org.telegram.messenger.FileLog;
import org.telegram.ui.ActionBar.SimpleTextView;

import java.io.InputStream;
import java.lang.ref.WeakReference;

/**
 * Патпат: рука спускается сверху и гладит название, надпись приседает в такт.
 *
 * Кадры руки и звуки из мода PatPat (LopyMine, LGPL-3.0), assets/patpat:
 * лист 560x112, пять кадров по 112, второй (с нуля) самый нижний, на нём
 * шлепок. Вид держится поверх всего окна, касаний не ловит и убирает себя,
 * когда рука уходит. Повторные нажатия добавляют шлепков.
 */
@SuppressLint("ViewConstructor")
public class FemboyPatOverlay extends View {

    private static final int FRAMES = 5;
    private static final int FRAME_SIZE = 112;
    private static final long FRAME = 60;
    private static final long PAT = FRAME * FRAMES;
    private static final long ENTER = 160;
    private static final long EXIT = 200;
    /** Кадр, на котором рука давит сильнее всего. */
    private static final int HIT_FRAME = 2;
    /** Низ руки в нижнем кадре и середина кисти, в пикселях листа. */
    private static final float HAND_BOTTOM = 97, HAND_CENTER = 52;
    /** Насколько приседает надпись на каждом кадре. */
    private static final float[] SQUASH = {0f, 0.09f, 0.21f, 0.12f, 0.03f};
    private static final int MAX_AHEAD = 5;

    private static WeakReference<FemboyPatOverlay> current;
    private static Bitmap sheet;
    private static boolean sheetFailed;

    /** Погладить надпись: новая рука или ещё шлепков к нынешней. */
    public static void pat(View title) {
        if (title == null || !title.isAttachedToWindow() || title.getWidth() == 0) {
            return;
        }
        FemboyPatSound.prepare();
        final FemboyPatOverlay running = current == null ? null : current.get();
        if (running != null && running.title == title && running.isAttachedToWindow() && !running.done) {
            running.more();
            return;
        }
        if (running != null) {
            running.finish();
        }
        if (sheet(title.getContext()) == null) {
            return;
        }
        final View root = title.getRootView();
        if (!(root instanceof ViewGroup)) {
            return;
        }
        final FemboyPatOverlay overlay = new FemboyPatOverlay(title.getContext(), title);
        ((ViewGroup) root).addView(overlay, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        current = new WeakReference<>(overlay);
    }

    private static Bitmap sheet(Context context) {
        if (sheet == null && !sheetFailed) {
            try (InputStream in = context.getAssets().open("patpat/patpat.png")) {
                sheet = BitmapFactory.decodeStream(in);
            } catch (Throwable t) {
                FileLog.e(t);
            }
            sheetFailed = sheet == null;
        }
        return sheet;
    }

    private final View title;
    private final SimpleTextView simple;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Rect src = new Rect();
    private final RectF dst = new RectF();
    private final int[] at = new int[2];
    private final int[] me = new int[2];

    private long start;
    // один тап = один шлепок; частые тапы добавляют ещё
    private int pats = 1;
    private int hits;
    private boolean done;

    private FemboyPatOverlay(Context context, View title) {
        super(context);
        this.title = title;
        this.simple = title instanceof SimpleTextView ? (SimpleTextView) title : null;
        // поверх соседей с высотой; фона нет, значит и тени не будет
        setTranslationZ(dp(40));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        start = SystemClock.uptimeMillis();
    }

    /** Ещё нажатие, пока рука здесь. */
    private void more() {
        final long t = SystemClock.uptimeMillis() - start;
        final long patEnd = ENTER + pats * PAT;
        if (t < patEnd) {
            final int now = (int) Math.max(0, (t - ENTER) / PAT);
            pats = Math.min(pats + 1, now + MAX_AHEAD);
        } else {
            // рука уже уходила: возвращаем её с той же высоты
            final float e = Math.min(1f, (t - patEnd) / (float) EXIT);
            start = SystemClock.uptimeMillis() - (long) ((1f - e) * ENTER);
            pats = 1;
            hits = 0;
        }
        invalidate();
    }

    /** Точка надписи в координатах окна: сдвиги и матрицы всех родителей, у самой надписи только сдвиг. */
    private void toWindow(float[] point) {
        point[0] += title.getLeft() + title.getTranslationX() - title.getScrollX();
        point[1] += title.getTop() + title.getTranslationY() - title.getScrollY();
        android.view.ViewParent parent = title.getParent();
        while (parent instanceof View) {
            final View view = (View) parent;
            if (!view.getMatrix().isIdentity()) {
                // getMatrix уже со сдвигом translation, поэтому left/top отдельно
                view.getMatrix().mapPoints(point);
                point[0] += view.getLeft() - view.getScrollX();
                point[1] += view.getTop() - view.getScrollY();
            } else {
                point[0] += view.getLeft() - view.getScrollX();
                point[1] += view.getTop() - view.getScrollY();
            }
            parent = view.getParent();
        }
    }

    private void finish() {
        if (done) {
            return;
        }
        done = true;
        resetTitle();
        final ViewGroup parent = (ViewGroup) getParent();
        if (parent != null) {
            parent.removeView(this);
        }
        if (current != null && current.get() == this) {
            current = null;
        }
    }

    private void resetTitle() {
        title.setScaleX(1f);
        title.setScaleY(1f);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        resetTitle();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (done) {
            return;
        }
        final View parent = (View) title.getParent();
        if (parent == null || !title.isAttachedToWindow() || title.getVisibility() != VISIBLE) {
            post(this::finish);
            return;
        }
        final long now = SystemClock.uptimeMillis();
        final long t = now - start;
        final long patEnd = ENTER + pats * PAT;

        int frame = 0;
        float squash = 0f;
        float lift = 0f;
        float alpha = 1f;
        if (t < ENTER) {
            final float p = t / (float) ENTER;
            lift = (1f - p) * (1f - p);
            alpha = Math.min(1f, p * 1.6f);
        } else if (t < patEnd) {
            final float k = (t - ENTER) / (float) FRAME;
            final int step = (int) k;
            frame = step % FRAMES;
            final int pat = step / FRAMES;
            // приседание между кадрами сглажено, рука же идёт кадрами, как в гифке
            final float f = k - step;
            final float next = step + 1 < pats * FRAMES ? SQUASH[(frame + 1) % FRAMES] : 0f;
            squash = SQUASH[frame] + (next - SQUASH[frame]) * f;
            if (frame >= HIT_FRAME && pat >= hits) {
                hits = pat + 1;
                FemboyPatSound.play();
            }
        } else if (t < patEnd + EXIT) {
            final float e = (t - patEnd) / (float) EXIT;
            lift = e * e;
            alpha = 1f - e;
        } else {
            post(this::finish);
            return;
        }

        // где надпись на экране: точки самой надписи переводим через все
        // сдвиги и масштабы родителей (свой масштаб надписи наш, его не берём)
        getLocationInWindow(me);
        final float h = title.getHeight();
        final float textX, textW, textH;
        if (simple != null && simple.getTextWidth() > 0) {
            textX = simple.getTextStartX() - simple.getX();
            textW = simple.getTextWidth();
            textH = Math.max(dp(14), simple.getTextHeight());
        } else if (title instanceof org.telegram.ui.Components.AnimatedTextView) {
            final org.telegram.ui.Components.AnimatedTextView animated = (org.telegram.ui.Components.AnimatedTextView) title;
            textW = Math.max(dp(20), animated.getDrawable().getCurrentWidth());
            textX = (animated.getDrawable().getGravity() & android.view.Gravity.HORIZONTAL_GRAVITY_MASK) == android.view.Gravity.CENTER_HORIZONTAL
                    ? (title.getWidth() - textW) / 2f : title.getPaddingLeft();
            textH = Math.max(dp(14), animated.getDrawable().getTextSize());
        } else if (title instanceof android.widget.ImageView && ((android.widget.ImageView) title).getDrawable() != null) {
            final android.graphics.drawable.Drawable d = ((android.widget.ImageView) title).getDrawable();
            final float fit = Math.min(1f, Math.min(title.getWidth() / (float) Math.max(1, d.getIntrinsicWidth()), h / (float) Math.max(1, d.getIntrinsicHeight())));
            textW = d.getIntrinsicWidth() * fit;
            textH = d.getIntrinsicHeight() * fit;
            textX = (title.getWidth() - textW) / 2f;
        } else {
            textX = 0;
            textW = title.getWidth();
            textH = Math.min(h, dp(24));
        }
        final float[] a = {textX, (h - textH) / 2f};
        final float[] b = {textX + textW, (h + textH) / 2f};
        toWindow(a);
        toWindow(b);
        final float sx = Math.max(0.1f, (b[0] - a[0]) / Math.max(1f, textW));
        final float sy = Math.max(0.1f, (b[1] - a[1]) / Math.max(1f, textH));
        final float left = a[0] - me[0] - textX * sx;
        final float top = a[1] - me[1] - (h - textH) / 2f * sy;
        final float textTop = (h - textH) / 2f;
        final float centerX = textX + textW / 2f;

        title.setPivotX(centerX);
        title.setPivotY(textTop + textH);
        title.setScaleY(1f - squash);
        title.setScaleX(1f + squash * 0.4f);

        final float size = Math.max(dp(48), Math.min(dp(96), Math.max(textH * 2.6f, textW * 0.5f))) * sy;
        final float k = size / FRAME_SIZE;
        // низ руки в нижнем кадре ложится на чуть присевшую надпись
        final float y = top + (textTop + textH * (SQUASH[HIT_FRAME] + 0.04f)) * sy - HAND_BOTTOM * k - dp(70) * lift;
        final float x = left + centerX * sx - HAND_CENTER * k;

        src.set(frame * FRAME_SIZE, 0, frame * FRAME_SIZE + FRAME_SIZE, FRAME_SIZE);
        dst.set(x, y, x + size, y + size);
        paint.setAlpha((int) (alpha * 255));
        canvas.drawBitmap(sheet, src, dst, paint);
        postInvalidateOnAnimation();
    }
}
