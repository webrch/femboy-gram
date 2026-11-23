package org.telegram.femboy;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Movie;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import java.io.InputStream;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;

/**
 * The animated FemboyGram mark in the fork's settings header.
 *
 * The header plays icon_360.gif from assets. If the GIF cannot be decoded, the
 * static artwork is drawn as a fallback. Drawn on a plain canvas, no GL surface:
 * this way it never fights the list rows for z-order while they scroll.
 */
public class FemboyIconView extends View {

    private final Movie movie;
    private final Bitmap bitmap;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private long started;

    public FemboyIconView(Context context) {
        super(context);
        Movie decoded = null;
        try (InputStream in = context.getAssets().open("femboy_header.gif")) {
            decoded = Movie.decodeStream(in);
        } catch (Exception e) {
            FileLog.e(e);
        }
        movie = decoded;
        bitmap = movie == null ? BitmapFactory.decodeResource(context.getResources(), R.drawable.femboy_icon_full) : null;
        started = SystemClock.uptimeMillis();
        setClickable(true);
    }

    public FemboyIconView(Context context, int unusedColor) {
        this(context);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0 || (movie == null && bitmap == null)) {
            return;
        }
        final float size = Math.min(w, h) * 0.92f;

        canvas.save();
        canvas.translate(w / 2f, h / 2f);
        if (movie != null) {
            final int duration = movie.duration();
            final int now = duration > 0
                    ? (int) ((SystemClock.uptimeMillis() - started) % duration)
                    : 0;
            movie.setTime(now);
            final float sx = size / movie.width();
            final float sy = size / movie.height();
            canvas.scale(sx, sy);
            movie.draw(canvas, -movie.width() / 2f, -movie.height() / 2f);
            invalidate();
        } else {
            final float scale = size / bitmap.getWidth();
            canvas.scale(scale, scale);
            paint.setAlpha(235);
            canvas.drawBitmap(bitmap, -bitmap.getWidth() / 2f, -bitmap.getHeight() / 2f, paint);
        }
        canvas.restore();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return super.onTouchEvent(event);
    }

    /** Kept for callers that sized the old view in dp. */
    public static int preferredSize() {
        return AndroidUtilities.dp(150);
    }
}
