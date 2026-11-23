package com.exteragram.messenger.utils;

import com.google.gson.Gson;

import org.telegram.messenger.FileLog;

/** То из AppUtils экстеры, чем пользуются плагины. */
public final class AppUtils {
    private AppUtils() {}

    private static Gson gson;

    public static Gson getGson() {
        if (gson == null) gson = new Gson();
        return gson;
    }

    public static void log(Throwable error) {
        FileLog.e(error);
    }

    public static void log(String text) {
        FileLog.d(text);
    }
}
