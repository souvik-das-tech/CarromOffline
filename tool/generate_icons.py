"""Draws the Carrom Offline launcher icons: a small carrom board (walnut frame, maple surface,
four pockets) with the queen and coins in the middle, on a dark walnut gradient.

Run from the project root:  python tool/generate_icons.py
Writes the adaptive-icon layers and legacy icons into app/src/main/res, and the store logo
into branding/.
"""
import math
import os

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
BRANDING = os.path.join(ROOT, "branding")

TOP = (0x1F, 0x5E, 0x5A)
BOTTOM = (0x07, 0x1A, 0x1C)
FRAME = (0x7A, 0x3B, 0x16)
FRAME_EDGE = (0xF2, 0xB6, 0x30)
MAPLE = (0xF6, 0xDD, 0xAE)
POCKET = (0x1A, 0x0E, 0x08)
IVORY = (0xFB, 0xF4, 0xE2)
EBONY = (0x2B, 0x1A, 0x12)
QUEEN = (0xE5, 0x30, 0x2A)
SS = 4  # supersampling factor

DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}


def gradient(size):
    img = Image.new("RGB", (size, size))
    px = img.load()
    for y in range(size):
        for x in range(size):
            t = (x + y) / (2 * (size - 1))
            px[x, y] = tuple(round(a + (b - a) * t) for a, b in zip(TOP, BOTTOM))
    return img


def coin(d, cx, cy, r, fill, ring):
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=fill + (255,), outline=ring + (255,), width=max(1, round(r * 0.12)))
    rr = r * 0.55
    d.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], outline=ring + (255,), width=max(1, round(r * 0.08)))


def artwork(size, mono=False):
    big = size * SS
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    white = (255, 255, 255)
    paint = (lambda c: white + (255,)) if mono else (lambda c: c + (255,))
    clear = (0, 0, 0, 0)
    d.rounded_rectangle([0, 0, big - 1, big - 1], radius=big * 0.14, fill=paint(FRAME),
                        outline=None if mono else FRAME_EDGE + (255,), width=round(big * 0.02))
    m = big * 0.11
    d.rounded_rectangle([m, m, big - m, big - m], radius=big * 0.04, fill=clear if mono else MAPLE + (255,))
    pr = big * 0.07
    for px, py in [(m + pr, m + pr), (big - m - pr, m + pr), (m + pr, big - m - pr), (big - m - pr, big - m - pr)]:
        d.ellipse([px - pr, py - pr, px + pr, py + pr], fill=paint(POCKET))
    c = big / 2
    if mono:
        cr = big * 0.06
        for k in range(7):
            a = k * math.pi / 3
            x, y = (c, c) if k == 6 else (c + 2.1 * cr * math.cos(a), c + 2.1 * cr * math.sin(a))
            d.ellipse([x - cr, y - cr, x + cr, y + cr], fill=white + (255,))
    else:
        ring_r = big * 0.2
        d.ellipse([c - ring_r, c - ring_r, c + ring_r, c + ring_r], outline=(0xC6, 0x28, 0x28, 255), width=round(big * 0.012))
        cr = big * 0.06
        for k in range(6):
            a = k * math.pi / 3 + math.pi / 6
            coin(d, c + 2.1 * cr * math.cos(a), c + 2.1 * cr * math.sin(a), cr, IVORY if k % 2 == 0 else EBONY,
                 (0xC9, 0xB8, 0x94) if k % 2 == 0 else (0x6B, 0x4A, 0x36))
        coin(d, c, c, cr, QUEEN, (0x9A, 0x14, 0x10))
        # Striker on the bottom baseline.
        sr = big * 0.08
        coin(d, c, big * 0.76, sr, (0xF4, 0xEF, 0xFF), FRAME_EDGE)
    return img.resize((size, size), Image.LANCZOS)


def icon(size, rounded=True):
    big = size * SS
    bg = gradient(big).convert("RGBA")
    if rounded:
        mask = Image.new("L", (big, big), 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, big - 1, big - 1], radius=big * 0.22, fill=255)
        bg.putalpha(mask)
    art = round(big * 0.7)
    bg.alpha_composite(artwork(art // SS).resize((art, art), Image.LANCZOS), ((big - art) // 2, (big - art) // 2))
    return bg.resize((size, size), Image.LANCZOS)


def save(img, *parts):
    path = os.path.join(*parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def main():
    for name, scale in DENSITIES.items():
        # Adaptive layers are 108dp; the artwork stays inside the 66dp safe circle.
        layer = round(108 * scale)
        art = round(50 * scale)
        for mono, file in [(False, "ic_launcher_foreground.png"), (True, "ic_launcher_monochrome.png")]:
            fg = Image.new("RGBA", (layer, layer), (0, 0, 0, 0))
            fg.alpha_composite(artwork(art, mono), ((layer - art) // 2, (layer - art) // 2))
            save(fg, RES, f"mipmap-{name}", file)
        save(icon(round(48 * scale)), RES, f"mipmap-{name}", "ic_launcher.png")
    save(icon(512, rounded=False), BRANDING, "CarromOffline_playstore_512.png")


if __name__ == "__main__":
    main()
