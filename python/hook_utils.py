'''Слой совместимости: hook_utils экстеры. Поиск классов и чужие закрытые поля.'''
import traceback

from java import jclass

_Class = jclass("java.lang.Class")
_JavaClassType = type(jclass("java.lang.Object"))


def _loader():
    return jclass("org.telegram.messenger.ApplicationLoader").applicationContext.getClassLoader()


def find_class(class_name, loader=None):
    '''Класс по имени или None, если такого нет.'''
    try:
        return jclass(str(class_name))
    except Exception:
        return None


def _java_lang_class(what):
    '''java.lang.Class из чего угодно: имени, питоновского jclass, объекта Class.'''
    if what is None:
        return None
    if isinstance(what, str):
        return _Class.forName(what, False, _loader())
    if isinstance(what, _Class):
        return what
    имя = getattr(what, "_chaquopy_name", None)
    if isinstance(what, type) and имя:
        return _Class.forName(str(имя), False, _loader())
    return what.getClass()


def _find_declared_field(klass, name):
    k = klass
    while k is not None:
        try:
            поле = k.getDeclaredField(str(name))
            поле.setAccessible(True)
            return поле
        except Exception:
            k = k.getSuperclass()
    return None


_SET = {"int": "setInt", "long": "setLong", "short": "setShort", "byte": "setByte",
        "float": "setFloat", "double": "setDouble", "boolean": "setBoolean", "char": "setChar"}


def _put(поле, obj, value):
    тип = поле.getType()
    if тип.isPrimitive():
        как = _SET.get(str(тип.getName()))
        if как == "setBoolean":
            value = bool(value)
        elif как in ("setFloat", "setDouble"):
            value = float(value)
        elif как == "setChar":
            value = str(value)[:1]
        elif как:
            value = int(value)
        getattr(поле, как)(obj, value)
    else:
        поле.set(obj, value)


def get_private_field(obj, field_name):
    try:
        поле = _find_declared_field(obj.getClass(), field_name)
        return None if поле is None else поле.get(obj)
    except Exception:
        print(traceback.format_exc())
        return None


def set_private_field(obj, field_name, new_value):
    try:
        поле = _find_declared_field(obj.getClass(), field_name)
        if поле is None:
            return False
        _put(поле, obj, new_value)
        return True
    except Exception:
        print(traceback.format_exc())
        return False


def get_static_private_field(clazz, field_name):
    try:
        поле = _find_declared_field(_java_lang_class(clazz), field_name)
        return None if поле is None else поле.get(None)
    except Exception:
        print(traceback.format_exc())
        return None


def set_static_private_field(clazz, field_name, new_value):
    try:
        поле = _find_declared_field(_java_lang_class(clazz), field_name)
        if поле is None:
            return False
        _put(поле, None, new_value)
        return True
    except Exception:
        print(traceback.format_exc())
        return False
