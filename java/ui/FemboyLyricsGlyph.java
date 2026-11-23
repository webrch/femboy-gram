package org.telegram.ui;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

/**
 * Иконка из одного символа, например «ツ» у чтения латиницей. Рисуется белым,
 * цвет даёт фильтр кнопки, как у векторных иконок. Размер подбирается по
 * настоящим границам глифа, чтобы он стоял ровно по центру.
 */
public class FemboyLyricsGlyph extends Drawable {

    private final String glyph;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Rect ink = new Rect();
    private final float fill;
    private int measuredFor = -1;

    /** fill: какую долю стороны занимает глиф (у иконок MD3 живая область около 0.8). */
    public FemboyLyricsGlyph(String glyph, float fill) {
        this.glyph = glyph;
        this.fill = fill;
        paint.setColor(Color.WHITE);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void measure(int side) {
        if (measuredFor == side) return;
        measuredFor = side;
        paint.setTextSize(100);
        paint.getTextBounds(glyph, 0, glyph.length(), ink);
        final float big = Math.max(1, Math.max(ink.width(), ink.height()));
        paint.setTextSize(100f * side * fill / big);
        paint.getTextBounds(glyph, 0, glyph.length(), ink);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        final Rect b = getBounds();
        final int side = Math.min(b.width(), b.height());
        if (side <= 0) return;
        measure(side);
        // ставим по центру саму краску глифа, а не строку с её отступами
        final float x = b.exactCenterX() - ink.left - ink.width() / 2f;
        final float y = b.exactCenterY() - ink.top - ink.height() / 2f;
        canvas.drawText(glyph, x, y, paint);
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
