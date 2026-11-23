package com.exteragram.messenger.utils.chats;

import org.telegram.ui.ActionBar.BaseFragment;

import java.util.Map;

/** The context exteraGram hands to plugin items of the side menu. */
public class MainMenuHelper {
    public static final class MenuContext {
        private final int currentAccount;
        private final BaseFragment fragment;
        private final Runnable archiveClick;
        private final Map<String, Object> pluginContextData;

        public MenuContext(int currentAccount, BaseFragment fragment, Runnable archiveClick, Map<String, Object> pluginContextData) {
            this.currentAccount = currentAccount;
            this.fragment = fragment;
            this.archiveClick = archiveClick;
            this.pluginContextData = pluginContextData;
        }

        public int currentAccount() { return currentAccount; }
        public BaseFragment fragment() { return fragment; }
        public Runnable archiveClick() { return archiveClick; }
        public Map<String, Object> pluginContextData() { return pluginContextData; }
    }

    public static MenuContext createMenuContext(int account, BaseFragment fragment) {
        return new MenuContext(account, fragment, null, null);
    }
}
