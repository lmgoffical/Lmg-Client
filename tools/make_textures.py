"""Generates the Lmg Client textures (Star of David logo, sparkle, mod icon).

Run from the repo root:  python tools/make_textures.py   (needs Pillow)
"""
import math
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter

OUT = Path("src/main/resources/assets/lmgclient")
SS = 4  # supersampling factor for smooth edges


def triangle(cx, cy, r, up):
    pts = []
    for i in range(3):
        a = math.radians((-90 if up else 90) + i * 120)
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return pts


def thick_outline(draw, pts, width, fill):
    n = len(pts)
    for i in range(n):
        draw.line([pts[i], pts[(i + 1) % n]], fill=fill, width=width)
    for p in pts:  # round the corners
        draw.ellipse([p[0] - width / 2, p[1] - width / 2, p[0] + width / 2, p[1] + width / 2], fill=fill)


def star(size, stroke, glow, filled=False):
    big = size * SS
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    r = big * 0.36
    c = big / 2
    for up in (True, False):
        pts = triangle(c, c, r, up)
        if filled:
            d.polygon(pts, fill=(255, 255, 255, 255))
        else:
            thick_outline(d, pts, stroke * SS, (255, 255, 255, 255))
    img = img.resize((size, size), Image.LANCZOS)
    if glow:
        halo = img.filter(ImageFilter.GaussianBlur(glow))
        # brighten the halo a bit and put the crisp star on top
        halo = Image.eval(halo, lambda v: min(255, int(v * 1.6)))
        out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        out.alpha_composite(halo)
        out.alpha_composite(img)
        img = out
    return img


def main():
    (OUT / "textures/gui").mkdir(parents=True, exist_ok=True)
    star(256, 9, 10).save(OUT / "textures/gui/logo.png")
    star(32, 3, 2).save(OUT / "textures/gui/sparkle.png")
    icon = Image.new("RGBA", (128, 128), (14, 14, 18, 255))
    icon.alpha_composite(star(128, 6, 5))
    icon.save(OUT / "icon.png")


if __name__ == "__main__":
    main()
