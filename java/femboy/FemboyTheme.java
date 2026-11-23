package org.telegram.femboy;

import org.telegram.messenger.FileLog;
import org.telegram.ui.ActionBar.Theme;

/**
 * Тема по умолчанию — нежно-розовая, с лавандовым и голубым рядом.
 *
 * Свою тему не рисуем: акценты уже есть в ночной теме телеграма. У «Night»
 * акцент с id 11 — розовый (0xFFDE5E7E), 10 — лавандовый (0xFF8D78E3),
 * 12 — голубой (0xFF5977E8). Первый запуск ставит розовый; поверх него
 * работает Material You с пастельным розовым сидом, так что вся палитра
 * получается нежной, а не кислотной.
 *
 * Ставится ровно один раз, при первом запуске. Если человек потом выберет
 * другую тему, форк в этот выбор больше не лезет.
 */
public class FemboyTheme {

    public static final int PINK_ACCENT_ID = 11;
    public static final int LAVENDER_ACCENT_ID = 10;
    public static final int BLUE_ACCENT_ID = 12;

    public static void applyOnFirstLaunch() {
        try {
            if (!FemboyConfig.claimFirstLaunch()) {
                return;
            }
            Theme.ThemeInfo night = Theme.getTheme("Night");
            if (night == null) {
                return;
            }
            night.setCurrentAccentId(PINK_ACCENT_ID);
            Theme.saveThemeAccents(night, true, false, true, false);
            Theme.applyTheme(night);
        } catch (Exception e) {
            // Тема — не то, ради чего стоит ронять запуск.
            FileLog.e(e);
        }
    }

    /**
     * Material You светлый днём и тёмный ночью, как телефон: дневная тема
     * светлая, ночная тёмная, переключение по системе. Делается один раз при
     * первом запуске с Material You (или когда его включают руками), дальше
     * выбор человека не трогаем.
     */
    public static void followSystemDayNight(boolean force) {
        try {
            if (!FemboyYou.enabled()) {
                return;
            }
            android.content.SharedPreferences prefs = FemboyConfig.prefs();
            if (!force && prefs.getBoolean("md3_day_night", false)) {
                return;
            }
            prefs.edit().putBoolean("md3_day_night", true).apply();
            Theme.ThemeInfo night = Theme.getCurrentNightTheme();
            if (night == null || !night.isDark()) {
                night = Theme.getTheme("Night");
            }
            if (night != null) {
                Theme.setCurrentNightTheme(night);
            }
            Theme.ThemeInfo day = Theme.getCurrentTheme();
            if (day == null || day.isDark()) {
                Theme.ThemeInfo blue = Theme.getTheme("Blue");
                if (blue != null) {
                    Theme.applyTheme(blue, false);
                }
            }
            Theme.selectedAutoNightType = Theme.AUTO_NIGHT_TYPE_SYSTEM;
            Theme.saveAutoNightThemeConfig();
            Theme.checkAutoNightThemeConditions(true);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }
}
