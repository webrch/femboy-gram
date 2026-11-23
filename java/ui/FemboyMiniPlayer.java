package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;

/**
 * Плашка «сейчас играет» во вкладке музыки: обложка, название, пауза и
 * следующий трек, тонкая полоса прогресса снизу. Нажатие открывает плеер.
 * Без неё во вкладке не было видно, что играет, и плеер открывался только из чатов.
 */
public class FemboyMiniPlayer extends FrameLayout {

    private final BackupImageView cover;
    private final TextView title, artist;
    private final ImageView play, next;
    private final Paint back = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private MessageObject shown;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            invalidate();
            if (isShown() && !MediaController.getInstance().isMessagePaused()) {
                AndroidUtilities.runOnUIThread(this, 500);
            }
        }
    };

    public FemboyMiniPlayer(Context context) {
        super(context);
        setWillNotDraw(false);
        cover = new BackupImageView(context);
        cover.setRoundRadius(dp(12));
        addView(cover, LayoutHelper.createFrame(44, 44, Gravity.LEFT | Gravity.CENTER_VERTICAL, 10, 0, 0, 0));

        final LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        title = new TextView(context);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        title.setTypeface(AndroidUtilities.bold());
        title.setSingleLine();
        title.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(title);
        artist = new TextView(context);
        artist.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        artist.setSingleLine();
        artist.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(artist, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 1, 0, 0));
        addView(texts, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 66, 0, 104, 0));

        play = button(context, R.drawable.femboy_ic_play_arrow_fill);
        play.setOnClickListener(v -> {
            final MessageObject now = MediaController.getInstance().getPlayingMessageObject();
            if (now == null) return;
            if (MediaController.getInstance().isMessagePaused()) MediaController.getInstance().playMessage(now);
            else MediaController.getInstance().pauseMessage(now);
        });
        addView(play, LayoutHelper.createFrame(44, 44, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 52, 0));
        next = button(context, R.drawable.femboy_ic_skip_next_fill);
        next.setOnClickListener(v -> MediaController.getInstance().playNextMessage());
        addView(next, LayoutHelper.createFrame(44, 44, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 8, 0));

        setOnClickListener(v -> FemboyPlayer.open(getContext()));
        update();
    }

    private ImageView button(Context context, int icon) {
        final ImageView view = new ImageView(context);
        view.setImageResource(icon);
        view.setScaleType(ImageView.ScaleType.CENTER);
        view.setBackground(Theme.createSelectorDrawable(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), 0.12f), Theme.RIPPLE_MASK_CIRCLE_20DP));
        return view;
    }

    /** Что играет сейчас; плашка прячется, если играет не музыка. */
    public void update() {
        final MessageObject now = MediaController.getInstance().getPlayingMessageObject();
        final boolean visible = now != null && now.isMusic();
        setVisibility(visible ? VISIBLE : GONE);
        if (!visible) {
            shown = null;
            return;
        }
        final int text = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
        final int accent = Theme.getColor(Theme.key_featuredStickers_addButton);
        title.setTextColor(text);
        artist.setTextColor(Theme.multAlpha(text, 0.6f));
        play.setColorFilter(new PorterDuffColorFilter(text, PorterDuff.Mode.SRC_IN));
        next.setColorFilter(new PorterDuffColorFilter(text, PorterDuff.Mode.SRC_IN));
        back.setColor(androidx.core.graphics.ColorUtils.blendARGB(Theme.getColor(Theme.key_windowBackgroundWhite), accent, 0.14f));
        bar.setColor(accent);
        if (shown != now) {
            shown = now;
            title.setText(now.getMusicTitle());
            artist.setText(now.getMusicAuthor());
            FemboyTrackCell.setCover(cover, now, 100);
        }
        play.setImageResource(MediaController.getInstance().isMessagePaused()
                ? R.drawable.femboy_ic_play_arrow_fill : R.drawable.femboy_ic_pause_fill);
        AndroidUtilities.cancelRunOnUIThread(tick);
        AndroidUtilities.runOnUIThread(tick);
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        AndroidUtilities.cancelRunOnUIThread(tick);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        rect.set(0, 0, getWidth(), getHeight());
        canvas.drawRoundRect(rect, dp(20), dp(20), back);
        final MessageObject now = MediaController.getInstance().getPlayingMessageObject();
        if (now != null && now == shown) {
            final float progress = Math.max(0f, Math.min(1f, now.audioProgress));
            final float left = dp(20), right = getWidth() - dp(20);
            rect.set(left, getHeight() - dp(3), left + (right - left) * progress, getHeight() - dp(1));
            canvas.drawRoundRect(rect, dp(1), dp(1), bar);
        }
    }
}
