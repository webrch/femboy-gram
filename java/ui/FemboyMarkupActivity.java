package org.telegram.ui;

import android.view.View;

import org.telegram.femboy.FemboyConfig;
import org.telegram.femboy.FemboyMarkup;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

/**
 * Категория «Сообщения»: своё оформление в чужих сообщениях, разметка
 * значками при отправке, поле ввода и мелочи про свои сообщения. Раньше это
 * были три вложенных экрана; теперь всё на одном, разделено заголовками.
 *
 * Выключатели оформления не про отправку, а про показ: отправить оформленное
 * можно всегда, а вот принимать чужую радугу человек может и не хотеть.
 */
public class FemboyMarkupActivity extends UniversalFragment {

    private static final int ID_SIZE = 1;
    private static final int ID_DIM = 2;
    private static final int ID_RAINBOW = 3;
    private static final int ID_OUTLINE = 4;
    private static final int ID_BUTTON = 5;
    private static final int ID_EMOJI = 6;
    private static final int ID_COPY = 7;
    private static final int ID_BUBBLES = 8;
    private static final int ID_TOP = 9;
    private static final int ID_MARKDOWN = 100;
    private static final int[] STICKER_SIZES = {50, 60, 70, 80, 90, 100, 115, 130, 150};

    private static final String[] KINDS = {
            "bold", "italic", "underline", "strike", "spoiler", "code", "quote", "quote_collapsed"
    };
    private static final int[] TITLES = {
            R.string.Bold, R.string.Italic, R.string.Underline, R.string.Strike,
            R.string.Spoiler, R.string.Mono, R.string.FemboyMarkdownQuote,
            R.string.FemboyMarkdownQuoteCollapsed
    };
    private static final String[] SAMPLES = {
            "**…**", "__…__", "++…++", "~~…~~", "||…||", "`…`", ">…", ">>…"
    };

    /** Высота поля в строках, 0 — «расти, пока есть место». */
    private static final int[] LINES = {2, 3, 4, 5, 6, 8, 10, 15, 0};
    private static final int[] SIZES = {14, 15, 16, 17, 18, 19, 20, 22, 24};


    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(R.string.FemboySecMessages);
    }

    @Override
    public View createView(android.content.Context context) {
        markupChat = null;
        inputChat = null;
        stickerChat = null;
        final View view = super.createView(context);
        listView.setSections();
        return view;
    }

    private static String s(int res) {
        return LocaleController.getString(res);
    }

    private static int indexOf(int[] arr, int value, int fallback) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == value) {
                return i;
            }
        }
        return fallback;
    }

    private FemboyChatPreview markupChat;
    private FemboyChatPreview inputChat;
    private FemboyChatPreview stickerChat;

    /**
     * Стикер для витрины: толстый тилкайо из файлов приложения. Документ
     * ненастоящий, картинка лежит там, где телеграм ищет скачанное, поэтому
     * в сеть за ней никто не идёт.
     */
    private org.telegram.tgnet.TLRPC.Document previewSticker() {
        try {
            final org.telegram.tgnet.TLRPC.TL_document doc = new org.telegram.tgnet.TLRPC.TL_document();
            doc.id = 0x46656D626F7953741L;
            doc.dc_id = 2;
            doc.access_hash = 0;
            doc.file_reference = new byte[0];
            doc.mime_type = "image/webp";
            doc.date = (int) (System.currentTimeMillis() / 1000);
            final org.telegram.tgnet.TLRPC.TL_documentAttributeImageSize size = new org.telegram.tgnet.TLRPC.TL_documentAttributeImageSize();
            size.w = size.h = 512;
            doc.attributes.add(size);
            final org.telegram.tgnet.TLRPC.TL_documentAttributeSticker sticker = new org.telegram.tgnet.TLRPC.TL_documentAttributeSticker();
            sticker.alt = "\uD83D\uDC96";
            sticker.stickerset = new org.telegram.tgnet.TLRPC.TL_inputStickerSetEmpty();
            doc.attributes.add(sticker);
            final java.io.File file = org.telegram.messenger.FileLoader.getInstance(currentAccount).getPathToAttach(doc, true);
            if (!file.exists() || file.length() == 0) {
                file.getParentFile().mkdirs();
                try (java.io.InputStream in = getContext().getAssets().open("femboy_sticker.webp");
                     java.io.OutputStream out = new java.io.FileOutputStream(file)) {
                    final byte[] buf = new byte[16384];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                }
            }
            doc.size = file.length();
            doc.localPath = file.getAbsolutePath();
            return doc;
        } catch (Exception e) {
            org.telegram.messenger.FileLog.e(e);
            return null;
        }
    }

    private void redraw() {
        if (markupChat != null) {
            markupChat.refresh();
        }
        if (inputChat != null) {
            inputChat.refresh();
        }
        if (stickerChat != null) {
            stickerChat.refresh();
        }
    }

    /** Сообщение со всеми видами своего оформления: какие включены, те и видны. */
    private static String markupSample() {
        final StringBuilder sb = new StringBuilder();
        sb.append(FemboyMarkup.open(FemboyMarkup.KIND_RAINBOW, 0)).append(s(R.string.FemboyMarkupRainbow)).append(FemboyMarkup.close()).append(", ");
        sb.append(FemboyMarkup.open(FemboyMarkup.KIND_OUTLINE, 0)).append(s(R.string.FemboyMarkupOutline).toLowerCase()).append(FemboyMarkup.close()).append(", ");
        sb.append(FemboyMarkup.open(FemboyMarkup.KIND_SIZE, FemboyMarkup.sizeValue(1.6f))).append(s(R.string.FemboyMarkupSize).toLowerCase()).append(FemboyMarkup.close()).append(" ");
        sb.append(s(R.string.FemboyPreviewAnd)).append(" ");
        sb.append(FemboyMarkup.open(FemboyMarkup.KIND_DIM, 7)).append(s(R.string.FemboyMarkupDim).toLowerCase()).append(FemboyMarkup.close());
        return sb.toString();
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        // своё оформление в чужих сообщениях
        items.add(UItem.asHeader(s(R.string.FemboyMarkupShowHeader)));
        if (markupChat == null && getContext() != null) {
            markupChat = new FemboyChatPreview(getContext(), false, new FemboyChatPreview.Message(markupSample(), false));
        }
        if (markupChat != null) {
            items.add(UItem.asCustom(markupChat));
        }
        items.add(UItem.asCheck(ID_RAINBOW, s(R.string.FemboyMarkupRainbow)).setChecked(FemboyConfig.markupEnabled(FemboyMarkup.KIND_RAINBOW)));
        items.add(UItem.asCheck(ID_OUTLINE, s(R.string.FemboyMarkupOutline)).setChecked(FemboyConfig.markupEnabled(FemboyMarkup.KIND_OUTLINE)));
        items.add(UItem.asCheck(ID_SIZE, s(R.string.FemboyMarkupSize)).setChecked(FemboyConfig.markupEnabled(FemboyMarkup.KIND_SIZE)));
        items.add(UItem.asCheck(ID_DIM, s(R.string.FemboyMarkupDim)).setChecked(FemboyConfig.markupEnabled(FemboyMarkup.KIND_DIM)));
        items.add(FemboyRowCell.Factory.check(ID_EMOJI, R.drawable.femboy_ic_looks, s(R.string.FemboyFreeEmoji),
                s(R.string.FemboyFreeEmojiAbout), FemboyConfig.freeEmoji()));
        items.add(UItem.asShadow(null));

        // разметка значками при отправке
        items.add(UItem.asHeader(s(R.string.FemboyMarkdown)));
        for (int i = 0; i < KINDS.length; i++) {
            items.add(UItem.asCheck(ID_MARKDOWN + i, s(TITLES[i]) + "   " + SAMPLES[i])
                    .setChecked(FemboyConfig.markdownEnabled(KINDS[i])));
        }
        items.add(UItem.asShadow(null));

        // поле ввода
        items.add(UItem.asHeader(s(R.string.FemboyInput)));
        if (inputChat == null && getContext() != null) {
            inputChat = new FemboyChatPreview(getContext(), true,
                    new FemboyChatPreview.Message(s(R.string.FemboyPreviewIn), false));
        }
        if (inputChat != null) {
            items.add(UItem.asCustom(inputChat));
        }
        items.add(UItem.asCheck(ID_TOP, s(R.string.FemboyInputOnTop)).setChecked(FemboyConfig.inputOnTop()));
        items.add(UItem.asShadow(null));
        items.add(UItem.asHeader(s(R.string.FemboyLines)));
        final String[] lines = new String[LINES.length];
        for (int i = 0; i < LINES.length; i++) {
            lines[i] = LINES[i] == 0 ? s(R.string.FemboyLinesMax) : String.valueOf(LINES[i]);
        }
        items.add(UItem.asSlideView(lines, indexOf(LINES, FemboyConfig.inputMaxLinesRaw(), 4),
                i -> { FemboyConfig.setInputMaxLines(LINES[i]); redraw(); }));
        items.add(UItem.asShadow(null));
        items.add(UItem.asHeader(s(R.string.FemboyTextSize)));
        final String[] sizes = new String[SIZES.length];
        for (int i = 0; i < SIZES.length; i++) {
            sizes[i] = String.valueOf(SIZES[i]);
        }
        items.add(UItem.asSlideView(sizes, indexOf(SIZES, Math.round(FemboyConfig.inputTextSize()), 4),
                i -> { FemboyConfig.setInputTextSize(SIZES[i]); redraw(); }));
        items.add(UItem.asShadow(null));

        // стикеры: размер и форма
        items.add(UItem.asHeader(s(R.string.FemboyStickers)));
        if (stickerChat == null && getContext() != null) {
            final org.telegram.tgnet.TLRPC.Document sticker = previewSticker();
            if (sticker != null) {
                stickerChat = new FemboyChatPreview(getContext(), false, new FemboyChatPreview.Message(sticker, false));
            }
        }
        if (stickerChat != null) {
            items.add(UItem.asCustom(stickerChat));
        }
        final String[] stickerSizes = new String[STICKER_SIZES.length];
        for (int i = 0; i < STICKER_SIZES.length; i++) {
            stickerSizes[i] = STICKER_SIZES[i] + "%";
        }
        items.add(UItem.asSlideView(stickerSizes, indexOf(STICKER_SIZES, FemboyConfig.stickerSize(), 5),
                i -> { FemboyConfig.setStickerSize(STICKER_SIZES[i]); redraw(); }));
        items.add(UItem.asHeader(s(R.string.FemboyStickerRound)));
        items.add(UItem.asIntSlideView(1, 0, FemboyConfig.stickerRound(), 50,
                v -> v == 0 ? s(R.string.FemboyStickerShapeAsIs) : v == 50 ? s(R.string.FemboyStickerShapeCircle) : v + "%",
                v -> { FemboyConfig.setStickerRound(v); if (stickerChat != null) stickerChat.invalidateAll(); }));
        items.add(UItem.asShadow(null));

        // свои сообщения
        items.add(FemboyRowCell.Factory.check(ID_COPY, R.drawable.femboy_ic_tune, s(R.string.FemboyCopyFormatted),
                s(R.string.FemboyCopyShort), FemboyConfig.copyFormatting()));
        items.add(UItem.asShadow(null));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        final int id = item.id;
        if (id >= ID_MARKDOWN && id < ID_MARKDOWN + KINDS.length) {
            final String kind = KINDS[id - ID_MARKDOWN];
            FemboyConfig.setMarkdownEnabled(kind, !FemboyConfig.markdownEnabled(kind));
        } else if (id == ID_COPY) {
            FemboyConfig.setCopyFormatting(!FemboyConfig.copyFormatting());
        } else if (id == ID_EMOJI) {
            FemboyConfig.setFreeEmoji(!FemboyConfig.freeEmoji());
        } else if (id == ID_TOP) {
            FemboyConfig.setInputOnTop(!FemboyConfig.inputOnTop());
        } else if (id == ID_SIZE || id == ID_DIM || id == ID_RAINBOW || id == ID_OUTLINE || id == ID_BUTTON) {
            final int kind = id == ID_SIZE ? FemboyMarkup.KIND_SIZE
                    : id == ID_DIM ? FemboyMarkup.KIND_DIM
                    : id == ID_OUTLINE ? FemboyMarkup.KIND_OUTLINE
                    : id == ID_BUTTON ? FemboyMarkup.KIND_BUTTON
                    : FemboyMarkup.KIND_RAINBOW;
            FemboyConfig.setMarkupEnabled(kind, !FemboyConfig.markupEnabled(kind));
        } else {
            return;
        }
        listView.adapter.update(true);
        redraw();
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    // Как экраны самого телеграма: рисуемся под полоской жестов, а список
    // получает снизу отступ на её высоту, вместо пустой полосы под ним.
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
