package org.telegram.femboy;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

/**
 * Настройки форка. Держим их в отдельном файле и в своём разделе настроек,
 * чтобы правки не растекались по коду оригинала: чем меньше тронуто чужих
 * строк, тем проще будет подтягивать новые версии телеграма.
 */
public class FemboyConfig {

    private static final String PREFS = "femboy";

    public static final int INPUT_LINES_DEFAULT = 6;
    public static final float INPUT_TEXT_SIZE_DEFAULT = 18f;

    public static final String APP_NAME = "FemboyGram";

    /**
     * Номер этой сборки. По нему клиент понимает, что на гитхабе лежит версия
     * новее, и предлагает обновиться.
     *
     * Поднимать его надо руками при выпуске — вместе с номером в version.json.
     * Забыть про него значит выпустить сборку, которая всю жизнь будет
     * предлагать обновиться сама на себя.
     */
    /**
     * Версия для сравнения. Только числа через точку — по ней решается, что
     * новее, а что старее.
     *
     * Показывается человеку не она, а {@link #versionLabel()}. Развести эти
     * два значения пришлось потому, что выпуск называется «Пререлиз 1.0», а
     * такую строку сравнить нельзя: разбор по точкам споткнётся о слово и
     * молча вернёт ноль. Один раз мы уже выпустили трижды подряд то, что ни
     * разу не выполнялось, — второй раз наступать на такое не будем.
     *
     * 0.99, а не 1.0, нарочно: настоящая единица должна остаться свободной
     * под настоящий выпуск, а пререлиз обязан быть строго меньше её.
     */
    public static final String APP_VERSION = "1.0";

    /**
     * Номер, который видит человек. У пререлизов 0.99.N он был «1.0.N»; с
     * FemboyGram 1.0 оба номера снова совпадают.
     */
    public static String shownVersion() {
        return APP_VERSION;
    }

    /** Как выпуск называется для человека: «Пререлиз 1.0», «Бета 1.1». */
    public static String versionLabel() {
        return org.telegram.messenger.LocaleController.formatString(
                org.telegram.messenger.R.string.FemboyVersionLabel, shownVersion());
    }

    /**
     * Как часто спрашивать гитхаб про новую версию, в минутах. Ноль — не
     * спрашивать вовсе; проверить руками кнопкой можно и тогда.
     */
    public static final int UPDATE_INTERVAL_OFF = 0;
    public static final int UPDATE_INTERVAL_DEFAULT = 3;
    /** Значения для выбора: от трёх минут до суток. */
    public static final int[] UPDATE_INTERVALS = {3, 15, 60, 6 * 60, 24 * 60, UPDATE_INTERVAL_OFF};

    public static int updateIntervalMinutes() {
        return prefs().getInt("update_interval", UPDATE_INTERVAL_DEFAULT);
    }

    public static void setUpdateIntervalMinutes(int minutes) {
        prefs().edit().putInt("update_interval", minutes).apply();
    }

    /** Спрашивать ли самому. Кнопки «проверить сейчас» это не касается. */
    public static boolean updatesChecked() {
        return updateIntervalMinutes() > 0;
    }

    public static final String CHANNEL_URL = "https://t.me/femboygm";
    /** Свой набор стикеров: ставится обычной кнопкой, как любой другой набор. */
    public static final String STICKERS_URL = "https://t.me/addstickers/FemboyGramPack";

    public static final String SOURCE_URL = "https://github.com/webrch/femboy-gram";
    public static final String FORUM_URL = "https://t.me/femboyconfs";
    /**
     * Написать нам. Это тот же канал, но с ?direct: телеграм открывает не
     * ленту, а поле для сообщения — человек с жалобой не должен искать, куда
     * её деть.
     */
    public static final String FEEDBACK_URL = "https://t.me/fnabout";
    /**
     * Документация по плагинам. Ведёт на файл в репозитории, а не на страницу
     * сайта: страницы гитхаба у репозитория не включены, а ссылка на
     * несуществующий сайт — просто обман.
     *
     * Язык берётся из приложения. Английская — та, что без суффикса: она же
     * открывается по ссылке из README, и с неё есть переходы на остальные.
     */
    public static String pluginsDocsUrl() {
        final String base = "https://github.com/webrch/femboy-gram/blob/main/docs/plugins";
        String language = null;
        try {
            language = org.telegram.messenger.LocaleController.getInstance()
                    .getCurrentLocale().getLanguage();
        } catch (Exception ignored) {
        }
        if ("ru".equals(language) || "zh".equals(language)) {
            return base + "." + language + ".md";
        }
        return base + ".md";
    }

    public static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /**
     * До скольких строк растёт поле ввода, прежде чем начать прокручиваться.
     * Ноль означает «расти, пока есть место на экране»: сам EditText выше
     * своего контейнера не станет, так что бесконечности тут не будет — будет
     * ровно высота экрана, о которой и просили.
     */
    public static int inputMaxLines() {
        int v = inputMaxLinesRaw();
        return v <= 0 ? Integer.MAX_VALUE : v;
    }

    /**
     * То же значение, но как оно записано: ноль остаётся нулём. Экрану
     * настроек нужно именно это — иначе «без предела» пришлось бы угадывать
     * по двум миллиардам строк.
     */
    /** Размер стикеров в чате, в процентах от обычного. */
    public static int stickerSize() {
        return prefs().getInt("sticker_size", 100);
    }

    public static void setStickerSize(int percent) {
        prefs().edit().putInt("sticker_size", percent).apply();
    }

    /** Скругление стикеров в процентах от стороны: 0 — как есть, 50 — круг. */
    public static int stickerRound() {
        return prefs().getInt("sticker_round", 0);
    }

    public static void setStickerRound(int percent) {
        prefs().edit().putInt("sticker_round", Math.max(0, Math.min(50, percent))).apply();
    }

    public static int inputMaxLinesRaw() {
        return prefs().getInt("input_max_lines", INPUT_LINES_DEFAULT);
    }

    public static void setInputMaxLines(int lines) {
        prefs().edit().putInt("input_max_lines", lines).apply();
    }

    /** Размер текста в поле ввода, в тех же единицах, что и в оригинале. */
    public static float inputTextSize() {
        return prefs().getFloat("input_text_size", INPUT_TEXT_SIZE_DEFAULT);
    }

    public static void setInputTextSize(float sp) {
        prefs().edit().putFloat("input_text_size", sp).apply();
    }

    /** Поле ввода сверху экрана, а не снизу. */
    public static boolean inputOnTop() {
        return prefs().getBoolean("input_on_top", false);
    }

    public static void setInputOnTop(boolean top) {
        prefs().edit().putBoolean("input_on_top", top).apply();
    }


    /**
     * Виды своего оформления по отдельности. Всё включено по умолчанию:
     * выключение — это про «мне мешает», а не про «покажите сначала».
     */
    public static boolean markupEnabled(int kind) {
        // «Кнопку» владелец убрал: подпись показывается обычным текстом
        if (kind == FemboyMarkup.KIND_BUTTON) {
            return false;
        }
        return prefs().getBoolean("markup_" + kind, true);
    }

    public static void setMarkupEnabled(int kind, boolean on) {
        prefs().edit().putBoolean("markup_" + kind, on).apply();
    }

    /**
     * Разметка значками по видам: **жирный**, __курсив__ и прочие, включая
     * свои — ++подчёркнутый++ и цитаты через «больше». Всё включено по
     * умолчанию; выключенный вид остаётся в тексте как есть.
     */
    public static boolean markdownEnabled(String kind) {
        return prefs().getBoolean("markdown_" + kind, true);
    }

    public static void setMarkdownEnabled(String kind, boolean on) {
        prefs().edit().putBoolean("markdown_" + kind, on).apply();
    }

    /** Показывать баннеры за аватарками. */
    public static boolean bannersEnabled() {
        return prefs().getBoolean("profile_banners", true);
    }

    public static void setBannersEnabled(boolean on) {
        prefs().edit().putBoolean("profile_banners", on).apply();
    }

    /** Показывать чужие градиенты профиля. */
    public static boolean gradientsEnabled() {
        return prefs().getBoolean("profile_gradients", true);
    }

    public static void setGradientsEnabled(boolean on) {
        prefs().edit().putBoolean("profile_gradients", on).apply();
    }

    /** Показывать стены в профилях. */
    public static boolean wallEnabled() {
        return prefs().getBoolean("profile_wall", true);
    }

    public static void setWallEnabled(boolean on) {
        prefs().edit().putBoolean("profile_wall", on).apply();
    }

    /** Премиум-значки без премиума: видны только в форке. */
    public static boolean freeEmoji() {
        return prefs().getBoolean("free_emoji", true);
    }

    public static void setFreeEmoji(boolean on) {
        prefs().edit().putBoolean("free_emoji", on).apply();
    }

    public static boolean emojiWarned() {
        return prefs().getBoolean("emoji_warned", false);
    }

    public static void setEmojiWarned(boolean value) {
        prefs().edit().putBoolean("emoji_warned", value).apply();
    }

    /** Пункт «Копировать с оформлением» в меню сообщения. */
    public static boolean copyFormatting() {
        return prefs().getBoolean("copy_formatting", true);
    }

    public static void setCopyFormatting(boolean on) {
        prefs().edit().putBoolean("copy_formatting", on).apply();
    }

    /** Показывали ли предупреждение о своём оформлении. Один раз за всё время. */
    public static boolean markupWarned() {
        return prefs().getBoolean("markup_warned", false);
    }

    public static void setMarkupWarned(boolean value) {
        prefs().edit().putBoolean("markup_warned", value).apply();
    }

    /** Скриншоты где угодно: каналы с запретом пересылки, секретные чаты, исчезающие фото. */
    public static boolean anyScreenshots() {
        return prefs().getBoolean("any_screenshots", true);
    }

    public static void setAnyScreenshots(boolean on) {
        prefs().edit().putBoolean("any_screenshots", on).apply();
    }

    /** Показывать айди в профилях людей, групп, каналов и ботов. */
    public static boolean showIds() {
        return prefs().getBoolean("show_ids", true);
    }

    public static void setShowIds(boolean on) {
        prefs().edit().putBoolean("show_ids", on).apply();
    }

    /**
     * «Приступ»: весь текст переливается радугой. Выключено по умолчанию и
     * включается только через предупреждение — мигающая картинка бывает опасна
     * не в переносном смысле.
     */
    public static boolean seizure() {
        return prefs().getBoolean("seizure", false);
    }

    public static void setSeizure(boolean on) {
        prefs().edit().putBoolean("seizure", on).apply();
    }

    /**
     * Режим Discord: сообщения стеной, без пузырей, все слева, с аватаркой и
     * ником у каждого автора, свои тоже.
     */
    public static boolean discordMode() {
        if (discordCache == null) {
            discordCache = prefs().getBoolean("discord_mode", false);
        }
        return discordCache;
    }

    private static Boolean discordCache;

    public static void setDiscordMode(boolean on) {
        discordCache = on;
        prefs().edit().putBoolean("discord_mode", on).apply();
    }

    /** Спрятать вкладку «Все чаты», когда есть папки. */
    public static boolean hideAllChatsTab() {
        return prefs().getBoolean("hide_all_chats_tab", false);
    }

    public static void setHideAllChatsTab(boolean on) {
        prefs().edit().putBoolean("hide_all_chats_tab", on).apply();
    }

    /** Свой прокси через веб-шлюз телеграма, см. FemboyProxy. */
    public static boolean proxyEnabled() {
        // встроенный прокси владелец убрал (2026-11-13): не включается и не предлагается
        return false;
    }

    public static void setProxyEnabled(boolean on) {
        prefs().edit().putBoolean("proxy_enabled", on).apply();
    }

    /** Человек уже ответил на предложение прокси и попросил больше не спрашивать. */
    public static boolean proxyAsked() {
        return prefs().getBoolean("proxy_asked", false);
    }

    public static void setProxyAsked(boolean asked) {
        prefs().edit().putBoolean("proxy_asked", asked).apply();
    }

    /** Секрет своего прокси, 32 шестнадцатеричных знака. */
    public static String proxySecret() {
        return prefs().getString("proxy_secret", null);
    }

    public static void setProxySecret(String secret) {
        prefs().edit().putString("proxy_secret", secret).apply();
    }

    /** Режим стримера: прятать номер телефона. */
    public static boolean streamerMode() {
        return prefs().getBoolean("streamer", false);
    }

    public static void setStreamerMode(boolean on) {
        prefs().edit().putBoolean("streamer", on).apply();
    }

    public static boolean streamerHidesOthers() {
        return prefs().getBoolean("streamer_others", false);
    }

    public static void setStreamerHidesOthers(boolean on) {
        prefs().edit().putBoolean("streamer_others", on).apply();
    }

    public static boolean streamerHidesUsername() {
        return prefs().getBoolean("streamer_username", false);
    }

    public static void setStreamerHidesUsername(boolean on) {
        prefs().edit().putBoolean("streamer_username", on).apply();
    }

    /** Правка тегов у музыки. По умолчанию включена. */
    /** Теги музыки всегда включены: пункт из настроек убран. */
    public static boolean tagsEnabled() {
        return true;
    }

    public static void setTagsEnabled(boolean enabled) {
        prefs().edit().putBoolean("tags_enabled", enabled).apply();
    }

    /**
     * Слышал ли человек мяуканье хоть раз. До этого раздела «Звук» в
     * настройках нет: настраивать то, о существовании чего не знаешь, незачем,
     * а найденная случайно шутка тем и хороша, что найдена.
     */
    public static boolean meowHeard() {
        return prefs().getBoolean("meow_heard", false);
    }

    public static void setMeowHeard() {
        prefs().edit().putBoolean("meow_heard", true).apply();
    }

    /** Мяуканье по нажатию на название — можно выключить совсем. */
    public static boolean meowEnabled() {
        return prefs().getBoolean("meow_enabled", true);
    }

    public static void setMeowEnabled(boolean enabled) {
        prefs().edit().putBoolean("meow_enabled", enabled).apply();
    }

    /**
     * Путь к своему звуку. Пусто — играет тот, что лежит в сборке. Файл
     * копируется к нам при выборе: ссылка на чужой файл живёт до первой
     * уборки в галерее, а копия — сколько нужно.
     */
    public static String meowPath() {
        return prefs().getString("meow_path", null);
    }

    public static void setMeowPath(String path) {
        if (path == null) {
            prefs().edit().remove("meow_path").apply();
        } else {
            prefs().edit().putString("meow_path", path).apply();
        }
    }

    /**
     * Первый запуск. Нужен, чтобы один раз включить тёмно-зелёную тему и
     * больше в выбор темы не лезть: если человек потом поставит другую, наше
     * дело в это не вмешиваться.
     */
    public static boolean claimFirstLaunch() {
        if (prefs().getBoolean("first_launch_done", false)) {
            return false;
        }
        prefs().edit().putBoolean("first_launch_done", true).apply();
        return true;
    }

    /**
     * Главный выключатель плагинов. По умолчанию выключен: плагин выполняется
     * внутри приложения и может всё, что может приложение, — такое не
     * включают за человека.
     */
    public static boolean pluginsEnabled() {
        return prefs().getBoolean("plugins_enabled", false);
    }

    public static void setPluginsEnabled(boolean on) {
        prefs().edit().putBoolean("plugins_enabled", on).apply();
    }

    /** Включён ли отдельный плагин. Новый плагин всегда выключен. */
    public static boolean pluginEnabled(String id) {
        return prefs().getBoolean("plugin_" + id, false);
    }

    public static void setPluginEnabled(String id, boolean on) {
        prefs().edit().putBoolean("plugin_" + id, on).apply();
    }

    /**
     * Пример плагина кладётся в папку один раз. Если человек его удалил,
     * второй раз он не появится: удаление — это ответ, а не случайность.
     */
    public static boolean claimExamplePlugin() {
        if (prefs().getBoolean("plugin_example_done", false)) {
            return false;
        }
        prefs().edit().putBoolean("plugin_example_done", true).apply();
        return true;
    }

    /** Used once to replace the inherited example plugin after an update. */
    public static void resetExamplePlugin() {
        prefs().edit().remove("plugin_example_done").apply();
    }

    // ------------------------------------------------------------- ghost mode
    public static boolean ghostMode() {
        return prefs().getBoolean("ghost_mode", false);
    }

    /** Master switch sets all four behaviours at once, like the upstream mod. */
    public static void setGhostMode(boolean on) {
        prefs().edit()
                .putBoolean("ghost_mode", on)
                .putBoolean("ghost_read", !on)
                .putBoolean("ghost_online", !on)
                .putBoolean("ghost_typing", !on)
                .putBoolean("ghost_offline_marker", on)
                .apply();
    }

    public static boolean ghostHidesRead() {
        return prefs().getBoolean("ghost_read", !ghostMode());
    }

    public static void setGhostHidesRead(boolean on) {
        prefs().edit().putBoolean("ghost_read", on).apply();
    }

    public static boolean ghostHidesOnline() {
        return prefs().getBoolean("ghost_online", !ghostMode());
    }

    public static void setGhostHidesOnline(boolean on) {
        prefs().edit().putBoolean("ghost_online", on).apply();
    }

    public static boolean ghostHidesTyping() {
        return prefs().getBoolean("ghost_typing", !ghostMode());
    }

    public static void setGhostHidesTyping(boolean on) {
        prefs().edit().putBoolean("ghost_typing", on).apply();
    }

    public static boolean ghostOfflineMarker() {
        return prefs().getBoolean("ghost_offline_marker", ghostMode());
    }

    public static void setGhostOfflineMarker(boolean on) {
        prefs().edit().putBoolean("ghost_offline_marker", on).apply();
    }

    public static boolean ghostMarkReadAfterSend() {
        return prefs().getBoolean("ghost_mark_after_send", true);
    }

    public static void setGhostMarkReadAfterSend(boolean on) {
        prefs().edit().putBoolean("ghost_mark_after_send", on).apply();
    }
}
