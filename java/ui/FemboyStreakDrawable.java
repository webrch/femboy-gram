package org.telegram.ui;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ReplacementSpan;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;

import java.lang.ref.WeakReference;

/**
 * Огонёк серии: свой рисунок пламени с градиентом и число жирным. Цвет по
 * длине серии, от год и больше радуга медленно переливается.
 */
public final class FemboyStreakDrawable {

    private FemboyStreakDrawable() {}

    // пламя в квадрате 1x1, рисуется в прямоугольнике 0.8 x 1
    private static final Path OUTER = new Path();
    private static final Path INNER = new Path();

    static {
        OUTER.moveTo(0.50f, 1.00f);
        OUTER.cubicTo(0.20f, 1.00f, 0.02f, 0.80f, 0.08f, 0.56f);
        OUTER.cubicTo(0.12f, 0.40f, 0.26f, 0.34f, 0.28f, 0.16f);
        OUTER.cubicTo(0.40f, 0.24f, 0.44f, 0.36f, 0.44f, 0.44f);
        OUTER.cubicTo(0.50f, 0.28f, 0.56f, 0.12f, 0.64f, 0.00f);
        OUTER.cubicTo(0.74f, 0.20f, 0.96f, 0.36f, 0.94f, 0.62f);
        OUTER.cubicTo(0.92f, 0.86f, 0.74f, 1.00f, 0.50f, 1.00f);
        OUTER.close();

        INNER.moveTo(0.50f, 0.97f);
        INNER.cubicTo(0.36f, 0.97f, 0.28f, 0.86f, 0.32f, 0.74f);
        INNER.cubicTo(0.36f, 0.64f, 0.46f, 0.60f, 0.50f, 0.48f);
        INNER.cubicTo(0.56f, 0.60f, 0.70f, 0.68f, 0.68f, 0.82f);
        INNER.cubicTo(0.66f, 0.93f, 0.58f, 0.97f, 0.50f, 0.97f);
        INNER.close();
    }

    private static final Path tmp = new Path();
    private static final Matrix matrix = new Matrix();
    private static final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint core = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final float[] hsv = new float[3];
    private static LinearGradient shader;
    private static int shaderTop, shaderBottom;
    private static float shaderH;

    public static boolean animated(int streak) {
        return streak >= 365;
    }

    /** {светлый верх, основной низ, цвет числа}. */
    public static int[] colors(int streak) {
        if (streak >= 365) {
            final float hue = (SystemClock.uptimeMillis() / 40L) % 360L;
            hsv[0] = hue; hsv[1] = 0.85f; hsv[2] = 1f;
            final int main = Color.HSVToColor(hsv);
            hsv[0] = (hue + 60f) % 360f; hsv[1] = 0.55f; hsv[2] = 1f;
            final int light = Color.HSVToColor(hsv);
            return new int[]{light, main, main};
        }
        if (streak >= 100) return new int[]{0xFF80E5FF, 0xFF2962FF, 0xFF2979FF};
        if (streak >= 30) return new int[]{0xFFFF7EDB, 0xFF9C27F0, 0xFFD500F9};
        if (streak >= 10) return new int[]{0xFFFFC400, 0xFFFF3D00, 0xFFFF5722};
        final int accent = Theme.getColor(Theme.key_featuredStickers_addButton);
        return new int[]{ColorUtils.blendARGB(accent, Color.WHITE, 0.45f), accent, accent};
    }

    /** Основной цвет яруса: для питомца и полосы. */
    public static int tierColor(int streak) {
        return colors(streak)[1];
    }

    /** Пламя в прямоугольнике x, y, w, h. Только с главного потока. */
    public static void drawFlame(Canvas canvas, float x, float y, float w, float h, int light, int main, int alpha) {
        if (shader == null || shaderTop != light || shaderBottom != main || shaderH != h) {
            shader = new LinearGradient(0, 0, 0, h, light, main, Shader.TileMode.CLAMP);
            shaderTop = light;
            shaderBottom = main;
            shaderH = h;
        }
        canvas.save();
        canvas.translate(x, y);
        matrix.setScale(w, h);
        OUTER.transform(matrix, tmp);
        fill.setShader(shader);
        fill.setAlpha(alpha);
        canvas.drawPath(tmp, fill);
        INNER.transform(matrix, tmp);
        core.setColor(ColorUtils.blendARGB(light, Color.WHITE, 0.6f));
        core.setAlpha((int) (alpha * 0.85f));
        canvas.drawPath(tmp, core);
        canvas.restore();
    }

    // ---------------------------------------------------------------- у имени

    private static final TextPaint measure = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    private static void setupNumber(TextPaint out, Paint base) {
        out.setTextSize(base.getTextSize() * 0.86f);
        out.setTypeface(AndroidUtilities.bold());
    }

    public static float spanWidth(Paint paint, int streak) {
        synchronized (measure) {
            setupNumber(measure, paint);
            final float size = paint.getTextSize();
            return size * 0.24f + size * 0.72f + size * 0.08f + measure.measureText(String.valueOf(streak));
        }
    }

    /** Огонёк с числом внутри текста имени. */
    public static final class Span extends ReplacementSpan {
        public final int streak;
        public final boolean waiting;
        private final WeakReference<View> parent;
        private final TextPaint number = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final String text;
        /** Где нарисован в последний раз, в координатах текста. -1 — не рисовался. */
        float lastX = -1;
        float lastWidth;

        public Span(View parent, int streak, boolean waiting) {
            this.parent = parent == null ? null : new WeakReference<>(parent);
            this.streak = streak;
            this.waiting = waiting;
            this.text = String.valueOf(streak);
        }

        @Override
        public int getSize(@NonNull Paint paint, CharSequence s, int start, int end, Paint.FontMetricsInt fm) {
            return (int) Math.ceil(spanWidth(paint, streak));
        }

        @Override
        public void draw(@NonNull Canvas canvas, CharSequence s, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
            final float size = paint.getTextSize();
            final Paint.FontMetrics fm = paint.getFontMetrics();
            final float center = y + (fm.ascent + fm.descent) / 2f;
            final int[] c = colors(streak);
            final int alpha = paint.getAlpha();
            // ждёт сегодняшних сообщений: огонёк приглушён
            final int flameAlpha = waiting ? (int) (alpha * 0.4f) : alpha;
            final float h = size * 0.9f;
            final float w = size * 0.72f;
            final float fx = x + size * 0.24f;
            drawFlame(canvas, fx, center - h / 2f, w, h, c[0], c[1], flameAlpha);
            setupNumber(number, paint);
            number.setColor(c[2]);
            number.setAlpha(waiting ? (int) (alpha * 0.6f) : alpha);
            canvas.drawText(text, fx + w + size * 0.08f, y, number);
            lastX = x;
            lastWidth = spanWidth(paint, streak);
            if (animated(streak) && parent != null) {
                final View v = parent.get();
                if (v != null) v.postInvalidateDelayed(66);
            }
        }
    }

    public static Span find(CharSequence text) {
        if (!(text instanceof Spanned)) return null;
        final Span[] spans = ((Spanned) text).getSpans(0, text.length(), Span.class);
        return spans == null || spans.length == 0 ? null : spans[spans.length - 1];
    }

    // ---------------------------------------------------------------- нажатие в шапке

    private static boolean headerDown;

    /** Нажатие по огоньку в заголовке чата. Координаты события в родителе заголовка. */
    public static boolean headerTouch(SimpleTextView title, MotionEvent ev, Runnable open) {
        if (title == null) return false;
        final Span span = find(title.getText());
        final boolean inside;
        if (span == null || span.lastX < 0) {
            inside = false;
        } else {
            final float pad = AndroidUtilities.dp(8);
            final float left = title.getTextStartX() + span.lastX;
            inside = ev.getX() >= left - pad && ev.getX() <= left + span.lastWidth + pad
                    && ev.getY() >= title.getY() - pad && ev.getY() <= title.getY() + title.getHeight() + pad;
        }
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                headerDown = inside;
                return inside;
            case MotionEvent.ACTION_MOVE:
                return headerDown;
            case MotionEvent.ACTION_UP:
                final boolean was = headerDown;
                headerDown = false;
                if (was && inside && open != null) open.run();
                return was;
            case MotionEvent.ACTION_CANCEL:
                final boolean wasDown = headerDown;
                headerDown = false;
                return wasDown;
        }
        return headerDown;
    }

    // ---------------------------------------------------------------- большой огонёк

    /** Огонёк отдельной картинкой, для экрана питомца. */
    public static final class Icon extends Drawable {
        private int streak;
        private int alpha = 255;
        private final Runnable tick = this::invalidateSelf;

        public Icon(int streak) {
            this.streak = streak;
        }

        public void setStreak(int streak) {
            this.streak = streak;
            invalidateSelf();
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            final int[] c = colors(streak);
            final float h = getBounds().height();
            final float w = Math.min(getBounds().width(), h * 0.8f);
            drawFlame(canvas, getBounds().centerX() - w / 2f, getBounds().top, w, h, c[0], c[1], alpha);
            if (animated(streak)) {
                unscheduleSelf(tick);
                scheduleSelf(tick, SystemClock.uptimeMillis() + 66);
            }
        }

        @Override
        public void setAlpha(int alpha) {
            this.alpha = alpha;
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public int getIntrinsicWidth() {
            return AndroidUtilities.dp(20);
        }

        @Override
        public int getIntrinsicHeight() {
            return AndroidUtilities.dp(24);
        }
    }
}
