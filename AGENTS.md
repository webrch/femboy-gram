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


