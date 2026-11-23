package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.femboy.FemboyAvatars;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

/**
 * The avatar shapes, as in FemboyT: a row of them under a heading, the chosen one filled with
 * the accent and the rest quiet. A tap changes every avatar in the app at once.
 */
@SuppressLint("ViewConstructor")
public class FemboyShapePicker extends LinearLayout {

    private static final int[] NAMES = {R.string.FemboyShapeCircle, R.string.FemboyShapeRounded, R.string.FemboyShapeSquare,
            R.string.FemboyShapeCookie, R.string.FemboyShapeClover, R.string.FemboyShapeBurst, R.string.FemboyShapeSun,
            R.string.FemboyShapePentagon, R.string.FemboyShapeHexagon, R.string.FemboyShapeHeart};

    private static int scrolled;

    public FemboyShapePicker(Context context, Runnable picked) {
        super(context);
        setOrientation(VERTICAL);
        final TextView title = new TextView(context);
        title.setText(LocaleController.getString(R.string.FemboyAvatarShape));
        title.setTextSize(16);
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setPadding(dp(20), dp(14), dp(20), 0);
        addView(title);
        final HorizontalScrollView scroll = new HorizontalScrollView(context) {
            @Override
            protected void onScrollChanged(int l, int t, int oldl, int oldt) {
                super.onScrollChanged(l, t, oldl, oldt);
                scrolled = l;
            }
        };
        scroll.setHorizontalScrollBarEnabled(false);
        final LinearLayout row = new LinearLayout(context);
        row.setPadding(dp(12), dp(8), dp(12), dp(12));
        for (int i = 0; i < FemboyAvatars.ALL.length; i++) {
            final int shape = i;
            final View tile = new View(context) {
                final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                final Path path = new Path();
                final Matrix place = new Matrix();

                @Override
                protected void onDraw(Canvas canvas) {
                    boolean on = FemboyAvatars.chosen() == shape;
                    float side = Math.min(getWidth(), getHeight()) - dp(on ? 8 : 12);
                    path.rewind();
                    place.setScale(side / 2f, side / 2f);
                    place.postTranslate(getWidth() / 2f, getHeight() / 2f);
                    path.addPath(FemboyAvatars.unit(shape), place);
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(on ? Theme.getColor(Theme.key_featuredStickers_addButton)
                            : Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon) & 0x66FFFFFF);
                    canvas.drawPath(path, paint);
                }
            };
            tile.setContentDescription(LocaleController.getString(NAMES[i]));
            tile.setOnClickListener(v -> {
                if (FemboyAvatars.chosen() == shape) return;
                FemboyAvatars.choose(shape);
                row.invalidate();
                for (int k = 0; k < row.getChildCount(); k++) row.getChildAt(k).invalidate();
                if (picked != null) picked.run();
            });
            row.addView(tile, LayoutHelper.createLinear(56, 56, Gravity.CENTER_VERTICAL, 4, 0, 4, 0));
        }
        scroll.addView(row);
        addView(scroll);
        scroll.post(() -> scroll.scrollTo(scrolled, 0));
    }
}
