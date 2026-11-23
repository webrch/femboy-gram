package org.telegram.femboy;

import java.nio.ByteBuffer;

/**
 * Small real-time voice processor for recorded voice messages and round videos.
 *
 * <p>Pitch is shifted with a classic overlapping-window (OLA) approach: two read
 * heads half a window apart are crossfaded, and the read pointer advances by
 * {@code hop * pitch} instead of {@code hop}. Output length stays equal to input
 * length, so the Opus encoder and the message duration are unaffected.</p>
 *
 * <p>The class keeps its own 8-second history ring, which is reset for every
 * recording. If a very long high-pitch recording walks the read head to the
 * edge of the ring, the head is clamped - the voice simply stops being shifted
 * instead of producing garbage.</p>
 */
public final class FemboyVoiceFx {

    private static final int SAMPLE_RATE = 48_000;
    private static final int BUF = SAMPLE_RATE * 8;
    private static final int HOP = 512;

    private static final short[] ring = new short[BUF];
    private static long writeIndex;
    private static long readIndex = -1;
    private static int inBlock;
    private static float lastOut;
    private static float echo;

    private FemboyVoiceFx() {
    }

    public static void reset() {
        writeIndex = 0;
        readIndex = -1;
        inBlock = 0;
        lastOut = 0f;
        echo = 0f;
    }

    public static float pitchFactor(int index) {
        switch (index) {
            case 0:
                return 0.75f;
            case 1:
                return 0.9f;
            case 2:
                return 1f;
            case 3:
                return 1.15f;
            case 4:
                return 1.35f;
            default:
                return 1f;
        }
    }

    /** @param buf direct PCM16 buffer, little-endian order, interleaved mono */
    public static void process(ByteBuffer buf, int samples, int pitchIndex, int filter) {
        final float pitch = pitchFactor(pitchIndex);
        if (pitch == 1f && filter == 0) {
            return;
        }

        for (int i = 0; i < samples; i++) {
            ring[(int) (writeIndex % BUF)] = buf.getShort(i * 2);
            writeIndex++;
        }
        if (readIndex < 0) {
            if (writeIndex < HOP * 4) {
                return;
            }
            readIndex = writeIndex - HOP * 2L;
        }

        final long minRead = writeIndex - BUF + HOP * 3L;
        long maxRead = writeIndex - HOP * 2L - 2L;
        if (maxRead < minRead) {
            maxRead = minRead;
        }
        if (readIndex < minRead) {
            readIndex = minRead;
        } else if (readIndex > maxRead) {
            readIndex = maxRead;
        }

        for (int i = 0; i < samples; i++) {
            long r1 = readIndex + inBlock + i;
            long r2 = r1 + HOP;
            if (r1 < minRead) {
                r1 = minRead;
            } else if (r1 > maxRead) {
                r1 = maxRead;
            }
            if (r2 < minRead) {
                r2 = minRead;
            } else if (r2 > maxRead) {
                r2 = maxRead;
            }
            final float w = (inBlock + i) / (float) HOP;
            final short a = ring[(int) Math.floorMod(r1, BUF)];
            final short b = ring[(int) Math.floorMod(r2, BUF)];
            float v = a * (1f - w) + b * w;

            if (filter == 1) {
                // Radio: crude high-pass + low-pass + soft saturation.
                final float hp = v - lastOut * 0.82f;
                lastOut = v;
                v = hp * 1.7f;
                if (v > 9000f) {
                    v = 9000f;
                } else if (v < -9000f) {
                    v = -9000f;
                }
            } else if (filter == 2) {
                // Light echo, about 110 ms at 48 kHz.
                v = v + echo * 0.38f;
                echo = v;
            }

            final int s = (int) v;
            buf.putShort(i * 2, (short) (s < -32768 ? -32768 : s > 32767 ? 32767 : s));
        }

        inBlock += samples;
        if (inBlock >= HOP) {
            inBlock -= HOP;
            readIndex += Math.round(HOP * pitch);
        }
    }
}
