package org.telegram.femboy;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * Музыка из чатов человека: сначала «Избранное» (messages.search по себе),
 * потом все чаты (messages.searchGlobal с фильтром музыки), страницами.
 * С пустым запросом это раздел «Из твоих чатов», с запросом — поиск по
 * Telegram на вкладке музыки.
 */
public final class FemboyMusicChats {

    private static final int PAGE = 50;

    public final int account;
    public final String query;
    public final ArrayList<MessageObject> items = new ArrayList<>();
    private final HashSet<String> keys = new HashSet<>();
    public boolean loading;
    /** Больше нечего грузить. */
    public boolean finished;
    /** Последний запрос упал. */
    public boolean failed;
    private boolean savedDone;
    private int nextRate;
    private int requestId = -1;
    private Runnable onChange;

    private static FemboyMusicChats library;

    /** Общий список «Из твоих чатов», чтобы не грузить его при каждом заходе. */
    public static FemboyMusicChats library(int account) {
        if (library == null || library.account != account) {
            if (library != null) library.cancel();
            library = new FemboyMusicChats(account, "");
        }
        return library;
    }

    public FemboyMusicChats(int account, String query) {
        this.account = account;
        this.query = query == null ? "" : query;
    }

    public void setOnChange(Runnable onChange) {
        this.onChange = onChange;
    }

    private void changed() {
        if (onChange != null) onChange.run();
    }

    public void cancel() {
        if (requestId >= 0) {
            ConnectionsManager.getInstance(account).cancelRequest(requestId, true);
            requestId = -1;
        }
        loading = false;
    }

    /** Догрузить следующую страницу, если она есть и ещё не грузится. */
    public void loadMore() {
        if (loading || finished) return;
        loading = true;
        failed = false;
        if (!savedDone) {
            final TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
            req.peer = new TLRPC.TL_inputPeerSelf();
            req.q = query;
            req.filter = new TLRPC.TL_inputMessagesFilterMusic();
            req.limit = 100;
            requestId = ConnectionsManager.getInstance(account).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                requestId = -1;
                loading = false;
                savedDone = true;
                if (res instanceof TLRPC.messages_Messages) {
                    take((TLRPC.messages_Messages) res);
                }
                changed();
                // «Избранное» мало: сразу продолжаем по всем чатам
                if (items.size() < 20) loadMore();
            }));
            return;
        }
        final TLRPC.TL_messages_searchGlobal req = new TLRPC.TL_messages_searchGlobal();
        req.filter = new TLRPC.TL_inputMessagesFilterMusic();
        req.q = query;
        req.limit = PAGE;
        final MessageObject last = lastGlobal;
        if (last != null) {
            req.offset_id = last.getId();
            req.offset_rate = nextRate;
            req.offset_peer = MessagesController.getInstance(account).getInputPeer(MessageObject.getPeerId(last.messageOwner.peer_id));
        } else {
            req.offset_rate = 0;
            req.offset_id = 0;
            req.offset_peer = new TLRPC.TL_inputPeerEmpty();
        }
        requestId = ConnectionsManager.getInstance(account).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            requestId = -1;
            loading = false;
            if (res instanceof TLRPC.messages_Messages) {
                final TLRPC.messages_Messages r = (TLRPC.messages_Messages) res;
                final MessageObject tail = take(r);
                if (tail != null) lastGlobal = tail;
                nextRate = r.next_rate;
                // не «срез» значит, что пришло всё сразу
                finished = r.messages.isEmpty() || !(r instanceof TLRPC.TL_messages_messagesSlice);
            } else {
                failed = true;
            }
            changed();
        }));
    }

    private MessageObject lastGlobal;

    /** Кладёт новые песни в список, возвращает последнее сообщение страницы. */
    private MessageObject take(TLRPC.messages_Messages r) {
        MessagesController.getInstance(account).putUsers(r.users, false);
        MessagesController.getInstance(account).putChats(r.chats, false);
        MessageObject tail = null;
        for (TLRPC.Message message : r.messages) {
            final MessageObject object;
            try {
                object = new MessageObject(account, message, false, true);
            } catch (Throwable e) {
                continue;
            }
            tail = object;
            if (!object.isMusic()) continue;
            if (keys.add(FemboyMusic.keyOf(object))) items.add(object);
        }
        return tail;
    }
}
