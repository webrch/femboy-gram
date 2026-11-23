package com.exteragram.messenger.plugins;

import org.telegram.messenger.FileLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.Utilities;

import java.util.HashMap;
import java.util.HashSet;

/**
 * Контроллер плагинов экстеры, в том объёме, в каком его трогают плагины.
 * Список заполняет наш base_plugin, когда поднимает плагин.
 */
public class PluginsController {

    private static final PluginsController INSTANCE = new PluginsController();

    public static final HashMap<String, PythonPluginsEngine> engines = new HashMap<>();

    static {
        engines.put(PluginsConstants.PYTHON, new PythonPluginsEngine());
    }

    public final HashMap<String, Plugin> plugins = new HashMap<>();
    private final HashSet<String> stopped = new HashSet<>();

    public static PluginsController getInstance() {
        return INSTANCE;
    }

    public static HashMap<String, PythonPluginsEngine> getEngines() {
        return engines;
    }

    public HashMap<String, Plugin> getPlugins() {
        return plugins;
    }

    public Plugin getPlugin(String id) {
        return plugins.get(id);
    }

    public static void register(String id, String name, String version, String author, String description) {
        synchronized (INSTANCE) {
            INSTANCE.plugins.put(id, new Plugin(id, name, version, author, description, true));
            INSTANCE.stopped.remove(id);
        }
    }

    public static void markStopped(String id) {
        synchronized (INSTANCE) {
            INSTANCE.stopped.add(id);
            Plugin plugin = INSTANCE.plugins.get(id);
            if (plugin != null) plugin.setEnabled(false);
        }
    }

    public boolean isPluginActive(String id) {
        synchronized (this) {
            return !stopped.contains(id);
        }
    }

    public boolean isPluginEnabled(String id) {
        Plugin plugin = plugins.get(id);
        return plugin != null && plugin.isEnabled();
    }

    public static boolean isPlugin(MessageObject message) {
        if (message == null || message.getDocument() == null) return false;
        String name = FileLoader.getDocumentFileName(message.getDocument());
        return name != null && name.toLowerCase().endsWith(PluginsConstants.PLUGINS_EXT);
    }

    public void loadPluginSettings(String id) {
    }

    public void setPluginEnabled(String id, boolean enabled, Utilities.Callback<String> done) {
        Plugin plugin = plugins.get(id);
        if (plugin != null) plugin.setEnabled(enabled);
        if (done != null) done.run(null);
    }
}
