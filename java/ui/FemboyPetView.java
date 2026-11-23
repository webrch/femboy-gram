package org.telegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.widget.FrameLayout;

/**
 * 3D-тилкайо, питомец серии. Общий договор двух частей: экран питомца
 * (FemboyPetSheet) только создаёт этот вид и зовёт методы ниже, а как он
 * рисует, решает сам вид. Сигнатуры не менять.
 *
 * Внутри TextureView с прозрачным фоном и свой GL-поток (FemboyPetRenderer),
 * модель строится из фигур в FemboyPetMesh, шейдеры в FemboyPetShaders.
 */
public class FemboyPetView extends FrameLayout {

    public static final int STAGE_EGG = 0;
    public static final int STAGE_HATCHING = 1;
    public static final int STAGE_BABY = 2;
    public static final int STAGE_ADULT = 3;

    private final TextureView texture;
    private final FemboyPetRenderer renderer;
    private boolean resumed = true;

    // жест
    private final int touchSlop;
    private float downX, downY, lastX, lastY;
    private long downTime;
    private boolean dragging;
    private VelocityTracker velocity;

    public FemboyPetView(Context context) {
        super(context);
        renderer = new FemboyPetRenderer(context.getResources().getDisplayMetrics().density);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        texture = new TextureView(context);
        texture.setOpaque(false);
        texture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture st, int width, int height) {
                renderer.start(st, width, height);
                updateRunning();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture st, int width, int height) {
                renderer.resize(width, height);
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
                // GL-поток сам освободит SurfaceTexture после разбора EGL
                renderer.stop();
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture st) {
            }
        });
        addView(texture, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    /** Стадия: яйцо, треснувшее яйцо, малыш, взрослый. Можно звать на ходу, смена с анимацией. */
    public void setStage(int stage) {
        renderer.setStage(stage);
    }

    /** Цвет огонька серии: им подсвечивается фон и мелкие детали. ARGB. */
    public void setAccent(int color) {
        renderer.setAccent(color);
    }

    /** Порадоваться: подпрыгнуть (новое имя, новый день серии). */
    public void cheer() {
        renderer.cheer();
    }

    /** Ограничить частоту кадров в покое (питомец в чате). 0 = как у экрана. */
    public void setIdleFps(int fps) {
        renderer.setIdleFps(fps);
    }

    /** Остановить отрисовку, когда экран закрыт. */
    public void onPause() {
        resumed = false;
        updateRunning();
    }

    public void onResume() {
        resumed = true;
        updateRunning();
    }

    // ---------- видимость: рисуем только когда на экране ----------

    private void updateRunning() {
        renderer.setRunning(resumed && isAttachedToWindow() && getWindowVisibility() == VISIBLE && isShown());
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateRunning();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        updateRunning();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        updateRunning();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        updateRunning();
    }

    // ---------- палец: крутить по Y, чуть наклонять, тап = радость ----------

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int w = Math.max(1, getWidth());
        float perPx = (float) Math.PI * 1.2f / w;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = e.getX();
                downY = lastY = e.getY();
                downTime = e.getEventTime();
                dragging = false;
                if (velocity != null) velocity.recycle();
                velocity = VelocityTracker.obtain();
                velocity.addMovement(e);
                renderer.setTouching(true);
                return true;
            case MotionEvent.ACTION_MOVE: {
                if (velocity != null) velocity.addMovement(e);
                float x = e.getX(), y = e.getY();
                if (!dragging) {
                    float dx = x - downX, dy = y - downY;
                    if (Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy)) {
                        dragging = true;
                        ViewParent parent = getParent();
                        if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
                        lastX = x;
                        lastY = y;
                    } else if (Math.abs(dy) > touchSlop * 2) {
                        // вертикальный жест не наш: отдаём шторке
                        endTouch();
                        return false;
                    }
                }
                if (dragging) {
                    renderer.drag((x - lastX) * perPx, (y - lastY) * perPx * 0.5f);
                    lastX = x;
                    lastY = y;
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
                if (dragging) {
                    if (velocity != null) {
                        velocity.addMovement(e);
                        velocity.computeCurrentVelocity(1000);
                        renderer.fling(velocity.getXVelocity() * perPx);
                    }
                } else if (e.getEventTime() - downTime < 400) {
                    cheer();
                    performClick();
                }
                endTouch();
                return true;
            case MotionEvent.ACTION_CANCEL:
                endTouch();
                return true;
        }
        return super.onTouchEvent(e);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private void endTouch() {
        dragging = false;
        renderer.setTouching(false);
        if (velocity != null) {
            velocity.recycle();
            velocity = null;
        }
    }
}
