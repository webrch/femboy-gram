package org.telegram.femboy;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.collection.LongSparseArray;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Удалённые сообщения остаются, как в FemboyT: когда собеседник удаляет
 * сообщение, телеграм присылает это с сервера, а мы не стираем его ни с
 * экрана, ни из базы, только запоминаем айди. В чате такое сообщение
 * рисуется полупрозрачным.
 *
 * Свои удаления сюда не попадают: они идут своим путём, не через обновления
 * с сервера, и работают как обычно.
 *
 * Айди в личках и обычных группах общие на аккаунт (ключ «u»), в каналах и
 * супергруппах свои у каждого канала (ключ «c» + номер канала).
 */
public class FemboyDeleted {

    private static final int MOST_PER_KEY = 5000;
    private static final HashMap<String, HashSet<Integer>> cache = new HashMap<>();

    public static boolean enabled() {
        return FemboyConfig.prefs().getBoolean("save_deleted", true);
    }

    public static void setEnabled(boolean on) {
        FemboyConfig.prefs().edit().putBoolean("save_deleted", on).apply();
    }

    private static SharedPreferences store(int account) {
        return ApplicationLoader.applicationContext.getSharedPreferences("femboy_deleted_" + account, Context.MODE_PRIVATE);
    }

    private static String key(long dialogKey) {
        return dialogKey == 0 ? "u" : "c" + (-dialogKey);
    }

    private static HashSet<Integer> set(int account, String key) {
        final String full = account + ":" + key;
        synchronized (cache) {
            HashSet<Integer> set = cache.get(full);
            if (set == null) {
                set = new HashSet<>();
                final String saved = store(account).getString(key, "");
                for (String part : saved.split(",")) {
                    if (!part.isEmpty()) {
                        try {
                            set.add(Integer.parseInt(part));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
                cache.put(full, set);
            }
            return set;
        }
    }

    /** Запомнить удалённое сервером. Ключ 0 — лички и группы, иначе -номер канала. */
    public static void remember(int account, LongSparseArray<ArrayList<Integer>> deleted) {
        final SharedPreferences.Editor editor = store(account).edit();
        for (int i = 0; i < deleted.size(); i++) {
            final String key = key(deleted.keyAt(i));
            final HashSet<Integer> set = set(account, key);
            synchronized (cache) {
                set.addAll(deleted.valueAt(i));
                final StringBuilder sb = new StringBuilder();
                int n = 0;
                for (Integer id : set) {
                    if (n++ >= MOST_PER_KEY) break;
                    if (sb.length() > 0) sb.append(',');
                    sb.append(id);
                }
                editor.putString(key, sb.toString());
            }
        }
        editor.apply();
        AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(account)
                .postNotificationName(NotificationCenter.femboyDeletedMessages));
    }

    public static boolean isDeleted(MessageObject message) {
        if (message == null || message.messageOwner == null || message.getId() <= 0) {
            return false;
        }
        final long channel = message.messageOwner.peer_id != null ? message.messageOwner.peer_id.channel_id : 0;
        final HashSet<Integer> set = set(message.currentAccount, channel != 0 ? "c" + channel : "u");
        synchronized (cache) {
            return set.contains(message.getId());
        }
    }

    private static final int QUOTE_MOST = 160;

    /**
     * Ответ на удалённое: телеграм такой ответ не примет (сообщения на сервере
     * уже нет), поэтому вместо ответа ставим сверху цитату. В ней ник того, кому
     * отвечаем, ссылкой-упоминанием, и его текст, длинный обрезаем.
     */
    public static void fakeReply(int account, SendMessagesHelper.SendMessageParams params) {
        final MessageObject reply = params.replyToMsg;
        if (reply == null || params.message == null || !enabled() || !isDeleted(reply)) {
            return;
        }
        final MessagesController controller = MessagesController.getInstance(account);
        final long from = reply.getFromChatId();
        String name = null;
        TLRPC.User user = null;
        TLRPC.Chat chat = null;
        if (from > 0) {
            user = controller.getUser(from);
            name = user != null ? UserObject.getUserName(user) : null;
        } else if (from < 0) {
            chat = controller.getChat(-from);
            name = chat != null ? chat.title : null;
        }
        if (name == null || name.isEmpty()) {
            name = "?";
        }
        String text = reply.messageOwner != null && reply.messageOwner.message != null && !reply.messageOwner.message.isEmpty()
                ? reply.messageOwner.message : reply.messageText != null ? reply.messageText.toString() : "";
        text = text.trim();
        if (text.length() > QUOTE_MOST) {
            int cut = QUOTE_MOST;
            if (Character.isHighSurrogate(text.charAt(cut - 1))) cut--;
            text = text.substring(0, cut).trim() + "…";
        }
        final String quote = text.isEmpty() ? name : name + "\n" + text;
        final int shift = quote.length() + 1;

        final java.util.ArrayList<TLRPC.MessageEntity> entities = new java.util.ArrayList<>();
        final TLRPC.TL_messageEntityBlockquote block = new TLRPC.TL_messageEntityBlockquote();
        block.offset = 0;
        block.length = quote.length();
        entities.add(block);
        final TLRPC.TL_messageEntityBold bold = new TLRPC.TL_messageEntityBold();
        bold.offset = 0;
        bold.length = name.length();
        entities.add(bold);
        if (user != null) {
            final TLRPC.TL_inputMessageEntityMentionName mention = new TLRPC.TL_inputMessageEntityMentionName();
            mention.user_id = controller.getInputUser(user);
            mention.offset = 0;
            mention.length = name.length();
            entities.add(mention);
        } else if (chat != null && !android.text.TextUtils.isEmpty(chat.username)) {
            final TLRPC.TL_messageEntityTextUrl link = new TLRPC.TL_messageEntityTextUrl();
            link.url = "https://t.me/" + chat.username;
            link.offset = 0;
            link.length = name.length();
            entities.add(link);
        }
        if (params.entities != null) {
            for (TLRPC.MessageEntity e : params.entities) {
                e.offset += shift;
                entities.add(e);
            }
        }
        params.message = quote + "\n" + params.message;
        params.entities = entities;
        // in a forum topic the message still goes to that topic: it replies to the topic's root,
        // as a message written there without a reply does; with no reply at all it went to General
        params.replyToMsg = params.replyToTopMsg;
        params.replyQuote = null;
    }
}
