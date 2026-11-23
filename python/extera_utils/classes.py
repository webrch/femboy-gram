'''Слой совместимости: наследование java-классов из питона, как у экстеры.

    @java_subclass(FrameLayout)
    class Ячейка(Base):
        def on_post_init(self, context): ...
        @joverride()
        def onMeasure(self, w, h):
            super().onMeasure(w, h)

    ячейка = Ячейка.new_instance(context)   # ячейка.java — настоящий FrameLayout
    Ячейка.from_java(view)                   # обратно к питоновскому объекту

Мост питона сам наследует только интерфейсы, поэтому класс собирает на ходу
FemboySubclass через dexmaker, ровно как это делает экстера.
'''
import threading
import traceback

from java import dynamic_proxy, jarray, jclass

_Sub = jclass('org.telegram.femboy.FemboySubclass')
_Object = jclass('java.lang.Object')
_System = jclass('java.lang.System')

_живые = {}
_замок = threading.Lock()


def _массив(значения):
    return jarray(_Object)(list(значения))


def joverride(name=None):
    '''Метод переопределяет java-метод с тем же (или указанным) именем.'''
    if callable(name):
        name.__joverride__ = name.__name__
        return name

    def decorator(fn):
        fn.__joverride__ = name or fn.__name__
        return fn
    return decorator


def jmethod(*args, **kwargs):
    return joverride(*args, **kwargs)


class _Цель(dynamic_proxy(_Sub.Target)):
    def __init__(self, обёртка):
        super().__init__()
        self.обёртка = обёртка

    def invoke(self, сам, метод, доводы):
        обёртка = self.обёртка
        if обёртка._java is None:
            обёртка._java = сам
        функция = type(обёртка)._переопределено.get(str(метод.getName()))
        if функция is None:
            return _Sub.callSuper(сам, метод, доводы)
        try:
            ответ = функция(обёртка, *list(доводы))
        except Exception:
            print(traceback.format_exc())
            ответ = None
        return _Sub.result(ответ, метод.getReturnType())


def _ключ(java):
    return int(_System.identityHashCode(java))


class Base:
    '''Питоновская половина наследника. Всё, чего нет у питона, ищется у java.'''

    _java_base = None
    _переопределено = {}

    def __init__(self, *args, **kwargs):
        self.__dict__['_java'] = None

    @classmethod
    def extends(cls, base):
        def decorator(sub):
            return java_subclass(base)(sub)
        return decorator

    def on_pre_init(self, *args, **kwargs):
        pass

    def on_post_init(self, *args, **kwargs):
        pass

    def on_release(self):
        pass

    @property
    def java(self):
        return self.__dict__.get('_java')

    @property
    def this(self):
        return self.__dict__.get('_java')

    @classmethod
    def java_class(cls):
        return cls._java_base

    @classmethod
    def new_instance(cls, *args, **kwargs):
        обёртка = cls.__new__(cls)
        обёртка.__dict__['_java'] = None
        try:
            обёртка.on_pre_init(*args, **kwargs)
        except TypeError:
            обёртка.on_pre_init()
        имена = jarray('java.lang.String')(list(cls._переопределено.keys()))
        java = _Sub.create(cls._java_base, _массив(args), имена, _Цель(обёртка))
        обёртка.__dict__['_java'] = java
        with _замок:
            _живые[_ключ(java)] = обёртка
        try:
            обёртка.on_post_init(*args, **kwargs)
        except TypeError:
            обёртка.on_post_init()
        return обёртка

    @classmethod
    def new_java_instance(cls, *args, **kwargs):
        return cls.new_instance(*args, **kwargs).java

    @classmethod
    def from_java(cls, java):
        if java is None:
            return None
        with _замок:
            return _живые.get(_ключ(java))

    def jrelease(self):
        java = self.__dict__.get('_java')
        if java is not None:
            with _замок:
                _живые.pop(_ключ(java), None)
        try:
            self.on_release()
        except Exception:
            print(traceback.format_exc())

    def __getattr__(self, имя):
        if имя.startswith('__') or имя == '_java':
            raise AttributeError(имя)
        java = self.__dict__.get('_java')
        if java is None:
            raise AttributeError(имя)
        return getattr(java, имя)


def _супер(имя):
    def вызвать(self, *args):
        java = self.__dict__.get('_java')
        if java is None:
            raise RuntimeError('java-объект ещё не создан')
        методы = _Sub.methods(type(self)._java_base, имя)
        доводы = _массив(args)
        for метод in методы:
            if _Sub.fits(метод.getParameterTypes(), доводы):
                return _Sub.callSuper(java, метод, доводы)
        raise TypeError('у родителя нет %s на %d доводов' % (имя, len(args)))
    вызвать.__name__ = имя
    return вызвать


_супер_классы = {}


def _java_lang_class(base):
    from hook_utils import _java_lang_class as найти
    return найти(base)


def java_subclass(base, *interfaces):
    '''Сделать питоновский класс наследником java-класса base.'''
    класс_java = _java_lang_class(base)

    def decorator(наш):
        имя_базы = str(класс_java.getName())
        супер = _супер_классы.get(имя_базы)
        if супер is None:
            # super().метод(...) внутри наследника находит эти заглушки и
            # зовёт метод родителя у java
            члены = {str(n): _супер(str(n)) for n in _Sub.names(класс_java)
                     if not str(n).startswith('__')}
            супер = type('_Super_' + str(класс_java.getSimpleName()), (Base,), члены)
            _супер_классы[имя_базы] = супер
        переопределено = {}
        for k in reversed(наш.__mro__):
            for ключ, значение in vars(k).items():
                имя = getattr(значение, '__joverride__', None)
                if имя:
                    переопределено[имя] = значение
        готовый = type(наш.__name__, (наш, супер), {
            '_java_base': класс_java,
            '_переопределено': переопределено,
            '__module__': наш.__module__,
            '__qualname__': наш.__qualname__,
        })
        return готовый

    return decorator


def class_proxy(*args, **kwargs):
    return java_subclass(*args, **kwargs)
