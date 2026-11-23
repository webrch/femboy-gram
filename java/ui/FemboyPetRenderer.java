package org.telegram.ui;

import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Choreographer;

import org.telegram.messenger.FileLog;

import java.util.Random;

/**
 * Рендер тилкайо. Свой поток с EGL поверх SurfaceTexture от TextureView,
 * кадры по Choreographer этого потока (vsync, без лишних кадров).
 * Вид передаёт сюда ввод через синхронизированные поля, всё остальное
 * живёт только в GL-потоке.
 */
final class FemboyPetRenderer {

    private static final float HOME_YAW = 0.55f; // три четверти спереди
    // тёплая охра, как у толстого тилкайо на фото, не песок
    private static final float TAN = 0.71f, TAN_G = 0.5f, TAN_B = 0.27f;

    // ---------- ввод от вида (под lock) ----------
    private final Object lock = new Object();
    private int inStage = FemboyPetView.STAGE_EGG;
    private float inAccR = 1f, inAccG = 0.55f, inAccB = 0.24f;
    private int inCheer;
    private boolean inTouching;
    private float inDragYaw, inDragTilt;
    private float inFling;
    private boolean inFlingSet;

    private volatile boolean running;
    /** Сколько кадров в секунду, когда питомец просто сидит. 0 = без ограничения. */
    private volatile int idleFps;
    private Session session;
    private final float density;

    FemboyPetRenderer(float density) {
        this.density = density;
    }

    void setStage(int stage) {
        synchronized (lock) {
            inStage = Math.max(FemboyPetView.STAGE_EGG, Math.min(FemboyPetView.STAGE_ADULT, stage));
        }
    }

    void setAccent(int color) {
        synchronized (lock) {
            inAccR = ((color >> 16) & 0xff) / 255f;
            inAccG = ((color >> 8) & 0xff) / 255f;
            inAccB = (color & 0xff) / 255f;
        }
    }

    void cheer() {
        synchronized (lock) {
            inCheer++;
        }
    }

    void setTouching(boolean touching) {
        synchronized (lock) {
            inTouching = touching;
        }
    }

    void drag(float yaw, float tilt) {
        synchronized (lock) {
            inDragYaw += yaw;
            inDragTilt += tilt;
        }
    }

    void fling(float yawVelocity) {
        synchronized (lock) {
            inFling = yawVelocity;
            inFlingSet = true;
        }
    }

    // ---------- жизненный цикл (UI-поток) ----------

    void start(SurfaceTexture st, int w, int h) {
        stop();
        session = new Session(st, w, h);
    }

    void resize(int w, int h) {
        if (session != null) session.resize(w, h);
    }

    /** Освобождает GL и саму SurfaceTexture в своём потоке. */
    void stop() {
        if (session != null) {
            session.finish();
            session = null;
        }
    }

    void setIdleFps(int fps) {
        idleFps = Math.max(0, fps);
    }

    void setRunning(boolean run) {
        if (running == run) return;
        running = run;
        if (session != null) session.wake();
    }

    // ---------- сцена: состояние анимаций (GL-поток) ----------

    private final Random rnd = new Random();
    private double time;
    private long lastNanos;
    private int shownStage = -1;
    private int phase; // 0 покой, 1 исчезает, 2 появляется
    private float phaseT;
    private int seenCheer;
    private float cheerT = -1f;
    private float yaw = HOME_YAW, yawVel, tilt, idleT;
    private double nextBlink = 2, blinkStart = -10, nextTwitch = 4, twitchStart = -10;
    private boolean twitchLeft;
    private float accR = 1f, accG = 0.55f, accB = 0.24f;

    /** Размеры частей для взрослого и малыша. */
    private static final class Pose {
        float bodyY, bodyZ, bodyRx, bodyRy, bodyRz;
        float headY, headZ, headRx, headRy, headRz;
        float cheekX, cheekR;
        float eyeX, eyeY, eyeZ, eyeRx, eyeRy, eyeRz, pupil;
        float earX, earY, earTilt, earW, earLen, earT;
        float pawFx, pawFz, pawBx, pawBz, pawRx, pawRy, pawRz;
        float hipX, hipY, hipZ, hipRx, hipRy, hipRz;
        float tail, scale, shadowX, shadowZ, jump;
    }

    private static final Pose ADULT = new Pose(), BABY = new Pose();

    static {
        // взрослый: круглая толстая буханка, сидит низко, голова шаром спереди
        Pose a = ADULT;
        a.bodyY = 0.34f; a.bodyZ = -0.12f; a.bodyRx = 0.8f; a.bodyRy = 0.6f; a.bodyRz = 0.88f;
        a.headY = 0.93f; a.headZ = 0.5f; a.headRx = 0.5f; a.headRy = 0.45f; a.headRz = 0.45f;
        a.cheekX = 0.27f; a.cheekR = 0.23f;
        a.eyeX = 0.185f; a.eyeY = 0.05f; a.eyeZ = 0.375f; a.eyeRx = 0.12f; a.eyeRy = 0.125f; a.eyeRz = 0.075f; a.pupil = 0.5f;
        a.earX = 0.25f; a.earY = 0.27f; a.earTilt = 13f; a.earW = 0.2f; a.earLen = 0.42f; a.earT = 0.06f;
        a.pawFx = 0.21f; a.pawFz = 0.42f; a.pawBx = 0.52f; a.pawBz = -0.08f; a.pawRx = 0.115f; a.pawRy = 0.08f; a.pawRz = 0.17f;
        a.hipX = 0.5f; a.hipY = 0.26f; a.hipZ = -0.36f; a.hipRx = 0.32f; a.hipRy = 0.3f; a.hipRz = 0.42f;
        a.tail = 1.08f; a.scale = 1f; a.shadowX = 1f; a.shadowZ = 1.12f; a.jump = 0.42f;

        // малыш: голова крупнее, глаза больше, тело короче
        Pose b = BABY;
        b.bodyY = 0.28f; b.bodyZ = -0.04f; b.bodyRx = 0.6f; b.bodyRy = 0.48f; b.bodyRz = 0.62f;
        b.headY = 0.86f; b.headZ = 0.24f; b.headRx = 0.54f; b.headRy = 0.49f; b.headRz = 0.49f;
        b.cheekX = 0.27f; b.cheekR = 0.23f;
        b.eyeX = 0.195f; b.eyeY = 0.03f; b.eyeZ = 0.4f; b.eyeRx = 0.15f; b.eyeRy = 0.16f; b.eyeRz = 0.085f; b.pupil = 0.56f;
        b.earX = 0.25f; b.earY = 0.28f; b.earTilt = 17f; b.earW = 0.18f; b.earLen = 0.32f; b.earT = 0.055f;
        b.pawFx = 0.16f; b.pawFz = 0.32f; b.pawBx = 0.4f; b.pawBz = -0.02f; b.pawRx = 0.095f; b.pawRy = 0.07f; b.pawRz = 0.14f;
        b.hipX = 0.37f; b.hipY = 0.21f; b.hipZ = -0.24f; b.hipRx = 0.25f; b.hipRy = 0.24f; b.hipRz = 0.3f;
        b.tail = 0.72f; b.scale = 0.92f; b.shadowX = 0.74f; b.shadowZ = 0.78f; b.jump = 0.5f;
    }

    // ---------- GL-поток ----------

    private final class Session implements Choreographer.FrameCallback {
        private final HandlerThread thread;
        private final Handler handler;
        private final SurfaceTexture st;
        private Choreographer choreo;
        private boolean scheduled, dead;
        private int width, height;
        private long lastDraw;

        private EGLDisplay dpy = EGL14.EGL_NO_DISPLAY;
        private EGLContext ctx = EGL14.EGL_NO_CONTEXT;
        private EGLSurface surf = EGL14.EGL_NO_SURFACE;

        private int prog, shProg;
        private int aPos, aNrm, uMVP, uModel, uNrm, uPart, uMat, uVar, uColor, uAccent, uCam, uView, uCrack, uClip, uEyeL, uEyeR, uHeadInv, uBodyC, uBodyR, uShell;
        private int sPos, sMVP, sAlpha;
        private FemboyPetMesh.Gpu gSphereHi, gSphereLo, gLoaf, gEgg, gEar, gTail, gQuad, gWhiskers;
        private float lineWidth = 1f;

        private final float[] proj = new float[16], view = new float[16], viewProj = new float[16];
        private final float[] world = new float[16], model = new float[16], mvp = new float[16];
        private final float[] nrm = new float[16], tmp = new float[16], save = new float[16];
        private final float[] part = new float[16], head = new float[16], egg = new float[16];
        private final float[] eyeL = new float[16], eyeR = new float[16], headInv = new float[16];
        private final float[] cam = new float[3];
        private final float[] v4 = new float[4], o4 = {0, 0, 0, 1};

        Session(SurfaceTexture st, int w, int h) {
            this.st = st;
            width = w;
            height = h;
            thread = new HandlerThread("FemboyPet");
            thread.start();
            handler = new Handler(thread.getLooper());
            handler.post(this::init);
        }

        void resize(int w, int h) {
            handler.post(() -> {
                width = w;
                height = h;
            });
        }

        void wake() {
            handler.post(this::schedule);
        }

        void finish() {
            handler.post(() -> {
                if (choreo != null) choreo.removeFrameCallback(this);
                scheduled = false;
                if (!dead) releaseGl();
                dead = true;
                releaseEgl();
                st.release();
                thread.quitSafely();
            });
        }

        private void init() {
            choreo = Choreographer.getInstance();
            try {
                if (!initEgl() || !initGl()) {
                    dead = true;
                    return;
                }
            } catch (Throwable e) {
                FileLog.e(e);
                dead = true;
                return;
            }
            schedule();
        }

        private void schedule() {
            if (dead || choreo == null || scheduled || !running) return;
            scheduled = true;
            lastNanos = 0;
            choreo.postFrameCallback(this);
        }

        @Override
        public void doFrame(long nanos) {
            scheduled = false;
            if (dead || !running) return;
            // в покое реже: пропускаем кадр, если с прошлого прошло мало
            final int fps = idleFps;
            if (fps > 0 && lastDraw != 0 && !busy() && nanos - lastDraw < 1_000_000_000L / fps - 4_000_000L) {
                scheduled = true;
                choreo.postFrameCallback(this);
                return;
            }
            lastDraw = nanos;
            try {
                drawFrame(nanos);
            } catch (Throwable e) {
                FileLog.e(e);
                dead = true;
                return;
            }
            scheduled = true;
            choreo.postFrameCallback(this);
        }

        // ---------- EGL ----------

        private boolean initEgl() {
            dpy = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            int[] ver = new int[2];
            if (dpy == EGL14.EGL_NO_DISPLAY || !EGL14.eglInitialize(dpy, ver, 0, ver, 1)) {
                FileLog.e("FemboyPet: eglInitialize failed");
                return false;
            }
            EGLConfig config = chooseConfig(true);
            if (config == null) config = chooseConfig(false);
            if (config == null) {
                FileLog.e("FemboyPet: no EGL config");
                return false;
            }
            ctx = EGL14.eglCreateContext(dpy, config, EGL14.EGL_NO_CONTEXT,
                    new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE}, 0);
            if (ctx == null || ctx == EGL14.EGL_NO_CONTEXT) {
                FileLog.e("FemboyPet: eglCreateContext failed");
                ctx = EGL14.EGL_NO_CONTEXT;
                return false;
            }
            surf = EGL14.eglCreateWindowSurface(dpy, config, st, new int[]{EGL14.EGL_NONE}, 0);
            if (surf == null || surf == EGL14.EGL_NO_SURFACE) {
                FileLog.e("FemboyPet: eglCreateWindowSurface failed");
                surf = EGL14.EGL_NO_SURFACE;
                return false;
            }
            return EGL14.eglMakeCurrent(dpy, surf, surf, ctx);
        }

        private EGLConfig chooseConfig(boolean msaa) {
            int[] attr = msaa ? new int[]{
                    EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                    EGL14.EGL_DEPTH_SIZE, 16, EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_SAMPLE_BUFFERS, 1, EGL14.EGL_SAMPLES, 4, EGL14.EGL_NONE
            } : new int[]{
                    EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                    EGL14.EGL_DEPTH_SIZE, 16, EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_NONE
            };
            EGLConfig[] configs = new EGLConfig[1];
            int[] num = new int[1];
            if (!EGL14.eglChooseConfig(dpy, attr, 0, configs, 0, 1, num, 0) || num[0] == 0) return null;
            return configs[0];
        }

        private void releaseEgl() {
            if (dpy == EGL14.EGL_NO_DISPLAY) return;
            EGL14.eglMakeCurrent(dpy, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
            if (surf != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(dpy, surf);
            if (ctx != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(dpy, ctx);
            // eglTerminate не зовём: дисплей общий с другими GL-частями телеграма
            EGL14.eglReleaseThread();
            surf = EGL14.EGL_NO_SURFACE;
            ctx = EGL14.EGL_NO_CONTEXT;
            dpy = EGL14.EGL_NO_DISPLAY;
        }

        // ---------- GL-ресурсы ----------

        private boolean initGl() {
            prog = program(FemboyPetShaders.PET_VS, FemboyPetShaders.PET_FS);
            shProg = program(FemboyPetShaders.SHADOW_VS, FemboyPetShaders.SHADOW_FS);
            if (prog == 0 || shProg == 0) return false;
            aPos = GLES20.glGetAttribLocation(prog, "aPos");
            aNrm = GLES20.glGetAttribLocation(prog, "aNrm");
            uMVP = GLES20.glGetUniformLocation(prog, "uMVP");
            uModel = GLES20.glGetUniformLocation(prog, "uModel");
            uNrm = GLES20.glGetUniformLocation(prog, "uNrm");
            uPart = GLES20.glGetUniformLocation(prog, "uPart");
            uMat = GLES20.glGetUniformLocation(prog, "uMat");
            uVar = GLES20.glGetUniformLocation(prog, "uVar");
            uColor = GLES20.glGetUniformLocation(prog, "uColor");
            uAccent = GLES20.glGetUniformLocation(prog, "uAccent");
            uCam = GLES20.glGetUniformLocation(prog, "uCam");
            uView = GLES20.glGetUniformLocation(prog, "uView");
            uCrack = GLES20.glGetUniformLocation(prog, "uCrack");
            uClip = GLES20.glGetUniformLocation(prog, "uClip");
            uEyeL = GLES20.glGetUniformLocation(prog, "uEyeL");
            uEyeR = GLES20.glGetUniformLocation(prog, "uEyeR");
            uHeadInv = GLES20.glGetUniformLocation(prog, "uHeadInv");
            uBodyC = GLES20.glGetUniformLocation(prog, "uBodyC");
            uBodyR = GLES20.glGetUniformLocation(prog, "uBodyR");
            uShell = GLES20.glGetUniformLocation(prog, "uShell");
            sPos = GLES20.glGetAttribLocation(shProg, "aPos");
            sMVP = GLES20.glGetUniformLocation(shProg, "uMVP");
            sAlpha = GLES20.glGetUniformLocation(shProg, "uAlpha");

            gSphereHi = FemboyPetMesh.sphereHi().upload();
            gSphereLo = FemboyPetMesh.sphereLo().upload();
            gLoaf = FemboyPetMesh.loaf().upload();
            gEgg = FemboyPetMesh.egg().upload();
            gEar = FemboyPetMesh.ear().upload();
            gTail = FemboyPetMesh.tail().upload();
            gQuad = FemboyPetMesh.quad().upload();
            gWhiskers = FemboyPetMesh.whiskers().upload();

            float[] range = new float[2];
            GLES20.glGetFloatv(GLES20.GL_ALIASED_LINE_WIDTH_RANGE, range, 0);
            lineWidth = Math.max(1f, Math.min(range[1] > 0 ? range[1] : 1f, 1.3f * density));
            return true;
        }

        private void releaseGl() {
            FemboyPetMesh.Gpu[] all = {gSphereHi, gSphereLo, gLoaf, gEgg, gEar, gTail, gQuad, gWhiskers};
            for (FemboyPetMesh.Gpu g : all) {
                if (g != null) g.release();
            }
            if (prog != 0) GLES20.glDeleteProgram(prog);
            if (shProg != 0) GLES20.glDeleteProgram(shProg);
            prog = shProg = 0;
        }

        private int program(String vs, String fs) {
            int v = shader(GLES20.GL_VERTEX_SHADER, vs);
            int f = shader(GLES20.GL_FRAGMENT_SHADER, fs);
            if (v == 0 || f == 0) return 0;
            int p = GLES20.glCreateProgram();
            GLES20.glAttachShader(p, v);
            GLES20.glAttachShader(p, f);
            GLES20.glLinkProgram(p);
            GLES20.glDeleteShader(v);
            GLES20.glDeleteShader(f);
            int[] ok = new int[1];
            GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0);
            if (ok[0] == 0) {
                FileLog.e("FemboyPet: link " + GLES20.glGetProgramInfoLog(p));
                GLES20.glDeleteProgram(p);
                return 0;
            }
            return p;
        }

        private int shader(int type, String src) {
            int s = GLES20.glCreateShader(type);
            GLES20.glShaderSource(s, src);
            GLES20.glCompileShader(s);
            int[] ok = new int[1];
            GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0);
            if (ok[0] == 0) {
                FileLog.e("FemboyPet: shader " + GLES20.glGetShaderInfoLog(s));
                GLES20.glDeleteShader(s);
                return 0;
            }
            return s;
        }

        // ---------- кадр ----------

        private void drawFrame(long nanos) {
            float dt = lastNanos == 0 ? 0f : Math.max(0f, Math.min(0.05f, (nanos - lastNanos) / 1e9f));
            lastNanos = nanos;
            step(dt);

            int w = Math.max(1, width), h = Math.max(1, height);
            GLES20.glViewport(0, 0, w, h);
            GLES20.glClearColor(0f, 0f, 0f, 0f);
            GLES20.glDepthMask(true);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
            setupCamera(w / (float) h);

            Anim a = anim;
            drawShadow(a);

            GLES20.glEnable(GLES20.GL_DEPTH_TEST);
            GLES20.glDepthFunc(GLES20.GL_LEQUAL);
            GLES20.glDisable(GLES20.GL_BLEND);
            GLES20.glUseProgram(prog);
            GLES20.glUniform3f(uAccent, accR, accG, accB);
            GLES20.glUniform3f(uCam, cam[0], cam[1], cam[2]);
            GLES20.glUniformMatrix4fv(uView, 1, false, view, 0);
            GLES20.glUniform1f(uCrack, 0f);
            GLES20.glUniform1f(uClip, 0f);
            GLES20.glUniform4f(uEyeL, 0, -10, 0, 1);
            GLES20.glUniform4f(uEyeR, 0, -10, 0, 1);
            GLES20.glUniform1f(uShell, 0f);
            Matrix.setIdentityM(headInv, 0);
            GLES20.glUniformMatrix4fv(uHeadInv, 1, false, headInv, 0);
            GLES20.glUniform3f(uBodyC, 0, 0.5f, 0);
            GLES20.glUniform3f(uBodyR, 1, 1, 1);

            buildWorld(a);
            if (a.stage == FemboyPetView.STAGE_EGG || a.stage == FemboyPetView.STAGE_HATCHING) {
                drawEgg(a);
            } else {
                drawCat(a.stage == FemboyPetView.STAGE_BABY ? BABY : ADULT, a);
            }
            if (!EGL14.eglSwapBuffers(dpy, surf)) {
                FileLog.e("FemboyPet: swap failed " + EGL14.eglGetError());
            }
        }

        private void setupCamera(float aspect) {
            float fov = 30f;
            float t = (float) Math.tan(Math.toRadians(fov / 2));
            float d = Math.max(1.6f / t, 1.4f / (t * aspect));
            float cx = 0f, cy = 0.95f, cz = 0f;
            float dirY = 0.36f, len = (float) Math.sqrt(1 + dirY * dirY);
            cam[0] = cx;
            cam[1] = cy + d * dirY / len;
            cam[2] = cz + d / len;
            Matrix.perspectiveM(proj, 0, fov, aspect, Math.max(0.5f, d - 4f), d + 6f);
            Matrix.setLookAtM(view, 0, cam[0], cam[1], cam[2], cx, cy, cz, 0f, 1f, 0f);
            Matrix.multiplyMM(viewProj, 0, proj, 0, view, 0);
        }

        /** Мир питомца: наклон и поворот от пальца, прыжок, качание, сжатие. */
        private void buildWorld(Anim a) {
            Matrix.setIdentityM(world, 0);
            Matrix.rotateM(world, 0, deg(tilt), 1, 0, 0);
            Matrix.rotateM(world, 0, deg(yaw), 0, 1, 0);
            Matrix.translateM(world, 0, 0, a.lift, 0);
            Matrix.rotateM(world, 0, a.rockZ, 0, 0, 1);
            Matrix.rotateM(world, 0, a.rockX, 1, 0, 0);
            float s = a.scale;
            Matrix.scaleM(world, 0, s * a.sxz, s * a.sy, s * a.sxz);
        }

        private void drawShadow(Anim a) {
            if (a.scale < 0.01f) return;
            Matrix.setIdentityM(model, 0);
            Matrix.rotateM(model, 0, deg(tilt), 1, 0, 0);
            Matrix.rotateM(model, 0, deg(yaw), 0, 1, 0);
            Matrix.translateM(model, 0, 0, 0.003f, a.shadowOffZ * a.scale);
            float k = a.scale * a.sxz / (1f + a.lift * 1.5f);
            Matrix.scaleM(model, 0, a.shadowX * k, 1, a.shadowZ * k);
            Matrix.multiplyMM(mvp, 0, viewProj, 0, model, 0);

            GLES20.glDisable(GLES20.GL_DEPTH_TEST);
            GLES20.glDepthMask(false);
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);
            if (aNrm >= 0) GLES20.glDisableVertexAttribArray(aNrm);
            GLES20.glUseProgram(shProg);
            GLES20.glUniformMatrix4fv(sMVP, 1, false, mvp, 0);
            GLES20.glUniform1f(sAlpha, 0.34f / (1f + a.lift * 4f));
            gQuad.draw(sPos, -1);
            GLES20.glDepthMask(true);
        }

        private void draw(FemboyPetMesh.Gpu g, float[] p, int mat, float var, float r, float gg, float b) {
            Matrix.multiplyMM(model, 0, world, 0, p, 0);
            Matrix.multiplyMM(mvp, 0, viewProj, 0, model, 0);
            if (Matrix.invertM(tmp, 0, model, 0)) {
                Matrix.transposeM(nrm, 0, tmp, 0);
            } else {
                System.arraycopy(model, 0, nrm, 0, 16);
            }
            GLES20.glUniformMatrix4fv(uMVP, 1, false, mvp, 0);
            GLES20.glUniformMatrix4fv(uModel, 1, false, model, 0);
            GLES20.glUniformMatrix4fv(uNrm, 1, false, nrm, 0);
            GLES20.glUniformMatrix4fv(uPart, 1, false, p, 0);
            GLES20.glUniform1f(uMat, mat);
            GLES20.glUniform1f(uVar, var);
            GLES20.glUniform3f(uColor, r, gg, b);
            g.draw(aPos, aNrm);
        }

        private void fur(FemboyPetMesh.Gpu g, float[] p, int variant) {
            draw(g, p, FemboyPetShaders.FUR, variant, TAN, TAN_G, TAN_B);
        }

        private void from(float[] parent) {
            System.arraycopy(parent, 0, part, 0, 16);
        }

        // ---------- яйцо ----------

        private void drawEgg(Anim a) {
            boolean hatch = a.stage == FemboyPetView.STAGE_HATCHING;
            Matrix.setIdentityM(egg, 0);
            Matrix.translateM(egg, 0, 0, 0.75f, 0);
            Matrix.scaleM(egg, 0, 0.6f, 0.6f, 0.6f);
            if (!hatch) {
                draw(gEgg, egg, FemboyPetShaders.SHELL, 0, 1, 1, 1);
                return;
            }
            GLES20.glUniform1f(uCrack, 1f);
            // низ скорлупы
            GLES20.glUniform1f(uClip, 1f);
            draw(gEgg, egg, FemboyPetShaders.SHELL, 0, 1, 1, 1);
            // крышка: приподнята и откинута назад на шарнире у задней стенки
            from(egg);
            Matrix.translateM(part, 0, 0, a.capLift, -0.85f);
            Matrix.translateM(part, 0, 0, 0.45f, 0);
            Matrix.rotateM(part, 0, -a.capAngle, 1, 0, 0);
            Matrix.translateM(part, 0, 0, -0.45f, 0.85f);
            GLES20.glUniform1f(uClip, -1f);
            draw(gEgg, part, FemboyPetShaders.SHELL, 0, 1, 1, 1);
            GLES20.glUniform1f(uClip, 0f);
            GLES20.glUniform1f(uCrack, 0f);

            // уши торчат сквозь верх
            for (int side = -1; side <= 1; side += 2) {
                float tw = (side < 0) == a.twitchLeft ? a.twitch : 0f;
                from(egg);
                Matrix.translateM(part, 0, side * 0.2f, 0.5f, 0f);
                Matrix.rotateM(part, 0, -side * (22f + tw), 0, 0, 1);
                Matrix.rotateM(part, 0, side * 12f, 0, 1, 0);
                Matrix.translateM(part, 0, 0, 0.5f, 0);
                System.arraycopy(part, 0, save, 0, 16);
                Matrix.scaleM(part, 0, 0.16f, 0.5f, 0.06f);
                fur(gEar, part, FemboyPetShaders.V_EAR);
                System.arraycopy(save, 0, part, 0, 16);
                Matrix.translateM(part, 0, 0, 0.04f, 0.035f);
                Matrix.scaleM(part, 0, 0.11f, 0.4f, 0.035f);
                draw(gEar, part, FemboyPetShaders.EAR_IN, 0, 1, 1, 1);
            }
            // глаза выглядывают в щель
            for (int side = -1; side <= 1; side += 2) {
                from(egg);
                Matrix.translateM(part, 0, side * 0.2f, 0.56f, 0.68f);
                Matrix.rotateM(part, 0, side * 15f, 0, 1, 0);
                Matrix.scaleM(part, 0, 0.12f, 0.13f * a.blink, 0.08f);
                draw(gSphereLo, part, FemboyPetShaders.EYE, 0.58f, 1, 1, 1);
            }
        }

        // ---------- тилкайо ----------

        private void drawCat(Pose p, Anim a) {
            float br = a.breath;
            GLES20.glUniform3f(uBodyC, 0, p.bodyY, p.bodyZ);
            GLES20.glUniform3f(uBodyR, p.bodyRx, p.bodyRy, p.bodyRz);
            // тело-буханка и два редких слоя шерсти поверх для пушистого края
            for (int layer = 0; layer < 3; layer++) {
                float k = 1f + 0.022f * layer;
                Matrix.setIdentityM(part, 0);
                Matrix.translateM(part, 0, 0, p.bodyY - 0.01f * layer, p.bodyZ);
                Matrix.scaleM(part, 0, p.bodyRx * k * (1 + 0.02f * br), p.bodyRy * k * (1 + 0.03f * br), p.bodyRz * k * (1 + 0.02f * br));
                GLES20.glUniform1f(uShell, layer);
                fur(gLoaf, part, FemboyPetShaders.V_BODY);
            }
            GLES20.glUniform1f(uShell, 0f);
            // бёдра чуть выпирают по бокам сзади
            for (int side = -1; side <= 1; side += 2) {
                Matrix.setIdentityM(part, 0);
                Matrix.translateM(part, 0, side * p.hipX, p.hipY, p.hipZ);
                Matrix.scaleM(part, 0, p.hipRx, p.hipRy * (1 + 0.03f * br), p.hipRz);
                fur(gLoaf, part, FemboyPetShaders.V_BODY);
            }

            // голова: своя система, в ней морда, глаза, уши, усы
            Matrix.setIdentityM(head, 0);
            Matrix.translateM(head, 0, 0, p.headY + 0.022f * br, p.headZ);
            Matrix.rotateM(head, 0, a.headTilt, 0, 0, 1);
            float hz = p.headRz / 0.46f;

            // глаза считаем заранее: голове нужны их центры для светлых ободков
            eyeMatrix(eyeL, p, -1, a.blink);
            eyeMatrix(eyeR, p, 1, a.blink);
            Matrix.multiplyMV(v4, 0, eyeL, 0, o4, 0);
            GLES20.glUniform4f(uEyeL, v4[0], v4[1], v4[2], p.eyeRy);
            Matrix.multiplyMV(v4, 0, eyeR, 0, o4, 0);
            GLES20.glUniform4f(uEyeR, v4[0], v4[1], v4[2], p.eyeRy);

            from(head);
            Matrix.scaleM(part, 0, p.headRx, p.headRy, p.headRz);
            // координаты головы для рисунка на морде: радиус 1, перед в +z
            if (!Matrix.invertM(headInv, 0, part, 0)) Matrix.setIdentityM(headInv, 0);
            GLES20.glUniformMatrix4fv(uHeadInv, 1, false, headInv, 0);
            fur(gSphereHi, part, FemboyPetShaders.V_HEAD);
            // пухлые щёки
            for (int side = -1; side <= 1; side += 2) {
                from(head);
                Matrix.translateM(part, 0, side * p.cheekX, -0.14f, 0.1f * hz);
                Matrix.scaleM(part, 0, p.cheekR, p.cheekR * 0.82f, p.cheekR);
                fur(gSphereHi, part, FemboyPetShaders.V_HEAD);
            }
            // светлая морда: две подушечки и подбородок
            for (int side = -1; side <= 1; side += 2) {
                from(head);
                Matrix.translateM(part, 0, side * 0.085f, -0.14f, 0.41f * hz);
                Matrix.scaleM(part, 0, 0.11f, 0.09f, 0.09f);
                draw(gSphereLo, part, FemboyPetShaders.CREAM, 0, 1, 1, 1);
            }
            from(head);
            Matrix.translateM(part, 0, 0, -0.24f, 0.36f * hz);
            Matrix.scaleM(part, 0, 0.09f, 0.06f, 0.07f);
            draw(gSphereLo, part, FemboyPetShaders.CREAM, 0, 1, 1, 1);
            // нос
            from(head);
            Matrix.translateM(part, 0, 0, -0.06f, 0.455f * hz);
            Matrix.scaleM(part, 0, 0.055f, 0.038f, 0.045f);
            draw(gSphereLo, part, FemboyPetShaders.NOSE, 0, 0.13f, 0.07f, 0.06f);
            // глаза
            GLES20.glUniform4f(uEyeL, 0, -10, 0, 1);
            GLES20.glUniform4f(uEyeR, 0, -10, 0, 1);
            draw(gSphereLo, eyeL, FemboyPetShaders.EYE, p.pupil, 1, 1, 1);
            draw(gSphereLo, eyeR, FemboyPetShaders.EYE, p.pupil, 1, 1, 1);
            // уши: снаружи мех с тёмным кончиком, внутри розовое
            for (int side = -1; side <= 1; side += 2) {
                float tw = (side < 0) == a.twitchLeft ? a.twitch : 0f;
                from(head);
                Matrix.translateM(part, 0, side * p.earX, p.earY, -0.05f);
                Matrix.rotateM(part, 0, -side * (p.earTilt + tw * 0.5f), 0, 0, 1);
                Matrix.rotateM(part, 0, -8f - a.earBack, 1, 0, 0);
                Matrix.rotateM(part, 0, side * (10f + tw), 0, 1, 0);
                Matrix.translateM(part, 0, 0, p.earLen, 0);
                System.arraycopy(part, 0, save, 0, 16);
                Matrix.scaleM(part, 0, p.earW, p.earLen, p.earT);
                fur(gEar, part, FemboyPetShaders.V_EAR);
                System.arraycopy(save, 0, part, 0, 16);
                Matrix.translateM(part, 0, 0, p.earLen * 0.08f, p.earT * 0.6f);
                Matrix.scaleM(part, 0, p.earW * 0.7f, p.earLen * 0.8f, p.earT * 0.6f);
                draw(gEar, part, FemboyPetShaders.EAR_IN, 0, 1, 1, 1);
            }
            // усы
            GLES20.glLineWidth(lineWidth);
            from(head);
            Matrix.scaleM(part, 0, 1f, 1f, hz);
            draw(gWhiskers, part, FemboyPetShaders.LINE, 0, 0.96f, 0.94f, 0.88f);

            // лапки: короткие, в цвет меха, выглядывают из-под тела
            for (int side = -1; side <= 1; side += 2) {
                Matrix.setIdentityM(part, 0);
                Matrix.translateM(part, 0, side * p.pawFx, p.pawRy * 0.62f, p.pawFz);
                Matrix.rotateM(part, 0, side * 6f, 0, 1, 0);
                Matrix.scaleM(part, 0, p.pawRx, p.pawRy, p.pawRz);
                fur(gLoaf, part, FemboyPetShaders.V_PAW);
                Matrix.setIdentityM(part, 0);
                Matrix.translateM(part, 0, side * p.pawBx, p.pawRy * 0.62f, p.pawBz);
                Matrix.rotateM(part, 0, side * 10f, 0, 1, 0);
                Matrix.scaleM(part, 0, p.pawRx * 1.1f, p.pawRy, p.pawRz * 1.1f);
                fur(gLoaf, part, FemboyPetShaders.V_PAW);
            }
            // хвост
            Matrix.setIdentityM(part, 0);
            Matrix.translateM(part, 0, 0, 0, p.bodyZ + 0.1f);
            Matrix.scaleM(part, 0, p.tail, p.tail, p.tail);
            fur(gTail, part, FemboyPetShaders.V_TAIL);
        }

        private void eyeMatrix(float[] out, Pose p, int side, float blink) {
            System.arraycopy(head, 0, out, 0, 16);
            Matrix.translateM(out, 0, side * p.eyeX, p.eyeY, p.eyeZ);
            Matrix.rotateM(out, 0, side * 18f, 0, 1, 0);
            Matrix.scaleM(out, 0, p.eyeRx, p.eyeRy * blink, p.eyeRz);
        }
    }

    // ---------- анимация (GL-поток, без GL-вызовов) ----------

    /** Идёт что-то быстрое: палец, прыжок, смена стадии, кручение. */
    private boolean busy() {
        synchronized (lock) {
            if (inTouching || inCheer != seenCheer || inStage != shownStage || inFlingSet) return true;
        }
        return cheerT >= 0f || phase != 0 || Math.abs(yawVel) > 0.05f || Math.abs(yaw - HOME_YAW) > 0.01f;
    }

    /** Итог шага анимации на кадр. */
    private static final class Anim {
        int stage;
        float scale = 1f, sy = 1f, sxz = 1f, lift, rockZ, rockX;
        float breath, blink = 1f, twitch, earBack, headTilt;
        boolean twitchLeft;
        float capLift, capAngle;
        float shadowX = 1f, shadowZ = 1f, shadowOffZ;
    }

    private final Anim anim = new Anim();

    private void step(float dt) {
        int want, cheerReq;
        boolean touching;
        float dYaw, dTilt;
        synchronized (lock) {
            want = inStage;
            cheerReq = inCheer;
            touching = inTouching;
            dYaw = inDragYaw;
            dTilt = inDragTilt;
            inDragYaw = inDragTilt = 0;
            if (inFlingSet) {
                yawVel = Math.max(-12f, Math.min(12f, inFling));
                inFlingSet = false;
                idleT = 0;
            }
            accR = inAccR;
            accG = inAccG;
            accB = inAccB;
        }
        time += dt;
        float t = (float) (time % 3600.0);

        // вращение пальцем, инерция, возврат к трём четвертям
        yaw += dYaw;
        tilt = clamp(tilt + dTilt, -0.3f, 0.35f);
        if (touching) {
            yawVel = 0;
            idleT = 0;
        } else {
            yaw += yawVel * dt;
            yawVel *= (float) Math.exp(-2.5 * dt);
            tilt += (0f - tilt) * (1f - (float) Math.exp(-4.0 * dt));
            idleT += dt;
            if (idleT > 1.5f && Math.abs(yawVel) < 0.4f) {
                double twoPi = Math.PI * 2;
                float target = (float) (HOME_YAW + twoPi * Math.round((yaw - HOME_YAW) / twoPi));
                yaw += (target - yaw) * (1f - (float) Math.exp(-1.6 * dt));
                // держим угол в разумных пределах
                if (Math.abs(yaw - target) < 1e-3f) yaw = HOME_YAW;
            }
        }

        // смена стадии: сжаться, подменить, выскочить с перелётом
        Anim a = anim;
        if (want != shownStage && phase != 1) {
            if (shownStage < 0) {
                shownStage = want;
                phase = 2;
            } else {
                phase = 1;
            }
            phaseT = 0;
        }
        float stageScale = 1f, ssY = 1f, ssXZ = 1f;
        if (phase == 1) {
            phaseT += dt;
            float u = Math.min(1f, phaseT / 0.2f);
            stageScale = 1f - u * u;
            ssY = 1f - 0.15f * u;
            ssXZ = 1f + 0.1f * u;
            if (u >= 1f) {
                shownStage = want;
                phase = 2;
                phaseT = 0;
                cheerT = -1f;
            }
        } else if (phase == 2) {
            phaseT += dt;
            float u = Math.min(1f, phaseT / 0.55f);
            float v = u - 1f;
            stageScale = Math.max(0f, 1f + 2.70158f * v * v * v + 1.70158f * v * v);
            float wob = (float) Math.sin(u * Math.PI * 2) * (1f - u);
            ssY = 1f + 0.18f * wob;
            ssXZ = 1f - 0.09f * wob;
            if (u >= 1f) phase = 0;
        }
        a.stage = shownStage < 0 ? want : shownStage;
        boolean isEgg = a.stage == FemboyPetView.STAGE_EGG || a.stage == FemboyPetView.STAGE_HATCHING;
        boolean hatch = a.stage == FemboyPetView.STAGE_HATCHING;
        Pose pose = a.stage == FemboyPetView.STAGE_BABY ? BABY : ADULT;

        if (cheerReq != seenCheer) {
            seenCheer = cheerReq;
            cheerT = 0f;
        }

        a.lift = 0;
        a.rockZ = 0;
        a.rockX = 0;
        a.sy = ssY;
        a.sxz = ssXZ;
        a.earBack = 0;
        a.headTilt = 0;
        a.breath = 0;

        // моргание и подёргивание уха
        if (time >= nextBlink) {
            blinkStart = time;
            nextBlink = time + 2.2 + rnd.nextFloat() * 3.5;
        }
        float bk = (float) ((time - blinkStart) / 0.16);
        a.blink = bk < 1f ? Math.max(0.08f, Math.abs(2f * bk - 1f)) : 1f;
        if (time >= nextTwitch) {
            twitchStart = time;
            twitchLeft = rnd.nextBoolean();
            nextTwitch = time + (hatch ? 2.0 : 3.5) + rnd.nextFloat() * 5.0;
        }
        float tk = (float) ((time - twitchStart) / 0.4);
        a.twitch = tk < 1f ? 22f * (float) Math.sin(tk * Math.PI * 2) * (1f - tk) : 0f;
        a.twitchLeft = twitchLeft;

        if (isEgg) {
            // покачивание пачками: яйцо редко, треснувшее часто
            float period = hatch ? 1.4f : 3.4f, win = hatch ? 0.45f : 0.3f;
            float ph = (t % period) / period;
            float env = ph < win ? (float) Math.sin(ph / win * Math.PI) : 0f;
            float osc = (float) Math.sin(ph / win * Math.PI * 2 * (hatch ? 3 : 2));
            a.rockZ = 6f + (hatch ? 7f : 4f) * env * osc;
            a.rockX = 3f;
            a.capLift = 0.06f + 0.04f * env * Math.abs(osc);
            a.capAngle = 14f + 6f * env * osc;
            if (cheerT >= 0f) {
                float c = cheerT;
                float damp = (float) Math.exp(-2.5 * c);
                a.rockZ += 20f * damp * (float) Math.sin(c * Math.PI * 2 * 2.5);
                a.rockX += 6f * damp * (float) Math.sin(c * Math.PI * 2 * 1.7);
                if (c < 0.3f) a.lift = 0.1f * (float) Math.sin(c / 0.3f * Math.PI);
                a.capLift += 0.12f * damp;
                a.capAngle += 10f * damp;
                cheerT += dt;
                if (cheerT > 1.6f) cheerT = -1f;
            }
            a.shadowX = 0.62f;
            a.shadowZ = 0.62f;
            a.shadowOffZ = 0f;
        } else {
            a.breath = (float) Math.sin(t * 2.2);
            if (a.stage == FemboyPetView.STAGE_BABY) {
                // малыш подпрыгивает на месте
                float hb = Math.abs((float) Math.sin(t * 3.6));
                float c = (float) Math.pow(1f - hb, 6);
                a.lift = 0.05f * hb;
                a.sy *= 1f - 0.08f * c;
                a.sxz *= 1f + 0.04f * c;
                a.headTilt = 5f * (float) Math.sin(t * 1.8);
            } else {
                a.headTilt = 3f * (float) Math.sin(t * 0.7);
            }
            if (cheerT >= 0f) {
                float u = cheerT / 0.95f;
                if (u < 0.12f) {
                    float k = (float) Math.sin(u / 0.12f * Math.PI / 2);
                    a.sy *= 1f - 0.18f * k;
                    a.sxz *= 1f + 0.1f * k;
                    a.lift = 0;
                } else if (u < 0.56f) {
                    float k = (u - 0.12f) / 0.44f;
                    float cs = (float) Math.cos(k * Math.PI);
                    a.lift = 4f * pose.jump * k * (1f - k);
                    a.sy *= 1f + 0.15f * cs;
                    a.sxz *= 1f - 0.07f * cs;
                    a.earBack = 25f * (float) Math.sin(k * Math.PI);
                } else {
                    float k = Math.min(1f, (u - 0.56f) / 0.44f);
                    float sq = -0.15f * (float) Math.exp(-4 * k) * (float) Math.cos(k * Math.PI * 2.5);
                    a.sy *= 1f + sq;
                    a.sxz *= 1f - sq * 0.5f;
                    a.lift = 0;
                }
                cheerT += dt;
                if (cheerT > 0.95f) cheerT = -1f;
            }
            a.shadowX = pose.shadowX;
            a.shadowZ = pose.shadowZ;
            a.shadowOffZ = -0.05f;
        }
        a.scale = stageScale * (isEgg ? 1f : pose.scale);
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static float deg(float rad) {
        return (float) Math.toDegrees(rad);
    }
}
