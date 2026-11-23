package org.telegram.femboy;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.SQLite.SQLiteCursor;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesStorage;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

/**
 * Edit history: when a message comes edited, what it said before is kept, so the message's
 * menu can show every version. The earlier text is read from the app's own database just
 * before the new one is written over it, on the same queue, so the order is certain.
 *
 * Only what this phone saw: a message that was never loaded here has no earlier version.
 */
public final class FemboyEdits {

    private FemboyEdits() {}

    private static final int MOST = 30;

    public static boolean enabled() {
        return FemboyConfig.prefs().getBoolean("edit_history", true);
    }

    public static void setEnabled(boolean on) {
        FemboyConfig.prefs().edit().putBoolean("edit_history", on).apply();
    }

    private static SharedPreferences store(int account) {
        return ApplicationLoader.applicationContext.getSharedPreferences("femboy_edits_" + account, Context.MODE_PRIVATE);
    }

    private static String key(long dialogId, int messageId) {
        return dialogId + "_" + messageId;
    }

    /** Called with the edited messages before they are saved: their earlier text is kept first. */
    public static void before(int account, long dialogId, ArrayList<MessageObject> edited) {
        if (!enabled() || edited == null || edited.isEmpty()) return;
        final ArrayList<TLRPC.Message> fresh = new ArrayList<>();
        for (MessageObject one : edited) if (one != null && one.messageOwner != null) fresh.add(one.messageOwner);
        final MessagesStorage storage = MessagesStorage.getInstance(account);
        storage.getStorageQueue().postRunnable(() -> {
            for (TLRPC.Message now : fresh) {
                SQLiteCursor cursor = null;
                try {
                    cursor = storage.getDatabase().queryFinalized("SELECT data FROM messages_v2 WHERE mid = " + now.id + " AND uid = " + dialogId);
                    if (!cursor.next()) continue;
                    NativeByteBuffer data = cursor.byteBufferValue(0);
                    if (data == null) continue;
                    TLRPC.Message was = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                    data.reuse();
                    if (was == null) continue;
                    String before = was.message == null ? "" : was.message;
                    String after = now.message == null ? "" : now.message;
                    if (before.equals(after)) continue;
                    remember(account, dialogId, now.id, before, was.edit_date != 0 ? was.edit_date : was.date);
                } catch (Throwable error) {
                    FileLog.e(error);
                } finally {
                    if (cursor != null) cursor.dispose();
                }
            }
        });
    }

    private static synchronized void remember(int account, long dialogId, int messageId, String text, int date) {
        try {
            SharedPreferences prefs = store(account);
            JSONArray list = new JSONArray(prefs.getString(key(dialogId, messageId), "[]"));
            if (list.length() > 0 && text.equals(list.getJSONObject(list.length() - 1).optString("text"))) return;
            list.put(new JSONObject().put("text", text).put("date", date));
            while (list.length() > MOST) list.remove(0);
            prefs.edit().putString(key(dialogId, messageId), list.toString()).apply();
        } catch (Throwable error) {
            FileLog.e(error);
        }
    }

    /** One version: what it said and since when (unix seconds). */
    public static final class Version {
        public final String text;
        public final int date;

        Version(String text, int date) {
            this.text = text;
            this.date = date;
        }
    }

    /** The earlier versions of a message, oldest first. Empty when it was never seen edited. */
    public static ArrayList<Version> history(MessageObject message) {
        ArrayList<Version> out = new ArrayList<>();
        if (message == null || message.messageOwner == null) return out;
        try {
            JSONArray list = new JSONArray(store(message.currentAccount).getString(key(message.getDialogId(), message.getId()), "[]"));
            for (int i = 0; i < list.length(); i++) {
                JSONObject one = list.getJSONObject(i);
                out.add(new Version(one.optString("text"), one.optInt("date")));
            }
        } catch (Throwable error) {
            FileLog.e(error);
        }
        return out;
    }

    public static boolean has(MessageObject message) {
        return message != null && message.messageOwner != null && message.messageOwner.edit_date != 0
                && store(message.currentAccount).contains(key(message.getDialogId(), message.getId()));
    }
}
