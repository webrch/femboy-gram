package com.exteragram.messenger.plugins.xposed;

import com.chaquo.python.PyObject;
import com.exteragram.messenger.plugins.PluginsConstants;
import com.exteragram.messenger.plugins.PluginsController;

import org.telegram.femboy.FemboyPluginHost;

import java.lang.reflect.InvocationTargetException;

import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;

/** Подмена метода целиком: питоновский replace_hooked_method вместо оригинала. */
public class PyMethodReplacement extends XC_MethodReplacement implements AutoCloseable {

    private final String pluginId;
    private final PyObject replace;
    private volatile boolean disabled;

    public PyMethodReplacement(String pluginId, PyObject callback) {
        super();
        this.pluginId = pluginId;
        this.replace = need(callback);
    }

    public PyMethodReplacement(String pluginId, PyObject callback, int priority) {
        super(priority);
        this.pluginId = pluginId;
        this.replace = need(callback);
    }

    private static PyObject need(PyObject callback) {
        if (callback == null) throw new IllegalArgumentException("Python callback object cannot be null");
        if (!callback.containsKey(PluginsConstants.Xposed.REPLACE_HOOKED_METHOD)) {
            throw new IllegalArgumentException("Python callback object must contain a method named 'replace_hooked_method'");
        }
        return callback.get(PluginsConstants.Xposed.REPLACE_HOOKED_METHOD);
    }

    @Override
    protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
        if (disabled || !PluginsController.getInstance().isPluginActive(pluginId)) {
            try {
                return XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args);
            } catch (InvocationTargetException e) {
                throw e.getTargetException() != null ? e.getTargetException() : e;
            }
        }
        try {
            PyObject result = replace.call(param);
            return result == null ? null : result.toJava(Object.class);
        } catch (Throwable t) {
            FemboyPluginHost.log(pluginId, "replace_hooked_method: " + t, true);
            return null;
        }
    }

    @Override
    public void close() {
        disabled = true;
    }
}
