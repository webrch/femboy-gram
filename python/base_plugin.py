"""Слой совместимости: плагины, написанные под exteraGram, в Femboy.

Их плагин — это класс, унаследованный от BasePlugin, и набор обёрток вокруг
той же самой java, что доступна и нам: у них и у нас питон живёт в одном и том
же приложении телеграма через Chaquopy. Поэтому переводить плагин целиком не
надо — достаточно положить рядом модули с их именами, которые делают то же
самое через femboy.

У каждого плагина свой femboy. Раньше он был один на всех, в глобальной
переменной, и второй поднятый плагин забирал его у первого: настройки,
меню и консоль первого начинали писаться от имени второго.
"""
import enum
import inspect
import logging
import sys
import threading
import traceback

from java import jclass

import plugin_settings
import file_utils as _file_utils

# У экстеры папка плагинов лежит в sys.path: плагины кладут туда свои модули
# (zwylib — zwylib_companion) и импортируют их оттуда.
try:
    _папка = _file_utils.get_plugins_dir()
    _file_utils.ensure_dir_exists(_папка)
    if _папка not in sys.path:
        sys.path.append(_папка)
except Exception:
    pass

_Hooks = jclass("org.telegram.femboy.FemboyHooks")
_Engine = jclass("org.telegram.femboy.FemboyHookEngine")

# femboy последнего поднятого плагина: для старого кода, который зовёт
# модульные функции без self
_мост = None
_классы = []
_живые = []


class AppEvent(enum.Enum):
    START = "app_start"
    STOP = "app_stop"
    PAUSE = "app_pause"
    RESUME = "app_resume"


class HookStrategy(enum.Enum):
    DEFAULT = "DEFAULT"
    CANCEL = "CANCEL"
    MODIFY = "MODIFY"
    MODIFY_FINAL = "MODIFY_FINAL"


class HookResult:
    def __init__(self, strategy=HookStrategy.DEFAULT, request=None, response=None,
                 update=None, updates=None, params=None, **прочее):
        self.strategy = strategy
        self.request = request
        self.response = response
        self.update = update
        self.updates = updates
        self.params = params
        for имя, значение in прочее.items():
            setattr(self, имя, значение)


def _стратегия(ответ):
    s = getattr(ответ, "strategy", HookStrategy.DEFAULT)
    if isinstance(s, HookStrategy):
        return s
    try:
        return HookStrategy(str(s).split(".")[-1].upper())
    except ValueError:
        return HookStrategy.DEFAULT


class PluginError(Exception):
    def __init__(self, *args):
        super().__init__(*args)


# --- меню ---

class MenuItemType(enum.Enum):
    CHAT_ACTION_MENU = "chat_action_menu"
    PROFILE_ACTION_MENU = "profile_action_menu"
    MESSAGE_CONTEXT_MENU = "message_context_menu"
    DRAWER_MENU = "drawer_menu"
    MAIN_MENU = "main_menu"
    CHAT_ATTACH_MENU = "chat_attach_menu"


# их место меню -> наше
_МЕСТА = {
    "chat_action_menu": "chat", "chat": "chat",
    "profile_action_menu": "profile", "profile": "profile",
    "message_context_menu": "message", "message": "message",
    "drawer_menu": "drawer", "main_menu": "drawer", "drawer": "drawer",
}


class MenuItemData:
    def __init__(self, menu_type, text, on_click=None, icon=None, subtext=None,
                 item_id=None, priority=0, condition=None, **прочее):
        self.menu_type = menu_type
        self.text = text
        self.on_click = on_click
        self.icon = icon
        self.subtext = subtext
        self.item_id = item_id
        self.priority = priority
        self.condition = condition


# --- хуки методов (xposed) ---

class BaseHook:
    """Хук из двух функций. У экстеры так собирают хук без своего класса:
    BaseHook(None, before=..., after=...). Метода нет — фазы нет."""

    def __init__(self, plugin=None, before=None, after=None, before_filters=None, after_filters=None):
        self.plugin = plugin
        if before is not None:
            self.before_hooked_method = _с_фильтрами(before, before_filters)
        if after is not None:
            self.after_hooked_method = _с_фильтрами(after, after_filters)


def _с_фильтрами(fn, filters):
    def обёртка(param):
        return fn(param)
    if filters:
        обёртка.__hook_filters__ = list(filters)
    return обёртка


class MethodHook:
    def before_hooked_method(self, param):
        pass

    def after_hooked_method(self, param):
        pass


XposedHook = MethodHook


class MethodReplacement:
    def replace_hooked_method(self, param):
        return None


class HookFilterData:
    def __init__(self, filter_type, arg_index=None, value=None, clazz=None, expression=None, filters=None):
        self.filter_type = filter_type
        self.arg_index = arg_index
        self.value = value
        self.clazz = clazz
        self.expression = expression
        self.filters = filters or []

    def to_java_filter(self, hook_instance=None):
        f = jclass("com.exteragram.messenger.plugins.hooks.HookFilter")(self.filter_type)
        if self.arg_index is not None:
            f.argIndex = jclass("java.lang.Integer").valueOf(int(self.arg_index))
        if self.clazz is not None:
            from hook_utils import _java_lang_class
            f.instanceOf = _java_lang_class(self.clazz)
        if self.expression is not None:
            f.mvelExpression = str(self.expression)
        if self.value is not None:
            f.object = self.value
        elif self.filter_type == "condition" and hook_instance is not None:
            f.object = hook_instance
        if self.filters:
            out = jclass("java.util.ArrayList")()
            for один in self.filters:
                out.add(один.to_java_filter(hook_instance) if isinstance(один, HookFilterData) else один)
            f.orFilters = out
        return f


class HookFilter:
    @staticmethod
    def ArgumentEqual(index, value):
        return HookFilterData("argument_equal", arg_index=index, value=value)

    @staticmethod
    def ArgumentNotEqual(index, value):
        return HookFilterData("argument_not_equal", arg_index=index, value=value)

    @staticmethod
    def ArgumentIsInstanceOf(index, clazz):
        return HookFilterData("argument_is_instance_of", arg_index=index, clazz=clazz)

    @staticmethod
    def ArgumentIsNull(index):
        return HookFilterData("argument_is_null", arg_index=index)

    @staticmethod
    def ArgumentNotNull(index):
        return HookFilterData("argument_not_null", arg_index=index)

    @staticmethod
    def ArgumentIsTrue(index):
        return HookFilterData("argument_is_true", arg_index=index)

    @staticmethod
    def ArgumentIsFalse(index):
        return HookFilterData("argument_is_false", arg_index=index)

    @staticmethod
    def ResultEqual(value):
        return HookFilterData("result_equal", value=value)

    @staticmethod
    def ResultNotEqual(value):
        return HookFilterData("result_not_equal", value=value)

    @staticmethod
    def ResultIsInstanceOf(clazz):
        return HookFilterData("result_is_instance_of", clazz=clazz)

    @staticmethod
    def ResultIsNull():
        return HookFilterData("result_is_null")

    @staticmethod
    def ResultNotNull():
        return HookFilterData("result_not_null")

    @staticmethod
    def ResultIsTrue():
        return HookFilterData("result_is_true")

    @staticmethod
    def ResultIsFalse():
        return HookFilterData("result_is_false")

    @staticmethod
    def Condition(expression, object=None):
        return HookFilterData("condition", expression=expression, value=object)

    @staticmethod
    def Or(*filters):
        return HookFilterData("or", filters=list(filters))


def hook_filters(*filters):
    """Фильтры на before_hooked_method / after_hooked_method: хук зовётся, только если все прошли."""
    def decorator(fn):
        fn.__hook_filters__ = list(filters)
        return fn
    return decorator


fn_hook_filters = hook_filters


def _java_фильтры(метод, hook_instance):
    filters = getattr(метод, "__hook_filters__", None)
    if filters is None:
        return None
    if callable(filters):
        filters = filters(hook_instance)
    out = jclass("java.util.ArrayList")()
    for f in filters or []:
        out.add(f.to_java_filter(hook_instance) if isinstance(f, HookFilterData) else f)
    return out


def _java_хук(plugin_id, hook, priority):
    """Питоновский хук -> java XC_MethodHook, как это делает сама экстера."""
    if hasattr(hook, "replace_hooked_method"):
        Замена = jclass("com.exteragram.messenger.plugins.xposed.PyMethodReplacement")
        return Замена(plugin_id, hook, int(priority))
    Хук = jclass("com.exteragram.messenger.plugins.xposed.PyMethodHook")
    готово = Хук(plugin_id, hook, int(priority))
    до = _java_фильтры(getattr(hook, "before_hooked_method", None), hook)
    if до is not None:
        готово.setBeforeHookedFilters(до)
    после = _java_фильтры(getattr(hook, "after_hooked_method", None), hook)
    if после is not None:
        готово.setAfterHookedFilters(после)
    return готово


# --- сам плагин ---

class Параметры:
    """Если приложение не отдало настоящий SendMessageParams, собираем похожий."""

    def __init__(self, message, peer):
        self.message = message
        self.peer = peer
        self.entities = None
        self.replyToMsg = None
        self.replyToTopMsg = None


class _Журнал(logging.Handler):
    def __init__(self, plugin):
        super().__init__()
        self.plugin = plugin

    def emit(self, record):
        try:
            текст = self.format(record)
            мост = self.plugin._мост
            if record.levelno >= logging.ERROR:
                мост.error(текст)
            else:
                мост.log(текст)
        except Exception:
            pass


class BasePlugin:
    def __init_subclass__(cls, **прочее):
        super().__init_subclass__(**прочее)
        # Модуль запоминаем по его globals, а не по имени: плагины экстеры
        # пишут свои метаданные прямо в __name__ модуля.
        try:
            cls._модуль_плагина = id(sys._getframe(1).f_globals)
        except Exception:
            cls._модуль_плагина = None
        _классы.append(cls)

    # то, что плагин переопределяет сам

    def on_plugin_load(self):
        pass

    def on_plugin_unload(self):
        pass

    def on_app_event(self, event_type):
        pass

    def create_settings(self):
        return []

    # служебное

    @property
    def _м(self):
        return self.__dict__.get("_мост") or _мост

    @property
    def id(self):
        своё = self.__dict__.get("_extera_id")
        if своё:
            return своё
        номер = str(self._м.id)
        return номер[len("extera."):] if номер.startswith("extera.") else номер

    @property
    def name(self):
        return self._м.name

    @property
    def stop_event(self):
        событие = self.__dict__.get("_stop_event")
        if событие is None:
            событие = threading.Event()
            self.__dict__["_stop_event"] = событие
        return событие

    @property
    def logger(self):
        журнал = self.__dict__.get("_logger")
        if журнал is None:
            журнал = logging.getLogger("plugin." + self.id)
            журнал.setLevel(logging.DEBUG)
            журнал.propagate = False
            обработчик = _Журнал(self)
            обработчик.setFormatter(logging.Formatter("%(levelname)s: %(message)s"))
            журнал.addHandler(обработчик)
            self.__dict__["_logger"] = журнал
        return журнал

    @property
    def client(self):
        from client_utils import AccountClient
        return AccountClient()

    # настройки

    def get_setting(self, key, default=None):
        сырое = self._м.get(key, None)
        if сырое is None:
            return default
        варианты = self.__dict__.get("_выборы", {}).get(key)
        if варианты is not None and not str(сырое).lstrip("-").isdigit():
            # выбор у нас хранится строкой варианта, у них номером
            try:
                return варианты.index(str(сырое))
            except ValueError:
                return default
        return plugin_settings.раскодировать(сырое, default)

    def set_setting(self, key, value, reload_settings=False):
        варианты = self.__dict__.get("_выборы", {}).get(key)
        if варианты is not None and isinstance(value, int) and not isinstance(value, bool) \
                and 0 <= value < len(варианты):
            value = варианты[value]
        self._м.set(key, plugin_settings.закодировать(value))
        if reload_settings:
            try:
                _настройки(self._м, self, self.create_settings() or [])
            except Exception:
                self._м.error(traceback.format_exc())

    def export_settings(self):
        return plugin_settings.get_all_settings(self._м.id)

    def import_settings(self, settings, reload_settings=True):
        for ключ, значение in (settings or {}).items():
            self.set_setting(ключ, значение)
        if reload_settings:
            try:
                _настройки(self._м, self, self.create_settings() or [])
            except Exception:
                self._м.error(traceback.format_exc())

    def log(self, *части):
        self._м.log(*части)

    def register_cleanup(self, fn):
        self.__dict__.setdefault("_уборка", []).append(fn)
        return fn

    def _cleanup(self):
        for fn in reversed(self.__dict__.get("_уборка", [])):
            try:
                fn()
            except Exception:
                self._м.error(traceback.format_exc())

    # меню

    def add_menu_item(self, data):
        тип = getattr(data, "menu_type", None)
        тип = getattr(тип, "value", тип)
        место = _МЕСТА.get(str(тип))
        if место is None:
            self._м.error("меню", тип, "у Femboy нет; строчка не добавлена")
            return None
        обработчик = data.on_click
        условие = getattr(data, "condition", None)
        ключ = str(getattr(data, "item_id", None) or data.text)

        def нажали(экран, предмет=None):
            if обработчик is None:
                return
            обработчик(_контекст(место, экран, предмет))

        if место in ("profile", "message"):
            self._м.menu(место, data.text, нажали, key=ключ)
        else:
            self._м.menu(место, data.text, lambda экран: нажали(экран), key=ключ)
        if условие is not None:
            self._м.log("условие показа строчки меню у Femboy не проверяется: она видна всегда")
        return ключ

    def remove_menu_item(self, item_id):
        self._м.error("убрать строчку меню на ходу Femboy не умеет; уберётся после перезапуска")
        return False

    # отправка и сеть

    def add_on_send_message_hook(self, priority=0):
        if not hasattr(self, "on_send_message_hook"):
            self._м.error("плагин попросил дверь отправки, но обработчика on_send_message_hook у него нет")
            return

        def отправляют(текст, куда):
            настоящие = _Hooks.sendingParams
            params = настоящие if настоящие is not None else Параметры(текст, куда)
            if настоящие is not None:
                params.message = текст
            from client_utils import account_scope, get_selected_account
            номер = get_selected_account()
            with account_scope(номер):
                ответ = self.on_send_message_hook(номер, params)
            if ответ is None:
                return None
            стратегия = _стратегия(ответ)
            if стратегия == HookStrategy.CANCEL:
                return False
            if стратегия in (HookStrategy.MODIFY, HookStrategy.MODIFY_FINAL):
                новые = getattr(ответ, "params", None) or params
                if новые is not params and настоящие is not None:
                    for поле in ("entities", "replyToMsg", "replyToTopMsg", "replyMarkup", "webPage"):
                        if hasattr(новые, поле):
                            try:
                                setattr(настоящие, поле, getattr(новые, поле))
                            except Exception:
                                pass
                return getattr(новые, "message", None)
            return None

        self._м.on_send(отправляют)

    def add_hook(self, name, match_substring=False, priority=0):
        """Перехват запросов и обновлений по имени класса TL."""
        имя = str(name)
        хвост = имя.split(".")[-1]

        def подходит(предмет):
            try:
                своё = str(предмет.getClass().getSimpleName())
            except Exception:
                return None
            if match_substring:
                return своё if имя in своё else None
            return своё if своё == имя or своё.endswith(хвост) else None

        from client_utils import account_scope, get_selected_account

        if hasattr(self, "pre_request_hook"):
            def запрос(предмет):
                своё = подходит(предмет)
                if своё is None:
                    return None
                номер = get_selected_account()
                with account_scope(номер):
                    return _ответ(_позвать(self.pre_request_hook, своё, номер, предмет), предмет, "request")
            self._м.on_request(запрос)

        if hasattr(self, "post_request_hook"):
            def ответ(запрос_, ответ_, ошибка):
                своё = подходит(запрос_)
                if своё is None:
                    return None
                номер = get_selected_account()
                with account_scope(номер):
                    return _ответ(_позвать(self.post_request_hook, своё, номер, ответ_, ошибка), ответ_, "response")
            self._м.on_answer(ответ)

        if hasattr(self, "on_update_hook") or hasattr(self, "on_updates_hook"):
            def обновление(предмет):
                своё = подходит(предмет)
                if своё is None:
                    return None
                контейнер = своё.startswith("TL_updates") or своё in ("TL_updateShort", "TL_updateShortMessage",
                                                                    "TL_updateShortChatMessage", "TL_updateShortSentMessage")
                обработчик = (getattr(self, "on_updates_hook", None) if контейнер else None) \
                    or getattr(self, "on_update_hook", None) or getattr(self, "on_updates_hook")
                номер = get_selected_account()
                with account_scope(номер):
                    поле = "updates" if контейнер else "update"
                    return _ответ(_позвать(обработчик, своё, номер, предмет), предмет, поле)
            self._м.on_update(обновление)

    def remove_hook(self, name):
        self._м.error("снять хук запросов на ходу Femboy не умеет; снимется после перезапуска")

    def add_intent_hook(self, *args, **kwargs):
        self._м.error("хуки интентов у Femboy не открыты")

    def remove_intent_hook(self, *args, **kwargs):
        pass

    def add_file_hook(self, *args, **kwargs):
        self._м.error("хуки файлов у Femboy не открыты")

    def remove_file_hook(self, *args, **kwargs):
        pass

    # хуки методов

    def _нужен_движок(self):
        if not _Engine.working():
            причина = "выключены в настройках" if not _Engine.enabled() else str(_Engine.failure() or "не поднялись")
            raise PluginError("хуки методов не работают: " + причина)

    def hook_method(self, method, hook, priority=10):
        self._нужен_движок()
        XposedBridge = jclass("de.robv.android.xposed.XposedBridge")
        снять = XposedBridge.hookMethod(method, _java_хук(self._м.id, hook, priority))
        self.__dict__.setdefault("_снятия", []).append(снять)
        return снять

    def hook_all_methods(self, clazz, method_name, hook, priority=10):
        self._нужен_движок()
        from hook_utils import _java_lang_class
        XposedBridge = jclass("de.robv.android.xposed.XposedBridge")
        снятия = list(XposedBridge.hookAllMethods(_java_lang_class(clazz), str(method_name),
                                                  _java_хук(self._м.id, hook, priority)).toArray())
        self.__dict__.setdefault("_снятия", []).extend(снятия)
        return снятия

    def hook_all_constructors(self, clazz, hook, priority=10):
        self._нужен_движок()
        from hook_utils import _java_lang_class
        XposedBridge = jclass("de.robv.android.xposed.XposedBridge")
        снятия = list(XposedBridge.hookAllConstructors(_java_lang_class(clazz),
                                                       _java_хук(self._м.id, hook, priority)).toArray())
        self.__dict__.setdefault("_снятия", []).extend(снятия)
        return снятия

    def unhook_method(self, unhook):
        if unhook is None:
            return
        for один in (unhook if isinstance(unhook, (list, tuple, set)) else [unhook]):
            try:
                один.unhook()
            except Exception:
                self._м.error(traceback.format_exc())


def _контекст(место, экран, предмет):
    """Словарь для on_click строчки меню, с теми ключами, что ждут их плагины."""
    from client_utils import get_selected_account
    контекст = {"account": get_selected_account(), "fragment": экран, "context": None}
    try:
        контекст["context"] = экран.getParentActivity() if экран is not None else None
    except Exception:
        pass
    try:
        if экран is not None and hasattr(экран, "getDialogId"):
            контекст["dialog_id"] = int(экран.getDialogId())
    except Exception:
        pass
    if место == "message":
        контекст["message"] = предмет
        try:
            контекст["dialog_id"] = int(предмет.getDialogId())
        except Exception:
            pass
    elif место == "profile" and предмет is not None:
        try:
            номер = int(предмет)
        except Exception:
            номер = None
        if номер is not None:
            from client_utils import get_messages_controller
            if номер > 0:
                контекст["userId"] = номер
                контекст["user"] = get_messages_controller().getUser(jclass("java.lang.Long").valueOf(номер))
            else:
                контекст["chatId"] = -номер
                контекст["chat"] = get_messages_controller().getChat(jclass("java.lang.Long").valueOf(-номер))
            контекст["dialog_id"] = номер
    return контекст


def _позвать(обработчик, имя, номер, *остальное):
    """Их хуки бывают с именем запроса первым доводом и без него."""
    try:
        доводов = len([p for p in inspect.signature(обработчик).parameters.values()
                       if p.kind in (p.POSITIONAL_ONLY, p.POSITIONAL_OR_KEYWORD)])
    except (TypeError, ValueError):
        доводов = len(остальное) + 2
    if доводов >= len(остальное) + 2:
        return обработчик(имя, номер, *остальное)
    return обработчик(номер, *остальное)


def _ответ(результат, исходное, поле):
    """Их HookResult — в то, что понимает femboy."""
    if результат is None:
        return None
    стратегия = _стратегия(результат)
    if стратегия == HookStrategy.CANCEL:
        return False
    if стратегия in (HookStrategy.MODIFY, HookStrategy.MODIFY_FINAL):
        for имя in (поле, "params"):
            подмена = getattr(результат, имя, None)
            if подмена is not None:
                return подмена
    return None


# --- подъём ---

def _глобалы_плагина(femboy):
    """globals модуля того плагина, которому принадлежит этот femboy."""
    кадр = sys._getframe(2)
    while кадр is not None:
        if кадр.f_globals.get("femboy") is femboy:
            return кадр.f_globals
        кадр = кадр.f_back
    return {}


def старт(femboy):
    """Поднять плагин экстеры. Зовётся из хвоста, который дописал конвертер."""
    global _мост
    _мост = femboy
    глоб = _глобалы_плагина(femboy)
    модуль = sys.modules.get("femboy_plugin_" + str(femboy.id))

    # плагин-библиотеку другие зовут по её номеру у экстеры: import zwylib
    номер = str(глоб.get("__id__") or femboy.id)
    if номер.startswith("extera."):
        номер = номер[len("extera."):]
    if модуль is not None and номер not in sys.modules:
        sys.modules[номер] = модуль

    try:
        jclass("com.exteragram.messenger.plugins.PluginsController").register(
            номер, str(глоб.get("__name__") or femboy.name), str(глоб.get("__version__") or ""),
            str(глоб.get("__author__") or ""), str(глоб.get("__description__") or ""))
    except Exception:
        pass

    свои = id(глоб)
    for класс in list(_классы):
        if getattr(класс, "_модуль_плагина", None) != свои or класс.__dict__.get("_поднят"):
            continue
        класс._поднят = True
        try:
            образец = класс.__new__(класс)
            образец.__dict__["_мост"] = femboy
            образец.__dict__["_extera_id"] = номер
            образец.__init__()
            _живые.append(образец)
            образец.on_plugin_load()
            строки = образец.create_settings()
            if строки:
                _настройки(femboy, образец, строки)
            образец.on_app_event(AppEvent.START)
        except Exception:
            femboy.error(traceback.format_exc())


def _настройки(femboy, образец, строки):
    """Их экран настроек — нашим femboy.settings."""
    наши = []
    выборы = {}
    при_смене = {}
    переключатели = set()
    _строки(femboy, строки, наши, выборы, при_смене, глубина=0, переключатели=переключатели)
    образец.__dict__["_выборы"] = выборы
    образец.__dict__["_переключатели"] = переключатели
    if при_смене and not образец.__dict__.get("_слушает"):
        образец.__dict__["_слушает"] = True

        def поменяли(ключ, значение):
            обработчик = образец.__dict__.get("_при_смене", {}).get(ключ)
            if обработчик is None:
                return
            варианты = образец.__dict__.get("_выборы", {}).get(ключ)
            if варианты is not None:
                try:
                    значение = варианты.index(str(значение))
                except ValueError:
                    pass
            elif значение in ("1", "0") and _это_переключатель(образец, ключ):
                значение = значение == "1"
            обработчик(значение)

        femboy.on_settings(поменяли)
    образец.__dict__["_при_смене"] = при_смене
    if наши:
        femboy.settings(*наши)


def _это_переключатель(образец, ключ):
    return ключ in образец.__dict__.get("_переключатели", ())


def _строки(femboy, строки, наши, выборы, при_смене, глубина, переключатели=None):
    for строка in строки or []:
        if строка is None:
            continue
        вид = type(строка).__name__
        ключ = getattr(строка, "key", None)
        название = getattr(строка, "text", None) or getattr(строка, "title", "") or ""
        про = getattr(строка, "subtext", None)
        смена = getattr(строка, "on_change", None)
        if ключ and смена is not None:
            при_смене[ключ] = смена
        if вид == "Header":
            наши.append(femboy.header(название))
        elif вид == "Divider":
            наши.append(femboy.note(про or название or ""))
        elif вид == "Switch":
            if переключатели is not None:
                переключатели.add(ключ)
            наши.append(femboy.switch(ключ, название,
                                        default=bool(getattr(строка, "default", False)),
                                        about=про))
        elif вид == "Selector":
            варианты = [str(x) for x in (getattr(строка, "items", []) or [])]
            выборы[ключ] = варианты
            номер = getattr(строка, "default", 0) or 0
            по_умолчанию = варианты[номер] if isinstance(номер, int) and 0 <= номер < len(варианты) else None
            наши.append(femboy.choice(ключ, название, варианты, default=по_умолчанию))
        elif вид in ("Input", "EditText"):
            наши.append(femboy.text(ключ, название or getattr(строка, "hint", "") or "",
                                      default=str(getattr(строка, "default", "") or ""),
                                      about=про))
        elif вид == "Text":
            нажатие = getattr(строка, "on_click", None)
            вложенные = getattr(строка, "create_sub_fragment", None)
            if нажатие is not None:
                наши.append(femboy.action(название, (lambda н=нажатие: _нажать(н)),
                                            key=ключ, danger=bool(getattr(строка, "red", False))))
            elif вложенные is not None and глубина < 3:
                # своих подэкранов у экрана настроек Femboy нет: раскрываем на месте
                наши.append(femboy.header(название))
                try:
                    _строки(femboy, вложенные(), наши, выборы, при_смене, глубина + 1, переключатели)
                except Exception:
                    femboy.error(traceback.format_exc())
            else:
                наши.append(femboy.note(str(название)))
        elif вид == "Custom":
            # своё java-представление строки у Femboy не рисуется
            continue
        else:
            наши.append(femboy.note(str(название)))


def _нажать(нажатие):
    try:
        нажатие(None)
    except TypeError:
        нажатие()
