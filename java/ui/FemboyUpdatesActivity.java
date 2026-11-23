package org.telegram.ui;

import android.view.View;

import org.telegram.femboy.FemboyConfig;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.femboy.FemboyUpdate;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

/**
 * Ветка «Обновления»: как часто спрашивать про новую версию и кнопка спросить
 * прямо сейчас.
 *
 * Раньше проверка была ровно одна — при запуске приложения. Для того, кто
 * держит телеграм открытым сутками, это значило «никогда».
 */
public class FemboyUpdatesActivity extends UniversalFragment {

    /** Кнопка и заголовок не должны попасть в номера значений интервала. */
    private static final int ID_CHECK_NOW = 1000;

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(R.string.FemboySecAbout);
    }

    @Override
    public View createView(android.content.Context context) {
        final View view = super.createView(context);
        listView.setSections();
        return view;
    }

    /** Название интервала: «3 минуты», «6 часов», «Никогда». */
    static String name(int minutes) {
        if (minutes <= 0) {
            return LocaleController.getString(R.string.FemboyUpdatesOff);
        }
        if (minutes < 60) {
            return LocaleController.formatPluralString("Minutes", minutes);
        }
        return LocaleController.formatPluralString("Hours", minutes / 60);
    }

    private static final int ID_INTERVAL = 1001;
    private static final int ID_CHANNEL = 1002;
    private static final int ID_FORUM = 1003;
    private static final int ID_FEEDBACK = 1004;
    private static final int ID_STICKERS = 1005;
    private static final int ID_SOURCE = 1006;
    private static final int ID_SOUND = 1007;
    private static final int ID_DONATE = 1008;
    private static final int ID_BADGE = 1009;
    private static final int ID_VOICE = 1010;
    private static final int ID_HIDDEN = 1011;

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(FemboyRowCell.Factory.of(ID_CHECK_NOW, R.drawable.femboy_ic_mobile_arrow_down,
                LocaleController.getString(R.string.FemboyUpdatesNow),
                LocaleController.formatString(R.string.FemboyUpdatesInstalled, FemboyConfig.shownVersion(), BuildVars.BUILD_VERSION_STRING)));
        items.add(FemboyRowCell.Factory.of(ID_INTERVAL, R.drawable.femboy_ic_restart_alt,
                LocaleController.getString(R.string.FemboyUpdatesAuto), name(FemboyConfig.updateIntervalMinutes())));
        items.add(UItem.asShadow(null));

        items.add(FemboyRowCell.Factory.of(ID_CHANNEL, R.drawable.femboy_ic_campaign, LocaleController.getString(R.string.FemboyChannel), "t.me/femboygm"));
        items.add(FemboyRowCell.Factory.of(ID_FORUM, R.drawable.femboy_ic_forum, LocaleController.getString(R.string.FemboyForum), "t.me/femboyconfs"));
        items.add(FemboyRowCell.Factory.of(ID_FEEDBACK, R.drawable.femboy_ic_feedback, LocaleController.getString(R.string.FemboyFeedback), LocaleController.getString(R.string.FemboyFeedbackShort)));
        items.add(FemboyRowCell.Factory.of(ID_STICKERS, R.drawable.femboy_ic_sticky_note_2, LocaleController.getString(R.string.FemboyGramStickers), LocaleController.getString(R.string.FemboyStickersShort)));
        items.add(FemboyRowCell.Factory.of(ID_SOURCE, R.drawable.femboy_ic_code, LocaleController.getString(R.string.FemboySource), "github.com/webrch/femboy-gram"));
        items.add(FemboyRowCell.Factory.of(ID_DONATE, R.drawable.femboy_ic_volunteer_activism,
                LocaleController.getString(R.string.FemboyDonate), LocaleController.getString(R.string.FemboyDonateRow)));
        items.add(FemboyRowCell.Factory.of(ID_BADGE, R.drawable.femboy_ic_star,
                LocaleController.getString(R.string.FemboyBadgeBuyTitle), LocaleController.getString(R.string.FemboyBadgeBuyPrice)));
        items.add(FemboyRowCell.Factory.of(ID_VOICE, R.drawable.femboy_ic_pets,
                LocaleController.getString(R.string.FemboyVoiceTitle), LocaleController.getString(R.string.FemboyVoiceRow)));
        items.add(FemboyRowCell.Factory.of(ID_HIDDEN, R.drawable.femboy_ic_visibility_off,
                LocaleController.getString(R.string.FemboyHiddenTitle), LocaleController.getString(R.string.FemboyHiddenRow)));
        // «Звук» появляется, только когда мяуканье уже услышали
        if (FemboyConfig.meowHeard()) {
            items.add(FemboyRowCell.Factory.of(ID_SOUND, R.drawable.femboy_ic_pets, LocaleController.getString(R.string.FemboyChime), LocaleController.getString(R.string.FemboySoundInfo)));
        }
        items.add(UItem.asShadow(null));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        switch (item.id) {
            case ID_CHECK_NOW:
                checkNow();
                break;
            case ID_INTERVAL:
                chooseInterval();
                break;
            case ID_CHANNEL:
                org.telegram.messenger.browser.Browser.openUrl(getContext(), FemboyConfig.CHANNEL_URL);
                break;
            case ID_FORUM:
                org.telegram.messenger.browser.Browser.openUrl(getContext(), FemboyConfig.FORUM_URL);
                break;
            case ID_FEEDBACK:
                org.telegram.messenger.browser.Browser.openUrl(getContext(), FemboyConfig.FEEDBACK_URL);
                break;
            case ID_STICKERS:
                org.telegram.ui.Components.BulletinFactory.of(this).createSimpleBulletin(org.telegram.messenger.R.raw.info, "скоро..").show();
                break;
            case ID_SOURCE:
                org.telegram.messenger.browser.Browser.openUrl(getContext(), FemboyConfig.SOURCE_URL);
                break;
            case ID_SOUND:
                presentFragment(new FemboySoundActivity());
                break;
            case ID_DONATE:
                presentFragment(new FemboyDonateActivity());
                break;
            case ID_BADGE:
                presentFragment(new FemboyDonateActivity(true));
                break;
            case ID_VOICE:
                presentFragment(new FemboyVoiceActivity());
                break;
            case ID_HIDDEN:
                presentFragment(new FemboyHiddenChatsActivity());
                break;
        }
    }

    /** Как часто искать новую версию: выбор из списка, а не шесть строк на экране. */
    private void chooseInterval() {
        final int[] values = FemboyConfig.UPDATE_INTERVALS;
        final CharSequence[] names = new CharSequence[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = name(values[i]);
        }
        new AlertDialog.Builder(getContext())
                .setTitle(LocaleController.getString(R.string.FemboyUpdatesAuto))
                .setItems(names, (d, which) -> {
                    FemboyConfig.setUpdateIntervalMinutes(values[which]);
                    // расписание переставляется сразу, без перезапуска
                    FemboyUpdate.schedule();
                    listView.adapter.update(true);
                })
                .show();
    }

    /**
     * Проверка по кнопке работает и при выключенныхавтоматических проверках: человек
     * попросил — значит спрашиваем.
     */
    private void checkNow() {
        // Проверяем разом и версию, и значки: для человека это одно действие.
        FemboyUpdate.checkAll(() -> {
            if (getContext() == null) {
                return;
            }
            final FemboyUpdate.Info info = FemboyUpdate.available();
            if (info != null) {
                NotificationCenter.getGlobalInstance()
                        .postNotificationName(NotificationCenter.appUpdateAvailable);
                offer(info);
            } else {
                BulletinFactory.of(this).createSimpleBulletin(R.raw.chats_infotip,
                        LocaleController.getString(R.string.FemboyUpdatesLatest)).show();
            }
        });
    }

    /**
     * Окно с найденной версией и кнопкой, которая её ставит.
     *
     * Раньше здесь была подсказка внизу экрана: «вышла версия новее» — и всё.
     * Человек нажал «проверить», узнал, что обновление есть, и остался с этим
     * знанием наедине: ни скачать, ни поставить отсюда было нельзя. Сообщать
     * о работе, которую сам же и не даёшь сделать, — худший вид вежливости.
     */
    private void offer(FemboyUpdate.Info info) {
        final StringBuilder text = new StringBuilder();
        final String about = info.about();
        if (about != null && about.length() > 0) {
            text.append(about);
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(getContext())
                .setTitle(LocaleController.formatString(R.string.FemboyUpdatesFound, info.label()))
                .setMessage(text.length() > 0 ? text.toString()
                        : LocaleController.getString(R.string.FemboyUpdatesAbout))
                .setNegativeButton(LocaleController.getString(R.string.FemboyLater), null);
        if (FemboyUpdate.downloaded() != null) {
            // Уже скачано — второй раз качать незачем.
            builder.setPositiveButton(LocaleController.getString(R.string.FemboyUpdatesInstall),
                    (d, w) -> FemboyUpdate.install(getParentActivity()));
        } else {
            builder.setPositiveButton(LocaleController.getString(R.string.FemboyUpdatesDownload),
                    (d, w) -> startDownload());
        }
        builder.show();
    }

    /**
     * Качает и показывает, сколько уже. Без этого длинная закачка выглядит
     * так же, как ничего не происходящее нажатие.
     */
    private void startDownload() {
        final AlertDialog progress = new AlertDialog(getContext(), AlertDialog.ALERT_TYPE_LOADING);
        progress.setCanCancel(true);
        progress.setOnCancelListener(d -> FemboyUpdate.cancel());
        progress.show();
        FemboyUpdate.download(() -> {
            if (FemboyUpdate.downloading()) {
                return;
            }
            progress.dismiss();
            if (getContext() == null) {
                return;
            }
            if (FemboyUpdate.downloaded() != null) {
                FemboyUpdate.install(getParentActivity());
            } else {
                BulletinFactory.of(this).createSimpleBulletin(R.raw.error,
                        LocaleController.getString(R.string.FemboyUpdatesFailed)).show();
            }
        });
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
