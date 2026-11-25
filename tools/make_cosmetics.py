"""Generates the Lmg Client cosmetic textures (cape, wings, crown, cat ear, halo).

Run from the repo root:  python tools/make_cosmetics.py   (needs Pillow)
"""
import math
import random
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter

OUT = Path("src/main/resources/assets/lmgclient/textures/cosmetic")


def hexagram(draw, cx, cy, r, width, fill):
    for up in (True, False):
        pts = []
        for i in range(3):
            a = math.radians((-90 if up else 90) + i * 120)
            pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
        for i in range(3):
            draw.line([pts[i], pts[(i + 1) % 3]], fill=fill, width=width)
        for p in pts:
            draw.ellipse([p[0] - width / 2, p[1] - width / 2, p[0] + width / 2, p[1] + width / 2], fill=fill)


def gradient(w, h, top, bottom):
    img = Image.new("RGBA", (w, h))
    for y in range(h):
        t = y / max(1, h - 1)
        c = tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3)) + (255,)
        ImageDraw.Draw(img).line([(0, y), (w, y)], fill=c)
    return img




def feather(ox, oy, ang, length, width, steps=48):
    """Outline points of one rounded feather starting at (ox, oy), pointing at angle ang."""
    pts = []
    for k in range(steps):
        a = math.pi * 2 * k / steps
        t = (1 + math.cos(a)) / 2                      # 0 at the quill, 1 at the tip
        half = width / 2 * (math.sin(math.pi * min(1.0, t * 1.05)) ** 0.55)
        ly = math.sin(a) * half / max(1e-6, abs(math.sin(a))) * abs(math.sin(a)) if half else 0
        lx = t * length
        pts.append((ox + lx * math.cos(ang) - ly * math.sin(ang), oy + lx * math.sin(ang) + ly * math.cos(ang)))
    return pts


def wing():
    """One wing, near-white so in-game tints work. The shoulder is at the top-left corner;
    the game splits it into an inner half (u < 0.42) and an outer half that folds."""
    size, sup = 128, 4
    big = size * sup
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    rnd = random.Random(5)
    ox, oy = big * 0.04, big * 0.09
    # rows from the back (long primaries) to the front (small coverts)
    rows = [
        # count, reach, width, angle from, angle to, base shade
        (9, 0.98, 0.17, 4, 62, 232),
        (8, 0.70, 0.18, 2, 56, 242),
        (7, 0.46, 0.18, 0, 48, 250),
        (6, 0.26, 0.18, -2, 38, 255),
    ]
    for count, reach, width, a0, a1, shade in rows:
        layer = Image.new("RGBA", (big, big), (0, 0, 0, 0))
        d = ImageDraw.Draw(layer)
        for i in range(count):
            t = i / max(1, count - 1)
            ang = math.radians(a0 + t * (a1 - a0) + rnd.uniform(-1.5, 1.5))
            length = big * reach * (1.0 - 0.30 * t)
            w = big * width * (1.0 - 0.25 * t)
            pts = feather(ox, oy, ang, length, w)
            c = shade - rnd.randint(0, 6)
            d.polygon(pts, fill=(c, c, c, 255))
            d.line(pts + [pts[0]], fill=(150, 150, 158, 255), width=sup * 2)
            # quill: a soft darker line down the middle of the feather
            ex, ey = ox + math.cos(ang) * length * 0.85, oy + math.sin(ang) * length * 0.85
            d.line([(ox, oy), (ex, ey)], fill=(205, 205, 212, 255), width=sup * 2)
        # small drop shadow so each row reads as sitting on top of the one behind it
        shadow = layer.split()[3].point(lambda a: 70 if a > 0 else 0)
        sh = Image.new("RGBA", (big, big), (40, 40, 48, 0))
        sh.putalpha(shadow.filter(ImageFilter.GaussianBlur(sup * 2)))
        img.alpha_composite(sh, (sup * 2, sup * 3))
        img.alpha_composite(layer)
    img = img.resize((size, size), Image.LANCZOS)
    img.save(OUT / "wing.png")


def membrane():
    """Bat/demon wing skin: lighter in the middle, darker toward the edges, with branching veins.
    Grayscale so the chosen colour tints it. Mapped flat across each wing (top-left = wrist)."""
    size, sup = 128, 4
    big = size * sup
    img = Image.new("RGBA", (big, big), (0, 0, 0, 255))
    px = img.load()
    for y in range(0, big, 2):
        for x in range(0, big, 2):
            d = math.hypot(x / big - 0.35, y / big - 0.35)
            v = int(255 * max(0.45, min(1.0, 1.05 - d * 0.75)))
            for dy in (0, 1):
                for dx in (0, 1):
                    px[x + dx, y + dy] = (v, v, v, 255)
    d = ImageDraw.Draw(img)
    rnd = random.Random(9)
    def vein(x, y, ang, length, width, depth):
        if depth == 0 or length < big * 0.04:
            return
        ex, ey = x + math.cos(ang) * length, y + math.sin(ang) * length
        mx, my = (x + ex) / 2 + rnd.uniform(-1, 1) * length * 0.08, (y + ey) / 2 + rnd.uniform(-1, 1) * length * 0.08
        d.line([(x, y), (mx, my), (ex, ey)], fill=(150, 150, 150, 255), width=max(sup, int(width)))
        for _ in range(2):
            vein(mx, my, ang + rnd.uniform(-0.7, 0.7), length * 0.55, width * 0.6, depth - 1)
    for i in range(5):
        vein(0, 0, math.radians(10 + i * 16), big * 0.95, sup * 2.2, 2)
    img = img.filter(ImageFilter.GaussianBlur(sup * 1.5)).resize((size, size), Image.LANCZOS)
    img.save(OUT / "membrane.png")


def butterfly():
    """Butterfly wings (upper + lower). Light areas take the chosen colour; black veins and
    edges stay dark; spots are white. The wing root is at the left edge, middle height."""
    size, sup = 128, 4
    big = size * sup
    for name, shape, spots in (
        ("butterfly_upper.png", [(0.02, 0.62), (0.18, 0.18), (0.55, 0.02), (0.92, 0.06), (0.98, 0.30), (0.80, 0.62), (0.45, 0.80), (0.08, 0.80)], [(0.72, 0.22, 0.07), (0.55, 0.40, 0.05)]),
        ("butterfly_lower.png", [(0.02, 0.10), (0.40, 0.04), (0.78, 0.22), (0.92, 0.55), (0.78, 0.90), (0.50, 0.98), (0.20, 0.80), (0.04, 0.45)], [(0.55, 0.55, 0.08)]),
    ):
        img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
        mask = Image.new("L", (big, big), 0)
        poly = [(x * big, y * big) for x, y in shape]
        ImageDraw.Draw(mask).polygon(poly, fill=255)
        mask = mask.filter(ImageFilter.GaussianBlur(sup * 2)).point(lambda a: 255 if a > 128 else 0)
        body = Image.new("RGBA", (big, big), (0, 0, 0, 255))
        bd = ImageDraw.Draw(body)
        # colour area: bright near the root, slightly darker outward
        for r in range(int(big * 1.2), 0, -sup * 2):
            v = int(150 + 105 * (1 - r / (big * 1.2)))
            bd.ellipse([-r, big * 0.5 - r, r, big * 0.5 + r], fill=(v, v, v, 255))
        # black outer band
        band = mask.filter(ImageFilter.MinFilter(17))
        edge = Image.new("RGBA", (big, big), (12, 12, 16, 255))
        body = Image.composite(body, edge, band)
        bd = ImageDraw.Draw(body)
        # veins from the root
        for i in range(7):
            a = math.radians(-60 + i * 20)
            bd.line([(0, big * 0.5), (math.cos(a) * big, big * 0.5 + math.sin(a) * big)], fill=(30, 30, 38, 255), width=sup * 2)
        for sx, sy, r in spots:
            bd.ellipse([sx * big - r * big, sy * big - r * big, sx * big + r * big, sy * big + r * big], fill=(255, 255, 255, 255))
            bd.ellipse([sx * big - r * big * 0.45, sy * big - r * big * 0.45, sx * big + r * big * 0.45, sy * big + r * big * 0.45], fill=(25, 25, 30, 255))
        img.paste(body, (0, 0), mask)
        img.resize((size, size), Image.LANCZOS).save(OUT / name)


def soft_bar():
    """A soft vertical glow strip (bright centre line fading out) for neon / energy parts."""
    w, h = 32, 8
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    for x in range(w):
        a = max(0.0, 1 - abs(x - (w - 1) / 2) / ((w - 1) / 2)) ** 1.8
        for y in range(h):
            img.putpixel((x, y), (255, 255, 255, int(255 * a)))
    img.save(OUT / "glow_bar.png")






if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    # capes are drawn by tools/make_capes.py
    wing()
    membrane()
    butterfly()
    soft_bar()
    Image.new("RGBA", (4, 4), (255, 255, 255, 255)).save(OUT / "white.png")
    print("done")
