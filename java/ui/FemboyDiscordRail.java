package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.os.Bundle;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.femboy.FemboyYou;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

/**
 * Режим Discord: слева колонка чатов иконками, как серверы в Discord, справа
 * сам чат целиком, с полем ввода. Колонка оборачивает экран чата, сам чат об
 * этом не знает.
 */
@SuppressLint("ViewConstructor")
public class FemboyDiscordRail extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    public static final int WIDTH_DP = 76;

    private final ChatActivity chat;
    private final long currentDialogId;
    private final int account;
    private final RecyclerView list;
    private final ArrayList<Long> dialogs = new ArrayList<>();

    /** Обернуть экран чата колонкой. content — то, что чат сам создал. */
    public static View wrap(ChatActivity chat, Context context, View content, long dialogId) {
        final FemboyDiscordRail rail = new FemboyDiscordRail(context, chat, dialogId);
        rail.addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, WIDTH_DP, 0, 0, 0));
        return rail;
    }

    private FemboyDiscordRail(Context context, ChatActivity chat, long dialogId) {
        super(context);
        this.chat = chat;
        this.currentDialogId = dialogId;
        this.account = UserConfig.selectedAccount;
        FemboyYou.ensureRoles();
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        list = new RecyclerView(context);
        list.setLayoutManager(new LinearLayoutManager(context));
        list.setClipToPadding(false);
        list.setVerticalScrollBarEnabled(false);
        list.setPadding(0, AndroidUtilities.statusBarHeight + dp(8), 0, dp(16));
        list.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                final Item item = new Item(parent.getContext());
                item.setLayoutParams(new RecyclerView.LayoutParams(dp(WIDTH_DP), dp(viewType == 1 ? 72 : 60)));
                return new RecyclerView.ViewHolder(item) {};
            }

            @Override
            public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                ((Item) holder.itemView).bind(position == 0 ? 0 : dialogs.get(position - 1), position == 0);
            }

            @Override
            public int getItemViewType(int position) {
                return position == 0 ? 1 : 0;
            }

            @Override
            public int getItemCount() {
                return dialogs.size() + 1;
            }
        });
        addView(list, LayoutHelper.createFrame(WIDTH_DP, LayoutHelper.MATCH_PARENT, Gravity.LEFT));
        reload();
    }

    private void reload() {
        dialogs.clear();
        final ArrayList<TLRPC.Dialog> all = MessagesController.getInstance(account).getDialogs(0);
        for (int i = 0; i < all.size() && dialogs.size() < 100; i++) {
            final long id = all.get(i).id;
            if (id != 0 && !DialogObject.isFolderDialogId(id)) {
                dialogs.add(id);
            }
        }
        if (list.getAdapter() != null) {
            list.getAdapter().notifyDataSetChanged();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.dialogsNeedReload);
        NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.updateInterfaces);
        reload();
        // текущий чат в колонке на виду
        final int at = dialogs.indexOf(currentDialogId);
        if (at > 3) {
            list.scrollToPosition(at + 1);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getInstance(account).removeObserver(this, NotificationCenter.dialogsNeedReload);
        NotificationCenter.getInstance(account).removeObserver(this, NotificationCenter.updateInterfaces);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        reload();
    }

    private void open(long dialogId) {
        if (dialogId == currentDialogId) {
            return;
        }
        final Bundle args = new Bundle();
        if (DialogObject.isEncryptedDialog(dialogId)) {
            args.putInt("enc_id", DialogObject.getEncryptedChatId(dialogId));
        } else if (DialogObject.isUserDialog(dialogId)) {
            args.putLong("user_id", dialogId);
        } else {
            args.putLong("chat_id", -dialogId);
        }
        // переключение как в Discord: сразу, без анимации, прошлый чат уходит из стопки
        chat.presentFragment(new ChatActivity(args), true, true);
    }

    /** Одна иконка колонки: аватарка, полоска слева, счётчик непрочитанного. */
    private class Item extends FrameLayout {
        private final BackupImageView avatar;
        private final ImageView home;
        private final AvatarDrawable avatarDrawable = new AvatarDrawable();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private long dialogId;
        private boolean isHome;
        private int unread;
        private boolean mention;
        private boolean muted;

        Item(Context context) {
            super(context);
            setWillNotDraw(false);
            avatar = new BackupImageView(context);
            addView(avatar, LayoutHelper.createFrame(48, 48, Gravity.CENTER));
            home = new ImageView(context);
            home.setScaleType(ImageView.ScaleType.CENTER);
            home.setImageResource(R.drawable.femboy_badge_plane);
            addView(home, LayoutHelper.createFrame(48, 48, Gravity.CENTER));
            text.setTextSize(dp(11));
            text.setTypeface(AndroidUtilities.bold());
            setOnClickListener(v -> {
                if (isHome) {
                    chat.finishFragment();
                } else {
                    open(dialogId);
                }
            });
        }

        void bind(long dialogId, boolean isHome) {
            this.dialogId = dialogId;
            this.isHome = isHome;
            final boolean selected = !isHome && dialogId == currentDialogId;
            home.setVisibility(isHome ? VISIBLE : GONE);
            avatar.setVisibility(isHome ? GONE : VISIBLE);
            if (isHome) {
                final android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
                bg.setCornerRadius(dp(16));
                bg.setColor(FemboyYou.on() ? FemboyYou.primaryContainer : Theme.getColor(Theme.key_featuredStickers_addButton));
                home.setBackground(bg);
                home.setColorFilter(new PorterDuffColorFilter(FemboyYou.on() ? FemboyYou.onPrimaryContainer : 0xFFFFFFFF, PorterDuff.Mode.SRC_IN));
                unread = 0;
                invalidate();
                return;
            }
            final MessagesController controller = MessagesController.getInstance(account);
            final TLObject object = DialogObject.isUserDialog(dialogId) ? controller.getUser(dialogId)
                    : DialogObject.isEncryptedDialog(dialogId) ? controller.getUser(controller.getEncryptedChat(DialogObject.getEncryptedChatId(dialogId)) != null
                            ? controller.getEncryptedChat(DialogObject.getEncryptedChatId(dialogId)).user_id : 0)
                    : controller.getChat(-dialogId);
            if (object instanceof TLRPC.User) {
                avatarDrawable.setInfo(account, (TLRPC.User) object);
            } else if (object instanceof TLRPC.Chat) {
                avatarDrawable.setInfo(account, (TLRPC.Chat) object);
            }
            avatar.setForUserOrChat(object, avatarDrawable);
            // выбранный чат скругляется квадратом, как сервер в Discord
            avatar.setRoundRadius(selected ? dp(16) : dp(24));
            final TLRPC.Dialog dialog = controller.dialogs_dict.get(dialogId);
            unread = dialog != null ? dialog.unread_count : 0;
            mention = dialog != null && dialog.unread_mentions_count > 0;
            muted = controller.isDialogMuted(dialogId, 0);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            final boolean selected = !isHome && dialogId == currentDialogId;
            // полоска слева: длинная у текущего, короткая у непрочитанного
            final float h = selected ? dp(40) : unread > 0 && !muted ? dp(8) : 0;
            if (h > 0) {
                paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                rect.set(-dp(4), getHeight() / 2f - h / 2f, dp(4), getHeight() / 2f + h / 2f);
                canvas.drawRoundRect(rect, dp(4), dp(4), paint);
            }
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            if (unread <= 0 || isHome) {
                return;
            }
            // счётчик в правом нижнем углу аватарки
            final String count = unread > 99 ? "99+" : String.valueOf(unread);
            final float w = Math.max(dp(18), text.measureText(count) + dp(10));
            final float cx = getWidth() / 2f + dp(18), cy = getHeight() / 2f + dp(18);
            paint.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
            rect.set(cx - w / 2f - dp(3), cy - dp(12), cx + w / 2f + dp(3), cy + dp(12));
            canvas.drawRoundRect(rect, dp(12), dp(12), paint);
            paint.setColor(mention ? Theme.getColor(Theme.key_chats_unreadCounter)
                    : Theme.getColor(muted ? Theme.key_chats_unreadCounterMuted : Theme.key_chats_unreadCounter));
            rect.set(cx - w / 2f, cy - dp(9), cx + w / 2f, cy + dp(9));
            canvas.drawRoundRect(rect, dp(9), dp(9), paint);
            text.setColor(Theme.getColor(Theme.key_chats_unreadCounterText));
            canvas.drawText(count, cx - text.measureText(count) / 2f, cy + dp(4), text);
        }
    }
}
