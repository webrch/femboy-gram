'''Слой совместимости: plugin_settings экстеры.

Настройки плагина лежат там же, где у всех плагинов Femboy: в его памяти,
строками. Переключатели хранятся как "1"/"0" (их так пишет экран настроек),
обычный текст как есть, всё остальное как json с меткой в начале.
'''
import json
import threading

from java import jclass

_Host = jclass('org.telegram.femboy.FemboyPluginHost')
_МЕТКА = '\u0001'
_lock = threading.Lock()
_settings_cache = {}


def наш_номер(plugin_id):
    '''Номер плагина у Femboy: переведённые из экстеры живут под extera.<их номер>.'''
    plugin_id = str(plugin_id)
    return plugin_id if plugin_id.startswith('extera.') else 'extera.' + plugin_id


def закодировать(value):
    if value is None:
        return None
    if isinstance(value, bool):
        return '1' if value else '0'
    if isinstance(value, str):
        return value
    try:
        return _МЕТКА + json.dumps(value, ensure_ascii=False)
    except Exception:
        return str(value)


def раскодировать(raw, default=None):
    if raw is None:
        return default
    raw = str(raw)
    if raw.startswith(_МЕТКА):
        try:
            return json.loads(raw[1:])
        except Exception:
            return default
    if isinstance(default, bool):
        return raw in ('1', 'true', 'True')
    if isinstance(default, int):
        try:
            return int(raw)
        except ValueError:
            try:
                return int(float(raw))
            except ValueError:
                return default
    if isinstance(default, float):
        try:
            return float(raw)
        except ValueError:
            return default
    return raw


def init(*args, **kwargs):
    pass


def _prefs(plugin_id):
    return jclass('org.telegram.messenger.ApplicationLoader').applicationContext.getSharedPreferences(
        'femboy_plugin_' + наш_номер(plugin_id), 0)


def get_setting(plugin_id, key, default=None):
    return раскодировать(_Host.get(наш_номер(plugin_id), str(key), None), default)


def set_setting(plugin_id, key, value):
    _Host.set(наш_номер(plugin_id), str(key), закодировать(value))


def get_all_settings(plugin_id):
    out = {}
    всё = _prefs(plugin_id).getAll()
    for ключ in всё.keySet().toArray():
        out[str(ключ)] = раскодировать(всё.get(ключ))
    return out


def set_all_settings(plugin_id, settings):
    for ключ, значение in (settings or {}).items():
        set_setting(plugin_id, ключ, значение)


def clear_settings(plugin_id):
    _prefs(plugin_id).edit().clear().apply()


def _load_settings_from_file(*args, **kwargs):
    return {}


def _save_settings_to_file(*args, **kwargs):
    pass


def _restore_plugin_settings(*args, **kwargs):
    pass
