package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.view.View;

import androidx.core.graphics.ColorUtils;

import org.telegram.femboy.FemboyConfig;
import org.telegram.femboy.FemboyYou;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

/**
 * Картинки над настройками: как будет выглядеть то, что включаешь. Каждая
 * рисует себя из текущих настроек и цветов, так что после переключения
 * достаточно перерисовать её.
 */
public class FemboyPreviews {

    /** Общее: высота, отступы карточки, кисти, «экран» со скруглением. */
    abstract static class Base extends View {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        final RectF rect = new RectF();
        final Path path = new Path();
        private final int heightDp;

        Base(Context context, int heightDp) {
            super(context);
            this.heightDp = heightDp;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(heightDp));
        }

        int color(int key) {
            return Theme.getColor(key);
        }

        /** Фон «экрана»: обои чата, как они есть, или цвет. */
        void screen(Canvas canvas, RectF r, float radius) {
            canvas.save();
            path.rewind();
            path.addRoundRect(r, radius, radius, Path.Direction.CW);
            canvas.clipPath(path);
            Drawable wallpaper = Theme.getCachedWallpaperNonBlocking();
            if (wallpaper != null) {
                wallpaper.setBounds((int) r.left, (int) r.top, (int) r.right, (int) (r.top + Math.max(r.height(), r.width() * 1.6f)));
                wallpaper.draw(canvas);
            } else {
                canvas.drawColor(color(Theme.key_windowBackgroundGray));
            }
            canvas.restore();
        }

        /** Пузырь сообщения с «текстом» из полосок. */
        void bubble(Canvas canvas, float left, float top, float width, float height, boolean out, int lines) {
            rect.set(left, top, left + width, top + height);
            paint.setColor(color(out ? Theme.key_chat_outBubble : Theme.key_chat_inBubble));
            final float r = FemboyYou.on() ? dp(16) : dp(12);
            canvas.drawRoundRect(rect, r, r, paint);
            paint.setColor(ColorUtils.setAlphaComponent(color(out ? Theme.key_chat_messageTextOut : Theme.key_chat_messageTextIn), 0x70));
            float y = top + dp(11);
            for (int i = 0; i < lines; i++) {
                float w = (width - dp(24)) * (i == lines - 1 ? 0.55f : 1f);
                rect.set(left + dp(12), y, left + dp(12) + w, y + dp(6));
                canvas.drawRoundRect(rect, dp(3), dp(3), paint);
                y += dp(11);
            }
        }
    }

    // ------------------------------------------------------------ стример

    /** Карточка профиля: что видно зрителям трансляции. */
    public static class Streamer extends Base {
        public Streamer(Context context) {
            super(context, 112);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final float pad = dp(16);
            rect.set(pad, dp(12), getWidth() - pad, getHeight() - dp(12));
            paint.setColor(color(Theme.key_windowBackgroundGray));
            canvas.drawRoundRect(rect, dp(18), dp(18), paint);

            final float cx = rect.left + dp(40), cy = rect.centerY();
            paint.setColor(color(Theme.key_avatar_backgroundBlue));
            canvas.drawCircle(cx, cy, dp(24), paint);
            text.setColor(0xffffffff);
            text.setTextSize(dp(18));
            text.setTypeface(AndroidUtilities.bold());
            canvas.drawText("N", cx - text.measureText("N") / 2f, cy + dp(6), text);

            final boolean on = FemboyConfig.streamerMode();
            final float tx = cx + dp(38);
            text.setColor(color(Theme.key_windowBackgroundWhiteBlackText));
            text.setTextSize(dp(16));
            canvas.drawText(LocaleController.getString(R.string.FemboyPreviewName), tx, cy - dp(10), text);
            text.setTypeface(null);
            text.setTextSize(dp(14));
            text.setColor(color(Theme.key_windowBackgroundWhiteGrayText2));
            canvas.drawText(on ? "+7 ••• •••-••-••" : "+7 912 345-67-89", tx, cy + dp(8), text);
            text.setColor(color(Theme.key_windowBackgroundWhiteBlueText));
            canvas.drawText(on && FemboyConfig.streamerHidesUsername() ? "@•••••••" : "@femboygram", tx, cy + dp(26), text);
        }
    }
}
