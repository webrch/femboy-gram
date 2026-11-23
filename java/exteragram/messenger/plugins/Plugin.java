package com.exteragram.messenger.plugins;

/** Плагин так, как его видит код экстеры. У Femboy это плагин, переведённый из .plugin. */
public class Plugin {
    private final String id;
    private String name;
    private String version;
    private String author;
    private String description;
    private boolean enabled;
    private String error;

    public Plugin(String id, String name, boolean enabled) {
        this(id, name, null, null, null, enabled);
    }

    public Plugin(String id, String name, String version, String author, String description, boolean enabled) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.author = author;
        this.description = description;
        this.enabled = enabled;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getVersion() { return version; }
    public String getAuthor() { return author; }
    public String getDescription() { return description; }
    public String getIcon() { return null; }
    public String getPack() { return null; }
    public int getIndex() { return -1; }
    public String getEngine() { return PluginsConstants.PYTHON; }
    public String getMinVersion() { return null; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean hasError() { return error != null; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public boolean hasSettings() { return false; }
}
