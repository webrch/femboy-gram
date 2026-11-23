package org.telegram.femboy;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import org.telegram.messenger.ApplicationLoader;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Мост к femboy_music_online.py: поиск и скачивание музыки через yt-dlp.
 *
 * Общая библиотека зовёт этот класс по имени, через отражение (FemboyMusicOnline),
 * потому что питон есть только в этой сборке. Все вызовы долгие: только не
 * с главного потока. Ответ каждого метода — строка JSON.
 */
public class FemboyMusicPython {

    private static PyObject module;

    private static synchronized PyObject module() {
        if (module == null) {
            if (!Python.isStarted()) {
                try {
                    Python.start(new AndroidPlatform(ApplicationLoader.applicationContext));
                } catch (IllegalStateException alreadyStarted) {
                    // плагины успели запустить питон с главного потока
                }
            }
            final PyObject one = Python.getInstance().getModule("femboy_music_online");
            final File cache = new File(ApplicationLoader.applicationContext.getCacheDir(), "yt-dlp");
            one.callAttr("init", cache.getAbsolutePath());
            module = one;
        }
        return module;
    }

    private static String text(PyObject answer) {
        return answer == null ? null : answer.toString();
    }

    public static String search(String source, String query, int limit) {
        return text(module().callAttr("search", source, query, limit));
    }

    public static String related(String source, String id, String url, int limit) {
        return text(module().callAttr("related", source, id, url, limit));
    }

    public static String recommend(String seeds, int limit) {
        return text(module().callAttr("recommend", seeds, limit));
    }

    /** outPath без расширения: питон допишет своё. progress: 0..1000. */
    public static String download(String source, String id, String url, String outPath, AtomicInteger progress) {
        return text(module().callAttr("download", source, id, url, outPath, progress));
    }

    public static String version() {
        return text(module().callAttr("version"));
    }
}
