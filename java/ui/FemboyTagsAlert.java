package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.femboy.FemboyTags;
import android.graphics.BitmapFactory;
import android.graphics.Outline;
import android.view.ViewOutlineProvider;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.ImageUpdater;
import org.telegram.ui.Components.LayoutHelper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;

/**
 * Правка тегов у аудио прямо из чата: название, исполнитель, обложка.
 *
 * В телеграме это обычно делают через ботов — то есть отдают свой файл чужому
 * серверу ради трёх строчек текста. Здесь всё происходит на телефоне: теги
 * пишутся в копию файла, копия отправляется в тот же чат. Исходное сообщение
 * не трогаем: чужое сообщение править нельзя, а своё телеграм разрешает менять
 * только текстом, не файлом.
 *
 * Обложка выбирается телеграмовским выбором фотографий и кадрируется его же
 * экраном — тем самым, что режет аватарки. Системный выбор файлов, стоявший
 * здесь сначала, выглядел чужеродно, а обрезать в нём было нечем.
 */
public class FemboyTagsAlert {

    /** Живой разбор переживает открытие выбора фото: экран уходит и приходит. */
    private static FemboyTagsAlert current;

    private final ChatActivity fragment;
    private final MessageObject message;

    private String title = "";
    private String artist = "";
    private byte[] cover;
    private Bitmap coverPreview;

    private EditTextBoldCursor titleField;
    private EditTextBoldCursor artistField;
    private android.app.Dialog dialog;
    private ImageUpdater imageUpdater;
    private boolean coverTaken;
    private ImageView coverView;
    private TextView coverHint;

    private FemboyTagsAlert(ChatActivity fragment, MessageObject message) {
        this.fragment = fragment;
        this.message = message;
        final TLRPC.Document document = message.getDocument();
        if (document != null) {
            for (TLRPC.DocumentAttribute a : document.attributes) {
                if (a instanceof TLRPC.TL_documentAttributeAudio) {
                    title = a.title == null ? "" : a.title;
                    artist = a.performer == null ? "" : a.performer;
                }
            }
        }
    }

    public static void show(ChatActivity fragment, MessageObject message) {
        if (fragment == null || fragment.getParentActivity() == null || message == null) {
            return;
        }
        current = new FemboyTagsAlert(fragment, message);
        current.open();
    }

    /** Поле MD3: контейнер со скруглением, подпись сверху мелко акцентом, текст под ней. */
    private static EditTextBoldCursor field(Activity context, LinearLayout parent, CharSequence label, String value) {
        org.telegram.femboy.FemboyYou.ensureRoles();
        final LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(10), dp(16), dp(8));
        final android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(dp(16));
        bg.setColor(org.telegram.femboy.FemboyYou.on() ? org.telegram.femboy.FemboyYou.surfaceContainerHighest : Theme.getColor(Theme.key_windowBackgroundGray));
        box.setBackground(bg);
        final TextView caption = new TextView(context);
        caption.setText(label);
        caption.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        caption.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
        box.addView(caption);
        final EditTextBoldCursor edit = new EditTextBoldCursor(context);
        edit.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        edit.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        edit.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
        edit.setCursorSize(dp(20));
        edit.setBackgroundDrawable(null);
        edit.setSingleLine(true);
        edit.setPadding(0, dp(2), 0, dp(2));
        edit.setText(value);
        box.addView(edit, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        box.setOnClickListener(v -> {
            edit.requestFocus();
            AndroidUtilities.showKeyboard(edit);
        });
        parent.addView(box, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 0));
        return edit;
    }

    private static TextView button(Activity context, CharSequence text, int bg, int fg) {
        final TextView b = new TextView(context);
        b.setText(text);
        b.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        b.setTypeface(AndroidUtilities.bold());
        b.setTextColor(fg);
        b.setGravity(Gravity.CENTER);
        b.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(24), bg, Theme.multAlpha(fg, 0.15f)));
        return b;
    }

    /**
     * Нижний лист MD3: большая обложка по центру, под ней «Сменить обложку»,
     * два поля и кнопки на всю ширину.
     */
    private void open() {
        final Activity context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        org.telegram.femboy.FemboyYou.ensureRoles();
        final boolean md3 = org.telegram.femboy.FemboyYou.on();

        final LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(8), dp(20), dp(16));

        final View handle = new View(context);
        final android.graphics.drawable.GradientDrawable h = new android.graphics.drawable.GradientDrawable();
        h.setCornerRadius(dp(2));
        h.setColor(Theme.getColor(Theme.key_sheet_scrollUp));
        handle.setBackground(h);
        layout.addView(handle, LayoutHelper.createLinear(32, 4, Gravity.CENTER_HORIZONTAL, 0, 6, 0, 14));

        final TextView heading = new TextView(context);
        heading.setText(LocaleController.getString(R.string.FemboyTrackTags));
        heading.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        heading.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        heading.setGravity(Gravity.CENTER);
        layout.addView(heading, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        coverView = new ImageView(context);
        coverView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        coverView.setBackgroundColor(md3 ? org.telegram.femboy.FemboyYou.surfaceContainerHighest : Theme.getColor(Theme.key_dialogInputField));
        coverView.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(24));
            }
        });
        coverView.setClipToOutline(true);
        coverView.setOnClickListener(v -> pickCover());
        layout.addView(coverView, LayoutHelper.createLinear(160, 160, Gravity.CENTER_HORIZONTAL));

        coverHint = button(context, "", md3 ? org.telegram.femboy.FemboyYou.secondaryContainer : Theme.getColor(Theme.key_windowBackgroundGray),
                md3 ? org.telegram.femboy.FemboyYou.onSecondaryContainer : Theme.getColor(Theme.key_dialogTextBlue2));
        coverHint.setPadding(dp(18), 0, dp(18), 0);
        coverHint.setOnClickListener(v -> pickCover());
        layout.addView(coverHint, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 40, Gravity.CENTER_HORIZONTAL, 0, 12, 0, 4));

        titleField = field(context, layout, LocaleController.getString(R.string.FemboyTrackTitle), title);
        artistField = field(context, layout, LocaleController.getString(R.string.FemboyTrackArtist), artist);

        final LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        final TextView cancel = button(context, LocaleController.getString(R.string.Cancel),
                md3 ? org.telegram.femboy.FemboyYou.secondaryContainer : Theme.getColor(Theme.key_windowBackgroundGray),
                md3 ? org.telegram.femboy.FemboyYou.onSecondaryContainer : Theme.getColor(Theme.key_dialogTextBlack));
        final TextView send = button(context, LocaleController.getString(R.string.FemboyTrackSend),
                Theme.getColor(Theme.key_featuredStickers_addButton), Theme.getColor(Theme.key_featuredStickers_buttonText));
        buttons.addView(cancel, LayoutHelper.createLinear(0, 48, 1f, 0, 0, 6, 0));
        buttons.addView(send, LayoutHelper.createLinear(0, 48, 1f, 6, 0, 0, 0));
        layout.addView(buttons, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 20, 0, 0));

        updateCoverView();

        final org.telegram.ui.ActionBar.BottomSheet sheet = new org.telegram.ui.ActionBar.BottomSheet(context, true);
        sheet.setApplyTopPadding(false);
        sheet.setApplyBottomPadding(false);
        final android.widget.ScrollView scroll = new android.widget.ScrollView(context);
        scroll.addView(layout);
        sheet.setCustomView(scroll);
        cancel.setOnClickListener(v -> sheet.dismiss());
        send.setOnClickListener(v -> {
            sheet.dismiss();
            apply();
        });
        dialog = sheet;
        sheet.show();
    }

    private void updateCoverView() {
        if (coverView == null) {
            return;
        }
        if (coverPreview != null) {
            coverView.setColorFilter(null);
            coverView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            coverView.setImageBitmap(coverPreview);
            coverHint.setText(LocaleController.getString(R.string.FemboyTrackCoverChosen));
        } else {
            coverView.setImageResource(R.drawable.femboy_ic_music_note);
            coverView.setScaleType(ImageView.ScaleType.CENTER);
            coverView.setColorFilter(Theme.getColor(Theme.key_dialogTextHint));
            coverHint.setText(LocaleController.getString(R.string.FemboyTrackCover));
        }
    }

    /** Запоминаем набранное: диалог сейчас закроется, а вернуться надо к нему же. */
    private void remember() {
        if (titleField != null) {
            title = titleField.getText().toString();
        }
        if (artistField != null) {
            artist = artistField.getText().toString();
        }
    }

    private void pickCover() {
        remember();
        // Окно надо закрыть ДО открытия галереи. Пока оно висело, галерея
        // открывалась за ним — владелец это увидел первым делом.
        if (dialog != null) {
            dialog.dismiss();
            dialog = null;
        }
        coverTaken = false;
        // Галерея и кадрирование телеграмовские, нынешние: тот же нижний лист,
        // что при смене аватарки. Своего кадрирования больше нет — из-за него
        // обрезка шла дважды, вторым разом чужим по стилю квадратным экраном.
        imageUpdater = new ImageUpdater(false, ImageUpdater.FOR_TYPE_USER, false);
        imageUpdater.parentFragment = fragment;
        imageUpdater.setCanSelectVideo(false);
        // Обязательно версия с двумя доводами. Та, что с одним, кроме поиска
        // картинок выключает и сам нижний лист — а без него открывается старый
        // экран галереи. Ровно на это владелец и показал двумя снимками.
        imageUpdater.setSearchAvailable(false, true);
        // Ничего не загружаем: нам нужен файл на диске, а не аватарка на
        // сервере. Так честнее, чем отменять уже начатую отправку.
        imageUpdater.setUploadAfterSelect(false);
        imageUpdater.setDelegate((photo, video, videoStartTimestamp, videoPath, bigSize, smallSize, isVideo, emojiMarkup) -> {
            if (coverTaken || bigSize == null) {
                return;
            }
            coverTaken = true;
            final File file = FileLoader.getInstance(fragment.getCurrentAccount())
                    .getPathToAttach(bigSize, true);
            if (file != null && file.exists()) {
                takeCover(BitmapFactory.decodeFile(file.getAbsolutePath()));
            }
            AndroidUtilities.runOnUIThread(this::open, 120);
        });
        imageUpdater.openMenu(false, null, d -> {
        }, 0);
    }

    private void takeCover(Bitmap bitmap) {
        if (bitmap == null) {
            return;
        }
        // Обложку ужимаем: в тег иногда кладут снимок с камеры на пять
        // мегабайт, и он поедет вместе с песней каждому получателю.
        final int max = 800;
        Bitmap out = bitmap;
        if (out.getWidth() > max || out.getHeight() > max) {
            final float scale = Math.min(max / (float) out.getWidth(), max / (float) out.getHeight());
            out = Bitmap.createScaledBitmap(out,
                    Math.round(out.getWidth() * scale), Math.round(out.getHeight() * scale), true);
        }
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        out.compress(Bitmap.CompressFormat.JPEG, 87, bytes);
        cover = bytes.toByteArray();
        coverPreview = out;
    }

    private void apply() {
        remember();
        final File src = FileLoader.getInstance(fragment.getCurrentAccount())
                .getPathToMessage(message.messageOwner);
        if (src == null || !src.exists()) {
            BulletinFactory.of(fragment).createSimpleBulletin(R.raw.error,
                    LocaleController.getString(R.string.FemboyTrackNeedFile)).show();
            return;
        }
        final String name = message.getDocumentName();
        final File dst = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE),
                (name == null || name.isEmpty() ? "track.mp3" : name));
        if (!FemboyTags.write(src, dst, title.trim(), artist.trim(), cover)) {
            BulletinFactory.of(fragment).createSimpleBulletin(R.raw.error,
                    LocaleController.getString(R.string.FemboyTrackWriteFailed)).show();
            return;
        }
        SendMessagesHelper.prepareSendingDocument(fragment.getAccountInstance(),
                dst.getAbsolutePath(), dst.getAbsolutePath(), null, null, "audio/mpeg",
                fragment.getDialogId(), null, fragment.getThreadMessage(), null, null, null,
                true, 0, null, fragment.getMessageChatSendParams(), false);
        current = null;
    }
}
