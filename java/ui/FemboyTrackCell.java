package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.femboy.FemboyMusic;
import org.telegram.femboy.FemboyMusicOnline;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

/**
 * Строка трека: обложка, название, исполнитель и длина, сердечко и три точки.
 * Та, что играет сейчас, подсвечена акцентом и значком эквалайзера на обложке.
 *
 * В списках музыки строка рисует себе плитку: соседние плитки идут с зазором
 * в 2dp, у первой и последней в группе крупные внешние углы.
 */
public class FemboyTrackCell extends LinearLayout {

    private final CoverBox coverBox;
    private final BackupImageView cover;
    private final ImageView playingView;
    private final TextView titleView;
    private final TextView subtitleView;
    private final ImageView likeView;
    private final ImageView moreView;
    private int textColor, grayColor, accentColor;

    private boolean tile, first, last;
    private final Paint tilePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path tilePath = new Path();
    private final RectF tileRect = new RectF();
    private final float[] radii = new float[8];
    private boolean liked;
    private Drawable offlineDrawable;

    public FemboyTrackCell(Context context) {
        this(context, Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
    }

    public FemboyTrackCell(Context context, int textColor, int grayColor) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setMinimumHeight(dp(64));
        setPadding(dp(16), dp(8), dp(4), dp(8));
        setWillNotDraw(false);

        coverBox = new CoverBox(context);
        final ImageView note = new ImageView(context);
        note.setImageResource(R.drawable.femboy_ic_music_note);
        note.setScaleType(ImageView.ScaleType.CENTER);
        coverBox.addView(note, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        cover = new BackupImageView(context);
        cover.setRoundRadius(dp(12));
        coverBox.addView(cover, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        playingView = new ImageView(context);
        playingView.setImageResource(R.drawable.femboy_ic_graphic_eq);
        playingView.setScaleType(ImageView.ScaleType.CENTER);
        playingView.setColorFilter(new PorterDuffColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN));
        final GradientDrawable dim = new GradientDrawable();
        dim.setCornerRadius(dp(12));
        dim.setColor(0x73000000);
        playingView.setBackground(dim);
        playingView.setVisibility(GONE);
        coverBox.addView(playingView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        addView(coverBox, LayoutHelper.createLinear(48, 48, Gravity.CENTER_VERTICAL));

        final LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(VERTICAL);
        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleView.setSingleLine();
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        subtitleView = new TextView(context);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitleView.setSingleLine();
        subtitleView.setEllipsize(TextUtils.TruncateAt.END);
        subtitleView.setCompoundDrawablePadding(dp(4));
        texts.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
        addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL, 14, 0, 4, 0));

        likeView = new ImageView(context);
        likeView.setScaleType(ImageView.ScaleType.CENTER);
        likeView.setVisibility(GONE);
        likeView.setBackground(Theme.createSelectorDrawable(Theme.multAlpha(textColor, 0.12f), Theme.RIPPLE_MASK_CIRCLE_20DP));
        addView(likeView, LayoutHelper.createLinear(40, 40, Gravity.CENTER_VERTICAL));

        moreView = new ImageView(context);
        moreView.setImageResource(R.drawable.femboy_ic_more_vert);
        moreView.setScaleType(ImageView.ScaleType.CENTER);
        addView(moreView, LayoutHelper.createLinear(44, 44, Gravity.CENTER_VERTICAL));

        setColors(textColor, grayColor);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), heightMeasureSpec);
    }

    public void setColors(int text, int gray) {
        textColor = text;
        grayColor = gray;
        accentColor = Theme.getColor(Theme.key_featuredStickers_addButton);
        final GradientDrawable back = new GradientDrawable();
        back.setCornerRadius(dp(12));
        back.setColor(Theme.multAlpha(text, 0.08f));
        coverBox.setBackground(back);
        coverBox.ringColor = accentColor;
        ((ImageView) coverBox.getChildAt(0)).setColorFilter(new PorterDuffColorFilter(Theme.multAlpha(text, 0.5f), PorterDuff.Mode.SRC_IN));
        moreView.setColorFilter(new PorterDuffColorFilter(gray, PorterDuff.Mode.SRC_IN));
        subtitleView.setTextColor(gray);
        titleView.setTextColor(text);
        offlineDrawable = getContext().getResources().getDrawable(R.drawable.femboy_ic_offline_pin_fill).mutate();
        offlineDrawable.setBounds(0, 0, dp(14), dp(14));
        offlineDrawable.setColorFilter(new PorterDuffColorFilter(accentColor, PorterDuff.Mode.SRC_IN));
        updateLike();
    }

    /** Плитка в группе: фон цвета карточки, крупные углы у краёв группы. */
    public void setTile(boolean tile, boolean first, boolean last) {
        this.tile = tile;
        this.first = first;
        this.last = last;
        final int side = tile ? dp(16) : 0;
        setPadding(side + dp(tile ? 12 : 16), dp(8), side + dp(4), dp(8) + (tile && !last ? dp(2) : 0));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (tile) {
            final float big = dp(24), small = dp(6);
            final float top = first ? big : small, bottom = last ? big : small;
            radii[0] = radii[1] = radii[2] = radii[3] = top;
            radii[4] = radii[5] = radii[6] = radii[7] = bottom;
            tileRect.set(dp(16), 0, getWidth() - dp(16), getHeight() - (last ? 0 : dp(2)));
            tilePath.rewind();
            tilePath.addRoundRect(tileRect, radii, Path.Direction.CW);
            tilePaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            canvas.drawPath(tilePath, tilePaint);
            if (isPressed()) {
                tilePaint.setColor(Theme.multAlpha(textColor, 0.07f));
                canvas.drawPath(tilePath, tilePaint);
            }
        }
        super.onDraw(canvas);
    }

    /** Вместо трёх точек ручка для перетаскивания (режим «изменить порядок»). */
    public void setDragHandle(boolean drag) {
        moreView.setImageResource(drag ? R.drawable.femboy_ic_drag_indicator : R.drawable.femboy_ic_more_vert);
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        if (tile) invalidate();
    }

    public void setMoreVisible(boolean visible) {
        moreView.setVisibility(visible ? VISIBLE : GONE);
    }

    public View moreView() {
        return moreView;
    }

    public View likeView() {
        return likeView;
    }

    /** Показать сердечко и его состояние. */
    public void setLike(boolean visible, boolean liked) {
        likeView.setVisibility(visible ? VISIBLE : GONE);
        this.liked = liked;
        updateLike();
    }

    private void updateLike() {
        if (likeView == null) return;
        likeView.setImageResource(liked ? R.drawable.femboy_ic_favorite_fill : R.drawable.femboy_ic_favorite);
        likeView.setColorFilter(new PorterDuffColorFilter(liked ? accentColor : grayColor, PorterDuff.Mode.SRC_IN));
    }

    /** Ход скачивания 0..1, или -1, если не качается. */
    public void setProgress(float progress) {
        coverBox.setProgress(progress);
    }

    /** Нажатие правее этой точки считаем нажатием на три точки. */
    public static int moreEdge(View row) {
        int edge = row.getWidth() - dp(52);
        if (row instanceof FemboyTrackCell && ((FemboyTrackCell) row).tile) edge -= dp(16);
        return edge;
    }

    /** Нажатие попало в сердечко. */
    public static boolean inLike(View row, float x) {
        if (!(row instanceof FemboyTrackCell)) return false;
        final View like = ((FemboyTrackCell) row).likeView;
        return like.getVisibility() == VISIBLE && x >= like.getLeft() && x <= like.getRight();
    }

    public void set(MessageObject message, boolean current) {
        if (message == null) {
            titleView.setText("");
            subtitleView.setText("");
            cover.clearImage();
            setCurrent(false);
            return;
        }
        final String artist = message.getMusicAuthor();
        final int duration = (int) message.getDuration();
        setTexts(message.getMusicTitle(), artist, duration, FemboyMusic.isLiked(message) && FemboyMusic.localFile(message) != null);
        setCover(cover, message, 90);
        setCurrent(current);
    }

    /** Трек из нашей библиотеки (из чата или из сети). */
    public void set(FemboyMusic.Track track, MessageObject message, boolean current) {
        if (message != null && !track.external()) {
            set(message, current);
            setTexts(message.getMusicTitle(), message.getMusicAuthor(), (int) message.getDuration(), track.localFile() != null);
            return;
        }
        setTexts(track.title(), track.artist(), track.duration(), track.localFile() != null);
        setCover(cover, track);
        setCurrent(current);
    }

    private void setTexts(String title, String artist, int duration, boolean offline) {
        titleView.setText(title);
        final String time = duration > 0 ? AndroidUtilities.formatShortDuration(duration) : "";
        if (TextUtils.isEmpty(artist)) artist = "";
        subtitleView.setText(TextUtils.isEmpty(time) ? artist : TextUtils.isEmpty(artist) ? time : artist + "  ·  " + time);
        subtitleView.setCompoundDrawables(offline ? offlineDrawable : null, null, null, null);
    }

    private void setCurrent(boolean current) {
        titleView.setTextColor(current ? accentColor : textColor);
        titleView.setTypeface(current ? AndroidUtilities.bold() : null);
        playingView.setVisibility(current ? VISIBLE : GONE);
    }

    /** Обложка трека: миниатюра из документа или картинка, которую телеграм ищет сам. */
    static void setCover(BackupImageView view, MessageObject message, int size) {
        final String external = FemboyMusic.coverUrl(message);
        if (!TextUtils.isEmpty(external)) {
            view.setImage(ImageLocation.getForPath(external), null, (Drawable) null, message);
            return;
        }
        final TLRPC.Document document = message.getDocument();
        TLRPC.PhotoSize thumb = document != null ? FileLoader.getClosestPhotoSizeWithSize(document.thumbs, size) : null;
        if (!(thumb instanceof TLRPC.TL_photoSize) && !(thumb instanceof TLRPC.TL_photoSizeProgressive)) {
            thumb = null;
        }
        if (thumb != null) {
            view.setImage(ImageLocation.getForDocument(thumb, document), null, (Drawable) null, message);
            return;
        }
        final String url = message.getArtworkUrl(size < 200);
        if (!TextUtils.isEmpty(url)) {
            view.setImage(ImageLocation.getForPath(url), null, (Drawable) null, message);
        } else {
            view.clearImage();
        }
    }

    /** Обложка трека из библиотеки: по ссылке у треков из сети, из сообщения у остальных. */
    static void setCover(BackupImageView view, FemboyMusic.Track track) {
        if (track.external()) {
            if (!TextUtils.isEmpty(track.cover())) {
                view.setImage(ImageLocation.getForPath(track.cover()), null, (Drawable) null, track);
            } else {
                view.clearImage();
            }
            return;
        }
        final MessageObject message = FemboyMusicTiles.message(track);
        if (message != null) setCover(view, message, 200);
        else view.clearImage();
    }

    /** Обложка с кольцом прогресса скачивания поверх. */
    private static class CoverBox extends FrameLayout {
        int ringColor;
        float progress = -1;
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        CoverBox(Context context) {
            super(context);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeCap(Paint.Cap.ROUND);
            ring.setStrokeWidth(dp(3));
        }

        void setProgress(float p) {
            if (progress == p) return;
            progress = p;
            invalidate();
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            if (progress < 0) return;
            // затемняем обложку и рисуем кольцо скачивания
            rect.set(0, 0, getWidth(), getHeight());
            canvas.drawRoundRect(rect, dp(12), dp(12), fill(0x80000000));
            final float pad = Math.min(getWidth(), getHeight()) * 0.22f;
            rect.set(pad, pad, getWidth() - pad, getHeight() - pad);
            ring.setColor(0x40FFFFFF);
            canvas.drawArc(rect, 0, 360, false, ring);
            ring.setColor(0xFFFFFFFF);
            canvas.drawArc(rect, -90, Math.max(8, 360 * progress), false, ring);
        }

        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private Paint fill(int color) {
            fillPaint.setColor(color);
            return fillPaint;
        }
    }

    /** Фабрика для списков на UniversalAdapter: в item.object лежит трек. */
    public static class Factory extends UItem.UItemFactory<FemboyTrackCell> {
        static { setup(new Factory()); }

        @Override
        public FemboyTrackCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            return new FemboyTrackCell(context);
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            final FemboyTrackCell cell = (FemboyTrackCell) view;
            if (item.object instanceof FemboyMusic.Track) {
                cell.set((FemboyMusic.Track) item.object, (MessageObject) item.object2, item.checked);
                cell.setProgress(FemboyMusicOnline.progress(((FemboyMusic.Track) item.object).key()));
            } else {
                cell.set((MessageObject) item.object2, item.checked);
            }
        }

        public static UItem of(int id, FemboyMusic.Track track, MessageObject message, boolean current) {
            final UItem item = UItem.ofFactory(Factory.class);
            item.id = id;
            item.object = track;
            item.object2 = message;
            item.checked = current;
            return item;
        }

        @Override
        public boolean equals(UItem a, UItem b) {
            return a.object == b.object;
        }

        @Override
        public boolean contentsEquals(UItem a, UItem b) {
            return a.object == b.object && a.checked == b.checked;
        }
    }
}
