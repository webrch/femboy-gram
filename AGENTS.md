# FemboyGram — заметки для будущих сессий

Это память проекта. Читай её первой, если продолжаешь work с этого места.

## 1. Суть и цель

FemboyGram — это мод (форк) Telegram для Android с **нежными пастельными темами
Material You**: розовая сакура по умолчанию, плюс лавандовая и небесно-голубая.
База — исходники [DrKLO/Telegram](https://github.com/DrKLO/Telegram), поверх
которых накладывается один большой патч.

Заказчик (владелец этого форка) поставил требования:

**Убрать:**
- все кредиты и соцсети исходного форка → заменить своими;
- его донат (свой добавят позже);
- отсылки к исходному форку, котов и тому подобное;
- бейджи.

**Оставить:**
- весь функционал;
- остальные дополнения (музыка, плеер, плагины, Material You и т.д.).

**Добавить:**
- оформление в стиле фембоя: розовые, фиолетовые и синие **нежные** темы в
  Material You;
- заменить все внутренние иконки, которые отсылают к оригинальной иконке форка.

## 2. Как устроен репозиторий

Это **не полный исходник Telegram**, а патч-форк:

| Путь | Что это |
| --- | --- |
| `patch/femboygram.patch` | единый патч со всеми правками Telegram (~4.1 МБ, бинарные иконки включены) |
| `patch/UPSTREAM` | коммит Telegram (`3de37c8cf...`), к которому применяется патч |
| `tg-src/` | рабочее дерево Telegram с наложенным патчом. **Здесь происходит разработка** |
| `java/`, `res/` | «чистые» копии файлов, которые форк добавляет целиком; реально для сборки не нужны, но их ведёт upstream |
| `python/`, `docs/`, `tools/`, `server/`, `assets/` | прослойка плагинов, документация, скрипты, графика |
| `version.json` | версия + адрес apk; клиент показывает полоску обновления |
| `AGENTS.md` | этот файл |

Порядок сборки (из README):

```bash
git clone https://github.com/DrKLO/Telegram
cd Telegram
git checkout $(cat ../FemboyGram/patch/UPSTREAM)
git submodule update --init --recursive
git apply ../FemboyGram/patch/femboygram.patch
gradle :TMessagesProj_AppStandalone:assembleAfatStandalone
```

Пакет переименован в `org.femboygram.app`, ключи в `local.properties` называются
`FEMBOY_APP_ID` / `FEMBOY_APP_HASH`, ключ подписи — `FEMBOY_STORE_FILE`,
`FEMBOY_STORE_PASSWORD`, `FEMBOY_KEY_ALIAS`, `FEMBOY_KEY_PASSWORD`.

## 3. Что уже сделано

Правки применялись к `tg-src/`, затем патч пересоздан из `git diff --binary`.

### Ребрендинг
- Глобальная замена по всему дереву: `MargyGram`/`Margy Gram` → `FemboyGram`,
  `Margy`/`Margelet` → `Femboy`, `narezany` → `femboygram`.
- Пакет: `cat.narezany.margygram` → `org.femboygram.app`.
- Все классы переименованы: `org.telegram.margelet` → `org.telegram.femboy`,
  `MargeletConfig` → `FemboyConfig`, `MargyMusic` → `FemboyMusic` и т.д.
- Все ресурсы переименованы: `margy_ic_*` / `margelet_*` → `femboy_*`.
- Ссылки заменены на **заглушки** (по просьбе владельца):
  - канал/форум/фидбек → `https://t.me/femboygram`;
  - исходники → `https://example.org/femboygram/FemboyGram`;
  - сайт → `https://femboygram.example`;
  - обновление → `https://example.org/femboygram/apk/femboygram-1.0.apk`.
- `APP_NAME = "FemboyGram"`, версия `1.0`.
- README (en/ru/zh), `version.json`, `ATTRIBUTION.md` переписаны. Старый
  `CLAUDE.md` с личными заметками исходного автора удалён. `FEATURES.md`
  оставлен как исторический документ с предупреждением вверху.

### Удалено
- **Донат**: активити, пункт меню, плашка «значок саппортера», команды бота,
  реквизиты, все строки на трёх языках.
- **Бейджи**: галерея, менеджер, пункты в настройках и профиле, серверный API,
  `badges.json`, документация, drawable-ресурсы. Класс `FemboyBadge` оставлен
  как **пустая заглушка**: в ядре Telegram десятки вызовов, и удаление класса
  сломало бы компиляцию; заглушка возвращает `false`/`null`/пустые списки.
  `FemboyOtherGram` тоже заглушка.
- **Коты**: случайные фото котов, `margelet_cat_*.jpg`, `cats.json`, дока,
  кошачий бейдж. Звук мяуканья заменён синтезированным мягким перезвоном
  (`femboy_chime.ogg`), класс `FemboyMeow` → `FemboyChime`, строки переименованы.
- Устаревшие инструменты `plane3d_check.py`, `plane_glyph.py`, `meow.py` удалены.

### Иконки
- Твои PNG из `../icons/` (`default_icon.png`, `full_icon.png`, `icon_1.png`)
  скопированы в `assets/branding/`.
- Сгенерированы launcher-иконки для всех плотностей (mdpi…xxxhdpi), адаптивный
  foreground, монохромный слой в виде **сердечка**, фоновый цвет заменён с
  зелёного на розовый `#FB9FD2`, шесть цветовых слотов standalone-сборки
  перекрашены в пастельные тона.
- Внутри приложения 3D-самолётик в шапке настроек заменён на `FemboyIconView`,
  который рисует твою иконку с лёгким покачиванием. `FemboyPlane3D` удалён.
- Значок входа в настройки и Discord-рейка теперь рисуют сердечко
  (`femboy_badge_plane.xml`).
- `tools/icon.py` и `tools/apply_icon.py` переписаны под новую графику.

### Тема
- `FemboyYou.SEED` = `0xFFFF9ECD` (нежно-розовый), по умолчанию используется
  он, а не цвета обоев.
- `FemboySeedPicker.SEEDS` — палитра из сакуры, лаванды, небесного, розы, ириса
  и льда (плюс кружок «из обоев»).
- `FemboyTheme` при первом запуске ставит розовый акцент Night-темы (id 11).
- В «Оформлении» добавлен заголовок «Темы FemboyGram» перед кружками.
- Material You, музыка и остальные дополнения **не тронуты**.


### Правки по просьбе владельца (последний заход)
- Иконка на лаунчере — `default_icon.png` (без изменений). `full_icon.png`
  заменён на `true_full_icon.png` и остался только статическим fallback.
- Шапка настроек форка проигрывает анимированный GIF `icon_360.gif`
  из assets (`femboy_header.gif`) через `android.graphics.Movie`; `FemboyIconView`
  переписан под него.
- Добавлен дефолтный плагин `femboy_example.marp` (иконка `icon_1.png`),
  ставится при первом запуске выключенным.
- Раздел «Профиль» полностью вырезан из настроек; класс `FemboyProfileActivity`
  и его deep-link'и удалены.
- Превью стикера в «Сообщениях» теперь `femboy_sticker.webp` из `sticker.jpg`.
- Ссылки: канал `t.me/femboygm`, «фембой кф» → `t.me/femboyconfs`, фидбек
  `t.me/fnabout`, исходники `github.com/webrch/femboy-gram`. Нажатие «Стикеры»
  показывает «скоро..» вместо открытия ссылки. Версия в клиенте — 1.0.

- Релиз выложен на GitHub: `https://github.com/webrch/femboy-gram`, тег `v1.0`, ассет `FemboyGram-1.0.apk`. Ссылка на скачивание: `https://github.com/webrch/femboy-gram/releases/download/v1.0/FemboyGram-1.0.apk`. Чистая экспортная копия без tg-src лежит в `../_release`.

- Пример плагина: в архиве `femboy_example.marp` (и в `TMessagesProj`, и в `TMessagesProj_AppStandalone`) заменены id на `femboy.example`, автор на `синдзи`, иконка на `femboygram_alt.png`. Старый пример с `narezany` удалён из APK. Пересобранный APK залит в релиз v1.0 (70342917 байт), прямая ссылка прежняя.

- Миграция плагина: `preinstallExample()` теперь удаляет старую папку `margelet.example` из данных приложения и сбрасывает флаг `plugin_example_done`, поэтому новый пример ставится даже при обновлении поверх старой версии. APK пересобран и залит в v1.0 (70342756 байт).


### Итог сессии 23.11.2026 (вечер)

**Состояние проекта:** FemboyGram 1.0 собран, выложен и работоспособен.
- Репозиторий: https://github.com/webrch/femboy-gram (ветка `main`, чистая история, один коммит на старте + правки).
- Релиз v1.0: https://github.com/webrch/femboy-gram/releases/tag/v1.0
- APK: https://github.com/webrch/femboy-gram/releases/download/v1.0/FemboyGram-1.0.apk
  (последняя сборка 70 342 756 байт, пакет `org.femboygram.app`).
- Локальные копии: `FemboyGram.apk` в корне рабочей папки и в `_release/`.
- Сборка проверялась неоднократно: `tools/build.sh` (bash + JDK 21 + SDK 36 + NDK 27.2.12479018),
  последний раз BUILD SUCCESSFUL.

**Что сделано в последнем заходе:**
- Удалены старые брендовые картинки из `assets/`: `banner.png` (с надписью Margelet),
  зелёные `logo.png`/`logo.svg`, `femboygram-badge-256.png`,
  `femboygram-badge-glyph-256.png`, `femboygram-icon-1024.png`.
- Найдена и исправлена настоящая причина «старого плагина»: пример ставился один раз
  (`plugin_example_done`) и старая папка `margelet.example` оставалась в данных приложения.
  Добавлена миграция в `FemboyPlugins.preinstallExample()` + `FemboyConfig.resetExamplePlugin()`.
- `femboy_example.marp` в обоих ассетах: id `femboy.example`, автор «синдзи», иконка
  из `femboygram_alt.png`. APK пересобран и перезалит в релиз.
- Текст релиза обновлён (включая строку про автоматическую замену старого плагина).
- GitHub-токен владельца использован через `gh auth setup-git`; после работы его лучше отозвать.

**О проверке упоминаний старого бренда:**
- В исходниках (`tg-src/.../src`, патче, java, python, docs) упоминаний Margy/Margelet/narezany нет.
- Остались только в `AGENTS.md` (история замен), `FEATURES.md` (одно слово «нареза» в
  историческом документе) и в regex `tools/check_refs.py` (он их и ищет).
- В локальном `FemboyGram/.git` есть `upstream`-remote и старые коммиты с именами MargyGram —
  на GitHub этого нет, там история чистая.

**Следующий шаг — рекламное видео (обсуждалось, ещё не делалось):**
- Для «рекламы как у Claude Code» нужно уточнить у владельца: подключён ли телефон
  (`adb devices`), нужен ли живой screenrecord через `adb shell screenrecord` + `input tap`,
  или достаточно motion-графики из иконок/скриншотов через ffmpeg; музыка — синтезировать
  или дать лицензионный трек; формат 9:16 или 16:9; длина 15–30 сек.
- Никаких файлов для видео пока не создано.

**Пожелания владельца на будущее:** не упоминать чужие донаты и кредиты в публичных постах;
релиз-пост уже согласован и опубликован в текст релиза.


### Донат (добавить в следующем заходе по просьбе владельца)

Нужно вернуть донат в форк, но уже с реквизитами владельца:
- **ЮMoney:** https://yoomoney.ru/to/4100118861022194
- **Звёзды Telegram:** аккаунт `9051996412` (ID Telegram)

Что восстанавливать:
- активити доната (раньше `MargeletDonateActivity`, теперь `FemboyDonateActivity`);
- пункт меню в разделе настроек форка;
- константы в `FemboyConfig` (`DONATE_YOOMONEY`, `DONATE_GIFT_USER`, при необходимости `DONATE_PAGE`);
- строки на трёх языках (`values`, `values-ru`, `values-zh`);
- deep-link `femboy://donate` в `FemboyScheme`, если он нужен.

Исходную реализацию можно взять из истории upstream:
`git show upstream/main:...` (файлы `MargeletDonateActivity.java`, `MargeletConfig.java`, строки),
затем переименовать под FemboyGram и заменить реквизиты на указанные выше.
Публично в постах упоминать только свой донат, без чужих кредитов.


### Режим призрака (добавлен 24.11.2026, полный)

Перенесён из AyuGram4A (`AyuGram/AyuGram4A`, GPL-2.0, Copyright @Radolyn, 2023),
код адаптирован под FemboyGram, атрибуция добавлена в `ATTRIBUTION.md`.
- Новый класс `org.telegram.femboy.FemboyGhost` + настройки в `FemboyConfig`
  (`ghost_mode`, `ghost_read`, `ghost_online`, `ghost_typing`, `ghost_offline_marker`,
  `ghost_mark_after_send`).
- Хук в `ConnectionsManager.sendRequestInternal()`:
  - дропает `TL_messages_setTyping` / `setEncryptedTyping` (скрытие набора текста);
  - выставляет `offline = true` в `org.telegram.tgnet.tl.TL_account.updateStatus`
    (в этой версии Telegram класс живёт в `tl`, а не в `TLRPC`!);
  - перехватывает read-пакеты (`messages.readHistory`, `readDiscussion`,
    `channels.readHistory`, `readEncryptedHistory`, `readMessageContents`),
    отвечая фейковым `TL_messages_affectedMessages` с `pts = -1`, `pts_count = 0`.
  - разовая поблажка `allowNextRead(1)` после отправки сообщения.
- Настройки: «Настройки → FemboyGram → Приватность и сеть», раздел «Режим призрака»,
  мастер-тумблер и 4 дополнительных переключателя, строки на en/ru/zh.
- Внимание: апостроф в Android-строках надо экранировать (`Don\'t`), иначе AAPT падает.
- APK пересобран (70 345 404 байт), залит в релиз v1.0, исходники запушены.
- ToS-breaking: Telegram может ограничить аккаунт — владелец предупреждён.


### Релиз 1.1 (24.11.2026)

Версия поднята везде: `FemboyConfig.APP_VERSION = "1.1"`, `version.json` указывает
на `https://github.com/webrch/femboy-gram/releases/download/v1.1/FemboyGram-1.1.apk`,
в README (en/ru/zh) обновлены ссылки на скачивание. Старый релиз v1.0 оставлен в истории.
- APK 1.1: 70 345 397 байт, собран и залит в релиз v1.1.
- Прямая ссылка: https://github.com/webrch/femboy-gram/releases/download/v1.1/FemboyGram-1.1.apk
- Страница релиза: https://github.com/webrch/femboy-gram/releases/tag/v1.1
- В тексте релиза описаны изменения 1.1 и предупреждение про ToS.


### Исправление обновления (24.11.2026)

Причина ошибки «не удалось скачать обновление»: `FemboyConfig.APP_VERSION` стал «1.1»,
но `APP_VERSION_CODE` в `tg-src/gradle.properties` остался 7237, поэтому versionCode APK
остался 72379 — такой же, как у 1.0. Проверка `FemboyUpdate.newerThanInstalled()`
сравнивает versionCode и отклоняла скачанный файл как «не новее установленного».
- Поднят `APP_VERSION_CODE` до 7238 → versionCode APK теперь 72389 (> 72379).
- APK пересобран (70 345 386 байт), ассет в релизе v1.1 заменён через `gh release upload --clobber`.
- **Правило на будущее:** при каждом повышении номера версии обязательно поднимать
  и `APP_VERSION_CODE`, иначе обновлялка будет ломаться.


### Бейджи и релиз 1.2 (24.11.2026)

Восстановлена система бейджей с иконкой-сердечком `icons/badge.png` -> белый глиф
`res/drawable-nodpi/femboy_badge_heart.png` (и копия в tg-src).
- Список бейджей вынесен в `badges.json` в корне репозитория. Клиент подтягивает его
  через `FemboyRemote` при запуске и при «Проверить сейчас» без обновления APK.
- Владельцы: 6536442376, 9051996412 — розовый `FF9ECD`.
- Друг владельца: 6497351887 — лавандовый `C9A6FF`.
- Каналы мода: -1004414542364, -1004353739064 — синий `9FD0FF`.
- Саппортеры: список пуст, цвет фиолетовый `B39DDB`; добавлять в `badges.json`.
- `FemboyBadge` снова рабочий: color field + белый глиф через LayerDrawable,
  бейджи у имён в чатах/списке и в профиле, окно с описанием и кнопкой ссылки.
- Версия 1.2, `APP_VERSION_CODE=7239` -> versionCode 72399.

## 4. Важные технические детали

1. **Патч — источник истины.** Любая правка кода делается в `tg-src/`, потом:
   `git -C tg-src add -A -N` и `git -C tg-src diff --binary > patch/femboygram.patch`.
   Копии `java/`/`res/` синхронизируются отдельно.
2. **CRLF-ловушка.** На Windows `core.autocrlf=true`; если патч сохранить с CRLF,
   `git apply` падает на бинарных кусках. Готовый `femboygram.patch` записан в LF.
3. **Сборка проверена (2026-11-23).** JDK 21, Android SDK 36, build-tools 36.0.0,
   NDK 27.2.12479018 и CMake 3.31.1 установлены в систему. Полная сборка
   `assembleAfatStandalone` прошла успешно за ~11 минут. Готовый APK скопирован
   в `../FemboyGram.apk` (65.8 МБ, пакет `org.femboygram.app`), пересобирать можно
   скриптом `tools/build.sh`. Все удаления делались консервативно (заглушки
   вместо вырезания вызовов в ядре Telegram). Проверка `R.string/R.drawable/R.raw`
   — скрипт `tools/check_refs.py`; неразрешённые имена, которые он показывает,
   присутствовали ещё в upstream (другие модули/plurals).
   При сборке на Windows: сабмодули нужно инициализировать **без** `--depth 1`
   (иначе они остаются на неверных коммитах), включить `core.longpaths true` и
   пересобрать `libprisma.a` через Git Bash.
4. **R8 и плагины:** классы, которые Python зовёт по имени, должны быть в
   `proguard-rules.pro` строкой `-keep class … { *; }`. После полной сборки
   стоит проверить dex наживую.
5. **Git remote:** исходный remote переименован в `upstream`. Свой remote пока
   не добавлен — владелец должен добавить свой.
6. Папка репозитория переименована из `MargyGram` в `FemboyGram`.

## 5. Что дальше (не сделано намеренно)

- Подставить **настоящие** кредиты и ссылки вместо заглушек (`t.me/femboygram`,
  `example.org`, `femboygram.example`). Все они сейчас сконцентрированы в
  `FemboyConfig.java`, `version.json` и README.
- Добавить **свой донат** — когда владелец даст реквизиты (нужно вернуть активити
  и пункт меню; проще всего взять их из `git show upstream/main:...`).
- **Проверить сборку и внешний вид на телефоне.** Особенно: три палитры,
  адаптивную иконку на лаунчере, шапку настроек, монохромную иконку.
- Решить судьбу 3D-питомца: он оставлен как дополнение, но в коде и строках
  ещё есть «tilcayo» и кошачьи шутки. Если владелец считает это «котами» —
  вырезать отдельной задачей (затрагивает `FemboyPet*`, `FemboyStreaks`,
  строки и `assets/femboy_tilcayo.webp`).
- При желании вынести три пресета тем в отдельный экран с подписями,
  а не только кружки в `FemboySeedPicker`.
- Временные скрипты `_rebrand/` уже удалены после сборки патча; история замен
  описана в этом файле.


