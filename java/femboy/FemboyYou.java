package org.telegram.femboy;

import android.content.Context;
import android.os.Build;
import android.util.SparseIntArray;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeColors;

/**
 * Material You: цвета темы телеграма из палитры телефона.
 *
 * Тема телеграма остаётся какой была (светлая днём, тёмная ночью), а поверх
 * неё каждый ключ перекрашивается. Главные ключи (фоны, текст, кнопки,
 * пузыри) получают роли MD3 напрямую. Остальные сотни ключей проходят через
 * общее правило: серое становится нейтральным тоном палитры той же светлоты,
 * синее и акцентное становится основным тоном, а красное, зелёное и прочие
 * смысловые цвета остаются своими.
 *
 * На андроиде 12 и новее палитра берётся у системы (Monet, цвета обоев),
 * на старых строится из мятного цвета Femboy.
 *
 * Выключение возвращает обычные темы без всякой починки: сама тема не
 * трогается, перекраска живёт только в {@link #color}.
 */
public class FemboyYou {

    /** Мятный цвет Femboy, из него палитра там, где Monet нет. */
    public static final int SEED = 0xffff9ecd;

    private static Boolean enabled;

    public static boolean enabled() {
        if (enabled == null) {
            enabled = FemboyConfig.prefs().getBoolean("material_you", true);
        }
        return enabled;
    }

    public static void setEnabled(boolean on) {
        enabled = on;
        FemboyConfig.prefs().edit().putBoolean("material_you", on).apply();
        invalidate();
        Theme.refreshThemeColors();
    }

    /** Короткое имя для мест, где решается форма: углы, переключатели, загрузка. */
    public static boolean on() {
        return enabled();
    }

    // ---------------------------------------------------------------- палитра

    /** Тональная палитра: цвет по тону от 0 (чёрный) до 100 (белый). */
    private interface Palette {
        int tone(float t);
    }

    private static final float[] SYSTEM_TONES = {100, 99, 95, 90, 80, 70, 60, 50, 40, 30, 20, 10, 0};

    /** Палитра из тринадцати системных оттенков, между ними по Lab. */
    private static final class SystemPalette implements Palette {
        final double[][] lab = new double[13][3];
        final int[] colors = new int[13];

        SystemPalette(int[] colors) {
            for (int i = 0; i < 13; i++) {
                this.colors[i] = colors[i];
                ColorUtils.colorToLAB(colors[i], lab[i]);
            }
        }

        @Override
        public int tone(float t) {
            if (t >= 100) return colors[0];
            if (t <= 0) return colors[12];
            for (int i = 0; i < 12; i++) {
                float hi = SYSTEM_TONES[i], lo = SYSTEM_TONES[i + 1];
                if (t <= hi && t >= lo) {
                    double f = (hi - t) / (hi - lo);
                    double[] a = lab[i], b = lab[i + 1];
                    return ColorUtils.LABToColor(
                            t,
                            a[1] + (b[1] - a[1]) * f,
                            a[2] + (b[2] - a[2]) * f);
                }
            }
            return colors[12];
        }
    }

    /** Палитра по оттенку и насыщенности в LCh, насыщенность ужимается до охвата sRGB. */
    private static final class SeedPalette implements Palette {
        final double hue;
        final double chroma;

        SeedPalette(double hue, double chroma) {
            this.hue = hue;
            this.chroma = chroma;
        }

        @Override
        public int tone(float t) {
            double c = chroma;
            double cos = Math.cos(hue), sin = Math.sin(hue);
            while (c > 0 && !inGamut(t, c * cos, c * sin)) {
                c -= 1;
            }
            return ColorUtils.LABToColor(t, Math.max(c, 0) * cos, Math.max(c, 0) * sin);
        }

        private static final double[] xyz = new double[3];

        private static boolean inGamut(double l, double a, double b) {
            ColorUtils.LABToXYZ(l, a, b, xyz);
            double x = xyz[0] / 100, y = xyz[1] / 100, z = xyz[2] / 100;
            double r = x * 3.2406 + y * -1.5372 + z * -0.4986;
            double g = x * -0.9689 + y * 1.8758 + z * 0.0415;
            double bl = x * 0.0557 + y * -0.2040 + z * 1.0570;
            final double e = 0.002;
            return r >= -e && r <= 1 + e && g >= -e && g <= 1 + e && bl >= -e && bl <= 1 + e;
        }
    }

    private static Palette primary, secondary, tertiary, neutral, neutralVariant;
    private static int paletteStamp;

    private static final int[][] SYSTEM_IDS = Build.VERSION.SDK_INT >= 31 ? new int[][]{
            {android.R.color.system_accent1_0, android.R.color.system_accent1_10, android.R.color.system_accent1_50, android.R.color.system_accent1_100, android.R.color.system_accent1_200, android.R.color.system_accent1_300, android.R.color.system_accent1_400, android.R.color.system_accent1_500, android.R.color.system_accent1_600, android.R.color.system_accent1_700, android.R.color.system_accent1_800, android.R.color.system_accent1_900, android.R.color.system_accent1_1000},
            {android.R.color.system_accent2_0, android.R.color.system_accent2_10, android.R.color.system_accent2_50, android.R.color.system_accent2_100, android.R.color.system_accent2_200, android.R.color.system_accent2_300, android.R.color.system_accent2_400, android.R.color.system_accent2_500, android.R.color.system_accent2_600, android.R.color.system_accent2_700, android.R.color.system_accent2_800, android.R.color.system_accent2_900, android.R.color.system_accent2_1000},
            {android.R.color.system_accent3_0, android.R.color.system_accent3_10, android.R.color.system_accent3_50, android.R.color.system_accent3_100, android.R.color.system_accent3_200, android.R.color.system_accent3_300, android.R.color.system_accent3_400, android.R.color.system_accent3_500, android.R.color.system_accent3_600, android.R.color.system_accent3_700, android.R.color.system_accent3_800, android.R.color.system_accent3_900, android.R.color.system_accent3_1000},
            {android.R.color.system_neutral1_0, android.R.color.system_neutral1_10, android.R.color.system_neutral1_50, android.R.color.system_neutral1_100, android.R.color.system_neutral1_200, android.R.color.system_neutral1_300, android.R.color.system_neutral1_400, android.R.color.system_neutral1_500, android.R.color.system_neutral1_600, android.R.color.system_neutral1_700, android.R.color.system_neutral1_800, android.R.color.system_neutral1_900, android.R.color.system_neutral1_1000},
            {android.R.color.system_neutral2_0, android.R.color.system_neutral2_10, android.R.color.system_neutral2_50, android.R.color.system_neutral2_100, android.R.color.system_neutral2_200, android.R.color.system_neutral2_300, android.R.color.system_neutral2_400, android.R.color.system_neutral2_500, android.R.color.system_neutral2_600, android.R.color.system_neutral2_700, android.R.color.system_neutral2_800, android.R.color.system_neutral2_900, android.R.color.system_neutral2_1000},
    } : null;

    /** Отпечаток системной палитры: поменялись обои, поменялся и он. */
    private static int systemStamp() {
        if (SYSTEM_IDS == null) {
            return SEED;
        }
        try {
            Context c = ApplicationLoader.applicationContext;
            return c.getColor(SYSTEM_IDS[0][7]) * 31 + c.getColor(SYSTEM_IDS[3][7]) * 17 + c.getColor(SYSTEM_IDS[2][7]);
        } catch (Throwable e) {
            return SEED;
        }
    }

    /** Если обои поменялись с прошлого раза, перекрашивает тему. Дёшево, звать можно часто. */
    public static void checkWallpaperPalette() {
        if (!enabled() || primary == null) {
            return;
        }
        if (systemStamp() != paletteStamp) {
            invalidate();
            Theme.refreshThemeColors();
        }
    }

    public static boolean isMonet() {
        return SYSTEM_IDS != null;
    }

    /** Свой цвет вместо цветов обоев; 0 — цвета обоев телефона. По умолчанию — нежно-розовый. */
    public static int seed() {
        return FemboyConfig.prefs().getInt("my_seed", SEED);
    }

    public static void setSeed(int color) {
        FemboyConfig.prefs().edit().putInt("my_seed", color).apply();
        invalidate();
        Theme.refreshThemeColors();
    }

    /** Три цвета для кружка выбора: основной, вторичный, третичный, в тонах нынешней темы. */
    public static int[] swatch(int seedColor) {
        final boolean isDark = Theme.getActiveTheme() != null && Theme.getActiveTheme().isDark();
        final Palette[] p = seedColor == 0 && SYSTEM_IDS != null ? systemPalettes() : seedPalettes(seedColor == 0 ? SEED : seedColor);
        if (p == null) {
            return new int[]{SEED, SEED, SEED};
        }
        return new int[]{p[0].tone(isDark ? 80 : 40), p[1].tone(isDark ? 70 : 80), p[2].tone(isDark ? 70 : 60)};
    }

    private static Palette[] systemPalettes() {
        try {
            final Context c = ApplicationLoader.applicationContext;
            final Palette[] out = new Palette[5];
            for (int p = 0; p < 5; p++) {
                final int[] colors = new int[13];
                for (int i = 0; i < 13; i++) {
                    colors[i] = c.getColor(SYSTEM_IDS[p][i]);
                }
                out[p] = new SystemPalette(colors);
            }
            return out;
        } catch (Throwable e) {
            return null;
        }
    }

    private static Palette[] seedPalettes(int seedColor) {
        final double[] lab = new double[3];
        ColorUtils.colorToLAB(seedColor | 0xFF000000, lab);
        final double hue = Math.atan2(lab[2], lab[1]);
        // доли насыщенности как у «тонального пятна» MD3
        return new Palette[]{new SeedPalette(hue, 40), new SeedPalette(hue, 16),
                new SeedPalette(hue + Math.toRadians(60), 26), new SeedPalette(hue, 4), new SeedPalette(hue, 8)};
    }

    private static void loadPalettes() {
        paletteStamp = systemStamp();
        final int own = seed();
        if (own != 0) {
            final Palette[] p = seedPalettes(own);
            primary = p[0];
            secondary = p[1];
            tertiary = p[2];
            neutral = p[3];
            neutralVariant = p[4];
            return;
        }
        if (SYSTEM_IDS != null) {
            try {
                Context c = ApplicationLoader.applicationContext;
                Palette[] out = new Palette[5];
                for (int p = 0; p < 5; p++) {
                    int[] colors = new int[13];
                    for (int i = 0; i < 13; i++) {
                        colors[i] = c.getColor(SYSTEM_IDS[p][i]);
                    }
                    out[p] = new SystemPalette(colors);
                }
                primary = out[0];
                secondary = out[1];
                tertiary = out[2];
                neutral = out[3];
                neutralVariant = out[4];
                return;
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        double[] lab = new double[3];
        ColorUtils.colorToLAB(SEED, lab);
        double hue = Math.atan2(lab[2], lab[1]);
        // доли насыщенности как у «тонального пятна» MD3
        primary = new SeedPalette(hue, 40);
        secondary = new SeedPalette(hue, 16);
        tertiary = new SeedPalette(hue + Math.toRadians(60), 26);
        neutral = new SeedPalette(hue, 4);
        neutralVariant = new SeedPalette(hue, 8);
    }

    // ------------------------------------------------------------------ роли

    private static boolean dark;
    private static boolean built;
    private static final SparseIntArray roles = new SparseIntArray();
    private static final SparseIntArray genericIn = new SparseIntArray();
    private static final SparseIntArray genericOut = new SparseIntArray();
    private static boolean[] skip;
    private static double accentHue = Double.NaN;

    public static int surface, surfaceContainerLow, surfaceContainer, surfaceContainerHigh, surfaceContainerHighest;
    public static int onSurface, onSurfaceVariant, outline, outlineVariant;
    public static int primaryColor, onPrimary, primaryContainer, onPrimaryContainer;
    public static int secondaryContainer, onSecondaryContainer, tertiaryColor, tertiaryContainer, onTertiaryContainer;

    public static void invalidate() {
        built = false;
        primary = null;
        genericIn.clear();
        genericOut.clear();
    }

    private static void ensure() {
        Theme.ThemeInfo theme = Theme.getActiveTheme();
        boolean isDark = theme != null && theme.isDark();
        if (built && isDark == dark) {
            return;
        }
        dark = isDark;
        if (primary == null) {
            loadPalettes();
        }
        genericIn.clear();
        genericOut.clear();
        buildRoles();
        built = true;
    }

    private static int t(Palette p, float light, float night) {
        return p.tone(dark ? night : light);
    }

    private static int alpha(int color, float a) {
        return ColorUtils.setAlphaComponent(color, Math.round(255 * a));
    }

    private static void buildRoles() {
        surface = t(neutral, 98, 6);
        surfaceContainerLow = t(neutral, 96, 10);
        surfaceContainer = t(neutral, 94, 12);
        surfaceContainerHigh = t(neutral, 92, 17);
        surfaceContainerHighest = t(neutral, 90, 22);
        onSurface = t(neutral, 10, 90);
        onSurfaceVariant = t(neutralVariant, 30, 80);
        outline = t(neutralVariant, 50, 60);
        outlineVariant = t(neutralVariant, 80, 30);
        primaryColor = t(primary, 40, 80);
        onPrimary = t(primary, 100, 20);
        primaryContainer = t(primary, 90, 30);
        onPrimaryContainer = t(primary, 10, 90);
        secondaryContainer = t(secondary, 90, 30);
        onSecondaryContainer = t(secondary, 10, 90);
        tertiaryColor = t(tertiary, 40, 80);
        tertiaryContainer = t(tertiary, 90, 30);
        onTertiaryContainer = t(tertiary, 10, 90);

        // фон списков и карточки секций: днём светлая карточка на сером, ночью
        // карточка светлее почти чёрного фона
        final int card = t(neutral, 99, 12);
        final int page = t(neutral, 94, 6);
        final int bar = card;
        final int sheet = t(neutral, 97, 14);
        final int menu = surfaceContainerHigh;
        final int selector = alpha(onSurface, dark ? 0.10f : 0.08f);
        final int gray = onSurfaceVariant;
        final int hint = t(neutralVariant, 50, 60);

        roles.clear();
        // фоны
        r(Theme.key_windowBackgroundWhite, card);
        r(Theme.key_windowBackgroundGray, page);
        r(Theme.key_windowBackgroundGrayShadow, 0);
        r(Theme.key_divider, 0);
        r(Theme.key_graySection, surfaceContainer);
        r(Theme.key_graySectionText, gray);
        r(Theme.key_listSelector, selector);
        r(Theme.key_settings_listSelector, selector);
        r(Theme.key_emptyListPlaceholder, gray);

        // текст
        r(Theme.key_windowBackgroundWhiteBlackText, onSurface);
        r(Theme.key_windowBackgroundWhiteGrayIcon, gray);
        r(Theme.key_windowBackgroundWhiteGrayText, gray);
        r(Theme.key_windowBackgroundWhiteGrayText2, gray);
        r(Theme.key_windowBackgroundWhiteGrayText3, hint);
        r(Theme.key_windowBackgroundWhiteGrayText4, gray);
        r(Theme.key_windowBackgroundWhiteGrayText6, gray);
        r(Theme.key_windowBackgroundWhiteGrayText8, gray);
        r(Theme.key_windowBackgroundWhiteHintText, hint);
        r(Theme.key_windowBackgroundWhiteBlueHeader, primaryColor);
        r(Theme.key_windowBackgroundWhiteValueText, primaryColor);
        r(Theme.key_windowBackgroundWhiteLinkText, primaryColor);
        r(Theme.key_windowBackgroundWhiteBlueText, primaryColor);
        r(Theme.key_windowBackgroundWhiteBlueText2, primaryColor);
        r(Theme.key_windowBackgroundWhiteBlueText4, primaryColor);
        r(Theme.key_windowBackgroundWhiteBlueButton, primaryColor);
        r(Theme.key_windowBackgroundWhiteBlueIcon, primaryColor);
        r(Theme.key_windowBackgroundWhiteInputField, outline);
        r(Theme.key_windowBackgroundWhiteInputFieldActivated, primaryColor);
        r(Theme.key_windowBackgroundWhiteLinkSelection, alpha(primaryColor, 0.25f));

        // переключатели, флажки, кнопки
        r(Theme.key_switchTrack, surfaceContainerHighest);
        r(Theme.key_switchTrackChecked, primaryColor);
        r(Theme.key_switchTrackBlue, surfaceContainerHighest);
        r(Theme.key_switchTrackBlueChecked, primaryColor);
        r(Theme.key_switchTrackBlueThumb, outline);
        r(Theme.key_switchTrackBlueThumbChecked, onPrimary);
        r(Theme.key_switch2TrackChecked, primaryColor);
        r(Theme.key_checkboxSquareBackground, primaryColor);
        r(Theme.key_checkboxSquareCheck, onPrimary);
        r(Theme.key_checkboxSquareUnchecked, onSurfaceVariant);
        r(Theme.key_checkbox, primaryColor);
        r(Theme.key_checkboxCheck, onPrimary);
        r(Theme.key_radioBackground, onSurfaceVariant);
        r(Theme.key_radioBackgroundChecked, primaryColor);
        r(Theme.key_featuredStickers_addButton, primaryColor);
        r(Theme.key_featuredStickers_addButtonPressed, t(primary, 35, 70));
        r(Theme.key_featuredStickers_buttonText, onPrimary);
        r(Theme.key_featuredStickers_buttonProgress, onPrimary);
        r(Theme.key_featuredStickers_addedIcon, primaryColor);
        r(Theme.key_featuredStickers_unread, primaryColor);
        r(Theme.key_progressCircle, primaryColor);
        r(Theme.key_fastScrollActive, primaryColor);
        r(Theme.key_fastScrollText, onPrimary);
        r(Theme.key_buttonNeutral, secondaryContainer);
        r(Theme.key_buttonNeutralText, onSecondaryContainer);

        // верхняя панель
        r(Theme.key_actionBarDefault, bar);
        r(Theme.key_actionBarDefaultIcon, onSurfaceVariant);
        r(Theme.key_actionBarDefaultTitle, onSurface);
        r(Theme.key_actionBarDefaultSubtitle, gray);
        r(Theme.key_actionBarDefaultSelector, selector);
        r(Theme.key_actionBarWhiteSelector, selector);
        r(Theme.key_actionBarDefaultSearch, onSurface);
        r(Theme.key_actionBarDefaultSearchPlaceholder, hint);
        r(Theme.key_actionBarDefaultSubmenuBackground, menu);
        r(Theme.key_actionBarDefaultSubmenuItem, onSurface);
        r(Theme.key_actionBarDefaultSubmenuItemIcon, onSurfaceVariant);
        r(Theme.key_actionBarDefaultSubmenuSeparator, surfaceContainer);
        r(Theme.key_actionBarActionModeDefault, bar);
        r(Theme.key_actionBarActionModeDefaultIcon, onSurfaceVariant);
        r(Theme.key_actionBarActionModeDefaultSelector, selector);
        r(Theme.key_actionBarTabActiveText, primaryColor);
        r(Theme.key_actionBarTabUnactiveText, onSurfaceVariant);
        r(Theme.key_actionBarTabLine, primaryColor);
        r(Theme.key_actionBarTabSelector, selector);
        r(Theme.key_actionBarDefaultArchived, bar);
        r(Theme.key_actionBarDefaultArchivedIcon, onSurfaceVariant);
        r(Theme.key_actionBarDefaultArchivedTitle, onSurface);
        r(Theme.key_actionBarDefaultArchivedSearch, onSurface);
        r(Theme.key_actionBarDefaultArchivedSelector, selector);

        // список чатов
        r(Theme.key_chats_name, onSurface);
        r(Theme.key_chats_nameArchived, onSurface);
        r(Theme.key_chats_message, gray);
        r(Theme.key_chats_message_threeLines, gray);
        r(Theme.key_chats_date, gray);
        r(Theme.key_chats_nameMessage, primaryColor);
        r(Theme.key_chats_attachMessage, primaryColor);
        r(Theme.key_chats_actionMessage, primaryColor);
        r(Theme.key_chats_unreadCounter, primaryColor);
        r(Theme.key_chats_unreadCounterText, onPrimary);
        // заглушённые: тот же цвет цифр, но на приглушённом тоне палитры, а не на сером
        r(Theme.key_chats_unreadCounterMuted, t(secondary, 60, 70));
        r(Theme.key_topics_unreadCounter, primaryColor);
        r(Theme.key_topics_unreadCounterMuted, t(secondary, 60, 70));
        r(Theme.key_chats_tabUnreadActiveBackground, primaryColor);
        r(Theme.key_chats_tabUnreadUnactiveBackground, t(secondary, 60, 70));
        r(Theme.key_chats_pinnedOverlay, alpha(onSurface, 0.04f));
        r(Theme.key_chats_pinnedIcon, hint);
        r(Theme.key_chats_muteIcon, hint);
        r(Theme.key_chats_actionBackground, primaryContainer);
        r(Theme.key_chats_actionPressedBackground, t(primary, 85, 35));
        r(Theme.key_chats_actionIcon, onPrimaryContainer);
        r(Theme.key_chats_archiveBackground, primaryColor);
        r(Theme.key_chats_archivePullDownBackgroundActive, primaryColor);
        r(Theme.key_chats_archivePullDownBackground, surfaceContainerHighest);
        r(Theme.key_chats_menuBackground, surfaceContainerLow);
        r(Theme.key_chats_menuItemText, onSurfaceVariant);
        r(Theme.key_chats_menuItemIcon, onSurfaceVariant);
        r(Theme.key_chats_menuItemCheck, primaryColor);

        // диалоги и нижние листы
        r(Theme.key_dialogBackground, sheet);
        r(Theme.key_dialogBackgroundGray, page);
        r(Theme.key_dialogTextBlack, onSurface);
        r(Theme.key_dialogTextGray2, gray);
        r(Theme.key_dialogTextGray3, hint);
        r(Theme.key_dialogTextHint, hint);
        r(Theme.key_dialogIcon, onSurfaceVariant);
        r(Theme.key_dialogButton, primaryColor);
        r(Theme.key_dialogButtonSelector, alpha(primaryColor, 0.12f));
        r(Theme.key_dialogTextLink, primaryColor);
        r(Theme.key_dialogTextBlue, primaryColor);
        r(Theme.key_dialogTextBlue2, primaryColor);
        r(Theme.key_dialogTextBlue4, primaryColor);
        r(Theme.key_dialogTopBackground, primaryColor);
        r(Theme.key_dialogInputField, outline);
        r(Theme.key_dialogInputFieldActivated, primaryColor);
        r(Theme.key_dialogCheckboxSquareBackground, primaryColor);
        r(Theme.key_dialogCheckboxSquareCheck, onPrimary);
        r(Theme.key_dialogRadioBackgroundChecked, primaryColor);
        r(Theme.key_dialogRoundCheckBox, primaryColor);
        r(Theme.key_dialogRoundCheckBoxCheck, onPrimary);
        r(Theme.key_dialogLineProgress, primaryColor);
        r(Theme.key_dialogLineProgressBackground, secondaryContainer);
        r(Theme.key_dialogGrayLine, 0);
        r(Theme.key_dialogScrollGlow, surfaceContainer);
        r(Theme.key_dialogSearchBackground, surfaceContainerHigh);
        r(Theme.key_dialogSearchHint, hint);
        r(Theme.key_dialogSearchIcon, onSurfaceVariant);
        r(Theme.key_dialogSearchText, onSurface);
        r(Theme.key_sheet_scrollUp, outlineVariant);

        // профиль
        r(Theme.key_profile_title, onSurface);
        r(Theme.key_profile_tabSelectedText, primaryColor);
        r(Theme.key_profile_tabSelectedLine, primaryColor);
        r(Theme.key_profile_tabText, onSurfaceVariant);
        r(Theme.key_profile_actionBackground, primaryContainer);
        r(Theme.key_profile_actionIcon, onPrimaryContainer);

        // чат: свои пузыри в основном контейнере, чужие на поверхности
        r(Theme.key_chat_outBubble, primaryContainer);
        r(Theme.key_chat_outBubbleSelected, t(primary, 84, 36));
        r(Theme.key_chat_messageTextOut, onPrimaryContainer);
        r(Theme.key_chat_messageLinkOut, dark ? t(primary, 95, 95) : t(primary, 30, 30));
        r(Theme.key_chat_inBubble, dark ? t(neutral, 100, 20) : t(neutral, 100, 100));
        r(Theme.key_chat_inBubbleSelected, dark ? t(neutral, 92, 26) : t(neutral, 92, 92));
        r(Theme.key_chat_messageTextIn, onSurface);
        r(Theme.key_chat_messagePanelBackground, surfaceContainerLow);
        r(Theme.key_chat_messagePanelText, onSurface);
        r(Theme.key_chat_messagePanelHint, hint);
        r(Theme.key_chat_messagePanelIcons, onSurfaceVariant);
        r(Theme.key_chat_messagePanelSend, primaryColor);
        r(Theme.key_chat_emojiPanelBackground, surfaceContainerLow);
        r(Theme.key_chat_inLoader, primaryColor);
        r(Theme.key_chat_inLoaderSelected, t(primary, 35, 70));
        r(Theme.key_chat_inMediaIcon, onPrimary);
        r(Theme.key_chat_inMediaIconSelected, onPrimary);
        r(Theme.key_chat_outLoader, t(primary, 40, 80));
        r(Theme.key_chat_outLoaderSelected, t(primary, 35, 75));
        r(Theme.key_chat_outMediaIcon, t(primary, 90, 20));
        r(Theme.key_chat_outMediaIconSelected, t(primary, 90, 20));

        // нижние вкладки
        r(Theme.key_glass_tabSelected, primaryColor);
        r(Theme.key_glass_tabSelectedText, primaryColor);
        r(Theme.key_glass_tabUnselected, onSurfaceVariant);
        r(Theme.key_glass_defaultIcon, alpha(onSurfaceVariant, 0.9f));
        r(Theme.key_glass_defaultText, alpha(onSurfaceVariant, 0.9f));
        r(Theme.key_glass_targetMainTabs, surfaceContainer);
        r(Theme.key_glass_targetMainTopPanel, surfaceContainer);

        // общее правило красит акцентное в основной тон; за акцент принимаем
        // синий телеграма и акцент той темы, что стоит сейчас
        accentHue = Double.NaN;
        try {
            Theme.ThemeInfo theme = Theme.getActiveTheme();
            if (theme != null) {
                Theme.ThemeAccent accent = theme.getAccent(false);
                int a = accent != null ? accent.accentColor : theme.accentBaseColor;
                if (a != 0) {
                    double[] lab = new double[3];
                    ColorUtils.colorToLAB(a, lab);
                    if (Math.hypot(lab[1], lab[2]) > 12) {
                        accentHue = Math.toDegrees(Math.atan2(lab[2], lab[1]));
                    }
                }
            }
        } catch (Throwable ignore) {
        }
    }

    private static void r(int key, int color) {
        roles.put(key, color);
    }

    /** Ключи, которые не перекрашиваем вовсе: картинки, именные цвета, премиум, звонки. */
    private static boolean skipped(int key) {
        if (skip == null) {
            boolean[] s = new boolean[Theme.colorsCount];
            String[] prefixes = {
                    "avatar_", "premium", "stories", "statistic", "code_", "color_", "chat_wallpaper",
                    "gift", "stars", "voipgroup", "calls_", "chat_service", "passport", "iv_", "chat_outBubbleGradient",
                    "wallpaper", "reactionStar", "telegram_color", "chats_onlineCircle", "returnToCall",
                    "chat_attach", "chat_mediaTimeBackground", "chat_mediaTimeText", "chat_previewDurationText",
                    "chat_secretTimeText", "chats_secret", "chat_gift", "groupcreate_spanBackground",
                    "fill_Red", "text_Red", "chat_inRedCall", "chat_outRedCall", "chat_recordedVoiceDot",
            };
            // у аватарок свои цвета людей, но шапка профиля и её кнопки должны быть в палитре
            String[] keep = {"ActionBar", "InProfile", "actionBar"};
            for (int key2 = 0; key2 < s.length; key2++) {
                String name = ThemeColors.getStringName(key2);
                if (name == null) {
                    s[key2] = true;
                    continue;
                }
                for (String p : prefixes) {
                    if (name.startsWith(p)) {
                        s[key2] = true;
                        break;
                    }
                }
                if (s[key2] && name.startsWith("avatar_")) {
                    for (String k : keep) {
                        if (name.contains(k)) {
                            s[key2] = false;
                            break;
                        }
                    }
                }
            }
            skip = s;
        }
        return key < 0 || key >= skip.length || skip[key];
    }

    private static final double[] tmpLab = new double[3];

    /** Общее правило для ключей без своей роли. */
    private static int generic(int raw) {
        int a = raw >>> 24;
        if (a == 0) {
            return raw;
        }
        double[] lab = tmpLab;
        ColorUtils.colorToLAB(raw | 0xff000000, lab);
        double chroma = Math.hypot(lab[1], lab[2]);
        float tone = (float) lab[0];
        int out;
        if (chroma < 12) {
            out = neutral.tone(tone);
        } else {
            double hue = Math.toDegrees(Math.atan2(lab[2], lab[1]));
            if (isAccentHue(hue)) {
                out = primary.tone(tone);
            } else {
                return raw;
            }
        }
        return (out & 0x00ffffff) | (a << 24);
    }

    private static boolean hueNear(double a, double b, double range) {
        double d = Math.abs(a - b) % 360;
        if (d > 180) d = 360 - d;
        return d <= range;
    }

    private static boolean isAccentHue(double hue) {
        // синий телеграма в Lab лежит около -80…-100 градусов
        if (hueNear(hue, -88, 38)) {
            return true;
        }
        return !Double.isNaN(accentHue) && hueNear(hue, accentHue, 30);
    }

    /**
     * Цвет ключа под Material You. raw — то, что дала бы тема.
     * Звать только если {@link #on()}.
     */
    public static synchronized int color(int key, int raw) {
        try {
            // градиент своих пузырей под Material You выключен, каким путём ни спроси
            if (dropsKey(key)) {
                return 0;
            }
            ensure();
            int i = roles.indexOfKey(key);
            if (i >= 0) {
                return roles.valueAt(i);
            }
            if (skipped(key)) {
                return raw;
            }
            int j = genericIn.indexOfKey(key);
            if (j >= 0 && genericIn.valueAt(j) == raw) {
                return genericOut.get(key);
            }
            int out = generic(raw);
            genericIn.put(key, raw);
            genericOut.put(key, out);
            return out;
        } catch (Throwable e) {
            return raw;
        }
    }

    /** Для {@link Theme#getCurrentColor}: градиент своих пузырей под Material You выключен. */
    public static boolean dropsKey(int key) {
        return key == Theme.key_chat_outBubbleGradient1 || key == Theme.key_chat_outBubbleGradient2
                || key == Theme.key_chat_outBubbleGradient3 || key == Theme.key_chat_outBubbleGradientAnimated;
    }

    /**
     * Обои чата под Material You. Обои, которые человек выбрал сам (картинка,
     * цвет, узор), показываем как есть. Обычные обои темы заменяем одним
     * ровным цветом палитры: так чат выглядит как приложение MD3, а не как
     * телеграм с чужим узором.
     */
    public static synchronized android.graphics.drawable.Drawable wallpaper(android.graphics.drawable.Drawable d, Theme.OverrideWallpaperInfo chosen) {
        if (!on()) {
            return d;
        }
        if (chosen != null && !chosen.isDefault()) {
            return d;
        }
        try {
            ensure();
            return new android.graphics.drawable.ColorDrawable(wallpaperColor());
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return d;
    }

    /** Ровный цвет обычных обоев: тёмный тон палитры ночью, светлый днём. */
    public static int wallpaperColor() {
        // тот же фон, что у списков во всём приложении
        return t(neutral, 99, 12);
    }

    /** Роль по светлоте темы, для мест, которые рисуют сами. */
    public static void ensureRoles() {
        ensure();
    }
}
