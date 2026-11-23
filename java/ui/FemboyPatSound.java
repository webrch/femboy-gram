package org.telegram.ui;

import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.SoundPool;

import org.telegram.femboy.FemboyConfig;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Random;

/**
 * Звук патпата: три шлепка из мода PatPat (LopyMine, LGPL-3.0), лежат в
 * assets/patpat. На каждый шлепок берётся случайный. Если в настройках
 * выбран свой файл, играет он.
 */
public class FemboyPatSound {

    private static final String[] BUILT_IN = {"patpat/pat.ogg", "patpat/pat1.ogg", "patpat/pat2.ogg"};

    private static SoundPool pool;
    private static int[] samples = new int[0];
    private static boolean[] ready = new boolean[0];
    /** Что сейчас загружено: путь своего файла или null для встроенных. */
    private static String loaded;
    private static boolean loadedAny;
    /** Свой файл, который SoundPool не осилил: второй раз не пробуем. */
    private static String failed;
    /** Шлепок попросили до того, как звук загрузился. */
    private static boolean pending;
    private static final Random random = new Random();

    /** Загрузить заранее, чтобы первый шлепок не опоздал. */
    public static void prepare() {
        if (!FemboyConfig.meowEnabled()) {
            return;
        }
        try {
            String own = FemboyConfig.meowPath();
            if (own != null && (own.equals(failed) || !new File(own).exists())) {
                own = null;
            }
            if (pool != null && loadedAny && (own == null ? loaded == null : own.equals(loaded))) {
                return;
            }
            if (pool == null) {
                pool = new SoundPool.Builder()
                        .setMaxStreams(4)
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_GAME)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build())
                        .build();
                pool.setOnLoadCompleteListener(FemboyPatSound::onLoaded);
            }
            unloadAll();
            loadedAny = true;
            if (own != null) {
                loaded = own;
                samples = new int[]{pool.load(own, 1)};
                ready = new boolean[1];
            } else {
                loadBuiltIn();
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    private static void onLoaded(SoundPool p, int id, int status) {
        for (int i = 0; i < samples.length; i++) {
            if (samples[i] != id) {
                continue;
            }
            if (status == 0) {
                ready[i] = true;
                if (pending) {
                    pending = false;
                    play();
                }
            } else if (loaded != null) {
                // свой файл не прочитался: откатываемся на встроенные
                failed = loaded;
                unloadAll();
                loadBuiltIn();
            }
            return;
        }
    }

    private static void unloadAll() {
        if (pool != null) {
            for (int sample : samples) {
                if (sample != 0) {
                    pool.unload(sample);
                }
            }
        }
        samples = new int[0];
        ready = new boolean[0];
    }

    private static void loadBuiltIn() {
        loaded = null;
        final int[] ids = new int[BUILT_IN.length];
        for (int i = 0; i < BUILT_IN.length; i++) {
            ids[i] = loadAsset(BUILT_IN[i]);
        }
        ready = new boolean[ids.length];
        samples = ids;
    }

    /** Из assets напрямую, а если файл в апк сжат, то через копию в кэше. */
    private static int loadAsset(String name) {
        try (AssetFileDescriptor fd = ApplicationLoader.applicationContext.getAssets().openFd(name)) {
            return pool.load(fd, 1);
        } catch (Throwable ignored) {
        }
        try {
            final File out = new File(ApplicationLoader.applicationContext.getCacheDir(), name.replace('/', '_'));
            if (!out.exists() || out.length() == 0) {
                try (InputStream in = ApplicationLoader.applicationContext.getAssets().open(name);
                     FileOutputStream os = new FileOutputStream(out)) {
                    final byte[] buf = new byte[8192];
                    int read;
                    while ((read = in.read(buf)) > 0) {
                        os.write(buf, 0, read);
                    }
                }
            }
            return pool.load(out.getAbsolutePath(), 1);
        } catch (Throwable t) {
            FileLog.e(t);
            return 0;
        }
    }

    /** Файл в настройках сменили: перечитать при следующем шлепке. */
    public static void reset() {
        failed = null;
        unloadAll();
        loadedAny = false;
    }

    /** Один шлепок, случайный из загруженных. Тихо молчит, если звук выключен. */
    public static void play() {
        if (!FemboyConfig.meowEnabled()) {
            return;
        }
        prepare();
        if (pool == null || samples.length == 0) {
            return;
        }
        try {
            int count = 0;
            for (boolean r : ready) {
                if (r) {
                    count++;
                }
            }
            if (count == 0) {
                pending = true;
                return;
            }
            int pick = random.nextInt(count);
            for (int i = 0; i < samples.length; i++) {
                if (ready[i] && pick-- == 0) {
                    pool.play(samples[i], 1f, 1f, 1, 0, 1f);
                    break;
                }
            }
            // «Звук» в настройках появляется после первого услышанного шлепка
            FemboyConfig.setMeowHeard();
        } catch (Throwable ignored) {
        }
    }
}
