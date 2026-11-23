package org.telegram.femboy;

import android.graphics.Bitmap;
import android.net.Uri;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Баннер профиля — картинка за аватаркой.
 *
 * Лежит не у нас, а в общей группе: человек отправляет туда фотографию с
 * меткой, и она становится его баннером. Чей баннер — видно по тому, кто
 * отправил, а подпись под сообщением ставит телеграм. Подделать нельзя, и
 * проверять нам нечего.
 *
 * Сменить баннер — это отправить новый и удалить старый. Удалить — просто
 * удалить своё сообщение. Обе вещи человек может сделать и руками, прямо в
 * группе, без нашего приложения: мы не владеем его баннером, мы его только
 * показываем.
 */
public class FemboyBanner {

    /** Найденное держим в памяти: профиль перерисовывается часто, баннер редко. */
    private static final HashMap<Long, Bitmap> pictures = new HashMap<>();
    private static final HashMap<Long, Integer> ownMessage = new HashMap<>();
    private static final Set<Long> looking = new HashSet<>();
    private static final Set<Long> missing = new HashSet<>();

    private static AccountInstance account() {
        return AccountInstance.getInstance(UserConfig.selectedAccount);
    }

    private static long me() {
        try {
            return UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * Баннер этого человека, если он уже у нас есть. Зовётся из отрисовки,
     * поэтому ничего не ждёт: нет — рисуем как раньше, а картинку тем
     * временем спросят у сервера Femboy и позовут {@code whenReady}.
     */
    public static Bitmap of(long userId, Runnable whenReady) {
        if (userId <= 0 || (!FemboyConfig.bannersEnabled() && userId != me())) {
            return null;
        }
        synchronized (pictures) {
            final Bitmap ready = pictures.get(userId);
            if (ready != null) {
                return ready;
            }
            if (missing.contains(userId) || looking.contains(userId)) {
                return null;
            }
            looking.add(userId);
        }
        FemboyHelperBot.info(userId, info -> {
            if (info == null || info.banner == 0) {
                synchronized (pictures) {
                    looking.remove(userId);
                    if (info != null) {
                        missing.add(userId);   // спросили и узнали: баннера нет
                    }
                }
                return;
            }
            FemboyHelperBot.banner(userId, info.banner, bitmap -> {
                synchronized (pictures) {
                    looking.remove(userId);
                    if (bitmap != null) {
                        pictures.put(userId, bitmap);
                    }
                }
                if (bitmap != null && whenReady != null) {
                    whenReady.run();
                }
            });
        });
        return null;
    }

    /**
     * Забирает саму картинку. Файл может быть уже скачан — телеграм хранит
     * скачанное у себя, и второй раз в сеть за ним не пойдёт.
     */
    private static void load(long userId, MessageObject message, Runnable whenReady) {
        try {
            final TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(
                    message.photoThumbs, 1280, false, null, true);
            if (size == null) {
                synchronized (pictures) {
                    looking.remove(userId);
                    missing.add(userId);
                }
                return;
            }
            final File file = FileLoader.getInstance(UserConfig.selectedAccount)
                    .getPathToAttach(size, true);
            if (file != null && file.exists()) {
                decode(userId, file, whenReady);
                return;
            }
            // Дожидаемся именно окончания загрузки, а не «двух с половиной
            // секунд». Раньше здесь стоял отложенный взгляд на файл: не успел
            // скачаться — баннер бросали до следующего открытия профиля.
            // Картинка приезжала когда угодно, только не тогда, когда её
            // ждали, и выглядело это так, будто баннеров нет вовсе.
            waitFor(userId, size, message, whenReady);
        } catch (Throwable t) {
            FileLog.e(t);
            synchronized (pictures) {
                looking.remove(userId);
                missing.add(userId);
            }
        }
    }

    /**
     * Подписывается на окончание загрузки этого файла и уходит, как только оно
     * случилось. Отписываемся сразу же: подписка, которую забыли снять,
     * переживёт и профиль, и сам баннер.
     */
    private static void waitFor(long userId, TLRPC.PhotoSize size, MessageObject message,
                                Runnable whenReady) {
        final String name = FileLoader.getAttachFileName(size);
        final NotificationCenter center = NotificationCenter.getInstance(UserConfig.selectedAccount);
        final NotificationCenter.NotificationCenterDelegate[] holder =
                new NotificationCenter.NotificationCenterDelegate[1];
        holder[0] = (id, account, args) -> {
            if (args.length == 0 || !name.equals(args[0])) {
                return;
            }
            center.removeObserver(holder[0], NotificationCenter.fileLoaded);
            center.removeObserver(holder[0], NotificationCenter.fileLoadFailed);
            if (id == NotificationCenter.fileLoadFailed) {
                synchronized (pictures) {
                    looking.remove(userId);
                }
                return;     // не «нет баннера», а «не скачался» — попробуем ещё
            }
            final File ready = FileLoader.getInstance(UserConfig.selectedAccount)
                    .getPathToAttach(size, true);
            if (ready != null && ready.exists()) {
                decode(userId, ready, whenReady);
            } else {
                synchronized (pictures) {
                    looking.remove(userId);
                }
            }
        };
        center.addObserver(holder[0], NotificationCenter.fileLoaded);
        center.addObserver(holder[0], NotificationCenter.fileLoadFailed);
        FileLoader.getInstance(UserConfig.selectedAccount).loadFile(
                ImageLocation.getForObject(size, message.photoThumbsObject), message,
                null, FileLoader.PRIORITY_NORMAL, FileLoader.PRELOAD_CACHE_TYPE);
    }

    private static void decode(long userId, File file, Runnable whenReady) {
        Bitmap bitmap = null;
        try {
            final android.graphics.BitmapFactory.Options options =
                    new android.graphics.BitmapFactory.Options();
            // Баннер рисуется полосой в пару сотен точек высотой. Держать в
            // памяти полноразмерную фотографию ради этого незачем.
            options.inSampleSize = 2;
            bitmap = android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        } catch (Throwable t) {
            FileLog.e(t);
        }
        synchronized (pictures) {
            looking.remove(userId);
            if (bitmap != null) {
                pictures.put(userId, bitmap);
            } else {
                missing.add(userId);
            }
        }
        if (bitmap != null && whenReady != null) {
            AndroidUtilities.runOnUIThread(whenReady);
        }
    }

    /** Показать только что выбранную картинку, не дожидаясь сервера. */
    private static void preview(long userId, Uri image) {
        try (java.io.InputStream in = org.telegram.messenger.ApplicationLoader
                .applicationContext.getContentResolver().openInputStream(image)) {
            if (in == null) {
                return;
            }
            final android.graphics.BitmapFactory.Options options =
                    new android.graphics.BitmapFactory.Options();
            options.inSampleSize = 2;
            final Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(in, null, options);
            if (bitmap == null) {
                return;
            }
            synchronized (pictures) {
                pictures.put(userId, bitmap);
                missing.remove(userId);
                looking.remove(userId);
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    /** Забыть скачанное: свой баннер поменяли — старый показывать нельзя. */
    public static void forget(long userId) {
        synchronized (pictures) {
            pictures.remove(userId);
            missing.remove(userId);
            looking.remove(userId);
        }
    }

    /**
     * Поставить себе баннер: тихо отправить картинку боту Femboy Helper. Свою
     * картинку показываем сразу, не дожидаясь сервера.
     */
    public static void set(Uri image, Runnable done) {
        final long id = me();
        if (id <= 0 || image == null) {
            if (done != null) {
                done.run();
            }
            return;
        }
        FemboyHelperBot.send("/gram_banner", image, ok -> {
            if (ok) {
                forget(id);
                preview(id, image);
                final FemboyHelperBot.Info was = FemboyHelperBot.known(id);
                FemboyHelperBot.remember(id, System.currentTimeMillis() / 1000, was != null ? was.gradient : null);
            }
            if (done != null) {
                done.run();
            }
        });
    }

    /** Убрать свой баннер: команда боту. Ответ честный: убрали или убирать было нечего. */
    public static void clear(FemboyGroup.Removed done) {
        final long id = me();
        if (id <= 0) {
            answer(done, FemboyGroup.FAILED);
            return;
        }
        final FemboyHelperBot.Info was = FemboyHelperBot.known(id);
        FemboyHelperBot.send("/gram_banner_off", null, ok -> {
            if (!ok) {
                answer(done, FemboyGroup.FAILED);
                return;
            }
            forget(id);
            synchronized (pictures) {
                missing.add(id);
            }
            FemboyHelperBot.remember(id, 0, was != null ? was.gradient : null);
            answer(done, was != null && was.banner == 0 ? FemboyGroup.NOTHING : FemboyGroup.REMOVED);
        });
    }

    private static void answer(FemboyGroup.Removed done, int what) {
        if (done != null) {
            AndroidUtilities.runOnUIThread(() -> done.onRemoved(what));
        }
    }
}
