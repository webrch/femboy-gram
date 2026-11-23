package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import org.telegram.femboy.FemboyConfig;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MotionBackgroundDrawable;
import org.telegram.ui.Stories.recorder.StoryEntry;

/**
 * Кусок настоящего чата для экранов настроек: те же ячейки сообщений, что в
 * переписке, на тех же обоях, и, если надо, поле ввода из тех же значков.
 * Не рисунок «как будто чат», а сам чат, только без сети.
 */
@SuppressLint("ViewConstructor")
public class FemboyChatPreview extends LinearLayout {

    /** Одно сообщение: текст (может быть с метками оформления) и чьё оно. */
    public static final class Message {
        final CharSequence text;
        final boolean out;

        final TLRPC.Document sticker;

        public Message(CharSequence text, boolean out) {
            this.text = text;
            this.out = out;
            this.sticker = null;
        }

        /** Сообщение-стикер: настоящий стикер из недавних. */
        public Message(TLRPC.Document sticker, boolean out) {
            this.text = "";
            this.out = out;
            this.sticker = sticker;
        }
    }

    private final LinearLayout messages;
    private Message[] current;
    private final boolean withInput;
    private LinearLayout inputBar;
    private EditText inputText;
    private ImageView sendButton;

    public FemboyChatPreview(Context context, boolean withInput, Message... list) {
        super(context);
        this.withInput = withInput;
        setWillNotDraw(false);
        setOrientation(VERTICAL);
        setPadding(0, dp(10), 0, dp(10));
        messages = new LinearLayout(context);
        messages.setOrientation(VERTICAL);
        addView(messages, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        if (withInput) {
            buildInput(context);
        }
        setMessages(list);
    }

    /** Подменить сообщения: например, когда переключили вид оформления. */
    public void setMessages(Message... list) {
        current = list;
        messages.removeAllViews();
        final int account = UserConfig.selectedAccount;
        final int date = (int) (System.currentTimeMillis() / 1000) - 60 * 5;
        int id = 1;
        for (Message m : list) {
            final TLRPC.TL_message message = new TLRPC.TL_message();
            message.message = m.text.toString();
            message.date = date + id * 60;
            message.dialog_id = 1;
            message.flags = 257 + 8;
            message.from_id = new TLRPC.TL_peerUser();
            message.from_id.user_id = m.out ? UserConfig.getInstance(account).getClientUserId() : 0;
            message.id = id++;
            if (m.sticker != null) {
                final TLRPC.TL_messageMediaDocument media = new TLRPC.TL_messageMediaDocument();
                media.flags |= 1;
                media.document = m.sticker;
                message.media = media;
            } else {
                message.media = new TLRPC.TL_messageMediaEmpty();
            }
            message.out = m.out;
            message.peer_id = new TLRPC.TL_peerUser();
            message.peer_id.user_id = 0;
            final MessageObject object = new MessageObject(account, message, true, false);
            object.resetLayout();
            object.eventId = 1;
            final ChatMessageCell cell = new ChatMessageCell(context(), account, false, null, null) {
                @Override
                public boolean onTouchEvent(MotionEvent event) {
                    return false;   // это витрина: нажатия в ней ничего не открывают
                }
            };
            cell.setFullyDraw(true);
            cell.setMessageObject(object, null, false, false, false);
            messages.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
        if (withInput) {
            placeInput();
        }
    }

    private Context context() {
        return getContext();
    }

    /** Перерисовать сообщения без пересборки: для ползунков, что двигаются непрерывно. */
    public void invalidateAll() {
        for (int i = 0; i < messages.getChildCount(); i++) {
            messages.getChildAt(i).invalidate();
        }
    }

    /** Перечитать настройки поля ввода и перестроить сообщения (цвета темы могли смениться). */
    public void refresh() {
        if (current != null) {
            setMessages(current);
        }
        invalidate();
    }

    // ---------------------------------------------------------------- поле ввода

    private void buildInput(Context context) {
        inputBar = new LinearLayout(context);
        inputBar.setOrientation(HORIZONTAL);
        inputBar.setGravity(Gravity.BOTTOM);
        inputBar.setPadding(dp(4), dp(4), dp(4), dp(4));

        final LinearLayout field = new LinearLayout(context);
        field.setOrientation(HORIZONTAL);
        field.setGravity(Gravity.BOTTOM);
        final GradientDrawable fieldBg = new GradientDrawable();
        fieldBg.setCornerRadius(dp(24));
        fieldBg.setColor(Theme.getColor(Theme.key_chat_messagePanelBackground));
        field.setBackground(fieldBg);

        final ImageView smile = icon(context, R.drawable.input_smile);
        field.addView(smile, LayoutHelper.createLinear(48, 48));
        inputText = new EditText(context);
        inputText.setBackground(null);
        inputText.setFocusable(false);
        inputText.setClickable(false);
        inputText.setPadding(0, dp(12), 0, dp(12));
        inputText.setTextColor(Theme.getColor(Theme.key_chat_messagePanelText));
        inputText.setText(org.telegram.messenger.LocaleController.getString(R.string.FemboyPreviewInputText));
        field.addView(inputText, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        final ImageView attach = icon(context, R.drawable.msg_input_attach2);
        field.addView(attach, LayoutHelper.createLinear(48, 48));
        inputBar.addView(field, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        sendButton = new ImageView(context);
        sendButton.setScaleType(ImageView.ScaleType.CENTER);
        sendButton.setImageResource(R.drawable.ic_send);
        sendButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelVoicePressed), PorterDuff.Mode.SRC_IN));
        final GradientDrawable sendBg = new GradientDrawable();
        sendBg.setShape(GradientDrawable.OVAL);
        sendBg.setColor(Theme.getColor(Theme.key_chat_messagePanelSend));
        sendButton.setBackground(sendBg);
        inputBar.addView(sendButton, LayoutHelper.createLinear(48, 48, 6, 0, 0, 0));
    }

    private ImageView icon(Context context, int res) {
        final ImageView view = new ImageView(context);
        view.setScaleType(ImageView.ScaleType.CENTER);
        view.setImageResource(res);
        view.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelIcons), PorterDuff.Mode.SRC_IN));
        return view;
    }

    /** Поле сверху или снизу, столько строк и такой текст, как в настройках. */
    private void placeInput() {
        if (inputBar.getParent() != null) {
            removeView(inputBar);
        }
        final int raw = FemboyConfig.inputMaxLinesRaw();
        inputText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, FemboyConfig.inputTextSize());
        inputText.setMaxLines(raw == 0 ? 8 : raw);
        inputText.setMinLines(Math.min(raw == 0 ? 4 : raw, 3));
        final LinearLayout.LayoutParams lp = LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 8, 6, 8, 6);
        if (FemboyConfig.inputOnTop()) {
            addView(inputBar, 0, lp);
        } else {
            addView(inputBar, lp);
        }
    }

    // ---------------------------------------------------------------- фон

    @Override
    protected void onDraw(Canvas canvas) {
        final Drawable drawable = Theme.getCachedWallpaperNonBlocking();
        if (drawable == null) {
            canvas.drawColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            return;
        }
        if (drawable instanceof ColorDrawable || drawable instanceof GradientDrawable || drawable instanceof MotionBackgroundDrawable) {
            drawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
            drawable.draw(canvas);
        } else if (drawable instanceof BitmapDrawable && drawable.getIntrinsicWidth() > 0) {
            final float scale = Math.max((float) getMeasuredWidth() / drawable.getIntrinsicWidth(),
                    (float) getMeasuredHeight() / drawable.getIntrinsicHeight());
            final int w = (int) Math.ceil(drawable.getIntrinsicWidth() * scale);
            final int h = (int) Math.ceil(drawable.getIntrinsicHeight() * scale);
            final int x = (getMeasuredWidth() - w) / 2, y = (getMeasuredHeight() - h) / 2;
            canvas.save();
            canvas.clipRect(0, 0, getMeasuredWidth(), getMeasuredHeight());
            drawable.setBounds(x, y, x + w, y + h);
            drawable.draw(canvas);
            canvas.restore();
        } else {
            StoryEntry.drawBackgroundDrawable(canvas, drawable, getWidth(), getHeight());
        }
    }
}
