package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.MimeTypeMap;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.palette.graphics.Palette;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.femboy.FemboyConfig;
import org.telegram.femboy.FemboyLyrics;
import org.telegram.femboy.FemboyMusic;
import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.audioinfo.AudioInfo;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ShareAlert;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Свой плеер FemboyGram на весь экран в духе Material 3 Expressive: большая
 * обложка с подсветкой её цветов, волнистая перемотка, кнопки с живой
 * формой, лайк, отправка в чат, очередь и текст песни (lrclib, Genius) с
 * чтением латиницей над иероглифами.
 *
 * Звук целиком на MediaController телеграма, здесь только вид и кнопки.
 * Закрывается стрелкой, кнопкой «назад» или свайпом вниз.
 */
public class FemboyPlayer extends Dialog implements NotificationCenter.NotificationCenterDelegate {

    private static FemboyPlayer shown;
    /** Что сказать, когда плеер снова откроется после выбора чата. */
    private static String pendingNotice;
    private static int pendingNoticeIcon;

    /** Открыть вместо AudioPlayerAlert. false: выключено в настройках или играет не музыка. */
    public static boolean open(Context context) {
        if (!FemboyMusic.ownPlayer()) return false;
        final MessageObject playing = MediaController.getInstance().getPlayingMessageObject();
        if (playing == null || !playing.isMusic()) return false;
        if (shown != null && shown.isShowing()) return true;
        Activity activity = AndroidUtilities.findActivity(context);
        if (activity == null) activity = LaunchActivity.instance;
        if (activity == null || activity.isFinishing()) return false;
        FemboyMusic.watch();
        try {
            final FemboyPlayer player = new FemboyPlayer(activity);
            player.show();
            shown = player;
        } catch (Throwable error) {
            org.telegram.messenger.FileLog.e(error);
            return false;
        }
        return true;
    }

    private static final int MODE_COVER = 0;
    private static final int MODE_LYRICS = 1;
    private static final int MODE_QUEUE = 2;

    private static final String PREF_AMBIENT = "music_ambient";
    private static final String PREF_ROMAJI = "music_romaji";
    private static final String PREF_TRANSLATE = "music_lyrics_translate";

    private static final int[] EVENTS = {
            NotificationCenter.messagePlayingDidStart,
            NotificationCenter.messagePlayingPlayStateChanged,
            NotificationCenter.messagePlayingDidReset,
            NotificationCenter.messagePlayingProgressDidChanged,
            NotificationCenter.musicDidLoad,
            NotificationCenter.moreMusicDidLoad,
            NotificationCenter.fileLoaded,
    };

    /** Цвета плеера от обложки, по ролям MD3. Все непрозрачные. */
    private static final class Scheme {
        int bg, primary, onPrimary, container, onContainer, track, gray, menu, inverse, onInverse, inversePrimary;

        Scheme blend(Scheme to, float t) {
            final Scheme s = new Scheme();
            s.bg = ColorUtils.blendARGB(bg, to.bg, t);
            s.primary = ColorUtils.blendARGB(primary, to.primary, t);
            s.onPrimary = ColorUtils.blendARGB(onPrimary, to.onPrimary, t);
            s.container = ColorUtils.blendARGB(container, to.container, t);
            s.onContainer = ColorUtils.blendARGB(onContainer, to.onContainer, t);
            s.track = ColorUtils.blendARGB(track, to.track, t);
            s.gray = ColorUtils.blendARGB(gray, to.gray, t);
            s.menu = ColorUtils.blendARGB(menu, to.menu, t);
            s.inverse = ColorUtils.blendARGB(inverse, to.inverse, t);
            s.onInverse = ColorUtils.blendARGB(onInverse, to.onInverse, t);
            s.inversePrimary = ColorUtils.blendARGB(inversePrimary, to.inversePrimary, t);
            return s;
        }
    }

    /** Строка текста песни: над ней мелко чтение латиницей, под ней перевод. У пустой строки (между куплетами) только root. */
    private static final class Row {
        View root;
        TextView ruby, main, tr;
    }

    private final Activity activity;
    private final int baseColor, textColor, grayColor, accentColor;
    private final boolean dark;

    private FrameLayout container;
    private Sheet sheet;
    private FemboyPlayerAmbient ambient;
    private LinearLayout content;
    private View evenSpace;
    private TextView captionView, sourceView;
    private ImageView closeButton, moreButton;
    private FrameLayout middle;
    private FrameLayout coverHolder;
    private BackupImageView coverImage;
    private LinearLayout lyricsPage;
    private TextView lyricsTitle, lyricsInfo;
    private FemboyPlayerButton romajiButton, translateButton;
    private ValueAnimator translatePulse;
    private ScrollView lyricsScroll;
    private LinearLayout lyricsBox;
    private TextView lyricsStatus;
    private LinearLayout queuePage;
    private TextView queueTitle;
    private RecyclerListView queueList;
    private QueueAdapter queueAdapter;
    private TextView titleView, artistView;
    private ImageView likeButton, addButton, sendButton;
    private FemboyWavySeekBar seek;
    private FemboyPlayerButton shuffleButton, prevButton, playButton, nextButton, repeatButton;
    private TextView lyricsSegment, queueSegment;

    private int mode = MODE_COVER;
    private MessageObject current;
    private String currentKey;
    private String coverFile;
    private boolean coverFromTags;
    private int tint;
    private Scheme scheme;
    private ValueAnimator schemeAnimator;
    private boolean closing;
    private boolean ambientOn;
    private boolean romajiOn;
    private boolean translateOn;
    /** Идёт перевод для этого текста: второй раз не просим. */
    private FemboyLyrics.Lyrics translating;
    private boolean paused = true;

    private String lyricsKey;
    private FemboyLyrics.Lyrics lyrics;
    private final ArrayList<Row> lyricRows = new ArrayList<>();
    private int lyricIndex = -1;
    private long lastLyricsTouch;

    private final Theme.ResourcesProvider md3 = new Theme.ResourcesProvider() {
        @Override
        public int getColor(int key) {
            if (scheme != null) {
                if (key == Theme.key_undo_background) return scheme.inverse;
                if (key == Theme.key_undo_infoColor) return scheme.onInverse;
                if (key == Theme.key_undo_cancelColor) return scheme.inversePrimary;
                if (key == Theme.key_actionBarDefaultSubmenuBackground) return scheme.menu;
                if (key == Theme.key_actionBarDefaultSubmenuItem) return textColor;
                if (key == Theme.key_actionBarDefaultSubmenuItemIcon) return scheme.gray;
            }
            return Theme.getColor(key);
        }
    };

    private FemboyPlayer(Activity activity) {
        super(activity, R.style.TransparentDialog);
        this.activity = activity;
        baseColor = Theme.getColor(Theme.key_windowBackgroundWhite);
        textColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
        grayColor = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2);
        accentColor = Theme.getColor(Theme.key_featuredStickers_addButton);
        dark = AndroidUtilities.computePerceivedBrightness(baseColor) < 0.5f;
        tint = baseColor;
        ambientOn = FemboyConfig.prefs().getBoolean(PREF_AMBIENT, true);
        romajiOn = FemboyConfig.prefs().getBoolean(PREF_ROMAJI, true);
        translateOn = FemboyConfig.prefs().getBoolean(PREF_TRANSLATE, false);
        scheme = schemeFor(accentColor, baseColor);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Window window = getWindow();
        window.setWindowAnimations(R.style.DialogNoAnimation);
        window.setBackgroundDrawable(new ColorDrawable(0));
        AndroidUtilities.enableEdgeToEdge(window);
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        final Context context = getContext();

        container = new FrameLayout(context);
        sheet = new Sheet(context);
        container.addView(sheet, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        setContentView(container, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // как у шторок телеграма: окно и вид заходят под полоску жестов, иначе
        // она отрезает низ, и под ней видно то, что лежит под плеером
        container.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

        final WindowManager.LayoutParams params = window.getAttributes();
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        params.height = ViewGroup.LayoutParams.MATCH_PARENT;
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.dimAmount = 0;
        params.flags &= ~WindowManager.LayoutParams.FLAG_DIM_BEHIND;
        AndroidUtilities.applyEdgeToEdgeLayoutParams(params);
        window.setAttributes(params);
        setCanceledOnTouchOutside(false);

        // подсветка под всем остальным, но над фоном листа
        ambient = new FemboyPlayerAmbient(context);
        ambient.setStrength(dark ? 0.9f : 0.6f);
        ambient.setVisibility(ambientOn ? View.VISIBLE : View.GONE);
        sheet.addView(ambient, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        // Раскладка считается, а не весами: свободное место делится на три
        // равные части (над обложкой, под ней, над нижним рядом). Высота блока
        // обложки одна во всех режимах, поэтому текст и очередь ничего не двигают.
        content = new LinearLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                if (middle != null && evenSpace != null && MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED) {
                    final int height = MeasureSpec.getSize(heightMeasureSpec) - getPaddingTop() - getPaddingBottom();
                    final int width = MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight();
                    int fixed = 0;
                    for (int i = 0; i < getChildCount(); i++) {
                        final View child = getChildAt(i);
                        final LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) child.getLayoutParams();
                        fixed += lp.topMargin + lp.bottomMargin;
                        if (child == middle || child == evenSpace || child.getVisibility() == GONE) continue;
                        measureChildWithMargins(child, widthMeasureSpec, 0, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), 0);
                        fixed += child.getMeasuredHeight();
                    }
                    final int free = Math.max(0, height - fixed);
                    final int side = Math.max(dp(120), Math.min(width, free - dp(36)));
                    final int gap = Math.max(0, (free - side) / 3);
                    final LinearLayout.LayoutParams m = (LinearLayout.LayoutParams) middle.getLayoutParams();
                    final LinearLayout.LayoutParams e = (LinearLayout.LayoutParams) evenSpace.getLayoutParams();
                    m.weight = 0;
                    m.height = side + gap * 2;
                    e.weight = 0;
                    e.height = gap;
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        };
        content.setOrientation(LinearLayout.VERTICAL);
        sheet.addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        ViewCompat.setOnApplyWindowInsetsListener(container, (v, insets) -> {
            final Insets bars = AndroidUtilities.getDefaultWindowInsets(insets, false);
            content.setPadding(dp(24) + bars.left, bars.top + dp(4), dp(24) + bars.right, bars.bottom + dp(12));
            return WindowInsetsCompat.CONSUMED;
        });

        buildTop(context);
        buildMiddle(context);
        buildInfo(context);
        buildControls(context);
        ambient.setAnchor(coverHolder);

        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            for (int event : EVENTS) NotificationCenter.getInstance(a).addObserver(this, event);
        }
        update(true);
        applyScheme(scheme);

        sheet.setVisibility(View.INVISIBLE);
        container.post(() -> {
            sheet.setVisibility(View.VISIBLE);
            sheet.setTranslationY(container.getHeight());
            sheet.animate().translationY(0).setDuration(320).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                    .setUpdateListener(a -> dimFromOffset()).start();
            if (pendingNotice != null) {
                final String text = pendingNotice;
                final int icon = pendingNoticeIcon;
                pendingNotice = null;
                AndroidUtilities.runOnUIThread(() -> notice(icon, text), 300);
            }
        });
    }

    // разметка

    /** Обычная кнопка-значок MD3: прозрачная, круглая рябь. */
    private ImageView icon(Context context, int res, int sizeDp, int color) {
        final ImageView view = new ImageView(context);
        view.setImageResource(res);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        final int pad = Math.max(0, (48 - sizeDp) / 2);
        view.setPadding(dp(pad), dp(pad), dp(pad), dp(pad));
        view.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
        view.setBackground(Theme.createSelectorDrawable(Theme.multAlpha(textColor, 0.12f), Theme.RIPPLE_MASK_CIRCLE_20DP));
        return view;
    }

    private static void tintImage(ImageView view, int color) {
        view.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
    }

    private void buildTop(Context context) {
        final FrameLayout top = new FrameLayout(context);
        closeButton = icon(context, R.drawable.femboy_ic_keyboard_arrow_down, 30, textColor);
        closeButton.setOnClickListener(v -> dismiss());
        top.addView(closeButton, LayoutHelper.createFrame(48, 48, Gravity.LEFT | Gravity.CENTER_VERTICAL, -12, 0, 0, 0));

        final LinearLayout middleText = new LinearLayout(context);
        middleText.setOrientation(LinearLayout.VERTICAL);
        middleText.setGravity(Gravity.CENTER_HORIZONTAL);
        // labelMedium сверху, titleSmall под ним
        captionView = new TextView(context);
        captionView.setText(LocaleController.getString(R.string.FemboyNowPlaying));
        captionView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        captionView.setTypeface(AndroidUtilities.bold());
        captionView.setLetterSpacing(0.04f);
        captionView.setTextColor(grayColor);
        captionView.setGravity(Gravity.CENTER);
        middleText.addView(captionView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));
        sourceView = new TextView(context);
        sourceView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        sourceView.setTypeface(AndroidUtilities.bold());
        sourceView.setTextColor(textColor);
        sourceView.setSingleLine();
        sourceView.setEllipsize(TextUtils.TruncateAt.END);
        sourceView.setGravity(Gravity.CENTER);
        middleText.addView(sourceView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 1, 0, 0));
        top.addView(middleText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 48, 0, 48, 0));

        moreButton = icon(context, R.drawable.femboy_ic_more_vert, 24, textColor);
        moreButton.setOnClickListener(v -> showMenu(moreButton));
        top.addView(moreButton, LayoutHelper.createFrame(48, 48, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, -12, 0));
        content.addView(top, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 52));
    }

    private void buildMiddle(Context context) {
        middle = new FrameLayout(context);
        content.addView(middle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

        // обложка: квадрат по меньшей стороне, по центру, скругление как у больших карточек MD3
        coverHolder = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                final int side = Math.min(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
                final int exact = MeasureSpec.makeMeasureSpec(side, MeasureSpec.EXACTLY);
                super.onMeasure(exact, exact);
            }
        };
        final GradientDrawable placeholder = new GradientDrawable();
        placeholder.setCornerRadius(dp(28));
        placeholder.setColor(Theme.multAlpha(textColor, 0.08f));
        coverHolder.setBackground(placeholder);
        coverHolder.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(28));
            }
        });
        coverHolder.setClipToOutline(true);
        coverHolder.setElevation(dp(ambientOn ? 4 : 10));
        final ImageView note = new ImageView(context);
        note.setImageResource(R.drawable.femboy_ic_music_note);
        note.setScaleType(ImageView.ScaleType.FIT_CENTER);
        note.setColorFilter(new PorterDuffColorFilter(Theme.multAlpha(textColor, 0.35f), PorterDuff.Mode.SRC_IN));
        coverHolder.addView(note, LayoutHelper.createFrame(96, 96, Gravity.CENTER));
        coverImage = new BackupImageView(context);
        coverImage.getImageReceiver().setDelegate((receiver, set, thumb, memCache) -> {
            if (set && !thumb) onCoverBitmap(receiver.getBitmap());
        });
        coverHolder.addView(coverImage, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        middle.addView(coverHolder, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));

        // текст песни
        lyricsPage = new LinearLayout(context);
        lyricsPage.setOrientation(LinearLayout.VERTICAL);
        lyricsPage.setVisibility(View.GONE);
        final LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        final LinearLayout headerTexts = new LinearLayout(context);
        headerTexts.setOrientation(LinearLayout.VERTICAL);
        lyricsTitle = pageTitle(context, R.string.FemboyLyrics);
        headerTexts.addView(lyricsTitle, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        lyricsInfo = new TextView(context);
        lyricsInfo.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        lyricsInfo.setTextColor(grayColor);
        lyricsInfo.setVisibility(View.GONE);
        headerTexts.addView(lyricsInfo, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 1, 0, 0));
        header.addView(headerTexts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        romajiButton = new FemboyPlayerButton(context, 22);
        // «ツ» похожа на улыбку и сразу говорит, что речь о японском чтении
        romajiButton.icon.setImageDrawable(new FemboyLyricsGlyph("ツ", 0.8f));
        romajiButton.setContentDescription(LocaleController.getString(R.string.FemboyRomaji));
        romajiButton.setVisibility(View.GONE);
        romajiButton.setOnClickListener(v -> {
            romajiOn = !romajiOn;
            FemboyConfig.prefs().edit().putBoolean(PREF_ROMAJI, romajiOn).apply();
            applyRomaji();
            paintToggles();
        });
        header.addView(romajiButton, LayoutHelper.createLinear(40, 40, Gravity.CENTER_VERTICAL));
        translateButton = new FemboyPlayerButton(context, 22);
        translateButton.setIcon(R.drawable.femboy_ic_translate);
        translateButton.setContentDescription(LocaleController.getString(R.string.FemboyLyricsTranslate));
        translateButton.setVisibility(View.GONE);
        translateButton.setOnClickListener(v -> {
            translateOn = !translateOn;
            FemboyConfig.prefs().edit().putBoolean(PREF_TRANSLATE, translateOn).apply();
            if (translateOn) startTranslation();
            applyTranslation();
            paintToggles();
        });
        header.addView(translateButton, LayoutHelper.createLinear(40, 40, Gravity.CENTER_VERTICAL, 4, 0, 0, 0));
        // высота шапки не зависит от того, видны ли кнопки
        header.setMinimumHeight(dp(40));
        lyricsPage.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 4));

        final FrameLayout lyricsFrame = new FrameLayout(context);
        lyricsScroll = new ScrollView(context) {
            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                lastLyricsTouch = SystemClock.elapsedRealtime();
                return super.dispatchTouchEvent(ev);
            }
        };
        lyricsScroll.setVerticalScrollBarEnabled(false);
        lyricsScroll.setVerticalFadingEdgeEnabled(true);
        lyricsScroll.setFadingEdgeLength(dp(40));
        lyricsBox = new LinearLayout(context);
        lyricsBox.setOrientation(LinearLayout.VERTICAL);
        lyricsBox.setPadding(0, dp(16), 0, dp(160));
        lyricsScroll.addView(lyricsBox, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        lyricsFrame.addView(lyricsScroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        lyricsStatus = new TextView(context);
        lyricsStatus.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        lyricsStatus.setTextColor(grayColor);
        lyricsStatus.setGravity(Gravity.CENTER);
        lyricsFrame.addView(lyricsStatus, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 16, 0, 16, 0));
        lyricsPage.addView(lyricsFrame, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));
        middle.addView(lyricsPage, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        // очередь
        queuePage = new LinearLayout(context);
        queuePage.setOrientation(LinearLayout.VERTICAL);
        queuePage.setVisibility(View.GONE);
        queueTitle = pageTitle(context, R.string.FemboyQueue);
        queuePage.addView(queueTitle, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));
        queueList = new RecyclerListView(context);
        queueList.setLayoutManager(new LinearLayoutManager(context));
        queueList.setVerticalScrollBarEnabled(false);
        queueList.setAdapter(queueAdapter = new QueueAdapter());
        queueList.setSelectorDrawableColor(Theme.multAlpha(textColor, 0.08f));
        queueList.setOnItemClickListener((view, position) -> {
            if (position < 0 || position >= queueAdapter.items.size()) return;
            MediaController.getInstance().findMessageInPlaylistAndPlay(queueAdapter.items.get(position));
        });
        queuePage.addView(queueList, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f, -16, 0, -16, 0));
        middle.addView(queuePage, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
    }

    /** Заголовок страницы: titleLarge. */
    private TextView pageTitle(Context context, int res) {
        final TextView title = new TextView(context);
        title.setText(LocaleController.getString(res));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(textColor);
        return title;
    }

    private void buildInfo(Context context) {
        final LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        final LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        // headlineSmall и titleMedium из шкалы MD3
        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(textColor);
        titleView.setSingleLine();
        titleView.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        titleView.setMarqueeRepeatLimit(-1);
        titleView.setHorizontalFadingEdgeEnabled(true);
        titleView.setSelected(true);
        texts.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        artistView = new TextView(context);
        artistView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        artistView.setLetterSpacing(0.01f);
        artistView.setTextColor(grayColor);
        artistView.setSingleLine();
        artistView.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(artistView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
        row.addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL));
        likeButton = icon(context, R.drawable.femboy_ic_favorite, 26, textColor);
        likeButton.setContentDescription(LocaleController.getString(R.string.FemboyLike));
        likeButton.setOnClickListener(v -> toggleLike());
        row.addView(likeButton, LayoutHelper.createLinear(48, 48, Gravity.CENTER_VERTICAL, 8, 0, -12, 0));
        content.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        seek = new FemboyWavySeekBar(context);
        seek.setListener(new FemboyWavySeekBar.Listener() {
            @Override
            public void onSeekStart() {
            }

            @Override
            public void onSeekMove(float progress) {
            }

            @Override
            public void onSeekEnd(float progress) {
                final MessageObject playing = MediaController.getInstance().getPlayingMessageObject();
                if (playing != null) MediaController.getInstance().seekToProgress(playing, progress);
                lastLyricsTouch = 0;
            }
        });
        content.addView(seek, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, -6, 10, -6, 0));
    }

    private void buildControls(Context context) {
        final LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        shuffleButton = new FemboyPlayerButton(context, 24);
        shuffleButton.setIcon(R.drawable.femboy_ic_shuffle);
        shuffleButton.setContentDescription(LocaleController.getString(R.string.FemboyShuffle));
        shuffleButton.setOnClickListener(v -> {
            MediaController.getInstance().setPlaybackOrderType(SharedConfig.shuffleMusic ? 0 : 2);
            updateModes();
            fillQueue();
        });
        prevButton = new FemboyPlayerButton(context, 30);
        prevButton.setIcon(R.drawable.femboy_ic_skip_previous_fill);
        prevButton.setShape(0.5f, 0.25f, false);
        prevButton.setOnClickListener(v -> MediaController.getInstance().playPreviousMessage());
        nextButton = new FemboyPlayerButton(context, 30);
        nextButton.setIcon(R.drawable.femboy_ic_skip_next_fill);
        nextButton.setShape(0.5f, 0.25f, false);
        nextButton.setOnClickListener(v -> MediaController.getInstance().playNextMessage());
        repeatButton = new FemboyPlayerButton(context, 24);
        repeatButton.setIcon(R.drawable.femboy_ic_repeat);
        repeatButton.setOnClickListener(v -> {
            SharedConfig.setRepeatMode((SharedConfig.repeatMode + 1) % 3);
            updateModes();
        });

        // большая залитая кнопка: на паузе круг, когда играет, скруглённый квадрат
        playButton = new FemboyPlayerButton(context, 36);
        playButton.setPressScale(0.9f);
        playButton.setOnClickListener(v -> {
            final MediaController controller = MediaController.getInstance();
            final MessageObject playing = controller.getPlayingMessageObject();
            if (playing == null) return;
            if (controller.isMessagePaused()) controller.playMessage(playing);
            else controller.pauseMessage(playing);
        });

        row.addView(shuffleButton, LayoutHelper.createLinear(48, 48));
        row.addView(new View(context), LayoutHelper.createLinear(0, 1, 1f));
        row.addView(prevButton, LayoutHelper.createLinear(60, 60));
        row.addView(new View(context), LayoutHelper.createLinear(0, 1, 1f));
        row.addView(playButton, LayoutHelper.createLinear(80, 80));
        row.addView(new View(context), LayoutHelper.createLinear(0, 1, 1f));
        row.addView(nextButton, LayoutHelper.createLinear(60, 60));
        row.addView(new View(context), LayoutHelper.createLinear(0, 1, 1f));
        row.addView(repeatButton, LayoutHelper.createLinear(48, 48));
        content.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, -4, 8, -4, 0));

        // низ: в плейлист, связанные кнопки «текст» и «очередь», отправить
        final LinearLayout bottom = new LinearLayout(context);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        addButton = icon(context, R.drawable.femboy_ic_playlist_add, 24, textColor);
        addButton.setContentDescription(LocaleController.getString(R.string.FemboyAddToPlaylist));
        addButton.setOnClickListener(v -> {
            if (current != null && FemboyMusic.canAdd(current)) {
                FemboyPlaylistPicker.show(activity, current, BulletinFactory.of(sheet, md3));
            }
        });
        bottom.addView(addButton, LayoutHelper.createLinear(48, 48, Gravity.CENTER_VERTICAL, -12, 0, 0, 0));
        final LinearLayout group = new LinearLayout(context);
        group.setOrientation(LinearLayout.HORIZONTAL);
        group.setGravity(Gravity.CENTER);
        lyricsSegment = segment(context, R.drawable.femboy_ic_lyrics, R.string.FemboyLyrics);
        lyricsSegment.setOnClickListener(v -> setMode(mode == MODE_LYRICS ? MODE_COVER : MODE_LYRICS));
        queueSegment = segment(context, R.drawable.femboy_ic_queue_music, R.string.FemboyQueue);
        queueSegment.setOnClickListener(v -> setMode(mode == MODE_QUEUE ? MODE_COVER : MODE_QUEUE));
        group.addView(lyricsSegment, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 40));
        group.addView(queueSegment, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 40, 2, 0, 0, 0));
        bottom.addView(group, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL));
        sendButton = icon(context, R.drawable.femboy_ic_send, 22, textColor);
        sendButton.setContentDescription(LocaleController.getString(R.string.FemboySendToChat));
        sendButton.setOnClickListener(v -> sendToChat());
        bottom.addView(sendButton, LayoutHelper.createLinear(48, 48, Gravity.CENTER_VERTICAL, 0, 0, -12, 0));
        // Свободное место делится: обложка по центру своей зоны (поровну сверху
        // и снизу) и немного над нижним рядом. Веса не меняются между режимами,
        // поэтому при переключении на текст или очередь ничего не прыгает.
        evenSpace = new View(context);
        content.addView(evenSpace, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0));
        content.addView(bottom, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 4, 0, 0));
    }

    private TextView segment(Context context, int iconRes, int textRes) {
        final TextView view = new TextView(context);
        view.setText(LocaleController.getString(textRes));
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        view.setTypeface(AndroidUtilities.bold());
        view.setGravity(Gravity.CENTER);
        view.setSingleLine();
        view.setPadding(dp(14), 0, dp(16), 0);
        view.setCompoundDrawablePadding(dp(8));
        final Drawable drawable = ContextCompat.getDrawable(context, iconRes);
        if (drawable != null) {
            final Drawable icon = drawable.mutate();
            icon.setBounds(0, 0, dp(18), dp(18));
            view.setCompoundDrawables(icon, null, null, null);
        }
        return view;
    }

    /** Связанная группа MD3 Expressive: внешние углы круглые, внутренние маленькие, выбранный сегмент целиком круглый. */
    private void paintSegment(TextView view, boolean selected, boolean first) {
        final float full = dp(20), inner = dp(6);
        final GradientDrawable shape = new GradientDrawable();
        if (selected) {
            shape.setCornerRadius(full);
        } else if (first) {
            shape.setCornerRadii(new float[]{full, full, inner, inner, inner, inner, full, full});
        } else {
            shape.setCornerRadii(new float[]{inner, inner, full, full, full, full, inner, inner});
        }
        final int fill = selected ? scheme.container : Theme.multAlpha(textColor, dark ? 0.1f : 0.07f);
        shape.setColor(fill);
        final int fg = selected ? scheme.onContainer : textColor;
        view.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(Theme.multAlpha(fg, 0.12f)), shape, null));
        view.setTextColor(fg);
        final Drawable[] drawables = view.getCompoundDrawables();
        if (drawables[0] != null) drawables[0].setColorFilter(new PorterDuffColorFilter(fg, PorterDuff.Mode.SRC_IN));
    }

    // состояние

    private static String keyOf(MessageObject message) {
        return message == null ? null : message.currentAccount + "_" + message.getDialogId() + "_" + message.getId();
    }

    private static boolean same(MessageObject a, MessageObject b) {
        return a != null && b != null && (a == b || a.currentAccount == b.currentAccount && a.getId() == b.getId() && a.getDialogId() == b.getDialogId());
    }

    private void update(boolean force) {
        final MessageObject playing = MediaController.getInstance().getPlayingMessageObject();
        if (playing == null || !playing.isMusic()) {
            if (!force) dismiss();
            return;
        }
        final String key = keyOf(playing);
        final boolean changed = force || !TextUtils.equals(key, currentKey);
        current = playing;
        if (changed) {
            currentKey = key;
            titleView.setText(playing.getMusicTitle());
            artistView.setText(playing.getMusicAuthor());
            sourceView.setText(sourceName(playing));
            final boolean canAdd = FemboyMusic.canAdd(playing);
            addButton.setEnabled(canAdd);
            addButton.setAlpha(canAdd ? 1f : 0.35f);
            updateLike(false);
            updateCover(playing);
            if (mode == MODE_LYRICS) loadLyrics();
            fillQueue();
        }
        updatePlayState();
        updateModes();
        updateProgress();
    }

    private String sourceName(MessageObject message) {
        final String ours = FemboyMusic.sourceOf(message);
        if (ours != null) return ours;
        final long did = message.getDialogId();
        if (did != 0 && !FemboyMusic.isExternal(message)) {
            final MessagesController controller = MessagesController.getInstance(message.currentAccount);
            if (did > 0) {
                final TLRPC.User user = controller.getUser(did);
                if (user != null) return UserObject.getUserName(user);
            } else {
                final TLRPC.Chat chat = controller.getChat(-did);
                if (chat != null) return chat.title;
            }
        }
        return LocaleController.getString(R.string.FemboyMusic);
    }

    private void updatePlayState() {
        paused = MediaController.getInstance().isMessagePaused();
        playButton.setIcon(paused ? R.drawable.femboy_ic_play_arrow_fill : R.drawable.femboy_ic_pause_fill);
        playButton.setContentDescription(LocaleController.getString(paused ? R.string.AccActionPlay : R.string.AccActionPause));
        playButton.setShape(paused ? 0.5f : 0.3f, 0.2f, true);
        seek.setPlaying(!paused);
        if (ambient != null) ambient.setBreathing(ambientOn && !paused);
    }

    private void updateModes() {
        final int repeat = SharedConfig.repeatMode;
        repeatButton.setIcon(repeat == 2 ? R.drawable.femboy_ic_repeat_one : R.drawable.femboy_ic_repeat);
        repeatButton.setContentDescription(LocaleController.getString(R.string.FemboyRepeat));
        paintToggles();
    }

    /** Переключатели MD3: включённый с тональной заливкой и квадратнее, выключенный прозрачный и круглый. */
    private void paintToggles() {
        paintToggle(shuffleButton, SharedConfig.shuffleMusic);
        paintToggle(repeatButton, SharedConfig.repeatMode != 0);
        paintToggle(romajiButton, romajiOn);
        paintToggle(translateButton, translateOn);
        paintSegment(lyricsSegment, mode == MODE_LYRICS, true);
        paintSegment(queueSegment, mode == MODE_QUEUE, false);
    }

    private void paintToggle(FemboyPlayerButton button, boolean on) {
        button.setColors(on ? scheme.container : Color.TRANSPARENT, on ? scheme.onContainer : scheme.gray);
        button.setShape(on ? 0.3f : 0.5f, 0.2f, true);
        button.setSelected(on);
    }

    private long durationMs(MessageObject message) {
        long ms = (long) (message.getDuration() * 1000);
        if (ms <= 0) ms = MediaController.getInstance().getDuration();
        return Math.max(0, ms);
    }

    private void updateProgress() {
        final MessageObject playing = current;
        if (playing == null) return;
        final long total = durationMs(playing);
        final long now = playing.audioProgressMs > 0 ? playing.audioProgressMs : (long) (playing.audioProgress * total);
        seek.setDuration(total);
        if (!seek.isDragging()) seek.setProgress(playing.audioProgress);
        if (mode == MODE_LYRICS) followLyrics(now);
    }

    // лайк

    private void updateLike(boolean bounce) {
        final boolean liked = current != null && FemboyMusic.isLiked(current);
        likeButton.setImageResource(liked ? R.drawable.femboy_ic_favorite_fill : R.drawable.femboy_ic_favorite);
        tintImage(likeButton, liked ? scheme.primary : textColor);
        likeButton.setSelected(liked);
        if (bounce) {
            likeButton.animate().cancel();
            likeButton.setScaleX(0.7f);
            likeButton.setScaleY(0.7f);
            likeButton.animate().scaleX(1f).scaleY(1f).setDuration(380)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(3f)).start();
        }
    }

    private void toggleLike() {
        final MessageObject message = current;
        if (message == null) return;
        final String key = currentKey;
        FemboyMusic.toggleLike(message, () -> {
            if (closing || !TextUtils.equals(key, currentKey)) return;
            final boolean liked = FemboyMusic.isLiked(message);
            updateLike(true);
            notice(liked ? R.drawable.femboy_ic_favorite_fill : R.drawable.femboy_ic_favorite,
                    LocaleController.getString(liked ? R.string.FemboyLiked : R.string.FemboyUnliked));
        });
    }

    /** Короткая плашка снизу в цветах MD3 snackbar. */
    private void notice(int iconRes, CharSequence text) {
        if (sheet == null || closing) return;
        Drawable icon = null;
        if (iconRes != 0) {
            final Drawable raw = ContextCompat.getDrawable(getContext(), iconRes);
            if (raw != null) {
                icon = raw.mutate();
                icon.setColorFilter(new PorterDuffColorFilter(scheme.inversePrimary, PorterDuff.Mode.SRC_IN));
            }
        }
        if (icon != null) {
            BulletinFactory.of(sheet, md3).createSimpleBulletin(icon, text).show();
        } else {
            BulletinFactory.of(sheet, md3).createSimpleBulletin(R.raw.info, text).show();
        }
    }

    // отправка в чат

    private static boolean noForwards(MessageObject message) {
        if (message.messageOwner != null && message.messageOwner.noforwards) return true;
        final long did = message.getDialogId();
        return did < 0 && MessagesController.getInstance(message.currentAccount).isChatNoForwards(-did);
    }

    private void sendToChat() {
        final MessageObject message = current;
        if (message == null) return;
        final boolean external = FemboyMusic.isExternal(message);
        final int account = UserConfig.selectedAccount;
        if (!external) {
            if (noForwards(message)) {
                notice(0, LocaleController.getString(R.string.FemboySendForbidden));
                return;
            }
            // Отправляем сам аудиофайл с подписью, а не пересылку: пересылка
            // восстановленного из плейлиста сообщения тихо не доходила.
            // переслать нельзя (чужой аккаунт или сообщения нет): отправим сам документ
            final TLRPC.Document document = message.getDocument();
            if (message.currentAccount == account && document instanceof TLRPC.TL_document) {
                final String caption = LocaleController.getString(R.string.FemboySentCaption);
                pickChat(did -> SendMessagesHelper.getInstance(account).sendMessage(SendMessagesHelper.SendMessageParams.of(
                        (TLRPC.TL_document) document, null, null, did, null, null, caption, null, null, null, true, 0, 0, 0, message, null, false)));
                return;
            }
        }
        final File file = external ? FemboyMusic.localFile(message) : downloadedFile(message);
        if (file == null || !file.exists()) {
            notice(0, LocaleController.getString(R.string.FemboySendNotDownloaded));
            return;
        }
        final String path = file.getAbsolutePath();
        final int dot = file.getName().lastIndexOf('.');
        final String ext = dot > 0 ? file.getName().substring(dot + 1) : "";
        final String mime = TextUtils.isEmpty(ext) ? null : MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase());
        final String caption = LocaleController.getString(R.string.FemboySentCaption);
        // файлы плеера лежат в личной папке приложения, а оттуда телеграм не
        // отправляет: FemboySend сначала копирует во внешний кэш
        final String fileName = niceFileName(message, file);
        pickChat(did -> org.telegram.femboy.FemboySend.privateFile(account, did, file, fileName, caption));
    }

    /** «Исполнитель - Название.расширение» без запрещённых в именах файлов знаков. */
    private static String niceFileName(MessageObject message, File file) {
        final int dot = file.getName().lastIndexOf('.');
        final String ext = dot > 0 ? file.getName().substring(dot + 1) : "";
        final String[] names = query(message);
        String name = (TextUtils.isEmpty(names[1]) ? "" : names[1] + " - ") + (TextUtils.isEmpty(names[0]) ? "track" : names[0]);
        name = name.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (name.length() > 120) name = name.substring(0, 120);
        return name + (TextUtils.isEmpty(ext) ? "" : "." + ext);
    }

    /** Файл трека, где бы он ни лежал: скачанный из чата, свой, из сети. */
    private static File anyFile(MessageObject message) {
        final File own = FemboyMusic.localFile(message);
        if (own != null && own.exists()) return own;
        return FemboyMusic.isExternal(message) ? null : downloadedFile(message);
    }

    // то, что умеет обычный плеер телеграма

    private void addToProfile(MessageObject message) {
        final TLRPC.Document document = message.getDocument();
        if (document == null || document.id == 0 || FemboyMusic.isExternal(message)) {
            uploadToProfile(message);
            return;
        }
        final TLRPC.TL_account_saveMusic req = new TLRPC.TL_account_saveMusic();
        req.id = new TLRPC.TL_inputDocument();
        req.id.id = document.id;
        req.id.access_hash = document.access_hash;
        req.id.file_reference = document.file_reference != null ? document.file_reference : new byte[0];
        org.telegram.tgnet.ConnectionsManager.getInstance(message.currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            if (closing) return;
            if (err == null) notice(R.drawable.femboy_ic_album, LocaleController.getString(R.string.AudioSaveToMyProfileSaved));
            else notice(0, err.text);
        }));
    }

    /**
     * Трек не из телеграма (YouTube, SoundCloud): сначала загружаем файл в
     * телеграм, как это делает его плеер, потом кладём в профиль.
     */
    private void uploadToProfile(MessageObject message) {
        final File file = anyFile(message);
        if (file == null) {
            notice(0, LocaleController.getString(R.string.FemboySendNotDownloaded));
            return;
        }
        final int account = UserConfig.selectedAccount;
        final String[] names = query(message);
        final String fileName = niceFileName(message, file);
        notice(R.drawable.femboy_ic_album, LocaleController.getString(R.string.FemboyUploadingToProfile));
        org.telegram.messenger.Utilities.globalQueue.postRunnable(() -> {
            final File upload = org.telegram.femboy.FemboySend.publicCopy(file, fileName);
            if (upload == null) return;
            AndroidUtilities.runOnUIThread(() -> org.telegram.messenger.FileLoader.getInstance(account).uploadFile(upload.getAbsolutePath(), input -> {
                if (input == null) {
                    AndroidUtilities.runOnUIThread(() -> { if (!closing) notice(0, LocaleController.getString(R.string.FemboyUploadFailed)); });
                    return;
                }
                final TLRPC.TL_messages_uploadMedia req = new TLRPC.TL_messages_uploadMedia();
                req.peer = org.telegram.messenger.MessagesController.getInstance(account).getInputPeer(UserConfig.getInstance(account).getClientUserId());
                final TLRPC.TL_inputMediaUploadedDocument media = new TLRPC.TL_inputMediaUploadedDocument();
                media.file = input;
                media.mime_type = upload.getName().toLowerCase().endsWith(".mp3") ? "audio/mpeg" : "audio/mp4";
                final TLRPC.TL_documentAttributeAudio audio = new TLRPC.TL_documentAttributeAudio();
                audio.title = names[0];
                audio.performer = names[1];
                audio.duration = message.getDuration();
                audio.flags |= 1 | 2;
                media.attributes.add(audio);
                final TLRPC.TL_documentAttributeFilename name = new TLRPC.TL_documentAttributeFilename();
                name.file_name = upload.getName();
                media.attributes.add(name);
                req.media = media;
                org.telegram.tgnet.ConnectionsManager.getInstance(account).sendRequest(req, (res, err) -> {
                    if (!(res instanceof TLRPC.TL_messageMediaDocument) || ((TLRPC.TL_messageMediaDocument) res).document == null) {
                        AndroidUtilities.runOnUIThread(() -> { if (!closing) notice(0, err != null ? err.text : LocaleController.getString(R.string.FemboyUploadFailed)); });
                        return;
                    }
                    final TLRPC.Document doc = ((TLRPC.TL_messageMediaDocument) res).document;
                    final TLRPC.TL_account_saveMusic save = new TLRPC.TL_account_saveMusic();
                    save.id = new TLRPC.TL_inputDocument();
                    save.id.id = doc.id;
                    save.id.access_hash = doc.access_hash;
                    save.id.file_reference = doc.file_reference != null ? doc.file_reference : new byte[0];
                    org.telegram.tgnet.ConnectionsManager.getInstance(account).sendRequest(save, (r2, e2) -> AndroidUtilities.runOnUIThread(() -> {
                        if (closing) return;
                        if (e2 == null) notice(R.drawable.femboy_ic_album, LocaleController.getString(R.string.AudioSaveToMyProfileSaved));
                        else notice(0, e2.text);
                    }));
                });
            }));
        });
    }

    private void saveToSaved(MessageObject message) {
        final int account = UserConfig.selectedAccount;
        final long self = UserConfig.getInstance(account).getClientUserId();
        final TLRPC.Document document = message.getDocument();
        final String caption = LocaleController.getString(R.string.FemboySentCaption);
        if (!FemboyMusic.isExternal(message) && message.currentAccount == account && document instanceof TLRPC.TL_document) {
            SendMessagesHelper.getInstance(account).sendMessage(SendMessagesHelper.SendMessageParams.of(
                    (TLRPC.TL_document) document, null, null, self, null, null, caption, null, null, null, true, 0, 0, 0, message, null, false));
        } else {
            final File file = anyFile(message);
            if (file == null) {
                notice(0, LocaleController.getString(R.string.FemboySendNotDownloaded));
                return;
            }
            org.telegram.femboy.FemboySend.privateFile(account, self, file, niceFileName(message, file), caption);
        }
        notice(R.drawable.femboy_ic_send, LocaleController.getString(R.string.AudioSaveToSavedMessagesSaved));
    }

    private void saveToMusicFolder(MessageObject message) {
        final File file = anyFile(message);
        if (file == null) {
            notice(0, LocaleController.getString(R.string.FemboySendNotDownloaded));
            return;
        }
        final String mime = message.getDocument() != null ? message.getDocument().mime_type : "audio/*";
        MediaController.saveFile(file.getAbsolutePath(), activity, 3, niceFileName(message, file), mime,
                uri -> notice(R.drawable.femboy_ic_album, LocaleController.getString(R.string.AudioSaveToMusicFolder)));
    }

    private void shareFile(MessageObject message) {
        final File file = anyFile(message);
        if (file == null) {
            notice(0, LocaleController.getString(R.string.FemboySendNotDownloaded));
            return;
        }
        final String name = niceFileName(message, file);
        org.telegram.messenger.Utilities.globalQueue.postRunnable(() -> {
            final File copy = org.telegram.femboy.FemboySend.publicCopy(file, name);
            if (copy == null) return;
            AndroidUtilities.runOnUIThread(() -> {
                try {
                    final android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_SEND);
                    intent.setType(message.getMimeType() != null ? message.getMimeType() : "audio/*");
                    intent.putExtra(android.content.Intent.EXTRA_STREAM, androidx.core.content.FileProvider.getUriForFile(
                            activity, org.telegram.messenger.ApplicationLoader.getApplicationId() + ".provider", copy));
                    intent.setFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    activity.startActivity(android.content.Intent.createChooser(intent, LocaleController.getString(R.string.ShareFile)));
                } catch (Throwable error) {
                    android.util.Log.e("FemboyPlayer", "share", error);
                }
            });
        });
    }

    private org.telegram.ui.Components.CastMediaRouteButton castButton;

    private void cast() {
        try {
            if (castButton == null) {
                castButton = new org.telegram.ui.Components.CastMediaRouteButton(getContext()) {
                    @Override
                    public void stateUpdated(boolean connected) {
                    }
                };
                castButton.setRouteSelector(com.google.android.gms.cast.framework.CastContext.getSharedInstance(getContext()).getMergedSelector());
                castButton.setVisibility(View.INVISIBLE);
                sheet.addView(castButton, LayoutHelper.createFrame(1, 1));
            }
            org.telegram.messenger.chromecast.ChromecastController.getInstance().setCurrentMediaAndCastIfNeeded(MediaController.getInstance().getCurrentChromecastMedia());
            castButton.performClick();
        } catch (Throwable error) {
            android.util.Log.e("FemboyPlayer", "cast", error);
        }
    }

    private void forwardMessage(MessageObject message) {
        final int account = UserConfig.selectedAccount;
        final ArrayList<MessageObject> list = new ArrayList<>();
        list.add(message);
        pickChat(did -> SendMessagesHelper.getInstance(account).sendMessage(list, did, false, false, true, 0, 0));
    }

    private static File downloadedFile(MessageObject message) {
        if (message.messageOwner == null) return null;
        if (!TextUtils.isEmpty(message.messageOwner.attachPath)) {
            final File own = new File(message.messageOwner.attachPath);
            if (own.exists()) return own;
        }
        final File cached = FileLoader.getInstance(message.currentAccount).getPathToMessage(message.messageOwner);
        return cached != null && cached.exists() ? cached : null;
    }

    /**
     * Стандартный выбор чата телеграма. Он живёт под плеером, поэтому плеер
     * на это время закрываем, а когда выбор закрылся, открываем снова.
     */
    private void pickChat(Utilities.Callback<Long> send) {
        final BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        if (fragment == null) return;
        final Bundle args = new Bundle();
        args.putBoolean("onlySelect", true);
        args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_FORWARD);
        args.putInt("messagesCount", 1);
        final boolean[] sent = {false};
        final DialogsActivity picker = new DialogsActivity(args) {
            @Override
            public void onFragmentDestroy() {
                super.onFragmentDestroy();
                if (sent[0]) {
                    pendingNotice = LocaleController.getString(R.string.FemboySent);
                    pendingNoticeIcon = R.drawable.femboy_ic_send;
                }
                AndroidUtilities.runOnUIThread(() -> {
                    if (LaunchActivity.instance != null) FemboyPlayer.open(LaunchActivity.instance);
                }, 250);
            }
        };
        picker.setDelegate((dialogs, dids, message, param, notify, scheduleDate, scheduleRepeatPeriod, topicsFragment) -> {
            for (MessagesStorage.TopicKey key : dids) {
                try {
                    send.run(key.dialogId);
                } catch (Throwable error) {
                    org.telegram.messenger.FileLog.e(error);
                }
            }
            sent[0] = !dids.isEmpty();
            dialogs.finishFragment();
            return true;
        });
        dismiss();
        fragment.presentFragment(picker);
    }

    // обложка и цвет

    private void updateCover(MessageObject message) {
        final AudioInfo info = MediaController.getInstance().getAudioInfo();
        if (info != null && info.getCover() != null) {
            coverImage.setImageBitmap(info.getCover());
            coverFile = null;
            coverFromTags = true;
            onCoverBitmap(info.getCover());
            return;
        }
        coverFromTags = false;
        final TLRPC.Document document = message.getDocument();
        coverFile = document != null ? FileLoader.getAttachFileName(document) : null;
        // у трека не из чата обложка своя, по адресу
        final String external = FemboyMusic.isExternal(message) ? FemboyMusic.coverUrl(message) : null;
        final String artwork = !TextUtils.isEmpty(external) ? external : message.getArtworkUrl(false);
        TLRPC.PhotoSize thumb = document != null ? FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 360) : null;
        if (!(thumb instanceof TLRPC.TL_photoSize) && !(thumb instanceof TLRPC.TL_photoSizeProgressive)) thumb = null;
        ImageLocation thumbLocation = thumb != null ? ImageLocation.getForDocument(thumb, document) : null;
        if (thumbLocation == null && TextUtils.isEmpty(external)) {
            final String small = message.getArtworkUrl(true);
            if (small != null) thumbLocation = ImageLocation.getForPath(small);
        }
        if (!TextUtils.isEmpty(artwork)) {
            coverImage.setImage(ImageLocation.getForPath(artwork), null, thumbLocation, null, null, 0, 1, message);
        } else if (thumbLocation != null) {
            coverImage.setImage(thumbLocation, null, (ImageLocation) null, null, null, 0, 1, message);
        } else {
            coverImage.setImageDrawable(null);
            onCoverBitmap(null);
        }
    }

    /** Обложка готова: из неё цвета и подсветка. Маленькую копию делаем сразу, большую не держим. */
    private void onCoverBitmap(Bitmap bitmap) {
        Bitmap small = null;
        if (bitmap != null && !bitmap.isRecycled()) {
            try {
                small = Bitmap.createScaledBitmap(bitmap, 64, 64, true);
            } catch (Throwable ignored) {
            }
        }
        if (ambient != null) ambient.setSource(small);
        if (small == null) {
            animateScheme(schemeFor(accentColor, baseColor));
            return;
        }
        final Bitmap forPalette = small;
        final String forKey = currentKey;
        Utilities.globalQueue.postRunnable(() -> {
            int background, seed;
            try {
                final Palette palette = Palette.from(forPalette).maximumColorCount(16).generate();
                final int fallback = palette.getDominantColor(baseColor);
                background = dark ? palette.getDarkVibrantColor(palette.getVibrantColor(fallback))
                        : palette.getLightVibrantColor(palette.getVibrantColor(fallback));
                seed = palette.getVibrantColor(palette.getLightVibrantColor(palette.getDarkVibrantColor(fallback)));
            } catch (Throwable error) {
                background = baseColor;
                seed = accentColor;
            }
            final int bg = background, s = seed;
            AndroidUtilities.runOnUIThread(() -> {
                if (TextUtils.equals(forKey, currentKey) && !closing) animateScheme(schemeFor(s, soften(bg)));
            });
        });
    }

    /** Цвет обложки, приглушённый под тему: текст поверх должен читаться. */
    private int soften(int color) {
        final float[] hsl = new float[3];
        ColorUtils.colorToHSL(color, hsl);
        if (dark) {
            hsl[2] = Math.min(hsl[2], 0.32f);
            hsl[1] = Math.min(hsl[1], 0.65f);
        } else {
            hsl[2] = Math.max(hsl[2], 0.78f);
            hsl[1] = Math.min(hsl[1], 0.6f);
        }
        return ColorUtils.blendARGB(baseColor, ColorUtils.HSLToColor(hsl), dark ? 0.85f : 0.75f);
    }

    private static int hsl(float h, float s, float l) {
        return ColorUtils.HSLToColor(new float[]{h, Math.max(0, Math.min(1, s)), Math.max(0, Math.min(1, l))});
    }

    /** Двигаем светлоту, пока цвет не станет читаться на фоне. */
    private static int contrast(int color, int background, double ratio, boolean lighter) {
        final float[] v = new float[3];
        ColorUtils.colorToHSL(color, v);
        int out = color;
        for (int i = 0; i < 25 && ColorUtils.calculateContrast(out, background) < ratio; i++) {
            v[2] = Math.max(0, Math.min(1, v[2] + (lighter ? 0.04f : -0.04f)));
            out = ColorUtils.HSLToColor(v);
        }
        return out;
    }

    /** Роли MD3 из цвета обложки: тон 80/20 и 30/90 в тёмной теме, 40/100 и 90/10 в светлой. */
    private Scheme schemeFor(int seed, int bg) {
        final float[] v = new float[3];
        ColorUtils.colorToHSL(seed, v);
        final float h = v[0], sat = Math.min(v[1], 0.8f);
        final Scheme s = new Scheme();
        s.bg = bg;
        // кнопки внизу, а фон там уже ближе к теме
        final int under = ColorUtils.blendARGB(bg, baseColor, 0.6f);
        if (dark) {
            s.primary = contrast(hsl(h, sat, 0.8f), under, 3.0, true);
            s.onPrimary = contrast(hsl(h, sat * 0.9f, 0.18f), s.primary, 4.5, false);
            s.container = hsl(h, sat * 0.55f, 0.3f);
            s.onContainer = contrast(hsl(h, sat * 0.5f, 0.9f), s.container, 4.5, true);
            s.menu = hsl(h, sat * 0.3f, 0.16f);
            s.inverse = hsl(h, sat * 0.2f, 0.9f);
            s.onInverse = hsl(h, sat * 0.2f, 0.2f);
            s.inversePrimary = contrast(hsl(h, sat, 0.4f), s.inverse, 3.0, false);
        } else {
            s.primary = contrast(hsl(h, sat, 0.4f), under, 3.0, false);
            s.onPrimary = contrast(Color.WHITE, s.primary, 4.5, false);
            s.container = hsl(h, sat * 0.6f, 0.86f);
            s.onContainer = contrast(hsl(h, sat * 0.6f, 0.14f), s.container, 4.5, false);
            s.menu = hsl(h, sat * 0.3f, 0.95f);
            s.inverse = hsl(h, sat * 0.2f, 0.2f);
            s.onInverse = hsl(h, sat * 0.15f, 0.95f);
            s.inversePrimary = contrast(hsl(h, sat, 0.8f), s.inverse, 3.0, true);
        }
        s.track = ColorUtils.compositeColors(Theme.multAlpha(s.primary, dark ? 0.26f : 0.22f), under);
        s.gray = contrast(grayColor, under, 4.0, dark);
        return s;
    }

    private void animateScheme(Scheme to) {
        if (schemeAnimator != null) schemeAnimator.cancel();
        final Scheme from = scheme;
        schemeAnimator = ValueAnimator.ofFloat(0, 1);
        schemeAnimator.setDuration(450);
        schemeAnimator.addUpdateListener(a -> applyScheme(from.blend(to, (float) a.getAnimatedValue())));
        schemeAnimator.start();
    }

    private void applyScheme(Scheme s) {
        scheme = s;
        tint = s.bg;
        sheet.invalidate();
        seek.setColors(s.primary, s.track, s.gray);
        playButton.setColors(s.primary, s.onPrimary);
        prevButton.setColors(s.container, s.onContainer);
        nextButton.setColors(s.container, s.onContainer);
        paintToggles();
        artistView.setTextColor(s.gray);
        captionView.setTextColor(s.gray);
        lyricsInfo.setTextColor(s.gray);
        if (likeButton.isSelected()) tintImage(likeButton, s.primary);
        final Window window = getWindow();
        if (window != null) {
            AndroidUtilities.setLightStatusBar(window, AndroidUtilities.computePerceivedBrightness(s.bg) > 0.6f);
            AndroidUtilities.setLightNavigationBar(this, AndroidUtilities.computePerceivedBrightness(baseColor) > 0.6f);
        }
    }

    // режимы: обложка, текст, очередь

    private void setMode(int to) {
        if (mode == to) return;
        mode = to;
        crossfade(coverHolder, to == MODE_COVER);
        crossfade(lyricsPage, to == MODE_LYRICS);
        crossfade(queuePage, to == MODE_QUEUE);
        if (ambient != null) ambient.setModeFactor(to == MODE_COVER ? 1f : 0.4f);
        updateModes();
        if (to == MODE_LYRICS) {
            loadLyrics();
        } else if (to == MODE_QUEUE) {
            fillQueue();
            scrollQueueToCurrent();
        }
    }

    private static void crossfade(View view, boolean show) {
        view.animate().cancel();
        if (show) {
            view.setVisibility(View.VISIBLE);
            view.setAlpha(0f);
            view.animate().alpha(1f).setDuration(200).setListener(null).start();
        } else if (view.getVisibility() == View.VISIBLE) {
            view.animate().alpha(0f).setDuration(150).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    view.setVisibility(View.GONE);
                }
            }).start();
        }
    }

    private void setAmbient(boolean on) {
        ambientOn = on;
        FemboyConfig.prefs().edit().putBoolean(PREF_AMBIENT, on).apply();
        ambient.animate().cancel();
        if (on) {
            ambient.setVisibility(View.VISIBLE);
        } else {
            ambient.setVisibility(View.GONE);
        }
        ambient.setBreathing(on && !paused);
        coverHolder.setElevation(dp(on ? 4 : 10));
    }

    // текст песни

    /** Название и исполнитель для поиска: из тегов, а если их нет, из имени файла «Исполнитель - Песня». */
    private static String[] query(MessageObject message) {
        String title = null, artist = null;
        final TLRPC.Document document = message.getDocument();
        if (document != null) {
            for (TLRPC.DocumentAttribute attribute : document.attributes) {
                if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                    title = attribute.title;
                    artist = attribute.performer;
                }
            }
            if (TextUtils.isEmpty(title)) {
                String name = FileLoader.getDocumentFileName(document);
                if (name != null) {
                    final int dot = name.lastIndexOf('.');
                    if (dot > 0) name = name.substring(0, dot);
                    final int dash = name.indexOf(" - ");
                    if (dash > 0 && TextUtils.isEmpty(artist)) {
                        artist = name.substring(0, dash).trim();
                        name = name.substring(dash + 3);
                    }
                    title = name.trim();
                }
            }
        }
        // у треков не из чата документа может не быть: берём то, что показываем
        if (TextUtils.isEmpty(title)) title = message.getMusicTitle(false);
        if (TextUtils.isEmpty(artist)) artist = message.getMusicAuthor(false);
        if (TextUtils.isEmpty(artist)) {
            final AudioInfo info = MediaController.getInstance().getAudioInfo();
            if (info != null && !TextUtils.isEmpty(info.getArtist())) artist = info.getArtist();
        }
        return new String[]{title, artist};
    }

    private void loadLyrics() {
        final MessageObject message = current;
        if (message == null) return;
        final String key = currentKey;
        if (TextUtils.equals(key, lyricsKey)) return;
        lyricsKey = key;
        lyrics = null;
        lyricIndex = -1;
        lyricRows.clear();
        lyricsBox.removeAllViews();
        lyricsScroll.scrollTo(0, 0);
        lyricsInfo.setVisibility(View.GONE);
        romajiButton.setVisibility(View.GONE);
        translateButton.setVisibility(View.GONE);
        setTranslatePulse(false);
        lyricsStatus.setVisibility(View.VISIBLE);
        lyricsStatus.setText(LocaleController.getString(R.string.FemboyLyricsLoading));
        final String[] q = query(message);
        FemboyLyrics.find(q[0], q[1], (int) message.getDuration(), found -> {
            if (!TextUtils.equals(key, lyricsKey) || closing) return;
            if (found == null) {
                lyricsStatus.setText(LocaleController.getString(R.string.FemboyLyricsNone));
                // следующее открытие текста спросит ещё раз: сеть могла просто не ответить
                lyricsKey = key + "_none";
                return;
            }
            showLyrics(found);
        });
    }

    private static boolean isSectionLabel(String line) {
        return line.length() > 2 && line.startsWith("[") && line.endsWith("]");
    }

    private Row lyricRow(Context context, String text, boolean synced) {
        final Row row = new Row();
        final LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        row.root = box;
        row.ruby = new TextView(context);
        row.ruby.setTextSize(TypedValue.COMPLEX_UNIT_DIP, synced ? 14 : 13);
        row.ruby.setTypeface(AndroidUtilities.bold());
        row.ruby.setLetterSpacing(0.02f);
        row.ruby.setTextColor(Theme.multAlpha(textColor, 0.7f));
        row.ruby.setVisibility(View.GONE);
        box.addView(row.ruby, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 1));
        row.main = new TextView(context);
        row.main.setText(text);
        row.main.setTextColor(textColor);
        if (synced) {
            // headlineSmall: строка, которую поют, крупно
            row.main.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24);
            row.main.setTypeface(AndroidUtilities.bold());
            row.main.setLineSpacing(dp(2), 1f);
            box.setPadding(0, dp(8), 0, dp(8));
        } else {
            // titleLarge и воздух между строками: читается, а не сплошная стена
            row.main.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
            row.main.setTypeface(AndroidUtilities.bold());
            row.main.setLineSpacing(dp(3), 1f);
            box.setPadding(0, dp(4), 0, dp(4));
        }
        box.addView(row.main, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        // перевод: мельче и тусклее строки, как чтение сверху, но не жирный
        row.tr = new TextView(context);
        row.tr.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        row.tr.setTextColor(Theme.multAlpha(textColor, 0.7f));
        row.tr.setLineSpacing(dp(1), 1f);
        row.tr.setVisibility(View.GONE);
        box.addView(row.tr, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
        return row;
    }

    private void showLyrics(FemboyLyrics.Lyrics found) {
        lyrics = found;
        lyricsStatus.setVisibility(View.GONE);
        lyricsBox.removeAllViews();
        lyricRows.clear();
        lyricIndex = -1;
        final Context context = getContext();
        final boolean synced = found.synced();
        final List<String> texts = found.textLines();
        if (synced) {
            for (int i = 0; i < found.lines.size(); i++) {
                final FemboyLyrics.Line line = found.lines.get(i);
                final Row row = lyricRow(context, TextUtils.isEmpty(line.text) ? "♪" : line.text, true);
                row.root.setAlpha(0.4f);
                row.root.setOnClickListener(v -> {
                    final MessageObject playing = MediaController.getInstance().getPlayingMessageObject();
                    if (playing == null) return;
                    lastLyricsTouch = 0;
                    MediaController.getInstance().seekToProgressMs(playing, line.time);
                    if (MediaController.getInstance().isMessagePaused()) MediaController.getInstance().playMessage(playing);
                });
                lyricsBox.addView(row.root, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                lyricRows.add(row);
            }
        } else if (texts.isEmpty()) {
            final Row row = lyricRow(context, "♪", false);
            lyricsBox.addView(row.root, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        } else {
            for (String raw : texts) {
                final String line = raw.trim();
                final Row row;
                if (line.isEmpty()) {
                    // пустая строка: граница куплета
                    row = new Row();
                    row.root = new View(context);
                    lyricsBox.addView(row.root, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 18));
                } else if (isSectionLabel(line)) {
                    // [Припев] и подобное: метка labelLarge, а не строка песни
                    row = new Row();
                    final TextView label = new TextView(context);
                    label.setText(line.substring(1, line.length() - 1));
                    label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
                    label.setTypeface(AndroidUtilities.bold());
                    label.setLetterSpacing(0.06f);
                    label.setAllCaps(true);
                    label.setTextColor(scheme.primary);
                    label.setPadding(0, dp(6), 0, dp(4));
                    row.root = label;
                    lyricsBox.addView(label, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                } else {
                    row = lyricRow(context, line, false);
                    lyricsBox.addView(row.root, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                }
                lyricRows.add(row);
            }
        }
        final TextView credit = new TextView(context);
        credit.setText(LocaleController.formatString(R.string.FemboyLyricsFrom, found.source));
        credit.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        credit.setTextColor(scheme.gray);
        credit.setPadding(0, dp(24), 0, 0);
        lyricsBox.addView(credit, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // под заголовком: откуда текст и что он не синхронный
        final String info = synced ? found.source
                : found.source + " · " + LocaleController.getString(R.string.FemboyLyricsNotSynced);
        lyricsInfo.setText(info);
        lyricsInfo.setVisibility(View.VISIBLE);

        final boolean needs = FemboyLyrics.needsRomaji(found);
        romajiButton.setVisibility(needs ? View.VISIBLE : View.GONE);
        if (needs) {
            if (found.romaji != null) {
                applyRomaji();
            } else {
                FemboyLyrics.romanize(found, () -> {
                    if (lyrics == found && !closing) applyRomaji();
                });
            }
        }
        FemboyLyrics.detectLanguage(found, lang -> {
            if (lyrics != found || closing) return;
            final boolean other = lang != null && !lang.equals(appLanguage());
            translateButton.setVisibility(other ? View.VISIBLE : View.GONE);
            if (other && translateOn) startTranslation();
            applyTranslation();
        });
        updateProgress();
    }

    private static String appLanguage() {
        return FemboyLyrics.baseLanguage(LocaleController.getInstance().getCurrentLocale().getLanguage());
    }

    /** Перевести текст на язык приложения, если перевода ещё нет; пока ждём, кнопка пульсирует. */
    private void startTranslation() {
        final FemboyLyrics.Lyrics found = lyrics;
        if (found == null || translating == found || translateButton.getVisibility() != View.VISIBLE) return;
        final String lang = appLanguage();
        if (lang == null || found.translations.containsKey(lang)) return;
        translating = found;
        setTranslatePulse(true);
        final int account = current != null ? current.currentAccount : UserConfig.selectedAccount;
        FemboyLyrics.translate(found, lang, account, (lines, error) -> {
            if (translating == found) translating = null;
            if (lyrics != found || closing) return;
            setTranslatePulse(false);
            if (lines == null) {
                translateOn = false;
                FemboyConfig.prefs().edit().putBoolean(PREF_TRANSLATE, false).apply();
                paintToggles();
                final String text = TextUtils.isEmpty(error)
                        ? LocaleController.getString(R.string.TranslationFailedAlert2)
                        : LocaleController.formatString(R.string.FemboyLyricsTranslateFailed, error);
                BulletinFactory.of(sheet, md3).createErrorBulletin(text).show();
            }
            applyTranslation();
        });
    }

    private void setTranslatePulse(boolean on) {
        if (translatePulse != null) {
            translatePulse.cancel();
            translatePulse = null;
        }
        if (translateButton == null) return;
        if (!on) {
            translateButton.icon.setAlpha(1f);
            return;
        }
        translatePulse = ValueAnimator.ofFloat(1f, 0.35f);
        translatePulse.setDuration(600);
        translatePulse.setRepeatMode(ValueAnimator.REVERSE);
        translatePulse.setRepeatCount(ValueAnimator.INFINITE);
        translatePulse.addUpdateListener(a -> translateButton.icon.setAlpha((float) a.getAnimatedValue()));
        translatePulse.start();
    }

    /** Показать или спрятать перевод под строками. */
    private void applyTranslation() {
        final FemboyLyrics.Lyrics found = lyrics;
        if (found == null) return;
        final ArrayList<String> tr = translateOn && translateButton.getVisibility() == View.VISIBLE
                ? found.translations.get(appLanguage()) : null;
        boolean changed = false;
        for (int i = 0; i < lyricRows.size(); i++) {
            final Row row = lyricRows.get(i);
            if (row.tr == null) continue;
            final String text = tr != null && i < tr.size() ? tr.get(i) : null;
            final boolean show = !TextUtils.isEmpty(text);
            if (show) row.tr.setText(text);
            if (show != (row.tr.getVisibility() == View.VISIBLE)) {
                row.tr.setVisibility(show ? View.VISIBLE : View.GONE);
                changed = true;
            }
        }
        if (changed) lyricsScroll.post(this::keepCurrentLine);
    }

    /** Строки выросли или сжались: текущую строку синхронного текста оставляем на её месте. */
    private void keepCurrentLine() {
        if (closing || lyrics == null || !lyrics.synced() || lyricIndex < 0 || lyricIndex >= lyricRows.size()) return;
        if (SystemClock.elapsedRealtime() - lastLyricsTouch <= 3000) return;
        final View line = lyricRows.get(lyricIndex).root;
        lyricsScroll.scrollTo(0, Math.max(0, line.getTop() - lyricsScroll.getHeight() / 3));
    }

    /** Показать или спрятать чтение латиницей над строками. */
    private void applyRomaji() {
        final FemboyLyrics.Lyrics found = lyrics;
        if (found == null) return;
        final ArrayList<String> romaji = found.romaji;
        for (int i = 0; i < lyricRows.size(); i++) {
            final Row row = lyricRows.get(i);
            if (row.ruby == null) continue;
            final String text = romaji != null && i < romaji.size() ? romaji.get(i) : null;
            if (romajiOn && !TextUtils.isEmpty(text)) {
                row.ruby.setText(text);
                row.ruby.setVisibility(View.VISIBLE);
            } else {
                row.ruby.setVisibility(View.GONE);
            }
        }
    }

    private void followLyrics(long nowMs) {
        if (lyrics == null || !lyrics.synced() || lyricRows.isEmpty()) return;
        // строка подсвечивается чуть раньше, как в стриминге: глазу так спокойнее
        final long at = nowMs + 250;
        int lo = 0, hi = lyrics.lines.size() - 1, found = -1;
        while (lo <= hi) {
            final int mid = (lo + hi) >>> 1;
            if (lyrics.lines.get(mid).time <= at) {
                found = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        if (found == lyricIndex) return;
        if (lyricIndex >= 0 && lyricIndex < lyricRows.size()) {
            lyricRows.get(lyricIndex).root.animate().alpha(0.4f).setDuration(250).start();
        }
        lyricIndex = found;
        if (found < 0 || found >= lyricRows.size()) return;
        final View line = lyricRows.get(found).root;
        line.animate().alpha(1f).setDuration(250).start();
        // человек листает сам: не дёргаем его пару секунд
        if (SystemClock.elapsedRealtime() - lastLyricsTouch > 3000) {
            final int target = Math.max(0, line.getTop() - lyricsScroll.getHeight() / 3);
            lyricsScroll.smoothScrollTo(0, target);
        }
    }

    // очередь

    private void fillQueue() {
        if (queueAdapter == null) return;
        final ArrayList<MessageObject> playlist = MediaController.getInstance().getPlaylist();
        final ArrayList<MessageObject> order = new ArrayList<>(playlist.size());
        // «дальше» у телеграма идёт к меньшему индексу, если порядок не обратный
        if (SharedConfig.playOrderReversed) {
            order.addAll(playlist);
        } else {
            for (int i = playlist.size() - 1; i >= 0; i--) order.add(playlist.get(i));
        }
        queueAdapter.items.clear();
        queueAdapter.items.addAll(order);
        queueAdapter.notifyDataSetChanged();
    }

    private void scrollQueueToCurrent() {
        for (int i = 0; i < queueAdapter.items.size(); i++) {
            if (same(queueAdapter.items.get(i), current)) {
                final int index = i;
                queueList.post(() -> ((LinearLayoutManager) queueList.getLayoutManager()).scrollToPositionWithOffset(index, dp(8)));
                return;
            }
        }
    }

    private class QueueAdapter extends RecyclerListView.SelectionAdapter {
        final ArrayList<MessageObject> items = new ArrayList<>();

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            final FemboyTrackCell cell = new FemboyTrackCell(parent.getContext(), textColor, grayColor);
            cell.setMoreVisible(false);
            cell.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            final MessageObject message = items.get(position);
            ((FemboyTrackCell) holder.itemView).set(message, same(message, current));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    // меню

    private void showMenu(View anchor) {
        final MessageObject message = current;
        if (message == null) return;
        final boolean external = FemboyMusic.isExternal(message);
        final ItemOptions options = ItemOptions.makeOptions(container, md3, anchor);
        // «В плейлист» и «Отправить в чат» уже есть кнопками в самом плеере
        // то, что есть в обычном плеере телеграма
        final boolean fromChat = !external && message.getId() > 0 && message.currentAccount == UserConfig.selectedAccount;
        options.addIf(fromChat && !noForwards(message), R.drawable.msg_forward, LocaleController.getString(R.string.Forward), () -> forwardMessage(message));
        options.add(R.drawable.msg_openprofile, LocaleController.getString(R.string.AudioAddToProfile), () -> addToProfile(message));
        options.addIf(!noForwards(message), R.drawable.msg_saved, LocaleController.getString(R.string.AudioSaveToSavedMessages), () -> saveToSaved(message));
        options.addIf(!noForwards(message), R.drawable.msg_download, LocaleController.getString(R.string.AudioSaveToMusicFolder), () -> saveToMusicFolder(message));
        options.addIf(!noForwards(message), R.drawable.msg_shareout, LocaleController.getString(R.string.ShareFile), () -> shareFile(message));
        options.add(R.drawable.menu_video_chromecast, LocaleController.getString(R.string.VideoPlayerChromecast), this::cast);
        options.addIf(!external && message.getDialogId() != 0 && message.currentAccount == UserConfig.selectedAccount && message.getId() > 0, R.drawable.msg_message,
                LocaleController.getString(R.string.FemboyOpenInChat), () -> openInChat(message));
        options.addChecked(ambientOn, R.drawable.femboy_ic_blur_on, LocaleController.getString(R.string.FemboyAmbientLight), () -> setAmbient(!ambientOn));
        options.add(R.drawable.femboy_ic_library_music, LocaleController.getString(R.string.FemboyPlaylists), () -> {
            final BaseFragment fragment = LaunchActivity.getSafeLastFragment();
            if (fragment == null) return;
            dismiss();
            fragment.presentFragment(new FemboyMusicActivity());
        });
        options.setGravity(Gravity.RIGHT);
        options.show();
    }

    private void openInChat(MessageObject message) {
        final BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        if (fragment == null) return;
        final Bundle args = new Bundle();
        final long did = message.getDialogId();
        if (did > 0) args.putLong("user_id", did);
        else args.putLong("chat_id", -did);
        args.putInt("message_id", message.getId());
        dismiss();
        fragment.presentFragment(new ChatActivity(args));
    }

    // события

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (closing) return;
        if (id == NotificationCenter.messagePlayingProgressDidChanged) {
            final MessageObject playing = MediaController.getInstance().getPlayingMessageObject();
            if (playing != null && playing.isMusic()) {
                if (!same(playing, current)) {
                    update(false);
                    return;
                }
                current = playing;
                updateProgress();
                // теги с обложкой появляются, когда файл уже открыт
                if (!coverFromTags) {
                    final AudioInfo info = MediaController.getInstance().getAudioInfo();
                    if (info != null && info.getCover() != null) updateCover(playing);
                }
            }
        } else if (id == NotificationCenter.messagePlayingDidStart || id == NotificationCenter.messagePlayingPlayStateChanged
                || id == NotificationCenter.messagePlayingDidReset) {
            update(false);
            if (mode == MODE_QUEUE) queueAdapter.notifyDataSetChanged();
        } else if (id == NotificationCenter.musicDidLoad || id == NotificationCenter.moreMusicDidLoad) {
            fillQueue();
        } else if (id == NotificationCenter.fileLoaded) {
            if (coverFile != null && args.length > 0 && coverFile.equals(args[0]) && current != null) {
                updateCover(current);
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (mode != MODE_COVER) {
            setMode(MODE_COVER);
            return;
        }
        dismiss();
    }

    @Override
    public void dismiss() {
        if (closing) return;
        closing = true;
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            for (int event : EVENTS) NotificationCenter.getInstance(a).removeObserver(this, event);
        }
        if (schemeAnimator != null) schemeAnimator.cancel();
        setTranslatePulse(false);
        if (ambient != null) ambient.setBreathing(false);
        if (shown == this) shown = null;
        if (sheet == null || container == null || container.getHeight() == 0) {
            finish();
            return;
        }
        sheet.animate().cancel();
        sheet.animate().translationY(container.getHeight()).setDuration(220).setInterpolator(CubicBezierInterpolator.EASE_IN)
                .setUpdateListener(a -> dimFromOffset())
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        finish();
                    }
                }).start();
    }

    private void finish() {
        try {
            super.dismiss();
        } catch (Throwable ignored) {
        }
    }

    private void dimFromOffset() {
        if (container == null || sheet == null) return;
        final float h = Math.max(1, container.getHeight());
        final float visible = 1f - Math.min(1f, Math.max(0f, sheet.getTranslationY() / h));
        container.setBackgroundColor(Color.argb((int) (120 * visible), 0, 0, 0));
        sheet.invalidate();
    }

    // фон и свайп вниз

    private class Sheet extends FrameLayout {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private int shaderTint, shaderHeight;
        private final int touchSlop;
        private float startX, startY;
        private boolean dragging, allowDrag;
        private VelocityTracker velocity;

        Sheet(Context context) {
            super(context);
            setWillNotDraw(false);
            touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            final int h = getHeight();
            if (shaderTint != tint || shaderHeight != h) {
                shaderTint = tint;
                shaderHeight = h;
                paint.setShader(new LinearGradient(0, 0, 0, h,
                        new int[]{tint, ColorUtils.blendARGB(tint, baseColor, 0.55f), baseColor},
                        new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            }
            rect.set(0, 0, getWidth(), h + dp(32));
            final float radius = getTranslationY() > 0 ? dp(28) : 0;
            canvas.drawRoundRect(rect, radius, radius, paint);
        }

        /** Палец над листающимся списком, который ещё не долистан доверху: тянуть плеер нельзя. */
        private boolean overScrollable(float x, float y) {
            View list = mode == MODE_LYRICS ? lyricsScroll : mode == MODE_QUEUE ? queueList : null;
            if (list == null || !list.canScrollVertically(-1)) return false;
            final int[] at = new int[2];
            final int[] me = new int[2];
            list.getLocationOnScreen(at);
            getLocationOnScreen(me);
            final float lx = x + me[0] - at[0], ly = y + me[1] - at[1];
            return lx >= 0 && ly >= 0 && lx <= list.getWidth() && ly <= list.getHeight();
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startX = ev.getX();
                    startY = ev.getY();
                    dragging = false;
                    allowDrag = !closing && !overScrollable(ev.getX(), ev.getY());
                    track(ev, true);
                    break;
                case MotionEvent.ACTION_MOVE:
                    track(ev, false);
                    final float dy = ev.getY() - startY, dx = ev.getX() - startX;
                    if (allowDrag && !dragging && dy > touchSlop && dy > Math.abs(dx) * 1.5f) {
                        dragging = true;
                        startY = ev.getY();
                        return true;
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    dragging = false;
                    break;
            }
            return dragging;
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            if (closing) return false;
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startX = ev.getX();
                    startY = ev.getY();
                    dragging = false;
                    allowDrag = true;
                    track(ev, true);
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    track(ev, false);
                    // ev идёт в координатах листа, а он сам едет вместе с пальцем
                    final float rawY = ev.getY() + getTranslationY();
                    if (!dragging) {
                        final float dy = ev.getY() - startY, dx = ev.getX() - startX;
                        if (allowDrag && dy > touchSlop && dy > Math.abs(dx) * 1.5f) {
                            dragging = true;
                            startY = rawY;
                        }
                    }
                    if (dragging) {
                        setTranslationY(Math.max(0, rawY - startY));
                        dimFromOffset();
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    float speed = 0;
                    if (velocity != null) {
                        velocity.computeCurrentVelocity(1000);
                        speed = velocity.getYVelocity();
                        velocity.recycle();
                        velocity = null;
                    }
                    if (dragging) {
                        dragging = false;
                        if (getTranslationY() > getHeight() / 4f || speed > dp(1200)) {
                            dismiss();
                        } else {
                            animate().translationY(0).setDuration(220).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                                    .setUpdateListener(a -> dimFromOffset()).setListener(null).start();
                        }
                    }
                    return true;
                }
            }
            return true;
        }

        private void track(MotionEvent ev, boolean reset) {
            if (reset && velocity != null) {
                velocity.recycle();
                velocity = null;
            }
            if (velocity == null) velocity = VelocityTracker.obtain();
            // скорость считаем по экрану: лист под пальцем сдвигается
            final MotionEvent copy = MotionEvent.obtain(ev);
            copy.offsetLocation(0, getTranslationY());
            velocity.addMovement(copy);
            copy.recycle();
        }
    }
}
