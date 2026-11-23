package org.telegram.femboy;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.Theme;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Градиент профиля — два своих цвета вместо телеграмовских.
 *
 * Живёт там же, где баннер: в общей группе, сообщением с меткой. Чей градиент
 * — видно по автору сообщения, и подделать это нельзя.
 *
 * Но, в отличие от баннера, это не картинка, а два числа. Поэтому и сообщение
 * не фотография, а строка: {@code #femboy_gradient 8DD1B0-B7A8E0}. Так оно
 * весит десяток байт, приходит поиском мгновенно и читается человеком, который
 * забрёл в группу и не знает, что это.
 */
public class FemboyGradient {

    /** Метка градиента. Одна на всех: чей — видно по автору. */
    public static final String TAG = "#femboy_gradient";

    /**
     * Что мы согласны считать градиентом.
     *
     * Два цвета, а не четыре и не один. Один — это не градиент, а заливка;
     * больше двух телеграмовский профиль всё равно не нарисует: у него под
     * шапкой радиальный градиент ровно на две точки.
     */
    private static final Pattern PAIR = Pattern.compile(
            "#femboy_gradient\\s+([0-9A-Fa-f]{6})-([0-9A-Fa-f]{6})\\b");

    /** Найденное держим в памяти: профиль перерисовывается по многу раз в секунду. */
    private static final HashMap<Long, int[]> colors = new HashMap<>();
    private static final HashMap<Long, Integer> ownMessage = new HashMap<>();
    private static final Set<Long> looking = new HashSet<>();
    private static final Set<Long> missing = new HashSet<>();

    private static AccountInstance account() {
        return AccountInstance.getInstance(UserConfig.selectedAccount);
    }

    private static long me() {
        try {
            return UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
        } catch (Throwable t) {
            return 0;
        }
    }

    /** Собрать строку сообщения из двух цветов. */
    public static String describe(int color1, int color2) {
        return TAG + " " + hex(color1) + "-" + hex(color2);
    }

    private static String hex(int color) {
        return String.format("%06X", color & 0xFFFFFF);
    }

    /**
     * Разобрать сообщение обратно в цвета. Возвращает null, если это не оно.
     *
     * Прозрачность дописываем сами, а не читаем из сообщения: полупрозрачная
     * шапка профиля — это не оформление, а поломка показа, и давать её ставить
     * незачем.
     */
    public static int[] parse(String text) {
        if (text == null) {
            return null;
        }
        final Matcher at = PAIR.matcher(text);
        if (!at.find()) {
            return null;
        }
        try {
            return new int[]{
                    0xFF000000 | Integer.parseInt(at.group(1), 16),
                    0xFF000000 | Integer.parseInt(at.group(2), 16)};
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Градиент этого человека, если он уже у нас есть. Свой показываем
     * всегда (он нужен и выбиральщику), чужие только при включённом показе.
     */
    public static int[] of(long userId, Runnable whenReady) {
        if (userId <= 0 || (userId != me() && !FemboyConfig.gradientsEnabled())) {
            return null;
        }
        synchronized (colors) {
            final int[] ready = colors.get(userId);
            if (ready != null) {
                return ready;
            }
            if (missing.contains(userId) || looking.contains(userId)) {
                return null;
            }
            looking.add(userId);
        }
        FemboyHelperBot.info(userId, info -> {
            synchronized (colors) {
                looking.remove(userId);
                if (info != null && info.gradient != null) {
                    colors.put(userId, info.gradient);
                    missing.remove(userId);
                } else if (info != null) {
                    missing.add(userId);
                }
            }
            if (info != null && info.gradient != null && whenReady != null) {
                whenReady.run();
            }
        });
        return null;
    }

    /** Забыть найденное: градиент поменяли, старый показывать нельзя. */
    public static void forget(long userId) {
        synchronized (colors) {
            colors.remove(userId);
            missing.remove(userId);
            looking.remove(userId);
        }
    }

    /** Поставить себе градиент: команда боту Femboy Helper. Свой показываем сразу. */
    public static void set(int color1, int color2, Runnable done) {
        final long id = me();
        if (id <= 0) {
            if (done != null) {
                done.run();
            }
            return;
        }
        final String command = String.format("/gram_gradient %06X %06X", color1 & 0xFFFFFF, color2 & 0xFFFFFF);
        FemboyHelperBot.send(command, null, ok -> {
            if (ok) {
                final int[] pair = {0xFF000000 | (color1 & 0xFFFFFF), 0xFF000000 | (color2 & 0xFFFFFF)};
                synchronized (colors) {
                    colors.put(id, pair);
                    missing.remove(id);
                    looking.remove(id);
                }
                final FemboyHelperBot.Info was = FemboyHelperBot.known(id);
                FemboyHelperBot.remember(id, was != null ? was.banner : 0, pair);
            }
            if (done != null) {
                done.run();
            }
        });
    }

    /** Убрать свой градиент: команда боту. */
    public static void clear(FemboyGroup.Removed done) {
        final long id = me();
        if (id <= 0) {
            answer(done, FemboyGroup.FAILED);
            return;
        }
        final FemboyHelperBot.Info was = FemboyHelperBot.known(id);
        FemboyHelperBot.send("/gram_gradient_off", null, ok -> {
            if (!ok) {
                answer(done, FemboyGroup.FAILED);
                return;
            }
            forget(id);
            synchronized (colors) {
                missing.add(id);
            }
            FemboyHelperBot.remember(id, was != null ? was.banner : 0, null);
            answer(done, was != null && was.gradient == null ? FemboyGroup.NOTHING : FemboyGroup.REMOVED);
        });
    }

    private static void answer(FemboyGroup.Removed done, int what) {
        if (done != null) {
            AndroidUtilities.runOnUIThread(() -> done.onRemoved(what));
        }
    }

    // --- цвета из пары ---

    /** Смесь двух цветов: 0 — первый, 1 — второй, с прозрачностью. */
    public static int mix(int from, int to, float k) {
        return Color.argb(
                (int) (Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * k),
                (int) (Color.red(from) + (Color.red(to) - Color.red(from)) * k),
                (int) (Color.green(from) + (Color.green(to) - Color.green(from)) * k),
                (int) (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * k));
    }

    public static int alpha(int color, float a) {
        return Color.argb((int) (Math.max(0f, Math.min(1f, a)) * 255f), Color.red(color), Color.green(color), Color.blue(color));
    }

    /** Яркость, как её видит глаз (Rec. 709): зелёный весит больше всех. */
    public static double brightness(int color) {
        return (Color.red(color) * 0.2126 + Color.green(color) * 0.7152 + Color.blue(color) * 0.0722) / 255.0;
    }

    /** Тот же оттенок, доведённый до нужной яркости: к белому или к чёрному. */
    public static int toBrightness(int color, double target) {
        double now = brightness(color);
        if (target > now) {
            if (now < 1) {
                return mix(color, 0xffffffff, (float) ((target - now) / (1 - now)));
            }
        } else if (now > 0) {
            return mix(color, 0xff000000, (float) (1 - target / now));
        }
        return color;
    }

    /** Цвет букв поверх пары: белый на тёмном, чёрный на светлом. */
    public static int ink(int[] pair) {
        if (pair == null || pair.length < 2 || brightness(mix(pair[0], pair[1], 0.5f)) <= 0.62) {
            return 0xffffffff;
        }
        return 0xff000000;
    }

    /** Подложка карточек на профиле: середина пары, чуть темнее. */
    public static int card(int[] pair) {
        if (pair == null || pair.length < 2) {
            return 0xff000000;
        }
        int middle = mix(pair[0], pair[1], 0.5f);
        return toBrightness(middle, brightness(middle) * 0.86);
    }

    /** Кнопки под аватаркой: заметно отличаются от фона, но в его оттенке. */
    public static int buttons(int[] pair) {
        if (pair == null || pair.length < 2) {
            return 0x22000000;
        }
        int middle = mix(pair[0], pair[1], 0.5f);
        double now = brightness(middle);
        double target = ink(pair) == 0xff000000
                ? Math.max(now - 0.06, 0.801)
                : Math.min(now + 0.1, 0.581);
        return toBrightness(middle, target);
    }

    // --- рисование ---

    private static final Paint FILL = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static int builtFrom;
    private static int builtTo;
    private static float builtHeight;

    /**
     * Залить прямоугольник парой: первый цвет снизу, второй сверху. Шейдер
     * строим заново, только когда поменялись цвета или высота — рисуют это
     * на каждом кадре прокрутки.
     */
    public static void paint(Canvas canvas, int[] pair, float left, float top, float right, float bottom, int alpha) {
        if (canvas == null || pair == null || pair.length < 2 || right <= left || bottom <= top) {
            return;
        }
        float width = right - left;
        float height = bottom - top;
        if (FILL.getShader() == null || builtFrom != pair[0] || builtTo != pair[1] || builtHeight != height) {
            builtFrom = pair[0];
            builtTo = pair[1];
            builtHeight = height;
            FILL.setShader(new LinearGradient(0, height, 0, 0, pair[0], pair[1], Shader.TileMode.CLAMP));
        }
        FILL.setAlpha(alpha);
        canvas.save();
        canvas.translate(left, top);
        canvas.drawRect(0, 0, width, height, FILL);
        canvas.restore();
    }

    /** Фон, который сам заливается градиентом человека, как только тот придёт. */
    public static final class Backdrop extends Drawable {
        private final long userId;
        private final Paint plain = new Paint();

        public Backdrop(long userId, int color) {
            this.userId = userId;
            plain.setColor(color);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            canvas.drawRect(bounds, plain);
            int[] pair = FemboyGradient.of(userId, this::invalidateSelf);
            if (pair != null) {
                FemboyGradient.paint(canvas, pair, bounds.left, bounds.top, bounds.right, bounds.bottom, 255);
            }
        }

        @Override
        public void setAlpha(int alpha) {
            plain.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter filter) {
            plain.setColorFilter(filter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /**
     * Цвета экрана профиля в тон градиенту его хозяина.
     *
     * Обёртка над обычными цветами темы: на фон, текст, разделители и выделение
     * отвечает пара, на всё остальное — тема, как раньше. Пока градиента нет
     * (или он ещё грузится), экран выглядит обычным.
     */
    public static final class Palette implements Theme.ResourcesProvider {
        private final Theme.ResourcesProvider under;
        private final long userId;

        public Palette(Theme.ResourcesProvider under, long userId) {
            this.under = under;
            this.userId = userId;
        }

        private int base(int key) {
            return under != null ? under.getColor(key) : Theme.getColor(key);
        }

        @Override
        public int getColor(int key) {
            int[] pair = userId != 0 ? FemboyGradient.of(userId, null) : null;
            if (pair == null) {
                return base(key);
            }
            int ink = FemboyGradient.ink(pair);
            if (key == Theme.key_windowBackgroundWhite || key == Theme.key_dialogBackground) {
                return FemboyGradient.card(pair);
            }
            if (key == Theme.key_windowBackgroundGray || key == Theme.key_dialogBackgroundGray) {
                return FemboyGradient.mix(pair[0], pair[1], 0.5f);
            }
            if (key == Theme.key_windowBackgroundWhiteBlackText || key == Theme.key_dialogTextBlack) {
                return ink;
            }
            if (key == Theme.key_windowBackgroundWhiteValueText || key == Theme.key_windowBackgroundWhiteBlueText
                    || key == Theme.key_windowBackgroundWhiteLinkText || key == Theme.key_dialogTextLink) {
                return FemboyGradient.alpha(ink, 0.92f);
            }
            if (key == Theme.key_windowBackgroundWhiteGrayText || key == Theme.key_windowBackgroundWhiteGrayText2
                    || key == Theme.key_windowBackgroundWhiteGrayText3 || key == Theme.key_windowBackgroundWhiteGrayText4
                    || key == Theme.key_windowBackgroundWhiteGrayText5 || key == Theme.key_windowBackgroundWhiteGrayText6
                    || key == Theme.key_windowBackgroundWhiteGrayText7 || key == Theme.key_windowBackgroundWhiteGrayText8
                    || key == Theme.key_windowBackgroundWhiteHintText || key == Theme.key_dialogTextGray2
                    || key == Theme.key_dialogTextGray3) {
                return FemboyGradient.alpha(ink, 0.6f);
            }
            if (key == Theme.key_divider || key == Theme.key_dialogGrayLine) {
                return FemboyGradient.alpha(ink, 0.15f);
            }
            if (key == Theme.key_sheet_scrollUp) {
                return FemboyGradient.alpha(ink, 0.35f);
            }
            if (key == Theme.key_listSelector || key == Theme.key_settings_listSelector) {
                return FemboyGradient.alpha(ink, 0.1f);
            }
            return base(key);
        }

        @Override
        public boolean isDark() {
            int[] pair = userId != 0 ? FemboyGradient.of(userId, null) : null;
            if (pair == null) {
                return Theme.isCurrentThemeDark();
            }
            return FemboyGradient.ink(pair) == 0xffffffff;
        }
    }
}
