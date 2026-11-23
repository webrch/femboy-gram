package com.exteragram.messenger.utils.ui;

import android.content.Context;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.RadioColorCell;

/** exteraGram's list dialogs, as plugins call them. */
public abstract class PopupUtils {
    public interface OnItemClickListener { void onClick(int which); }
    public interface OnMultiSelectListener { void onClick(boolean[] checked); }

    public static void showDialog(CharSequence[] items, String title, int current, Context context, OnItemClickListener listener) {
        showDialog(items, null, title, current, context, listener, null, true);
    }

    public static void showDialog(CharSequence[] items, int[] icons, String title, int current, Context context, OnItemClickListener listener) {
        showDialog(items, icons, title, current, context, listener, null, true);
    }

    public static void showDialog(CharSequence[] items, int[] icons, String title, int current, Context context, OnItemClickListener listener,
                                  Theme.ResourcesProvider resources) {
        showDialog(items, icons, title, current, context, listener, resources, true);
    }

    public static void showDialog(CharSequence[] items, int[] icons, String title, int current, Context context, final OnItemClickListener listener,
                                  Theme.ResourcesProvider resources, boolean radio) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(context, resources);
        builder.setTitle(title);
        if (radio) {
            LinearLayout list = new LinearLayout(context);
            list.setOrientation(LinearLayout.VERTICAL);
            builder.setView(list);
            for (int i = 0; i < items.length; i++) {
                RadioColorCell cell = new RadioColorCell(context);
                cell.setPadding(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4), 0);
                cell.setTag(i);
                cell.setCheckColor(Theme.getColor(Theme.key_radioBackground, resources), Theme.getColor(Theme.key_dialogRadioBackgroundChecked, resources));
                cell.setTextAndValue(items[i], current == i);
                cell.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 2));
                list.addView(cell);
                cell.setOnClickListener(v -> {
                    builder.getDismissRunnable().run();
                    listener.onClick((Integer) v.getTag());
                });
            }
        } else if (icons != null) {
            builder.setItems(items, icons, (d, which) -> listener.onClick(which));
        } else {
            builder.setItems(items, (d, which) -> listener.onClick(which));
        }
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.show();
    }
}
