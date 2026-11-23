package org.telegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import org.telegram.ui.Components.LayoutHelper;

/**
 * Кнопка плеера в духе MD3 Expressive: форма живая. Скругление задаётся
 * долей от стороны (0.5 — круг), при нажатии кнопка становится квадратнее
 * и чуть сжимается, отпускается на пружине.
 */
public class FemboyPlayerButton extends FrameLayout {

    private static final FloatPropertyCompat<FemboyPlayerButton> CORNER = new FloatPropertyCompat<FemboyPlayerButton>("corner") {
        @Override
        public float getValue(FemboyPlayerButton object) {
            return object.corner;
        }

        @Override
        public void setValue(FemboyPlayerButton object, float value) {
            object.corner = value;
            object.invalidate();
        }
    };

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    public final ImageView icon;

    private float corner = 0.5f;
    private float restCorner = 0.5f;
    private float pressedCorner = 0.22f;
    private float pressScale = 0.92f;
    private boolean wasPressed;
    private SpringAnimation spring;

    public FemboyPlayerButton(Context context, int iconSizeDp) {
        super(context);
        setWillNotDraw(false);
        setClickable(true);
        icon = new ImageView(context);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        addView(icon, LayoutHelper.createFrame(iconSizeDp, iconSizeDp, Gravity.CENTER));
        paint.setColor(Color.TRANSPARENT);
    }

    public void setIcon(int res) {
        icon.setImageResource(res);
    }

    public void setColors(int background, int iconColor) {
        paint.setColor(background);
        icon.setColorFilter(new PorterDuffColorFilter(iconColor, PorterDuff.Mode.SRC_IN));
        invalidate();
    }

    /** Форма в покое (0.5 круг) и при нажатии. */
    public void setShape(float rest, float pressed, boolean animate) {
        pressedCorner = pressed;
        if (restCorner == rest && (animate || corner == rest)) return;
        restCorner = rest;
        if (animate && isAttachedToWindow()) {
            springTo(isPressed() ? pressedCorner : restCorner, SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY);
        } else {
            if (spring != null) spring.cancel();
            corner = isPressed() ? pressedCorner : restCorner;
            invalidate();
        }
    }

    public void setPressScale(float scale) {
        pressScale = scale;
    }

    private void springTo(float value, float damping) {
        if (spring == null) {
            spring = new SpringAnimation(this, CORNER);
            spring.setSpring(new SpringForce());
        }
        spring.getSpring().setFinalPosition(value).setStiffness(SpringForce.STIFFNESS_MEDIUM).setDampingRatio(damping);
        spring.start();
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        final boolean pressed = isPressed();
        if (pressed == wasPressed) return;
        wasPressed = pressed;
        springTo(pressed ? pressedCorner : restCorner, pressed ? SpringForce.DAMPING_RATIO_NO_BOUNCY : SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY);
        animate().cancel();
        animate().scaleX(pressed ? pressScale : 1f).scaleY(pressed ? pressScale : 1f)
                .setDuration(pressed ? 90 : 260)
                .setInterpolator(pressed ? null : new android.view.animation.OvershootInterpolator(2.2f)).start();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (Color.alpha(paint.getColor()) == 0) return;
        rect.set(0, 0, getWidth(), getHeight());
        final float r = Math.max(0, Math.min(0.5f, corner)) * Math.min(getWidth(), getHeight());
        canvas.drawRoundRect(rect, r, r, paint);
    }
}
