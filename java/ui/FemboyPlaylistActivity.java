package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.femboy.FemboyMusic;
import org.telegram.femboy.FemboyMusicOnline;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

/**
 * Один плейлист, «Понравившиеся» или «Недавно играло»: обложка-коллаж,
 * название, кнопки «Слушать» и «Перемешать», треки плитками. Порядок
 * меняется в режиме «Изменить порядок» перетаскиванием.
 */
public class FemboyPlaylistActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    /** Вместо номера плейлиста: показать недавно игравшее. */
    public static final long RECENT = -1;

    private static final int ID_RENAME = 1;
    private static final int ID_DELETE = 2;
    private static final int ID_CLEAR = 3;
    private static final int ID_REORDER = 4;
    private static final int ID_DONE = 5;

    private static final int T_HEADER = 0, T_TRACK = 1, T_NOTE = 2, T_SPACE = 3;

    private final long playlistId;
    private final HashMap<FemboyMusic.Track, MessageObject> messages = new HashMap<>();
    private RecyclerListView listView;
    private Adapter adapter;
    private ItemTouchHelper touchHelper;
    private boolean reordering;
    private final ArrayList<FemboyMusic.Track> order = new ArrayList<>();
    private ActionBarMenuItem doneItem, moreItem;
    private Header header;
    private final Runnable musicListener = () -> {
        if (!reordering && adapter != null) adapter.notifyDataSetChanged();
    };

    public FemboyPlaylistActivity(long playlistId) {
        this.playlistId = playlistId;
    }

    private boolean recent() {
        return playlistId == RECENT;
    }

    private FemboyMusic.Playlist playlist() {
        return recent() ? null : FemboyMusic.playlist(playlistId);
    }

    private ArrayList<FemboyMusic.Track> tracks() {
        if (reordering) return order;
        if (recent()) return FemboyMusic.recent();
        final FemboyMusic.Playlist playlist = playlist();
        return playlist == null ? new ArrayList<>() : playlist.tracks;
    }

    private String name() {
        if (recent()) return LocaleController.getString(R.string.FemboyRecent);
        final FemboyMusic.Playlist playlist = playlist();
        return playlist == null ? "" : playlist.name;
    }

    private MessageObject message(FemboyMusic.Track track) {
        if (track.external()) return null;
        MessageObject message = messages.get(track);
        if (message == null && !messages.containsKey(track)) {
            message = track.message();
            messages.put(track, message);
        }
        return message;
    }

    @Override
    public boolean onFragmentCreate() {
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            NotificationCenter.getInstance(a).addObserver(this, NotificationCenter.messagePlayingDidStart);
            NotificationCenter.getInstance(a).addObserver(this, NotificationCenter.messagePlayingDidReset);
            NotificationCenter.getInstance(a).addObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        }
        FemboyMusic.addListener(musicListener);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            NotificationCenter.getInstance(a).removeObserver(this, NotificationCenter.messagePlayingDidStart);
            NotificationCenter.getInstance(a).removeObserver(this, NotificationCenter.messagePlayingDidReset);
            NotificationCenter.getInstance(a).removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        }
        FemboyMusic.removeListener(musicListener);
        super.onFragmentDestroy();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (listView != null && adapter != null && !reordering) {
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public View createView(Context context) {
        final int page = Theme.getColor(Theme.key_windowBackgroundGray);
        final int text = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setBackgroundColor(page);
        actionBar.setItemsColor(text, false);
        actionBar.setItemsBackgroundColor(Theme.multAlpha(text, 0.1f), false);
        actionBar.setTitleColor(text);
        actionBar.setCastShadows(false);
        actionBar.setTitle(name());
        actionBar.getTitleTextView().setAlpha(0f);
        final org.telegram.ui.ActionBar.ActionBarMenu menu = actionBar.createMenu();
        doneItem = menu.addItem(ID_DONE, R.drawable.ic_ab_done);
        doneItem.setVisibility(View.GONE);
        moreItem = menu.addItem(0, R.drawable.ic_ab_other);
        if (recent()) {
            moreItem.addSubItem(ID_CLEAR, R.drawable.femboy_ic_delete, LocaleController.getString(R.string.FemboyRecentClear));
        } else {
            moreItem.addSubItem(ID_REORDER, R.drawable.femboy_ic_drag_indicator, LocaleController.getString(R.string.FemboyReorder));
            if (playlistId != FemboyMusic.LIKED) {
                moreItem.addSubItem(ID_RENAME, R.drawable.femboy_ic_edit, LocaleController.getString(R.string.FemboyPlaylistRename));
                moreItem.addSubItem(ID_DELETE, R.drawable.femboy_ic_delete, LocaleController.getString(R.string.FemboyPlaylistDelete));
            }
        }
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (reordering) setReordering(false);
                    else finishFragment();
                } else if (id == ID_RENAME) {
                    rename();
                } else if (id == ID_DELETE) {
                    delete();
                } else if (id == ID_CLEAR) {
                    FemboyMusic.clearRecent();
                    adapter.notifyDataSetChanged();
                } else if (id == ID_REORDER) {
                    setReordering(true);
                } else if (id == ID_DONE) {
                    setReordering(false);
                }
            }
        });

        final FrameLayout frame = new FrameLayout(context);
        frame.setBackgroundColor(page);
        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setAdapter(adapter = new Adapter());
        listView.setItemAnimator(null);
        listView.setSelectorType(9);
        listView.setSelectorDrawableColor(0);
        listView.setClipToPadding(false);
        listView.setPadding(0, 0, 0, dp(24));
        listView.setOnItemClickListener((view, position, x, y) -> onClick(view, position, x));
        listView.setOnItemLongClickListener((view, position) -> {
            if (reordering) return false;
            final FemboyMusic.Track track = trackAt(position);
            if (track == null) return false;
            showMenu(track, view);
            return true;
        });
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                // название в шапке появляется, когда большое уехало
                final View first = listView.getLayoutManager() != null ? listView.getLayoutManager().findViewByPosition(0) : null;
                final float alpha = first == null ? 1f : Math.max(0, Math.min(1, -first.getTop() / (float) dp(160)));
                actionBar.getTitleTextView().setAlpha(reordering ? 1f : alpha);
            }
        });
        touchHelper = new ItemTouchHelper(new ItemTouchHelper.Callback() {
            @Override
            public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder holder) {
                return reordering && holder.getItemViewType() == T_TRACK ? makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) : 0;
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder from, @NonNull RecyclerView.ViewHolder to) {
                if (to.getItemViewType() != T_TRACK) return false;
                final int a = from.getAdapterPosition() - 1, b = to.getAdapterPosition() - 1;
                if (a < 0 || b < 0 || a >= order.size() || b >= order.size()) return false;
                Collections.swap(order, a, b);
                adapter.notifyItemMoved(from.getAdapterPosition(), to.getAdapterPosition());
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder holder, int direction) {
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return reordering;
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder holder) {
                super.clearView(recyclerView, holder);
                holder.itemView.setScaleX(1f);
                holder.itemView.setScaleY(1f);
                AndroidUtilities.runOnUIThread(() -> adapter.notifyDataSetChanged());
            }

            @Override
            public void onSelectedChanged(RecyclerView.ViewHolder holder, int state) {
                super.onSelectedChanged(holder, state);
                if (holder != null && state == ItemTouchHelper.ACTION_STATE_DRAG) {
                    holder.itemView.setScaleX(1.02f);
                    holder.itemView.setScaleY(1.02f);
                }
            }
        });
        touchHelper.attachToRecyclerView(listView);
        frame.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        fragmentView = frame;
        return frame;
    }

    private void setReordering(boolean on) {
        if (reordering == on) return;
        final FemboyMusic.Playlist playlist = playlist();
        if (playlist == null) return;
        if (on) {
            order.clear();
            order.addAll(playlist.tracks);
        } else {
            FemboyMusic.reorder(playlist, new ArrayList<>(order));
            order.clear();
        }
        reordering = on;
        doneItem.setVisibility(on ? View.VISIBLE : View.GONE);
        moreItem.setVisibility(on ? View.GONE : View.VISIBLE);
        actionBar.setTitle(on ? LocaleController.getString(R.string.FemboyReorder) : name());
        actionBar.getTitleTextView().setAlpha(on ? 1f : 0f);
        adapter.notifyDataSetChanged();
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        if (reordering) {
            if (invoked) setReordering(false);
            return false;
        }
        return super.onBackPressed(invoked);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private FemboyMusic.Track trackAt(int position) {
        final ArrayList<FemboyMusic.Track> tracks = tracks();
        final int index = position - 1;
        return index >= 0 && index < tracks.size() ? tracks.get(index) : null;
    }

    /** shuffle: true перемешать, false по порядку, null как сейчас выбрано в плеере. */
    private void play(int start, Boolean shuffle) {
        final ArrayList<FemboyMusic.Track> tracks = tracks();
        if (tracks.isEmpty()) return;
        FemboyMusic.play(new ArrayList<>(tracks), start, shuffle, name());
        adapter.notifyDataSetChanged();
    }

    private void onClick(View view, int position, float x) {
        if (reordering) return;
        final FemboyMusic.Track track = trackAt(position);
        if (track == null) return;
        if (x > FemboyTrackCell.moreEdge(view)) {
            showMenu(track, view);
            return;
        }
        if (FemboyTrackCell.inLike(view, x)) {
            FemboyMusicActions.like(track, message(track), null, () -> adapter.notifyDataSetChanged());
            return;
        }
        if (FemboyMusic.isPlaying(track.key()) && !org.telegram.messenger.MediaController.getInstance().isMessagePaused()) {
            if (FemboyPlayer.open(getParentActivity())) return;
        }
        play(position - 1, null);
    }

    private void showMenu(FemboyMusic.Track track, View anchor) {
        FemboyMusicActions.menu(this, anchor, track, message(track), BulletinFactory.of(this), playlist(), () -> {
            messages.remove(track);
            adapter.notifyDataSetChanged();
        });
    }

    private void rename() {
        final FemboyMusic.Playlist playlist = playlist();
        if (playlist == null || getContext() == null) return;
        FemboyPlaylistPicker.askName(getContext(), LocaleController.getString(R.string.FemboyPlaylistRename), playlist.name, name -> {
            FemboyMusic.rename(playlist, name);
            actionBar.setTitle(name);
            adapter.notifyDataSetChanged();
        });
    }

    private void delete() {
        final FemboyMusic.Playlist playlist = playlist();
        if (playlist == null || getContext() == null) return;
        final AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setTitle(LocaleController.getString(R.string.FemboyPlaylistDelete))
                .setMessage(LocaleController.formatString(R.string.FemboyPlaylistDeleteText, playlist.name))
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .setPositiveButton(LocaleController.getString(R.string.Delete), (d, w) -> {
                    FemboyMusic.delete(playlist);
                    finishFragment();
                })
                .create();
        showDialog(dialog);
        final View button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (button instanceof TextView) {
            ((TextView) button).setTextColor(Theme.getColor(Theme.key_text_RedBold));
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        final ArrayList<ThemeDescription> list = new ArrayList<>();
        final ThemeDescription.ThemeDescriptionDelegate delegate = () -> {
            header = null;
            if (listView != null) listView.setAdapter(adapter);
        };
        list.add(new ThemeDescription(null, 0, null, null, null, delegate, Theme.key_windowBackgroundGray));
        return list;
    }

    // шапка

    private static TextView pill(Context context, int icon, int title, int bg, int fg, View.OnClickListener click) {
        final TextView pill = new TextView(context);
        pill.setText(LocaleController.getString(title));
        pill.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        pill.setTypeface(AndroidUtilities.bold());
        pill.setTextColor(fg);
        pill.setGravity(Gravity.CENTER);
        final Drawable drawable = context.getResources().getDrawable(icon).mutate();
        drawable.setBounds(0, 0, dp(20), dp(20));
        drawable.setColorFilter(new PorterDuffColorFilter(fg, PorterDuff.Mode.SRC_IN));
        pill.setCompoundDrawables(drawable, null, null, null);
        pill.setCompoundDrawablePadding(dp(8));
        pill.setPadding(dp(16), 0, dp(20), 0);
        pill.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(26), bg, Theme.multAlpha(fg, 0.15f)));
        pill.setOnClickListener(click);
        return pill;
    }

    /** Коллаж, название, число треков и две кнопки. */
    private class Header extends LinearLayout {
        final FemboyMusicTiles.Collage collage;
        final TextView title, subtitle;
        final LinearLayout buttons;

        Header(Context context) {
            super(context);
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER_HORIZONTAL);
            setPadding(dp(16), dp(4), dp(16), dp(16));
            collage = new FemboyMusicTiles.Collage(context, dp(28));
            addView(collage, LayoutHelper.createLinear(184, 184, Gravity.CENTER_HORIZONTAL));
            title = new TextView(context);
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 26);
            title.setTypeface(AndroidUtilities.bold());
            title.setGravity(Gravity.CENTER);
            title.setMaxLines(2);
            title.setEllipsize(TextUtils.TruncateAt.END);
            addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 18, 0, 0));
            subtitle = new TextView(context);
            subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            subtitle.setGravity(Gravity.CENTER);
            addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));
            buttons = new LinearLayout(context);
            buttons.setOrientation(HORIZONTAL);
            final int accent = Theme.getColor(Theme.key_featuredStickers_addButton);
            final int onAccent = Theme.getColor(Theme.key_featuredStickers_buttonText);
            buttons.addView(pill(context, R.drawable.femboy_ic_play_arrow_fill, R.string.FemboyPlayAll, accent, onAccent, v -> play(0, false)),
                    LayoutHelper.createLinear(0, 52, 1f, 0, 0, 6, 0));
            buttons.addView(pill(context, R.drawable.femboy_ic_shuffle, R.string.FemboyShuffle,
                    Theme.getColor(Theme.key_buttonNeutral), Theme.getColor(Theme.key_buttonNeutralText), v -> play(-1, true)),
                    LayoutHelper.createLinear(0, 52, 1f, 6, 0, 0, 0));
            addView(buttons, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 52, 0, 20, 0, 0));
        }

        void bind() {
            final ArrayList<FemboyMusic.Track> tracks = tracks();
            final int accent = Theme.getColor(Theme.key_featuredStickers_addButton);
            final boolean liked = playlistId == FemboyMusic.LIKED;
            final int icon = liked ? R.drawable.femboy_ic_favorite_fill : recent() ? R.drawable.femboy_ic_history : R.drawable.femboy_ic_queue_music;
            if (liked) {
                collage.set(new ArrayList<>(), icon, accent, Theme.getColor(Theme.key_featuredStickers_buttonText));
            } else {
                collage.set(tracks, icon, Theme.multAlpha(accent, 0.14f), accent);
            }
            title.setText(name());
            title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            String text = LocaleController.formatPluralString("FemboySongs", tracks.size());
            if (liked) {
                final int offline = FemboyMusic.offlineCount();
                if (offline > 0) text += "  ·  " + LocaleController.formatString(R.string.FemboyOfflineCount, offline);
            }
            subtitle.setText(text);
            subtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            buttons.setVisibility(tracks.isEmpty() || reordering ? GONE : VISIBLE);
        }
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == T_TRACK;
        }

        @Override
        public int getItemCount() {
            final int n = tracks().size();
            return 1 + n + (n == 0 ? 1 : 0) + 1;
        }

        @Override
        public int getItemViewType(int position) {
            final int n = tracks().size();
            if (position == 0) return T_HEADER;
            if (position <= n) return T_TRACK;
            if (n == 0 && position == 1) return T_NOTE;
            return T_SPACE;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            final Context context = parent.getContext();
            View view;
            if (viewType == T_HEADER) {
                if (header == null) header = new Header(context);
                AndroidUtilities.removeFromParent(header);
                view = header;
            } else if (viewType == T_TRACK) {
                view = new FemboyTrackCell(context);
            } else if (viewType == T_NOTE) {
                final TextView note = new TextView(context);
                note.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
                note.setGravity(Gravity.CENTER);
                note.setPadding(dp(32), dp(24), dp(32), dp(24));
                view = note;
            } else {
                view = new View(context);
                view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(16)));
                return new RecyclerListView.Holder(view);
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            final int type = holder.getItemViewType();
            if (type == T_HEADER) {
                ((Header) holder.itemView).bind();
            } else if (type == T_TRACK) {
                final ArrayList<FemboyMusic.Track> tracks = tracks();
                final FemboyMusic.Track track = tracks.get(position - 1);
                final FemboyTrackCell cell = (FemboyTrackCell) holder.itemView;
                cell.set(track, message(track), FemboyMusic.isPlaying(track.key()));
                cell.setLike(!reordering, FemboyMusic.isLiked(track));
                cell.setProgress(FemboyMusicOnline.progress(track.key()));
                cell.setDragHandle(reordering);
                cell.setTile(true, position == 1, position == tracks.size());
            } else if (type == T_NOTE) {
                final TextView note = (TextView) holder.itemView;
                note.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
                note.setText(LocaleController.getString(playlistId == FemboyMusic.LIKED ? R.string.FemboyLikedEmpty : R.string.FemboyPlaylistEmpty));
            }
        }
    }

    // Как экраны самого телеграма: рисуемся под полоской жестов, а список
    // получает снизу отступ на её высоту, вместо пустой полосы под ним.
    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        if (listView != null) {
            listView.setPadding(listView.getPaddingLeft(), listView.getPaddingTop(), listView.getPaddingRight(), bottom);
            listView.setClipToPadding(false);
        }
    }
}
