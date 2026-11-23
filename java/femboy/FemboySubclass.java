package org.telegram.femboy;

import com.android.dx.stock.ProxyBuilder;

import org.telegram.messenger.ApplicationLoader;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Наследник java-класса, написанный на питоне: то, что у экстеры зовётся
 * java_subclass. Мост питона сам наследует только интерфейсы, поэтому
 * класс-наследник собирается на ходу dexmaker'ом, как у них. Все методы
 * проходят через обработчик, но в питон уходят только те, что плагин
 * переопределил; остальные сразу идут к родителю.
 */
public final class FemboySubclass {

    private FemboySubclass() {}

    /** Питоновская сторона: переопределённый метод. */
    public interface Target {
        Object invoke(Object self, Method method, Object[] args) throws Throwable;
    }

    private static final class Handler implements InvocationHandler {
        final Set<String> names;
        final Target target;

        Handler(Set<String> names, Target target) {
            this.names = names;
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (names.contains(method.getName())) {
                return target.invoke(proxy, method, args == null ? new Object[0] : args);
            }
            if (Modifier.isAbstract(method.getModifiers())) {
                return defaultValue(method.getReturnType());
            }
            return ProxyBuilder.callSuper(proxy, method, args);
        }
    }

    private static File cache() {
        File dir = new File(ApplicationLoader.applicationContext.getCodeCacheDir(), "femboy_classes");
        dir.mkdirs();
        return dir;
    }

    public static Object create(Class<?> base, Object[] args, String[] overridden, Target target) throws Throwable {
        if (args == null) args = new Object[0];
        Set<String> names = new HashSet<>(Arrays.asList(overridden == null ? new String[0] : overridden));
        Handler handler = new Handler(names, target);
        ProxyBuilder<?> builder;
        if (base.isInterface()) {
            builder = ProxyBuilder.forClass(Object.class).implementing(base);
        } else {
            builder = ProxyBuilder.forClass(base);
            Constructor<?> constructor = pick(base, args);
            if (constructor == null) {
                throw new IllegalArgumentException("у " + base.getName() + " нет конструктора на " + args.length + " доводов подходящих типов");
            }
            Class<?>[] types = constructor.getParameterTypes();
            Object[] values = new Object[args.length];
            for (int i = 0; i < args.length; i++) values[i] = coerce(args[i], types[i]);
            builder.constructorArgTypes(types).constructorArgValues(values);
        }
        builder.parentClassLoader(FemboySubclass.class.getClassLoader()).dexCache(cache()).handler(handler);
        return builder.build();
    }

    public static Object callSuper(Object proxy, Method method, Object[] args) throws Throwable {
        Class<?>[] types = method.getParameterTypes();
        Object[] values = new Object[types.length];
        for (int i = 0; i < types.length && args != null && i < args.length; i++) values[i] = coerce(args[i], types[i]);
        if (Modifier.isFinal(method.getModifiers()) || method.getDeclaringClass().isInterface()) {
            // у final-методов нет копии родителя: они и так не переопределены
            method.setAccessible(true);
            return method.invoke(proxy, values);
        }
        return ProxyBuilder.callSuper(proxy, method, values);
    }

    /** Методы с этим именем у класса и его родителей, открытые и защищённые. */
    public static Method[] methods(Class<?> base, String name) {
        java.util.ArrayList<Method> out = new java.util.ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        for (Class<?> k = base; k != null; k = k.getSuperclass()) {
            for (Method m : k.getDeclaredMethods()) {
                if (!m.getName().equals(name)) continue;
                int mod = m.getModifiers();
                if (Modifier.isPrivate(mod) || Modifier.isStatic(mod)) continue;
                String key = Arrays.toString(m.getParameterTypes());
                if (seen.add(key)) {
                    m.setAccessible(true);
                    out.add(m);
                }
            }
        }
        for (Class<?> i : base.getInterfaces()) {
            for (Method m : i.getMethods()) {
                if (m.getName().equals(name) && seen.add(Arrays.toString(m.getParameterTypes()))) out.add(m);
            }
        }
        return out.toArray(new Method[0]);
    }

    /** Имена всех методов, которые можно переопределить: из них собирается super() в питоне. */
    public static String[] names(Class<?> base) {
        HashSet<String> out = new HashSet<>();
        for (Class<?> k = base; k != null; k = k.getSuperclass()) {
            for (Method m : k.getDeclaredMethods()) {
                int mod = m.getModifiers();
                if (!Modifier.isPrivate(mod) && !Modifier.isStatic(mod) && !Modifier.isFinal(mod)) out.add(m.getName());
            }
        }
        for (Method m : base.getMethods()) {
            if (!Modifier.isFinal(m.getModifiers()) && !Modifier.isStatic(m.getModifiers())) out.add(m.getName());
        }
        return out.toArray(new String[0]);
    }

    /** Подходит ли набор доводов под типы: для выбора перегрузки из питона. */
    public static boolean fits(Class<?>[] types, Object[] args) {
        if (args == null) args = new Object[0];
        if (types.length != args.length) return false;
        for (int i = 0; i < types.length; i++) {
            if (!fits(types[i], args[i])) return false;
        }
        return true;
    }

    private static Constructor<?> pick(Class<?> base, Object[] args) {
        for (Constructor<?> c : base.getDeclaredConstructors()) {
            if (Modifier.isPrivate(c.getModifiers())) continue;
            if (fits(c.getParameterTypes(), args)) return c;
        }
        return null;
    }

    private static boolean fits(Class<?> type, Object value) {
        if (value == null) return !type.isPrimitive();
        Class<?> boxed = box(type);
        if (boxed.isInstance(value)) return true;
        if (value instanceof Number && Number.class.isAssignableFrom(boxed)) return true;
        if (value instanceof Number && boxed == Character.class) return true;
        if (value instanceof String && (boxed == Character.class || type == CharSequence.class)) return true;
        return false;
    }

    private static Object coerce(Object value, Class<?> type) {
        if (value == null) return null;
        Class<?> boxed = box(type);
        if (boxed.isInstance(value)) return value;
        if (value instanceof Number) {
            Number n = (Number) value;
            if (boxed == Integer.class) return n.intValue();
            if (boxed == Long.class) return n.longValue();
            if (boxed == Float.class) return n.floatValue();
            if (boxed == Double.class) return n.doubleValue();
            if (boxed == Short.class) return n.shortValue();
            if (boxed == Byte.class) return n.byteValue();
            if (boxed == Character.class) return (char) n.intValue();
        }
        if (value instanceof String && boxed == Character.class && ((String) value).length() > 0) {
            return ((String) value).charAt(0);
        }
        return value;
    }

    /** Ответ питона под тип, который ждёт java: int из питона приходит как Long. */
    public static Object result(Object value, Class<?> type) {
        if (type == void.class || type == Void.class) return null;
        if (value == null) return defaultValue(type);
        return coerce(value, type);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return (char) 0;
        return coerce(0, type);
    }

    private static Class<?> box(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == boolean.class) return Boolean.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == short.class) return Short.class;
        if (type == byte.class) return Byte.class;
        if (type == char.class) return Character.class;
        return Void.class;
    }
}
