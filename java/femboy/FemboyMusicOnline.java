package org.telegram.femboy;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.BulletinFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Музыка из сети: поиск на SoundCloud и YouTube Music, похожее и скачивание.
 *
 * Сама работа в питоне (femboy_music_online.py, yt-dlp), а сюда он попадает
 * через FemboyMusicPython из модуля приложения: зовём его по имени, через
 * отражение, как и плагины. Скачанное лежит в кэше, лайкнутое переносится в
 * filesDir (это делает FemboyMusic).
 */
public final class FemboyMusicOnline {

    private FemboyMusicOnline() {}

    private static final String BRIDGE = "org.telegram.femboy.FemboyMusicPython";
    private static final long CACHE_LIMIT = 400L * 1024 * 1024;
    private static final long RECS_TTL = 4 * 60 * 60 * 1000L;

    /** Ответ питона: список треков или ошибка. */
    public static final class Result {
        public final boolean ok;
        public final String error;
        public final String message;
        public final ArrayList<FemboyMusic.Track> items = new ArrayList<>();

        Result(boolean ok, String error, String message) {
            this.ok = ok;
            this.error = error;
            this.message = message;
        }

        /** Текст ошибки для человека. */
        public String errorText() {
            return FemboyMusicOnline.errorText(error, message);
        }
    }

    private static final ExecutorService searchPool = Executors.newFixedThreadPool(2);
    private static final ExecutorService downloadPool = Executors.newFixedThreadPool(2);

    private static Boolean available;

    /** Есть ли в этой сборке питон с yt-dlp. */
    public static boolean available() {
        if (available == null) {
            try {
                Class.forName(BRIDGE);
                available = true;
            } catch (Throwable e) {
                available = false;
            }
        }
        return available;
    }

    private static String call(String method, Class<?>[] types, Object... args) throws Throwable {
        try {
            return (String) Class.forName(BRIDGE).getMethod(method, types).invoke(null, args);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw e.getCause() == null ? e : e.getCause();
        }
    }

    private static Result parse(String json) {
        if (json == null) return new Result(false, "other", "no answer");
        try {
            final JSONObject root = new JSONObject(json);
            if (!root.optBoolean("ok")) {
                return new Result(false, root.optString("error", "other"), root.optString("message"));
            }
            final Result result = new Result(true, null, null);
            final JSONArray items = root.optJSONArray("items");
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    final FemboyMusic.Track track = trackOf(items.getJSONObject(i));
                    if (track != null) result.items.add(track);
                }
            }
            return result;
        } catch (Throwable e) {
            return new Result(false, "other", e.getMessage());
        }
    }

    static FemboyMusic.Track trackOf(JSONObject one) {
        final String source = one.optString("source");
        final String id = one.optString("id");
        if (TextUtils.isEmpty(id) || !(FemboyMusic.SC.equals(source) || FemboyMusic.YT.equals(source))) return null;
        return new FemboyMusic.Track(source, id, one.optString("title"), one.optString("artist"), one.optInt("duration"),
                emptyToNull(one.optString("cover", null)), emptyToNull(one.optString("url", null)));
    }

    private static String emptyToNull(String s) {
        return TextUtils.isEmpty(s) || "null".equals(s) ? null : s;
    }

    private static JSONObject jsonOf(FemboyMusic.Track track) throws Exception {
        return new JSONObject().put("source", track.source).put("id", track.extId).put("title", track.title())
                .put("artist", track.artist()).put("duration", track.duration()).put("cover", track.cover())
                .put("url", track.url());
    }

    private static Result failNoPython() {
        return new Result(false, "nopython", null);
    }

    private interface Work {
        String call() throws Throwable;
    }

    private static void run(Utilities.Callback<Result> done, Work work) {
        if (!available()) {
            AndroidUtilities.runOnUIThread(() -> done.run(failNoPython()));
            return;
        }
        searchPool.execute(() -> {
            Result result;
            try {
                result = parse(work.call());
            } catch (Throwable e) {
                FileLog.e(e);
                result = new Result(false, "other", e.getMessage());
            }
            final Result finalResult = result;
            AndroidUtilities.runOnUIThread(() -> done.run(finalResult));
        });
    }

    /** Поиск. source: FemboyMusic.SC или FemboyMusic.YT. done на главном потоке. */
    public static void search(String source, String query, int limit, Utilities.Callback<Result> done) {
        run(done, () -> call("search", new Class<?>[]{String.class, String.class, int.class}, source, query, limit));
    }

    /** Похожее на один трек: радио YouTube Music или «рекомендуемое» SoundCloud. */
    public static void related(FemboyMusic.Track track, int limit, Utilities.Callback<Result> done) {
        run(done, () -> call("related", new Class<?>[]{String.class, String.class, String.class, int.class},
                track.source, track.extId, track.url(), limit));
    }

    // рекомендации

    private static Result recs;
    private static long recsTime;
    private static boolean recsLoading;
    private static final ArrayList<Utilities.Callback<Result>> recsWaiters = new ArrayList<>();

    private static File recsFile() {
        return new File(ApplicationLoader.getFilesDirFixed(), "femboy_music_recs.json");
    }

    /** Свежие рекомендации из памяти или файла, или null. */
    public static Result cachedRecommendations() {
        if (recs == null) {
            try {
                final File file = recsFile();
                if (file.exists()) {
                    final String text = read(file);
                    final JSONObject root = new JSONObject(text);
                    recsTime = root.optLong("time");
                    recs = parse(root.optString("answer"));
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        if (recs != null && System.currentTimeMillis() - recsTime < RECS_TTL) return recs;
        return null;
    }

    /**
     * Рекомендации по лайкам и недавнему. Держим несколько часов, force
     * спрашивает заново. Без семян отвечает пустым списком.
     */
    public static void recommendations(boolean force, Utilities.Callback<Result> done) {
        if (!force) {
            final Result cached = cachedRecommendations();
            if (cached != null) {
                done.run(cached);
                return;
            }
        }
        recsWaiters.add(done);
        if (recsLoading) return;
        final String seeds = seeds();
        if (seeds == null) {
            recsWaiters.clear();
            done.run(new Result(true, null, null));
            return;
        }
        recsLoading = true;
        run(result -> {
            recsLoading = false;
            if (result.ok) {
                recs = result;
                recsTime = System.currentTimeMillis();
                saveRecs(result);
            }
            final ArrayList<Utilities.Callback<Result>> waiters = new ArrayList<>(recsWaiters);
            recsWaiters.clear();
            for (Utilities.Callback<Result> one : waiters) one.run(result);
        }, () -> call("recommend", new Class<?>[]{String.class, int.class}, seeds, 30));
    }

    /** До трёх семян: лайкнутое и недавнее вперемешку, сначала из сети. */
    private static String seeds() {
        final ArrayList<FemboyMusic.Track> pool = new ArrayList<>();
        final HashSet<String> keys = new HashSet<>();
        final ArrayList<FemboyMusic.Track> liked = FemboyMusic.liked().tracks;
        final ArrayList<FemboyMusic.Track> recent = FemboyMusic.recent();
        for (int i = 0; i < Math.min(8, liked.size()); i++) if (keys.add(liked.get(i).key())) pool.add(liked.get(i));
        for (int i = 0; i < Math.min(8, recent.size()); i++) if (keys.add(recent.get(i).key())) pool.add(recent.get(i));
        if (pool.isEmpty()) return null;
        Collections.shuffle(pool, Utilities.random);
        // трек из сети даёт похожее одним запросом, из чата нужно сначала найти его
        Collections.sort(pool, (a, b) -> Boolean.compare(!a.external(), !b.external()));
        try {
            final JSONArray array = new JSONArray();
            for (FemboyMusic.Track track : pool) {
                if (array.length() >= 3) break;
                if (TextUtils.isEmpty(track.title()) && TextUtils.isEmpty(track.artist())) continue;
                array.put(jsonOf(track));
            }
            // остальные просто исключаем из выдачи
            for (FemboyMusic.Track track : liked) {
                if (track.external()) array.put(new JSONObject().put("source", "skip").put("id", track.extId));
            }
            return array.toString();
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    private static void saveRecs(Result result) {
        try {
            final JSONArray items = new JSONArray();
            for (FemboyMusic.Track track : result.items) items.put(jsonOf(track));
            final String answer = new JSONObject().put("ok", true).put("items", items).toString();
            final String text = new JSONObject().put("time", recsTime).put("answer", answer).toString();
            Utilities.globalQueue.postRunnable(() -> {
                try (FileOutputStream out = new FileOutputStream(recsFile())) {
                    out.write(text.getBytes(StandardCharsets.UTF_8));
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            });
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private static String read(File file) throws Exception {
        try (FileInputStream in = new FileInputStream(file)) {
            final byte[] bytes = new byte[(int) file.length()];
            int read = 0;
            while (read < bytes.length) {
                final int n = in.read(bytes, read, bytes.length - read);
                if (n < 0) break;
                read += n;
            }
            return new String(bytes, 0, read, StandardCharsets.UTF_8);
        }
    }

    // скачивание

    private static final ConcurrentHashMap<String, AtomicInteger> progress = new ConcurrentHashMap<>();
    private static final HashMap<String, ArrayList<Utilities.Callback<Boolean>>> waiters = new HashMap<>();
    private static final ConcurrentHashMap<String, String> cachedPaths = new ConcurrentHashMap<>();
    private static boolean polling;

    static File cacheDir() {
        final File dir = new File(ApplicationLoader.applicationContext.getCacheDir(), "femboy_music_online");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    /** Скачанный в кэш файл трека или null. */
    public static File cached(FemboyMusic.Track track) {
        if (track == null || !track.external()) return null;
        final String key = track.key();
        final String known = cachedPaths.get(key);
        if (known != null) {
            final File file = new File(known);
            if (file.exists() && file.length() > 0) return file;
            cachedPaths.remove(key);
        }
        if (progress.containsKey(key)) return null;
        final String prefix = FemboyMusic.safe(key) + ".";
        final File[] files = cacheDir().listFiles((dir, name) -> name.startsWith(prefix)
                && !name.endsWith(".part") && !name.endsWith(".ytdl") && !name.contains(".part-") && !name.endsWith(".temp"));
        if (files != null) {
            for (File file : files) {
                if (file.length() > 0) {
                    cachedPaths.put(key, file.getAbsolutePath());
                    return file;
                }
            }
        }
        return null;
    }

    /** Ход скачивания трека 0..1, или -1, если он не качается. */
    public static float progress(String key) {
        final AtomicInteger one = key == null ? null : progress.get(key);
        return one == null ? -1 : one.get() / 1000f;
    }

    /** Качается ли сейчас что-нибудь. */
    public static boolean busy() {
        return !progress.isEmpty();
    }

    /**
     * Скачать трек в кэш, если его там нет. done(true) на главном потоке, когда
     * файл готов; при ошибке done(false) и плашка с причиной.
     */
    public static void fetch(FemboyMusic.Track track, Utilities.Callback<Boolean> done) {
        if (track == null || !track.external()) {
            if (done != null) done.run(false);
            return;
        }
        if (FemboyMusic.playableFile(track) != null) {
            if (done != null) done.run(true);
            return;
        }
        final String key = track.key();
        ArrayList<Utilities.Callback<Boolean>> list = waiters.get(key);
        if (list != null) {
            if (done != null) list.add(done);
            return;
        }
        list = new ArrayList<>();
        if (done != null) list.add(done);
        waiters.put(key, list);
        if (!available()) {
            finish(track, false, failNoPython());
            return;
        }
        final AtomicInteger counter = new AtomicInteger(0);
        progress.put(key, counter);
        startPolling();
        final String out = new File(cacheDir(), FemboyMusic.safe(key)).getAbsolutePath();
        downloadPool.execute(() -> {
            Result result;
            String path = null;
            try {
                final String json = call("download", new Class<?>[]{String.class, String.class, String.class, String.class, AtomicInteger.class},
                        track.source, track.extId, track.url(), out, counter);
                final JSONObject root = new JSONObject(json);
                if (root.optBoolean("ok")) {
                    path = root.optString("path", null);
                    result = new Result(true, null, null);
                } else {
                    result = new Result(false, root.optString("error", "other"), root.optString("message"));
                }
            } catch (Throwable e) {
                FileLog.e(e);
                result = new Result(false, "other", e.getMessage());
            }
            final String finalPath = path;
            final Result finalResult = result;
            if (path != null) trimCache(path);
            AndroidUtilities.runOnUIThread(() -> {
                progress.remove(key);
                boolean ok = finalResult.ok && finalPath != null && new File(finalPath).exists();
                if (ok) cachedPaths.put(key, finalPath);
                finish(track, ok, finalResult);
            });
        });
        FemboyMusic.changed();
    }

    private static void finish(FemboyMusic.Track track, boolean ok, Result result) {
        final ArrayList<Utilities.Callback<Boolean>> list = waiters.remove(track.key());
        if (ok) {
            FemboyMusic.onFetched(track);
        } else {
            try {
                BulletinFactory.global().createSimpleBulletin(R.raw.chats_infotip, result.errorText()).show();
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        if (list != null) for (Utilities.Callback<Boolean> one : list) one.run(ok);
        FemboyMusic.changed();
    }

    /** Пока что-то качается, раз в треть секунды просим экраны обновить прогресс. */
    private static void startPolling() {
        if (polling) return;
        polling = true;
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                if (progress.isEmpty()) {
                    polling = false;
                    return;
                }
                FemboyMusic.changed();
                AndroidUtilities.runOnUIThread(this, 330);
            }
        }, 330);
    }

    /** Кэш не больше CACHE_LIMIT: старое удаляем, свежий файл не трогаем. */
    private static void trimCache(String keep) {
        try {
            final File[] files = cacheDir().listFiles();
            if (files == null) return;
            long total = 0;
            for (File f : files) total += f.length();
            if (total <= CACHE_LIMIT) return;
            Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
            for (File f : files) {
                if (total <= CACHE_LIMIT * 3 / 4) break;
                if (f.getAbsolutePath().equals(keep)) continue;
                total -= f.length();
                f.delete();
            }
            cachedPaths.clear();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    /** Понятный текст по коду ошибки из питона. */
    public static String errorText(String error, String message) {
        if (error == null) error = "other";
        switch (error) {
            case "nopython":
                return LocaleController.getString(R.string.FemboyOnlineNoPython);
            case "network":
                return LocaleController.getString(R.string.FemboyOnlineErrorNetwork);
            case "js":
            case "format":
                return LocaleController.getString(R.string.FemboyOnlineErrorYouTube);
            case "blocked":
                return LocaleController.getString(R.string.FemboyOnlineErrorBlocked);
            case "unavailable":
                return LocaleController.getString(R.string.FemboyOnlineErrorUnavailable);
            default:
                String text = message == null ? "" : message.replaceFirst("^ERROR:\\s*", "");
                if (text.length() > 120) text = text.substring(0, 120) + "…";
                return LocaleController.formatString(R.string.FemboyOnlineError, text);
        }
    }
}
