package org.telegram.ui;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;

import org.telegram.femboy.FemboyMusic;
import org.telegram.femboy.FemboyMusicOnline;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ItemOptions;

/** Общие действия над треком: меню, лайк, «показать в чате», похожее. */
final class FemboyMusicActions {

    private FemboyMusicActions() {}

    /**
     * Меню трека. Есть track или message (или оба). from: плейлист, из
     * которого трек можно убрать, или null.
     */
    static void menu(BaseFragment fragment, View anchor, FemboyMusic.Track track, MessageObject message,
                     BulletinFactory bulletins, FemboyMusic.Playlist from, Runnable changed) {
        if (fragment == null || anchor == null) return;
        if (message == null && track != null) message = FemboyMusicTiles.message(track);
        final MessageObject msg = message;
        final FemboyMusic.Track one = track != null ? track : FemboyMusic.track(msg);
        if (one == null) return;
        final boolean liked = FemboyMusic.isLiked(one);
        final ItemOptions options = ItemOptions.makeOptions(fragment, anchor);
        options.add(liked ? R.drawable.femboy_ic_favorite_fill : R.drawable.femboy_ic_favorite,
                LocaleController.getString(liked ? R.string.FemboyUnlike : R.string.FemboyLike),
                () -> like(one, msg, bulletins, changed));
        options.addIf(msg != null && FemboyMusic.canAdd(msg), R.drawable.femboy_ic_playlist_add,
                LocaleController.getString(R.string.FemboyAddToPlaylist),
                () -> FemboyPlaylistPicker.show(fragment.getParentActivity(), msg, bulletins));
        options.addIf(one.external() && FemboyMusicOnline.available(), R.drawable.femboy_ic_radio,
                LocaleController.getString(R.string.FemboySimilar), () -> similar(one, bulletins));
        options.addIf(!one.external() && one.account == UserConfig.selectedAccount, R.drawable.femboy_ic_chat,
                LocaleController.getString(R.string.FemboyOpenInChat), () -> openInChat(fragment, one.dialogId, one.messageId));
        if (from != null) {
            options.add(R.drawable.femboy_ic_playlist_remove, LocaleController.getString(
                    from.liked() ? R.string.FemboyUnlike : R.string.FemboyPlaylistRemove), true, () -> {
                if (from.liked()) {
                    FemboyMusic.toggleLike(one, changed);
                } else {
                    FemboyMusic.remove(from, one);
                    if (changed != null) changed.run();
                }
            });
        }
        options.setGravity(Gravity.RIGHT);
        options.show();
    }

    static void like(FemboyMusic.Track track, MessageObject message, BulletinFactory bulletins, Runnable changed) {
        final Runnable done = () -> {
            final boolean now = track != null ? FemboyMusic.isLiked(track) : FemboyMusic.isLiked(message);
            if (bulletins != null) {
                bulletins.createSimpleBulletin(now ? R.raw.contact_check : R.raw.chats_infotip,
                        LocaleController.getString(now ? R.string.FemboyLikedAdded : R.string.FemboyLikedRemoved)).show();
            }
            if (changed != null) changed.run();
        };
        if (message != null && (track == null || !track.external())) {
            FemboyMusic.toggleLike(message, done);
        } else {
            FemboyMusic.toggleLike(track, done);
        }
    }

    static void openInChat(BaseFragment fragment, long dialogId, int messageId) {
        final Bundle args = new Bundle();
        if (dialogId > 0) args.putLong("user_id", dialogId);
        else args.putLong("chat_id", -dialogId);
        args.putInt("message_id", messageId);
        fragment.presentFragment(new ChatActivity(args));
    }

    /** Похожее на трек из сети: радио YouTube Music или рекомендации SoundCloud. */
    static void similar(FemboyMusic.Track track, BulletinFactory bulletins) {
        FemboyMusicOnline.related(track, 25, result -> {
            if (!result.ok || result.items.isEmpty()) {
                if (bulletins != null) {
                    bulletins.createSimpleBulletin(R.raw.chats_infotip, result.ok
                            ? LocaleController.getString(R.string.FemboySearchNothing) : result.errorText()).show();
                }
                return;
            }
            final java.util.ArrayList<FemboyMusic.Track> list = new java.util.ArrayList<>();
            list.add(track);
            list.addAll(result.items);
            FemboyMusic.play(list, 0, null, LocaleController.getString(R.string.FemboySimilar));
        });
    }
}
