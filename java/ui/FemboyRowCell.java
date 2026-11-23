package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.Switch;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

/**
 * Строка настроек FemboyGram в духе MD3: значок без подложки, название,
 * короткое пояснение в одну-две строки и, если надо, переключатель.
 */
public class FemboyRowCell extends LinearLayout implements Theme.Colorable {

    private final Theme.ResourcesProvider resourcesProvider;
    private final ImageView iconView;
    private final TextView titleView;
    private final TextView subtitleView;
    private final Switch switchView;
    /** Кнопка справа за вертикальным разделителем (например, корзина). */
    private final ImageView actionView;
    private final View actionDivider;
    private int lastId = -1;

    public FemboyRowCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setMinimumHeight(dp(56));
        setPadding(0, dp(12), 0, dp(12));

        iconView = new ImageView(context);
        iconView.setScaleType(ImageView.ScaleType.CENTER);

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(VERTICAL);

        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleView.setSingleLine();
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        subtitleView = new TextView(context);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitleView.setMaxLines(2);
        subtitleView.setEllipsize(TextUtils.TruncateAt.END);
        subtitleView.setLineSpacing(dp(1), 1f);
        texts.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        actionDivider = new View(context);
        actionView = new ImageView(context);
        actionView.setScaleType(ImageView.ScaleType.CENTER);
        actionView.setVisibility(GONE);
        actionDivider.setVisibility(GONE);

        switchView = new Switch(context, resourcesProvider);
        switchView.setColors(Theme.key_switchTrack, Theme.key_switchTrackChecked, Theme.key_windowBackgroundWhite, Theme.key_windowBackgroundWhite);
        switchView.setFocusable(false);
        switchView.setClickable(false);

        if (LocaleController.isRTL) {
            addView(switchView, LayoutHelper.createLinear(52, 32, Gravity.CENTER_VERTICAL, 16, 0, 0, 0));
            addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL, 16, 0, 16, 0));
            addView(iconView, LayoutHelper.createLinear(24, 24, Gravity.CENTER_VERTICAL, 0, 0, 16, 0));
        } else {
            // отступы списка MD3: 16dp от края, 16dp от значка до текста
            addView(iconView, LayoutHelper.createLinear(24, 24, Gravity.CENTER_VERTICAL, 16, 0, 0, 0));
            addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL, 16, 0, 16, 0));
            addView(switchView, LayoutHelper.createLinear(52, 32, Gravity.CENTER_VERTICAL, 0, 0, 16, 0));
            addView(actionDivider, LayoutHelper.createLinear(1, 32, Gravity.CENTER_VERTICAL, 0, 0, 4, 0));
            addView(actionView, LayoutHelper.createLinear(48, 48, Gravity.CENTER_VERTICAL, 0, 0, 8, 0));
        }
        updateColors();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // список отдаёт строке ширину «по содержимому»: без этого строка, её
        // нажатие и разделитель кончаются там, где кончается текст
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), heightMeasureSpec);
    }

    public void set(int id, int icon, CharSequence title, CharSequence subtitle, boolean hasSwitch, boolean checked) {
        final boolean animated = id == lastId;
        lastId = id;
        if (icon != 0) {
            iconView.setImageResource(icon);
            iconView.setVisibility(VISIBLE);
        } else {
            iconView.setVisibility(GONE);
        }
        titleView.setText(title);
        subtitleView.setText(subtitle);
        subtitleView.setVisibility(TextUtils.isEmpty(subtitle) ? GONE : VISIBLE);
        switchView.setVisibility(hasSwitch ? VISIBLE : GONE);
        if (hasSwitch) {
            switchView.setChecked(checked, animated);
        }
    }

    /** Значок кнопки справа; 0 — кнопки нет. Нажатие по ней ловит экран по координате: правее {@link #actionEdge()}. */
    public void setAction(int icon) {
        actionView.setVisibility(icon != 0 ? VISIBLE : GONE);
        actionDivider.setVisibility(icon != 0 ? VISIBLE : GONE);
        if (icon != 0) {
            actionView.setImageResource(icon);
            actionView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon, resourcesProvider), PorterDuff.Mode.SRC_IN));
            actionDivider.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider) & 0x40FFFFFF);
        }
    }

    /** Где начинается кнопка справа, в точках от левого края строки. */
    public static int actionEdge(View row) {
        return row.getWidth() - org.telegram.messenger.AndroidUtilities.dp(62);
    }

    public void setChecked(boolean checked) {
        switchView.setChecked(checked, true);
    }

    @Override
    public void updateColors() {
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
        iconView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon, resourcesProvider), PorterDuff.Mode.SRC_IN));
    }

    public static class Factory extends UItem.UItemFactory<FemboyRowCell> {
        static { setup(new Factory()); }

        @Override
        public FemboyRowCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            return new FemboyRowCell(context, resourcesProvider);
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            ((FemboyRowCell) view).set(item.id, item.iconResId, item.text, item.subtext, item.intValue == 1, item.checked);
            ((FemboyRowCell) view).setAction(item.object2 instanceof Integer ? (Integer) item.object2 : 0);
        }

        /** Строка-переход: значок, название, пояснение. */
        public static UItem of(int id, int icon, CharSequence title, CharSequence subtitle) {
            final UItem item = UItem.ofFactory(Factory.class);
            item.id = id;
            item.iconResId = icon;
            item.text = title;
            item.subtext = subtitle;
            return item;
        }

        /** Строка-переход с кнопкой справа за разделителем. */
        public static UItem withAction(int id, int icon, CharSequence title, CharSequence subtitle, int action) {
            final UItem item = of(id, icon, title, subtitle);
            item.object2 = action;
            return item;
        }

        /** Строка с переключателем. */
        public static UItem check(int id, int icon, CharSequence title, CharSequence subtitle, boolean checked) {
            final UItem item = of(id, icon, title, subtitle);
            item.intValue = 1;
            item.checked = checked;
            return item;
        }

        @Override
        public boolean equals(UItem a, UItem b) {
            return a.id == b.id;
        }

        @Override
        public boolean contentsEquals(UItem a, UItem b) {
            return a.checked == b.checked && a.iconResId == b.iconResId && java.util.Objects.equals(a.object2, b.object2)
                    && TextUtils.equals(a.text, b.text) && TextUtils.equals(a.subtext, b.subtext);
        }
    }
}
