# FemboyGram

**Форк Telegram для Android с нежными пастельными темами Material You.**

FemboyGram собран из [DrKLO/Telegram](https://github.com/DrKLO/Telegram)
патчем из этого репозитория. Ставится рядом с обычным Telegram: другой пакет,
другая подпись. Только arm64.

[English](README.md) · [中文](README.zh.md)

## Скачать

**[FemboyGram 1.0 — APK](https://github.com/webrch/femboy-gram/releases/download/v1.0/FemboyGram-1.0.apk)**

Ставится рядом с обычным Telegram. Андроид 23+, только arm64.

---

Пакет: `org.femboygram.app`.

Всё, что добавляет форк, лежит в одном месте: **Настройки → FemboyGram**, первая
строка.

## Что добавлено

- **Нежные фембой-темы**: по умолчанию розовая сакура, плюс лавандовая и
  небесно-голубая. Это тональные палитры Material You, а не простая замена цвета.
- **Material You** для всего приложения: цвета обоев на Андроиде 12+, либо свой
  цвет-сид на старых версиях.
- **Поле ввода**, которое растёт сколько нужно и может переехать наверх.
- **Вкладка музыки и плеер**: теги, тексты песен, плейлисты, фоновый плеер.
- **Серии** с маленьким 3D-питомцем.
- **Режим стримера**, айди в профилях, удалённые подарки, история правок.
- **Своё оформление текста**, баннеры и градиенты профиля, формы аватарок.
- **Плагины exteraGram на Python** (по умолчанию выключены).
- **Режим чатов как в Discord**, сохранение удалённых сообщений и другое.

Донат, значки в профиле и случайные кошки из исходного форка в FemboyGram
**удалены**.

## Темы

**Настройки → FemboyGram → Оформление**. Первая строка включает Material You,
ниже — кружки с готовыми нежными цветами:

| Кружок | Цвет |
| --- | --- |
| Сакура | розовый, по умолчанию |
| Лаванда | мягкий фиолетовый |
| Небесный | бледно-голубой |
| Роза, ирис, лёд | более приглушённые оттенки |

Первый кружок всегда берёт палитру обоев (Андроид 12+). Сид задаёт только
оттенок — насыщенность и тона берутся из Material You, поэтому цвет остаётся
нежным.

## Сборка

Нужны SDK 36 с build-tools 36.0.0, NDK 27.2.12479018 и JDK 21.

```bash
git clone https://github.com/DrKLO/Telegram
cd Telegram
git checkout $(cat ../FemboyGram/patch/UPSTREAM)
git submodule update --init --recursive
git apply ../FemboyGram/patch/femboygram.patch
(cd TMessagesProj/jni/prebuild && ABIS=arm64-v8a bash build_prisma.sh)
# свои api_id / api_hash с https://my.telegram.org пишутся в local.properties:
#   FEMBOY_APP_ID=…
#   FEMBOY_APP_HASH=…
# плюс ключ подписи (FEMBOY_STORE_FILE, FEMBOY_STORE_PASSWORD,
# FEMBOY_KEY_ALIAS, FEMBOY_KEY_PASSWORD); без него используется публичный ключ Telegram
gradle :TMessagesProj_AppStandalone:assembleAfatStandalone
```

APK появится в `TMessagesProj_AppStandalone/build/outputs/apk/afat/standalone/`.

## Чего здесь нет

- **api_id / api_hash.** Получи свои на my.telegram.org, в коммиты они не попадают.
- **google-services.json.** Пока у форка нет своего Firebase-проекта плагин
  выключен, поэтому пуши не приходят, когда приложение не запущено.

## Файлы

| | |
| --- | --- |
| `patch/femboygram.patch` | все правки исходников Telegram одним файлом |
| `patch/UPSTREAM` | коммит Telegram, к которому применяется патч |
| `FEATURES.md` | унаследованный документ портирования (исторический) |
| `java/`, `res/` | файлы, которые форк добавляет целиком |
| `python/` | прослойка между приложением и Python-плагинами |
| `version.json` | версия и адрес apk — отсюда берётся полоска обновления |
| `docs/` | документация по плагинам и пример плагина |
| `tools/` | генерация иконки и вспомогательные скрипты |
| `assets/branding/` | графика FemboyGram |
| `ATTRIBUTION.md` | чужое, что осталось в сборке |
| `AGENTS.md` | заметки о проекте для будущих сессий |

## Лицензия

Исходники Telegram — GPL v2 или новее, форк наследует её. Раздаёшь apk —
обязан отдать исходники: этот репозиторий плюс коммит из `patch/UPSTREAM`.
