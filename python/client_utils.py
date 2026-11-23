"""Слой совместимости: client_utils экстеры.

Их client_utils — обёртки вокруг обычных классов телеграма. Те же классы
доступны и нам: питон в приложении настоящий, java видна как есть. Поэтому
здесь не эмуляция, а те же самые вызовы под их именами.
"""
import contextlib
import threading
import traceback

from java import dynamic_proxy, jclass

from android_utils import R, run_on_ui_thread

_Connections = jclass("org.telegram.tgnet.ConnectionsManager")
_RequestDelegate = jclass("org.telegram.tgnet.RequestDelegate")
_UserConfig = jclass("org.telegram.messenger.UserConfig")
_Account = jclass("org.telegram.messenger.AccountInstance")
_Launch = jclass("org.telegram.ui.LaunchActivity")
_Utilities = jclass("org.telegram.messenger.Utilities")
_Queue = jclass("org.telegram.messenger.DispatchQueue")
_Send = jclass("org.telegram.femboy.FemboySend")
_Params = jclass("org.telegram.messenger.SendMessagesHelper$SendMessageParams")
_ArrayList = jclass("java.util.ArrayList")
_HashMap = jclass("java.util.HashMap")
_NotificationCenter = jclass("org.telegram.messenger.NotificationCenter")

STAGE_QUEUE = "stageQueue"
GLOBAL_QUEUE = "globalQueue"
CACHE_CLEAR_QUEUE = "cacheClearQueue"
SEARCH_QUEUE = "searchQueue"
PHONE_BOOK_QUEUE = "phoneBookQueue"
THEME_QUEUE = "themeQueue"
EXTERNAL_NETWORK_QUEUE = "externalNetworkQueue"
PLUGINS_QUEUE = "pluginsQueue"

_свои_очереди = {}
_замок = threading.Lock()
_местный = threading.local()


def _resolve_account(account=None):
    if account is not None:
        return int(account)
    своё = getattr(_местный, "account", None)
    if своё is not None:
        return своё
    return int(_UserConfig.selectedAccount)


def get_selected_account():
    return int(_UserConfig.selectedAccount)


def get_hook_account():
    """Аккаунт, в чьём хуке сейчас идёт работа, иначе выбранный."""
    return _resolve_account()


@contextlib.contextmanager
def account_scope(account):
    было = getattr(_местный, "account", None)
    _местный.account = int(account)
    try:
        yield
    finally:
        _местный.account = было


def get_last_fragment():
    return _Launch.getLastFragment()


def get_account_instance(account=None):
    return _Account.getInstance(_resolve_account(account))


def get_messages_controller(account=None):
    return get_account_instance(account).getMessagesController()


def get_messages_storage(account=None):
    return get_account_instance(account).getMessagesStorage()


def get_contacts_controller(account=None):
    return get_account_instance(account).getContactsController()


def get_media_data_controller(account=None):
    return get_account_instance(account).getMediaDataController()


def get_connections_manager(account=None):
    return get_account_instance(account).getConnectionsManager()


def get_location_controller(account=None):
    return get_account_instance(account).getLocationController()


def get_notifications_controller(account=None):
    return get_account_instance(account).getNotificationsController()


def get_notification_center(account=None):
    return get_account_instance(account).getNotificationCenter()


def get_send_messages_helper(account=None):
    return get_account_instance(account).getSendMessagesHelper()


def get_file_loader(account=None):
    return get_account_instance(account).getFileLoader()


def get_secret_chat_helper(account=None):
    return get_account_instance(account).getSecretChatHelper()


def get_download_controller(account=None):
    return get_account_instance(account).getDownloadController()


def get_notifications_settings(account=None):
    return get_account_instance(account).getNotificationsSettings()


def get_user_config(account=None):
    return get_account_instance(account).getUserConfig()


def get_media_controller():
    return jclass("org.telegram.messenger.MediaController").getInstance()


def get_queue_by_name(name):
    """Очередь телеграма по имени. Своей очереди плагинов у телеграма нет, заводим."""
    name = str(name)
    try:
        очередь = getattr(_Utilities, name)
        if очередь is not None:
            return очередь
    except Exception:
        pass
    with _замок:
        очередь = _свои_очереди.get(name)
        if очередь is None:
            очередь = _Queue(name)
            _свои_очереди[name] = очередь
        return очередь


def run_on_queue(fn, queue_name=PLUGINS_QUEUE, delay=0):
    """Работа в стороне от рисующего потока."""
    очередь = get_queue_by_name(queue_name)
    if delay:
        очередь.postRunnable(R(fn), int(delay))
    else:
        очередь.postRunnable(R(fn))


class RequestCallback(dynamic_proxy(_RequestDelegate)):
    def __init__(self, fn):
        super().__init__()
        self.fn = fn

    def run(self, response, error):
        if self.fn is None:
            return
        try:
            self.fn(response, error)
        except Exception:
            print(traceback.format_exc())


def send_request(request, callback=None, account=None):
    """Запрос к серверу. Ответ приходит в callback(ответ, ошибка), как у них."""
    if callback is not None and not isinstance(callback, RequestCallback):
        callback = RequestCallback(callback)
    return _Connections.getInstance(_resolve_account(account)).sendRequest(
        request, callback or RequestCallback(None))


class NotificationCenterDelegate(dynamic_proxy(jclass("org.telegram.messenger.NotificationCenter$NotificationCenterDelegate"))):
    def __init__(self, fn=None):
        super().__init__()
        self.fn = fn

    def didReceivedNotification(self, id, account, args):
        if self.fn is None:
            return
        try:
            self.fn(id, account, args)
        except Exception:
            print(traceback.format_exc())


def _список(что):
    if что is None:
        return None
    if isinstance(что, (list, tuple)):
        out = _ArrayList()
        for x in что:
            out.add(x)
        return out
    return что


def _словарь(что):
    if что is None:
        return None
    if isinstance(что, dict):
        out = _HashMap()
        for k, v in что.items():
            out.put(str(k), None if v is None else str(v))
        return out
    return что


def send_message(params, account=None):
    """Отправить сообщение. Словарь с message и peer, как у них, плюс любые
    поля SendMessageParams под теми же именами (entities, replyToMsg, …)."""
    if not isinstance(params, dict):
        _Send.params(_resolve_account(account), params)
        return
    текст = params.get("message")
    куда = params.get("peer")
    if текст is None or куда is None:
        raise ValueError("send_message: нужны message и peer")
    готово = _Params.of(str(текст), int(куда))
    for ключ, значение in params.items():
        if ключ in ("message", "peer"):
            continue
        if ключ == "entities":
            значение = _список(значение)
        elif ключ == "params":
            значение = _словарь(значение)
        try:
            setattr(готово, ключ, значение)
        except Exception:
            print("send_message: поля", ключ, "у SendMessageParams нет")
    _Send.params(_resolve_account(account), готово)


def send_text(peer_id, text, reply_to=None, reply_to_top=None, entities=None,
              notify=True, schedule_date=0, account=None, **прочее):
    if прочее.get("parse_markdown") or прочее.get("parse_mode") in ("markdown", "Markdown", "MARKDOWN"):
        from markdown_utils import parse_markdown
        разобрано = parse_markdown(text)
        text = разобрано.text
        entities = [e.to_tlrpc_object() for e in разобрано.entities]
    _Send.text(_resolve_account(account), int(peer_id), str(text), _список(entities),
               reply_to, reply_to_top, bool(notify), int(schedule_date))


def send_document(peer_id, path, caption=None, reply_to=None, reply_to_top=None, account=None, **прочее):
    _Send.document(_resolve_account(account), int(peer_id), str(path),
                   None if caption is None else str(caption), reply_to, reply_to_top)


def send_photo(peer_id, path, caption=None, reply_to=None, reply_to_top=None, account=None, **прочее):
    _Send.photo(_resolve_account(account), int(peer_id), str(path),
                None if caption is None else str(caption), reply_to, reply_to_top)


def send_video(peer_id, path, caption=None, reply_to=None, reply_to_top=None, account=None, **прочее):
    _Send.video(_resolve_account(account), int(peer_id), str(path),
                None if caption is None else str(caption), reply_to, reply_to_top)


def send_audio(peer_id, path, caption=None, reply_to=None, reply_to_top=None, account=None, **прочее):
    _Send.document(_resolve_account(account), int(peer_id), str(path),
                   None if caption is None else str(caption), reply_to, reply_to_top)


def edit_message(message_object, text, entities=None, **прочее):
    _Send.edit(message_object, str(text), _список(entities))


class AccountClient:
    """Всё то же, привязанное к одному аккаунту."""

    def __init__(self, account=None):
        self.account = _resolve_account(account)

    def __eq__(self, other):
        return isinstance(other, AccountClient) and other.account == self.account

    def __hash__(self):
        return hash(self.account)

    def __repr__(self):
        return "AccountClient(%d)" % self.account

    def get_account_instance(self):
        return get_account_instance(self.account)

    def get_messages_controller(self):
        return get_messages_controller(self.account)

    def get_messages_storage(self):
        return get_messages_storage(self.account)

    def get_contacts_controller(self):
        return get_contacts_controller(self.account)

    def get_media_data_controller(self):
        return get_media_data_controller(self.account)

    def get_connections_manager(self):
        return get_connections_manager(self.account)

    def get_location_controller(self):
        return get_location_controller(self.account)

    def get_notifications_controller(self):
        return get_notifications_controller(self.account)

    def get_notification_center(self):
        return get_notification_center(self.account)

    def get_send_messages_helper(self):
        return get_send_messages_helper(self.account)

    def get_file_loader(self):
        return get_file_loader(self.account)

    def get_secret_chat_helper(self):
        return get_secret_chat_helper(self.account)

    def get_download_controller(self):
        return get_download_controller(self.account)

    def get_notifications_settings(self):
        return get_notifications_settings(self.account)

    def get_user_config(self):
        return get_user_config(self.account)

    def send_request(self, request, callback=None):
        return send_request(request, callback, account=self.account)

    def send_message(self, params):
        return send_message(params, account=self.account)

    def send_text(self, peer_id, text, **kw):
        return send_text(peer_id, text, account=self.account, **kw)

    def send_document(self, peer_id, path, caption=None, **kw):
        return send_document(peer_id, path, caption, account=self.account, **kw)

    def send_photo(self, peer_id, path, caption=None, **kw):
        return send_photo(peer_id, path, caption, account=self.account, **kw)

    def send_video(self, peer_id, path, caption=None, **kw):
        return send_video(peer_id, path, caption, account=self.account, **kw)

    def send_audio(self, peer_id, path, caption=None, **kw):
        return send_audio(peer_id, path, caption, account=self.account, **kw)

    def edit_message(self, message_object, text, **kw):
        return edit_message(message_object, text, **kw)


def get_client(account=None):
    return AccountClient(account)


# старое имя внутри Femboy
_Работа = R
