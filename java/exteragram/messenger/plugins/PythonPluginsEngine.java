package com.exteragram.messenger.plugins;

import java.io.File;

/**
 * Питоновский движок экстеры. У Femboy плагины поднимает свой хост, поэтому
 * здесь только то, о чём спрашивают плагины-библиотеки: плагин ли это файл.
 * Загрузить или выгрузить плагин на ходу Femboy не умеет, и здесь это видно.
 */
public class PythonPluginsEngine {

    public boolean isPlugin(File file, Object callback) {
        return file != null && file.getName().endsWith(PluginsConstants.PLUGINS_EXT);
    }

    public void loadPlugin(String id, String path) {
        throw new UnsupportedOperationException("Femboy поднимает плагины сама, на ходу не загружает: поставь файл .plugin и перезапусти");
    }

    public void unloadPlugin(String id) {
        throw new UnsupportedOperationException("Femboy выгружает плагины только перезапуском");
    }
}
