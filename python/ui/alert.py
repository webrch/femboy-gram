"""Слой совместимости: окна экстеры.

AlertDialogBuilder у них — обёртка над AlertDialog.Builder телеграма, и здесь
то же самое. Обработчики кнопок получают (этот builder, номер кнопки), как у них.
"""
import traceback

from java import dynamic_proxy, jarray, jclass

_Alert = jclass("org.telegram.ui.ActionBar.AlertDialog")
_Builder = jclass("org.telegram.ui.ActionBar.AlertDialog$Builder")
_OnButton = jclass("org.telegram.ui.ActionBar.AlertDialog$OnButtonClickListener")
_OnClick = jclass("android.content.DialogInterface$OnClickListener")
_OnDismiss = jclass("android.content.DialogInterface$OnDismissListener")
_OnCancel = jclass("android.content.DialogInterface$OnCancelListener")


def _позвать(fn, *args):
    if fn is None:
        return
    try:
        fn(*args)
    except TypeError:
        try:
            fn()
        except Exception:
            print(traceback.format_exc())
    except Exception:
        print(traceback.format_exc())


class _ButtonClickListenerProxy(dynamic_proxy(_OnButton)):
    def __init__(self, builder, fn):
        super().__init__()
        self.builder = builder
        self.fn = fn

    def onClick(self, dialog, which):
        _позвать(self.fn, self.builder, which)


class _ItemsClickListenerProxy(dynamic_proxy(_OnClick)):
    def __init__(self, builder, fn):
        super().__init__()
        self.builder = builder
        self.fn = fn

    def onClick(self, dialog, which):
        _позвать(self.fn, self.builder, which)


class _DismissListenerProxy(dynamic_proxy(_OnDismiss)):
    def __init__(self, builder, fn):
        super().__init__()
        self.builder = builder
        self.fn = fn

    def onDismiss(self, dialog):
        _позвать(self.fn, self.builder)


class _CancelListenerProxy(dynamic_proxy(_OnCancel)):
    def __init__(self, builder, fn):
        super().__init__()
        self.builder = builder
        self.fn = fn

    def onCancel(self, dialog):
        _позвать(self.fn, self.builder)


class AlertDialogBuilder:
    ALERT_TYPE_MESSAGE = 0
    ALERT_TYPE_LOADING = 2
    ALERT_TYPE_SPINNER = 3

    BUTTON_POSITIVE = -1
    BUTTON_NEGATIVE = -2
    BUTTON_NEUTRAL = -3

    def __init__(self, context, progress_style=0, resources_provider=None):
        self._context = context
        self._builder = _Builder(context, int(progress_style or 0), resources_provider)
        self._dialog = None
        self._слушатели = []

    def _держать(self, слушатель):
        self._слушатели.append(слушатель)
        return слушатель

    def get_context(self):
        return self._context

    def set_title(self, title):
        self._builder.setTitle(None if title is None else str(title) if isinstance(title, str) else title)
        return self

    def set_message(self, message):
        self._builder.setMessage(message if not isinstance(message, str) else str(message))
        return self

    def set_message_text_view_clickable(self, value):
        self._builder.setMessageTextViewClickable(bool(value))
        return self

    def set_view(self, view, height=None):
        if height is None:
            self._builder.setView(view)
        else:
            self._builder.setView(view, int(height))
        return self

    def set_items(self, items, listener=None, icons=None):
        массив = jarray(jclass("java.lang.CharSequence"))([str(x) for x in items])
        слушатель = self._держать(_ItemsClickListenerProxy(self, listener))
        if icons:
            self._builder.setItems(массив, jarray("I")(list(icons)), слушатель)
        else:
            self._builder.setItems(массив, слушатель)
        return self

    def _кнопка(self, как, text, listener):
        getattr(self._builder, как)(None if text is None else str(text),
                                     self._держать(_ButtonClickListenerProxy(self, listener)))
        return self

    def set_positive_button(self, text, listener=None):
        return self._кнопка("setPositiveButton", text, listener)

    def set_negative_button(self, text, listener=None):
        return self._кнопка("setNegativeButton", text, listener)

    def set_neutral_button(self, text, listener=None):
        return self._кнопка("setNeutralButton", text, listener)

    def set_on_back_button_listener(self, listener):
        self._builder.setOnBackButtonListener(self._держать(_ButtonClickListenerProxy(self, listener)))
        return self

    def set_on_dismiss_listener(self, listener):
        self._builder.setOnDismissListener(self._держать(_DismissListenerProxy(self, listener)))
        return self

    def set_on_cancel_listener(self, listener):
        self._builder.setOnCancelListener(self._держать(_CancelListenerProxy(self, listener)))
        return self

    def set_top_image(self, res_id, background_color):
        self._builder.setTopImage(int(res_id), int(background_color))
        return self

    def set_top_drawable(self, drawable, background_color):
        self._builder.setTopImage(drawable, int(background_color))
        return self

    def set_top_animation(self, res_id, size, auto_repeat, background_color, layer_colors=None):
        self._builder.setTopAnimation(int(res_id), int(size), bool(auto_repeat), int(background_color))
        return self

    def set_top_animation_is_new(self, is_new):
        self._builder.setTopAnimationIsNew(bool(is_new))
        return self

    def set_dialog_button_color_key(self, key):
        self._builder.setDialogButtonColorKey(int(key))
        return self

    def set_blurred_background(self, value):
        self._builder.setBlurredBackground(bool(value))
        return self

    def set_dim_enabled(self, value):
        self._builder.setDimEnabled(bool(value))
        return self

    def make_button_red(self, which):
        self._builder.makeRed(int(which))
        return self

    def set_cancelable(self, value):
        if self._dialog is not None:
            self._dialog.setCancelable(bool(value))
        else:
            self._cancelable = bool(value)
        return self

    def set_canceled_on_touch_outside(self, value):
        if self._dialog is not None:
            self._dialog.setCanceledOnTouchOutside(bool(value))
        else:
            self._outside = bool(value)
        return self

    def create(self):
        if self._dialog is None:
            self._dialog = self._builder.create()
            if hasattr(self, "_cancelable"):
                self._dialog.setCancelable(self._cancelable)
            if hasattr(self, "_outside"):
                self._dialog.setCanceledOnTouchOutside(self._outside)
        return self

    def show(self):
        self.create()
        self._dialog.show()
        return self

    def dismiss(self):
        if self._dialog is not None:
            self._dialog.dismiss()

    def get_dialog(self):
        return self._dialog

    def get_button(self, which):
        return None if self._dialog is None else self._dialog.getButton(int(which))

    def set_progress(self, progress):
        if self._dialog is not None:
            self._dialog.setProgress(int(progress))
        return self
