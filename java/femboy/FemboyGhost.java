package org.telegram.femboy;

/**
 * Ghost mode: read receipts, online status and typing are hidden from the
 * server while the client keeps working normally.
 *
 * The one-shot read allowance exists for the rare case when the user explicitly
 * wants a single chat to be marked as read (for example after sending a message).
 */
public final class FemboyGhost {

    private FemboyGhost() {}

    private static boolean allowRead;
    private static int allowReadLeft;

    public static synchronized void allowNextRead(int times) {
        allowRead = true;
        allowReadLeft = Math.max(1, times);
    }

    public static synchronized boolean consumeReadAllowance() {
        if (!allowRead) {
            return false;
        }
        allowReadLeft--;
        if (allowReadLeft <= 0) {
            allowRead = false;
            allowReadLeft = 0;
        }
        return true;
    }

    public static boolean active() {
        return FemboyConfig.ghostMode();
    }

    public static boolean hidesRead() {
        return FemboyConfig.ghostHidesRead();
    }

    public static boolean hidesOnline() {
        return FemboyConfig.ghostHidesOnline();
    }

    public static boolean hidesTyping() {
        return FemboyConfig.ghostHidesTyping();
    }

    public static boolean markReadAfterSend() {
        return FemboyConfig.ghostMarkReadAfterSend();
    }
}
