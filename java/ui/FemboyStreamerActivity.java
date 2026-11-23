package org.telegram.ui;

import android.view.View;

import org.telegram.femboy.FemboyConfig;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

/** Ветка «Режим стримера»: что прятать на экране во время эфира. */
public class FemboyStreamerActivity extends UniversalFragment {

    private static final int ID_ON = 1;
    private static final int ID_OTHERS = 2;
    private static final int ID_USERNAME = 3;
    private static final int ID_PROXY = 4;
    private static final int ID_SCREENSHOTS = 5;

    private static final int ID_GHOST = 10;
    private static final int ID_GHOST_READ = 11;
    private static final int ID_GHOST_ONLINE = 12;
    private static final int ID_GHOST_TYPING = 13;
    private static final int ID_GHOST_OFFLINE = 14;
    private static final int ID_GHOST_MARK_SEND = 15;

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(R.string.FemboySecPrivacy);
    }

    private FemboyPreviews.Streamer preview;

    @Override
    public View createView(android.content.Context context) {
        preview = new FemboyPreviews.Streamer(context);
        final View view = super.createView(context);
        listView.setSections();
        return view;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (preview != null) {
            items.add(UItem.asCustom(preview));
        }
        items.add(UItem.asHeader(LocaleController.getString(R.string.FemboyStreamer)));
        items.add(UItem.asCheck(ID_ON, LocaleController.getString(R.string.FemboyStreamerOn))
                .setChecked(FemboyConfig.streamerMode()));
        // Остальные два появляются только при включённом режиме: сами по себе
        // они ничего не делают, и включённый выключатель без последствий
        // выглядит как поломка.
        if (FemboyConfig.streamerMode()) {
            items.add(UItem.asCheck(ID_OTHERS, LocaleController.getString(R.string.FemboyStreamerOthers))
                    .setChecked(FemboyConfig.streamerHidesOthers()));
            items.add(UItem.asCheck(ID_USERNAME, LocaleController.getString(R.string.FemboyStreamerUsername))
                    .setChecked(FemboyConfig.streamerHidesUsername()));
        }
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(LocaleController.getString(R.string.FemboyGhost)));
        items.add(UItem.asCheck(ID_GHOST, LocaleController.getString(R.string.FemboyGhostOn))
                .setChecked(FemboyConfig.ghostMode()));
        if (FemboyConfig.ghostMode()) {
            items.add(UItem.asCheck(ID_GHOST_READ, LocaleController.getString(R.string.FemboyGhostRead))
                    .setChecked(FemboyConfig.ghostHidesRead()));
            items.add(UItem.asCheck(ID_GHOST_ONLINE, LocaleController.getString(R.string.FemboyGhostOnline))
                    .setChecked(FemboyConfig.ghostHidesOnline()));
            items.add(UItem.asCheck(ID_GHOST_TYPING, LocaleController.getString(R.string.FemboyGhostTyping))
                    .setChecked(FemboyConfig.ghostHidesTyping()));
            items.add(UItem.asCheck(ID_GHOST_OFFLINE, LocaleController.getString(R.string.FemboyGhostOffline))
                    .setChecked(FemboyConfig.ghostOfflineMarker()));
            items.add(UItem.asCheck(ID_GHOST_MARK_SEND, LocaleController.getString(R.string.FemboyGhostMarkSend))
                    .setChecked(FemboyConfig.ghostMarkReadAfterSend()));
        }
        items.add(UItem.asShadow(LocaleController.getString(R.string.FemboyGhostInfo)));

        items.add(UItem.asCheck(ID_SCREENSHOTS, LocaleController.getString(R.string.FemboyAnyScreenshots))
                .setChecked(FemboyConfig.anyScreenshots()));
        items.add(UItem.asShadow(LocaleController.getString(R.string.FemboyAnyScreenshotsInfo)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_PROXY) {
            org.telegram.femboy.FemboyProxy.enable(!FemboyConfig.proxyEnabled());
            listView.adapter.update(true);
            return;
        }
        if (item.id == ID_ON) {
            FemboyConfig.setStreamerMode(!FemboyConfig.streamerMode());
        } else if (item.id == ID_OTHERS) {
            FemboyConfig.setStreamerHidesOthers(!FemboyConfig.streamerHidesOthers());
        } else if (item.id == ID_USERNAME) {
            FemboyConfig.setStreamerHidesUsername(!FemboyConfig.streamerHidesUsername());
        } else if (item.id == ID_GHOST) {
            FemboyConfig.setGhostMode(!FemboyConfig.ghostMode());
        } else if (item.id == ID_GHOST_READ) {
            FemboyConfig.setGhostHidesRead(!FemboyConfig.ghostHidesRead());
        } else if (item.id == ID_GHOST_ONLINE) {
            FemboyConfig.setGhostHidesOnline(!FemboyConfig.ghostHidesOnline());
        } else if (item.id == ID_GHOST_TYPING) {
            FemboyConfig.setGhostHidesTyping(!FemboyConfig.ghostHidesTyping());
        } else if (item.id == ID_GHOST_OFFLINE) {
            FemboyConfig.setGhostOfflineMarker(!FemboyConfig.ghostOfflineMarker());
        } else if (item.id == ID_GHOST_MARK_SEND) {
            FemboyConfig.setGhostMarkReadAfterSend(!FemboyConfig.ghostMarkReadAfterSend());
        } else if (item.id == ID_SCREENSHOTS) {
            FemboyConfig.setAnyScreenshots(!FemboyConfig.anyScreenshots());
        } else {
            return;
        }
        listView.adapter.update(true);
        if (preview != null) {
            preview.invalidate();
        }
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
