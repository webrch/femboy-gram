package org.telegram.femboy;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;

/**
 * Формы Material 3: карточки секций, волнистая загрузка, углы листов и
 * диалогов, кнопка действия. Всё смотрит на {@link FemboyYou#on()}: выключили
 * Material You, и форма телеграма возвращается как была.
 */
public class FemboyShapes {

    // ----------------------------------------------------------- секции

    /** Отступ карточки секции от края экрана. */
    public static int sectionPadding() {
        return FemboyYou.on() ? dp(16) : dp(12);
    }

    /** Внешние углы карточки секции: крупные, как в настройках андроида. */
    public static float sectionRadius() {
        return FemboyYou.on() ? dp(24) : dp(16);
    }

    /**
     * Заголовок секции (синий текст над группой строк). Под Material You он
     * стоит над карточкой, а не внутри неё. Если строке кто-то дал свой фон,
     * она остаётся в карточке: иначе вышел бы белый прямоугольник на сером.
     */
    public static boolean isSectionTitle(View view) {
        if (!FemboyYou.on() || !(view instanceof HeaderCell)
                || ((HeaderCell) view).titleColorKey != Theme.key_windowBackgroundWhiteBlueHeader) {
            return false;
        }
        android.graphics.drawable.Drawable bg = view.getBackground();
        // универсальный список красит заголовку фон цветом карточки: это не «свой фон»
        return bg == null || bg instanceof android.graphics.drawable.ColorDrawable
                && ((android.graphics.drawable.ColorDrawable) bg).getColor() == Theme.getColor(Theme.key_windowBackgroundWhite);
    }

    private static final Paint dividerPaint = new Paint();
    private static final java.util.ArrayList<View> dividerRows = new java.util.ArrayList<>();

    /**
     * Разделители MD3 внутри карточки: тонкая линия между соседними строками
     * одной секции, от начала текста до края. Рисуются списком, поверх
     * карточки и под строками, так что строкам самим ничего знать не надо.
     */
    public static void drawSectionDividers(androidx.recyclerview.widget.RecyclerView list, Canvas canvas, org.telegram.messenger.Utilities.CallbackReturn<View, Boolean> isSection) {
        dividerRows.clear();
        for (int i = 0; i < list.getChildCount(); i++) {
            View child = list.getChildAt(i);
            if (child.getVisibility() == View.VISIBLE && child.getAlpha() > 0 && list.getChildAdapterPosition(child) != androidx.recyclerview.widget.RecyclerView.NO_POSITION) {
                dividerRows.add(child);
            }
        }
        java.util.Collections.sort(dividerRows, (a, b) -> Integer.compare(list.getChildAdapterPosition(a), list.getChildAdapterPosition(b)));
        FemboyYou.ensureRoles();
        dividerPaint.setColor(FemboyYou.outlineVariant);
        final float thick = Math.max(1f, dpf2(0.7f));
        for (int i = 0; i + 1 < dividerRows.size(); i++) {
            View a = dividerRows.get(i), b = dividerRows.get(i + 1);
            if (list.getChildAdapterPosition(b) != list.getChildAdapterPosition(a) + 1) continue;
            if (!isSection.run(a) || !isSection.run(b)) continue;
            if (a instanceof HeaderCell || b instanceof HeaderCell) continue;
            final float bottom = a.getY() + a.getHeight();
            if (Math.abs(b.getY() - bottom) > dp(2)) continue;
            // на всю ширину карточки: строки секции и есть её ширина
            final float start = a.getX();
            final float end = a.getX() + a.getWidth();
            if (end - start < dp(24)) continue;
            dividerPaint.setAlpha((int) (Color.alpha(FemboyYou.outlineVariant) * Math.min(a.getAlpha(), b.getAlpha())));
            canvas.drawRect(start, bottom - thick / 2f, end, bottom + thick / 2f, dividerPaint);
        }
        dividerRows.clear();
    }

    /** Где в строке начинается текст: с него и начинается разделитель. */
    private static float textStart(View row) {
        float found = findText(row, 0, 0);
        return found < 0 ? dp(16) : found;
    }

    private static float findText(View v, float offset, int depth) {
        if (depth > 0 && v.getVisibility() != View.VISIBLE) return -1;
        if (depth > 0 && (v instanceof android.widget.TextView || v instanceof org.telegram.ui.ActionBar.SimpleTextView
                || v instanceof org.telegram.ui.Components.AnimatedTextView)) {
            return offset + v.getLeft() + v.getTranslationX() + v.getPaddingLeft();
        }
        if (v instanceof android.view.ViewGroup && depth < 3) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            float best = -1;
            float base = depth == 0 ? 0 : offset + v.getLeft() + v.getTranslationX();
            for (int i = 0; i < g.getChildCount(); i++) {
                float x = findText(g.getChildAt(i), base, depth + 1);
                if (x >= 0 && (best < 0 || x < best)) best = x;
            }
            return best;
        }
        return -1;
    }

    // ----------------------------------------------------- волнистая дуга

    private static final Path wave = new Path();

    /**
     * Замена canvas.drawArc для колец загрузки. Под Material You дуга идёт
     * волной («червячок» MD3 Expressive), волна медленно бежит по кругу.
     */
    public static void arc(Canvas canvas, RectF oval, float startAngle, float sweepAngle, boolean useCenter, Paint paint) {
        if (!FemboyYou.on() || useCenter || paint.getStyle() == Paint.Style.FILL || Math.abs(sweepAngle) < 2f) {
            canvas.drawArc(oval, startAngle, sweepAngle, useCenter, paint);
            return;
        }
        final float r = Math.min(oval.width(), oval.height()) / 2f;
        if (r < dp(4)) {
            canvas.drawArc(oval, startAngle, sweepAngle, useCenter, paint);
            return;
        }
        final float cx = oval.centerX(), cy = oval.centerY();
        // высота волны растёт с кольцом, но не больше полутора dp и не меньше
        // трети толщины линии, иначе на тонких кольцах её не видно
        float amp = Math.min(dpf2(1.6f), r * 0.075f);
        amp = Math.max(amp, Math.min(paint.getStrokeWidth() * 0.35f, dpf2(1.6f)));
        final int waves = Math.max(5, Math.round((float) (2 * Math.PI * r / dp(11))));
        final double phase = (SystemClock.uptimeMillis() % 3000L) / 3000.0 * 2 * Math.PI;
        final float sweep = Math.min(Math.abs(sweepAngle), 360f) * Math.signum(sweepAngle);
        final int steps = Math.max(12, (int) Math.ceil(Math.abs(sweep) / Math.max(1.2f, 360f / (waves * 10f))));
        final float base = r - amp * 0.5f;
        wave.rewind();
        for (int i = 0; i <= steps; i++) {
            final double a = Math.toRadians(startAngle + sweep * i / steps);
            double rr = base + amp * Math.sin(waves * a + phase);
            final float x = (float) (cx + rr * Math.cos(a));
            final float y = (float) (cy + rr * Math.sin(a));
            if (i == 0) {
                wave.moveTo(x, y);
            } else {
                wave.lineTo(x, y);
            }
        }
        final Paint.Join join = paint.getStrokeJoin();
        paint.setStrokeJoin(Paint.Join.ROUND);
        canvas.drawPath(wave, paint);
        paint.setStrokeJoin(join);
    }

    // ------------------------------------------------- листы и диалоги

    public static float dialogRadius(float telegram) {
        return FemboyYou.on() ? dp(28) : telegram;
    }

    /**
     * Фон нижнего листа: верхние углы 28dp. Отступы как у картинки телеграма,
     * чтобы раскладка листа не сдвинулась. Цвет задаётся через
     * setColorFilter(цвет, MULTIPLY), как телеграм и делает с картинкой.
     */
    public static Drawable sheetBackground(Drawable telegram) {
        if (!FemboyYou.on() || telegram == null) {
            return telegram;
        }
        Rect pad = new Rect();
        telegram.getPadding(pad);
        return new RoundDrawable(pad, dp(28), 0);
    }

    public static class RoundDrawable extends Drawable {
        private final Rect padding;
        private final float top, bottom;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF rect = new RectF();
        private final float[] radii = new float[8];

        public RoundDrawable(Rect padding, float top, float bottom) {
            this.padding = new Rect(padding);
            this.top = top;
            this.bottom = bottom;
            paint.setColor(0xffffffff);
            if (Build.VERSION.SDK_INT >= 28) {
                paint.setShadowLayer(dpf2(3), 0, -dpf2(0.5f), 0x22000000);
            }
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect b = getBounds();
            rect.set(b.left + padding.left, b.top + padding.top, b.right - padding.right, b.bottom - (bottom > 0 ? padding.bottom : 0));
            radii[0] = radii[1] = radii[2] = radii[3] = top;
            radii[4] = radii[5] = radii[6] = radii[7] = bottom;
            path.rewind();
            path.addRoundRect(rect, radii, Path.Direction.CW);
            canvas.drawPath(path, paint);
        }

        @Override
        public boolean getPadding(@NonNull Rect out) {
            out.set(padding);
            return true;
        }

        @Override
        public void getOutline(@NonNull Outline outline) {
            Rect b = getBounds();
            outline.setRoundRect(b.left + padding.left, b.top + padding.top, b.right - padding.right, b.bottom, top);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
            invalidateSelf();
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    // --------------------------------------------------- кнопка действия

    /** Кнопка действия MD3: скруглённый квадрат, не круг. */
    public static boolean fabSquircle() {
        return FemboyYou.on();
    }

    public static float fabRadius(int size) {
        // 16dp у кнопки 56dp, пропорционально у других
        return size * 16f / 56f;
    }

    public static final ViewOutlineProvider FAB_OUTLINE = new ViewOutlineProvider() {
        @Override
        public void getOutline(View view, Outline outline) {
            int w = view.getWidth(), h = view.getHeight();
            outline.setRoundRect(0, 0, w, h, fabRadius(Math.min(w, h)));
        }
    };
}
