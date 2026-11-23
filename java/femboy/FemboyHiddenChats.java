package org.telegram.femboy;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

import java.util.HashSet;
import java.util.Set;

/** Storage and reveal state for chats hidden by the owner. */
public final class FemboyHiddenChats {

    private static final String PREFS = "femboy_hidden_chats";
    private static final String KEY_IDS = "ids";
    private static final String KEY_PIN = "pin";

    private static boolean revealed;

    private FemboyHiddenChats() {
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static Set<Long> getHidden() {
        final Set<Long> result = new HashSet<>();
        for (String value : prefs().getStringSet(KEY_IDS, new HashSet<String>())) {
            try {
                result.add(Long.parseLong(value));
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }

    public static boolean hasHidden() {
        return !getHidden().isEmpty();
    }

    public static boolean isHidden(long dialogId) {
        return getHidden().contains(dialogId);
    }

    public static void setHidden(long dialogId, boolean hidden) {
        final Set<String> ids = new HashSet<>();
        for (String value : prefs().getStringSet(KEY_IDS, new HashSet<String>())) {
            ids.add(value);
        }
        final String key = String.valueOf(dialogId);
        if (hidden) {
            ids.add(key);
        } else {
            ids.remove(key);
        }
        prefs().edit().putStringSet(KEY_IDS, ids).apply();
    }

    public static void unhideAll() {
        prefs().edit().putStringSet(KEY_IDS, new HashSet<String>()).apply();
        revealed = false;
    }

    public static String getPin() {
        return prefs().getString(KEY_PIN, "");
    }

    public static void setPin(String pin) {
        prefs().edit().putString(KEY_PIN, pin == null ? "" : pin).apply();
    }

    public static boolean hasPin() {
        return !getPin().isEmpty();
    }

    public static boolean checkPin(String value) {
        return hasPin() && value != null && getPin().equals(value);
    }

    public static boolean isRevealed() {
        return revealed;
    }

    public static void reveal() {
        revealed = true;
    }

    public static void conceal() {
        revealed = false;
    }

    public static void toggleReveal() {
        revealed = !revealed;
    }
}
