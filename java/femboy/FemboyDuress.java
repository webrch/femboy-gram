package org.telegram.femboy;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;

/**
 * Duress code for the app passcode screen.
 *
 * Entering this code instead of the real passcode logs out every authorized
 * account. The code is only consulted by PasscodeView while unlocking, so it
 * cannot fire while the owner is setting a new passcode.
 */
public final class FemboyDuress {

    private static final String PREFS = "femboy_duress";
    private static final String KEY_CODE = "code";

    private FemboyDuress() {
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String get() {
        return prefs().getString(KEY_CODE, "");
    }

    public static void set(String code) {
        prefs().edit().putString(KEY_CODE, code == null ? "" : code).apply();
    }

    public static boolean isSet() {
        return !get().isEmpty();
    }

    public static boolean matches(String code) {
        return isSet() && code != null && get().equals(code);
    }

    /** Called from the unlock screen when the duress code was entered. */
    public static void logoutEverywhere() {
        final int[] accounts = new int[UserConfig.MAX_ACCOUNT_COUNT];
        int count = 0;
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated()) {
                accounts[count++] = a;
            }
        }
        for (int i = 0; i < count; i++) {
            try {
                MessagesController.getInstance(accounts[i]).performLogout(1);
            } catch (Throwable ignored) {
            }
        }
    }
}
