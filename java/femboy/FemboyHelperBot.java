package org.telegram.femboy;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;

/**
 * Баннер и градиент профиля через бота Femboy Helper.
 *
 * Поставить или убрать: клиент тихо шлёт боту команду со своего аккаунта
 * (баннер картинкой с подписью). Кто прислал, говорит сам телеграм, подделать
 * это нельзя, поэтому баннер ставит только владелец аккаунта. Бот запоминает
 * и сразу удаляет команду из переписки.
 *
 * Посмотреть: сервер Femboy отдаёт по айди телеграма что стоит
 * (/api/gram/айди) и саму картинку (/api/gram/banner/айди).
 */
public class FemboyHelperBot {

    public static final String USERNAME = "femboy_robot";
    private static final String SITE = "https://github.com/webrch/femboy-gram";
    /** Сколько помним чужой ответ, прежде чем спросить снова. */
    private static final long FRESH = 10 * 60 * 1000L;

    /** Что стоит у человека. banner — номер версии картинки, 0 — нет. */
    public static final class Info {
        public final long banner;
        public final int[] gradient;
        final long at;

        Info(long banner, int[] gradient) {
            this.banner = banner;
            this.gradient = gradient;
            this.at = System.currentTimeMillis();
        }
    }

    public interface Got<T> {
        void got(T value);
    }

    private static final HashMap<Long, Info> infos = new HashMap<>();
    private static final HashMap<Long, java.util.ArrayList<Got<Info>>> asking = new HashMap<>();

    /** Уже известное, без сети. Null — не спрашивали или устарело. */
    public static Info known(long uid) {
        synchronized (infos) {
            final Info info = infos.get(uid);
            return info != null && System.currentTimeMillis() - info.at < FRESH ? info : null;
        }
    }

    /** Забыть, чтобы спросить заново. Своё свежее изменение не забывается. */
    public static void forget(long uid) {
        synchronized (infos) {
            final Long until = pinnedUntil.get(uid);
            if (until != null && until > System.currentTimeMillis()) {
                return;
            }
            infos.remove(uid);
        }
    }

    /** Спросить сервер в обход памяти: подтвердилось ли своё изменение. */
    public static void recheck(long uid, Got<Info> done) {
        synchronized (infos) {
            final Info keep = infos.get(uid);
            infos.remove(uid);
            if (keep != null) {
                pendingKeep.put(uid, keep);
            }
        }
        info(uid, done);
    }

    private static final HashMap<Long, Info> pendingKeep = new HashMap<>();

    /**
     * Своё изменение держится, пока сервер не подтвердит то же самое (или
     * минуту): иначе ответ, ушедший до того, как бот разобрал команду, вернул
     * бы на экран старое.
     */
    private static final HashMap<Long, Long> pinnedUntil = new HashMap<>();

    /** Положить своё сразу после изменения, не дожидаясь сервера. */
    static void remember(long uid, long banner, int[] gradient) {
        synchronized (infos) {
            infos.put(uid, new Info(banner, gradient));
            pinnedUntil.put(uid, System.currentTimeMillis() + 60_000L);
        }
    }

    private static boolean same(Info a, Info b) {
        if ((a.banner > 0) != (b.banner > 0)) return false;
        if (a.gradient == null || b.gradient == null) return a.gradient == b.gradient;
        return a.gradient[0] == b.gradient[0] && a.gradient[1] == b.gradient[1];
    }

    /** Спросить сервер, что стоит у человека. Ответ на главном потоке; при ошибке сети null. */
    public static void info(long uid, Got<Info> done) {
        final Info ready = known(uid);
        if (ready != null) {
            if (done != null) {
                AndroidUtilities.runOnUIThread(() -> done.got(ready));
            }
            return;
        }
        synchronized (asking) {
            java.util.ArrayList<Got<Info>> waiting = asking.get(uid);
            if (waiting != null) {
                if (done != null) waiting.add(done);
                return;
            }
            waiting = new java.util.ArrayList<>();
            if (done != null) waiting.add(done);
            asking.put(uid, waiting);
        }
        Utilities.globalQueue.postRunnable(() -> {
            Info info = null;
            try {
                final byte[] body = get(SITE + "/api/gram/" + uid);
                if (body != null) {
                    final JSONObject json = new JSONObject(new String(body, "UTF-8"));
                    final JSONArray colors = json.optJSONArray("gradient");
                    int[] gradient = null;
                    if (colors != null && colors.length() >= 2) {
                        gradient = new int[]{
                                0xFF000000 | Integer.parseInt(colors.getString(0), 16),
                                0xFF000000 | Integer.parseInt(colors.getString(1), 16)};
                    }
                    info = new Info(json.optLong("banner", 0), gradient);
                    synchronized (infos) {
                        Info kept = pendingKeep.remove(uid);
                        if (kept == null) {
                            kept = infos.get(uid);
                        }
                        final Long until = pinnedUntil.get(uid);
                        if (kept != null && until != null && until > System.currentTimeMillis() && !same(kept, info)) {
                            info = kept;   // сервер ещё не знает про свежее изменение
                        } else {
                            pinnedUntil.remove(uid);
                        }
                        infos.put(uid, info);
                    }
                }
            } catch (Throwable t) {
                FileLog.e(t);
            }
            final java.util.ArrayList<Got<Info>> waiting;
            synchronized (asking) {
                waiting = asking.remove(uid);
            }
            final Info result = info;
            if (waiting != null) {
                AndroidUtilities.runOnUIThread(() -> {
                    for (Got<Info> one : waiting) {
                        one.got(result);
                    }
                });
            }
        });
    }

    /** Картинка баннера этой версии. Кэшируется файлом, так что второй раз сеть не нужна. */
    public static void banner(long uid, long version, Got<Bitmap> done) {
        Utilities.globalQueue.postRunnable(() -> {
            Bitmap bitmap = null;
            try {
                final java.io.File dir = new java.io.File(org.telegram.messenger.ApplicationLoader.applicationContext.getCacheDir(), "femboy_banners");
                dir.mkdirs();
                final java.io.File file = new java.io.File(dir, uid + "_" + version + ".jpg");
                if (!file.exists()) {
                    final byte[] body = get(SITE + "/api/gram/banner/" + uid + "?v=" + version);
                    if (body != null) {
                        try (java.io.FileOutputStream out = new java.io.FileOutputStream(file)) {
                            out.write(body);
                        }
                    }
                }
                if (file.exists()) {
                    final BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = 2;
                    bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
                }
            } catch (Throwable t) {
                FileLog.e(t);
            }
            final Bitmap result = bitmap;
            AndroidUtilities.runOnUIThread(() -> done.got(result));
        });
    }

    private static byte[] get(String address) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(address).openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(20000);
            connection.setRequestProperty("User-Agent", "FemboyGram/" + FemboyConfig.APP_VERSION);
            if (connection.getResponseCode() != 200) {
                return null;
            }
            try (InputStream in = connection.getInputStream()) {
                final ByteArrayOutputStream out = new ByteArrayOutputStream();
                final byte[] buffer = new byte[16384];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    out.write(buffer, 0, read);
                    if (out.size() > 8 * 1024 * 1024) {
                        return null;
                    }
                }
                return out.toByteArray();
            }
        } catch (Throwable t) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    // ------------------------------------------------------------ команды боту

    private static AccountInstance account() {
        return AccountInstance.getInstance(UserConfig.selectedAccount);
    }

    private static long botId;

    /** Айди бота: из памяти телеграма или спросив по юзернейму. */
    private static void resolve(Got<Long> done) {
        if (botId != 0) {
            done.got(botId);
            return;
        }
        account().getMessagesController().getUserNameResolver().resolve(USERNAME, id -> {
            if (id != null && id > 0) {
                botId = id;
            }
            done.got(id == null ? 0L : id);
        });
    }

    /** Тихо отправить боту команду; с картинкой, если она есть. Ответ: ушло или нет. */
    public static void send(String command, Uri picture, Got<Boolean> done) {
        resolve(id -> {
            if (id == null || id <= 0) {
                if (done != null) done.got(false);
                return;
            }
            try {
                if (picture != null) {
                    SendMessagesHelper.prepareSendingPhoto(account(), null, picture, id,
                            null, null, null, command, null, null, null, 0,
                            null, false, 0, 0, null);
                } else {
                    final SendMessagesHelper.SendMessageParams params = SendMessagesHelper.SendMessageParams.of(command, id);
                    params.notify = false;
                    account().getSendMessagesHelper().sendMessage(params);
                }
                if (done != null) done.got(true);
            } catch (Throwable t) {
                FileLog.e(t);
                if (done != null) done.got(false);
            }
        });
    }


    /** Badge commands were removed in FemboyGram; kept so the sender compiles. */
    public static boolean giveBadge(int account, SendMessagesHelper.SendMessageParams params) {
        return false;
    }

}
