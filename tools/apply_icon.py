#!/usr/bin/env python3
"""Put the FemboyGram artwork into a freshly patched Telegram source tree.

    python3 tools/apply_icon.py [telegram-root]

Defaults to `tg-src` next to this repository. Run after `git apply` of the
patch; the patch already contains the generated icons, so this is only needed
when the artwork in `assets/branding/` changes.
"""
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, HERE)
import icon  # noqa: E402

TG = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "tg-src")
RES = os.path.join(TG, "TMessagesProj/src/main/res")
RES_SA = os.path.join(TG, "TMessagesProj_AppStandalone/src/main/res")
DPI = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
SLOTS = [
    ("icon_background_sa", "ic_launcher_sa", "default"),
    ("icon_2_background_sa", "icon_2_launcher_sa", "night"),
    ("icon_3_background_sa", "icon_3_launcher_sa", "lavender"),
    ("icon_4_background_sa", "icon_4_launcher_sa", "sand"),
    ("icon_5_background_sa", "icon_5_launcher_sa", "sea"),
    ("icon_6_background_sa", "icon_6_launcher_sa", "rose"),
]
SHAPE = ('<?xml version="1.0" encoding="utf-8"?>\n'
         '<shape xmlns:android="http://schemas.android.com/apk/res/android"\n'
         '    android:shape="rectangle">\n'
         '    <solid android:color="#%02X%02X%02X" />\n'
         '</shape>\n')


def save(im, path, size):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.resize((size, size), Image.LANCZOS).save(path)


def main():
    art = icon.load()
    save(art, os.path.join(RES, "drawable-nodpi/femboy_icon_big.png"), 512)
    fg = icon.foreground(art)
    for dpi, px in DPI.items():
        d = os.path.join(RES, "mipmap-" + dpi)
        save(art, os.path.join(d, "ic_launcher.png"), px)
        save(art, os.path.join(d, "ic_launcher_round.png"), px)
        fpx = int(round(px * 108 / 48))
        save(fg, os.path.join(d, "femboy_foreground.png"), fpx)
    open(os.path.join(RES, "drawable/femboy_background.xml"), "w").write(SHAPE % icon.BASE_PINK)
    for bg, launcher, key in SLOTS:
        color = icon.PALETTE[key]
        open(os.path.join(RES, "drawable", bg + ".xml"), "w").write(SHAPE % color)
        themed = icon.tint(art, color)
        for dpi, px in DPI.items():
            save(themed, os.path.join(RES_SA, "mipmap-" + dpi, launcher + ".png"), px)
    print("FemboyGram icons applied to", TG)


if __name__ == "__main__":
    main()
