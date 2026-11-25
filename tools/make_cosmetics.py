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


def cape():
    # Vanilla cape layout is 64x32; this is 4x (256x128) for a sharp logo.
    s = 4
    img = Image.new("RGBA", (64 * s, 32 * s), (0, 0, 0, 0))
    body = gradient(22 * s, 17 * s, (26, 26, 38), (8, 8, 12))
    img.paste(body, (0, 0))
    d = ImageDraw.Draw(img)
    # outer face (seen from behind the player): x 1..11, y 1..17
    fx0, fy0, fx1, fy1 = 1 * s, 1 * s, 11 * s, 17 * s
    d.rectangle([fx0, fy0, fx1 - 1, fy1 - 1], outline=(235, 235, 245, 255), width=2)
    star = Image.new("RGBA", img.size, (0, 0, 0, 0))
    hexagram(ImageDraw.Draw(star), (fx0 + fx1) / 2, fy0 + 6.5 * s, 4.0 * s, 3, (255, 255, 255, 255))
    glow = star.filter(ImageFilter.GaussianBlur(3))
    img.alpha_composite(glow)
    img.alpha_composite(star)
    # "LMG" in small blocky letters under the star
    letters = {
        "L": ["10", "10", "10", "10", "11"],
        "M": ["10001", "11011", "10101", "10001", "10001"],
        "G": ["111", "100", "101", "101", "111"],
    }
    px = 2
    x = (fx0 + fx1) / 2 - (2 + 5 + 3 + 2) * px / 2
    y = fy0 + 11.5 * s
    for ch in "LMG":
        rows = letters[ch]
        for ry, row in enumerate(rows):
            for rx, bit in enumerate(row):
                if bit == "1":
                    d.rectangle([x + rx * px, y + ry * px, x + rx * px + px - 1, y + ry * px + px - 1], fill=(230, 230, 240, 255))
        x += (len(rows[0]) + 1) * px
    # elytra area: x 22..46, y 0..22
    ely = gradient(24 * s, 22 * s, (30, 30, 44), (8, 8, 12))
    img.paste(ely, (22 * s, 0))
    st = Image.new("RGBA", img.size, (0, 0, 0, 0))
    hexagram(ImageDraw.Draw(st), 34 * s, 8 * s, 3.0 * s, 3, (255, 255, 255, 230))
    img.alpha_composite(st.filter(ImageFilter.GaussianBlur(3)))
    img.alpha_composite(st)
    img.save(OUT / "cape.png")


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


def crown():
    # 64x16 band, repeated on each side of the head.
    w, h = 64, 16
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    gold, dark, light = (240, 190, 60, 255), (170, 115, 25, 255), (255, 230, 140, 255)
    d.rectangle([0, 10, w - 1, h - 1], fill=gold)
    d.line([(0, 10), (w - 1, 10)], fill=light)
    d.line([(0, h - 1), (w - 1, h - 1)], fill=dark)
    for i in range(4):  # spikes
        cx = 8 + i * 16
        d.polygon([(cx - 7, 10), (cx, 1), (cx + 7, 10)], fill=gold, outline=dark)
        d.ellipse([cx - 2, 0, cx + 2, 4], fill=light)
    for i in range(4):  # gems
        cx = 8 + i * 16
        color = [(220, 40, 60, 255), (60, 120, 230, 255)][i % 2]
        d.rectangle([cx - 2, 11, cx + 2, 14], fill=color)
        d.point((cx - 1, 12), fill=(255, 255, 255, 255))
    img.save(OUT / "crown.png")


def ear():
    size, sup = 32, 4
    big = size * sup
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.polygon([(big * 0.08, big * 0.98), (big * 0.5, big * 0.04), (big * 0.92, big * 0.98)], fill=(255, 255, 255, 255))
    d.polygon([(big * 0.28, big * 0.92), (big * 0.5, big * 0.32), (big * 0.72, big * 0.92)], fill=(200, 170, 180, 255))
    img.resize((size, size), Image.LANCZOS).save(OUT / "cat_ear.png")


def halo():
    size, sup = 64, 4
    big = size * sup
    ring = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    d = ImageDraw.Draw(ring)
    m = big * 0.14
    d.ellipse([m, m, big - m, big - m], outline=(255, 255, 255, 255), width=int(big * 0.07))
    ring = ring.resize((size, size), Image.LANCZOS)
    glow = ring.filter(ImageFilter.GaussianBlur(3))
    glow = Image.eval(glow, lambda v: min(255, int(v * 1.4)))
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.alpha_composite(glow)
    out.alpha_composite(ring)
    out.save(OUT / "halo.png")


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    cape()
    wing()
    crown()
    ear()
    halo()
    Image.new("RGBA", (4, 4), (255, 255, 255, 255)).save(OUT / "white.png")
    print("done")
