package org.telegram.ui;

import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/**
 * Геометрия питомца: всё строится из единичной UV-сферы, которую при желании
 * гнут функцией Deform. Вершина = позиция (3) + нормаль (3).
 * Данные в памяти общие, в GL заливаются на каждый контекст отдельно (Gpu).
 */
final class FemboyPetMesh {

    /** Изгиб: точка единичной сферы -> точка фигуры. */
    interface Deform {
        void apply(float x, float y, float z, float[] out);
    }

    static final int STRIDE = 6 * 4;

    final float[] data;
    final short[] index;
    final int mode;

    private FemboyPetMesh(float[] data, short[] index, int mode) {
        this.data = data;
        this.index = index;
        this.mode = mode;
    }

    // ---------- готовые фигуры ----------

    private static FemboyPetMesh sphereHi, sphereLo, loaf, egg, ear, tail, quad, whiskers;

    /** Гладкая сфера для крупных частей (голова, щёки). */
    static synchronized FemboyPetMesh sphereHi() {
        if (sphereHi == null) sphereHi = sphere(32, 44, null);
        return sphereHi;
    }

    /** Мелкая сфера: глаза, нос, морда. */
    static synchronized FemboyPetMesh sphereLo() {
        if (sphereLo == null) sphereLo = sphere(16, 24, null);
        return sphereLo;
    }

    /** Буханка: низ широкий и плоский, чтобы сидела на земле всем пузом. */
    static synchronized FemboyPetMesh loaf() {
        if (loaf == null) loaf = sphere(32, 44, (x, y, z, o) -> {
            o[0] = x;
            float d = y < 0 ? 0.62f * (float) Math.pow(-y, 1.8f) : 0f;
            // мягко срезать низ в плоское пузо
            if (d > 0.44f) d = 0.44f + 0.08f * (float) Math.tanh((d - 0.44f) / 0.08f);
            o[1] = y < 0 ? -d : y;
            o[2] = z;
        });
        return loaf;
    }

    /** Яйцо: верх уже низа, по y от -1.25 до 1.25. */
    static synchronized FemboyPetMesh egg() {
        if (egg == null) egg = sphere(40, 48, (x, y, z, o) -> {
            float k = 1f - 0.16f * y;
            o[0] = x * k;
            o[1] = y * 1.25f;
            o[2] = z * k;
        });
        return egg;
    }

    /** Ухо: широкое у основания (y=-1), к верху круглый кончик, как у тилкайо. */
    static synchronized FemboyPetMesh ear() {
        if (ear == null) ear = sphere(24, 24, (x, y, z, o) -> {
            float h = (y + 1f) * 0.5f;
            float w = 1f - 0.42f * (float) Math.pow(h, 1.6f);
            // полнее в нижней половине, кончик не острый
            w *= 1f + 0.3f * (float) Math.sin(Math.PI * Math.min(1f, h * 1.25f));
            o[0] = x * w;
            o[1] = y;
            o[2] = z * w - 0.1f * h * h;
        });
        return ear;
    }

    /** Хвост: изогнутая колбаска в координатах питомца, вокруг правого бока. */
    static synchronized FemboyPetMesh tail() {
        if (tail == null) tail = sphere(28, 16, (x, y, z, o) -> {
            float t = (y + 1f) * 0.5f;
            float c = (float) Math.sqrt(Math.max(0f, 1f - y * y));
            float k = c > 1e-5f ? (float) Math.pow(c, 0.4f) / c : 0f;
            float cx = x * k, cz = z * k;
            float r = 0.12f * (1f - 0.35f * t);
            double th = Math.toRadians(-80 + 95 * t);
            float R = 0.78f;
            float cy = 0.3f * (1f - t) + 0.12f * t + 0.1f * smooth(0.7f, 1f, t);
            float dx = (float) Math.cos(th), dz = (float) Math.sin(th);
            o[0] = R * dx + dx * cx * r;
            o[1] = cy + cz * r;
            o[2] = R * dz + dz * cx * r;
        });
        return tail;
    }

    /** Квадрат на земле под тень, xz от -1 до 1. */
    static synchronized FemboyPetMesh quad() {
        if (quad == null) {
            float[] d = {
                    -1, 0, -1, 0, 1, 0,
                    1, 0, -1, 0, 1, 0,
                    1, 0, 1, 0, 1, 0,
                    -1, 0, 1, 0, 1, 0,
            };
            quad = new FemboyPetMesh(d, new short[]{0, 2, 1, 0, 3, 2}, GLES20.GL_TRIANGLES);
        }
        return quad;
    }

    /** Усы линиями, в системе головы (до её масштаба). По три с каждой стороны. */
    static synchronized FemboyPetMesh whiskers() {
        if (whiskers == null) {
            float[] d = new float[2 * 3 * 2 * 6];
            int p = 0;
            for (int s = -1; s <= 1; s += 2) {
                for (int k = 0; k < 3; k++) {
                    float[] a = {s * 0.13f, -0.12f - 0.025f * k, 0.44f};
                    float[] b = {s * 0.66f, -0.02f - 0.085f * k, 0.30f - 0.03f * k};
                    for (float[] v : new float[][]{a, b}) {
                        d[p++] = v[0];
                        d[p++] = v[1];
                        d[p++] = v[2];
                        d[p++] = 0;
                        d[p++] = 0;
                        d[p++] = 1;
                    }
                }
            }
            short[] idx = new short[12];
            for (short i = 0; i < 12; i++) idx[i] = i;
            whiskers = new FemboyPetMesh(d, idx, GLES20.GL_LINES);
        }
        return whiskers;
    }

    // ---------- построение ----------

    private static float smooth(float a, float b, float x) {
        float t = Math.max(0f, Math.min(1f, (x - a) / (b - a)));
        return t * t * (3f - 2f * t);
    }

    /**
     * UV-сфера rings x segs. Без изгиба нормаль = точка. С изгибом нормаль
     * считается разностями по двум касательным, так нет шва и полюса не ломаются.
     */
    static FemboyPetMesh sphere(int rings, int segs, Deform deform) {
        int vc = (rings + 1) * (segs + 1);
        float[] d = new float[vc * 6];
        float[] f = new float[3], fa = new float[3], fb = new float[3], fi = new float[3];
        int p = 0;
        final float e = 1e-3f;
        for (int i = 0; i <= rings; i++) {
            double phi = Math.PI * i / rings;
            float sp = (float) Math.sin(phi), cp = (float) Math.cos(phi);
            for (int j = 0; j <= segs; j++) {
                double th = 2 * Math.PI * j / segs;
                float x = sp * (float) Math.cos(th), y = cp, z = sp * (float) Math.sin(th);
                if (i == 0 || i == rings) {
                    x = 0;
                    z = 0;
                }
                float nx, ny, nz;
                if (deform == null) {
                    f[0] = x;
                    f[1] = y;
                    f[2] = z;
                    nx = x;
                    ny = y;
                    nz = z;
                } else {
                    deform.apply(x, y, z, f);
                    // касательный базис к сфере в этой точке
                    float ux = 0, uy = 1, uz = 0;
                    if (Math.abs(y) > 0.99f) {
                        ux = 1;
                        uy = 0;
                    }
                    float t1x = y * uz - z * uy, t1y = z * ux - x * uz, t1z = x * uy - y * ux;
                    float l = len(t1x, t1y, t1z);
                    t1x /= l;
                    t1y /= l;
                    t1z /= l;
                    float t2x = y * t1z - z * t1y, t2y = z * t1x - x * t1z, t2z = x * t1y - y * t1x;
                    onSphere(x + e * t1x, y + e * t1y, z + e * t1z, deform, fa);
                    onSphere(x + e * t2x, y + e * t2y, z + e * t2z, deform, fb);
                    float ax = fa[0] - f[0], ay = fa[1] - f[1], az = fa[2] - f[2];
                    float bx = fb[0] - f[0], by = fb[1] - f[1], bz = fb[2] - f[2];
                    nx = ay * bz - az * by;
                    ny = az * bx - ax * bz;
                    nz = ax * by - ay * bx;
                    // наружу = от внутренней точки к поверхности
                    deform.apply(x * 0.97f, y * 0.97f, z * 0.97f, fi);
                    float ox = f[0] - fi[0], oy = f[1] - fi[1], oz = f[2] - fi[2];
                    if (nx * ox + ny * oy + nz * oz < 0) {
                        nx = -nx;
                        ny = -ny;
                        nz = -nz;
                    }
                    l = len(nx, ny, nz);
                    if (l < 1e-12f) {
                        nx = ox;
                        ny = oy;
                        nz = oz;
                        l = len(nx, ny, nz);
                        if (l < 1e-12f) {
                            nx = 0;
                            ny = 1;
                            nz = 0;
                            l = 1;
                        }
                    }
                    nx /= l;
                    ny /= l;
                    nz /= l;
                }
                d[p++] = f[0];
                d[p++] = f[1];
                d[p++] = f[2];
                d[p++] = nx;
                d[p++] = ny;
                d[p++] = nz;
            }
        }
        short[] idx = new short[rings * segs * 6];
        int q = 0;
        for (int i = 0; i < rings; i++) {
            for (int j = 0; j < segs; j++) {
                int a = i * (segs + 1) + j, b = a + segs + 1, c = a + 1, dd = b + 1;
                // против часовой снаружи
                idx[q++] = (short) a;
                idx[q++] = (short) c;
                idx[q++] = (short) b;
                idx[q++] = (short) c;
                idx[q++] = (short) dd;
                idx[q++] = (short) b;
            }
        }
        return new FemboyPetMesh(d, idx, GLES20.GL_TRIANGLES);
    }

    private static void onSphere(float x, float y, float z, Deform deform, float[] out) {
        float l = len(x, y, z);
        deform.apply(x / l, y / l, z / l, out);
    }

    private static float len(float x, float y, float z) {
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    // ---------- GL ----------

    Gpu upload() {
        int[] ids = new int[2];
        GLES20.glGenBuffers(2, ids, 0);
        FloatBuffer fb = ByteBuffer.allocateDirect(data.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        fb.put(data).position(0);
        ShortBuffer sb = ByteBuffer.allocateDirect(index.length * 2).order(ByteOrder.nativeOrder()).asShortBuffer();
        sb.put(index).position(0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, ids[0]);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, data.length * 4, fb, GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ids[1]);
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, index.length * 2, sb, GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
        return new Gpu(ids[0], ids[1], index.length, mode);
    }

    /** Буферы одной фигуры в текущем GL-контексте. */
    static final class Gpu {
        private int vbo, ibo;
        private final int count, mode;

        Gpu(int vbo, int ibo, int count, int mode) {
            this.vbo = vbo;
            this.ibo = ibo;
            this.count = count;
            this.mode = mode;
        }

        void draw(int aPos, int aNrm) {
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
            GLES20.glEnableVertexAttribArray(aPos);
            GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, STRIDE, 0);
            if (aNrm >= 0) {
                GLES20.glEnableVertexAttribArray(aNrm);
                GLES20.glVertexAttribPointer(aNrm, 3, GLES20.GL_FLOAT, false, STRIDE, 12);
            }
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo);
            GLES20.glDrawElements(mode, count, GLES20.GL_UNSIGNED_SHORT, 0);
        }

        void release() {
            if (vbo != 0) {
                GLES20.glDeleteBuffers(2, new int[]{vbo, ibo}, 0);
                vbo = ibo = 0;
            }
        }
    }
}
