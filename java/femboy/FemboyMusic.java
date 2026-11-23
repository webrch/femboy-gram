package org.telegram.femboy;

import android.text.TextUtils;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Музыка FemboyGram: свои плейлисты, «Понравившиеся», недавно игравшее и
 * треки из сети (SoundCloud, YouTube Music). Играет всё через обычный
 * MediaController телеграма.
 *
 * Трек из чата хранится как само сообщение (TLRPC.Message в base64), номер
 * чата и аккаунт: из него снова собирается MessageObject, и телеграм сам
 * обновляет ссылку на файл. Трек из сети хранится как источник, номер,
 * название, исполнитель, обложка и ссылка; для плеера из него собирается
 * MessageObject с локальным файлом.
 *
 * Лайкнутые треки копируются в filesDir/femboy_music, чтобы играть без сети
 * и пережить удаление чата. Всё описание лежит в femboy_music.json.
 */
public final class FemboyMusic {

    private FemboyMusic() {}

    private static final int RECENT_MOST = 50;

    /** Номер плейлиста «Понравившиеся». */
    public static final long LIKED = -2;

    public static final String TG = "tg";
    public static final String SC = "sc";
    public static final String YT = "yt";

    // настройки

    /** Свой плеер всегда: выключателя больше нет. */
    public static boolean ownPlayer() {
        return true;
    }

    public static void setOwnPlayer(boolean on) {
        FemboyConfig.prefs().edit().putBoolean("own_player", on).apply();
    }

    public static boolean musicTab() {
        return FemboyConfig.prefs().getBoolean("music_tab", true);
    }

    public static void setMusicTab(boolean on) {
        FemboyConfig.prefs().edit().putBoolean("music_tab", on).apply();
    }

    // данные

    /** Один трек: из чата (сообщение) или из сети (описание). */
    public static final class Track {
        public final String source;
        public final int account;
        public final long dialogId;
        public final int messageId;
        final String data;
        /** Для треков из сети. */
        public final String extId;
        final String title, artist, cover, url;
        final int duration;
        private TLRPC.Message message;
        private String[] info;

        Track(int account, long dialogId, int messageId, String data) {
            this.source = TG;
            this.account = account;
            this.dialogId = dialogId;
            this.messageId = messageId;
            this.data = data;
            this.extId = null;
            this.title = null;
            this.artist = null;
            this.cover = null;
            this.url = null;
            this.duration = 0;
        }

        public Track(String source, String extId, String title, String artist, int duration, String cover, String url) {
            this.source = source;
            this.account = 0;
            this.dialogId = 0;
            this.messageId = 0;
            this.data = null;
            this.extId = extId;
            this.title = title == null ? "" : title;
            this.artist = artist == null ? "" : artist;
            this.duration = duration;
            this.cover = cover;
            this.url = url;
        }

        public boolean external() {
            return !TG.equals(source);
        }

        public String key() {
            return external() ? source + ":" + extId : account + "_" + dialogId + "_" + messageId;
        }

        public String title() {
            if (external()) return title;
            final String[] i = info();
            return i[0];
        }

        public String artist() {
            if (external()) return artist;
            final String[] i = info();
            return i[1];
        }

        public int duration() {
            if (external()) return duration;
            final String[] i = info();
            return Utilities.parseInt(i[2]);
        }

        public String cover() {
            return cover;
        }

        public String url() {
            return url;
        }

        /** Название, исполнитель и длина из самого сообщения. */
        private String[] info() {
            if (info != null) return info;
            String t = "", a = "";
            int d = 0;
            final TLRPC.Message m = raw();
            if (m != null) {
                final TLRPC.Document document = MessageObject.getDocument(m);
                if (document != null) {
                    for (TLRPC.DocumentAttribute attribute : document.attributes) {
                        if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                            if (attribute.title != null) t = attribute.title;
                            if (attribute.performer != null) a = attribute.performer;
                            d = (int) attribute.duration;
                        }
                    }
                    if (TextUtils.isEmpty(t)) t = FileLoader.getDocumentFileName(document);
                }
            }
            info = new String[]{t, a, String.valueOf(d)};
            return info;
        }

        private TLRPC.Message raw() {
            if (message == null && data != null) {
                message = decode(data);
                if (message != null) message.dialog_id = dialogId;
            }
            return message;
        }

        /** Скачанный к себе файл, если есть. */
        public File localFile() {
            return FemboyMusic.saved(key());
        }

        /** Свежий MessageObject для плеера; null, если аккаунта уже нет или данные битые. */
        public MessageObject message() {
            if (external()) {
                return externalMessage(this);
            }
            if (!UserConfig.isValidAccount(account) || !UserConfig.getInstance(account).isClientActivated()) {
                return null;
            }
            final TLRPC.Message m = raw();
            if (m == null) return null;
            // лайкнутый трек играет из своей копии: живая ссылка телеграма не нужна
            final File local = localFile();
            if (local != null) m.attachPath = local.getAbsolutePath();
            try {
                return new MessageObject(account, m, false, true);
            } catch (Throwable error) {
                FileLog.e(error);
                return null;
            }
        }

        JSONObject json() throws Exception {
            final JSONObject one = new JSONObject();
            if (external()) {
                one.put("s", source).put("x", extId).put("t", title).put("p", artist).put("u", duration);
                if (cover != null) one.put("c", cover);
                if (url != null) one.put("w", url);
            } else {
                one.put("a", account).put("d", dialogId).put("m", messageId).put("b", data);
            }
            final File local = localFile();
            if (local != null) one.put("l", local.getAbsolutePath());
            return one;
        }

        static Track of(JSONObject one) {
            final String source = one.optString("s", TG);
            Track track;
            if (TG.equals(source)) {
                final String data = one.optString("b", null);
                if (TextUtils.isEmpty(data)) return null;
                track = new Track(one.optInt("a"), one.optLong("d"), one.optInt("m"), data);
            } else {
                final String id = one.optString("x", null);
                if (TextUtils.isEmpty(id)) return null;
                track = new Track(source, id, one.optString("t"), one.optString("p"), one.optInt("u"),
                        one.optString("c", null), one.optString("w", null));
            }
            final String local = one.optString("l", null);
            if (!TextUtils.isEmpty(local) && !locals.containsKey(track.key())) {
                locals.put(track.key(), local);
            }
            return track;
        }
    }

    /** Плейлист: название и треки по порядку. */
    public static final class Playlist {
        public final long id;
        public String name;
        public final ArrayList<Track> tracks = new ArrayList<>();

        Playlist(long id, String name) {
            this.id = id;
            this.name = name;
        }

        public boolean has(Track track) {
            return has(track.key());
        }

        public boolean has(String key) {
            for (Track one : tracks) if (one.key().equals(key)) return true;
            return false;
        }

        public boolean liked() {
            return id == LIKED;
        }
    }

    private static boolean loaded;
    private static final ArrayList<Playlist> playlists = new ArrayList<>();
    private static final ArrayList<Track> recent = new ArrayList<>();
    private static Playlist liked;
    /** Ключ трека и путь к его копии у нас. */
    private static final ConcurrentHashMap<String, String> locals = new ConcurrentHashMap<>();

    private static File file() {
        return new File(ApplicationLoader.getFilesDirFixed(), "femboy_music.json");
    }

    /** Папка с копиями лайкнутых треков. */
    public static File offlineDir() {
        final File dir = new File(ApplicationLoader.getFilesDirFixed(), "femboy_music");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    static String safe(String key) {
        return key.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    static File saved(String key) {
        final String path = locals.get(key);
        if (path == null) return null;
        final File file = new File(path);
        return file.exists() && file.length() > 0 ? file : null;
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        liked = new Playlist(LIKED, LocaleController.getString(R.string.FemboyLiked));
        File file = file();
        if (!file.exists()) return;
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] bytes = new byte[(int) file.length()];
            int read = 0;
            while (read < bytes.length) {
                int n = in.read(bytes, read, bytes.length - read);
                if (n < 0) break;
                read += n;
            }
            JSONObject root = new JSONObject(new String(bytes, 0, read, StandardCharsets.UTF_8));
            JSONObject paths = root.optJSONObject("locals");
            if (paths != null) {
                for (java.util.Iterator<String> it = paths.keys(); it.hasNext(); ) {
                    final String key = it.next();
                    locals.put(key, paths.optString(key));
                }
            }
            JSONArray lists = root.optJSONArray("playlists");
            if (lists != null) {
                for (int i = 0; i < lists.length(); i++) {
                    JSONObject one = lists.getJSONObject(i);
                    Playlist playlist = new Playlist(one.optLong("id"), one.optString("name"));
                    readTracks(one.optJSONArray("tracks"), playlist.tracks);
                    playlists.add(playlist);
                }
            }
            readTracks(root.optJSONArray("liked"), liked.tracks);
            readTracks(root.optJSONArray("recent"), recent);
        } catch (Throwable error) {
            FileLog.e(error);
        }
    }

    private static void readTracks(JSONArray array, ArrayList<Track> into) throws Exception {
        if (array == null) return;
        for (int k = 0; k < array.length(); k++) {
            Track track = Track.of(array.getJSONObject(k));
            if (track != null) into.add(track);
        }
    }

    private static JSONArray writeTracks(ArrayList<Track> tracks) throws Exception {
        JSONArray array = new JSONArray();
        for (Track track : tracks) array.put(track.json());
        return array;
    }

    /** Пишем снимок на фоне: сам JSON собирается здесь, пока список не поменялся. */
    static void save() {
        final String text;
        try {
            JSONObject root = new JSONObject();
            JSONArray lists = new JSONArray();
            for (Playlist playlist : playlists) {
                lists.put(new JSONObject().put("id", playlist.id).put("name", playlist.name).put("tracks", writeTracks(playlist.tracks)));
            }
            root.put("playlists", lists);
            root.put("liked", writeTracks(liked.tracks));
            root.put("recent", writeTracks(recent));
            JSONObject paths = new JSONObject();
            for (java.util.Map.Entry<String, String> e : locals.entrySet()) paths.put(e.getKey(), e.getValue());
            root.put("locals", paths);
            text = root.toString();
        } catch (Throwable error) {
            FileLog.e(error);
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            File file = file();
            File temp = new File(file.getPath() + ".tmp");
            try (FileOutputStream out = new FileOutputStream(temp)) {
                out.write(text.getBytes(StandardCharsets.UTF_8));
                out.getFD().sync();
            } catch (Throwable error) {
                FileLog.e(error);
                return;
            }
            if (!temp.renameTo(file)) {
                FileLog.e("femboy music: rename failed");
            }
        });
        changed();
    }

    private static String encode(TLRPC.Message message) {
        SerializedData out = null;
        try {
            out = new SerializedData(message.getObjectSize());
            message.serializeToStream(out);
            return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
        } catch (Throwable error) {
            FileLog.e(error);
            return null;
        } finally {
            if (out != null) out.cleanup();
        }
    }

    private static TLRPC.Message decode(String data) {
        SerializedData in = null;
        try {
            in = new SerializedData(Base64.decode(data, Base64.DEFAULT));
            return TLRPC.Message.TLdeserialize(in, in.readInt32(false), false);
        } catch (Throwable error) {
            FileLog.e(error);
            return null;
        } finally {
            if (in != null) in.cleanup();
        }
    }

    /** Можно ли положить сообщение в плейлист: песня из обычного чата или из сети. */
    public static boolean canAdd(MessageObject message) {
        if (isExternal(message)) return true;
        return message != null && message.messageOwner != null && message.isMusic() && message.getId() > 0
                && !message.scheduled && !DialogObject.isEncryptedDialog(message.getDialogId());
    }

    public static Track track(MessageObject message) {
        if (message == null || message.messageOwner == null) return null;
        final Track external = externalIds.get(message.getId());
        if (external != null && message.getDialogId() == 0) return external;
        String data = encode(message.messageOwner);
        if (data == null) return null;
        return new Track(message.currentAccount, message.getDialogId(), message.getId(), data);
    }

    /** Ключ трека для сравнения: тот же, что у Track.key(). */
    public static String keyOf(MessageObject message) {
        if (message == null) return null;
        final Track external = message.getDialogId() == 0 ? externalIds.get(message.getId()) : null;
        if (external != null) return external.key();
        return message.currentAccount + "_" + message.getDialogId() + "_" + message.getId();
    }

    /** Свои плейлисты, без «Понравившихся». */
    public static ArrayList<Playlist> playlists() {
        load();
        return playlists;
    }

    public static Playlist liked() {
        load();
        return liked;
    }

    public static Playlist playlist(long id) {
        if (id == LIKED) return liked();
        for (Playlist playlist : playlists()) if (playlist.id == id) return playlist;
        return null;
    }

    public static Playlist create(String name) {
        load();
        long id = System.currentTimeMillis();
        while (playlist(id) != null) id++;
        Playlist playlist = new Playlist(id, name);
        playlists.add(playlist);
        save();
        return playlist;
    }

    public static void rename(Playlist playlist, String name) {
        if (playlist.liked()) return;
        playlist.name = name;
        save();
    }

    public static void delete(Playlist playlist) {
        if (playlist.liked()) return;
        playlists.remove(playlist);
        for (Track track : playlist.tracks) forgetLocalIfUnused(track.key());
        save();
    }

    /** false, если такой трек там уже есть. */
    public static boolean add(Playlist playlist, MessageObject message) {
        Track track = track(message);
        return add(playlist, track);
    }

    public static boolean add(Playlist playlist, Track track) {
        if (track == null || playlist.has(track)) return false;
        playlist.tracks.add(track);
        save();
        // трек из сети в плейлисте лучше держать у себя: иначе он без сети молчит
        if (track.external()) keepOffline(track, null);
        return true;
    }

    public static void remove(Playlist playlist, Track track) {
        playlist.tracks.remove(track);
        forgetLocalIfUnused(track.key());
        save();
    }

    public static void reorder(Playlist playlist, ArrayList<Track> order) {
        if (order.size() != playlist.tracks.size()) return;
        playlist.tracks.clear();
        playlist.tracks.addAll(order);
        save();
    }

    public static ArrayList<Track> recent() {
        load();
        return recent;
    }

    public static void clearRecent() {
        load();
        recent.clear();
        save();
    }

    // кто смотрит на музыку: экраны подписываются, чтобы перерисоваться

    private static final ArrayList<Runnable> listeners = new ArrayList<>();

    public static void addListener(Runnable listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    public static void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    static void changed() {
        AndroidUtilities.runOnUIThread(() -> {
            for (Runnable one : new ArrayList<>(listeners)) one.run();
        });
    }

    // треки из сети

    /** Номер сообщения и трек из сети, для которого оно собрано. */
    private static final ConcurrentHashMap<Integer, Track> externalIds = new ConcurrentHashMap<>();

    /** Отрицательный номер сообщения, свой у каждого трека и постоянный. */
    static int fakeId(String key) {
        int hash = key.hashCode() & 0x3fffffff;
        return -(hash + 1000);
    }

    /** Готовый к игре файл трека из сети или null, если ещё не скачан. */
    public static File playableFile(Track track) {
        final File local = track.localFile();
        if (local != null) return local;
        return FemboyMusicOnline.cached(track);
    }

    static MessageObject externalMessage(Track track) {
        final int account = UserConfig.selectedAccount;
        final TLRPC.TL_message m = new TLRPC.TL_message();
        m.id = fakeId(track.key());
        m.out = true;
        m.date = (int) (System.currentTimeMillis() / 1000);
        m.peer_id = new TLRPC.TL_peerUser();
        m.dialog_id = 0;
        m.message = "";
        final File file = playableFile(track);
        final TLRPC.TL_messageMediaDocument media = new TLRPC.TL_messageMediaDocument();
        final TLRPC.TL_document document = new TLRPC.TL_document();
        document.id = m.id;
        document.date = m.date;
        document.file_reference = new byte[0];
        document.dc_id = 0;
        document.mime_type = mime(file);
        document.size = file != null ? file.length() : 0;
        final TLRPC.TL_documentAttributeAudio audio = new TLRPC.TL_documentAttributeAudio();
        audio.duration = track.duration;
        audio.title = track.title;
        audio.performer = track.artist;
        audio.flags |= 3;
        document.attributes.add(audio);
        final TLRPC.TL_documentAttributeFilename name = new TLRPC.TL_documentAttributeFilename();
        name.file_name = safe(track.artist + " - " + track.title) + extension(file);
        document.attributes.add(name);
        media.document = document;
        media.flags |= 1;
        m.media = media;
        m.attachPath = file != null ? file.getAbsolutePath() : "";
        externalIds.put(m.id, track);
        try {
            return new MessageObject(account, m, false, false);
        } catch (Throwable error) {
            FileLog.e(error);
            return null;
        }
    }

    private static String extension(File file) {
        if (file == null) return ".m4a";
        final String name = file.getName();
        final int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot) : "";
    }

    private static String mime(File file) {
        final String ext = extension(file).toLowerCase(Locale.ROOT);
        switch (ext) {
            case ".mp3": return "audio/mpeg";
            case ".opus":
            case ".ogg": return "audio/ogg";
            case ".webm": return "audio/webm";
            case ".mp4": return "audio/mp4";
            case ".aac": return "audio/aac";
            default: return "audio/mp4";
        }
    }

    // воспроизведение

    private static String source;
    private static final HashSet<String> sourceKeys = new HashSet<>();
    private static int playSeq;

    /**
     * Играть список с трека start. shuffle: true перемешать, false по порядку,
     * null оставить как выбрано в плеере. Если первый трек из сети ещё не
     * скачан, сначала скачиваем его (прогресс виден в downloadProgress).
     */
    public static boolean play(ArrayList<Track> tracks, int start, Boolean shuffle, String name) {
        if (tracks == null || tracks.isEmpty()) return false;
        if (shuffle != null && shuffle && (start < 0 || start >= tracks.size())) {
            start = Utilities.random.nextInt(tracks.size());
        }
        if (start < 0 || start >= tracks.size()) start = 0;
        final Track first = tracks.get(start);
        final int seq = ++playSeq;
        if (first.external() && playableFile(first) == null) {
            final int from = start;
            FemboyMusicOnline.fetch(first, ok -> {
                // пока качали, человек мог выбрать другой трек
                if (ok && seq == playSeq) playNow(tracks, from, shuffle, name);
            });
            changed();
            return true;
        }
        return playNow(tracks, start, shuffle, name);
    }

    private static boolean playNow(ArrayList<Track> tracks, int start, Boolean shuffle, String name) {
        ArrayList<MessageObject> list = new ArrayList<>();
        MessageObject first = null;
        HashSet<String> keys = new HashSet<>();
        for (int i = 0; i < tracks.size(); i++) {
            MessageObject one = tracks.get(i).message();
            if (one == null || !one.isMusic()) continue;
            list.add(one);
            keys.add(tracks.get(i).key());
            if (i == start) first = one;
        }
        if (list.isEmpty()) return false;
        if (shuffle != null) {
            SharedConfig.setPlaybackOrderType(shuffle ? 2 : 0);
        } else if (SharedConfig.playOrderReversed) {
            SharedConfig.setPlaybackOrderType(0);
        }
        if (first == null) first = list.get(0);
        source = name;
        sourceKeys.clear();
        sourceKeys.addAll(keys);
        final boolean ok = MediaController.getInstance().setOrderedPlaylist(list, first);
        prefetchAround(first);
        changed();
        return ok;
    }

    /** Играть готовые сообщения из чатов (поиск, «Из твоих чатов»). */
    public static boolean playMessages(ArrayList<MessageObject> messages, int start, String name) {
        if (messages == null || messages.isEmpty()) return false;
        playSeq++;
        if (start < 0 || start >= messages.size()) start = 0;
        if (SharedConfig.playOrderReversed) SharedConfig.setPlaybackOrderType(0);
        source = name;
        sourceKeys.clear();
        for (MessageObject one : messages) sourceKeys.add(keyOf(one));
        final boolean ok = MediaController.getInstance().setOrderedPlaylist(new ArrayList<>(messages), messages.get(start));
        changed();
        return ok;
    }

    /** Название нашего плейлиста, из которого играет это сообщение, или null. */
    public static String sourceOf(MessageObject message) {
        if (message == null || source == null) return null;
        return sourceKeys.contains(keyOf(message)) ? source : null;
    }

    /** Это сообщение сейчас играет (или грузится для игры). */
    public static boolean isPlaying(String key) {
        if (key == null) return false;
        final MessageObject now = MediaController.getInstance().getPlayingMessageObject();
        return now != null && key.equals(keyOf(now));
    }

    // подкачка треков из сети, пока играет соседний

    /** Скачать соседей текущего трека, чтобы переход был без паузы. */
    private static void prefetchAround(MessageObject current) {
        final ArrayList<MessageObject> list = MediaController.getInstance().getPlaylist();
        if (list == null || current == null) return;
        final int index = list.indexOf(current);
        if (index < 0) return;
        for (int d = -2; d <= 2; d++) {
            if (d == 0) continue;
            final int i = index + d;
            if (i < 0 || i >= list.size()) continue;
            final MessageObject one = list.get(i);
            if (!isExternal(one)) continue;
            final Track track = externalIds.get(one.getId());
            if (track != null && playableFile(track) == null) {
                FemboyMusicOnline.fetch(track, null);
            }
        }
    }

    /** Скачался трек из сети: подставить файл в сообщения очереди и доиграть текущий. */
    static void onFetched(Track track) {
        final File file = playableFile(track);
        if (file == null) return;
        final MediaController controller = MediaController.getInstance();
        final ArrayList<MessageObject> list = controller.getPlaylist();
        final int id = fakeId(track.key());
        if (list != null) {
            for (MessageObject one : list) {
                if (one.getId() == id && one.getDialogId() == 0) fill(one, file);
            }
        }
        final MessageObject now = controller.getPlayingMessageObject();
        if (now != null && now.getId() == id && now.getDialogId() == 0 && waitingKeys.remove(track.key())) {
            fill(now, file);
            controller.cleanupPlayer(false, false);
            controller.playMessage(now);
        }
    }

    private static void fill(MessageObject message, File file) {
        message.messageOwner.attachPath = file.getAbsolutePath();
        final TLRPC.Document document = message.getDocument();
        if (document != null) {
            document.size = file.length();
            document.mime_type = mime(file);
        }
        message.mediaExists = true;
    }

    /** Треки из сети, которые дошли до плеера раньше, чем скачались. */
    private static final HashSet<String> waitingKeys = new HashSet<>();

    private static void checkPlayingExternal() {
        final MediaController controller = MediaController.getInstance();
        final MessageObject now = controller.getPlayingMessageObject();
        if (now == null || !isExternal(now)) return;
        final Track track = externalIds.get(now.getId());
        if (track == null) return;
        if (playableFile(track) != null) {
            prefetchAround(now);
            return;
        }
        if (waitingKeys.contains(track.key())) return;
        // плеер пытается играть пустышку: держим на паузе, пока не скачаем
        waitingKeys.add(track.key());
        controller.pauseMessage(now);
        FemboyMusicOnline.fetch(track, ok -> {
            if (ok) {
                onFetched(track);
            } else {
                waitingKeys.remove(track.key());
            }
        });
    }

    // недавнее

    private static boolean watching;

    /** Слушаем, что начинает играть, и кладём в «Недавно». Повторный вызов ничего не делает. */
    public static void watch() {
        if (watching) return;
        watching = true;
        final NotificationCenter.NotificationCenterDelegate listener = (id, account, args) -> {
            if (id == NotificationCenter.messagePlayingDidStart) {
                if (args.length == 0 || !(args[0] instanceof MessageObject)) return;
                MessageObject message = (MessageObject) args[0];
                AndroidUtilities.runOnUIThread(FemboyMusic::checkPlayingExternal);
                if (!canAdd(message)) return;
                // кодирование не бесплатное, а событие приходит и на перемотку назад
                AndroidUtilities.runOnUIThread(() -> remember(message));
            } else if (id == NotificationCenter.messagePlayingPlayStateChanged) {
                AndroidUtilities.runOnUIThread(FemboyMusic::checkPlayingExternal);
            }
        };
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            NotificationCenter.getInstance(a).addObserver(listener, NotificationCenter.messagePlayingDidStart);
            NotificationCenter.getInstance(a).addObserver(listener, NotificationCenter.messagePlayingPlayStateChanged);
        }
    }

    private static void remember(MessageObject message) {
        load();
        Track track = track(message);
        if (track == null) return;
        if (!recent.isEmpty() && recent.get(0).key().equals(track.key())) return;
        for (int i = recent.size() - 1; i >= 0; i--) {
            if (recent.get(i).key().equals(track.key())) recent.remove(i);
        }
        recent.add(0, track);
        while (recent.size() > RECENT_MOST) recent.remove(recent.size() - 1);
        save();
    }

    // офлайн-копии

    /** Скачивания из чатов, которые ждём, чтобы скопировать к себе. */
    private static final HashMap<String, Track> pendingCopies = new HashMap<>();
    private static NotificationCenter.NotificationCenterDelegate copyListener;

    /**
     * Положить файл трека к себе. Трек из чата копируется из кэша телеграма
     * (или сначала скачивается), трек из сети переносится из кэша или
     * скачивается заново.
     */
    static void keepOffline(Track track, MessageObject message) {
        if (track.localFile() != null) return;
        if (track.external()) {
            final File cached = FemboyMusicOnline.cached(track);
            if (cached != null) {
                copyIn(track, cached);
            } else {
                FemboyMusicOnline.fetch(track, ok -> {
                    final File file = FemboyMusicOnline.cached(track);
                    if (ok && file != null) copyIn(track, file);
                });
            }
            return;
        }
        if (message == null) message = track.message();
        if (message == null || message.getDocument() == null) return;
        final MessageObject msg = message;
        final int account = msg.currentAccount;
        File have = null;
        if (!TextUtils.isEmpty(msg.messageOwner.attachPath)) {
            final File f = new File(msg.messageOwner.attachPath);
            if (f.exists() && f.length() > 0) have = f;
        }
        if (have == null) {
            final File f = FileLoader.getInstance(account).getPathToMessage(msg.messageOwner);
            if (f != null && f.exists() && f.length() > 0) have = f;
        }
        if (have != null) {
            copyIn(track, have);
            return;
        }
        // файла ещё нет: пусть телеграм скачает, а мы скопируем, когда придёт
        final String name = FileLoader.getAttachFileName(msg.getDocument());
        pendingCopies.put(name, track);
        if (copyListener == null) {
            copyListener = (id, acc, args) -> {
                if (id != NotificationCenter.fileLoaded || args.length < 2) return;
                final Track waiting = pendingCopies.remove((String) args[0]);
                if (waiting != null && args[1] instanceof File) {
                    copyIn(waiting, (File) args[1]);
                }
            };
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                NotificationCenter.getInstance(a).addObserver(copyListener, NotificationCenter.fileLoaded);
            }
        }
        FileLoader.getInstance(account).loadFile(msg.getDocument(), msg, FileLoader.PRIORITY_NORMAL, 0);
    }

    private static void copyIn(Track track, File from) {
        Utilities.globalQueue.postRunnable(() -> {
            final String name = from.getName();
            final int dot = name.lastIndexOf('.');
            final String ext = dot >= 0 ? name.substring(dot) : ".mp3";
            final File to = new File(offlineDir(), safe(track.key()) + ext);
            try {
                if (!to.exists() || to.length() != from.length()) {
                    AndroidUtilities.copyFile(from, to);
                }
            } catch (Throwable error) {
                FileLog.e(error);
                return;
            }
            AndroidUtilities.runOnUIThread(() -> {
                locals.put(track.key(), to.getAbsolutePath());
                save();
            });
        });
    }

    /** Убрать свою копию, если трек больше нигде не нужен. */
    private static void forgetLocalIfUnused(String key) {
        if (liked().has(key)) return;
        for (Playlist playlist : playlists) if (playlist.has(key)) return;
        final String path = locals.remove(key);
        if (path != null) {
            Utilities.globalQueue.postRunnable(() -> new File(path).delete());
        }
    }

    /** Сколько лайкнутых уже лежат у нас. */
    public static int offlineCount() {
        int n = 0;
        for (Track track : liked().tracks) if (track.localFile() != null) n++;
        return n;
    }

    public static boolean isLiked(Track track) {
        return track != null && liked().has(track);
    }

    public static void toggleLike(Track track, Runnable done) {
        if (track == null) {
            if (done != null) AndroidUtilities.runOnUIThread(done);
            return;
        }
        final Playlist list = liked();
        if (list.has(track)) {
            for (int i = list.tracks.size() - 1; i >= 0; i--) {
                if (list.tracks.get(i).key().equals(track.key())) list.tracks.remove(i);
            }
            forgetLocalIfUnused(track.key());
            save();
        } else {
            list.tracks.add(0, track);
            save();
            keepOffline(track, null);
        }
        if (done != null) AndroidUtilities.runOnUIThread(done);
    }

    // ---- общий договор плеера и вкладки музыки ----
    // Сигнатуры ниже трогать нельзя: плеер (FemboyPlayer) зовёт их.

    /** Лайкнут ли трек (в плейлисте «Понравившиеся»). */
    public static boolean isLiked(MessageObject message) {
        final String key = keyOf(message);
        return key != null && liked().has(key);
    }

    /**
     * Поставить или снять лайк. Лайк кладёт трек в «Понравившиеся» и
     * скачивает файл к себе, чтобы он играл без сети и пережил удаление чата.
     * done зовётся на главном потоке, когда лайк записан (может быть null);
     * копия файла докачивается после.
     */
    public static void toggleLike(MessageObject message, Runnable done) {
        if (!canAdd(message)) {
            if (done != null) AndroidUtilities.runOnUIThread(done);
            return;
        }
        final Track track = track(message);
        if (track == null) {
            if (done != null) AndroidUtilities.runOnUIThread(done);
            return;
        }
        final Playlist list = liked();
        if (!list.has(track)) {
            list.tracks.add(0, track);
            save();
            keepOffline(track, track.external() ? null : message);
            if (done != null) AndroidUtilities.runOnUIThread(done);
            return;
        }
        toggleLike(track, done);
    }

    /** Трек не из чата (свой файл, SoundCloud, YouTube Music): у него нет переписки. */
    public static boolean isExternal(MessageObject message) {
        return message != null && message.getDialogId() == 0 && message.getId() < 0 && externalIds.containsKey(message.getId());
    }

    /** Трек из сети, из которого собрано это сообщение, или null. */
    public static Track externalTrack(MessageObject message) {
        return isExternal(message) ? externalIds.get(message.getId()) : null;
    }

    /** Адрес обложки для трека не из чата, или null. */
    public static String coverUrl(MessageObject message) {
        final Track track = externalTrack(message);
        return track != null && !TextUtils.isEmpty(track.cover) ? track.cover : null;
    }

    /** Локальный файл трека, если он скачан (для отправки в чат), или null. */
    public static java.io.File localFile(MessageObject message) {
        if (message == null || message.messageOwner == null) return null;
        final String key = keyOf(message);
        File file = key != null ? saved(key) : null;
        if (file != null) return file;
        final Track external = externalTrack(message);
        if (external != null) return FemboyMusicOnline.cached(external);
        if (!TextUtils.isEmpty(message.messageOwner.attachPath)) {
            file = new File(message.messageOwner.attachPath);
            if (file.exists() && file.length() > 0) return file;
        }
        try {
            file = FileLoader.getInstance(message.currentAccount).getPathToMessage(message.messageOwner);
            if (file != null && file.exists() && file.length() > 0) return file;
        } catch (Throwable ignore) {
        }
        return null;
    }
}
