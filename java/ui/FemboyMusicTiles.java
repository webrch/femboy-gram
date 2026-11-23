package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.femboy.FemboyMusic;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.WeakHashMap;

/** Плитки вкладки музыки: квадратные обложки, коллажи плейлистов и полки. */
public final class FemboyMusicTiles {

    private FemboyMusicTiles() {}

    private static final WeakHashMap<FemboyMusic.Track, MessageObject> messages = new WeakHashMap<>();

    /** MessageObject трека из чата, собранный один раз (для обложки и меню). */
    static MessageObject message(FemboyMusic.Track track) {
        if (track == null) return null;
        if (track.external()) return track.message();
        if (messages.containsKey(track)) return messages.get(track);
        final MessageObject message = track.message();
        messages.put(track, message);
        return message;
    }

    static int color(int key) {
        return Theme.getColor(key);
    }

    /** Обложка на всю плитку: скруглённая, с заглушкой-нотой и затемнением, если играет. */
    static class Cover extends FrameLayout {
        final ImageView note;
        final BackupImageView image;
        final ImageView playing;
        private float progress = -1;
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dim = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        final int radius;

        Cover(Context context, int radius) {
            super(context);
            this.radius = radius;
            note = new ImageView(context);
            note.setImageResource(R.drawable.femboy_ic_music_note);
            note.setScaleType(ImageView.ScaleType.CENTER);
            addView(note, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            image = new BackupImageView(context);
            image.setRoundRadius(radius);
            addView(image, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            playing = new ImageView(context);
            playing.setImageResource(R.drawable.femboy_ic_graphic_eq);
            playing.setScaleType(ImageView.ScaleType.CENTER);
            playing.setColorFilter(new PorterDuffColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN));
            final GradientDrawable shade = new GradientDrawable();
            shade.setCornerRadius(radius);
            shade.setColor(0x66000000);
            playing.setBackground(shade);
            playing.setVisibility(GONE);
            addView(playing, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeCap(Paint.Cap.ROUND);
            ring.setStrokeWidth(dp(4));
            dim.setColor(0x80000000);
            updateColors();
        }

        void updateColors() {
            final int text = color(Theme.key_windowBackgroundWhiteBlackText);
            final GradientDrawable back = new GradientDrawable();
            back.setCornerRadius(radius);
            back.setColor(Theme.multAlpha(text, 0.08f));
            setBackground(back);
            note.setColorFilter(new PorterDuffColorFilter(Theme.multAlpha(text, 0.45f), PorterDuff.Mode.SRC_IN));
        }

        void setPlaying(boolean on) {
            playing.setVisibility(on ? VISIBLE : GONE);
        }

        void setProgress(float p) {
            if (p == progress) return;
            progress = p;
            invalidate();
        }

        @Override
        protected void dispatchDraw(@NonNull Canvas canvas) {
            super.dispatchDraw(canvas);
            if (progress < 0) return;
            rect.set(0, 0, getWidth(), getHeight());
            canvas.drawRoundRect(rect, radius, radius, dim);
            final float size = Math.min(getWidth(), getHeight()) * 0.34f;
            rect.set(getWidth() / 2f - size / 2f, getHeight() / 2f - size / 2f, getWidth() / 2f + size / 2f, getHeight() / 2f + size / 2f);
            ring.setColor(0x40FFFFFF);
            canvas.drawArc(rect, 0, 360, false, ring);
            ring.setColor(0xFFFFFFFF);
            canvas.drawArc(rect, -90, Math.max(8, 360 * progress), false, ring);
        }
    }

    /** Сжатие при нажатии, как у кнопок Material 3 Expressive. */
    static void pressScale(View view, boolean pressed) {
        view.animate().scaleX(pressed ? 0.95f : 1f).scaleY(pressed ? 0.95f : 1f).setDuration(pressed ? 120 : 220).start();
    }

    /** Плитка трека: квадратная обложка, под ней название и исполнитель. */
    public static class TrackTile extends LinearLayout {
        final Cover cover;
        final TextView title, subtitle;

        public TrackTile(Context context, int size) {
            super(context);
            setOrientation(VERTICAL);
            cover = new Cover(context, dp(18));
            addView(cover, LayoutHelper.createLinear(size, size));
            title = new TextView(context);
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            title.setTypeface(AndroidUtilities.bold());
            title.setSingleLine();
            title.setEllipsize(TextUtils.TruncateAt.END);
            addView(title, LayoutHelper.createLinear(size, LayoutHelper.WRAP_CONTENT, 2, 8, 2, 0));
            subtitle = new TextView(context);
            subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            subtitle.setSingleLine();
            subtitle.setEllipsize(TextUtils.TruncateAt.END);
            addView(subtitle, LayoutHelper.createLinear(size, LayoutHelper.WRAP_CONTENT, 2, 1, 2, 0));
        }

        @Override
        public void setPressed(boolean pressed) {
            super.setPressed(pressed);
            pressScale(cover, pressed);
        }

        void colors(boolean current) {
            title.setTextColor(color(current ? Theme.key_featuredStickers_addButton : Theme.key_windowBackgroundWhiteBlackText));
            subtitle.setTextColor(color(Theme.key_windowBackgroundWhiteGrayText2));
            cover.updateColors();
        }

        public void set(FemboyMusic.Track track, boolean current, float progress) {
            colors(current);
            title.setText(track.title());
            subtitle.setText(track.artist());
            FemboyTrackCell.setCover(cover.image, track);
            cover.setPlaying(current);
            cover.setProgress(progress);
            setAlpha(1f);
        }

        public void set(MessageObject message, boolean current) {
            colors(current);
            title.setText(message.getMusicTitle());
            subtitle.setText(message.getMusicAuthor());
            FemboyTrackCell.setCover(cover.image, message, 200);
            cover.setPlaying(current);
            cover.setProgress(-1);
            setAlpha(1f);
        }

        /** Пустая плитка, пока грузится. */
        public void setLoading() {
            colors(false);
            title.setText("");
            subtitle.setText("");
            cover.image.clearImage();
            cover.setPlaying(false);
            cover.setProgress(-1);
            setAlpha(0.6f);
        }
    }

    /** Коллаж 2×2 из обложек плейлиста, или одна обложка, если разных меньше четырёх. */
    public static class Collage extends FrameLayout {
        private final BackupImageView[] images = new BackupImageView[4];
        private final ImageView icon;
        private final int radius;

        public Collage(Context context, int radius) {
            super(context);
            this.radius = radius;
            icon = new ImageView(context);
            icon.setScaleType(ImageView.ScaleType.CENTER);
            addView(icon, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            for (int i = 0; i < 4; i++) {
                images[i] = new BackupImageView(context);
                addView(images[i]);
            }
            setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), Collage.this.radius);
                }
            });
            setClipToOutline(true);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            final int w = right - left, h = bottom - top;
            final boolean grid = images[1].getVisibility() == VISIBLE;
            for (int i = 0; i < 4; i++) {
                if (grid) {
                    final int x = (i % 2) * w / 2, y = (i / 2) * h / 2;
                    images[i].layout(x, y, x + w / 2, y + h / 2);
                } else {
                    images[i].layout(0, 0, w, h);
                }
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            final int w = getMeasuredWidth(), h = getMeasuredHeight();
            for (BackupImageView image : images) {
                image.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
            }
        }

        /** iconRes и цвета для заглушки, когда обложек нет. */
        public void set(ArrayList<FemboyMusic.Track> tracks, int iconRes, int bg, int fg) {
            setBackgroundColor(bg);
            icon.setImageResource(iconRes);
            icon.setColorFilter(new PorterDuffColorFilter(fg, PorterDuff.Mode.SRC_IN));
            final ArrayList<FemboyMusic.Track> picked = new ArrayList<>();
            final HashSet<String> seen = new HashSet<>();
            for (FemboyMusic.Track track : tracks) {
                if (picked.size() >= 4) break;
                final String id = track.external() ? track.cover() : track.artist() + "|" + track.title();
                if (id != null && seen.add(id)) picked.add(track);
            }
            final boolean grid = picked.size() >= 4;
            for (int i = 0; i < 4; i++) {
                final boolean visible = grid || i == 0 && !picked.isEmpty();
                images[i].setVisibility(visible ? VISIBLE : GONE);
                if (visible) {
                    FemboyTrackCell.setCover(images[i], picked.get(i));
                } else {
                    images[i].clearImage();
                }
            }
            requestLayout();
        }
    }

    /** Плитка плейлиста: коллаж, название и число треков. */
    public static class PlaylistTile extends LinearLayout {
        final Collage collage;
        final TextView title, subtitle;

        public PlaylistTile(Context context, int size) {
            super(context);
            setOrientation(VERTICAL);
            collage = new Collage(context, dp(18));
            addView(collage, LayoutHelper.createLinear(size, size));
            title = new TextView(context);
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            title.setTypeface(AndroidUtilities.bold());
            title.setSingleLine();
            title.setEllipsize(TextUtils.TruncateAt.END);
            addView(title, LayoutHelper.createLinear(size, LayoutHelper.WRAP_CONTENT, 2, 8, 2, 0));
            subtitle = new TextView(context);
            subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            subtitle.setSingleLine();
            addView(subtitle, LayoutHelper.createLinear(size, LayoutHelper.WRAP_CONTENT, 2, 1, 2, 0));
        }

        @Override
        public void setPressed(boolean pressed) {
            super.setPressed(pressed);
            pressScale(collage, pressed);
        }

        private void colors() {
            title.setTextColor(color(Theme.key_windowBackgroundWhiteBlackText));
            subtitle.setTextColor(color(Theme.key_windowBackgroundWhiteGrayText2));
        }

        public void set(FemboyMusic.Playlist playlist) {
            colors();
            title.setText(playlist.name);
            subtitle.setText(LocaleController.formatPluralString("FemboySongs", playlist.tracks.size()));
            final int accent = color(Theme.key_featuredStickers_addButton);
            collage.set(playlist.tracks, R.drawable.femboy_ic_queue_music, Theme.multAlpha(accent, 0.14f), accent);
        }

        /** Плитка «Новый плейлист». */
        public void setNew() {
            colors();
            title.setText(LocaleController.getString(R.string.FemboyPlaylistNew));
            subtitle.setText("");
            collage.set(new ArrayList<>(), R.drawable.femboy_ic_add, color(Theme.key_windowBackgroundWhite), color(Theme.key_featuredStickers_addButton));
        }
    }

    /** Полка: горизонтальный список плиток с отступами по краям. */
    public static class Shelf extends RecyclerListView {

        public interface Binder {
            int count();

            View create(Context context);

            void bind(View view, int position);
        }

        private final Binder binder;
        private OnItemClickListener click;
        private OnItemLongClickListener longClick;

        // Нажатия вешаем на сами плитки: встроенные нажатия списка внутри
        // пейджера вкладок не срабатывали, вместо нажатия листалась полка.
        @Override
        public void setOnItemClickListener(OnItemClickListener listener) {
            click = listener;
        }

        @Override
        public void setOnItemLongClickListener(OnItemLongClickListener listener) {
            longClick = listener;
        }

        @Override
        protected boolean allowSelectChildAtPosition(View child) {
            return false;
        }

        private float downX, downY;
        private final int slop = android.view.ViewConfiguration.get(getContext()).getScaledTouchSlop();

        // Плитка не должна запрещать полке листаться: её запрет уходит выше, а
        // сама полка свайп всё равно видит.
        @Override
        public void requestDisallowInterceptTouchEvent(boolean disallow) {
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(disallow);
        }

        @Override
        public boolean onInterceptTouchEvent(android.view.MotionEvent e) {
            switch (e.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    downX = e.getX();
                    downY = e.getY();
                    break;
                case android.view.MotionEvent.ACTION_MOVE: {
                    final float dx = e.getX() - downX, dy = e.getY() - downY;
                    if (Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) && canScrollHorizontally(dx < 0 ? 1 : -1)) {
                        // свайп вбок по плитке: листаем полку, а не жмём плитку
                        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                        if (getScrollState() != SCROLL_STATE_DRAGGING) {
                            super.onInterceptTouchEvent(e);
                            return true;
                        }
                    }
                    break;
                }
            }
            return super.onInterceptTouchEvent(e);
        }

        public Shelf(Context context, Binder binder) {
            super(context);
            this.binder = binder;
            setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
            setClipToPadding(false);
            setPadding(dp(16), 0, dp(16), 0);
            setHorizontalScrollBarEnabled(false);
            setOverScrollMode(OVER_SCROLL_NEVER);
            setSelectorType(9);
            setSelectorDrawableColor(0);
            addItemDecoration(new ItemDecoration() {
                @Override
                public void getItemOffsets(@NonNull android.graphics.Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull State state) {
                    final int position = parent.getChildAdapterPosition(view);
                    outRect.left = position > 0 ? dp(12) : 0;
                }
            });
            setAdapter(new SelectionAdapter() {
                @Override
                public boolean isEnabled(RecyclerView.ViewHolder holder) {
                    return true;
                }

                @NonNull
                @Override
                public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                    final View view = binder.create(parent.getContext());
                    view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    final Holder holder = new Holder(view);
                    view.setOnClickListener(v -> {
                        final int position = holder.getAdapterPosition();
                        if (click != null && position >= 0) click.onItemClick(v, position);
                    });
                    view.setOnLongClickListener(v -> {
                        final int position = holder.getAdapterPosition();
                        return longClick != null && position >= 0 && longClick.onItemClick(v, position);
                    });
                    return holder;
                }

                @Override
                public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                    binder.bind(holder.itemView, position);
                }

                @Override
                public int getItemCount() {
                    return binder.count();
                }
            });
        }

        /** Перерисовать плитки на месте, не сбрасывая прокрутку. */
        public void refresh() {
            for (int i = 0; i < getChildCount(); i++) {
                final View child = getChildAt(i);
                final int position = getChildAdapterPosition(child);
                if (position >= 0 && position < binder.count()) binder.bind(child, position);
            }
        }

        public void reload() {
            if (getAdapter() != null) getAdapter().notifyDataSetChanged();
        }
    }
}
