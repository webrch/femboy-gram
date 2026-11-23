package org.telegram.ui;

import android.view.View;

import org.telegram.femboy.FemboyConfig;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

/** Voice changer for voice messages and round videos. */
public class FemboyVoiceActivity extends UniversalFragment {

    private static final int ID_ENABLED = 1;
    private static final int ID_PITCH_0 = 10;
    private static final int ID_FILTER_0 = 20;
    private static final int ID_RESET = 99;

    private static final int[] PITCH_STRINGS = {
            R.string.FemboyVoicePitch0, R.string.FemboyVoicePitch1, R.string.FemboyVoicePitch2,
            R.string.FemboyVoicePitch3, R.string.FemboyVoicePitch4
    };
    private static final int[] FILTER_STRINGS = {
            R.string.FemboyVoiceFilter0, R.string.FemboyVoiceFilter1, R.string.FemboyVoiceFilter2
    };

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(R.string.FemboyVoiceTitle);
    }

    @Override
    public View createView(android.content.Context context) {
        final View view = super.createView(context);
        listView.setSections();
        return view;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asCheck(ID_ENABLED, LocaleController.getString(R.string.FemboyVoiceEnabled))
                .setChecked(FemboyConfig.voiceFxEnabled()));
        items.add(UItem.asShadow(LocaleController.getString(R.string.FemboyVoiceAbout)));

        items.add(UItem.asHeader(LocaleController.getString(R.string.FemboyVoicePitch)));
        for (int i = 0; i < PITCH_STRINGS.length; i++) {
            items.add(UItem.asRadio(ID_PITCH_0 + i, LocaleController.getString(PITCH_STRINGS[i]))
                    .setChecked(FemboyConfig.voicePitch() == i));
        }

        items.add(UItem.asHeader(LocaleController.getString(R.string.FemboyVoiceFilter)));
        for (int i = 0; i < FILTER_STRINGS.length; i++) {
            items.add(UItem.asRadio(ID_FILTER_0 + i, LocaleController.getString(FILTER_STRINGS[i]))
                    .setChecked(FemboyConfig.voiceFilter() == i));
        }

        items.add(UItem.asShadow(LocaleController.getString(R.string.FemboyVoiceNote)));
        items.add(UItem.asButton(ID_RESET, LocaleController.getString(R.string.FemboyVoiceReset)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_ENABLED) {
            FemboyConfig.setVoiceFxEnabled(!FemboyConfig.voiceFxEnabled());
        } else if (item.id >= ID_PITCH_0 && item.id < ID_PITCH_0 + PITCH_STRINGS.length) {
            FemboyConfig.setVoicePitch(item.id - ID_PITCH_0);
        } else if (item.id >= ID_FILTER_0 && item.id < ID_FILTER_0 + FILTER_STRINGS.length) {
            FemboyConfig.setVoiceFilter(item.id - ID_FILTER_0);
        } else if (item.id == ID_RESET) {
            FemboyConfig.setVoiceFxEnabled(false);
            FemboyConfig.setVoicePitch(2);
            FemboyConfig.setVoiceFilter(0);
        } else {
            return;
        }
        listView.adapter.update(true);
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
