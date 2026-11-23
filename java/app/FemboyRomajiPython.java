package org.telegram.femboy;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import org.telegram.messenger.ApplicationLoader;

import java.util.List;

/**
 * Мостик к питону для чтения японского текста латиницей (pykakasi).
 *
 * Как и FemboyPython, живёт в модуле приложения, а общая библиотека зовёт
 * его по имени через отражение. Питон поднимаем сами, если плагины его ещё
 * не подняли. Звать только не с главного потока: первый вызов грузит словари.
 */
public class FemboyRomajiPython {

    private static void ensureStarted() {
        synchronized (Python.class) {
            if (!Python.isStarted()) {
                try {
                    Python.start(new AndroidPlatform(ApplicationLoader.applicationContext));
                } catch (IllegalStateException ignored) {
                    // кто-то успел поднять раньше нас: тоже годится
                }
            }
        }
    }

    /** По строке ответа на каждую строку текста; пустая, если читать нечего. */
    public static String[] romanize(String[] lines) {
        ensureStarted();
        final PyObject module = Python.getInstance().getModule("femboy_romaji");
        final PyObject answer = module.callAttr("romanize", (Object) lines);
        final String[] out = new String[lines.length];
        if (answer == null) return out;
        final List<PyObject> list = answer.asList();
        for (int i = 0; i < out.length && i < list.size(); i++) {
            final PyObject one = list.get(i);
            out[i] = one == null ? "" : one.toString();
        }
        return out;
    }
}
