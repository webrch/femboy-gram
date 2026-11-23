"""Слой совместимости: всплывающие плашки экстеры.

Настоящая плашка телеграма поверх открытого экрана. Если экрана нет,
глобальная плашка, а если и её показать некуда, обычная системная надпись.
"""
import traceback

from java import jclass

from android_utils import R as _Runnable, get_application_context, run_on_ui_thread

_Factory = jclass("org.telegram.ui.Components.BulletinFactory")
_Raw = jclass("org.telegram.messenger.R$raw")
_Toast = jclass("android.widget.Toast")


def _иконка(имя, запас=0):
    try:
        return int(getattr(_Raw, имя))
    except Exception:
        return запас


class BulletinHelper:
    DURATION_SHORT = 1500
    DURATION_LONG = 2750
    DURATION_PROLONG = 5000

    @staticmethod
    def _get_factory_and_provider(fragment=None):
        if fragment is None:
            try:
                fragment = jclass("org.telegram.ui.LaunchActivity").getLastFragment()
            except Exception:
                fragment = None
        if fragment is not None:
            try:
                return _Factory.of(fragment), fragment.getResourceProvider()
            except Exception:
                pass
        глобальная = getattr(_Factory, "global_", None) or getattr(_Factory, "global")
        return глобальная(), None

    @staticmethod
    def _показать(сделать, текст):
        def задача():
            try:
                фабрика, _ = BulletinHelper._get_factory_and_provider(BulletinHelper._fragment)
                плашка = сделать(фабрика)
                if плашка is not None:
                    плашка.show()
                    return
            except Exception:
                print(traceback.format_exc())
            _Toast.makeText(get_application_context(), str(текст), _Toast.LENGTH_SHORT).show()
        run_on_ui_thread(задача)

    _fragment = None

    @staticmethod
    def show_simple(text, icon_res_id=0, fragment=None):
        BulletinHelper._fragment = fragment
        BulletinHelper._показать(lambda ф: ф.createSimpleBulletin(int(icon_res_id or _иконка("info")), str(text)), text)

    @staticmethod
    def show_info(message, fragment=None):
        BulletinHelper.show_simple(message, _иконка("info"), fragment)

    @staticmethod
    def show_error(message, fragment=None):
        BulletinHelper._fragment = fragment
        BulletinHelper._показать(lambda ф: ф.createErrorBulletin(str(message)), message)

    @staticmethod
    def show_success(message, fragment=None):
        BulletinHelper.show_simple(message, _иконка("contact_check", _иконка("info")), fragment)

    @staticmethod
    def show_two_line(title, subtitle, icon_res_id=0, fragment=None):
        BulletinHelper._fragment = fragment
        BulletinHelper._показать(lambda ф: ф.createSimpleBulletin(
            int(icon_res_id or _иконка("info")), str(title), str(subtitle)), title)

    @staticmethod
    def show_with_button(text, icon_res_id, button_text, on_click, fragment=None, duration=None):
        BulletinHelper._fragment = fragment
        BulletinHelper._показать(lambda ф: ф.createSimpleBulletin(
            int(icon_res_id or _иконка("info")), str(text), str(button_text),
            int(duration or BulletinHelper.DURATION_PROLONG), _Runnable(on_click or (lambda: None))), text)

    @staticmethod
    def show_undo(text, on_undo, on_action=None, subtitle=None, fragment=None):
        BulletinHelper._fragment = fragment
        отмена = _Runnable(on_undo or (lambda: None))
        дело = _Runnable(on_action or (lambda: None))
        if subtitle:
            BulletinHelper._показать(lambda ф: ф.createUndoBulletin(str(text), str(subtitle), отмена, дело), text)
        else:
            BulletinHelper._показать(lambda ф: ф.createUndoBulletin(str(text), отмена, дело), text)

    @staticmethod
    def show_copied_to_clipboard(message=None, fragment=None):
        BulletinHelper._fragment = fragment
        BulletinHelper._показать(lambda ф: ф.createCopyBulletin(str(message or "Скопировано")), message or "Скопировано")

    @staticmethod
    def show_link_copied(is_private=False, fragment=None):
        BulletinHelper._fragment = fragment
        BulletinHelper._показать(lambda ф: ф.createCopyLinkBulletin(), "Ссылка скопирована")

    @staticmethod
    def show_file_saved_to_gallery(is_video=False, amount=1, fragment=None):
        BulletinHelper.show_success("Сохранено в галерею", fragment)

    @staticmethod
    def show_file_saved_to_downloads(file_type=None, amount=1, fragment=None):
        BulletinHelper.show_success("Сохранено в загрузки", fragment)
