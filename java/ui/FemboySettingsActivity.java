package org.telegram.ui;

import android.view.Gravity;
import org.telegram.messenger.AndroidUtilities;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.femboy.FemboyConfig;
import org.telegram.femboy.FemboyFonts;
import org.telegram.femboy.FemboyIconView;
import org.telegram.femboy.FemboyProxy;
import org.telegram.femboy.FemboySeizure;
import org.telegram.femboy.FemboyYou;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

/**
 * Корень раздела FemboyGram. Всё разложено по смыслу: вид, чаты, профиль,
 * приватность, плагины и сам FemboyGram. Простые переключатели живут прямо
 * здесь, своим экраном открывается только то, где настроек несколько.
 *
 * Пояснение под строкой короткое, в одну-две строки: что делает, а не
 * почему. Подробности, если нужны, на экране самой функции.
 */
public class FemboySettingsActivity extends UniversalFragment {

    private static final int ID_HELP = 1;

    private static final int ID_MATERIAL_YOU = 10;
    private static final int ID_FONTS = 11;
    private static final int ID_INPUT = 12;
    private static final int ID_MARKUP = 13;
    private static final int ID_SEIZURE = 14;
    private static final int ID_DISCORD = 15;
    private static final int ID_EDIT_HISTORY = 16;
    private static final int ID_OWN_PLAYER = 17;
    private static final int ID_MUSIC_TAB = 18;
    private static final int ID_MUSIC = 19;
    private static final int ID_SAVE_DELETED = 24;
    private static final int ID_STREAK_FLAMES = 25;
    private static final int ID_STREAK_PET = 26;
    private static final int ID_PET_IN_CHAT = 27;

    private static final int ID_HIDE_ALL = 20;
    private static final int ID_TRACKS = 21;
    private static final int ID_COPY = 22;

    private static final int ID_BUBBLES = 32;

    private static final int ID_STREAMER = 40;
    private static final int ID_PROXY = 41;

    private static final int ID_PLUGINS = 50;

    private static final int ID_UPDATES = 60;
    private static final int ID_SOUND = 61;
    private static final int ID_STICKERS = 62;
    private static final int ID_CHANNEL = 63;
    private static final int ID_FORUM = 64;
    private static final int ID_FEEDBACK = 65;
    private static final int ID_SOURCE = 67;

    // категории: корень показывает их списком, каждая открывается этим же классом
    private static final int CAT_ROOT = 0;
    private static final int CAT_LOOK = 1;
    private static final int CAT_CHATS = 2;
    private static final int CAT_PRIVACY = 4;
    private static final int CAT_ABOUT = 5;
    private static final int ID_CAT = 70;
    private static final int ID_MESSAGES = 80;
    private static final int ID_PRIVACY = 82;

    private final int category;
    private FrameLayout header;
    private FemboySeedPicker seedPicker;
    private FemboyShapePicker shapePicker;
    private FemboyChatPreview chat;

    public FemboySettingsActivity() {
        this(CAT_ROOT);
    }

    private FemboySettingsActivity(int category) {
        this.category = category;
    }

    @Override
    protected CharSequence getTitle() {
        switch (category) {
            case CAT_LOOK: return s(R.string.FemboySecLook);
            case CAT_CHATS: return s(R.string.FemboySecChats);
            case CAT_PRIVACY: return s(R.string.FemboySecPrivacy);
            case CAT_ABOUT: return s(R.string.FemboySecAbout);
        }
        return FemboyConfig.APP_NAME;
    }

    private static String s(int res) {
        return LocaleController.getString(res);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        switch (category) {
            case CAT_LOOK:
                // настоящий кусок чата: видно, во что Material You красит переписку
                if (chat == null && getContext() != null) {
                    chat = new FemboyChatPreview(getContext(), false,
                            new FemboyChatPreview.Message(s(R.string.FemboyPreviewIn), false),
                            new FemboyChatPreview.Message(s(R.string.FemboyPreviewOut), true));
                }
                if (chat != null) {
                    items.add(UItem.asCustom(chat));
                }
                items.add(FemboyRowCell.Factory.check(ID_MATERIAL_YOU, R.drawable.femboy_ic_palette, s(R.string.FemboyMaterialYou),
                        s(FemboyYou.isMonet() ? R.string.FemboyMaterialYouShort : R.string.FemboyMaterialYouShortOld), FemboyYou.enabled()));
                // нежные темы Material You: розовая, лавандовая, голубая
                items.add(UItem.asHeader(s(R.string.FemboyThemes)));
                if (FemboyYou.enabled() && getContext() != null) {
                    if (seedPicker == null) {
                        seedPicker = new FemboySeedPicker(getContext(), () -> {
                            if (parentLayout != null) {
                                parentLayout.rebuildAllFragmentViews(true, true);
                            }
                        });
                    }
                    items.add(UItem.asCustom(seedPicker));
                }
                // формы аватарок, как в FemboyT
                if (getContext() != null) {
                    if (shapePicker == null) {
                        shapePicker = new FemboyShapePicker(getContext(), () -> {
                            if (parentLayout != null) {
                                parentLayout.rebuildAllFragmentViews(false, false);
                            }
                        });
                    }
                    items.add(UItem.asCustom(shapePicker));
                }
                items.add(FemboyRowCell.Factory.of(ID_FONTS, R.drawable.femboy_ic_font_download, s(R.string.FemboyFonts), fontName()));
                items.add(FemboyRowCell.Factory.check(ID_DISCORD, R.drawable.femboy_ic_forum, s(R.string.FemboyDiscord), s(R.string.FemboyDiscordShort), FemboyConfig.discordMode()));
                items.add(FemboyRowCell.Factory.check(ID_SEIZURE, R.drawable.femboy_ic_looks, s(R.string.FemboySeizure), s(R.string.FemboySeizureShort), FemboySeizure.enabled()));
                items.add(UItem.asShadow(null));
                return;
            case CAT_CHATS:
                // по смыслу: список чатов, сообщения, серии, музыка
                items.add(UItem.asHeader(s(R.string.FemboyChatsGroupList)));
                items.add(FemboyRowCell.Factory.check(ID_HIDE_ALL, R.drawable.femboy_ic_tab, s(R.string.FemboyHideAllChats), s(R.string.FemboyHideAllShort), FemboyConfig.hideAllChatsTab()));
                items.add(UItem.asHeader(s(R.string.FemboyChatsGroupMessages)));
                items.add(FemboyRowCell.Factory.check(ID_SAVE_DELETED, R.drawable.femboy_ic_visibility, s(R.string.FemboySaveDeleted), s(R.string.FemboySaveDeletedShort), org.telegram.femboy.FemboyDeleted.enabled()));
                items.add(FemboyRowCell.Factory.check(ID_EDIT_HISTORY, R.drawable.femboy_ic_history, s(R.string.FemboyEditHistory), s(R.string.FemboyEditHistoryShort), org.telegram.femboy.FemboyEdits.enabled()));
                items.add(UItem.asHeader(s(R.string.FemboyChatsGroupStreaks)));
                // огоньки и тилкайо одним переключателем
                items.add(FemboyRowCell.Factory.check(ID_STREAK_FLAMES, R.drawable.femboy_ic_local_fire_department, s(R.string.FemboyStreakAll), s(R.string.FemboyStreakAllShort), org.telegram.femboy.FemboyStreaks.flamesEnabled()));
                if (org.telegram.femboy.FemboyStreaks.flamesEnabled()) {
                    items.add(FemboyRowCell.Factory.check(ID_PET_IN_CHAT, R.drawable.femboy_ic_pets, s(R.string.FemboyPetInChat), s(R.string.FemboyPetInChatShort), FemboyPetFloat.inChatEnabled()));
                }
                items.add(UItem.asHeader(s(R.string.FemboyChatsGroupMusic)));
                items.add(FemboyRowCell.Factory.check(ID_MUSIC_TAB, R.drawable.femboy_ic_library_music, s(R.string.FemboyMusicTab), s(R.string.FemboyMusicTabShort), org.telegram.femboy.FemboyMusic.musicTab()));
                items.add(UItem.asShadow(null));
                return;
        }

        if (header == null && getContext() != null) {
            header = new FrameLayout(getContext());
            // Значок сидит в своём квадрате, а не во всю ширину строки: тогда
            // список тянется пальцем везде, кроме самого значка.
            header.addView(new FemboyIconView(getContext()),
                    LayoutHelper.createFrame(150, 150, Gravity.CENTER));
        }
        if (header != null) {
            items.add(UItem.asCustomShadow(header, 168));
        }
        items.add(FemboyRowCell.Factory.of(ID_CAT + CAT_LOOK, R.drawable.femboy_ic_palette, s(R.string.FemboySecLook), s(R.string.FemboySecLookShort)));
        items.add(FemboyRowCell.Factory.of(ID_MESSAGES, R.drawable.femboy_ic_format_paint, s(R.string.FemboySecMessages), s(R.string.FemboySecMessagesShort)));
        items.add(FemboyRowCell.Factory.of(ID_CAT + CAT_CHATS, R.drawable.femboy_ic_chat_bubble, s(R.string.FemboySecChats), s(R.string.FemboySecChatsShort)));
        items.add(FemboyRowCell.Factory.of(ID_PRIVACY, R.drawable.femboy_ic_shield_person, s(R.string.FemboySecPrivacy), s(R.string.FemboySecPrivacyShort)));
        items.add(FemboyRowCell.Factory.of(ID_PLUGINS, R.drawable.femboy_ic_extension, s(R.string.FemboyPlugins), s(R.string.FemboyPluginsShort)));
        items.add(UItem.asShadow(null));
        items.add(FemboyRowCell.Factory.of(ID_UPDATES, R.drawable.femboy_ic_info, s(R.string.FemboySecAbout),
                LocaleController.formatString(R.string.FemboySecAboutShort, FemboyConfig.shownVersion())));
        items.add(UItem.asShadow(null));
    }

    private static String fontName() {
        final String chosen = FemboyFonts.chosen();
        if (chosen != null && !chosen.isEmpty()) {
            for (FemboyFonts.Font font : FemboyFonts.list()) {
                if (chosen.equals(font.id)) {
                    return font.name;
                }
            }
        }
        return s(R.string.FemboyFontsDefault);
    }

    private static String inputSummary() {
        final int lines = FemboyConfig.inputMaxLinesRaw();
        return LocaleController.formatString(R.string.FemboyInputShort,
                lines == 0 ? s(R.string.FemboyLinesMax) : String.valueOf(lines),
                Math.round(FemboyConfig.inputTextSize()),
                s(FemboyConfig.inputOnTop() ? R.string.FemboyInputTop : R.string.FemboyInputBottom));
    }

    @Override
    public void onResume() {
        super.onResume();
        // подписи под строками (шрифт, поле ввода) могли поменяться на вложенных экранах
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(false);
        }
    }

    @Override
    public View createView(android.content.Context context) {
        header = null;      // прошлый экран уносит с собой своё окно
        seedPicker = null;
        chat = null;
        final View view = super.createView(context);
        if (category == CAT_ROOT) {
            actionBar.createMenu().addItem(ID_HELP, R.drawable.outline_question_mark);
        }
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == ID_HELP) {
                    new AlertDialog.Builder(getContext())
                            .setTitle(LocaleController.getString(R.string.FemboyAboutTitle))
                            .setMessage(LocaleController.getString(R.string.FemboyAboutText))
                            .setPositiveButton(LocaleController.getString(R.string.Close), null)
                            .show();
                }
            }
        });
        listView.setSections();
        return view;
    }

    private void toggled(View view, boolean on) {
        if (view instanceof FemboyRowCell) {
            ((FemboyRowCell) view).setChecked(on);
        }
        listView.adapter.update(true);
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_MESSAGES) {
            presentFragment(new FemboyMarkupActivity());
            return;
        } else if (item.id == ID_PRIVACY) {
            presentFragment(new FemboyStreamerActivity());
            return;
        }
        if (item.id > ID_CAT && item.id <= ID_CAT + CAT_ABOUT) {
            presentFragment(new FemboySettingsActivity(item.id - ID_CAT));
            return;
        }
        switch (item.id) {
            case ID_MATERIAL_YOU:
                FemboyYou.setEnabled(!FemboyYou.enabled());
                if (FemboyYou.enabled()) {
                    org.telegram.femboy.FemboyTheme.followSystemDayNight(true);
                }
                // формы (карточки, углы, кнопки) строятся при создании экранов
                // и текущий экран тоже, чтобы новое было видно сразу
                if (parentLayout != null) {
                    parentLayout.rebuildAllFragmentViews(true, true);
                }
                break;
            case ID_SEIZURE:
                toggleSeizure(view);
                break;
            case ID_SAVE_DELETED:
                org.telegram.femboy.FemboyDeleted.setEnabled(!org.telegram.femboy.FemboyDeleted.enabled());
                toggled(view, org.telegram.femboy.FemboyDeleted.enabled());
                break;
            case ID_EDIT_HISTORY:
                org.telegram.femboy.FemboyEdits.setEnabled(!org.telegram.femboy.FemboyEdits.enabled());
                listView.adapter.update(true);
                return;
            case ID_STREAK_FLAMES: {
                final boolean on = !org.telegram.femboy.FemboyStreaks.flamesEnabled();
                org.telegram.femboy.FemboyStreaks.setFlamesEnabled(on);
                org.telegram.femboy.FemboyStreaks.setPetEnabled(on);
                // строка «Тилкайо в переписке» видна только при включённых сериях
                listView.adapter.update(true);
                break;
            }
            case ID_PET_IN_CHAT:
                FemboyPetFloat.setInChatEnabled(!FemboyPetFloat.inChatEnabled());
                toggled(view, FemboyPetFloat.inChatEnabled());
                break;
            case ID_STREAK_PET:
                org.telegram.femboy.FemboyStreaks.setPetEnabled(!org.telegram.femboy.FemboyStreaks.petEnabled());
                toggled(view, org.telegram.femboy.FemboyStreaks.petEnabled());
                break;
            case ID_DISCORD:
                FemboyConfig.setDiscordMode(!FemboyConfig.discordMode());
                toggled(view, FemboyConfig.discordMode());
                // ячейки и разметка сообщений строятся при открытии чата: перестраиваем экраны
                if (parentLayout != null) {
                    parentLayout.rebuildAllFragmentViews(false, false);
                }
                break;
            case ID_OWN_PLAYER:
                org.telegram.femboy.FemboyMusic.setOwnPlayer(!org.telegram.femboy.FemboyMusic.ownPlayer());
                toggled(view, org.telegram.femboy.FemboyMusic.ownPlayer());
                break;
            case ID_MUSIC_TAB:
                // вкладка появится или уйдёт, когда главный экран снова станет виден
                org.telegram.femboy.FemboyMusic.setMusicTab(!org.telegram.femboy.FemboyMusic.musicTab());
                toggled(view, org.telegram.femboy.FemboyMusic.musicTab());
                break;
            case ID_MUSIC:
                presentFragment(new FemboyMusicActivity());
                break;
            case ID_HIDE_ALL:
                FemboyConfig.setHideAllChatsTab(!FemboyConfig.hideAllChatsTab());
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogFiltersUpdated);
                toggled(view, FemboyConfig.hideAllChatsTab());
                break;
            case ID_TRACKS:
                FemboyConfig.setTagsEnabled(!FemboyConfig.tagsEnabled());
                toggled(view, FemboyConfig.tagsEnabled());
                break;
            case ID_COPY:
                FemboyConfig.setCopyFormatting(!FemboyConfig.copyFormatting());
                toggled(view, FemboyConfig.copyFormatting());
                break;
            case ID_PROXY:
                FemboyProxy.enable(!FemboyConfig.proxyEnabled());
                // включение может не удаться: показываем то, что вышло на деле
                toggled(view, FemboyConfig.proxyEnabled());
                break;
            case ID_FONTS:
                presentFragment(new FemboyFontsActivity());
                break;
            case ID_INPUT:
            case ID_MARKUP:
                presentFragment(new FemboyMarkupActivity());
                break;
            case ID_STREAMER:
                presentFragment(new FemboyStreamerActivity());
                break;
            case ID_PLUGINS:
                presentFragment(new FemboyPluginsActivity());
                break;
            case ID_UPDATES:
                presentFragment(new FemboyUpdatesActivity());
                break;
            case ID_SOUND:
                presentFragment(new FemboySoundActivity());
                break;
            case ID_STICKERS:
                org.telegram.ui.Components.BulletinFactory.of(this).createSimpleBulletin(org.telegram.messenger.R.raw.info, "скоро..").show();
                break;
            case ID_CHANNEL:
                Browser.openUrl(getContext(), FemboyConfig.CHANNEL_URL);
                break;
            case ID_FORUM:
                Browser.openUrl(getContext(), FemboyConfig.FORUM_URL);
                break;
            case ID_FEEDBACK:
                Browser.openUrl(getContext(), FemboyConfig.FEEDBACK_URL);
                break;
            case ID_SOURCE:
                Browser.openUrl(getContext(), FemboyConfig.SOURCE_URL);
                break;
        }
    }

    /**
     * Выключается молча, включается только через предупреждение: подвижная
     * картинка бывает опасна не в переносном смысле.
     */
    private void toggleSeizure(View view) {
        if (FemboySeizure.enabled()) {
            FemboySeizure.set(false);
            toggled(view, false);
            return;
        }
        new AlertDialog.Builder(getContext())
                .setTitle(s(R.string.FemboySeizureWarning))
                .setMessage(s(R.string.FemboySeizureWarningText))
                .setPositiveButton(s(R.string.FemboySeizureEnable), (d, w) -> {
                    FemboySeizure.set(true);
                    toggled(view, true);
                })
                .setNegativeButton(s(R.string.Cancel), null)
                .show();
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
