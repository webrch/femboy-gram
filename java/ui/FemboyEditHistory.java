package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.femboy.FemboyEdits;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

/** Every version of an edited message, the one shown now first, each with its time. */
public final class FemboyEditHistory {

    private FemboyEditHistory() {}

    public static void show(Context context, MessageObject message) {
        if (context == null || message == null) return;
        ArrayList<FemboyEdits.Version> versions = FemboyEdits.history(message);
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(20), dp(4), dp(20), dp(8));
        String now = message.messageOwner.message == null ? "" : message.messageOwner.message;
        list.addView(card(context, LocaleController.getString(R.string.FemboyEditNow) + "  ·  " + when(message.messageOwner.edit_date), now, true));
        for (int i = versions.size() - 1; i >= 0; i--) {
            FemboyEdits.Version v = versions.get(i);
            list.addView(card(context, when(v.date), v.text, false));
        }
        ScrollView scroll = new ScrollView(context);
        scroll.addView(list);
        new AlertDialog.Builder(context)
                .setTitle(LocaleController.getString(R.string.FemboyEditHistory))
                .setView(scroll)
                .setPositiveButton(LocaleController.getString(R.string.Close), null)
                .show();
    }

    private static String when(int date) {
        if (date <= 0) return "";
        return LocaleController.formatDateChat(date) + ", " + LocaleController.getInstance().getFormatterDay().format(date * 1000L);
    }

    private static LinearLayout card(Context context, String head, String text, boolean current) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(10), dp(14), dp(12));
        GradientDrawable back = new GradientDrawable();
        back.setCornerRadius(dp(16));
        int base = Theme.getColor(Theme.key_dialogBackground);
        int ink = Theme.getColor(Theme.key_dialogTextBlack);
        back.setColor(current ? Theme.getColor(Theme.key_featuredStickers_addButton) & 0x33FFFFFF | 0 : (ink & 0x00FFFFFF) | 0x0F000000);
        card.setBackground(back);
        TextView top = new TextView(context);
        top.setText(head);
        top.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        top.setTextColor(Theme.getColor(Theme.key_dialogTextGray3));
        card.addView(top);
        TextView body = new TextView(context);
        body.setText(text.length() > 0 ? text : LocaleController.getString(R.string.FemboyEditEmpty));
        body.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        body.setTextColor(ink);
        body.setTextIsSelectable(true);
        body.setPadding(0, dp(4), 0, 0);
        card.addView(body);
        LinearLayout.LayoutParams place = LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8);
        card.setLayoutParams(place);
        return card;
    }
}
