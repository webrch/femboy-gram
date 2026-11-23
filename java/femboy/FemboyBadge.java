package org.telegram.femboy;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Badges shown next to names. The list lives in badges.json in the repository,
 * so supporters can be added without rebuilding the app. A small built-in copy
 * is kept for the first launch / offline case.
 */
public final class FemboyBadge {

    private FemboyBadge() {}

    public static final class Badge {
        public final long peerId;
        private final int titleRes;
        private final int aboutRes;
        private final JSONObject remote;
        public final int color;
        public final String url;
        private String builtInId = "";

        Badge(String id, long peerId, int titleRes, int aboutRes, int color, String url) {
            this(peerId, titleRes, aboutRes, color, url);
            this.builtInId = id;
        }

        Badge(long peerId, int titleRes, int aboutRes, int color, String url) {
            this.peerId = peerId;
            this.titleRes = titleRes;
            this.aboutRes = aboutRes;
            this.remote = null;
            this.color = color;
            this.url = url;
        }

        Badge(long peerId, JSONObject remote, int color, String url) {
            this.peerId = peerId;
            this.titleRes = 0;
            this.aboutRes = 0;
            this.remote = remote;
            this.color = color;
            this.url = url;
        }

        public String id() {
            if (remote != null) {
                final String id = remote.optString("id", "");
                if (!id.isEmpty()) return id;
                if ("supporter".equals(remote.optString("kind", ""))) return "supporter";
                return remote.optString("title", "").toLowerCase().replace(' ', '_');
            }
            return builtInId;
        }

        public String title() {
            return remote != null
                    ? FemboyRemote.localized(remote, "title", "")
                    : LocaleController.getString(titleRes);
        }

        public String about() {
            return remote != null
                    ? FemboyRemote.localized(remote, "about", "")
                    : LocaleController.getString(aboutRes);
        }

        JSONObject remote() {
            return remote;
        }
    }

    /** Built-in fallback, mirrors badges.json in the repository. */
    private static final Badge[] BUILT_IN = {
            new Badge("owner", 6536442376L, R.string.FemboyBadgeOwnerTitle, R.string.FemboyBadgeOwnerAbout,
                    0xFFFF9ECD, "https://t.me/femboygm"),
            new Badge("owner", 9051996412L, R.string.FemboyBadgeOwnerTitle, R.string.FemboyBadgeOwnerAbout,
                    0xFFFF9ECD, "https://t.me/femboygm"),
            new Badge("friend", 6497351887L, R.string.FemboyBadgeFriendTitle, R.string.FemboyBadgeFriendAbout,
                    0xFFC9A6FF, null),
            new Badge("channel", -1004414542364L, R.string.FemboyBadgeChannelTitle, R.string.FemboyBadgeChannelAbout,
                    0xFF9FD0FF, "https://t.me/femboygm"),
            new Badge("channel", -1004353739064L, R.string.FemboyBadgeChannelTitle, R.string.FemboyBadgeChannelAbout,
                    0xFF9FD0FF, "https://t.me/femboyconfs"),
    };

    private static final String FILE = "badges.json";
    private static final String CACHE_KEY = "badges";
    private static final long MAX_AGE = 5 * 60 * 1000L;

    private static Badge[] parsed;
    private static String parsedLanguage;
    private static Map<Long, String[][]> serverPrefs = new HashMap<>();
    private static final Map<Long, String[][]> localPrefs = new HashMap<>();

    /** Re-read badges.json from the repository. Safe to call often. */
    public static void refresh() {
        FemboyRemote.fetch(FILE, CACHE_KEY, text -> {
            if (text == null) return;
            parsed = null;
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                if (UserConfig.getInstance(a).isClientActivated()) {
                    NotificationCenter.getInstance(a).postNotificationName(
                            NotificationCenter.updateInterfaces,
                            org.telegram.messenger.MessagesController.UPDATE_MASK_NAME);
                }
            }
        });
    }

    public static void refreshIfStale() {
        if (System.currentTimeMillis() - FemboyRemote.cachedAt(CACHE_KEY) > MAX_AGE) {
            refresh();
        }
    }

    private static Badge[] badges() {
        final String language = language();
        if (parsed != null && equal(parsedLanguage, language)) return parsed;
        final String text = FemboyRemote.cached(CACHE_KEY);
        if (text == null) return BUILT_IN;
        final Badge[] fromFile = parse(text);
        if (fromFile == null || fromFile.length == 0) return BUILT_IN;
        parsed = fromFile;
        parsedLanguage = language;
        return parsed;
    }

    private static String language() {
        try {
            return LocaleController.getInstance().getCurrentLocale().getLanguage();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean equal(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private static Badge[] parse(String text) {
        final List<Badge> out = new ArrayList<>();
        final Map<Long, String[][]> prefs = new HashMap<>();
        try {
            final JSONArray array = new JSONArray(text);
            for (int i = 0; i < array.length(); i++) {
                try {
                    final JSONObject item = array.getJSONObject(i);
                    if (item.has("prefs")) {
                        prefs.put(item.getLong("prefs"),
                                new String[][]{strings(item.optJSONArray("order")), strings(item.optJSONArray("hidden"))});
                        continue;
                    }
                    final long peer = item.getLong("peer");
                    final String url = item.optString("url", null);
                    out.add(new Badge(peer, item, color(item.optString("color", null)),
                            url == null || url.isEmpty() ? null : url));
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        serverPrefs = prefs;
        return out.toArray(new Badge[0]);
    }

    private static String[] strings(JSONArray array) {
        if (array == null) return new String[0];
        final String[] out = new String[array.length()];
        for (int i = 0; i < out.length; i++) out[i] = array.optString(i, "");
        return out;
    }

    private static int color(String value) {
        if (value == null) return 0xFFFF9ECD;
        String hex = value.trim();
        if (hex.startsWith("#")) hex = hex.substring(1);
        try {
            final long parsedColor = Long.parseLong(hex, 16);
            return hex.length() <= 6 ? (int) (0xFF000000L | parsedColor) : (int) parsedColor;
        } catch (Exception ignored) {
            return 0xFFFF9ECD;
        }
    }

    public static long chatPeer(long chatId) {
        return -chatId;
    }

    public static void setLocalPrefs(long peerId, String[] order, String[] hidden) {
        localPrefs.put(peerId, new String[][]{order, hidden});
    }

    private static String[][] prefsOf(long peerId) {
        final String[][] local = localPrefs.get(peerId);
        return local != null ? local : serverPrefs.get(peerId);
    }

    public static List<Badge> raw(long peerId) {
        final List<Badge> found = new ArrayList<>();
        for (Badge badge : badges()) {
            if (badge.peerId == peerId) {
                boolean dup = false;
                for (Badge b : found) if (b.id().equals(badge.id())) dup = true;
                if (!dup) found.add(badge);
            }
        }
        return found;
    }

    public static boolean isHidden(long peerId, Badge badge) {
        final String[][] p = prefsOf(peerId);
        if (p == null) return false;
        for (String h : p[1]) if (h.equals(badge.id())) return true;
        return false;
    }

    public static List<Badge> ordered(long peerId) {
        final List<Badge> found = raw(peerId);
        final String[][] p = prefsOf(peerId);
        if (p == null) return found;
        final List<String> order = Arrays.asList(p[0]);
        Collections.sort(found, (a, b) -> {
            int ia = order.indexOf(a.id()), ib = order.indexOf(b.id());
            if (ia < 0) ia = Integer.MAX_VALUE;
            if (ib < 0) ib = Integer.MAX_VALUE;
            return Integer.compare(ia, ib);
        });
        return found;
    }

    public static Badge[] list() {
        final List<Badge> kinds = new ArrayList<>();
        for (Badge badge : badges()) {
            boolean seen = false;
            for (Badge already : kinds) {
                if (already.title().equals(badge.title())) { seen = true; break; }
            }
            if (!seen) kinds.add(badge);
        }
        return kinds.toArray(new Badge[0]);
    }

    public static Badge of(long peerId) {
        final List<Badge> all = all(peerId);
        return all.isEmpty() ? null : all.get(0);
    }

    public static List<Badge> all(long peerId) {
        final List<Badge> found = new ArrayList<>();
        if (!FemboyConfig.badgesEnabled()) return found;
        for (Badge badge : ordered(peerId)) {
            if (!isHidden(peerId, badge)) found.add(badge);
        }
        return found;
    }

    public static boolean has(long peerId) {
        return of(peerId) != null;
    }

    public static Drawable iconDrawable(Context context, long peerId) {
        return iconDrawable(context, of(peerId));
    }

    public static CharSequence withBadge(Context context, CharSequence name, long peerId) {
        if (name == null || peerId == 0) return name;
        final List<Badge> all = all(peerId);
        if (all.isEmpty()) return name;
        final android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder(name);
        for (Badge badge : shown(all)) {
            final Drawable icon = iconDrawable(context, badge);
            if (icon == null) continue;
            final int at = sb.length();
            sb.append(" \u2060");
            sb.setSpan(new BadgeSpan(icon), at + 1, at + 2, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return sb;
    }

    public static float badgeWidth(Paint paint) {
        return paint.getTextSize() * 0.95f + paint.measureText(" ");
    }

    public static float badgeWidth(Paint paint, long peerId) {
        return badgeWidth(paint) * Math.max(1, shown(all(peerId)).size());
    }

    private static List<Badge> shown(List<Badge> all) {
        final List<Badge> out = new ArrayList<>();
        if (!all.isEmpty()) out.add(all.get(0));
        return out;
    }

    public static final class BadgeSpan extends android.text.style.ReplacementSpan {
        private final Drawable icon;

        BadgeSpan(Drawable icon) {
            this.icon = icon;
        }

        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            return Math.round(paint.getTextSize() * 0.95f);
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end, float x,
                         int top, int y, int bottom, Paint paint) {
            final int size = Math.round(paint.getTextSize() * 0.95f);
            final Paint.FontMetricsInt fm = paint.getFontMetricsInt();
            final int center = y + (fm.ascent + fm.descent) / 2;
            icon.setBounds((int) x, center - size / 2, (int) x + size, center - size / 2 + size);
            icon.setAlpha(paint.getAlpha());
            icon.draw(canvas);
        }
    }

    public static Drawable iconDrawable(Context context, Badge badge) {
        if (context == null || badge == null) return null;
        try {
            final GradientDrawable field = new GradientDrawable();
            field.setShape(GradientDrawable.RECTANGLE);
            field.setCornerRadius(AndroidUtilities.dp(24) * 5f / 24f);
            field.setColor(badge.color);
            final Drawable heart = ContextCompat.getDrawable(context, R.drawable.femboy_badge_heart);
            if (heart == null) return field;
            return new LayerDrawable(new Drawable[]{field, heart});
        } catch (Throwable t) {
            return null;
        }
    }

    public static String title(long peerId) {
        final Badge badge = of(peerId);
        return badge == null ? null : badge.title();
    }

    public static void show(Context context, long peerId) {
        show(context, of(peerId));
    }

    public static void show(Context context, Badge badge) {
        if (context == null || badge == null) return;
        try {
            final LinearLayout layout = new LinearLayout(context);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setGravity(Gravity.CENTER_HORIZONTAL);

            final ImageView icon = new ImageView(context);
            icon.setImageDrawable(iconDrawable(context, badge));
            layout.addView(icon, LayoutHelper.createLinear(72, 72, Gravity.CENTER_HORIZONTAL, 0, 4, 0, 12));

            final TextView title = new TextView(context);
            title.setText(badge.title());
            title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 18);
            title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            layout.addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

            final TextView about = new TextView(context);
            about.setText(badge.about());
            about.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 14);
            about.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            layout.addView(about, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 4, 0, 0));

            final AlertDialog.Builder builder = new AlertDialog.Builder(context)
                    .setView(layout)
                    .setTitle(badge.title())
                    .setNegativeButton(LocaleController.getString(R.string.Close), null);
            if (badge.url != null && !badge.url.isEmpty()) {
                builder.setPositiveButton(LocaleController.getString(R.string.OpenLink),
                        (d, w) -> Browser.openUrl(context, badge.url));
            }
            builder.show();
        } catch (Exception ignored) {
        }
    }
}
