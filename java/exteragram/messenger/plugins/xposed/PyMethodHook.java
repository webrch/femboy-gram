package com.exteragram.messenger.plugins.xposed;

import com.chaquo.python.PyException;
import com.chaquo.python.PyObject;
import com.exteragram.messenger.plugins.PluginsConstants;
import com.exteragram.messenger.plugins.PluginsController;
import com.exteragram.messenger.plugins.hooks.HookFilter;

import org.telegram.femboy.FemboyPluginHost;

import java.util.ArrayList;

import de.robv.android.xposed.XC_MethodHook;

/**
 * Хук метода, которым из питона пользуются плагины экстеры. Перенесён с их
 * PyMethodHook.kt: питоновский объект с before_hooked_method и
 * after_hooked_method, фильтры до вызова питона, ошибки в консоль плагина.
 */
public class PyMethodHook extends XC_MethodHook implements AutoCloseable {

    private final String pluginId;
    private final PyObject before;
    private final PyObject after;
    private ArrayList<HookFilter> beforeFilters = new ArrayList<>();
    private ArrayList<HookFilter> afterFilters = new ArrayList<>();
    private volatile boolean disabled;

    public PyMethodHook(String pluginId, PyObject callback) {
        this(pluginId, callback, true, true);
    }

    public PyMethodHook(String pluginId, PyObject callback, int priority) {
        this(pluginId, callback, priority, true, true);
    }

    public PyMethodHook(String pluginId, PyObject callback, boolean hasBefore, boolean hasAfter) {
        super();
        if (callback == null) throw new IllegalArgumentException("Python callback object cannot be null");
        this.pluginId = pluginId;
        before = pick(callback, PluginsConstants.Xposed.BEFORE_HOOKED_METHOD, hasBefore);
        after = pick(callback, PluginsConstants.Xposed.AFTER_HOOKED_METHOD, hasAfter);
    }

    public PyMethodHook(String pluginId, PyObject callback, int priority, boolean hasBefore, boolean hasAfter) {
        super(priority);
        if (callback == null) throw new IllegalArgumentException("Python callback object cannot be null");
        this.pluginId = pluginId;
        before = pick(callback, PluginsConstants.Xposed.BEFORE_HOOKED_METHOD, hasBefore);
        after = pick(callback, PluginsConstants.Xposed.AFTER_HOOKED_METHOD, hasAfter);
    }

    private static PyObject pick(PyObject callback, String name, boolean wanted) {
        if (!wanted) return null;
        try {
            if (callback.containsKey(name)) return callback.get(name);
        } catch (Throwable ignored) {
        }
        return null;
    }

    public void setBeforeHookedFilters(ArrayList<HookFilter> filters) {
        beforeFilters = filters == null ? new ArrayList<>() : filters;
    }

    public void setAfterHookedFilters(ArrayList<HookFilter> filters) {
        afterFilters = filters == null ? new ArrayList<>() : filters;
    }

    public ArrayList<HookFilter> getBeforeHookedFilters() {
        return beforeFilters;
    }

    public ArrayList<HookFilter> getAfterHookedFilters() {
        return afterFilters;
    }

    @Override
    protected void beforeHookedMethod(MethodHookParam param) {
        run(before, beforeFilters, param, true, "before_hooked_method");
    }

    @Override
    protected void afterHookedMethod(MethodHookParam param) {
        run(after, afterFilters, param, false, "after_hooked_method");
    }

    private void run(PyObject hook, ArrayList<HookFilter> filters, MethodHookParam param, boolean isBefore, String where) {
        if (disabled || hook == null || !PluginsController.getInstance().isPluginActive(pluginId)) return;
        try {
            for (HookFilter filter : filters) {
                if (!filter.execute(param, isBefore)) return;
            }
            hook.call(param);
        } catch (Throwable t) {
            failed(where, t);
        }
    }

    private void failed(String where, Throwable t) {
        if (t instanceof PyException && t.getMessage() != null && t.getMessage().contains("closed")) {
            disabled = true;
            return;
        }
        FemboyPluginHost.log(pluginId, where + ": " + t, true);
    }

    @Override
    public void close() {
        disabled = true;
    }
}
