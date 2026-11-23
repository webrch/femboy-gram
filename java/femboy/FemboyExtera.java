package org.telegram.femboy;

import android.content.Context;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Плагин exteraGram (.plugin) в плагин Femboy (.marp).
 *
 * Их плагин — один файл на питоне с метаданными в переменных __id__,
 * __name__ и прочих. Наш — архив с manifest.json и main.py. Код переводить не
 * надо: модули с их именами (base_plugin, client_utils, ui …) лежат в
 * приложении и делают то же через femboy. Остаётся упаковать файл и
 * дописать в конец точку входа.
 *
 * Перед установкой человек видит, чего у нас нет из того, чем плагин
 * пользуется: лучше узнать это до, чем гадать после, почему не работает.
 */
public class FemboyExtera {

    /** Плагин больше 4 МБ — это уже не текст на питоне. */
    private static final int LIMIT = 4 * 1024 * 1024;

    private static final String TAIL = "\n\n# --- дописано при переводе из exteraGram ---\n"
            + "# Их плагин только объявляет класс; поднимает его приложение.\n"
            + "# У Femboy точка входа — on_start, поэтому она здесь и стоит.\n"
            + "def on_start():\n"
            + "    import base_plugin\n"
            + "    base_plugin.старт(femboy)\n";

    /** Модули, которые у нас есть под их именами. */
    private static final Set<String> KNOWN = new HashSet<>(Arrays.asList(
            "base_plugin", "client_utils", "android_utils", "hook_utils", "file_utils",
            "markdown_utils", "plugin_settings",
            "ui", "ui.settings", "ui.alert", "ui.bulletin",
            "extera_utils", "extera_utils.classes", "extera_utils.text_formatting", "java", "femboy",
            "com", "de", "dalvik", "packaging", "typing_extensions"));

    /** Что у них есть, а у нас нет; найденное в тексте плагина называем вслух. */
    private static final String[][] BROKEN = {
            {"add_intent_hook", "хуки интентов"},
            {"add_file_hook", "хуки файлов"},
            {"CHAT_ATTACH_MENU", "меню скрепки"},
            {"__requirements__", "установка пакетов пипом"},
    };

    public static final class Parsed {
        public final String id;
        public final String name;
        public final String version;
        public final String author;
        public final String description;
        /** Чего нет у Femboy, человеческими словами. */
        public final List<String> missing;

        Parsed(String id, String name, String version, String author, String description, List<String> missing) {
            this.id = id;
            this.name = name;
            this.version = version;
            this.author = author;
            this.description = description;
            this.missing = missing;
        }
    }

    /** Значение переменной name = "…" в начале строки. */
    private static String meta(String text, String name) {
        Matcher m = Pattern.compile("^" + name + "\\s*=\\s*[\"']([^\"']*)[\"']", Pattern.MULTILINE).matcher(text);
        return m.find() ? m.group(1) : null;
    }

    /** @return null, если это не плагин экстеры: у их плагина всегда есть __id__ и __name__. */
    public static Parsed parse(String text) {
        String id = meta(text, "__id__");
        String name = meta(text, "__name__");
        if (id == null || name == null) {
            return null;
        }
        List<String> missing = new ArrayList<>();
        Matcher imports = Pattern.compile("^\\s*(?:from|import)\\s+([A-Za-z_][\\w.]*)", Pattern.MULTILINE).matcher(text);
        Set<String> told = new HashSet<>();
        while (imports.find()) {
            String module = imports.group(1);
            String top = module.contains(".") ? module.substring(0, module.indexOf('.')) : module;
            if (KNOWN.contains(module) || KNOWN.contains(top)) {
                continue;
            }
            // java видна через jclass, стандартная библиотека питона с нами
            if ("org".equals(top) || "android".equals(top) || "androidx".equals(top) || "kotlin".equals(top) || isStandard(top)
                    || installedLibrary(top)) {
                continue;
            }
            if (told.add(top)) {
                missing.add(LocaleController.formatString(R.string.FemboyExteraUnknownModule, top));
            }
        }
        for (String[] thing : BROKEN) {
            if (text.contains(thing[0])) {
                missing.add(thing[1]);
            }
        }
        return new Parsed(id, name,
                first(meta(text, "__version__"), "1.0"),
                first(meta(text, "__author__"), ""),
                first(meta(text, "__description__"), ""),
                missing);
    }

    private static String first(String value, String otherwise) {
        return value == null || value.isEmpty() ? otherwise : value;
    }

    /** Другой плагин экстеры, который у нас уже стоит: его зовут как модуль. */
    private static boolean installedLibrary(String module) {
        try {
            return new File(new File(org.telegram.messenger.ApplicationLoader.applicationContext.getFilesDir(),
                    "femboy_plugins"), "extera." + module + "/main.py").exists();
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isStandard(String module) {
        return Arrays.asList("os", "sys", "re", "json", "time", "math", "random", "base64", "hashlib",
                "traceback", "datetime", "threading", "typing", "collections", "itertools", "functools",
                "struct", "zipfile", "io", "urllib", "html", "string", "dataclasses", "enum", "abc", "copy",
                "shutil", "tempfile", "textwrap", "uuid", "binascii", "unicodedata", "asyncio", "logging",
                "inspect", "shlex", "zlib", "types", "contextlib", "weakref", "queue", "socket", "ssl", "http",
                "email", "gzip", "csv", "sqlite3", "platform", "operator", "decimal", "fractions", "statistics",
                "bisect", "heapq", "pathlib", "glob", "fnmatch", "pprint", "warnings", "signal", "subprocess",
                "concurrent", "secrets", "hmac", "locale", "calendar", "ctypes", "array", "mimetypes", "xml",
                "codecs", "gettext", "builtins", "importlib", "traceback", "numbers", "colorsys", "difflib",
                "tarfile", "lzma", "bz2", "pickle", "marshal", "atexit", "sched", "selectors", "ipaddress").contains(module);
    }

    /** Наш .marp: manifest.json и main.py с дописанным хвостом. */
    public static byte[] pack(Parsed parsed, String text) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ZipOutputStream zip = new ZipOutputStream(bytes);
        zip.putNextEntry(new ZipEntry("manifest.json"));
        String manifest = "{\n"
                + "  \"id\": \"extera." + escape(parsed.id) + "\",\n"
                + "  \"name\": \"" + escape(parsed.name) + "\",\n"
                + "  \"version\": \"" + escape(parsed.version) + "\",\n"
                + "  \"author\": \"" + escape(parsed.author) + "\",\n"
                + "  \"description\": \"" + escape(parsed.description) + "\"\n"
                + "}\n";
        zip.write(manifest.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
        zip.putNextEntry(new ZipEntry("main.py"));
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.write(TAIL.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
        zip.close();
        return bytes.toByteArray();
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ").replace("\t", " ");
    }

    /**
     * Открыли файл .plugin: если это плагин экстеры, предложить перевод.
     *
     * @return true, если файл наш и окно показано; false — пусть файл
     * открывается как обычно.
     */
    public static boolean offerConvert(Context context, File file) {
        try {
            if (file.length() > LIMIT) {
                return false;
            }
            int length = (int) file.length();
            byte[] data = new byte[length];
            try (FileInputStream in = new FileInputStream(file)) {
                int done = 0;
                while (done < length) {
                    int got = in.read(data, done, length - done);
                    if (got <= 0) {
                        break;
                    }
                    done += got;
                }
            }
            String text = new String(data, StandardCharsets.UTF_8);
            Parsed parsed = parse(text);
            if (parsed == null) {
                return false;
            }
            ask(context, parsed, text);
            return true;
        } catch (Throwable t) {
            FileLog.e(t);
            return false;
        }
    }

    private static void ask(Context context, Parsed parsed, String text) {
        StringBuilder message = new StringBuilder();
        message.append(LocaleController.formatString(R.string.FemboyExteraAbout, parsed.name));
        if (!parsed.missing.isEmpty()) {
            message.append("\n\n").append(LocaleController.getString(R.string.FemboyExteraMissing));
            for (String thing : parsed.missing) {
                message.append("\n• ").append(thing);
            }
        }
        new AlertDialog.Builder(context)
                .setTitle(LocaleController.getString(R.string.FemboyExteraTitle))
                .setMessage(message.toString())
                .setPositiveButton(LocaleController.getString(R.string.FemboyExteraConvert), (dialog, which) -> convert(context, parsed, text))
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .show();
    }

    private static void convert(Context context, Parsed parsed, String text) {
        try {
            FemboyPlugins.askInstall(context, new ByteArrayInputStream(pack(parsed, text)), null);
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }
}
