package org.telegram.femboy;

import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * Avatar shapes, the same ten as in FemboyT: circle, rounded square, square, cookie, clover,
 * burst, sun, pentagon, hexagon, heart. Every round avatar in the app (people and ordinary
 * chats; forums keep their own rounded squares) is cut to the chosen one where
 * ImageReceiver draws it.
 */
public final class FemboyAvatars {

    private FemboyAvatars() {}

    public static final String[] ALL = {"circle", "rounded", "square", "cookie", "clover", "burst", "sun",
            "pentagon", "hexagon", "heart"};
    private static final int POINTS = 144;
    private static final Path[] UNIT = new Path[ALL.length];

    private static int chosen = -1;

    /** Set while an avatar is drawn in a shape: AvatarDrawable fills the whole square then. */
    public static boolean drawing;

    public static int chosen() {
        if (chosen < 0) {
            chosen = Math.max(0, Math.min(ALL.length - 1, FemboyConfig.prefs().getInt("avatar_shape", 0)));
        }
        return chosen;
    }

    public static void choose(int shape) {
        chosen = Math.max(0, Math.min(ALL.length - 1, shape));
        FemboyConfig.prefs().edit().putInt("avatar_shape", chosen).apply();
    }

    /** A shape other than the circle is chosen. */
    public static boolean shaped() {
        return chosen() != 0;
    }

    private static final Path placed = new Path();
    private static final Matrix place = new Matrix();

    /** The chosen shape filling this square, for a canvas to be clipped by. Main thread only. */
    public static Path path(float x, float y, float w, float h) {
        placed.rewind();
        place.setScale(w / 2f, h / 2f);
        place.postTranslate(x + w / 2f, y + h / 2f);
        placed.addPath(unit(chosen()), place);
        return placed;
    }

    /** A shape in a square two across, centred on nothing. Built once. */
    public static Path unit(int shape) {
        Path known = UNIT[shape];
        if (known != null) return known;
        Path made = new Path();
        switch (ALL[shape]) {
            case "rounded":
                made.addRoundRect(new RectF(-1, -1, 1, 1), 0.44f, 0.44f, Path.Direction.CW);
                break;
            case "square":
                made.addRect(-1, -1, 1, 1, Path.Direction.CW);
                break;
            case "heart":
                made.arcTo(new RectF(-1f, -1f, 0.1f, 0.1f), -35.1f, -174.9f, true);
                made.cubicTo(-0.70f, 0.25f, -0.28f, 0.70f, 0f, 1f);
                made.cubicTo(0.28f, 0.70f, 0.70f, 0.25f, 0.926f, -0.175f);
                made.arcTo(new RectF(-0.1f, -1f, 1f, 0.1f), 30f, -174.9f, false);
                made.close();
                break;
            case "circle":
                made.addCircle(0, 0, 1, Path.Direction.CW);
                break;
            default:
                for (int i = 0; i < POINTS; i++) {
                    double around = 2 * Math.PI * i / POINTS - Math.PI / 2;
                    float reach = reach(ALL[shape], around + Math.PI / 2);
                    float px = (float) (reach * Math.cos(around)), py = (float) (reach * Math.sin(around));
                    if (i == 0) made.moveTo(px, py);
                    else made.lineTo(px, py);
                }
                made.close();
        }
        UNIT[shape] = made;
        return made;
    }

    private static float reach(String shape, double around) {
        switch (shape) {
            case "cookie": return 0.92f + 0.08f * (float) Math.cos(9 * around);
            case "clover": return 0.84f + 0.16f * (float) Math.cos(4 * around + Math.PI);
            case "burst": return 0.90f + 0.10f * (float) Math.cos(12 * around);
            case "sun": return 0.87f + 0.13f * (float) Math.cos(8 * around);
            case "pentagon": return sided(5, around);
            case "hexagon": return sided(6, around);
            default: return 1f;
        }
    }

    private static float sided(int sides, double around) {
        double slice = 2 * Math.PI / sides;
        double within = around % slice;
        if (within < 0) within += slice;
        double flat = Math.cos(Math.PI / sides) / Math.cos(within - slice / 2);
        double cap = 0.93, sharp = 36.0;
        double soft = -Math.log(Math.exp(-sharp * flat) + Math.exp(-sharp * cap)) / sharp;
        return (float) (soft / cap);
    }

    private static final Path ringPath = new Path();
    private static final android.graphics.PathMeasure measure = new android.graphics.PathMeasure();

    /** The story ring in the chosen shape, around `rect`. */
    public static void ring(android.graphics.Canvas canvas, RectF rect, android.graphics.Paint paint) {
        canvas.drawPath(path(rect.left, rect.top, rect.width(), rect.height()), paint);
    }

    /**
     * A piece of the ring, by the angles Android's drawArc takes (0 at three o'clock, clockwise).
     * The shapes start at twelve o'clock, so the angles are turned a quarter back.
     */
    public static void segment(android.graphics.Canvas canvas, RectF rect, android.graphics.Paint paint, float startAngle, float endAngle) {
        Path whole = path(rect.left, rect.top, rect.width(), rect.height());
        measure.setPath(whole, true);
        float length = measure.getLength();
        float from = (((startAngle + 90f) % 360f) + 360f) % 360f / 360f * length;
        float sweep = Math.max(0f, Math.min(360f, endAngle - startAngle)) / 360f * length;
        ringPath.rewind();
        if (from + sweep <= length) {
            measure.getSegment(from, from + sweep, ringPath, true);
        } else {
            measure.getSegment(from, length, ringPath, true);
            measure.getSegment(0, from + sweep - length, ringPath, true);
        }
        canvas.drawPath(ringPath, paint);
    }
}
