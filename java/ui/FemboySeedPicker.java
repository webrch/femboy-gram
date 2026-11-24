package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.femboy.FemboyYou;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

/**
 * Выбор цветов Material You, как в настройках обоев андроида: первый кружок —
 * цвета обоев телефона, дальше готовые цвета. Каждый кружок показывает три
 * тона своей палитры.
 */
@SuppressLint("ViewConstructor")
public class FemboySeedPicker extends HorizontalScrollView {

    /** 0 — цвета обоев, остальное — сид палитры. */
    /**
     * Первый кружок — палитра обоев телефона, дальше нежные фембой-цвета:
     * розовый, лавандовый, голубой и их мягкие оттенки. Насыщенность самой
     * палитры берётся из MD3, поэтому сид задаёт только оттенок.
     */
    private static final int[] SEEDS = {
            0,
            // Originally requested pastel presets.
            0xFFFF9ECD, // сакура
            0xFFC9A6FF, // лаванда
            0xFF9FD0FF, // небесный
            0xFFF7B6D2, // бледная роза
            0xFFB9A6E8, // ирис
            0xFFA8D8EA, // лёд
            0xFFF3C6DE, // персиковая роза
            // Ten new presets from the owner (24.11.2026).
            0xFFFFB59D, // Peach — нежный персиковый
            0xFFEED9B7, // Vanilla — молочно-бежевый
            0xFFFFAEC9, // Strawberry Milk — тёплый розовый
            0xFFB7D5AC, // Matcha — приглушённый пастельно-зелёный
            0xFFA8E6CF, // Mint — мятно-бирюзовый
            0xFFC8A2C8, // Wisteria — пыльный сиренево-розовый
            0xFFC4A484, // Mocha — кофейно-пудровый
            0xFFCFE3C6, // Pistachio — светлый Matcha
            0xFFFFD1BA, // Apricot — мягкий Peach
            0xFFC7F0E0, // Seafoam — светло-мятный
    };

    /** Подписи кружков для доступности и подсказки при долгом нажатии. */
    private static final int[] NAMES = {
            R.string.FemboyThemeFromWallpaper,
            R.string.FemboyThemeSakura,
            R.string.FemboyThemeLavender,
            R.string.FemboyThemeSky,
            R.string.FemboyThemePaleRose,
            R.string.FemboyThemeIris,
            R.string.FemboyThemeIce,
            R.string.FemboyThemePeachRose,
            R.string.FemboyThemePeach,
            R.string.FemboyThemeVanilla,
            R.string.FemboyThemeStrawberryMilk,
            R.string.FemboyThemeMatcha,
            R.string.FemboyThemeMint,
            R.string.FemboyThemeWisteria,
            R.string.FemboyThemeMocha,
            R.string.FemboyThemePistachio,
            R.string.FemboyThemeApricot,
            R.string.FemboyThemeSeafoam,
    };

    public FemboySeedPicker(Context context, Runnable picked) {
        super(context);
        setHorizontalScrollBarEnabled(false);
        final LinearLayout row = new LinearLayout(context);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        for (int i = 0; i < SEEDS.length; i++) {
            final int seed = SEEDS[i];
            final Swatch swatch = new Swatch(context, seed,
                    LocaleController.getString(NAMES[i]));
            swatch.setOnClickListener(v -> {
                if (FemboyYou.seed() != seed) {
                    FemboyYou.setSeed(seed);
                    if (picked != null) picked.run();
                }
            });
            row.addView(swatch, LayoutHelper.createLinear(56, 56, 4, 0, 4, 0));
        }
        addView(row);
        // после выбора экран пересобирается: ставим карусель туда же, где была
        post(() -> scrollTo(savedScroll, 0));
    }

    /** Где стояла карусель: переживает пересборку экрана после смены цвета. */
    private static int savedScroll;

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        savedScroll = l;
    }

    private static class Swatch extends View {
        private final int seed;
        private final int[] colors;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        Swatch(Context context, int seed, String name) {
            super(context);
            this.seed = seed;
            this.colors = FemboyYou.swatch(seed);
            setContentDescription(name);
            if (Build.VERSION.SDK_INT >= 26) {
                setTooltipText(name);
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final float cx = getWidth() / 2f, cy = getHeight() / 2f;
            final boolean selected = FemboyYou.seed() == seed;
            final float r = dp(selected ? 20 : 24);
            rect.set(cx - r, cy - r, cx + r, cy + r);
            // верх основным, низ пополам вторичным и третичным
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(colors[0]);
            canvas.drawArc(rect, 180, 180, true, paint);
            paint.setColor(colors[1]);
            canvas.drawArc(rect, 90, 90, true, paint);
            paint.setColor(colors[2]);
            canvas.drawArc(rect, 0, 90, true, paint);
            if (selected) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(2));
                paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                canvas.drawCircle(cx, cy, dp(25), paint);
            }
            if (seed == 0) {
                // кружок «из обоев»: маленькая точка в середине
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                canvas.drawCircle(cx, cy, dp(5), paint);
            }
        }
    }
}
