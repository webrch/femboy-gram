'''Слой совместимости: file_utils экстеры.'''
import os
import traceback

from java import jclass

_Loader = jclass('org.telegram.messenger.ApplicationLoader')
_FileLoader = jclass('org.telegram.messenger.FileLoader')


def _context():
    return _Loader.applicationContext


def get_plugins_dir():
    '''Где лежат плагины. У Femboy каждый в своей папке внутри этой.'''
    return str(os.path.join(str(_context().getFilesDir().getAbsolutePath()), 'femboy_plugins'))


def get_cache_dir():
    return str(_context().getCacheDir().getAbsolutePath())


def get_files_dir():
    return str(_context().getFilesDir().getAbsolutePath())


def _media_dir(kind):
    try:
        папка = _FileLoader.getDirectory(kind)
        if папка is not None:
            return str(папка.getAbsolutePath())
    except Exception:
        pass
    return get_cache_dir()


def get_images_dir():
    return _media_dir(_FileLoader.MEDIA_DIR_IMAGE)


def get_videos_dir():
    return _media_dir(_FileLoader.MEDIA_DIR_VIDEO)


def get_audios_dir():
    return _media_dir(_FileLoader.MEDIA_DIR_AUDIO)


def get_documents_dir():
    return _media_dir(_FileLoader.MEDIA_DIR_DOCUMENT)


def ensure_dir_exists(path):
    try:
        os.makedirs(path, exist_ok=True)
        return True
    except Exception:
        print(traceback.format_exc())
        return False


def list_dir(path, recursive=False, include_files=True, include_dirs=False, extensions=None):
    if not os.path.isdir(path):
        return []
    if extensions:
        extensions = tuple(e.lower() if e.startswith('.') else '.' + e.lower() for e in extensions)
    out = []

    def годится(имя):
        return not extensions or имя.lower().endswith(extensions)

    if recursive:
        for корень, папки, файлы in os.walk(path):
            if include_dirs:
                out.extend(os.path.join(корень, п) for п in папки)
            if include_files:
                out.extend(os.path.join(корень, ф) for ф in файлы if годится(ф))
    else:
        for имя in os.listdir(path):
            полный = os.path.join(path, имя)
            if os.path.isdir(полный):
                if include_dirs:
                    out.append(полный)
            elif include_files and годится(имя):
                out.append(полный)
    return out


def read_file(path, encoding='utf-8'):
    try:
        with open(path, 'r', encoding=encoding) as f:
            return f.read()
    except Exception:
        print(traceback.format_exc())
        return None


def read_file_bytes(path):
    try:
        with open(path, 'rb') as f:
            return f.read()
    except Exception:
        print(traceback.format_exc())
        return None


def write_file(path, content, encoding='utf-8'):
    try:
        папка = os.path.dirname(path)
        if папка:
            os.makedirs(папка, exist_ok=True)
        with open(path, 'w', encoding=encoding) as f:
            f.write(content)
        return True
    except Exception:
        print(traceback.format_exc())
        return False


def write_file_bytes(path, content):
    try:
        папка = os.path.dirname(path)
        if папка:
            os.makedirs(папка, exist_ok=True)
        with open(path, 'wb') as f:
            f.write(content)
        return True
    except Exception:
        print(traceback.format_exc())
        return False


def delete_file(path):
    try:
        if os.path.isdir(path):
            import shutil
            shutil.rmtree(path)
        elif os.path.exists(path):
            os.remove(path)
        return True
    except Exception:
        print(traceback.format_exc())
        return False


class ExtensionAlreadyRegistered(Exception):
    def __init__(self, extension=''):
        super().__init__('расширение уже занято: %s' % extension)


class ExtensionNotRegistered(Exception):
    def __init__(self, extension=''):
        super().__init__('расширение не занято: %s' % extension)


class SecretInvalid(Exception):
    def __init__(self, *args):
        super().__init__('неверный ключ')


class FilesController:
    '''У экстеры через него плагин открывает свои типы файлов из чата.
    У Femboy такой двери нет; регистрация запоминается, но файлы по ней не открываются.'''
    SUPPORT_ICONS = False
    DIRECT_FILE_ICONS = False
    _занятые = {}

    @classmethod
    def register(cls, extension, *args, **kwargs):
        extension = str(extension).lower()
        if extension in cls._занятые:
            raise ExtensionAlreadyRegistered(extension)
        cls._занятые[extension] = (args, kwargs)
        print('file_utils: открытие файлов .%s плагином в Femboy не поддерживается' % extension)
        return extension

    @classmethod
    def unregister(cls, extension, *args, **kwargs):
        extension = str(extension).lower()
        if extension not in cls._занятые:
            raise ExtensionNotRegistered(extension)
        del cls._занятые[extension]
