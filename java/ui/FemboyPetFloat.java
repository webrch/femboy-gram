package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.femboy.FemboyStreaks;
import org.telegram.femboy.FemboyYou;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Random;

/**
 * Маленький тилкайо поверх переписки. Слой во весь чат, но сам касания не
 * берёт: всё мимо питомца и облачка уходит списку сообщений.
 *
 * Жесты на питомце:
 * тап = радуется и что-то говорит в облачке;
 * быстрый свайп вбок = крутится (это делает сам FemboyPetView);
 * удержать и тащить = переносится, отпустить = прилипает к ближнему краю;
 * удержать и отпустить на месте = окно питомца.
 */
@SuppressLint("ViewConstructor")
public class FemboyPetFloat extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    private static final int SIZE = 110;
    private static final int EDGE = 4;
    private static final int DEFAULT_FROM_BOTTOM = 72;
    private static final int BOTH = FemboyStreaks.ME | FemboyStreaks.THEM;

    /** Что считать нижней границей чата: верх поля ввода. */
    public interface Anchor {
        View get();
    }

    private final BaseFragment fragment;
    private final int account;
    private final long did;
    private final Anchor anchor;
    private final Random rnd = new Random();

    private final FrameLayout host;
    private FemboyPetView pet;
    private final Bubble bubble;

    private boolean resumed = true;
    private boolean shown;
    private int lastStreak = -1, lastFlags = -1;
    private String lastPhrase;
    private long lastNewMessageSay;

    // место: край и отступ от низа в dp
    private int side;
    private float fromBottomDp;
    private float minY, maxY;

    // жест
    private final int touchSlop;
    private final long longPressMs;
    private float downRawX, downRawY, startTx, startTy;
    private boolean picked, movedPicked, touching;
    private boolean xAnimating;
    private final Runnable longPress = this::pick;
    private final Runnable hideBubble = () -> showBubble(false);

    private final int[] loc = new int[2], loc2 = new int[2];

    private final ViewTreeObserver.OnPreDrawListener preDraw = () -> {
        place();
        return true;
    };

    public FemboyPetFloat(Context context, BaseFragment fragment, long did, Anchor anchor) {
        super(context);
        this.fragment = fragment;
        this.account = fragment.getCurrentAccount();
        this.did = did;
        this.anchor = anchor;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        longPressMs = Math.min(ViewConfiguration.getLongPressTimeout(), 380);
        setClipChildren(false);

        final SharedPreferences p = prefs(account);
        side = p.getInt("s" + did, 1);
        fromBottomDp = p.getFloat("b" + did, DEFAULT_FROM_BOTTOM);

        host = new FrameLayout(context) {
            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                // в руке: всё наше; иначе как обычно, FemboyPetView крутит и ловит тап
                if (hostTouch(ev)) return true;
                return super.dispatchTouchEvent(ev) || ev.getActionMasked() == MotionEvent.ACTION_DOWN;
            }
        };
        host.setVisibility(GONE);
        addView(host, new LayoutParams(dp(SIZE), dp(SIZE), Gravity.TOP | Gravity.LEFT));

        bubble = new Bubble(context);
        bubble.setVisibility(GONE);
        addView(bubble, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT));
    }

    // ---------------------------------------------------------------- настройки чата

    private static SharedPreferences prefs(int account) {
        return ApplicationLoader.applicationContext.getSharedPreferences("femboy_pet_float_" + account, Context.MODE_PRIVATE);
    }

    /** Тилкайо в переписках вообще: переключатель в настройках FemboyGram. */
    public static boolean inChatEnabled() {
        return org.telegram.femboy.FemboyConfig.prefs().getBoolean("pet_in_chat", true);
    }

    /** Включить или выключить; включение заодно возвращает питомца во все чаты, где его прятали крестиком. */
    public static void setInChatEnabled(boolean on) {
        org.telegram.femboy.FemboyConfig.prefs().edit().putBoolean("pet_in_chat", on).apply();
        if (on) {
            for (int a = 0; a < org.telegram.messenger.UserConfig.MAX_ACCOUNT_COUNT; a++) {
                final SharedPreferences p = prefs(a);
                final SharedPreferences.Editor e = p.edit();
                for (String key : p.getAll().keySet()) {
                    if (key.startsWith("h")) e.remove(key);
                }
                e.apply();
            }
        }
        for (int a = 0; a < org.telegram.messenger.UserConfig.MAX_ACCOUNT_COUNT; a++) {
            NotificationCenter.getInstance(a).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_NAME);
        }
    }

    /** Спрятан ли питомец в этом чате. */
    public static boolean isHidden(int account, long did) {
        return prefs(account).getBoolean("h" + did, false);
    }

    /** Спрятать или вернуть питомца в чате; открытый чат узнает сразу. */
    public static void setHidden(int account, long did, boolean hidden) {
        prefs(account).edit().putBoolean("h" + did, hidden).apply();
        NotificationCenter.getInstance(account).postNotificationName(
                NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_NAME);
    }

    // ---------------------------------------------------------------- жизненный цикл

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.updateInterfaces);
        getViewTreeObserver().addOnPreDrawListener(preDraw);
        refresh();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getInstance(account).removeObserver(this, NotificationCenter.updateInterfaces);
        getViewTreeObserver().removeOnPreDrawListener(preDraw);
        AndroidUtilities.cancelRunOnUIThread(longPress);
        AndroidUtilities.cancelRunOnUIThread(hideBubble);
    }

    public void onResume() {
        resumed = true;
        refresh();
    }

    public void onPause() {
        resumed = false;
        if (pet != null) pet.onPause();
        showBubble(false);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id != NotificationCenter.updateInterfaces) return;
        final int mask = args.length > 0 && args[0] instanceof Integer ? (Integer) args[0] : 0;
        if ((mask & MessagesController.UPDATE_MASK_NAME) != 0) refresh();
    }

    /** Точка на экране попадает в питомца или облачко: тогда свайп назад не начинать. */
    public boolean hits(float rawX, float rawY) {
        return hit(host, rawX, rawY) || hit(bubble, rawX, rawY);
    }

    private boolean hit(View v, float rawX, float rawY) {
        if (v.getVisibility() != VISIBLE || !v.isAttachedToWindow()) return false;
        v.getLocationOnScreen(loc);
        return rawX >= loc[0] && rawX < loc[0] + v.getWidth() && rawY >= loc[1] && rawY < loc[1] + v.getHeight();
    }

    /** Новые сообщения в этом чате. */
    public void onNewMessages(ArrayList<MessageObject> list) {
        if (!shown || list == null || list.isEmpty()) return;
        boolean incoming = false, any = false;
        for (MessageObject m : list) {
            if (m == null || m.messageOwner == null || m.messageOwner instanceof TLRPC.TL_messageService) continue;
            any = true;
            if (!m.isOut()) incoming = true;
        }
        if (!any) return;
        pet.cheer();
        final long now = SystemClock.elapsedRealtime();
        if (incoming && now - lastNewMessageSay > 15000 && bubble.getVisibility() != VISIBLE) {
            lastNewMessageSay = now;
            say(LocaleController.formatString(R.string.FemboyPetSayNewMessage, name()));
        }
    }

    // ---------------------------------------------------------------- состояние

    private void refresh() {
        final FemboyStreaks.State s = FemboyStreaks.state(account, did);
        final boolean want = isAttachedToWindow() && FemboyStreaks.petEnabled() && FemboyStreaks.flamesEnabled()
                && s.streak >= FemboyStreaks.MIN_SHOWN && inChatEnabled() && !isHidden(account, did);
        if (want) {
            if (pet == null) {
                // сам GL-вид создаём, только когда питомец правда нужен
                pet = new FemboyPetView(getContext());
                pet.setIdleFps(30);
                pet.setOnClickListener(v -> say(phrase(FemboyStreaks.state(account, did))));
                host.addView(pet, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            }
            pet.setStage(FemboyStreaks.stage(s.streak));
            pet.setAccent(FemboyStreakDrawable.tierColor(s.streak));
            if (!shown) {
                shown = true;
                host.setVisibility(VISIBLE);
                host.setScaleX(0.4f);
                host.setScaleY(0.4f);
                host.setAlpha(0f);
                host.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(320)
                        .setInterpolator(new OvershootInterpolator(1.6f)).start();
            } else if (lastStreak >= 0 && s.streak > lastStreak) {
                pet.cheer();
            }
            // день засчитался прямо сейчас: порадоваться вслух
            if (lastFlags >= 0 && lastFlags != BOTH && s.todayFlags == BOTH) {
                pet.cheer();
                say(LocaleController.getString(R.string.FemboyPetSayDone));
            }
        } else if (shown) {
            shown = false;
            showBubble(false);
            host.animate().cancel();
            host.setVisibility(GONE);
        }
        lastStreak = s.streak;
        lastFlags = s.todayFlags;
        if (pet != null) {
            if (shown && resumed) pet.onResume();
            else pet.onPause();
        }
    }

    private String name() {
        final TLRPC.User user = MessagesController.getInstance(account).getUser(did);
        return user == null ? "" : UserObject.getFirstName(user);
    }

    /** Фраза по обстановке: имя, серия, кто не написал, время суток, просто милое. */
    private String phrase(FemboyStreaks.State s) {
        final ArrayList<String> pool = new ArrayList<>();
        final int stage = FemboyStreaks.stage(s.streak);
        final int left = FemboyStreaks.nextStageAt(s.streak) - s.streak;
        if (stage == FemboyPetView.STAGE_EGG || stage == FemboyPetView.STAGE_HATCHING) {
            pool.add(LocaleController.getString(R.string.FemboyPetSayEggKnock));
            pool.add(LocaleController.getString(R.string.FemboyPetSayEggWobble));
            pool.add(LocaleController.getString(R.string.FemboyPetSayEggWarm));
            if (left > 0) {
                pool.add(LocaleController.formatString(stage == FemboyPetView.STAGE_EGG ? R.string.FemboyPetSayEggSoon : R.string.FemboyPetSayHatchSoon,
                        LocaleController.formatPluralString("FemboyStreakLeft", left)));
            }
        } else {
            if (s.petName != null) {
                final String n = LocaleController.formatString(R.string.FemboyPetSayName, s.petName);
                pool.add(n);
                pool.add(n);
            }
            pool.add(LocaleController.getString(R.string.FemboyPetSayMrr));
            pool.add(LocaleController.getString(R.string.FemboyPetSayPetMe));
            pool.add(LocaleController.getString(R.string.FemboyPetSayFluff));
            pool.add(LocaleController.getString(R.string.FemboyPetSayEars));
            pool.add(LocaleController.getString(R.string.FemboyPetSaySnack));
            pool.add(LocaleController.getString(R.string.FemboyPetSayLove));
            pool.add(LocaleController.getString(R.string.FemboyPetSaySpin));
            pool.add(LocaleController.getString(R.string.FemboyPetSayHold));
            pool.add(LocaleController.getString(R.string.FemboyPetSayPurr));
        }
        pool.add(LocaleController.formatString(R.string.FemboyPetSayStreak, LocaleController.formatPluralString("FemboyStreakDays", s.streak)));
        pool.add(timeOfDay());
        // кто ещё не написал сегодня: это важнее, кладём несколько раз
        final String who;
        if ((s.todayFlags & BOTH) == BOTH) {
            who = LocaleController.getString(R.string.FemboyPetSayDone);
        } else if ((s.todayFlags & FemboyStreaks.ME) != 0) {
            who = LocaleController.formatString(R.string.FemboyPetSayWaitThem, name());
        } else if ((s.todayFlags & FemboyStreaks.THEM) != 0) {
            who = LocaleController.formatString(R.string.FemboyPetSayWaitYou, name());
        } else {
            who = LocaleController.getString(R.string.FemboyPetSayWaitBoth);
        }
        pool.add(who);
        if ((s.todayFlags & BOTH) != BOTH) {
            pool.add(who);
            pool.add(who);
        }
        String pick;
        int guard = 0;
        do {
            pick = pool.get(rnd.nextInt(pool.size()));
        } while (pick.equals(lastPhrase) && ++guard < 8);
        lastPhrase = pick;
        return pick;
    }

    private String timeOfDay() {
        final int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (h >= 5 && h < 12) return LocaleController.getString(R.string.FemboyPetSayMorning);
        if (h >= 12 && h < 18) return LocaleController.getString(R.string.FemboyPetSayDay);
        if (h >= 18 && h < 23) return LocaleController.getString(R.string.FemboyPetSayEvening);
        return LocaleController.getString(R.string.FemboyPetSayNight);
    }

    // ---------------------------------------------------------------- облачко

    private void say(String text) {
        if (!shown || TextUtils.isEmpty(text)) return;
        bubble.text.setText(text);
        bubble.applyColors();
        showBubble(true);
        AndroidUtilities.cancelRunOnUIThread(hideBubble);
        AndroidUtilities.runOnUIThread(hideBubble, 4500);
    }

    private void showBubble(boolean show) {
        if (!show) AndroidUtilities.cancelRunOnUIThread(hideBubble);
        if (show) {
            final boolean was = bubble.getVisibility() == VISIBLE;
            bubble.setVisibility(VISIBLE);
            bubble.animate().cancel();
            if (!was) {
                bubble.setAlpha(0f);
                bubble.setScaleX(0.85f);
                bubble.setScaleY(0.85f);
            }
            bubble.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200)
                    .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).setListener(null).start();
        } else if (bubble.getVisibility() == VISIBLE) {
            bubble.animate().cancel();
            bubble.animate().alpha(0f).scaleX(0.9f).scaleY(0.9f).setDuration(160)
                    .setInterpolator(CubicBezierInterpolator.EASE_OUT).setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            bubble.setVisibility(GONE);
                        }
                    }).start();
        }
    }

    private void closeFromBubble() {
        showBubble(false);
        setHidden(account, did, true);
        BulletinFactory.of(fragment).createSimpleBulletin(R.raw.chats_infotip,
                LocaleController.getString(R.string.FemboyPetFloatHidden),
                LocaleController.getString(R.string.Undo),
                () -> setHidden(account, did, false)).show();
    }

    // ---------------------------------------------------------------- место

    /** Каждый кадр: держать питомца внутри чата и над полем ввода. */
    private void place() {
        if (!shown) return;
        final int size = dp(SIZE);
        final int w = getWidth();
        float bottom = getHeight();
        if (getParent() instanceof View) bottom = ((View) getParent()).getHeight() - getTop();
        final View a = anchor == null ? null : anchor.get();
        if (a != null && a.getVisibility() == VISIBLE && a.isAttachedToWindow() && a.getHeight() > 0) {
            getLocationInWindow(loc);
            a.getLocationInWindow(loc2);
            bottom = Math.min(bottom, loc2[1] - loc[1]);
        }
        minY = dp(8);
        maxY = Math.max(minY, bottom - size - dp(4));
        if (!picked && !xAnimating) {
            final float x = side == 0 ? dp(EDGE) : w - size - dp(EDGE);
            if (host.getTranslationX() != x) host.setTranslationX(x);
        }
        if (!picked) {
            final float y = clamp(bottom - size - dp(fromBottomDp), minY, maxY);
            if (host.getTranslationY() != y) host.setTranslationY(y);
        }
        if (bubble.getVisibility() == VISIBLE) placeBubble();
    }

    private void placeBubble() {
        final int bw = bubble.getWidth(), bh = bubble.getHeight();
        if (bw == 0) return;
        final float hx = host.getTranslationX(), hy = host.getTranslationY();
        final float cx = hx + dp(SIZE) / 2f;
        final boolean below = hy - bh + dp(10) < dp(4);
        final float x = clamp(cx - bw / 2f, dp(8), Math.max(dp(8), getWidth() - bw - dp(8)));
        final float y = below ? hy + dp(SIZE) - dp(6) : hy - bh + dp(10);
        bubble.setPivotX(clamp(cx - x, 0, bw));
        bubble.setPivotY(below ? 0 : bh);
        if (bubble.getTranslationX() != x) bubble.setTranslationX(x);
        if (bubble.getTranslationY() != y) bubble.setTranslationY(y);
        bubble.setTail(cx - x, below);
    }

    // ---------------------------------------------------------------- жест

    /** true = событие забрали себе (питомец в руке). */
    private boolean hostTouch(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                downRawX = ev.getRawX();
                downRawY = ev.getRawY();
                picked = movedPicked = false;
                touching = true;
                // палец на питомце: ни свайп назад, ни список не перехватывают
                requestDisallowInterceptTouchEvent(true);
                AndroidUtilities.runOnUIThread(longPress, longPressMs);
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                final float dx = ev.getRawX() - downRawX, dy = ev.getRawY() - downRawY;
                if (picked) {
                    if (!movedPicked && Math.hypot(dx, dy) > touchSlop) movedPicked = true;
                    if (movedPicked) {
                        host.setTranslationX(clamp(startTx + dx, 0, getWidth() - dp(SIZE)));
                        host.setTranslationY(clamp(startTy + dy, minY, maxY));
                    }
                    return true;
                }
                if (Math.hypot(dx, dy) > touchSlop) AndroidUtilities.cancelRunOnUIThread(longPress);
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                AndroidUtilities.cancelRunOnUIThread(longPress);
                touching = false;
                if (picked) {
                    drop(ev.getActionMasked() == MotionEvent.ACTION_UP && !movedPicked);
                    picked = false;
                    return true;
                }
                break;
            }
        }
        return picked;
    }

    /** Долгое нажатие: питомец в руке, вращение отменяем. */
    private void pick() {
        if (!touching || !shown) return;
        picked = true;
        movedPicked = false;
        startTx = host.getTranslationX();
        startTy = host.getTranslationY();
        host.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        if (pet != null) {
            final long now = SystemClock.uptimeMillis();
            final MotionEvent cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0, 0, 0);
            pet.dispatchTouchEvent(cancel);
            cancel.recycle();
        }
        showBubble(false);
        host.animate().cancel();
        host.animate().scaleX(1.12f).scaleY(1.12f).setDuration(160).setInterpolator(CubicBezierInterpolator.EASE_OUT).start();
    }

    /** Отпустили: на месте = окно питомца, иначе прилипнуть к ближнему краю. */
    private void drop(boolean openSheet) {
        host.animate().cancel();
        host.animate().scaleX(1f).scaleY(1f).setDuration(200).setInterpolator(new OvershootInterpolator(2f)).start();
        if (openSheet) {
            FemboyPetSheet.show(fragment, did);
            return;
        }
        final int size = dp(SIZE);
        final float cx = host.getTranslationX() + size / 2f;
        side = cx < getWidth() / 2f ? 0 : 1;
        final float x = side == 0 ? dp(EDGE) : getWidth() - size - dp(EDGE);
        final float y = host.getTranslationY();
        // отступ от низа считаем от той же границы, что в place()
        final float bottom = maxY + size + dp(4);
        fromBottomDp = Math.max(0, (bottom - size - y) / AndroidUtilities.density);
        xAnimating = true;
        host.animate().translationX(x).setDuration(260).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        xAnimating = false;
                        host.animate().setListener(null);
                    }
                }).start();
        prefs(account).edit().putInt("s" + did, side).putFloat("b" + did, fromBottomDp).apply();
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    // ---------------------------------------------------------------- облачко MD3

    private final class Bubble extends LinearLayout {
        final TextView text;
        final ImageView close;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF rect = new RectF();
        private float tailX;
        private boolean tailTop;

        Bubble(Context context) {
            super(context);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setWillNotDraw(false);
            text = new TextView(context);
            text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            text.setMaxWidth(dp(220));
            text.setLineSpacing(dp(1), 1f);
            addView(text, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0));
            close = new ImageView(context);
            close.setImageResource(R.drawable.femboy_ic_close);
            close.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            close.setPadding(dp(6), dp(6), dp(6), dp(6));
            close.setContentDescription(LocaleController.getString(R.string.FemboyPetFloatHide));
            close.setOnClickListener(v -> closeFromBubble());
            addView(close, LayoutHelper.createLinear(30, 30, Gravity.CENTER_VERTICAL, 4, 0, -6, 0));
            setOnClickListener(v -> showBubble(false));
            setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, tailTop ? dp(8) : 0, view.getWidth(), view.getHeight() - (tailTop ? 0 : dp(8)), dp(18));
                }
            });
            setElevation(dp(3));
            setTail(0, false);
            applyColors();
        }

        void applyColors() {
            FemboyYou.ensureRoles();
            final boolean you = FemboyYou.on();
            paint.setColor(you ? FemboyYou.surfaceContainerHigh : Theme.getColor(Theme.key_chat_inBubble));
            final int onColor = you ? FemboyYou.onSurface : Theme.getColor(Theme.key_chat_messageTextIn);
            text.setTextColor(onColor);
            close.setColorFilter(new PorterDuffColorFilter(Theme.multAlpha(onColor, 0.6f), PorterDuff.Mode.SRC_IN));
            close.setBackground(Theme.createSelectorDrawable(Theme.multAlpha(onColor, 0.12f), Theme.RIPPLE_MASK_CIRCLE_20DP));
            invalidate();
        }

        void setTail(float x, boolean top) {
            if (tailX == x && tailTop == top && getPaddingTop() != 0) return;
            final boolean flip = tailTop != top || getPaddingTop() == 0;
            tailX = x;
            tailTop = top;
            if (flip) {
                setPadding(dp(14), dp(8) + (top ? dp(8) : 0), dp(8), dp(8) + (top ? 0 : dp(8)));
                invalidateOutline();
            }
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final float t = dp(8);
            final float r = dp(18);
            rect.set(0, tailTop ? t : 0, getWidth(), getHeight() - (tailTop ? 0 : t));
            path.rewind();
            path.addRoundRect(rect, r, r, Path.Direction.CW);
            // хвостик к питомцу
            final float x = clamp(tailX, r + t, getWidth() - r - t);
            if (tailTop) {
                path.moveTo(x - t, rect.top + 1);
                path.quadTo(x - dp(2), rect.top - t * 0.2f, x, rect.top - t);
                path.quadTo(x + dp(2), rect.top - t * 0.2f, x + t, rect.top + 1);
            } else {
                path.moveTo(x - t, rect.bottom - 1);
                path.quadTo(x - dp(2), rect.bottom + t * 0.2f, x, rect.bottom + t);
                path.quadTo(x + dp(2), rect.bottom + t * 0.2f, x + t, rect.bottom - 1);
            }
            path.close();
            canvas.drawPath(path, paint);
        }
    }
}
