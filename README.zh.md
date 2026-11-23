# FemboyGram

**带柔和粉彩 Material You 主题的 Telegram 分支。**

FemboyGram 由本仓库的补丁基于 [DrKLO/Telegram](https://github.com/DrKLO/Telegram)
构建。包名和签名与官方 Telegram 不同，可以并存安装。仅支持 arm64。

[English](README.md) · [Русский](README.ru.md)

## 下载

**[FemboyGram 1.2.1 — APK](https://github.com/webrch/femboy-gram/releases/download/v1.2.1/FemboyGram-1.2.1.apk)**

可与官方 Telegram 并存安装。需要 Android 23+，仅支持 arm64。

---

包名：`org.femboygram.app`。

分支新增的全部功能都在一处：**设置 → FemboyGram**，第一行。

## 新增内容

- **粉彩 femboy 主题**：默认樱花粉，另有薰衣草紫和天空蓝。主题基于
  Material You 色调调色板，而不是简单换色。
- **全应用 Material You**：Android 12+ 取壁纸颜色，旧版可用自定义种子色。
- **可无限增高的输入框**，还能移到屏幕顶部。
- **音乐标签页和播放器**：标签、歌词、播放列表、后台播放。
- **连续聊天天数**与小 3D 宠物。
- **主播模式**、资料中的 ID、已撤回礼物、编辑历史。
- **自定义文字样式**、资料横幅与渐变、头像形状。
- **exteraGram Python 插件**（默认关闭）。
- **Discord 式聊天布局**、保存已删除消息等。

上游分支的捐赠、资料徽章和随机猫咪功能在 FemboyGram 中**已移除**。

## 主题

打开 **设置 → FemboyGram → 外观**。第一行开关 Material You，下面是内置的
柔和种子色：

| 色块 | 颜色 |
| --- | --- |
| 樱花 | 粉色，默认 |
| 薰衣草 | 柔和紫色 |
| 天空 | 淡蓝色 |
| 玫瑰、鸢尾、冰 | 更淡的变体 |

第一个色块始终使用壁纸调色板（Android 12+）。种子色只决定色相，饱和度和
色调遵循 Material You，因此颜色保持柔和。

## 自行构建

需要 SDK 36（build-tools 36.0.0）、NDK 27.2.12479018 和 JDK 21。

```bash
git clone https://github.com/DrKLO/Telegram
cd Telegram
git checkout $(cat ../FemboyGram/patch/UPSTREAM)
git submodule update --init --recursive
git apply ../FemboyGram/patch/femboygram.patch
(cd TMessagesProj/jni/prebuild && ABIS=arm64-v8a bash build_prisma.sh)
# 在 https://my.telegram.org 获取自己的 api_id / api_hash，写入 local.properties：
#   FEMBOY_APP_ID=…
#   FEMBOY_APP_HASH=…
# 以及签名密钥（FEMBOY_STORE_FILE、FEMBOY_STORE_PASSWORD、
# FEMBOY_KEY_ALIAS、FEMBOY_KEY_PASSWORD）；不提供则使用 Telegram 公钥
gradle :TMessagesProj_AppStandalone:assembleAfatStandalone
```

APK 位于 `TMessagesProj_AppStandalone/build/outputs/apk/afat/standalone/`。

## 本仓库不包含

- **api_id / api_hash。** 请在 my.telegram.org 自行获取，永不提交。
- **google-services.json。** 在分支拥有自己的 Firebase 项目之前插件保持关闭，
  应用未运行时不会收到推送。

## 许可证

Telegram 源码为 GPL v2 或更高版本，分支继承该协议。分发 apk 时必须提供源码：
本仓库加上 `patch/UPSTREAM` 中指定的提交。
