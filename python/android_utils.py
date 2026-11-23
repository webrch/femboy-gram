"""Слой совместимости: android_utils экстеры."""
import traceback

from java import dynamic_proxy, jclass

_Android = jclass("org.telegram.messenger.AndroidUtilities")
_Runnable = jclass("java.lang.Runnable")
_OnClick = jclass("android.view.View$OnClickListener")
_OnLongClick = jclass("android.view.View$OnLongClickListener")


class R(dynamic_proxy(_Runnable)):
    """Питоновская функция как java Runnable. У них это называется R."""

    def __init__(self, fn, *args, **kwargs):
        super().__init__()
        self.fn = fn
        self.args = args
        self.kwargs = kwargs

    def run(self):
        try:
            self.fn(*self.args, **self.kwargs)
        except Exception:
            print(traceback.format_exc())


# старое имя внутри Femboy
_Работа = R


def run_on_ui_thread(func, delay=0):
    if delay:
        _Android.runOnUIThread(R(func), int(delay))
    else:
        _Android.runOnUIThread(R(func))


def log(*data):
    print(" ".join(str(d) for d in data))


def copy_to_clipboard(text):
    """Положить текст в буфер обмена. True, если получилось."""
    try:
        return bool(_Android.addToClipboard(str(text)))
    except Exception:
        try:
            контекст = get_application_context()
            буфер = контекст.getSystemService("clipboard")
            ClipData = jclass("android.content.ClipData")
            буфер.setPrimaryClip(ClipData.newPlainText("label", str(text)))
            return True
        except Exception:
            print(traceback.format_exc())
            return False


class OnClickListener(dynamic_proxy(_OnClick)):
    def __init__(self, fn):
        super().__init__()
        self.fn = fn

    def onClick(self, view):
        try:
            self.fn(view)
        except Exception:
            print(traceback.format_exc())


class OnLongClickListener(dynamic_proxy(_OnLongClick)):
    def __init__(self, fn):
        super().__init__()
        self.fn = fn

    def onLongClick(self, view):
        try:
            ответ = self.fn(view)
            return True if ответ is None else bool(ответ)
        except Exception:
            print(traceback.format_exc())
            return False


def get_application_context():
    return jclass("org.telegram.messenger.ApplicationLoader").applicationContext
