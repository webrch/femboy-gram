package com.exteragram.messenger.plugins.hooks;

import com.exteragram.messenger.plugins.PluginsConstants.HookFilterTypes;

import org.mvel2.MVEL;
import org.telegram.messenger.FileLog;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

import de.robv.android.xposed.XC_MethodHook;

/**
 * Фильтр хука экстеры: решает до питона, звать ли его вообще. Так хук на
 * горячий метод не будит питон на каждый вызов. Перенесено с их HookFilter.kt
 * один к одному, условие так же считает MVEL.
 */
public class HookFilter {

    public final String filterType;
    public Integer argIndex;
    public ArrayList<HookFilter> orFilters;
    public String mvelExpression;
    public Class<?> instanceOf;
    public Object object;

    private String compiledSource;
    private Serializable compiled;

    public HookFilter(String filterType) {
        this.filterType = filterType;
    }

    public String getFilterType() { return filterType; }
    public Integer getArgIndex() { return argIndex; }
    public void setArgIndex(Integer v) { argIndex = v; }
    public ArrayList<HookFilter> getOrFilters() { return orFilters; }
    public void setOrFilters(ArrayList<HookFilter> v) { orFilters = v; }
    public String getMvelExpression() { return mvelExpression; }
    public void setMvelExpression(String v) { mvelExpression = v; }
    public Class<?> getInstanceOf() { return instanceOf; }
    public void setInstanceOf(Class<?> v) { instanceOf = v; }
    public Object getObject() { return object; }
    public void setObject(Object v) { object = v; }

    public boolean execute(XC_MethodHook.MethodHookParam param, boolean isBefore) {
        try {
            switch (filterType) {
                case HookFilterTypes.OR:
                    if (orFilters != null) {
                        for (HookFilter f : orFilters) {
                            if (f.execute(param, isBefore)) return true;
                        }
                    }
                    return false;
                case HookFilterTypes.CONDITION: {
                    if (mvelExpression == null) return false;
                    Serializable expression = compiled(mvelExpression);
                    if (expression == null) return false;
                    HashMap<String, Object> vars = new HashMap<>(4);
                    vars.put("param", param);
                    vars.put("result", isBefore ? null : param.getResult());
                    vars.put("object", object);
                    Boolean result = MVEL.executeExpression(expression, param.thisObject, vars, Boolean.class);
                    return result != null && result;
                }
                case HookFilterTypes.ARGUMENT_EQUAL:
                case HookFilterTypes.ARGUMENT_NOT_EQUAL:
                case HookFilterTypes.ARGUMENT_IS_INSTANCE_OF:
                case HookFilterTypes.ARGUMENT_IS_FALSE:
                case HookFilterTypes.ARGUMENT_IS_NULL:
                case HookFilterTypes.ARGUMENT_IS_TRUE:
                case HookFilterTypes.ARGUMENT_NOT_NULL: {
                    Object[] args = param.args;
                    if (argIndex == null || args == null || argIndex < 0 || argIndex >= args.length) return false;
                    Object arg = args[argIndex];
                    switch (filterType) {
                        case HookFilterTypes.ARGUMENT_EQUAL: return same(arg, object);
                        case HookFilterTypes.ARGUMENT_NOT_EQUAL: return !same(arg, object);
                        case HookFilterTypes.ARGUMENT_IS_INSTANCE_OF: return instanceOf != null && instanceOf.isInstance(arg);
                        case HookFilterTypes.ARGUMENT_IS_FALSE: return arg instanceof Boolean && !(Boolean) arg;
                        case HookFilterTypes.ARGUMENT_IS_NULL: return arg == null;
                        case HookFilterTypes.ARGUMENT_IS_TRUE: return arg instanceof Boolean && (Boolean) arg;
                        default: return arg != null;
                    }
                }
                case HookFilterTypes.RESULT_EQUAL:
                case HookFilterTypes.RESULT_NOT_EQUAL:
                case HookFilterTypes.RESULT_IS_FALSE:
                case HookFilterTypes.RESULT_IS_INSTANCE_OF:
                case HookFilterTypes.RESULT_IS_NULL:
                case HookFilterTypes.RESULT_IS_TRUE:
                case HookFilterTypes.RESULT_NOT_NULL: {
                    if (isBefore) return false;
                    Object result = param.getResult();
                    switch (filterType) {
                        case HookFilterTypes.RESULT_EQUAL: return same(result, object);
                        case HookFilterTypes.RESULT_NOT_EQUAL: return !same(result, object);
                        case HookFilterTypes.RESULT_IS_FALSE: return result instanceof Boolean && !(Boolean) result;
                        case HookFilterTypes.RESULT_IS_TRUE: return result instanceof Boolean && (Boolean) result;
                        case HookFilterTypes.RESULT_IS_INSTANCE_OF: return instanceOf != null && instanceOf.isInstance(result);
                        case HookFilterTypes.RESULT_IS_NULL: return result == null;
                        default: return result != null;
                    }
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return false;
    }

    private synchronized Serializable compiled(String expression) {
        if (expression.equals(compiledSource)) return compiled;
        Serializable made = null;
        try {
            made = MVEL.compileExpression(expression);
        } catch (Exception e) {
            FileLog.e(e);
        }
        compiledSource = expression;
        compiled = made;
        return made;
    }

    private static boolean same(Object a, Object b) {
        if (a == b) return true;
        if (a != null && a.equals(b)) return true;
        if (a instanceof Number && b instanceof Number) {
            if (a instanceof Double || a instanceof Float || b instanceof Double || b instanceof Float) {
                return ((Number) a).doubleValue() == ((Number) b).doubleValue();
            }
            return ((Number) a).longValue() == ((Number) b).longValue();
        }
        return false;
    }
}
