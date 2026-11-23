package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.femboy.FemboyYou;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

/**
 * Выбор градиента профиля: превью сверху, два слота (видно, какой цвет
 * сейчас правится), квадрат насыщенности и яркости, полоса оттенка и готовые
 * пары. Полей ввода нет, клавиатура не выскакивает.
 */
@SuppressLint("ViewConstructor")
public class FemboyGradientEditor extends LinearLayout {

    public interface Changed {
        void changed(int first, int second);
    }

    private final int[] colors;
    private int slot;
    private final float[] hsv = new float[3];
    private final Changed changed;

    private final View preview;
    private final TextView[] slots = new TextView[2];
    private final SvView sv;
    private final HueView hue;

    public FemboyGradientEditor(Context context, int first, int second, Changed changed) {
        super(context);
        this.colors = new int[]{first | 0xFF000000, second | 0xFF000000};
        this.changed = changed;
        setOrientation(VERTICAL);
        setPadding(dp(20), dp(8), dp(20), dp(4));
        FemboyYou.ensureRoles();

        preview = new View(context) {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final RectF rect = new RectF();

            @Override
            protected void onDraw(Canvas canvas) {
                rect.set(0, 0, getWidth(), getHeight());
                paint.setShader(new LinearGradient(0, 0, getWidth(), 0, colors[0], colors[1], Shader.TileMode.CLAMP));
                canvas.drawRoundRect(rect, dp(16), dp(16), paint);
            }
        };
        addView(preview, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 64));

        final LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        for (int i = 0; i < 2; i++) {
            final int index = i;
            final TextView chip = new TextView(context);
            chip.setText(LocaleController.getString(i == 0 ? R.string.FemboyGradientFirst : R.string.FemboyGradientSecond));
            chip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            chip.setTypeface(AndroidUtilities.bold());
            chip.setGravity(Gravity.CENTER_VERTICAL);
            chip.setSingleLine();
            chip.setEllipsize(TextUtils.TruncateAt.END);
            chip.setCompoundDrawablePadding(dp(10));
            chip.setPadding(dp(12), 0, dp(12), 0);
            chip.setOnClickListener(v -> select(index));
            slots[i] = chip;
            row.addView(chip, LayoutHelper.createLinear(0, 48, 1f, i == 0 ? 0 : 6, 0, i == 0 ? 6 : 0, 0));
        }
        addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 12, 0, 0));

        sv = new SvView(context);
        addView(sv, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 170, 0, 14, 0, 0));
        hue = new HueView(context);
        addView(hue, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 32, 0, 12, 0, 0));

        // готовые пары: из палитры телефона и несколько ярких
        final int[][] presets = {
                {FemboyYou.primaryContainer, FemboyYou.tertiaryContainer},
                {FemboyYou.primaryColor, FemboyYou.tertiaryColor},
                {0xFF8DD1B0, 0xFFB7A8E0}, {0xFFFF8A65, 0xFFFFD54F}, {0xFF64B5F6, 0xFFBA68C8},
                {0xFF4DB6AC, 0xFF81C784}, {0xFFF06292, 0xFF9575CD}, {0xFF37474F, 0xFF90A4AE}};
        final HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        final LinearLayout dots = new LinearLayout(context);
        for (int[] pair : presets) {
            final View dot = new View(context);
            final GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{pair[0] | 0xFF000000, pair[1] | 0xFF000000});
            g.setShape(GradientDrawable.OVAL);
            dot.setBackground(g);
            dot.setOnClickListener(v -> {
                colors[0] = pair[0] | 0xFF000000;
                colors[1] = pair[1] | 0xFF000000;
                select(slot);
                notifyChanged();
            });
            dots.addView(dot, LayoutHelper.createLinear(40, 40, 0, 0, 10, 0));
        }
        scroll.addView(dots);
        addView(scroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 14, 0, 0));

        select(0);
    }

    public int first() {
        return colors[0];
    }

    public int second() {
        return colors[1];
    }

    private void select(int index) {
        slot = index;
        Color.colorToHSV(colors[slot], hsv);
        for (int i = 0; i < 2; i++) {
            final boolean on = i == slot;
            final GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(24));
            bg.setColor(on ? FemboyYou.secondaryContainer : 0);
            bg.setStroke(dp(on ? 0 : 1), FemboyYou.outlineVariant);
            slots[i].setBackground(bg);
            slots[i].setTextColor(on ? FemboyYou.onSecondaryContainer : Theme.getColor(Theme.key_dialogTextBlack));
            final GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(colors[i]);
            dot.setStroke(dp(1), FemboyYou.outline);
            dot.setSize(dp(22), dp(22));
            slots[i].setCompoundDrawablesWithIntrinsicBounds(dot, null, null, null);
        }
        sv.invalidate();
        hue.invalidate();
        preview.invalidate();
    }

    private void fromHsv() {
        colors[slot] = Color.HSVToColor(hsv);
        final GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(colors[slot]);
        dot.setStroke(dp(1), FemboyYou.outline);
        dot.setSize(dp(22), dp(22));
        slots[slot].setCompoundDrawablesWithIntrinsicBounds(dot, null, null, null);
        sv.invalidate();
        hue.invalidate();
        preview.invalidate();
        notifyChanged();
    }

    private void notifyChanged() {
        if (changed != null) {
            changed.changed(colors[0], colors[1]);
        }
    }

    private static void thumb(Canvas canvas, Paint paint, float x, float y, int color) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x33000000);
        canvas.drawCircle(x, y + dp(1), dp(12), paint);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(x, y, dp(12), paint);
        paint.setColor(color);
        canvas.drawCircle(x, y, dp(9), paint);
    }

    /** Квадрат: вправо насыщеннее, вниз темнее. */
    private class SvView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        SvView(Context context) {
            super(context);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            rect.set(0, 0, getWidth(), getHeight());
            final int pure = Color.HSVToColor(new float[]{hsv[0], 1f, 1f});
            final Shader sat = new LinearGradient(0, 0, getWidth(), 0, Color.WHITE, pure, Shader.TileMode.CLAMP);
            final Shader val = new LinearGradient(0, 0, 0, getHeight(), Color.WHITE, Color.BLACK, Shader.TileMode.CLAMP);
            paint.setShader(new ComposeShader(sat, val, PorterDuff.Mode.MULTIPLY));
            canvas.drawRoundRect(rect, dp(16), dp(16), paint);
            thumb(canvas, paint, hsv[1] * getWidth(), (1f - hsv[2]) * getHeight(), colors[slot]);
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(MotionEvent e) {
            getParent().requestDisallowInterceptTouchEvent(true);
            hsv[1] = Math.max(0f, Math.min(1f, e.getX() / getWidth()));
            hsv[2] = Math.max(0f, Math.min(1f, 1f - e.getY() / getHeight()));
            fromHsv();
            return true;
        }
    }

    /** Полоса оттенка. */
    private class HueView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final int[] rainbow = {0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFFFF0000};

        HueView(Context context) {
            super(context);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final float pad = dp(12);
            rect.set(pad, getHeight() / 2f - dp(8), getWidth() - pad, getHeight() / 2f + dp(8));
            paint.setShader(new LinearGradient(rect.left, 0, rect.right, 0, rainbow, null, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(rect, dp(8), dp(8), paint);
            thumb(canvas, paint, rect.left + hsv[0] / 360f * rect.width(), getHeight() / 2f,
                    Color.HSVToColor(new float[]{hsv[0], 1f, 1f}));
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(MotionEvent e) {
            getParent().requestDisallowInterceptTouchEvent(true);
            final float pad = dp(12);
            hsv[0] = Math.max(0f, Math.min(359.9f, (e.getX() - pad) / (getWidth() - pad * 2) * 360f));
            fromHsv();
            return true;
        }
    }
}
