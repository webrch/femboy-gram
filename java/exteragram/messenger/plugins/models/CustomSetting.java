package com.exteragram.messenger.plugins.models;

import android.content.Context;
import android.view.View;

import com.exteragram.messenger.plugins.Plugin;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

/**
 * Своя строка настроек экстеры. Своих фабрик экран настроек Femboy не строит,
 * но класс должен быть: плагины-библиотеки наследуют Factory.
 */
public class CustomSetting {

    public UItem item;
    public Factory<?> factory;
    public Object factoryArgs;

    public abstract static class Factory<V extends View> extends UItem.UItemFactory<V> {
        public boolean isShadowValue = false;
        public boolean isClickableValue = true;

        public UItem create(Plugin plugin, CustomSetting setting, Object args) {
            return null;
        }

        public void onClick(Plugin plugin, UItem item, View view) {
        }

        public void onLongClick(Plugin plugin, UItem item, View view) {
        }

        @Override
        public boolean isShadow() {
            return isShadowValue;
        }

        @Override
        public boolean isClickable() {
            return isClickableValue;
        }
    }
}
