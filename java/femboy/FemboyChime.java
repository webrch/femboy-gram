package org.telegram.femboy;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.CubicBezierInterpolator;

/**
 * Нажатие на название на главном экране. Когда-то мяукало, теперь патпат:
 * рука гладит надпись (см. {@link org.telegram.ui.FemboyPatOverlay}).
 * Класс оставлен под старым именем, на него завязаны настройки звука.
 *
 * Событие не съедается: нажатие по названию по-прежнему прокручивает
 * список к началу. Нажатием считаем короткое касание без ухода пальца в
 * сторону, иначе патпат срабатывал бы на каждой прокрутке.
 */
public class FemboyChime {

    public static void attach(View view) {
        if (view == null) {
            return;
        }
        view.setOnTouchListener(new View.OnTouchListener() {
            private float startX, startY;
            private long startAt;
            private boolean tracking;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                final int slop = ViewConfiguration.get(v.getContext()).getScaledTouchSlop();
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = event.getX();
                        startY = event.getY();
                        startAt = System.currentTimeMillis();
                        tracking = true;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (Math.abs(event.getX() - startX) > slop
                                || Math.abs(event.getY() - startY) > slop) {
                            tracking = false;
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                        if (tracking && System.currentTimeMillis() - startAt
                                <= ViewConfiguration.getLongPressTimeout()) {
                            pat(v);
                        }
                        tracking = false;
                        break;
                    default:
                        tracking = false;
                        break;
                }
                return false;
            }
        });
    }

    /** Прогиб: надпись приседает и отпружинивает обратно. */
    public static void squish(View view) {
        if (view == null) {
            return;
        }
        view.animate().cancel();
        view.setPivotY(view.getHeight());
        view.animate()
                .scaleY(0.72f).scaleX(1.09f)
                .setDuration(90)
                .setInterpolator(CubicBezierInterpolator.EASE_OUT)
                .withEndAction(() -> view.animate()
                        .scaleY(1f).scaleX(1f)
                        .setDuration(340)
                        .setInterpolator(CubicBezierInterpolator.EASE_OUT_BACK)
                        .start())
                .start();
    }

    /** Патпат по надписи: рука, приседание и шлепки. */
    public static void pat(View title) {
        org.telegram.ui.FemboyPatOverlay.pat(title);
    }

    /** Один шлепок без руки: выбранный звук, если он включён. */
    public static void play(Context context) {
        org.telegram.ui.FemboyPatSound.play();
    }

    /** Послушать в настройках: пат-пат, два шлепка подряд. */
    public static void preview() {
        org.telegram.ui.FemboyPatSound.play();
        AndroidUtilities.runOnUIThread(org.telegram.ui.FemboyPatSound::play, 300);
    }

    /** Свой файл сменили: звук надо перечитать. */
    public static void soundChanged() {
        org.telegram.ui.FemboyPatSound.reset();
    }
}
