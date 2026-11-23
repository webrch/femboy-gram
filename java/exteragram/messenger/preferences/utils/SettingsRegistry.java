package com.exteragram.messenger.preferences.utils;

import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.UItem;

/** exteraGram's links to single settings ("tg://extera/..."): FemboyGram has none, so there is no link. */
public class SettingsRegistry {
    private static final SettingsRegistry INSTANCE = new SettingsRegistry();

    public static SettingsRegistry getInstance() {
        return INSTANCE;
    }

    public String getFirstSettingLink(Class<? extends BaseFragment> cls, UItem item) {
        return null;
    }
}
