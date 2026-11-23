package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import org.telegram.femboy.FemboyStreaks;
import org.telegram.femboy.FemboyYou;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;

/**
 * Серия и питомец: тилкайо сверху, имя, стадия, сколько дней и сколько до
 * следующей стадии, кто уже написал сегодня, и кнопка оборвать серию.
 */
public class FemboyPetSheet extends BottomSheet implements NotificationCenter.NotificationCenterDelegate {

    public static void show(BaseFragment fragment, long did) {
        if (fragment == null || fragment.getParentActivity() == null) return;
        final FemboyPetSheet sheet = new FemboyPetSheet(fragment.getParentActivity(), fragment.getCurrentAccount(), did);
        fragment.showDialog(sheet);
    }

    private final int account;
    private final long did;
    private final boolean pet;
    private final FemboyPetView petView;
    private final ImageView placeholder;
    private final FrameLayout petCard;
    private final LinearLayout body;
    private final int text, gray, card;
    private int lastStreak = -1;
    private String lastName;

    public FemboyPetSheet(Context context, int account, long did) {
        super(context, false);
        this.account = account;
        this.did = did;
        this.pet = FemboyStreaks.petEnabled();
        FemboyYou.ensureRoles();
        setApplyTopPadding(false);
        setApplyBottomPadding(false);
        fixNavigationBar();

        text = Theme.getColor(Theme.key_dialogTextBlack);
        gray = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2);
        card = FemboyYou.on() ? FemboyYou.surfaceContainerHigh : Theme.getColor(Theme.key_windowBackgroundGray);

        final LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(8), dp(16), dp(16));

        final View handle = new View(context);
        handle.setBackground(round(Theme.getColor(Theme.key_sheet_scrollUp), dp(2)));
        root.addView(handle, LayoutHelper.createLinear(32, 4, Gravity.CENTER_HORIZONTAL, 0, 6, 0, 14));

        final TLRPC.User user = MessagesController.getInstance(account).getUser(did);
        final TextView title = text(context, LocaleController.formatString(R.string.FemboyStreakSheetTitle,
                user == null ? "" : UserObject.getFirstName(user)), 20, text);
        title.setTypeface(AndroidUtilities.bold());
        title.setSingleLine();
        title.setEllipsize(TextUtils.TruncateAt.END);
        root.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 4, 0, 4, 12));

        // сам тилкайо или яйцо-заглушка, пока серии нет
        petCard = new FrameLayout(context);
        petCard.setClipToOutline(true);
        petView = pet ? new FemboyPetView(context) : null;
        if (petView != null) {
            petCard.addView(petView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        }
        placeholder = new ImageView(context);
        placeholder.setScaleType(ImageView.ScaleType.FIT_CENTER);
        petCard.addView(placeholder, LayoutHelper.createFrame(96, 96, Gravity.CENTER));
        root.addView(petCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, pet ? 260 : 160));

        body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        root.addView(body, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        final ScrollView scroll = new ScrollView(context);
        scroll.addView(root);
        setCustomView(scroll);
        bind();
    }

    @Override
    public void show() {
        super.show();
        NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.updateInterfaces);
        if (petView != null) petView.onResume();
    }

    @Override
    public void dismiss() {
        NotificationCenter.getInstance(account).removeObserver(this, NotificationCenter.updateInterfaces);
        if (petView != null) petView.onPause();
        super.dismiss();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id != NotificationCenter.updateInterfaces) return;
        final int mask = args.length > 0 && args[0] instanceof Integer ? (Integer) args[0] : 0;
        if ((mask & MessagesController.UPDATE_MASK_NAME) == 0) return;
        // перестраивать только если что-то правда поменялось
        if (!signature(FemboyStreaks.state(this.account, did)).equals(lastSignature)) bind();
    }

    private String lastSignature;

    private static String signature(FemboyStreaks.State s) {
        return s.streak + "/" + s.waiting + "/" + s.todayFlags + "/" + s.petName;
    }

    /** Разложить всё заново по свежему состоянию. Тилкайо не пересоздаётся. */
    private void bind() {
        final Context context = getContext();
        final FemboyStreaks.State s = FemboyStreaks.state(account, did);
        lastSignature = signature(s);
        final int streak = s.streak;
        final boolean has = streak >= FemboyStreaks.MIN_SHOWN;
        final int accent = has ? FemboyStreakDrawable.tierColor(streak) : Theme.getColor(Theme.key_featuredStickers_addButton);

        petCard.setBackground(round(ColorUtils.blendARGB(card, accent, 0.16f), dp(28)));
        if (petView != null && has) {
            petView.setVisibility(View.VISIBLE);
            petView.setStage(FemboyStreaks.stage(streak));
            petView.setAccent(accent);
            placeholder.setVisibility(View.GONE);
            // новый день серии или новое имя: порадоваться
            if (lastStreak >= 0 && (streak > lastStreak || !TextUtils.equals(lastName, s.petName))) {
                petView.cheer();
            }
        } else {
            if (petView != null) petView.setVisibility(View.GONE);
            placeholder.setVisibility(View.VISIBLE);
            if (has) {
                placeholder.setImageDrawable(new FemboyStreakDrawable.Icon(streak));
                placeholder.clearColorFilter();
            } else {
                placeholder.setImageResource(R.drawable.femboy_ic_egg);
                placeholder.setColorFilter(new PorterDuffColorFilter(Theme.multAlpha(text, 0.35f), PorterDuff.Mode.SRC_IN));
            }
        }
        lastStreak = streak;
        lastName = s.petName;

        body.removeAllViews();

        if (has && pet) {
            // имя и стадия
            final LinearLayout nameRow = new LinearLayout(context);
            nameRow.setOrientation(LinearLayout.HORIZONTAL);
            nameRow.setGravity(Gravity.CENTER_VERTICAL);
            final LinearLayout names = new LinearLayout(context);
            names.setOrientation(LinearLayout.VERTICAL);
            final TextView name = text(context, s.petName != null ? s.petName : LocaleController.getString(R.string.FemboyStreakNoName), 22,
                    s.petName != null ? text : gray);
            name.setTypeface(AndroidUtilities.bold());
            name.setSingleLine();
            name.setEllipsize(TextUtils.TruncateAt.END);
            names.addView(name);
            names.addView(text(context, stageTitle(streak), 15, accent), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
            nameRow.addView(names, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
            final ImageView rename = new ImageView(context);
            rename.setImageResource(R.drawable.femboy_ic_edit);
            rename.setScaleType(ImageView.ScaleType.CENTER);
            final int onTonal = FemboyYou.on() ? FemboyYou.onSecondaryContainer : text;
            rename.setColorFilter(new PorterDuffColorFilter(onTonal, PorterDuff.Mode.SRC_IN));
            rename.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(24),
                    FemboyYou.on() ? FemboyYou.secondaryContainer : card, Theme.multAlpha(onTonal, 0.15f)));
            rename.setContentDescription(LocaleController.getString(R.string.FemboyStreakRename));
            rename.setOnClickListener(v -> askName(s.petName));
            nameRow.addView(rename, LayoutHelper.createLinear(48, 48));
            body.addView(nameRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 4, 16, 0, 12));
        } else if (!has) {
            final TextView head = text(context, LocaleController.getString(R.string.FemboyStreakNoEggTitle), 20, text);
            head.setTypeface(AndroidUtilities.bold());
            body.addView(head, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 4, 16, 4, 4));
            final TextView how = text(context, LocaleController.getString(R.string.FemboyStreakNoEggText), 15, gray);
            how.setLineSpacing(dp(2), 1f);
            body.addView(how, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 4, 0, 4, 12));
        } else {
            body.addView(new View(context), LayoutHelper.createLinear(1, 12));
        }

        // сегменты: серия и сегодня
        final LinearLayout first = segment(context, true, false);
        final LinearLayout streakRow = new LinearLayout(context);
        streakRow.setOrientation(LinearLayout.HORIZONTAL);
        streakRow.setGravity(Gravity.CENTER_VERTICAL);
        if (has) {
            final ImageView flame = new ImageView(context);
            flame.setImageDrawable(new FemboyStreakDrawable.Icon(streak));
            flame.setAlpha(s.waiting ? 0.45f : 1f);
            streakRow.addView(flame, LayoutHelper.createLinear(24, 28, 0, 0, 12, 0));
        }
        final TextView days = text(context, has
                ? LocaleController.formatPluralString("FemboyStreakDays", streak)
                : LocaleController.formatString(R.string.FemboyStreakNoEggProgress, streak, FemboyStreaks.MIN_SHOWN), 18, text);
        days.setTypeface(AndroidUtilities.bold());
        streakRow.addView(days, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        first.addView(streakRow);

        final int next = FemboyStreaks.nextStageAt(streak);
        if (next > 0 && (pet || !has)) {
            final int from = FemboyStreaks.stageFrom(streak);
            final float progress = next == from ? 1f : (streak - from) / (float) (next - from);
            first.addView(new Progress(context, progress, accent, Theme.multAlpha(accent, 0.22f)),
                    LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 8, 0, 12, 0, 8));
            first.addView(text(context, LocaleController.formatString(R.string.FemboyStreakNext,
                    LocaleController.formatPluralString("FemboyStreakLeft", next - streak)), 14, gray));
        } else if (has && pet) {
            first.addView(text(context, LocaleController.getString(R.string.FemboyStreakMax), 14, gray),
                    LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 6, 0, 0));
        }
        body.addView(first, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // показ в переписке теперь в настройках FemboyGram, здесь только серия
        final boolean floatRow = false;
        final LinearLayout second = segment(context, false, !floatRow);
        final TextView todayTitle = text(context, LocaleController.getString(R.string.FemboyStreakToday), 14, accent);
        todayTitle.setTypeface(AndroidUtilities.bold());
        second.addView(todayTitle);
        final LinearLayout chips = new LinearLayout(context);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.addView(chip(context, LocaleController.getString(R.string.FemboyStreakYou), (s.todayFlags & FemboyStreaks.ME) != 0, accent),
                LayoutHelper.createLinear(0, 36, 1f, 0, 0, 4, 0));
        chips.addView(chip(context, LocaleController.getString(R.string.FemboyStreakThem), (s.todayFlags & FemboyStreaks.THEM) != 0, accent),
                LayoutHelper.createLinear(0, 36, 1f, 4, 0, 0, 0));
        second.addView(chips, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36, 0, 10, 0, 0));
        if (streak > 0 && s.waiting) {
            second.addView(text(context, LocaleController.getString(R.string.FemboyStreakWaiting), 14, gray),
                    LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 10, 0, 0));
        }
        body.addView(second, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        // тилкайо в самой переписке: спрятать или вернуть
        if (floatRow) {
            final LinearLayout third = segment(context, false, true);
            third.setOrientation(LinearLayout.HORIZONTAL);
            third.setGravity(Gravity.CENTER_VERTICAL);
            final LinearLayout texts = new LinearLayout(context);
            texts.setOrientation(LinearLayout.VERTICAL);
            final TextView inChat = text(context, LocaleController.getString(R.string.FemboyPetShowInChat), 16, text);
            texts.addView(inChat);
            final TextView inChatInfo = text(context, LocaleController.getString(R.string.FemboyPetShowInChatInfo), 13, gray);
            inChatInfo.setLineSpacing(dp(1), 1f);
            texts.addView(inChatInfo, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
            third.addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 0, 0, 12, 0));
            final org.telegram.ui.Components.Switch toggle = new org.telegram.ui.Components.Switch(context);
            toggle.setChecked(!FemboyPetFloat.isHidden(account, did), false);
            third.addView(toggle, LayoutHelper.createLinear(37, 40));
            third.setOnClickListener(v -> {
                final boolean show = FemboyPetFloat.isHidden(account, did);
                toggle.setChecked(show, true);
                FemboyPetFloat.setHidden(account, did, !show);
            });
            third.setBackground(new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{
                    third.getBackground(), Theme.createSelectorDrawable(Theme.multAlpha(text, 0.1f), Theme.RIPPLE_MASK_ALL)}));
            third.setClipToOutline(true);
            body.addView(third, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
        }

        // оборвать серию: красная тональная кнопка
        if (streak > 0) {
            final int red = Theme.getColor(Theme.key_text_RedBold);
            final TextView breakButton = text(context, LocaleController.getString(R.string.FemboyStreakBreak), 15, red);
            breakButton.setTypeface(AndroidUtilities.bold());
            breakButton.setGravity(Gravity.CENTER);
            breakButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(24), Theme.multAlpha(red, 0.12f), Theme.multAlpha(red, 0.2f)));
            breakButton.setOnClickListener(v -> confirmBreak());
            body.addView(breakButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 16, 0, 0));
        }
    }

    private String stageTitle(int streak) {
        final int stage = FemboyStreaks.stage(streak);
        if (stage == FemboyPetView.STAGE_ADULT) return LocaleController.getString(R.string.FemboyStreakStageAdult);
        if (stage == FemboyPetView.STAGE_BABY) return LocaleController.getString(R.string.FemboyStreakStageBaby);
        if (stage == FemboyPetView.STAGE_HATCHING) return LocaleController.getString(R.string.FemboyStreakStageHatching);
        return LocaleController.getString(R.string.FemboyStreakStageEgg);
    }

    private void confirmBreak() {
        new AlertDialog.Builder(getContext())
                .setTitle(LocaleController.getString(R.string.FemboyStreakBreakTitle))
                .setMessage(LocaleController.getString(pet ? R.string.FemboyStreakBreakText : R.string.FemboyStreakBreakTextNoPet))
                .setPositiveButton(LocaleController.getString(R.string.FemboyStreakBreakOk),
                        (d, w) -> FemboyStreaks.breakStreak(account, did, this::bind))
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .makeRed(AlertDialog.BUTTON_POSITIVE)
                .show();
    }

    /** Окно с полем для имени. Пустое не принимается. */
    private void askName(String initial) {
        final Context context = getContext();
        final FrameLayout frame = new FrameLayout(context);
        final EditTextBoldCursor edit = new EditTextBoldCursor(context);
        edit.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        edit.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        edit.setHintTextColor(Theme.getColor(Theme.key_dialogTextGray3));
        edit.setHint(LocaleController.getString(R.string.FemboyStreakNameHint));
        edit.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
        edit.setCursorSize(dp(20));
        edit.setSingleLine(true);
        edit.setFilters(new InputFilter[]{new InputFilter.LengthFilter(24)});
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        edit.setImeOptions(EditorInfo.IME_ACTION_DONE);
        edit.setPadding(dp(16), dp(12), dp(16), dp(12));
        final GradientDrawable back = new GradientDrawable();
        back.setCornerRadius(dp(16));
        back.setColor(Theme.multAlpha(Theme.getColor(Theme.key_dialogTextBlack), 0.06f));
        edit.setBackground(back);
        if (initial != null) {
            edit.setText(initial);
            edit.setSelection(edit.length());
        }
        frame.addView(edit, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 24, 4, 24, 4));

        final AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(LocaleController.getString(R.string.FemboyStreakRename))
                .setView(frame)
                .setNegativeButton(LocaleController.getString(R.string.Cancel), (d, w) -> d.dismiss())
                .setPositiveButton(LocaleController.getString(R.string.OK), null)
                .create();
        dialog.setDismissDialogByButtons(false);
        final Utilities.Callback<String> done = name -> FemboyStreaks.setPetName(account, did, name, () -> {
            bind();
            if (petView != null) petView.cheer();
        });
        final Runnable submit = () -> {
            final String name = edit.getText() == null ? "" : edit.getText().toString().trim();
            if (TextUtils.isEmpty(name)) {
                AndroidUtilities.shakeViewSpring(edit, 6);
                return;
            }
            AndroidUtilities.hideKeyboard(edit);
            dialog.dismiss();
            done.run(name);
        };
        dialog.setPositiveButtonListener((d, w) -> submit.run());
        edit.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit.run();
                return true;
            }
            return false;
        });
        dialog.setOnShowListener(d -> AndroidUtilities.runOnUIThread(() -> {
            edit.requestFocus();
            AndroidUtilities.showKeyboard(edit);
        }, 150));
        dialog.show();
    }

    // ---------------------------------------------------------------- куски

    /** Сегмент списка: большие углы снаружи, маленькие на стыке. */
    private LinearLayout segment(Context context, boolean top, boolean bottom) {
        final LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(14), dp(16), dp(14));
        final float big = dp(20), small = dp(4);
        final float t = top ? big : small, b = bottom ? big : small;
        final GradientDrawable d = new GradientDrawable();
        d.setColor(card);
        d.setCornerRadii(new float[]{t, t, t, t, b, b, b, b});
        box.setBackground(d);
        return box;
    }

    private View chip(Context context, String label, boolean done, int accent) {
        final TextView view = text(context, label + (done ? "  ✓" : "  ✗"), 14,
                done ? (FemboyYou.on() ? FemboyYou.onSecondaryContainer : text) : gray);
        view.setGravity(Gravity.CENTER);
        view.setSingleLine();
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setTypeface(AndroidUtilities.bold());
        if (done) {
            view.setBackground(round(ColorUtils.blendARGB(card, accent, 0.28f), dp(18)));
        } else {
            final GradientDrawable d = new GradientDrawable();
            d.setCornerRadius(dp(18));
            d.setStroke(dp(1), Theme.multAlpha(text, 0.18f));
            view.setBackground(d);
        }
        return view;
    }

    /** Линейный прогресс MD3: полоса, зазор, дорожка. */
    private static final class Progress extends View {
        private final float value;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int active, track;
        private final RectF rect = new RectF();

        Progress(Context context, float value, int active, int track) {
            super(context);
            this.value = Math.max(0f, Math.min(1f, value));
            this.active = active;
            this.track = track;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final float h = getHeight(), w = getWidth(), r = h / 2f, gap = dp(4);
            final float split = Math.max(h, w * value);
            if (value < 1f) {
                paint.setColor(track);
                rect.set(Math.min(w - h, split + gap), 0, w, h);
                canvas.drawRoundRect(rect, r, r, paint);
            }
            if (value > 0f) {
                paint.setColor(active);
                rect.set(0, 0, value >= 1f ? w : split, h);
                canvas.drawRoundRect(rect, r, r, paint);
            }
        }
    }

    private static TextView text(Context context, CharSequence value, int sp, int color) {
        final TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sp);
        view.setTextColor(color);
        return view;
    }

    private static GradientDrawable round(int color, int radius) {
        final GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }
}
