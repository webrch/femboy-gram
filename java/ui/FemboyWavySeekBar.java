package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;

/**
 * Перемотка в духе MD3 Expressive: пройденная часть идёт волной, которая
 * бежит, пока играет, и выпрямляется на паузе. Остаток ровный, в конце
 * точка-ограничитель, бегунок круглый. Время рисуем тут же, под дорожкой.
 */
public class FemboyWavySeekBar extends View {

    public interface Listener {
        void onSeekStart();

        /** Палец ещё едет: показать время, но не перематывать. */
        void onSeekMove(float progress);

        void onSeekEnd(float progress);
    }

    private static final float WAVE_LENGTH_DP = 36;
    private static final float AMPLITUDE_DP = 3.2f;
    // один период волны в секунду с небольшим: спокойно, но заметно
    private static final float WAVE_PERIOD_MS = 1300;
    private static final float AMP_ANIM_MS = 350;

    private final Paint activePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint inactivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint timePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Path wave = new Path();

    private Listener listener;
    private float progress;
    private float dragProgress;
    private boolean dragging;
    private long durationMs;
    private boolean playing;

    private float amplitude;
    private float phase;
    private float thumbScale = 1f;
    private long lastFrame;

    public FemboyWavySeekBar(Context context) {
        super(context);
        activePaint.setStyle(Paint.Style.STROKE);
        activePaint.setStrokeCap(Paint.Cap.ROUND);
        activePaint.setStrokeJoin(Paint.Join.ROUND);
        activePaint.setStrokeWidth(dp(4));
        inactivePaint.setStyle(Paint.Style.STROKE);
        inactivePaint.setStrokeCap(Paint.Cap.ROUND);
        inactivePaint.setStrokeWidth(dp(4));
        timePaint.setTextSize(dp(12));
        timePaint.setFontFeatureSettings("tnum");
        setColors(0xff6750a4, 0xffe8def8, 0xff49454f);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setColors(int active, int inactive, int text) {
        activePaint.setColor(active);
        thumbPaint.setColor(active);
        inactivePaint.setColor(inactive);
        timePaint.setColor(text);
        invalidate();
    }

    public void setDuration(long ms) {
        if (durationMs == ms) return;
        durationMs = Math.max(0, ms);
        invalidate();
    }

    public void setProgress(float value) {
        value = clamp(value);
        if (value == progress) return;
        progress = value;
        if (!dragging) invalidate();
    }

    public float getProgress() {
        return dragging ? dragProgress : progress;
    }

    public boolean isDragging() {
        return dragging;
    }

    public void setPlaying(boolean playing) {
        if (this.playing == playing) return;
        this.playing = playing;
        lastFrame = 0;
        invalidate();
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private float trackLeft() {
        return dp(10);
    }

    private float trackRight() {
        return getWidth() - dp(10);
    }

    private float trackY() {
        return dp(16);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(54), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        final long now = SystemClock.elapsedRealtime();
        final float dt = lastFrame == 0 ? 0 : Math.min(64, now - lastFrame);
        lastFrame = now;

        // волна гаснет на паузе и пока человек тянет бегунок
        final float targetAmp = playing && !dragging ? 1f : 0f;
        if (amplitude < targetAmp) amplitude = Math.min(targetAmp, amplitude + dt / AMP_ANIM_MS);
        else if (amplitude > targetAmp) amplitude = Math.max(targetAmp, amplitude - dt / AMP_ANIM_MS);
        if (amplitude > 0) phase = (phase + (float) (2 * Math.PI) * dt / WAVE_PERIOD_MS) % (float) (2 * Math.PI);
        final float targetThumb = dragging ? 1.35f : 1f;
        if (thumbScale != targetThumb) {
            final float step = dt / 150f * 0.35f;
            thumbScale = thumbScale < targetThumb ? Math.min(targetThumb, thumbScale + step) : Math.max(targetThumb, thumbScale - step);
        }

        final float left = trackLeft(), right = trackRight(), cy = trackY();
        final float p = dragging ? dragProgress : progress;
        final float x = left + (right - left) * p;
        final float thumbR = dp(7) * thumbScale;
        final float gap = dp(4);

        // пройденное: волной или прямой
        final float activeEnd = x - thumbR - gap;
        if (activeEnd > left) {
            if (amplitude <= 0.01f) {
                canvas.drawLine(left, cy, activeEnd, cy, activePaint);
            } else {
                final float length = dp(WAVE_LENGTH_DP);
                final float amp = dp(AMPLITUDE_DP) * amplitude;
                final float ramp = length / 2f;
                final float step = Math.max(1, dp(1.5f));
                wave.rewind();
                for (float xi = left; ; xi += step) {
                    final float cx = Math.min(xi, activeEnd);
                    // у краёв волна мягко сходит в ноль, чтобы не обрываться на гребне
                    final float env = Math.min(1f, Math.min((cx - left) / ramp, (activeEnd - cx) / ramp));
                    final float y = cy + amp * Math.max(0, env) * (float) Math.sin(2 * Math.PI * (cx - left) / length - phase);
                    if (cx == left) wave.moveTo(cx, y);
                    else wave.lineTo(cx, y);
                    if (cx >= activeEnd) break;
                }
                canvas.drawPath(wave, activePaint);
            }
        }

        // остаток и точка в конце
        final float inactiveStart = x + thumbR + gap;
        if (inactiveStart < right) {
            canvas.drawLine(inactiveStart, cy, right, cy, inactivePaint);
        }
        if (right - x > thumbR + dp(6)) {
            canvas.drawCircle(right, cy, dp(2), thumbPaint);
        }
        canvas.drawCircle(x, cy, thumbR, thumbPaint);

        // время
        if (durationMs > 0) {
            final long at = (long) (p * durationMs);
            final String nowText = AndroidUtilities.formatShortDuration((int) (at / 1000));
            final String leftText = "-" + AndroidUtilities.formatShortDuration((int) (Math.max(0, durationMs - at) / 1000));
            final float baseline = getHeight() - dp(6);
            canvas.drawText(nowText, left - dp(2), baseline, timePaint);
            canvas.drawText(leftText, right + dp(2) - timePaint.measureText(leftText), baseline, timePaint);
        }

        if (amplitude > 0 || amplitude != targetAmp || thumbScale != targetThumb) {
            if (isAttachedToWindow() && getVisibility() == VISIBLE) postInvalidateOnAnimation();
        } else {
            lastFrame = 0;
        }
    }

    private float fromX(float x) {
        final float left = trackLeft(), right = trackRight();
        return clamp((x - left) / Math.max(1, right - left));
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                dragging = true;
                dragProgress = fromX(ev.getX());
                lastFrame = 0;
                if (listener != null) listener.onSeekStart();
                if (listener != null) listener.onSeekMove(dragProgress);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!dragging) return false;
                dragProgress = fromX(ev.getX());
                if (listener != null) listener.onSeekMove(dragProgress);
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                if (!dragging) return false;
                dragProgress = fromX(ev.getX());
                progress = dragProgress;
                dragging = false;
                if (listener != null) listener.onSeekEnd(progress);
                invalidate();
                return true;
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                invalidate();
                return true;
        }
        return false;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        lastFrame = 0;
        invalidate();
    }
}
