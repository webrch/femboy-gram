#!/usr/bin/env python3
"""FemboyGram icon artwork helpers.

The mark is the supplied heart-with-bow artwork, not a redrawn paper plane.
`assets/branding/` holds the source PNGs; this module recolours them into the
six launcher-icon slots and extracts a transparent foreground layer for
adaptive icons.

    python3 tools/icon.py          # write preview PNGs into the repo root
"""
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
ART = os.path.join(ROOT, "assets", "branding")

BASE_PINK = (251, 159, 210)
PALETTE = {
    "default": (251, 159, 210),
    "night": (58, 42, 68),
    "lavender": (183, 168, 224),
    "sand": (245, 198, 216),
    "sea": (168, 216, 234),
    "rose": (244, 169, 192),
}


def load(name="femboygram_icon.png"):
    return Image.open(os.path.join(ART, name)).convert("RGBA")


def _dist(a, b):
    return sum((x - y) ** 2 for x, y in zip(a[:3], b[:3])) ** 0.5


def tint(im, color):
    """Replace the pink field with `color`, keep the white heart and bow."""
    out = im.copy()
    px = out.load()
    w, h = out.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            if _dist((r, g, b), BASE_PINK) < 62 or _dist((r, g, b), (255, 255, 255)) >= 70:
                px[x, y] = (color[0], color[1], color[2], a)
    return out


def foreground(im):
    """Artwork on transparent, for the adaptive-icon foreground layer."""
    out = im.copy()
    px = out.load()
    w, h = out.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            d = _dist((r, g, b), BASE_PINK)
            if d < 48:
                alpha = max(0, min(255, int((d - 10) * 255 / 38)))
                px[x, y] = (255, 255, 255, min(a, alpha))
    return out


if __name__ == "__main__":
    im = load()
    im.save("femboygram_icon_preview.png")
    tint(im, PALETTE["lavender"]).save("femboygram_icon_lavender.png")
    tint(im, PALETTE["sea"]).save("femboygram_icon_sea.png")
    foreground(im).save("femboygram_foreground_preview.png")
    print("previews written; colours:", ", ".join(PALETTE))
