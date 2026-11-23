# FemboyGram

**A Telegram fork for Android with soft pastel Material You themes.**

FemboyGram is a fork of [DrKLO/Telegram](https://github.com/DrKLO/Telegram)
built from the patch in this repository. It installs next to the official
Telegram — different package, different signature. arm64 only.

[Русский](README.ru.md) · [中文](README.zh.md)

## Download

**[FemboyGram 1.2.2 — APK](https://github.com/webrch/femboy-gram/releases/download/v1.2.2/FemboyGram-1.2.2.apk)**

Installs next to the official Telegram. Android 23+, arm64 only.

---

Package: `org.femboygram.app`.

Everything the fork adds lives in one place: **Settings → FemboyGram**, the
first row.

## What it adds

- **Pastel femboy themes** — Sakura pink by default, with lavender and sky blue
  presets. Themes are Material You tonal palettes, not flat recolours.
- **Material You** for the whole app: tones from the wallpaper on Android 12+,
  or from a chosen seed colour on older versions.
- **A message field that grows as far as you want**, and can move to the top.
- **Music tab and player** with tags, lyrics, playlists and an ambient player.
- **Streaks** with a small 3D pet.
- **Streamer mode**, IDs in profiles, deleted gifts and edit history.
- **Custom text styling**, profile banners, gradients, avatar shapes.
- **ExteraGram-compatible Python plugins** (off by default).
- **Discord-style chat layout**, deleted-message saving, and more.

Donate, profile badges and the random-cat feature of the upstream fork are
**not part of FemboyGram**.

## Themes

Open **Settings → FemboyGram → Appearance**. The first row toggles Material You;
the swatches below it are the built-in pastel seeds:

| Swatch | Colour |
| --- | --- |
| Sakura | pink, the default |
| Lavender | soft violet |
| Sky | pale blue |
| Blush, Iris, Ice | softer variants |

The first circle always uses your wallpaper palette (Android 12+). The seed
only picks the hue — saturation and tones follow Material You, so the result
stays gentle.

## Building it yourself

You need SDK 36 with build-tools 36.0.0, NDK 27.2.12479018 and JDK 21.

```bash
git clone https://github.com/DrKLO/Telegram
cd Telegram
git checkout $(cat ../FemboyGram/patch/UPSTREAM)
git submodule update --init --recursive
git apply ../FemboyGram/patch/femboygram.patch
(cd TMessagesProj/jni/prebuild && ABIS=arm64-v8a bash build_prisma.sh)
# your own api_id / api_hash from https://my.telegram.org go to local.properties:
#   FEMBOY_APP_ID=…
#   FEMBOY_APP_HASH=…
# your signing key too (FEMBOY_STORE_FILE, FEMBOY_STORE_PASSWORD,
# FEMBOY_KEY_ALIAS, FEMBOY_KEY_PASSWORD); without it Telegram's public key is used
gradle :TMessagesProj_AppStandalone:assembleAfatStandalone
```

The apk lands in `TMessagesProj_AppStandalone/build/outputs/apk/afat/standalone/`.

## What this repository does not contain

- **api_id / api_hash.** Get your own at my.telegram.org. They are never committed.
- **google-services.json.** Until the fork has its own Firebase project the
  plugin stays disabled, so push notifications do not arrive while the app is
  not running.

## Files here

| | |
| --- | --- |
| `patch/femboygram.patch` | every change to Telegram's sources, in one file |
| `patch/UPSTREAM` | the Telegram commit the patch applies to |
| `FEATURES.md` | inherited porting document (historical) |
| `java/`, `res/` | files the fork adds whole |
| `python/` | layer between the app and Python plugins |
| `version.json` | latest version and apk URL — drives the update bar |
| `docs/` | plugin documentation and an example plugin |
| `tools/` | icon generation and helper scripts |
| `assets/branding/` | the FemboyGram artwork |
| `ATTRIBUTION.md` | third-party bits that someone else made |
| `AGENTS.md` | project notes for future sessions |

## Licence

Telegram's sources are GPL v2 or later, and the fork inherits that. If you hand
someone the apk, you owe them the source it was built from: this repository
plus the commit named in `patch/UPSTREAM`.
