package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.femboy.FemboyMusic;
import org.telegram.femboy.FemboyMusicChats;
import org.telegram.femboy.FemboyMusicOnline;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;

/**
 * Страница музыки: заголовок с поиском, «Понравившиеся», недавнее,
 * плейлисты, «Для тебя» и вся музыка из чатов. Поиск ищет по чатам, на
 * SoundCloud и YouTube Music.
 *
 * Одна и та же страница живёт во вкладке внизу (FemboyMusicTabFragment) и на
 * отдельном экране (FemboyMusicActivity).
 */
public class FemboyMusicPage extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    /** Кто держит страницу. */
    public interface Host {
        BaseFragment fragment();

        BulletinFactory bulletins();

        /** Страница во вкладке: снизу плавающая панель вкладок. */
        boolean inTabs();

        /** Стрелка «назад» в заголовке (только на отдельном экране). */
        void back();
    }

    private static final int T_TITLE = 0, T_LIKED = 1, T_SECTION = 2, T_RECENT = 3, T_PLAYLISTS = 4,
            T_RECS = 5, T_TRACK = 6, T_LOADING = 7, T_NOTE = 8, T_SPACE = 9;

    private static final int A_NONE = 0, A_RECENT = 1, A_RECS = 2, A_RETRY_RECS = 3, A_RETRY_CHATS = 4, A_RETRY_SEARCH = 5;

    private static final int SRC_TG = 0, SRC_SC = 1, SRC_YT = 2;

    /** Трек в списке: из чата (message) или из сети (track). */
    private static final class Item {
        final FemboyMusic.Track track;
        final MessageObject message;
        final String key;

        Item(FemboyMusic.Track track, MessageObject message) {
            this.track = track;
            this.message = message;
            this.key = track != null ? track.key() : FemboyMusic.keyOf(message);
        }
    }

    private static final class Row {
        final int type;
        String text;
        int action;
        Item item;
        int index;
        boolean first, last;

        Row(int type) {
            this.type = type;
        }

        String sign() {
            return type + ":" + (item != null ? item.key : text) + ":" + action + (first ? "f" : "") + (last ? "l" : "");
        }
    }

    private final Host host;
    public final RecyclerListView listView;
    private final LinearLayoutManager layoutManager;
    private final Adapter adapter;
    private final View statusBackground;
    private final ArrayList<Row> rows = new ArrayList<>();
    private String signature = "";

    private int insetTop = AndroidUtilities.statusBarHeight;
    private int insetBottom = AndroidUtilities.navigationBarHeight;

    // библиотека
    private FemboyMusicChats chats;
    private FemboyMusicOnline.Result recs;
    private boolean recsLoading;
    private final ArrayList<MessageObject> recsFromChats = new ArrayList<>();

    // поиск
    private boolean searching;
    private String query = "";
    private int source = SRC_TG;
    private FemboyMusicChats tgSearch;
    private FemboyMusicOnline.Result onlineResult;
    private boolean onlineLoading;
    private int onlineSeq;
    private final LinearLayout searchPanel;
    private final EditTextBoldCursor searchField;
    private final ImageView searchClear;
    private final TextView[] chips = new TextView[3];
    private final Runnable searchRunnable = this::runSearch;

    private View titleRow;
    private LikedCard likedCard;
    private FemboyMusicTiles.Shelf recentShelf, playlistShelf, recsShelf;
    private int recentCount = -1, playlistCount = -1, recsCount = -1;

    private final Runnable musicListener = this::update;
    private FemboyMiniPlayer miniPlayer;

    public FemboyMusicPage(Context context, Host host) {
        super(context);
        this.host = host;
        FemboyMusic.watch();

        listView = new RecyclerListView(context) {
            // нажатия свои (wire): встроенный выбор строки шлёт полкам поддельное
            // касание, и от него у них ломалась прокрутка
            @Override
            protected boolean allowSelectChildAtPosition(View child) {
                return false;
            }
        };
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context));
        listView.setAdapter(adapter = new Adapter());
        listView.setClipToPadding(false);
        listView.setVerticalScrollBarEnabled(false);
        listView.setItemAnimator(null);
        listView.setSelectorType(9);
        listView.setSelectorDrawableColor(0);
        // Нажатия вешаем на сами строки (wire), а не на список: встроенные
        // нажатия RecyclerListView внутри пейджера вкладок не срабатывали.
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                checkLoadMore();
                if (searching && dy != 0 && listView.scrollingByUser) AndroidUtilities.hideKeyboard(searchField);
            }
        });
        addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        // что играет сейчас: над панелью вкладок
        miniPlayer = new FemboyMiniPlayer(context);
        addView(miniPlayer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 64, Gravity.BOTTOM, 12, 0, 12, 0));

        statusBackground = new View(context);
        addView(statusBackground, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 0, Gravity.TOP));

        // панель поиска сверху: поле и источники
        searchPanel = new LinearLayout(context);
        searchPanel.setOrientation(LinearLayout.VERTICAL);
        searchPanel.setVisibility(GONE);
        searchPanel.setClickable(true);
        final LinearLayout fieldRow = new LinearLayout(context);
        fieldRow.setOrientation(LinearLayout.HORIZONTAL);
        fieldRow.setGravity(Gravity.CENTER_VERTICAL);
        final ImageView back = iconButton(context, R.drawable.femboy_ic_arrow_back);
        back.setOnClickListener(v -> closeSearch());
        fieldRow.addView(back, LayoutHelper.createLinear(48, 48, Gravity.CENTER_VERTICAL, 4, 0, 0, 0));
        searchField = new EditTextBoldCursor(context);
        searchField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        searchField.setSingleLine(true);
        searchField.setBackground(null);
        searchField.setPadding(dp(4), 0, dp(4), 0);
        searchField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        searchField.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        searchField.setHint(LocaleController.getString(R.string.FemboyMusicSearch));
        searchField.setCursorSize(dp(20));
        searchField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                searchClear.setVisibility(s.length() > 0 ? VISIBLE : GONE);
                AndroidUtilities.cancelRunOnUIThread(searchRunnable);
                AndroidUtilities.runOnUIThread(searchRunnable, source == SRC_TG ? 350 : 700);
            }
        });
        searchField.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                AndroidUtilities.cancelRunOnUIThread(searchRunnable);
                runSearch();
                AndroidUtilities.hideKeyboard(searchField);
                return true;
            }
            return false;
        });
        final FrameLayout fieldBox = new FrameLayout(context);
        fieldBox.addView(searchField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        fieldRow.addView(fieldBox, LayoutHelper.createLinear(0, 52, 1f, Gravity.CENTER_VERTICAL));
        searchClear = iconButton(context, R.drawable.femboy_ic_close);
        searchClear.setVisibility(GONE);
        searchClear.setOnClickListener(v -> {
            searchField.setText("");
            AndroidUtilities.showKeyboard(searchField);
        });
        fieldRow.addView(searchClear, LayoutHelper.createLinear(48, 48, Gravity.CENTER_VERTICAL, 0, 0, 8, 0));
        searchPanel.addView(fieldRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 56, 0, 4, 0, 0));
        final HorizontalScrollView chipsScroll = new HorizontalScrollView(context);
        chipsScroll.setHorizontalScrollBarEnabled(false);
        final LinearLayout chipsRow = new LinearLayout(context);
        chipsRow.setOrientation(LinearLayout.HORIZONTAL);
        chipsRow.setPadding(dp(16), 0, dp(16), 0);
        final int[] names = {R.string.FemboySourceTelegram, R.string.FemboySourceSoundCloud, R.string.FemboySourceYouTube};
        for (int i = 0; i < 3; i++) {
            final int which = i;
            final TextView chip = new TextView(context);
            chip.setText(LocaleController.getString(names[i]));
            chip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            chip.setTypeface(AndroidUtilities.bold());
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(16), 0, dp(16), 0);
            chip.setOnClickListener(v -> setSource(which));
            chips[i] = chip;
            chipsRow.addView(chip, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 36, 0, 0, i < 2 ? 8 : 0, 0));
        }
        chipsScroll.addView(chipsRow);
        searchPanel.addView(chipsScroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 10));
        addView(searchPanel, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));
        searchPanel.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, orr, ob) -> {
            if (b - t != ob - ot) applyPadding();
        });

        ViewCompat.setOnApplyWindowInsetsListener(this, (v, insets) -> {
            final Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            final Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            insetTop = bars.top;
            insetBottom = Math.max(bars.bottom, ime.bottom);
            applyPadding();
            return insets;
        });

        updateColors();
        applyPadding();
        update();
    }

    private static ImageView iconButton(Context context, int icon) {
        final ImageView view = new ImageView(context);
        view.setImageResource(icon);
        view.setScaleType(ImageView.ScaleType.CENTER);
        view.setBackground(Theme.createSelectorDrawable(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), 0.1f), Theme.RIPPLE_MASK_CIRCLE_20DP));
        view.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.SRC_IN));
        return view;
    }

    private static int color(int key) {
        return Theme.getColor(key);
    }

    public void updateColors() {
        final int page = color(Theme.key_windowBackgroundGray);
        setBackgroundColor(page);
        statusBackground.setBackgroundColor(page);
        searchPanel.setBackgroundColor(page);
        searchField.setTextColor(color(Theme.key_windowBackgroundWhiteBlackText));
        searchField.setHintTextColor(color(Theme.key_windowBackgroundWhiteHintText));
        searchField.setCursorColor(color(Theme.key_featuredStickers_addButton));
        updateChips();
        // заголовок и карточку собираем заново в новых цветах
        titleRow = null;
        likedCard = null;
        signature = "";
        listView.setAdapter(adapter);
    }

    private void applyPadding() {
        final int under = insetBottom + (host.inTabs() ? dp(DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS) : 0);
        final boolean playing = miniPlayer != null && miniPlayer.getVisibility() == VISIBLE;
        final int bottom = under + dp(20) + (playing ? dp(72) : 0);
        if (miniPlayer != null) {
            final FrameLayout.LayoutParams mlp = (FrameLayout.LayoutParams) miniPlayer.getLayoutParams();
            final int margin = under + dp(8);
            if (mlp.bottomMargin != margin) {
                mlp.bottomMargin = margin;
                miniPlayer.setLayoutParams(mlp);
            }
        }
        int top = searching ? searchPanel.getHeight() : insetTop;
        if (searching && top == 0) top = insetTop + dp(110);
        searchPanel.setPadding(0, insetTop, 0, 0);
        final ViewGroup.LayoutParams lp = statusBackground.getLayoutParams();
        if (lp.height != insetTop) {
            lp.height = insetTop;
            statusBackground.setLayoutParams(lp);
        }
        if (listView.getPaddingTop() != top || listView.getPaddingBottom() != bottom) {
            listView.setPadding(0, top, 0, bottom);
        }
    }

    // жизнь страницы

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            NotificationCenter.getInstance(a).addObserver(this, NotificationCenter.messagePlayingDidStart);
            NotificationCenter.getInstance(a).addObserver(this, NotificationCenter.messagePlayingDidReset);
            NotificationCenter.getInstance(a).addObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        }
        FemboyMusic.addListener(musicListener);
        // общий список «Из твоих чатов» сообщает о себе той странице, что на экране
        chats = null;
        ViewCompat.requestApplyInsets(this);
        update();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            NotificationCenter.getInstance(a).removeObserver(this, NotificationCenter.messagePlayingDidStart);
            NotificationCenter.getInstance(a).removeObserver(this, NotificationCenter.messagePlayingDidReset);
            NotificationCenter.getInstance(a).removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        }
        FemboyMusic.removeListener(musicListener);
        AndroidUtilities.cancelRunOnUIThread(searchRunnable);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        refreshVisible();
        if (miniPlayer != null) {
            final int was = miniPlayer.getVisibility();
            miniPlayer.update();
            if (was != miniPlayer.getVisibility()) applyPadding();
        }
    }

    /** Экран снова показан: данные могли поменяться где-то ещё. */
    public void onResume() {
        update();
        if (miniPlayer != null) {
            miniPlayer.update();
            applyPadding();
        }
        loadRecsIfNeeded(false);
    }

    /** true, если страница сама обработала «назад» (закрыла поиск). */
    public boolean onBackPressed() {
        if (searching) {
            closeSearch();
            return true;
        }
        return false;
    }

    public void scrollToTop() {
        listView.smoothScrollToPosition(0);
    }

    /** Можно ли листать вкладки: нельзя, если палец на полке, которая сама ещё листается. */
    public boolean canSlide(MotionEvent ev, boolean forward) {
        if (ev == null) return true;
        final View[] scrollers = {recentShelf, playlistShelf, recsShelf, chips[0] != null ? (View) chips[0].getParent().getParent() : null};
        final int[] loc = new int[2];
        for (View view : scrollers) {
            if (view == null || !view.isShown()) continue;
            view.getLocationOnScreen(loc);
            if (ev.getRawX() >= loc[0] && ev.getRawX() <= loc[0] + view.getWidth()
                    && ev.getRawY() >= loc[1] && ev.getRawY() <= loc[1] + view.getHeight()) {
                return !view.canScrollHorizontally(forward ? 1 : -1);
            }
        }
        return true;
    }

    // данные

    private FemboyMusicChats chats() {
        final FemboyMusicChats now = FemboyMusicChats.library(UserConfig.selectedAccount);
        if (now != chats) {
            chats = now;
            chats.setOnChange(this::update);
        }
        if (chats.items.isEmpty() && !chats.loading && !chats.finished && !chats.failed) chats.loadMore();
        return chats;
    }

    private void checkLoadMore() {
        final int last = layoutManager.findLastVisibleItemPosition();
        if (last < 0 || last < adapter.getItemCount() - 8) return;
        if (searching) {
            if (source == SRC_TG && tgSearch != null && !tgSearch.loading && !tgSearch.finished && !tgSearch.failed) tgSearch.loadMore();
        } else if (chats != null && !chats.loading && !chats.finished && !chats.failed) {
            chats.loadMore();
        }
    }

    private void loadRecsIfNeeded(boolean force) {
        if (recsLoading) return;
        if (!force && recs != null) return;
        if (!FemboyMusicOnline.available()) {
            recs = null;
            update();
            return;
        }
        if (!force) {
            final FemboyMusicOnline.Result cached = FemboyMusicOnline.cachedRecommendations();
            if (cached != null) {
                recs = cached;
                update();
                return;
            }
        }
        recsLoading = true;
        update();
        FemboyMusicOnline.recommendations(force, result -> {
            recsLoading = false;
            recs = result;
            update();
        });
    }

    /** Запасные рекомендации: песни из чатов тех исполнителей, что нравятся. */
    private void fillRecsFromChats() {
        recsFromChats.clear();
        final HashSet<String> artists = new HashSet<>();
        for (FemboyMusic.Track track : FemboyMusic.liked().tracks) addArtist(artists, track.artist());
        final ArrayList<FemboyMusic.Track> recent = FemboyMusic.recent();
        for (int i = 0; i < Math.min(15, recent.size()); i++) addArtist(artists, recent.get(i).artist());
        if (artists.isEmpty() || chats == null) return;
        for (MessageObject message : chats.items) {
            if (recsFromChats.size() >= 20) break;
            final String artist = message.getMusicAuthor();
            if (artist != null && artists.contains(artist.trim().toLowerCase(Locale.ROOT)) && !FemboyMusic.isLiked(message)) {
                recsFromChats.add(message);
            }
        }
    }

    private static void addArtist(HashSet<String> into, String artist) {
        if (!TextUtils.isEmpty(artist)) into.add(artist.trim().toLowerCase(Locale.ROOT));
    }

    private boolean recsOnline() {
        return recs != null && recs.ok && !recs.items.isEmpty();
    }

    // строки списка

    private void build(ArrayList<Row> out) {
        if (searching) {
            buildSearch(out);
            return;
        }
        out.add(new Row(T_TITLE));
        out.add(new Row(T_LIKED));

        final ArrayList<FemboyMusic.Track> recent = FemboyMusic.recent();
        if (!recent.isEmpty()) {
            out.add(section(LocaleController.getString(R.string.FemboyRecent), A_RECENT));
            out.add(new Row(T_RECENT));
        }

        out.add(section(LocaleController.getString(R.string.FemboyPlaylists), A_NONE));
        out.add(new Row(T_PLAYLISTS));

        final FemboyMusicChats chats = chats();

        // «Для тебя»: из сети, а если не вышло, по любимым исполнителям из чатов
        out.add(section(LocaleController.getString(R.string.FemboyForYou), FemboyMusicOnline.available() && !recsLoading ? A_RECS : A_NONE));
        if (recsLoading && !recsOnline()) {
            out.add(new Row(T_RECS));
        } else if (recsOnline()) {
            out.add(new Row(T_RECS));
        } else {
            fillRecsFromChats();
            if (!recsFromChats.isEmpty()) {
                out.add(new Row(T_RECS));
                out.add(note(LocaleController.getString(R.string.FemboyForYouChats), A_NONE));
            } else if (recs != null && !recs.ok) {
                out.add(note(recs.errorText(), A_RETRY_RECS));
            } else if (!FemboyMusicOnline.available()) {
                out.add(note(LocaleController.getString(R.string.FemboyOnlineNoPython), A_NONE));
            } else {
                out.add(note(LocaleController.getString(R.string.FemboyForYouEmpty), A_NONE));
            }
        }

        out.add(section(LocaleController.getString(R.string.FemboyFromChats), A_NONE));
        addMessages(out, chats.items);
        if (chats.loading) {
            out.add(new Row(T_LOADING));
        } else if (chats.failed) {
            out.add(note(LocaleController.getString(R.string.FemboyOnlineErrorNetwork), A_RETRY_CHATS));
        } else if (chats.items.isEmpty() && chats.finished) {
            out.add(note(LocaleController.getString(R.string.FemboyFromChatsEmpty), A_NONE));
        }
        out.add(new Row(T_SPACE));
    }

    private void buildSearch(ArrayList<Row> out) {
        if (TextUtils.isEmpty(query)) {
            out.add(note(LocaleController.getString(R.string.FemboySearchHint), A_NONE));
            return;
        }
        if (source == SRC_TG) {
            if (tgSearch == null) return;
            addMessages(out, tgSearch.items);
            if (tgSearch.loading) {
                out.add(new Row(T_LOADING));
            } else if (tgSearch.failed) {
                out.add(note(LocaleController.getString(R.string.FemboyOnlineErrorNetwork), A_RETRY_SEARCH));
            } else if (tgSearch.items.isEmpty()) {
                out.add(note(LocaleController.getString(R.string.FemboySearchNothing), A_NONE));
            }
        } else {
            if (onlineLoading) {
                out.add(new Row(T_LOADING));
            } else if (onlineResult != null && !onlineResult.ok) {
                out.add(note(onlineResult.errorText(), A_RETRY_SEARCH));
            } else if (onlineResult != null && onlineResult.items.isEmpty()) {
                out.add(note(LocaleController.getString(R.string.FemboySearchNothing), A_NONE));
            } else if (onlineResult != null) {
                for (int i = 0; i < onlineResult.items.size(); i++) {
                    final Row row = new Row(T_TRACK);
                    row.item = new Item(onlineResult.items.get(i), null);
                    row.index = i;
                    row.first = i == 0;
                    row.last = i == onlineResult.items.size() - 1;
                    out.add(row);
                }
            }
        }
    }

    private static void addMessages(ArrayList<Row> out, ArrayList<MessageObject> messages) {
        for (int i = 0; i < messages.size(); i++) {
            final Row row = new Row(T_TRACK);
            row.item = new Item(null, messages.get(i));
            row.index = i;
            row.first = i == 0;
            row.last = i == messages.size() - 1;
            out.add(row);
        }
    }

    private static Row section(String text, int action) {
        final Row row = new Row(T_SECTION);
        row.text = text;
        row.action = action;
        return row;
    }

    private static Row note(String text, int action) {
        final Row row = new Row(T_NOTE);
        row.text = text;
        row.action = action;
        return row;
    }

    /** Пересобрать строки; если состав не поменялся, только перерисовать видимое. */
    public void update() {
        final ArrayList<Row> fresh = new ArrayList<>();
        build(fresh);
        final StringBuilder sign = new StringBuilder();
        for (Row row : fresh) sign.append(row.sign()).append('|');
        final String now = sign.toString();
        rows.clear();
        rows.addAll(fresh);
        if (now.equals(signature)) {
            refreshVisible();
        } else {
            signature = now;
            adapter.notifyDataSetChanged();
        }
    }

    private void refreshVisible() {
        for (int i = 0; i < listView.getChildCount(); i++) {
            final View child = listView.getChildAt(i);
            final RecyclerView.ViewHolder holder = listView.getChildViewHolder(child);
            final int position = holder.getAdapterPosition();
            if (position >= 0 && position < rows.size()) adapter.onBindViewHolder(holder, position);
        }
    }

    // поиск

    public void openSearch() {
        if (searching) return;
        searching = true;
        searchPanel.setVisibility(VISIBLE);
        statusBackground.setVisibility(GONE);
        searchPanel.setAlpha(0f);
        searchPanel.setTranslationY(-dp(12));
        searchPanel.animate().alpha(1f).translationY(0).setDuration(220).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
        applyPadding();
        update();
        listView.scrollToPosition(0);
        searchField.requestFocus();
        AndroidUtilities.runOnUIThread(() -> AndroidUtilities.showKeyboard(searchField), 120);
    }

    public void closeSearch() {
        if (!searching) return;
        searching = false;
        AndroidUtilities.hideKeyboard(searchField);
        AndroidUtilities.cancelRunOnUIThread(searchRunnable);
        searchPanel.setVisibility(GONE);
        statusBackground.setVisibility(VISIBLE);
        if (tgSearch != null) tgSearch.cancel();
        tgSearch = null;
        onlineResult = null;
        onlineLoading = false;
        onlineSeq++;
        query = "";
        searchField.setText("");
        applyPadding();
        update();
    }

    public boolean isSearching() {
        return searching;
    }

    private void setSource(int which) {
        if (source == which) return;
        source = which;
        updateChips();
        query = "";
        runSearch();
    }

    private void updateChips() {
        final int primary = color(Theme.key_featuredStickers_addButton);
        final int onPrimary = color(Theme.key_featuredStickers_buttonText);
        final int text = color(Theme.key_windowBackgroundWhiteBlackText);
        final int card = color(Theme.key_windowBackgroundWhite);
        for (int i = 0; i < chips.length; i++) {
            final TextView chip = chips[i];
            if (chip == null) continue;
            final boolean on = i == source;
            chip.setTextColor(on ? onPrimary : text);
            chip.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(18), on ? primary : card, Theme.multAlpha(on ? onPrimary : text, 0.15f)));
        }
    }

    private void runSearch() {
        final String q = searchField.getText() == null ? "" : searchField.getText().toString().trim();
        if (q.equals(query) && (source == SRC_TG ? tgSearch != null : onlineResult != null || onlineLoading)) return;
        query = q;
        if (tgSearch != null) tgSearch.cancel();
        tgSearch = null;
        onlineResult = null;
        onlineLoading = false;
        final int seq = ++onlineSeq;
        if (!TextUtils.isEmpty(q)) {
            if (source == SRC_TG) {
                tgSearch = new FemboyMusicChats(UserConfig.selectedAccount, q);
                tgSearch.setOnChange(this::update);
                tgSearch.loadMore();
            } else {
                onlineLoading = true;
                FemboyMusicOnline.search(source == SRC_SC ? FemboyMusic.SC : FemboyMusic.YT, q, 25, result -> {
                    if (seq != onlineSeq) return;
                    onlineLoading = false;
                    onlineResult = result;
                    update();
                });
            }
        }
        update();
        listView.scrollToPosition(0);
    }

    // нажатия

    private String sourceName() {
        if (!searching) return LocaleController.getString(R.string.FemboyFromChats);
        return query;
    }

    private void onClick(View view, int position, float x) {
        if (position < 0 || position >= rows.size()) return;
        final Row row = rows.get(position);
        switch (row.type) {
            case T_TRACK:
                if (x > FemboyTrackCell.moreEdge(view)) {
                    FemboyMusicActions.menu(host.fragment(), view, row.item.track, row.item.message, host.bulletins(), null, this::update);
                } else if (FemboyTrackCell.inLike(view, x)) {
                    FemboyMusicActions.like(row.item.track, row.item.message, null, this::update);
                } else {
                    playRow(row);
                }
                break;
            case T_LIKED: {
                // кнопку «играть» нажимает её собственный обработчик
                final View play = ((LikedCard) view).play;
                if (play.getVisibility() == VISIBLE && x >= play.getLeft() - dp(8) && x <= play.getRight() + dp(8)) break;
                host.fragment().presentFragment(new FemboyPlaylistActivity(FemboyMusic.LIKED));
                break;
            }
            case T_SECTION:
                if (row.action == A_RECENT) {
                    host.fragment().presentFragment(new FemboyPlaylistActivity(FemboyPlaylistActivity.RECENT));
                } else if (row.action == A_RECS) {
                    loadRecsIfNeeded(true);
                }
                break;
            case T_NOTE:
                if (row.action == A_RETRY_RECS) {
                    loadRecsIfNeeded(true);
                } else if (row.action == A_RETRY_CHATS && chats != null) {
                    chats.failed = false;
                    chats.loadMore();
                    update();
                } else if (row.action == A_RETRY_SEARCH) {
                    query = "\u0000";
                    runSearch();
                }
                break;
        }
    }

    private boolean onLongClick(View view, int position) {
        if (position < 0 || position >= rows.size()) return false;
        final Row row = rows.get(position);
        if (row.type != T_TRACK) return false;
        FemboyMusicActions.menu(host.fragment(), view, row.item.track, row.item.message, host.bulletins(), null, this::update);
        return true;
    }

    private boolean openPlayerIfCurrent(String key) {
        if (FemboyMusic.isPlaying(key) && MediaController.getInstance().getPlayingMessageObject() != null
                && !MediaController.getInstance().isMessagePaused()) {
            return FemboyPlayer.open(getContext());
        }
        return false;
    }

    private void playRow(Row row) {
        if (openPlayerIfCurrent(row.item.key)) return;
        if (row.item.track != null) {
            FemboyMusic.play(new ArrayList<>(onlineResult.items), row.index, null, sourceName());
        } else {
            final ArrayList<MessageObject> list = searching && tgSearch != null ? tgSearch.items : chats.items;
            FemboyMusic.playMessages(list, row.index, sourceName());
        }
        refreshVisible();
    }

    private void createPlaylist() {
        FemboyPlaylistPicker.askName(getContext(), LocaleController.getString(R.string.FemboyPlaylistNew), "", name -> {
            final FemboyMusic.Playlist playlist = FemboyMusic.create(name);
            host.fragment().presentFragment(new FemboyPlaylistActivity(playlist.id));
        });
    }

    private void playlistMenu(View anchor, FemboyMusic.Playlist playlist) {
        final org.telegram.ui.Components.ItemOptions options = org.telegram.ui.Components.ItemOptions.makeOptions(host.fragment(), anchor);
        options.addIf(!playlist.tracks.isEmpty(), R.drawable.femboy_ic_play_arrow_fill, LocaleController.getString(R.string.FemboyPlayAll),
                () -> FemboyMusic.play(new ArrayList<>(playlist.tracks), 0, false, playlist.name));
        options.addIf(!playlist.tracks.isEmpty(), R.drawable.femboy_ic_shuffle, LocaleController.getString(R.string.FemboyShuffle),
                () -> FemboyMusic.play(new ArrayList<>(playlist.tracks), -1, true, playlist.name));
        options.add(R.drawable.femboy_ic_edit, LocaleController.getString(R.string.FemboyPlaylistRename), () ->
                FemboyPlaylistPicker.askName(getContext(), LocaleController.getString(R.string.FemboyPlaylistRename), playlist.name, name -> FemboyMusic.rename(playlist, name)));
        options.add(R.drawable.femboy_ic_delete, LocaleController.getString(R.string.FemboyPlaylistDelete), true, () -> FemboyMusic.delete(playlist));
        options.show();
    }

    // полки

    private FemboyMusicTiles.Shelf recentShelf(Context context) {
        if (recentShelf != null) return recentShelf;
        recentShelf = new FemboyMusicTiles.Shelf(context, new FemboyMusicTiles.Shelf.Binder() {
            @Override
            public int count() {
                return Math.min(20, FemboyMusic.recent().size());
            }

            @Override
            public View create(Context context) {
                return new FemboyMusicTiles.TrackTile(context, 132);
            }

            @Override
            public void bind(View view, int position) {
                final FemboyMusic.Track track = FemboyMusic.recent().get(position);
                ((FemboyMusicTiles.TrackTile) view).set(track, FemboyMusic.isPlaying(track.key()), FemboyMusicOnline.progress(track.key()));
            }
        });
        recentShelf.setOnItemClickListener((view, position) -> {
            final ArrayList<FemboyMusic.Track> recent = FemboyMusic.recent();
            if (position >= recent.size()) return;
            if (openPlayerIfCurrent(recent.get(position).key())) return;
            FemboyMusic.play(new ArrayList<>(recent), position, null, LocaleController.getString(R.string.FemboyRecent));
        });
        recentShelf.setOnItemLongClickListener((view, position) -> {
            final ArrayList<FemboyMusic.Track> recent = FemboyMusic.recent();
            if (position >= recent.size()) return false;
            FemboyMusicActions.menu(host.fragment(), view, recent.get(position), null, host.bulletins(), null, this::update);
            return true;
        });
        return recentShelf;
    }

    private FemboyMusicTiles.Shelf playlistShelf(Context context) {
        if (playlistShelf != null) return playlistShelf;
        playlistShelf = new FemboyMusicTiles.Shelf(context, new FemboyMusicTiles.Shelf.Binder() {
            @Override
            public int count() {
                return FemboyMusic.playlists().size() + 1;
            }

            @Override
            public View create(Context context) {
                return new FemboyMusicTiles.PlaylistTile(context, 132);
            }

            @Override
            public void bind(View view, int position) {
                final ArrayList<FemboyMusic.Playlist> lists = FemboyMusic.playlists();
                if (position < lists.size()) {
                    ((FemboyMusicTiles.PlaylistTile) view).set(lists.get(position));
                } else {
                    ((FemboyMusicTiles.PlaylistTile) view).setNew();
                }
            }
        });
        playlistShelf.setOnItemClickListener((view, position) -> {
            final ArrayList<FemboyMusic.Playlist> lists = FemboyMusic.playlists();
            if (position < lists.size()) {
                host.fragment().presentFragment(new FemboyPlaylistActivity(lists.get(position).id));
            } else {
                createPlaylist();
            }
        });
        playlistShelf.setOnItemLongClickListener((view, position) -> {
            final ArrayList<FemboyMusic.Playlist> lists = FemboyMusic.playlists();
            if (position >= lists.size()) return false;
            playlistMenu(view, lists.get(position));
            return true;
        });
        return playlistShelf;
    }

    private FemboyMusicTiles.Shelf recsShelf(Context context) {
        if (recsShelf != null) return recsShelf;
        recsShelf = new FemboyMusicTiles.Shelf(context, new FemboyMusicTiles.Shelf.Binder() {
            @Override
            public int count() {
                if (recsOnline()) return recs.items.size();
                if (recsLoading) return 4;
                return recsFromChats.size();
            }

            @Override
            public View create(Context context) {
                return new FemboyMusicTiles.TrackTile(context, 132);
            }

            @Override
            public void bind(View view, int position) {
                final FemboyMusicTiles.TrackTile tile = (FemboyMusicTiles.TrackTile) view;
                if (recsOnline()) {
                    final FemboyMusic.Track track = recs.items.get(position);
                    tile.set(track, FemboyMusic.isPlaying(track.key()), FemboyMusicOnline.progress(track.key()));
                } else if (recsLoading) {
                    tile.setLoading();
                } else if (position < recsFromChats.size()) {
                    final MessageObject message = recsFromChats.get(position);
                    tile.set(message, FemboyMusic.isPlaying(FemboyMusic.keyOf(message)));
                }
            }
        });
        recsShelf.setOnItemClickListener((view, position) -> {
            if (recsOnline()) {
                if (position >= recs.items.size()) return;
                if (openPlayerIfCurrent(recs.items.get(position).key())) return;
                FemboyMusic.play(new ArrayList<>(recs.items), position, null, LocaleController.getString(R.string.FemboyForYou));
            } else if (!recsLoading && position < recsFromChats.size()) {
                if (openPlayerIfCurrent(FemboyMusic.keyOf(recsFromChats.get(position)))) return;
                FemboyMusic.playMessages(new ArrayList<>(recsFromChats), position, LocaleController.getString(R.string.FemboyForYou));
            }
        });
        recsShelf.setOnItemLongClickListener((view, position) -> {
            if (recsOnline() && position < recs.items.size()) {
                FemboyMusicActions.menu(host.fragment(), view, recs.items.get(position), null, host.bulletins(), null, this::update);
                return true;
            } else if (!recsLoading && position < recsFromChats.size()) {
                FemboyMusicActions.menu(host.fragment(), view, null, recsFromChats.get(position), host.bulletins(), null, this::update);
                return true;
            }
            return false;
        });
        return recsShelf;
    }

    // заголовок и карточка

    private View titleRow(Context context) {
        if (titleRow != null) return titleRow;
        final LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        final LinearLayout top = new LinearLayout(context);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        if (!host.inTabs()) {
            final ImageView back = iconButton(context, R.drawable.femboy_ic_arrow_back);
            back.setOnClickListener(v -> host.back());
            top.addView(back, LayoutHelper.createLinear(48, 48, Gravity.CENTER_VERTICAL, 8, 0, 0, 0));
        }
        final TextView title = new TextView(context);
        title.setText(LocaleController.getString(R.string.FemboyMusic));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 32);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(color(Theme.key_windowBackgroundWhiteBlackText));
        title.setSingleLine();
        top.addView(title, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL, host.inTabs() ? 20 : 8, 0, 8, 0));
        final ImageView add = iconButton(context, R.drawable.femboy_ic_add);
        add.setColorFilter(new PorterDuffColorFilter(color(Theme.key_buttonNeutralText), PorterDuff.Mode.SRC_IN));
        add.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(22), color(Theme.key_buttonNeutral), Theme.multAlpha(color(Theme.key_buttonNeutralText), 0.15f)));
        add.setOnClickListener(v -> createPlaylist());
        top.addView(add, LayoutHelper.createLinear(44, 44, Gravity.CENTER_VERTICAL, 0, 0, 16, 0));
        box.addView(top, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 64, 0, 8, 0, 0));

        // поиск: таблетка, по нажатию открывается панель поиска
        final LinearLayout pill = new LinearLayout(context);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(28), color(Theme.key_windowBackgroundWhite),
                Theme.multAlpha(color(Theme.key_windowBackgroundWhiteBlackText), 0.08f)));
        final ImageView icon = new ImageView(context);
        icon.setImageResource(R.drawable.femboy_ic_search);
        icon.setScaleType(ImageView.ScaleType.CENTER);
        icon.setColorFilter(new PorterDuffColorFilter(color(Theme.key_windowBackgroundWhiteGrayText2), PorterDuff.Mode.SRC_IN));
        pill.addView(icon, LayoutHelper.createLinear(24, 24, Gravity.CENTER_VERTICAL, 18, 0, 12, 0));
        final TextView hint = new TextView(context);
        hint.setText(LocaleController.getString(R.string.FemboyMusicSearch));
        hint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        hint.setTextColor(color(Theme.key_windowBackgroundWhiteGrayText2));
        hint.setSingleLine();
        pill.addView(hint, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL, 0, 0, 16, 0));
        pill.setOnClickListener(v -> openSearch());
        box.addView(pill, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 56, 16, 4, 16, 12));
        titleRow = box;
        return box;
    }

    /** Большая карточка «Понравившиеся»: сердце, число треков и кнопка «играть». */
    private class LikedCard extends FrameLayout {
        final TextView title, subtitle;
        final ImageView play;
        final LinearLayout texts;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF rect = new RectF();

        LikedCard(Context context) {
            super(context);
            setWillNotDraw(false);
            final int onPrimary = color(Theme.key_featuredStickers_buttonText);
            final ImageView heart = new ImageView(context);
            heart.setImageResource(R.drawable.femboy_ic_favorite_fill);
            heart.setScaleType(ImageView.ScaleType.CENTER);
            heart.setColorFilter(new PorterDuffColorFilter(onPrimary, PorterDuff.Mode.SRC_IN));
            // просто большое сердце, без подложки
            heart.setScaleX(2f);
            heart.setScaleY(2f);
            addView(heart, LayoutHelper.createFrame(64, 64, Gravity.LEFT | Gravity.CENTER_VERTICAL, 32, 0, 0, 0));
            texts = new LinearLayout(context);
            texts.setOrientation(LinearLayout.VERTICAL);
            title = new TextView(context);
            title.setText(LocaleController.getString(R.string.FemboyLiked));
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 21);
            title.setTypeface(AndroidUtilities.bold());
            title.setTextColor(onPrimary);
            title.setSingleLine();
            title.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(title);
            subtitle = new TextView(context);
            subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            subtitle.setTextColor(Theme.multAlpha(onPrimary, 0.8f));
            subtitle.setLineSpacing(dp(1), 1f);
            subtitle.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));
            addView(texts, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 112, 0, 32, 0));
            play = new ImageView(context);
            play.setImageResource(R.drawable.femboy_ic_play_arrow_fill);
            play.setScaleType(ImageView.ScaleType.CENTER);
            play.setColorFilter(new PorterDuffColorFilter(color(Theme.key_featuredStickers_addButton), PorterDuff.Mode.SRC_IN));
            play.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(20), onPrimary, Theme.multAlpha(color(Theme.key_featuredStickers_addButton), 0.2f)));
            play.setOnClickListener(v -> {
                final ArrayList<FemboyMusic.Track> liked = FemboyMusic.liked().tracks;
                if (liked.isEmpty()) return;
                FemboyMusic.play(new ArrayList<>(liked), 0, null, LocaleController.getString(R.string.FemboyLiked));
            });
            addView(play, LayoutHelper.createFrame(56, 56, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 32, 0));
        }

        @Override
        public void setPressed(boolean pressed) {
            super.setPressed(pressed);
            FemboyMusicTiles.pressScale(this, pressed);
        }

        void bind() {
            final int count = FemboyMusic.liked().tracks.size();
            final int offline = FemboyMusic.offlineCount();
            String text = LocaleController.formatPluralString("FemboySongs", count);
            if (offline > 0) text += "  ·  " + LocaleController.formatString(R.string.FemboyOfflineCount, offline);
            subtitle.setText(count == 0 ? LocaleController.getString(R.string.FemboyLikedEmpty) : text);
            // пустая карточка: подсказка в три строки помельче, чтобы влезла в те же 120dp
            subtitle.setMaxLines(count == 0 ? 3 : 1);
            subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, count == 0 ? 13 : 14);
            play.setVisibility(count == 0 ? GONE : VISIBLE);
            // справа место под кнопку, только если она есть
            final FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) texts.getLayoutParams();
            final int right = dp(count == 0 ? 32 : 100);
            if (lp.rightMargin != right) {
                lp.rightMargin = right;
                texts.setLayoutParams(lp);
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            // заливка основным цветом с мягким переходом к более тёмному
            final int primary = color(Theme.key_featuredStickers_addButton);
            rect.set(dp(16), dp(4), getWidth() - dp(16), getHeight() - dp(4));
            paint.setShader(new android.graphics.LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                    primary, androidx.core.graphics.ColorUtils.blendARGB(primary, 0xFF000000, 0.28f), android.graphics.Shader.TileMode.CLAMP));
            canvas.drawRoundRect(rect, dp(28), dp(28), paint);
        }
    }

    // адаптер

    private class Adapter extends RecyclerListView.SelectionAdapter {

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            final int position = holder.getAdapterPosition();
            if (position < 0 || position >= rows.size()) return false;
            final Row row = rows.get(position);
            return row.type == T_TRACK || row.type == T_LIKED || row.type == T_SECTION && row.action != A_NONE
                    || row.type == T_NOTE && row.action != A_NONE;
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        @Override
        public int getItemViewType(int position) {
            return rows.get(position).type;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            final Context context = parent.getContext();
            View view;
            switch (viewType) {
                case T_TITLE:
                    view = titleRow(context);
                    AndroidUtilities.removeFromParent(view);
                    break;
                case T_LIKED:
                    view = likedCard = new LikedCard(context);
                    view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(120)));
                    return wire(new RecyclerListView.Holder(view));
                case T_SECTION:
                    view = new SectionView(context);
                    break;
                case T_RECENT:
                    view = recentShelf(context);
                    AndroidUtilities.removeFromParent(view);
                    break;
                case T_PLAYLISTS:
                    view = playlistShelf(context);
                    AndroidUtilities.removeFromParent(view);
                    break;
                case T_RECS:
                    view = recsShelf(context);
                    AndroidUtilities.removeFromParent(view);
                    break;
                case T_TRACK:
                    view = new FemboyTrackCell(context);
                    break;
                case T_LOADING: {
                    final FrameLayout box = new FrameLayout(context);
                    final RadialProgressView progress = new RadialProgressView(context);
                    progress.setSize(dp(28));
                    progress.setProgressColor(color(Theme.key_featuredStickers_addButton));
                    box.addView(progress, LayoutHelper.createFrame(40, 40, Gravity.CENTER));
                    view = box;
                    view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(72)));
                    return new RecyclerListView.Holder(view);
                }
                case T_NOTE:
                    view = new NoteView(context);
                    break;
                default:
                    view = new View(context);
                    view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8)));
                    return new RecyclerListView.Holder(view);
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            final RecyclerListView.Holder holder = new RecyclerListView.Holder(view);
            return viewType == T_TRACK || viewType == T_SECTION || viewType == T_NOTE ? wire(holder) : holder;
        }

        /** Обычные нажатия андроида на строке; x касания нужен строкам треков (лайк, меню). */
        private RecyclerView.ViewHolder wire(RecyclerView.ViewHolder holder) {
            final View v = holder.itemView;
            final float[] downX = {0};
            v.setOnTouchListener((view, e) -> {
                if (e.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) downX[0] = e.getX();
                return false;
            });
            v.setOnClickListener(view -> onClick(view, holder.getAdapterPosition(), downX[0]));
            v.setOnLongClickListener(view -> onLongClick(view, holder.getAdapterPosition()));
            return holder;
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            final Row row = rows.get(position);
            final View view = holder.itemView;
            switch (row.type) {
                case T_LIKED:
                    ((LikedCard) view).bind();
                    break;
                case T_SECTION:
                    ((SectionView) view).set(row.text, row.action == A_RECENT ? LocaleController.getString(R.string.FemboySeeAll)
                            : row.action == A_RECS ? LocaleController.getString(R.string.FemboyRefresh) : null);
                    break;
                case T_RECENT: {
                    final int count = Math.min(20, FemboyMusic.recent().size());
                    if (count != recentCount) {
                        recentCount = count;
                        recentShelf.reload();
                    } else {
                        recentShelf.refresh();
                    }
                    break;
                }
                case T_PLAYLISTS: {
                    final int count = FemboyMusic.playlists().size();
                    if (count != playlistCount) {
                        playlistCount = count;
                        playlistShelf.reload();
                    } else {
                        playlistShelf.refresh();
                    }
                    break;
                }
                case T_RECS: {
                    final int count = recsOnline() ? recs.items.size() : recsLoading ? -4 : recsFromChats.size() + 1000;
                    if (count != recsCount) {
                        recsCount = count;
                        recsShelf.reload();
                    } else {
                        recsShelf.refresh();
                    }
                    break;
                }
                case T_TRACK: {
                    final FemboyTrackCell cell = (FemboyTrackCell) view;
                    final Item item = row.item;
                    final boolean current = FemboyMusic.isPlaying(item.key);
                    if (item.track != null) {
                        cell.set(item.track, null, current);
                        cell.setLike(true, FemboyMusic.isLiked(item.track));
                        cell.setProgress(FemboyMusicOnline.progress(item.key));
                    } else {
                        cell.set(item.message, current);
                        cell.setLike(true, FemboyMusic.isLiked(item.message));
                        cell.setProgress(-1);
                    }
                    cell.setTile(true, row.first, row.last);
                    break;
                }
                case T_NOTE:
                    ((NoteView) view).set(row.text, row.action != A_NONE);
                    break;
            }
        }
    }

    /** Заголовок раздела с действием справа («Все», «Обновить»). */
    private static class SectionView extends LinearLayout {
        final TextView title, action;

        SectionView(Context context) {
            super(context);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(dp(20), dp(18), dp(12), dp(10));
            title = new TextView(context);
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
            title.setTypeface(AndroidUtilities.bold());
            title.setSingleLine();
            title.setEllipsize(TextUtils.TruncateAt.END);
            addView(title, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
            action = new TextView(context);
            action.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            action.setTypeface(AndroidUtilities.bold());
            action.setPadding(dp(12), dp(6), dp(12), dp(6));
            addView(action, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        }

        void set(String text, String actionText) {
            title.setText(text);
            title.setTextColor(color(Theme.key_windowBackgroundWhiteBlackText));
            action.setVisibility(actionText == null ? GONE : VISIBLE);
            action.setText(actionText);
            action.setTextColor(color(Theme.key_featuredStickers_addButton));
            action.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(16), 0, Theme.multAlpha(color(Theme.key_featuredStickers_addButton), 0.12f)));
        }
    }

    /** Подсказка или ошибка в плитке, по нажатию «ещё раз». */
    private static class NoteView extends LinearLayout {
        final TextView text, retry;

        NoteView(Context context) {
            super(context);
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER_HORIZONTAL);
            text = new TextView(context);
            text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            text.setGravity(Gravity.CENTER);
            addView(text, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 28, 18, 28, 0));
            retry = new TextView(context);
            retry.setText(LocaleController.getString(R.string.FemboyRetry));
            retry.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            retry.setTypeface(AndroidUtilities.bold());
            retry.setGravity(Gravity.CENTER);
            retry.setPadding(dp(20), 0, dp(20), 0);
            addView(retry, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 40, Gravity.CENTER_HORIZONTAL, 0, 12, 0, 0));
            setPadding(dp(16), 0, dp(16), dp(18));
        }

        void set(String message, boolean canRetry) {
            text.setText(message);
            text.setTextColor(color(Theme.key_windowBackgroundWhiteGrayText2));
            retry.setVisibility(canRetry ? VISIBLE : GONE);
            retry.setTextColor(color(Theme.key_buttonNeutralText));
            final GradientDrawable back = new GradientDrawable();
            back.setCornerRadius(dp(20));
            back.setColor(color(Theme.key_buttonNeutral));
            retry.setBackground(back);
        }
    }
}
