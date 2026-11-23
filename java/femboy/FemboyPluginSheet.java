package org.telegram.femboy;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Switch;

/**
 * Установка плагина нижним листом: значок и имя сверху, описание
 * сворачивается, разрешения списком со значками, внизу две кнопки. Вместо
 * простыни текста в окошке посреди экрана.
 */
public class FemboyPluginSheet extends BottomSheet {

    public interface Actions {
        void install(boolean startNow);
        void viewSource();
        void cancelled();
    }

    private boolean done;

    public FemboyPluginSheet(Context context, FemboyPlugins.Plugin plugin, FemboyPlugins.Plugin existing, Actions actions) {
        super(context, false);
        FemboyYou.ensureRoles();
        setApplyTopPadding(false);
        setApplyBottomPadding(false);
        fixNavigationBar();

        final int text = Theme.getColor(Theme.key_dialogTextBlack);
        final int gray = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2);
        final int card = FemboyYou.on() ? FemboyYou.surfaceContainerHigh : Theme.getColor(Theme.key_windowBackgroundGray);

        final LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(8), dp(20), dp(16));

        // ручка
        final View handle = new View(context);
        handle.setBackground(round(Theme.getColor(Theme.key_sheet_scrollUp), dp(2)));
        root.addView(handle, LayoutHelper.createLinear(32, 4, Gravity.CENTER_HORIZONTAL, 0, 6, 0, 18));

        // шапка: значок, имя, версия и автор
        final LinearLayout head = new LinearLayout(context);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        final ImageView icon = new ImageView(context);
        final Bitmap bitmap = plugin.icon();
        if (bitmap != null) {
            icon.setImageBitmap(bitmap);
            icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
            icon.setClipToOutline(true);
            icon.setBackground(round(card, dp(16)));
        } else {
            icon.setImageResource(R.drawable.femboy_ic_extension);
            icon.setScaleType(ImageView.ScaleType.CENTER);
            icon.setColorFilter(new PorterDuffColorFilter(FemboyYou.on() ? FemboyYou.onPrimaryContainer : text, PorterDuff.Mode.SRC_IN));
            icon.setBackground(round(FemboyYou.on() ? FemboyYou.primaryContainer : card, dp(16)));
        }
        head.addView(icon, LayoutHelper.createLinear(56, 56));
        final LinearLayout names = new LinearLayout(context);
        names.setOrientation(LinearLayout.VERTICAL);
        final TextView name = text(context, plugin.name, 20, text);
        name.setTypeface(AndroidUtilities.bold());
        name.setSingleLine();
        name.setEllipsize(TextUtils.TruncateAt.END);
        names.addView(name);
        String sub = plugin.version;
        if (plugin.author != null && plugin.author.length() > 0) {
            sub = sub + " · " + plugin.author;
        }
        final TextView by = text(context, sub, 14, gray);
        if (plugin.author != null && plugin.author.startsWith("@")) {
            by.setOnClickListener(v -> org.telegram.messenger.browser.Browser.openUrl(context, "https://t.me/" + plugin.author.substring(1)));
        }
        names.addView(by, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
        head.addView(names, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 16, 0, 0, 0));
        root.addView(head);

        // уже стоит: обновление или та же версия
        if (existing != null) {
            final TextView note = text(context, existing.version.equals(plugin.version)
                    ? LocaleController.formatString(R.string.FemboyPluginSame, existing.version)
                    : LocaleController.formatString(R.string.FemboyPluginUpdate, existing.version, plugin.version), 14,
                    FemboyYou.on() ? FemboyYou.onSecondaryContainer : text);
            note.setPadding(dp(14), dp(10), dp(14), dp(10));
            note.setBackground(round(FemboyYou.on() ? FemboyYou.secondaryContainer : card, dp(12)));
            root.addView(note, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 16, 0, 0));
        }

        // описание, свёрнутое в три строки
        if (plugin.description != null && plugin.description.length() > 0) {
            final LinearLayout box = card(context, card);
            final TextView title = text(context, LocaleController.getString(R.string.FemboyPluginAbout), 14, Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
            title.setTypeface(AndroidUtilities.bold());
            box.addView(title);
            final TextView body = text(context, plugin.description, 15, text);
            body.setMaxLines(3);
            body.setEllipsize(TextUtils.TruncateAt.END);
            body.setLineSpacing(dp(2), 1f);
            box.addView(body, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 6, 0, 0));
            final TextView more = text(context, LocaleController.getString(R.string.FemboyPluginMore), 14, Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
            more.setTypeface(AndroidUtilities.bold());
            box.addView(more, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 6, 0, 0));
            box.setOnClickListener(v -> {
                final boolean open = body.getMaxLines() == 3;
                body.setMaxLines(open ? Integer.MAX_VALUE : 3);
                more.setText(LocaleController.getString(open ? R.string.FemboyPluginLess : R.string.FemboyPluginMore));
            });
            // короткое описание сворачивать нечего
            body.post(() -> {
                if (body.getLayout() != null && body.getLayout().getEllipsisCount(body.getLineCount() - 1) == 0 && body.getLineCount() <= 3) {
                    more.setVisibility(View.GONE);
                    box.setClickable(false);
                }
            });
            root.addView(box, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 16, 0, 0));
        }

        // что плагин заявляет
        final LinearLayout perms = card(context, card);
        final TextView permsTitle = text(context, LocaleController.getString(R.string.FemboyPluginAsks), 14, Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
        permsTitle.setTypeface(AndroidUtilities.bold());
        perms.addView(permsTitle);
        if (plugin.permissions.isEmpty()) {
            perms.addView(permission(context, R.drawable.femboy_ic_tune, LocaleController.getString(R.string.FemboyPluginPermNone), text));
        } else {
            for (String p : plugin.permissions) {
                perms.addView(permission(context, permissionIcon(p), FemboyPlugins.permissionName(p), text));
            }
        }
        if (plugin.usesHooks()) {
            perms.addView(permission(context, R.drawable.femboy_ic_code, LocaleController.getString(R.string.FemboyPluginHooks),
                    Theme.getColor(Theme.key_text_RedRegular)));
        }
        root.addView(perms, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 0));

        final TextView warn = text(context, LocaleController.getString(R.string.FemboyPluginWarn), 13, gray);
        root.addView(warn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 4, 10, 4, 0));

        // включить сразу (снято по умолчанию: плагин видишь впервые)
        final boolean[] startNow = {false};
        final LinearLayout startRow = new LinearLayout(context);
        startRow.setOrientation(LinearLayout.HORIZONTAL);
        startRow.setGravity(Gravity.CENTER_VERTICAL);
        startRow.setPadding(dp(16), dp(14), dp(16), dp(14));
        startRow.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(16), card, Theme.getColor(Theme.key_listSelector)));
        startRow.addView(text(context, LocaleController.getString(R.string.FemboyPluginEnableAfter), 16, text),
                LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        final Switch sw = new Switch(context);
        sw.setColors(Theme.key_switchTrack, Theme.key_switchTrackChecked, Theme.key_windowBackgroundWhite, Theme.key_windowBackgroundWhite);
        startRow.addView(sw, LayoutHelper.createLinear(37, 20));
        startRow.setOnClickListener(v -> {
            startNow[0] = !startNow[0];
            sw.setChecked(startNow[0], true);
        });
        root.addView(startRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 0));

        // кнопки
        final LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        final TextView source = button(context, LocaleController.getString(R.string.FemboyPluginCode),
                FemboyYou.on() ? FemboyYou.secondaryContainer : card, FemboyYou.on() ? FemboyYou.onSecondaryContainer : text);
        source.setOnClickListener(v -> actions.viewSource());
        buttons.addView(source, LayoutHelper.createLinear(0, 48, 1f, 0, 0, 6, 0));
        final TextView install = button(context, LocaleController.getString(existing != null
                        ? R.string.FemboyPluginUpdateOk : R.string.FemboyPluginInstallOk),
                Theme.getColor(Theme.key_featuredStickers_addButton), Theme.getColor(Theme.key_featuredStickers_buttonText));
        install.setOnClickListener(v -> {
            done = true;
            dismiss();
            actions.install(startNow[0]);
        });
        buttons.addView(install, LayoutHelper.createLinear(0, 48, 1f, 6, 0, 0, 0));
        root.addView(buttons, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 16, 0, 0));

        final ScrollView scroll = new ScrollView(context);
        scroll.addView(root);
        setCustomView(scroll);
        setOnDismissListener(d -> {
            if (!done) {
                actions.cancelled();
            }
        });
    }

    private static int permissionIcon(String key) {
        switch (key) {
            case "read_chats": return R.drawable.femboy_ic_chat_bubble;
            case "send_messages": return R.drawable.femboy_ic_campaign;
            case "edit_messages": return R.drawable.femboy_ic_format_paint;
            case "delete_messages": return R.drawable.femboy_ic_visibility_off;
            case "change_profile": return R.drawable.femboy_ic_account_circle;
            case "ui": return R.drawable.femboy_ic_palette;
        }
        return R.drawable.femboy_ic_tune;
    }

    private static LinearLayout card(Context context, int color) {
        final LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(14), dp(16), dp(14));
        box.setBackground(round(color, dp(16)));
        return box;
    }

    private static View permission(Context context, int icon, CharSequence label, int color) {
        final LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, 0);
        final ImageView image = new ImageView(context);
        image.setImageResource(icon);
        image.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
        row.addView(image, LayoutHelper.createLinear(22, 22));
        row.addView(text(context, label, 15, color), LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 14, 0, 0, 0));
        return row;
    }

    private static TextView text(Context context, CharSequence value, int sp, int color) {
        final TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sp);
        view.setTextColor(color);
        return view;
    }

    private static TextView button(Context context, CharSequence value, int bg, int fg) {
        final TextView view = text(context, value, 15, fg);
        view.setTypeface(AndroidUtilities.bold());
        view.setGravity(Gravity.CENTER);
        view.setSingleLine();
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setPadding(dp(12), 0, dp(12), 0);
        view.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(24), bg, Theme.multAlpha(fg, 0.15f)));
        return view;
    }

    private static GradientDrawable round(int color, int radius) {
        final GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }
}
