package org.telegram.femboy;

import android.graphics.drawable.Drawable;

import java.util.Collections;
import java.util.List;

/** Badge import from other forks is disabled in FemboyGram. */
public final class FemboyOtherGram {

    private FemboyOtherGram() {}

    public static boolean enabled() { return false; }
    public static void setEnabled(boolean on) {}
    public static List<FemboyBadge.Badge> of(long peerId) { return Collections.emptyList(); }
    public static boolean is(FemboyBadge.Badge badge) { return false; }
    public static Drawable icon(FemboyBadge.Badge badge) { return null; }
    public static void redraw() {}
}
