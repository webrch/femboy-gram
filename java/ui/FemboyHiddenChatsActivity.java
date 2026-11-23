package org.telegram.ui;

import android.view.View;
import android.widget.EditText;

import org.telegram.femboy.FemboyHiddenChats;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

/** Settings for chats hidden from the main list. */
public class FemboyHiddenChatsActivity extends UniversalFragment {

    private static final int ID_PIN = 1;
    private static final int ID_UNHIDE = 2;
    private static final int ID_CONCEAL = 3;

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(R.string.FemboyHiddenTitle);
    }

    @Override
    public View createView(android.content.Context context) {
        final View view = super.createView(context);
        listView.setSections();
        return view;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asShadow(LocaleController.getString(R.string.FemboyHiddenAbout)));
        items.add(UItem.asButton(ID_PIN, LocaleController.getString(
                FemboyHiddenChats.hasPin() ? R.string.FemboyHiddenChangePin : R.string.FemboyHiddenSetPin)));
        items.add(UItem.asButton(ID_UNHIDE, LocaleController.getString(R.string.FemboyHiddenUnhideAll)));
        items.add(UItem.asButton(ID_CONCEAL, LocaleController.getString(R.string.FemboyHiddenConceal)));
        items.add(UItem.asShadow(LocaleController.getString(R.string.FemboyHiddenHow)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_PIN) {
            final EditText input = new EditText(getContext());
            input.setSingleLine(true);
            input.setText(FemboyHiddenChats.getPin());
            input.setSelection(input.getText().length());
            final AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            builder.setTitle(LocaleController.getString(R.string.FemboyHiddenSetPin));
            builder.setView(input);
            builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
            builder.setPositiveButton(LocaleController.getString(R.string.Save), (dialog, which) -> {
                FemboyHiddenChats.setPin(input.getText().toString().trim());
                listView.adapter.update(true);
            });
            builder.show();
        } else if (item.id == ID_UNHIDE) {
            FemboyHiddenChats.unhideAll();
            reloadDialogs();
            listView.adapter.update(true);
        } else if (item.id == ID_CONCEAL) {
            FemboyHiddenChats.conceal();
            reloadDialogs();
        }
    }

    private void reloadDialogs() {
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogsNeedReload);
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

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
