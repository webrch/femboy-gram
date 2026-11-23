package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.femboy.FemboyRemote;
import org.telegram.femboy.FemboyYou;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * «Тиндер» с тилкайо: пасхалка на вкладке «Чаты», вместо случайной кошки.
 *
 * Стопка карточек: фото, имя, возраст, пара слов о себе и метки. Вправо —
 * нравится, влево — нет. Список приходит с сервера, последний ответ лежит
 * в кэше; пока сервера нет, есть один вшитый толстый тилкайо. Кого уже
 * лайкнули или отвергли, помним и больше не показываем.
 */
public class FemboyTilcayoMatch extends Dialog {

    public static final String URL = "https://femboygram.example/api/gram/tilcayos";
    private static final String CACHE_KEY = "gram_tilcayos";
    private static final long REFRESH_MS = 10 * 60 * 1000L;
    private static final String ASSET = "asset:femboy_tilcayo.webp";
    private static final String PREFS = "femboy_tilcayo";

    // Те же правила, что были у кошки: семь нажатий подряд, не реже раза в секунду,
    // чтобы обычное переключение вкладок на пасхалку не натыкалось.
    private static final int TAPS_NEEDED = 7;
    private static final long GAP_MS = 1000;
    private static int taps;
    private static long lastTap;

    public static void tap(Activity activity) {
        final long now = System.currentTimeMillis();
        taps = (now - lastTap > GAP_MS) ? 1 : taps + 1;
        lastTap = now;
        if (taps >= TAPS_NEEDED) {
            taps = 0;
            show(activity);
        }
    }

    public static void show(Activity activity) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        try {
            new FemboyTilcayoMatch(activity).show();
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    /** Перечитать список с сервера, если ответ старше десяти минут. Ответа никто не ждёт. */
    public static void refresh() {
        FemboyRemote.refreshIfOlder(URL, CACHE_KEY, REFRESH_MS, null);
    }

    // ---------- данные ----------

    public static final class Tilcayo {
        public final String id;
        public final String name;
        public final int age;
        public final String about;
        public final String image;
        public final List<String> tags;

        Tilcayo(String id, String name, int age, String about, String image, List<String> tags) {
            this.id = id;
            this.name = name;
            this.age = age;
            this.about = about;
            this.image = image;
            this.tags = tags;
        }
    }

    private static String language() {
        try {
            return LocaleController.getInstance().getCurrentLocale().getLanguage();
        } catch (Exception e) {
            return "ru";
        }
    }

    /** Основное поле русское; для других языков сперва свой суффикс, потом _en. */
    private static String text(JSONObject json, String key, String lang) {
        if (!"ru".equals(lang)) {
            String v = json.optString(key + "_" + lang, "");
            if (v.isEmpty()) {
                v = json.optString(key + "_en", "");
            }
            if (!v.isEmpty()) {
                return v;
            }
        }
        return json.optString(key, "");
    }

    private static List<String> tags(JSONObject json, String lang) {
        JSONArray array = null;
        if (!"ru".equals(lang)) {
            array = json.optJSONArray("tags_" + lang);
            if (array == null || array.length() == 0) {
                array = json.optJSONArray("tags_en");
            }
        }
        if (array == null || array.length() == 0) {
            array = json.optJSONArray("tags");
        }
        final List<String> out = new ArrayList<>();
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                final String s = array.optString(i, "");
                if (!s.isEmpty()) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    /** Разбор ответа сервера; null, если это не список тилкайо. */
    private static List<Tilcayo> parse(String text) {
        if (text == null) {
            return null;
        }
        try {
            final JSONArray array = new JSONArray(text.trim());
            final String lang = language();
            final List<Tilcayo> out = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                final JSONObject item = array.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                final String id = item.optString("id", "");
                final String name = text(item, "name", lang);
                if (id.isEmpty() || name.isEmpty()) {
                    continue;
                }
                out.add(new Tilcayo(id, name, item.optInt("age", 0), text(item, "about", lang),
                        item.optString("image", ""), tags(item, lang)));
            }
            return out.isEmpty() ? null : out;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Вшитый запас, пока сервер молчит: один толстый тилкайо. */
    private static List<Tilcayo> builtIn() {
        final List<String> tags = new ArrayList<>();
        tags.add(LocaleController.getString(R.string.FemboyTilcayoFatTag1));
        tags.add(LocaleController.getString(R.string.FemboyTilcayoFatTag2));
        tags.add(LocaleController.getString(R.string.FemboyTilcayoFatTag3));
        final List<Tilcayo> out = new ArrayList<>();
        out.add(new Tilcayo("fat", LocaleController.getString(R.string.FemboyTilcayoFatName), 3,
                LocaleController.getString(R.string.FemboyTilcayoFatAbout), ASSET, tags));
        return out;
    }

    private static List<Tilcayo> all() {
        final List<Tilcayo> remote = parse(FemboyRemote.cached(CACHE_KEY));
        return remote != null ? remote : builtIn();
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static Set<String> seen() {
        final Set<String> out = new HashSet<>(prefs().getStringSet("liked", new HashSet<>()));
        out.addAll(prefs().getStringSet("noped", new HashSet<>()));
        return out;
    }

    private static void remember(String id, boolean like) {
        final String key = like ? "liked" : "noped";
        final Set<String> set = new HashSet<>(prefs().getStringSet(key, new HashSet<>()));
        set.add(id);
        prefs().edit().putStringSet(key, set).apply();
    }

    private static void forget() {
        prefs().edit().remove("liked").remove("noped").apply();
    }

    // ---------- окно ----------

    private final Activity activity;
    private final ArrayList<Tilcayo> deck = new ArrayList<>();
    private FrameLayout root;
    private FrameLayout stack;
    private LinearLayout buttons;
    private LinearLayout empty;
    private Card top, next;
    private boolean busy;

    private final int surface, cardColor, textColor, grayColor, accent, onAccent, chipBg, chipText;
    private final int likeColor = 0xFF2BAE66;
    private final int nopeColor;
    private final boolean dark;

    private FemboyTilcayoMatch(Activity activity) {
        super(activity);
        this.activity = activity;
        dark = Theme.isCurrentThemeDark();
        if (FemboyYou.on()) {
            FemboyYou.ensureRoles();
            surface = FemboyYou.surface;
            cardColor = FemboyYou.surfaceContainerHigh;
            textColor = FemboyYou.onSurface;
            grayColor = FemboyYou.onSurfaceVariant;
            accent = FemboyYou.primaryColor;
            onAccent = FemboyYou.onPrimary;
            chipBg = FemboyYou.secondaryContainer;
            chipText = FemboyYou.onSecondaryContainer;
        } else {
            surface = Theme.getColor(Theme.key_windowBackgroundWhite);
            cardColor = Theme.getColor(Theme.key_windowBackgroundGray);
            textColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
            grayColor = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText);
            accent = Theme.getColor(Theme.key_featuredStickers_addButton);
            onAccent = Theme.getColor(Theme.key_featuredStickers_buttonText);
            chipBg = Theme.multAlpha(accent, 0.14f);
            chipText = accent;
        }
        nopeColor = Theme.getColor(Theme.key_text_RedBold);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        final Context context = getContext();

        root = new FrameLayout(context);
        root.setBackgroundColor(surface);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int t = 0, b = 0;
            if (Build.VERSION.SDK_INT >= 30) {
                final android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                t = bars.top;
                b = bars.bottom;
            } else {
                t = insets.getSystemWindowInsetTop();
                b = insets.getSystemWindowInsetBottom();
            }
            v.setPadding(0, t, 0, b);
            return insets;
        });

        final LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        root.addView(column, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        // верхняя строка: крестик и заголовок
        final LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        final ImageView close = new ImageView(context);
        close.setImageResource(R.drawable.femboy_ic_close);
        close.setColorFilter(new PorterDuffColorFilter(textColor, PorterDuff.Mode.SRC_IN));
        close.setScaleType(ImageView.ScaleType.CENTER);
        close.setBackground(Theme.createSelectorDrawable(Theme.multAlpha(textColor, 0.12f), Theme.RIPPLE_MASK_CIRCLE_20DP));
        close.setContentDescription(LocaleController.getString(R.string.Close));
        close.setOnClickListener(v -> dismiss());
        bar.addView(close, LayoutHelper.createLinear(48, 48, Gravity.CENTER_VERTICAL, 4, 0, 0, 0));
        final TextView title = new TextView(context);
        title.setText(LocaleController.getString(R.string.FemboyTilcayoTitle));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(textColor);
        bar.addView(title, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL, 8, 0, 16, 0));
        column.addView(bar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 64));

        stack = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                // фото квадратное, пока помещается; место под текст оставляем
                photoMax = MeasureSpec.getSize(heightMeasureSpec) - dp(200);
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        };
        stack.setClipChildren(false);
        column.setClipChildren(false);
        root.setClipChildren(false);
        column.addView(stack, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f, 16, 4, 16, 8));

        empty = buildEmpty(context);
        stack.addView(empty, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);
        final RoundButton nope = new RoundButton(context, R.drawable.femboy_ic_close, nopeColor);
        nope.setContentDescription(LocaleController.getString(R.string.FemboyTilcayoNopeButton));
        nope.setOnClickListener(v -> swipeByButton(false));
        buttons.addView(nope, LayoutHelper.createLinear(68, 68, Gravity.CENTER, 0, 0, 28, 0));
        final RoundButton like = new RoundButton(context, R.drawable.femboy_ic_favorite_fill, likeColor);
        like.setContentDescription(LocaleController.getString(R.string.FemboyTilcayoLikeButton));
        like.setOnClickListener(v -> swipeByButton(true));
        buttons.addView(like, LayoutHelper.createLinear(68, 68, Gravity.CENTER, 28, 0, 0, 0));
        column.addView(buttons, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 100));

        setContentView(root, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        final Window window = getWindow();
        if (window != null) {
            // как у шторок телеграма: под полоской жестов тоже наше окно
            root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            window.setBackgroundDrawable(new ColorDrawable(surface));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setWindowAnimations(R.style.DialogNoAnimation);
            window.setStatusBarColor(surface);
            window.setNavigationBarColor(surface);
            AndroidUtilities.setLightStatusBar(window, AndroidUtilities.computePerceivedBrightness(surface) > 0.721f);
            AndroidUtilities.setLightNavigationBar(window.getDecorView(), AndroidUtilities.computePerceivedBrightness(surface) > 0.721f);
        }

        fillDeck(all());
        render();
        root.setAlpha(0f);
        root.setTranslationY(dp(24));
        root.animate().alpha(1f).translationY(0).setDuration(220).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();

        // свежий список: новых тилкайо подкладываем под стопку
        FemboyRemote.refreshIfOlder(URL, CACHE_KEY, REFRESH_MS, text -> {
            final List<Tilcayo> fresh = parse(text);
            if (fresh == null || !isShowing()) {
                return;
            }
            final boolean wasEmpty = deck.isEmpty();
            final int before = deck.size();
            fillDeck(fresh);
            if (wasEmpty || before < 2) {
                render();
            }
        });
    }

    private void fillDeck(List<Tilcayo> list) {
        final Set<String> seen = seen();
        final Set<String> have = new HashSet<>();
        for (Tilcayo t : deck) {
            have.add(t.id);
        }
        if (top != null) {
            have.add(top.tilcayo.id);
        }
        // вшитого заменяет серверный с тем же id, лишних карточек не плодим
        for (Tilcayo t : list) {
            if (seen.contains(t.id)) {
                continue;
            }
            if (have.contains(t.id)) {
                for (int i = 0; i < deck.size(); i++) {
                    if (deck.get(i).id.equals(t.id) && ASSET.equals(deck.get(i).image) && (top == null || top.tilcayo != deck.get(i))) {
                        deck.set(i, t);
                    }
                }
                continue;
            }
            deck.add(t);
            have.add(t.id);
        }
    }

    /** Перестроить стопку: верхняя карточка и одна под ней. */
    private void render() {
        if (top != null) {
            stack.removeView(top);
        }
        if (next != null) {
            stack.removeView(next);
        }
        top = next = null;
        if (deck.size() > 1) {
            next = new Card(getContext(), deck.get(1));
            next.setBehind(0f);
            stack.addView(next, cardParams());
        }
        if (!deck.isEmpty()) {
            top = new Card(getContext(), deck.get(0));
            stack.addView(top, cardParams());
        }
        updateEmpty(false);
    }

    private FrameLayout.LayoutParams cardParams() {
        return LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER);
    }

    private void updateEmpty(boolean animated) {
        final boolean none = deck.isEmpty();
        empty.setVisibility(none ? View.VISIBLE : View.GONE);
        if (none && animated) {
            empty.setAlpha(0f);
            empty.setScaleX(0.9f);
            empty.setScaleY(0.9f);
            empty.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(CubicBezierInterpolator.EASE_OUT_BACK).start();
        }
        buttons.animate().alpha(none ? 0f : 1f).setDuration(animated ? 200 : 0).start();
        for (int i = 0; i < buttons.getChildCount(); i++) {
            buttons.getChildAt(i).setEnabled(!none);
        }
    }

    private void swipeByButton(boolean like) {
        if (top == null || busy) {
            return;
        }
        top.flyOut(like ? 1 : -1, 0, 0);
    }

    /** Карточка улетела. */
    private void onSwiped(Card card, boolean like) {
        remember(card.tilcayo.id, like);
        stack.removeView(card);
        deck.remove(card.tilcayo);
        top = next;
        next = null;
        if (top != null) {
            top.animate().scaleX(1f).scaleY(1f).translationY(0).setDuration(160).start();
        }
        if (deck.size() > 1) {
            next = new Card(getContext(), deck.get(1));
            next.setBehind(0f);
            next.setAlpha(0f);
            next.animate().alpha(1f).setDuration(200).start();
            stack.addView(next, 1, cardParams());
        }
        updateEmpty(true);
        if (like) {
            showMatch(card);
        }
    }

    private void reset() {
        forget();
        deck.clear();
        top = null;
        next = null;
        for (int i = stack.getChildCount() - 1; i >= 0; i--) {
            if (stack.getChildAt(i) instanceof Card) {
                stack.removeViewAt(i);
            }
        }
        fillDeck(all());
        render();
        if (top != null) {
            top.setAlpha(0f);
            top.setScaleX(0.9f);
            top.setScaleY(0.9f);
            top.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(CubicBezierInterpolator.EASE_OUT_BACK).start();
        }
    }

    private LinearLayout buildEmpty(Context context) {
        final LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(dp(24), dp(24), dp(24), dp(24));

        final ImageView icon = new ImageView(context);
        icon.setImageResource(R.drawable.femboy_ic_pets);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setPadding(dp(30), dp(30), dp(30), dp(30));
        icon.setColorFilter(new PorterDuffColorFilter(chipText, PorterDuff.Mode.SRC_IN));
        icon.setBackground(Theme.createCircleDrawable(dp(112), chipBg));
        box.addView(icon, LayoutHelper.createLinear(112, 112, Gravity.CENTER_HORIZONTAL));

        final TextView title = new TextView(context);
        title.setText(LocaleController.getString(R.string.FemboyTilcayoEmpty));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(textColor);
        title.setGravity(Gravity.CENTER);
        box.addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 20, 0, 0));

        final TextView text = new TextView(context);
        text.setText(LocaleController.getString(R.string.FemboyTilcayoEmptyText));
        text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        text.setTextColor(grayColor);
        text.setGravity(Gravity.CENTER);
        box.addView(text, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 6, 0, 0));

        final TextView again = new TextView(context);
        again.setText(LocaleController.getString(R.string.FemboyTilcayoRestart));
        again.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        again.setTypeface(AndroidUtilities.bold());
        again.setTextColor(chipText);
        again.setGravity(Gravity.CENTER);
        again.setPadding(dp(20), 0, dp(20), 0);
        again.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(20), chipBg, Theme.multAlpha(chipText, 0.15f)));
        again.setOnClickListener(v -> reset());
        box.addView(again, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 40, Gravity.CENTER_HORIZONTAL, 0, 20, 0, 0));

        // долгое нажатие по пустому месту тоже начинает заново
        box.setOnLongClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            reset();
            return true;
        });
        return box;
    }

    // ---------- картинки ----------

    private static void loadImage(Tilcayo t, ImageView into) {
        final String image = t.image;
        if (image == null || image.isEmpty() || image.startsWith("asset:")) {
            final String asset = image != null && image.startsWith("asset:") ? image.substring(6) : "femboy_tilcayo.webp";
            decode(() -> {
                try (InputStream in = ApplicationLoader.applicationContext.getAssets().open(asset)) {
                    return BitmapFactory.decodeStream(in);
                }
            }, into);
            return;
        }
        FemboyRemote.image(image, file -> {
            if (file != null) {
                decode(() -> decodeFile(file), into);
            } else if ("fat".equals(t.id)) {
                // сервер не отдал картинку, а толстый есть и в сборке
                loadImage(new Tilcayo(t.id, t.name, t.age, t.about, ASSET, t.tags), into);
            }
        });
    }

    private static Bitmap decodeFile(File file) {
        final BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / sample > 1280) {
            sample *= 2;
        }
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }

    private interface BitmapSource {
        Bitmap get() throws Exception;
    }

    private static void decode(BitmapSource source, ImageView into) {
        Utilities.globalQueue.postRunnable(() -> {
            Bitmap bitmap = null;
            try {
                bitmap = source.get();
            } catch (Throwable t) {
                FileLog.e(t);
            }
            final Bitmap done = bitmap;
            if (done != null) {
                AndroidUtilities.runOnUIThread(() -> {
                    into.setImageBitmap(done);
                    into.setAlpha(0f);
                    into.animate().alpha(1f).setDuration(180).start();
                });
            }
        });
    }

    // ---------- карточка ----------

    private int photoMax = Integer.MAX_VALUE;

    private class Card extends FrameLayout {

        final Tilcayo tilcayo;
        private final ImageView photo;
        private final View tint;
        private final TextView stampLike, stampNope;
        private final int touchSlop;
        private VelocityTracker velocity;
        private float downX, downY, startDx, startDy;
        private float dx, dy;
        private float rotSign = 1f;
        private boolean dragging;
        private boolean overThreshold;
        private ValueAnimator anim;

        Card(Context context, Tilcayo tilcayo) {
            super(context);
            this.tilcayo = tilcayo;
            touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
            setBackgroundColor(cardColor);
            setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(28));
                }
            });
            setClipToOutline(true);
            setElevation(dp(3));

            final LinearLayout content = new LinearLayout(context);
            content.setOrientation(LinearLayout.VERTICAL);
            addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            final FrameLayout photoBox = new FrameLayout(context) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    final int w = MeasureSpec.getSize(widthMeasureSpec);
                    final int h = Math.max(dp(160), Math.min(w, photoMax));
                    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
                }
            };
            photoBox.setBackgroundColor(ColorUtils.blendARGB(cardColor, textColor, 0.06f));
            final ImageView paw = new ImageView(context);
            paw.setImageResource(R.drawable.femboy_ic_pets);
            paw.setColorFilter(new PorterDuffColorFilter(Theme.multAlpha(grayColor, 0.4f), PorterDuff.Mode.SRC_IN));
            photoBox.addView(paw, LayoutHelper.createFrame(48, 48, Gravity.CENTER));
            photo = new ImageView(context);
            photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
            photoBox.addView(photo, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            stampLike = stamp(context, "♥ " + LocaleController.getString(R.string.FemboyTilcayoLike), likeColor, -14);
            photoBox.addView(stampLike, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 22, 30, 0, 0));
            stampNope = stamp(context, "✕ " + LocaleController.getString(R.string.FemboyTilcayoNope), nopeColor, 14);
            photoBox.addView(stampNope, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.RIGHT, 0, 30, 22, 0));
            content.addView(photoBox, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            final LinearLayout info = new LinearLayout(context);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setPadding(dp(20), dp(16), dp(20), dp(20));
            final TextView name = new TextView(context);
            name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24);
            name.setTypeface(AndroidUtilities.bold());
            name.setTextColor(textColor);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            final StringBuilder title = new StringBuilder(tilcayo.name);
            if (tilcayo.age > 0) {
                title.append(", ").append(tilcayo.age);
            }
            name.setText(title);
            info.addView(name, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            if (!tilcayo.about.isEmpty()) {
                final TextView about = new TextView(context);
                about.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
                about.setTextColor(grayColor);
                about.setLineSpacing(dp(2), 1f);
                about.setMaxLines(4);
                about.setEllipsize(android.text.TextUtils.TruncateAt.END);
                about.setText(tilcayo.about);
                info.addView(about, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 6, 0, 0));
            }
            if (!tilcayo.tags.isEmpty()) {
                final Flow chips = new Flow(context);
                for (String tag : tilcayo.tags) {
                    final TextView chip = new TextView(context);
                    chip.setText(tag);
                    chip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
                    chip.setTextColor(chipText);
                    chip.setGravity(Gravity.CENTER);
                    chip.setSingleLine(true);
                    chip.setPadding(dp(12), 0, dp(12), 0);
                    chip.setBackground(Theme.createRoundRectDrawable(dp(8), chipBg));
                    chips.addView(chip, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32)));
                }
                info.addView(chips, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 0));
            }
            content.addView(info, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            tint = new View(context);
            tint.setAlpha(0f);
            addView(tint, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            loadImage(tilcayo, photo);
        }

        private TextView stamp(Context context, String text, int color, float rotation) {
            final TextView stamp = new TextView(context);
            stamp.setText(text);
            stamp.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 26);
            stamp.setTypeface(AndroidUtilities.bold());
            stamp.setTextColor(color);
            stamp.setPadding(dp(12), dp(4), dp(12), dp(4));
            final GradientDrawable border = new GradientDrawable();
            border.setCornerRadius(dp(10));
            border.setStroke(dp(4), color);
            border.setColor(Theme.multAlpha(Color.WHITE, 0.75f));
            stamp.setBackground(border);
            stamp.setRotation(rotation);
            stamp.setAlpha(0f);
            return stamp;
        }

        /** Карточка под верхней: чуть меньше, растёт, пока верхнюю тянут. */
        void setBehind(float progress) {
            final float s = 0.94f + 0.06f * progress;
            setScaleX(s);
            setScaleY(s);
            setTranslationY(dp(14) * (1f - progress));
        }

        private void setDrag(float x, float y) {
            dx = x;
            dy = y;
            setTranslationX(x);
            setTranslationY(y);
            final float w = Math.max(1, getWidth());
            setRotation(x / w * 18f * rotSign);
            final float p = Math.max(-1f, Math.min(1f, x / (w * 0.35f)));
            stampLike.setAlpha(Math.max(0f, p));
            stampNope.setAlpha(Math.max(0f, -p));
            stampLike.setScaleX(1.3f - 0.3f * Math.max(0f, p));
            stampLike.setScaleY(stampLike.getScaleX());
            stampNope.setScaleX(1.3f - 0.3f * Math.max(0f, -p));
            stampNope.setScaleY(stampNope.getScaleX());
            tint.setBackgroundColor(p >= 0 ? likeColor : nopeColor);
            tint.setAlpha(Math.abs(p) * 0.22f);
            if (next != null && this == top) {
                next.setBehind(Math.abs(p));
            }
            final boolean over = Math.abs(p) >= 1f;
            if (over && !overThreshold && dragging) {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            }
            overThreshold = over;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (this != top || busy) {
                return false;
            }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (anim != null) {
                        anim.cancel();
                    }
                    downX = event.getRawX();
                    downY = event.getRawY();
                    startDx = dx;
                    startDy = dy;
                    dragging = false;
                    rotSign = event.getY() < getHeight() / 2f ? 1f : -1f;
                    velocity = VelocityTracker.obtain();
                    velocity.addMovement(event);
                    getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    if (velocity != null) {
                        velocity.addMovement(event);
                    }
                    final float mx = event.getRawX() - downX;
                    final float my = event.getRawY() - downY;
                    if (!dragging && Math.hypot(mx, my) > touchSlop) {
                        dragging = true;
                    }
                    if (dragging) {
                        setDrag(startDx + mx, startDy + my);
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    float vx = 0, vy = 0;
                    if (velocity != null) {
                        velocity.addMovement(event);
                        velocity.computeCurrentVelocity(1000);
                        vx = velocity.getXVelocity();
                        vy = velocity.getYVelocity();
                        velocity.recycle();
                        velocity = null;
                    }
                    final boolean wasDragging = dragging;
                    dragging = false;
                    if (event.getActionMasked() == MotionEvent.ACTION_UP && wasDragging) {
                        final float threshold = getWidth() * 0.35f;
                        final boolean fast = Math.abs(vx) > dp(900);
                        int dir = 0;
                        if (fast && Math.signum(vx) == Math.signum(dx) && Math.abs(dx) > dp(24)) {
                            dir = vx > 0 ? 1 : -1;
                        } else if (Math.abs(dx) > threshold && !(fast && Math.signum(vx) != Math.signum(dx))) {
                            dir = dx > 0 ? 1 : -1;
                        }
                        if (dir != 0) {
                            flyOut(dir, vx, vy);
                            return true;
                        }
                    }
                    springBack();
                    return true;
                }
            }
            return true;
        }

        private void springBack() {
            final float fromX = dx, fromY = dy;
            anim = ValueAnimator.ofFloat(1f, 0f);
            anim.setDuration(420);
            anim.setInterpolator(CubicBezierInterpolator.EASE_OUT_BACK);
            anim.addUpdateListener(a -> {
                final float v = (float) a.getAnimatedValue();
                setDrag(fromX * v, fromY * v);
            });
            anim.start();
        }

        void flyOut(int dir, float vx, float vy) {
            if (busy) {
                return;
            }
            busy = true;
            final float fromX = dx, fromY = dy;
            final float w = Math.max(getWidth(), dp(200));
            final float toX = dir * (stack.getWidth() + w) * 0.9f + (dir > 0 ? Math.max(0, fromX) : Math.min(0, fromX));
            final float speed = Math.max(Math.abs(vx), dp(1800));
            final long duration = (long) Math.max(160, Math.min(360, Math.abs(toX - fromX) / speed * 1000f));
            final float toY = fromY + vy * duration / 1000f * 0.6f + (vx == 0 ? dp(30) : 0);
            if (vx == 0 && vy == 0) {
                rotSign = 1f;
            }
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            anim = ValueAnimator.ofFloat(0f, 1f);
            anim.setDuration(duration);
            anim.setInterpolator(vx == 0 ? CubicBezierInterpolator.EASE_IN : CubicBezierInterpolator.EASE_OUT);
            anim.addUpdateListener(a -> {
                final float v = (float) a.getAnimatedValue();
                setDrag(fromX + (toX - fromX) * v, fromY + (toY - fromY) * v);
            });
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    busy = false;
                    onSwiped(Card.this, dir > 0);
                }
            });
            anim.start();
        }
    }

    /** Метки в несколько строк, как чипы в MD3. */
    private static class Flow extends ViewGroup {
        Flow(Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            final int width = MeasureSpec.getSize(widthMeasureSpec);
            final int gap = dp(8);
            int x = 0, y = 0, row = 0;
            for (int i = 0; i < getChildCount(); i++) {
                final View child = getChildAt(i);
                child.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
                        MeasureSpec.makeMeasureSpec(child.getLayoutParams().height, MeasureSpec.EXACTLY));
                if (x > 0 && x + child.getMeasuredWidth() > width) {
                    x = 0;
                    y += row + gap;
                    row = 0;
                }
                x += child.getMeasuredWidth() + gap;
                row = Math.max(row, child.getMeasuredHeight());
            }
            setMeasuredDimension(width, y + row);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            final int width = r - l;
            final int gap = dp(8);
            int x = 0, y = 0, row = 0;
            for (int i = 0; i < getChildCount(); i++) {
                final View child = getChildAt(i);
                if (x > 0 && x + child.getMeasuredWidth() > width) {
                    x = 0;
                    y += row + gap;
                    row = 0;
                }
                child.layout(x, y, x + child.getMeasuredWidth(), y + child.getMeasuredHeight());
                x += child.getMeasuredWidth() + gap;
                row = Math.max(row, child.getMeasuredHeight());
            }
        }
    }

    /** Круглая кнопка лайка или отказа. */
    private class RoundButton extends FrameLayout {
        RoundButton(Context context, int icon, int color) {
            super(context);
            final GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(cardColor);
            bg.setStroke(dp(1.5f), Theme.multAlpha(color, 0.35f));
            setBackground(bg);
            setForeground(Theme.createSelectorDrawable(Theme.multAlpha(color, 0.18f), Theme.RIPPLE_MASK_CIRCLE_20DP));
            setElevation(dp(2));
            setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setOval(0, 0, view.getWidth(), view.getHeight());
                }
            });
            final ImageView image = new ImageView(context);
            image.setImageResource(icon);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
            addView(image, LayoutHelper.createFrame(32, 32, Gravity.CENTER));
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                animate().scaleX(0.88f).scaleY(0.88f).setDuration(100).start();
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(CubicBezierInterpolator.EASE_OUT_BACK).start();
            }
            return super.onTouchEvent(event);
        }
    }

    // ---------- мэтч ----------

    private void showMatch(Card card) {
        final Context context = getContext();
        final FrameLayout overlay = new FrameLayout(context);
        overlay.setBackgroundColor(0xD0101014);
        overlay.setClickable(true);

        final Confetti confetti = new Confetti(context, new int[]{likeColor, 0xFFFF5C8A, 0xFFFF8FB1, accent, 0xFFFFC94D});
        overlay.addView(confetti, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        final LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        overlay.addView(box, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 24, 0, 24, 0));

        final TextView title = new TextView(context);
        title.setText(LocaleController.getString(R.string.FemboyTilcayoMatch));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 40);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(0xFFFF8FB1);
        title.setGravity(Gravity.CENTER);
        title.setRotation(-4f);
        title.setShadowLayer(dp(8), 0, dp(2), 0x80FF2D6F);
        box.addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        final ImageView photo = new ImageView(context);
        photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        photo.setImageDrawable(card.photo.getDrawable());
        photo.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        photo.setClipToOutline(true);
        photo.setBackgroundColor(cardColor);
        final FrameLayout ring = new FrameLayout(context);
        ring.setBackground(Theme.createCircleDrawable(dp(148), Color.WHITE));
        ring.addView(photo, LayoutHelper.createFrame(140, 140, Gravity.CENTER));
        box.addView(ring, LayoutHelper.createLinear(148, 148, Gravity.CENTER_HORIZONTAL, 0, 28, 0, 0));

        final TextView text = new TextView(context);
        text.setText(LocaleController.formatString(R.string.FemboyTilcayoMatchText, card.tilcayo.name));
        text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        text.setTextColor(Color.WHITE);
        text.setGravity(Gravity.CENTER);
        box.addView(text, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 24, 0, 0));

        final TextView ok = new TextView(context);
        ok.setText(LocaleController.getString(R.string.FemboyTilcayoMatchOk));
        ok.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        ok.setTypeface(AndroidUtilities.bold());
        ok.setTextColor(onAccent);
        ok.setGravity(Gravity.CENTER);
        ok.setPadding(dp(32), 0, dp(32), 0);
        ok.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(24), accent, Theme.multAlpha(onAccent, 0.2f)));
        box.addView(ok, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 48, Gravity.CENTER_HORIZONTAL, 0, 32, 0, 0));

        final Runnable close = () -> overlay.animate().alpha(0f).setDuration(200).withEndAction(() -> root.removeView(overlay)).start();
        ok.setOnClickListener(v -> close.run());
        overlay.setOnClickListener(v -> close.run());

        root.addView(overlay, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        overlay.setAlpha(0f);
        overlay.animate().alpha(1f).setDuration(200).start();
        title.setScaleX(0.3f);
        title.setScaleY(0.3f);
        title.animate().scaleX(1f).scaleY(1f).setDuration(520).setInterpolator(CubicBezierInterpolator.EASE_OUT_BACK).start();
        ring.setScaleX(0f);
        ring.setScaleY(0f);
        ring.animate().scaleX(1f).scaleY(1f).setStartDelay(120).setDuration(480).setInterpolator(CubicBezierInterpolator.EASE_OUT_BACK).start();
        overlay.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        confetti.burst();
    }

    /** Сердечки, разлетающиеся из середины и падающие вниз. */
    private static class Confetti extends View {
        private static final int COUNT = 48;
        private static final long LIFE = 2600;
        private final int[] colors;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path heart = new Path();
        private final float[] x = new float[COUNT], y = new float[COUNT], vx = new float[COUNT], vy = new float[COUNT];
        private final float[] size = new float[COUNT], rot = new float[COUNT], spin = new float[COUNT];
        private final int[] color = new int[COUNT];
        private long started, last;

        Confetti(Context context, int[] colors) {
            super(context);
            this.colors = colors;
            // сердце в единичном квадрате с центром в нуле
            heart.moveTo(0, 0.38f);
            heart.cubicTo(-0.06f, 0.32f, -0.5f, 0.06f, -0.5f, -0.17f);
            heart.cubicTo(-0.5f, -0.43f, -0.2f, -0.52f, 0, -0.28f);
            heart.cubicTo(0.2f, -0.52f, 0.5f, -0.43f, 0.5f, -0.17f);
            heart.cubicTo(0.5f, 0.06f, 0.06f, 0.32f, 0, 0.38f);
            heart.close();
        }

        void burst() {
            started = 0;
            invalidate();
        }

        private void seed() {
            final Random r = new Random();
            final float cx = getWidth() / 2f, cy = getHeight() * 0.42f;
            for (int i = 0; i < COUNT; i++) {
                final double a = -Math.PI / 2 + (r.nextFloat() - 0.5f) * Math.PI * 1.6f;
                final float speed = dp(260) + r.nextFloat() * dp(620);
                x[i] = cx + (r.nextFloat() - 0.5f) * dp(40);
                y[i] = cy + (r.nextFloat() - 0.5f) * dp(40);
                vx[i] = (float) Math.cos(a) * speed;
                vy[i] = (float) Math.sin(a) * speed;
                size[i] = dp(10) + r.nextFloat() * dp(16);
                rot[i] = (r.nextFloat() - 0.5f) * 60f;
                spin[i] = (r.nextFloat() - 0.5f) * 240f;
                color[i] = colors[r.nextInt(colors.length)];
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final long now = android.os.SystemClock.uptimeMillis();
            if (started == 0) {
                if (getWidth() == 0) {
                    postInvalidateOnAnimation();
                    return;
                }
                seed();
                started = last = now;
            }
            final float dt = Math.min(0.05f, (now - last) / 1000f);
            last = now;
            final float age = (now - started) / (float) LIFE;
            if (age >= 1f) {
                return;
            }
            final float gravity = dp(900);
            final int alpha = (int) (255 * Math.min(1f, (1f - age) * 2.5f));
            for (int i = 0; i < COUNT; i++) {
                vy[i] += gravity * dt;
                vx[i] *= 0.985f;
                x[i] += vx[i] * dt;
                y[i] += vy[i] * dt;
                rot[i] += spin[i] * dt;
                paint.setColor(color[i]);
                paint.setAlpha(alpha);
                canvas.save();
                canvas.translate(x[i], y[i]);
                canvas.rotate(rot[i]);
                canvas.scale(size[i], size[i]);
                canvas.drawPath(heart, paint);
                canvas.restore();
            }
            postInvalidateOnAnimation();
        }
    }
}
