package org.telegram.femboy;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Badges are disabled in FemboyGram. The class is kept as an inert adapter so
 * the many call sites in Telegram's own UI keep compiling without any badge
 * logic being present in the app.
 */
public final class FemboyBadge {

    private FemboyBadge() {}

    public static final class Badge {
        public Badge(long peerId, Object remote, int color, Object unused) {}
        public String id() { return ""; }
        public String title() { return ""; }
        public String about() { return ""; }
        public int color() { return 0; }
    }

    public static void refresh() {}
    public static void refreshIfStale() {}
    public static long chatPeer(long id) { return 0; }
    public static boolean has(long peerId) { return false; }
    public static CharSequence withBadge(Context context, CharSequence name, long peerId) { return name; }
    public static float badgeWidth(Paint paint) { return 0; }
    public static float badgeWidth(Paint paint, long peerId) { return 0; }
    public static Drawable iconDrawable(Context context, Badge badge) { return null; }
    public static String title(long peerId) { return null; }
    public static void show(Context context, Badge badge) {}
    public static List<Badge> all(long peerId) { return Collections.emptyList(); }
    public static List<Badge> ordered(long peerId) { return new ArrayList<>(); }
    public static boolean isHidden(long peerId, Badge badge) { return false; }
    public static Badge[] list() { return new Badge[0]; }
    public static void setLocalPrefs(long peerId, String[] order, String[] hidden) {}
}
