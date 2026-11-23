package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.telegram.femboy.FemboyMusic;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

/** Куда положить песню: список плейлистов и «Новый плейлист». */
public final class FemboyPlaylistPicker {

    private FemboyPlaylistPicker() {}

    public static void show(Context context, MessageObject message, BulletinFactory bulletins) {
        if (context == null || !FemboyMusic.canAdd(message)) return;
        final ArrayList<FemboyMusic.Playlist> lists = FemboyMusic.playlists();
        final CharSequence[] names = new CharSequence[lists.size() + 1];
        final int[] icons = new int[lists.size() + 1];
        for (int i = 0; i < lists.size(); i++) {
            names[i] = lists.get(i).name;
            icons[i] = R.drawable.femboy_ic_queue_music;
        }
        names[lists.size()] = LocaleController.getString(R.string.FemboyPlaylistNew);
        icons[lists.size()] = R.drawable.femboy_ic_add;
        final BottomSheet.Builder builder = new BottomSheet.Builder(context);
        builder.setTitle(LocaleController.getString(R.string.FemboyAddToPlaylist));
        builder.setItems(names, icons, (dialog, which) -> {
            if (which < lists.size()) {
                put(context, lists.get(which), message, bulletins);
            } else {
                askName(context, LocaleController.getString(R.string.FemboyPlaylistNew), "", name ->
                        put(context, FemboyMusic.create(name), message, bulletins));
            }
        });
        builder.show();
    }

    private static void put(Context context, FemboyMusic.Playlist playlist, MessageObject message, BulletinFactory bulletins) {
        final boolean added = FemboyMusic.add(playlist, message);
        final String text = LocaleController.formatString(added ? R.string.FemboyPlaylistAdded : R.string.FemboyPlaylistAlready, playlist.name);
        if (bulletins != null) {
            bulletins.createSimpleBulletin(added ? R.raw.contact_check : R.raw.chats_infotip, text).show();
        } else {
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
        }
    }

    /** Окно с полем для названия. Пустое название не принимается. */
    public static void askName(Context context, String title, String initial, Utilities.Callback<String> done) {
        final FrameLayout frame = new FrameLayout(context);
        final EditTextBoldCursor edit = new EditTextBoldCursor(context);
        edit.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        edit.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        edit.setHintTextColor(Theme.getColor(Theme.key_dialogTextGray3));
        edit.setHint(LocaleController.getString(R.string.FemboyPlaylistName));
        edit.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
        edit.setCursorSize(dp(20));
        edit.setSingleLine(true);
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        edit.setImeOptions(EditorInfo.IME_ACTION_DONE);
        edit.setPadding(dp(16), dp(12), dp(16), dp(12));
        final GradientDrawable back = new GradientDrawable();
        back.setCornerRadius(dp(16));
        back.setColor(Theme.multAlpha(Theme.getColor(Theme.key_dialogTextBlack), 0.06f));
        edit.setBackground(back);
        edit.setText(initial);
        edit.setSelection(edit.length());
        frame.addView(edit, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 24, 4, 24, 4));

        final AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(title)
                .setView(frame)
                .setNegativeButton(LocaleController.getString(R.string.Cancel), (d, w) -> d.dismiss())
                .setPositiveButton(LocaleController.getString(R.string.OK), null)
                .create();
        // пустое название окно не закрывает
        dialog.setDismissDialogByButtons(false);
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
}
