"""Слой совместимости: строки экрана настроек экстеры.

Это просто описания — их читает base_plugin и превращает в femboy.settings.
Ничего не рисуют сами, как и у них. Доводы те же, что у экстеры, и
позиционные, и по имени.
"""


class _Строка:
    _поля = ()

    def __init__(self, *args, **kwargs):
        for имя in self._поля:
            setattr(self, имя, None)
        for имя, значение in zip(self._поля, args):
            setattr(self, имя, значение)
        for имя, значение in kwargs.items():
            setattr(self, имя, значение)

    def __repr__(self):
        return "%s(%r)" % (type(self).__name__, getattr(self, "text", ""))


class Header(_Строка):
    _поля = ("text",)


class Divider(_Строка):
    _поля = ("text",)

    @property
    def subtext(self):
        return getattr(self, "text", None)


class Switch(_Строка):
    _поля = ("key", "text", "default", "subtext", "icon", "on_change", "on_long_click", "link_alias")


class Selector(_Строка):
    _поля = ("key", "text", "default", "items", "icon", "on_change", "on_long_click", "link_alias")

    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        if self.items is None:
            self.items = []
        if self.default is None:
            self.default = 0


class Input(_Строка):
    _поля = ("key", "text", "default", "subtext", "icon", "on_change", "on_long_click", "link_alias")


class EditText(_Строка):
    _поля = ("key", "hint", "default", "multiline", "max_length", "mask", "on_change")

    @property
    def text(self):
        return self.hint


class Text(_Строка):
    _поля = ("text", "icon", "accent", "red", "on_click", "create_sub_fragment", "on_long_click",
             "link_alias", "subtext")

    @property
    def key(self):
        return self.__dict__.get("key") or self.__dict__.get("link_alias")

    @key.setter
    def key(self, значение):
        self.__dict__["key"] = значение


class Custom(_Строка):
    _поля = ("item", "view", "factory", "factory_args", "on_click", "on_long_click", "link_alias")


class SimpleSettingFactory:
    def __init__(self, *args, **kwargs):
        self.args = args
        self.kwargs = kwargs

    def __call__(self, *args, **kwargs):
        return Custom(factory=self, factory_args=args or kwargs or None)

    def release(self):
        pass
