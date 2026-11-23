package com.exteragram.messenger.utils.chats;

import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;

import java.io.File;

/** exteraGram's message helpers, the ones plugins call. */
public class ChatUtils {
    private static final ChatUtils[] INSTANCES = new ChatUtils[UserConfig.MAX_ACCOUNT_COUNT];
    private final int account;

    private ChatUtils(int account) {
        this.account = account;
    }

    public static ChatUtils getInstance() {
        return getInstance(UserConfig.selectedAccount);
    }

    public static synchronized ChatUtils getInstance(int account) {
        if (INSTANCES[account] == null) INSTANCES[account] = new ChatUtils(account);
        return INSTANCES[account];
    }

    public CharSequence getMessageText(MessageObject message, MessageObject.GroupedMessages group) {
        if (message == null || message.translated || message.isRestrictedMessage) return null;
        if (group != null) {
            for (MessageObject one : group.messages) {
                if (one.caption != null && one.caption.length() > 0) return one.caption;
            }
        }
        if (message.isPoll()) {
            try {
                TLRPC.Poll poll = ((TLRPC.TL_messageMediaPoll) message.messageOwner.media).poll;
                StringBuilder text = new StringBuilder(poll.question.text).append('\n');
                for (TLRPC.PollAnswer answer : poll.answers) text.append("\n🔘 ").append(answer.text == null ? "" : answer.text.text);
                return text.toString();
            } catch (Exception ignored) {
            }
        }
        if (message.caption != null && message.caption.length() > 0) return message.caption;
        return message.messageOwner != null && !TextUtils.isEmpty(message.messageOwner.message) ? message.messageOwner.message : message.messageText;
    }

    private static boolean exists(String path) {
        return !TextUtils.isEmpty(path) && new File(path).exists();
    }

    public String getPathToMessage(MessageObject message) {
        if (message == null) return null;
        FileLoader loader = FileLoader.getInstance(message.currentAccount);
        if (message.messageOwner != null && exists(message.messageOwner.attachPath)) return message.messageOwner.attachPath;
        if (message.messageOwner != null) {
            String path = loader.getPathToMessage(message.messageOwner).toString();
            if (exists(path)) return path;
        }
        if (message.getDocument() != null) {
            String path = loader.getPathToAttach(message.getDocument(), false).toString();
            if (exists(path)) return path;
            path = loader.getPathToAttach(message.getDocument(), true).toString();
            if (exists(path)) return path;
        }
        return null;
    }

    public void addMessageToClipboard(MessageObject message, Runnable done) {
        String path = getPathToMessage(message);
        if (TextUtils.isEmpty(path)) return;
        try {
            android.content.Context context = org.telegram.messenger.ApplicationLoader.applicationContext;
            android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(context,
                    org.telegram.messenger.ApplicationLoader.getApplicationId() + ".provider", new File(path));
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) context.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(android.content.ClipData.newUri(context.getContentResolver(), "file", uri));
            if (done != null) AndroidUtilities.runOnUIThread(done);
        } catch (Throwable ignored) {
        }
    }
}
