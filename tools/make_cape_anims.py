"""Animated Lmg capes: draws each cape as FRAMES pictures (a seamless loop) and stacks them top to bottom into
textures/cosmetic/anim/<cape>.png (26.3) and the 1.8.9 edition's textures/capes/anim/<cape>.png.
The game shows one frame at a time (AnimatedCapes.java). The still pictures from make_capes.py stay for menus.

    python3 tools/make_cape_anims.py            (needs Pillow)
"""
import math
import random
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, str(Path(__file__).resolve().parent))
import make_capes as mc  # noqa: E402  (helpers: vgrad, base, trim, glow_layer, hexagram, stars, sizes)

FRAMES = 24
FW, FH = mc.FW, mc.FH
TAU = math.pi * 2
OUT_MAIN = mc.OUT / "anim"
OUT_LEGACY = Path(__file__).resolve().parents[1] / "legacy-1.8.9/src/main/resources/assets/lmgclient/textures/capes/anim"


def twinkle(img, box, n, seed, t, color=(255, 255, 255)):
    """Stars that fade in and out, each at its own pace (whole cycles per loop, so it stays seamless)."""
    rnd = random.Random(seed)
    d = ImageDraw.Draw(img)
    for _ in range(n):
        x, y = rnd.uniform(box[0], box[2]), rnd.uniform(box[1], box[3])
        speed, phase, base_a = rnd.choice([1, 2, 3]), rnd.random(), rnd.randint(90, 230)
        a = int(base_a * (0.35 + 0.65 * (0.5 + 0.5 * math.sin(TAU * (t * speed + phase)))))
        d.point((x, y), fill=color + (a,))


def shine(face, t, color=(255, 255, 255), strength=110, width=10, start=0.15, length=0.45):
    """A soft diagonal light that sweeps across once per loop, between `start` and `start+length` of the loop."""
    u = (t - start) / length
    if not 0 <= u <= 1:
        return
    layer = Image.new("RGBA", face.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    cx = -FH * 0.6 + u * (FW + FH * 1.2)
    for k in range(-width, width + 1):
        a = int(strength * (1 - abs(k) / (width + 1)) ** 2)
        d.line([(cx + k - FH * 0.5, FH), (cx + k + FH * 0.5, 0)], fill=color + (a,), width=1)
    mask = face.split()[3]
    layer.putalpha(Image.composite(layer.split()[3], Image.new("L", face.size, 0), mask))
    face.alpha_composite(layer.filter(ImageFilter.GaussianBlur(1)))


def pulse_star(face, cx, cy, r, width, color, t, blur=3, lo=0.35):
    st = Image.new("RGBA", face.size, (0, 0, 0, 0))
    mc.hexagram(ImageDraw.Draw(st), cx, cy, r, width, color)
    k = lo + (1 - lo) * (0.5 + 0.5 * math.sin(TAU * t))
    glow = st.filter(ImageFilter.GaussianBlur(blur + 2 * k))
    glow.putalpha(glow.split()[3].point(lambda v: int(v * k)))
    face.alpha_composite(glow)
    face.alpha_composite(st)


# ---- the capes ----------------------------------------------------------------------------------

def lmg(t):
    face = vgrad_cached("lmg", lambda: mc.vgrad(FW, FH, [(0, (32, 34, 52)), (1, (8, 8, 14))]))
    pulse_star(face, FW / 2, FH * 0.38, 15, 3, (255, 255, 255, 255), t)
    letters = {"L": ["10", "10", "10", "10", "11"], "M": ["10001", "11011", "10101", "10001", "10001"], "G": ["111", "100", "101", "101", "111"]}
    d = ImageDraw.Draw(face)
    px = 2
    x = FW / 2 - 12 * px / 2
    y = FH * 0.7
    for ch in "LMG":
        for ry, row in enumerate(letters[ch]):
            for rx, bit in enumerate(row):
                if bit == "1":
                    d.rectangle([x + rx * px, y + ry * px, x + rx * px + px - 1, y + ry * px + px - 1], fill=(235, 235, 245, 255))
        x += (len(letters[ch][0]) + 1) * px
    shine(face, t, strength=90)
    mc.trim(face, (235, 235, 245, 255))
    return face


def aurora(t):
    face = mc.vgrad(FW, FH, [(0, (6, 10, 28)), (0.6, (10, 24, 44)), (1, (4, 8, 16))])
    twinkle(face, (0, 0, FW, FH * 0.6), 35, 3, t)
    rib = Image.new("RGBA", face.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(rib)
    for k, (col, off, amp) in enumerate([((60, 255, 170), 0.32, 5), ((70, 200, 255), 0.42, 6), ((170, 110, 255), 0.24, 4)]):
        glow = 0.75 + 0.25 * math.sin(TAU * (t + k / 3))
        for x in range(FW):
            y = FH * off + math.sin(x / 7 + k * 2 + TAU * t) * amp + math.sin(x / 3.1 + k - TAU * 2 * t) * 1.5
            for h in range(22):
                a = int(220 * glow * (1 - h / 22) ** 1.3)
                d.point((x, y - h), fill=col + (a,))
                if h < 3:
                    d.point((x, y + h), fill=col + (int(a * 0.6),))
    face.alpha_composite(rib.filter(ImageFilter.GaussianBlur(1.4)))
    d = ImageDraw.Draw(face)
    pts = [(0, FH)]
    rnd = random.Random(9)
    for x in range(0, FW + 6, 6):
        pts.append((x, FH * 0.8 + rnd.uniform(-6, 4)))
    pts.append((FW, FH))
    d.polygon(pts, fill=(10, 16, 28, 255))
    mc.trim(face, (120, 230, 220, 255), 1)
    return face


def royal(t):
    face = mc.royal()
    # the emblem breathes and a gold glint runs across
    pulse_star(face, FW / 2, FH * 0.52, 13, 3, (240, 196, 84, 255), t, blur=2, lo=0.2)
    shine(face, t, color=(255, 236, 170), strength=120, width=8)
    return face


def void(t):
    face = vgrad_cached("void", lambda: mc.vgrad(FW, FH, [(0, (6, 2, 14)), (1, (2, 0, 6))]))
    neb = Image.new("RGBA", face.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(neb)
    rnd = random.Random(4)
    for _ in range(60):
        x = FW / 2 + rnd.gauss(0, FW * 0.22)
        y = FH * 0.45 + rnd.gauss(0, FH * 0.2)
        r = rnd.uniform(3, 9) * (0.9 + 0.1 * math.sin(TAU * t + rnd.random() * TAU))
        col = rnd.choice([(150, 60, 255), (90, 40, 200), (255, 70, 200), (60, 120, 255)])
        d.ellipse([x - r, y - r, x + r, y + r], fill=col + (rnd.randint(20, 60),))
    face.alpha_composite(neb.filter(ImageFilter.GaussianBlur(5)))
    twinkle(face, (0, 0, FW, FH), 40, 8, t)
    sw = Image.new("RGBA", face.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(sw)
    cx, cy = FW / 2, FH * 0.45
    spin = t * math.pi   # two arms, so half a turn loops perfectly
    for arm in range(2):
        prev = None
        for i in range(160):
            u = i / 160
            a = arm * math.pi + u * math.pi * 3.2 + spin
            r = 2 + u * 18
            p = (cx + math.cos(a) * r, cy + math.sin(a) * r * 0.9)
            if prev:
                d.line([prev, p], fill=(220, 170, 255, int(230 * (1 - u))), width=2)
            prev = p
    mc.glow_layer(face, sw, 2)
    d = ImageDraw.Draw(face)
    core = 2 + 0.8 * math.sin(TAU * t)
    d.ellipse([cx - core, cy - core, cx + core, cy + core], fill=(255, 255, 255, 255))
    mc.trim(face, (160, 90, 255, 255), 1)
    return face


def sunset(t):
    face = mc.vgrad(FW, FH, [(0, (40, 12, 70)), (0.45, (230, 70, 120)), (0.62, (255, 160, 80)), (0.63, (24, 8, 40)), (1, (10, 4, 20))])
    d = ImageDraw.Draw(face)
    cx, cy, r = FW / 2, FH * 0.5, 15
    sun = Image.new("RGBA", face.size, (0, 0, 0, 0))
    sd = ImageDraw.Draw(sun)
    for y in range(int(cy - r), int(cy) + 1):
        u = (y - (cy - r)) / r
        col = (255, int(230 - 120 * u), int(90 + 40 * u), 255)
        half = math.sqrt(max(0, r * r - (y - cy) ** 2))
        # the gaps in the sun slide down, like the classic synthwave sun
        stripe = u > 0.55 and int((y - cy) * 1.0 - t * 4) % 4 in (0, 1)
        if not stripe:
            sd.line([(cx - half, y), (cx + half, y)], fill=col)
    face.alpha_composite(sun.filter(ImageFilter.GaussianBlur(4)))
    face.alpha_composite(sun)
    hz = FH * 0.63
    for i in range(0, 9):
        f = (i + t) / 8
        y = hz + (FH - hz) * f ** 1.8
        d.line([(0, y), (FW, y)], fill=(255, 80, 200, int(80 + 120 * min(1, f * 1.5))), width=1)
    for k in range(-8, 9):
        d.line([(cx + k * 2, hz), (cx + k * 14, FH)], fill=(255, 80, 200, 200), width=1)
    mc.trim(face, (255, 120, 190, 255), 1)
    return face


def cherry(t):
    face = mc.cherry()
    # extra petals drifting down and swaying
    rnd = random.Random(21)
    d = ImageDraw.Draw(face)
    for _ in range(14):
        x0, y0, sway, speed = rnd.uniform(3, FW - 3), rnd.uniform(0, FH), rnd.uniform(1.5, 3.5), rnd.choice([1, 2])
        y = (y0 + t * FH * speed) % FH
        x = x0 + math.sin(TAU * (t * speed) + y0) * sway
        d.point((x, y), fill=(255, 190, 220, 240))
        d.point((x + 1, y), fill=(255, 170, 210, 170))
    mc.trim(face, (255, 170, 205, 255), 1)
    return face


def ocean(t):
    face = mc.vgrad(FW, FH, [(0, (10, 22, 58)), (0.5, (24, 70, 130)), (0.52, (12, 48, 98)), (1, (4, 16, 40))])
    d = ImageDraw.Draw(face)
    twinkle(face, (0, 0, FW, FH * 0.45), 26, 4, t)
    moon = Image.new("RGBA", face.size, (0, 0, 0, 0))
    ImageDraw.Draw(moon).ellipse([FW / 2 - 7, 7, FW / 2 + 7, 21], fill=(255, 246, 220, 255))
    mc.glow_layer(face, moon, 4)
    hz = FH * 0.51
    for i in range(14):
        y = hz + 2 + i * 2.2
        half = 6 - i * 0.3 + (i % 2) * 1.5 + 1.6 * math.sin(TAU * t * 2 + i * 0.9)
        d.line([(FW / 2 - half, y), (FW / 2 + half, y)], fill=(255, 240, 200, max(40, 200 - i * 12)), width=1)
    for row in range(6):
        y = hz + 6 + row * 5
        off = (row % 2) * 4 + t * 8 * (1 if row % 2 == 0 else -1)
        for x in range(-16, FW + 16, 8):
            d.arc([x + off, y, x + off + 8, y + 4], 200, 340, fill=(140, 200, 255, 150 - row * 15), width=1)
    mc.trim(face, (120, 190, 255, 255), 1)
    return face


def inferno(t):
    face = mc.vgrad(FW, FH, [(0, (20, 4, 4)), (0.45, (90, 14, 6)), (1, (255, 120, 20))])
    rnd = random.Random(7)
    fire = Image.new("RGBA", face.size, (0, 0, 0, 0))
    fd = ImageDraw.Draw(fire)
    for layer, (col, scale) in enumerate([((255, 70, 20), 1.0), ((255, 150, 30), 0.75), ((255, 230, 120), 0.45)]):
        for k in range(9):
            x = (k + 0.5) * FW / 9 + rnd.uniform(-2, 2)
            base_h = FH * (0.45 + rnd.uniform(0, 0.35)) * scale
            speed, phase = rnd.choice([1, 2, 3]), rnd.random()
            h = base_h * (0.8 + 0.2 * math.sin(TAU * (t * speed + phase)))
            lean = 2.5 * math.sin(TAU * (t * speed + phase + 0.25))
            w = FW / 9 * (1.2 - layer * 0.25)
            pts = [(x - w / 2, FH), (x - w / 4 + lean * 0.5, FH - h * 0.55), (x + lean, FH - h), (x + w / 4 + lean * 0.5, FH - h * 0.5), (x + w / 2, FH)]
            fd.polygon(pts, fill=col + (235,))
    mc.glow_layer(face, fire, 3)
    d = ImageDraw.Draw(face)
    for _ in range(22):   # embers rise and wrap round
        x0, y0, speed = rnd.uniform(2, FW - 2), rnd.uniform(0, FH), rnd.choice([1, 2])
        y = (y0 - t * FH * speed) % FH
        x = x0 + math.sin(TAU * t * speed + y0) * 1.5
        d.point((x, y), fill=(255, rnd.randint(150, 230), 80, int(255 * min(1, y / (FH * 0.5)))))
    mc.trim(face, (255, 140, 40, 255), 1)
    return face


def rainbow(t):
    cols = [(232, 64, 72), (247, 141, 54), (250, 214, 70), (88, 196, 104), (64, 150, 232), (138, 92, 220)]
    face = Image.new("RGBA", (FW, FH), (255, 255, 255, 255))
    d = ImageDraw.Draw(face)
    band = FH / len(cols)
    for i, c in enumerate(cols):
        d.rectangle([0, i * band, FW, (i + 1) * band], fill=c + (255,))
    clouds = Image.new("RGBA", face.size, (0, 0, 0, 0))
    cd = ImageDraw.Draw(clouds)
    span = FW + 24
    for (cx, cy, speed) in [(12, 14, 1), (31, 40, -1), (14, 58, 1)]:
        cx = (cx + t * span * speed + 12) % span - 12
        for dx, dy, r in [(-5, 1, 4), (0, -2, 5), (5, 1, 4), (0, 2, 4)]:
            cd.ellipse([cx + dx - r, cy + dy - r, cx + dx + r, cy + dy + r], fill=(255, 255, 255, 245))
    mc.glow_layer(face, clouds, 1)
    shine(face, t, strength=70, width=8, start=0.5, length=0.4)
    mc.trim(face, (255, 255, 255, 255), 1)
    return face


def forest(t):
    face = mc.vgrad(FW, FH, [(0, (14, 18, 44)), (0.6, (34, 52, 86)), (1, (12, 26, 22))])
    d = ImageDraw.Draw(face)
    twinkle(face, (0, 0, FW, FH * 0.5), 30, 9, t)
    d.ellipse([FW - 15, 5, FW - 6, 14], fill=(240, 240, 255, 255))
    d.ellipse([FW - 12, 4, FW - 4, 12], fill=(20, 26, 54, 255))
    rnd = random.Random(3)
    for col, base_y, hmin in [((40, 78, 74), FH * 0.62, 14), ((26, 56, 50), FH * 0.78, 18), ((14, 32, 28), FH, 24)]:
        x = -4
        while x < FW + 4:
            h = rnd.uniform(hmin, hmin + 10)
            w = h * 0.42
            d.polygon([(x - w / 2, base_y), (x, base_y - h), (x + w / 2, base_y)], fill=col + (255,))
            x += w * rnd.uniform(0.55, 0.85)
        d.rectangle([0, base_y - 1, FW, FH], fill=col + (255,))
    flies = Image.new("RGBA", face.size, (0, 0, 0, 0))
    fl = ImageDraw.Draw(flies)
    for _ in range(16):
        x0, y0 = rnd.uniform(4, FW - 4), rnd.uniform(FH * 0.55, FH - 5)
        speed, phase = rnd.choice([1, 2]), rnd.random()
        x = x0 + 2.5 * math.cos(TAU * (t * speed + phase))
        y = y0 + 1.5 * math.sin(TAU * (t * speed + phase) * 2)
        a = int(255 * max(0, math.sin(TAU * (t * speed + phase))) ** 0.7)
        if a > 8:
            fl.ellipse([x - 0.8, y - 0.8, x + 0.8, y + 0.8], fill=(255, 240, 120, a))
    mc.glow_layer(face, flies, 2)
    mc.trim(face, (120, 170, 140, 255), 1)
    return face


def racer(t):
    face = Image.new("RGBA", (FW, FH), (20, 20, 24, 255))
    d = ImageDraw.Draw(face)
    sq = FW / 8
    scroll = t * sq * 2   # two squares per loop keeps the pattern seamless
    for r in range(-2, int(FH / sq) + 2):
        for k in range(8):
            if (r + k) % 2 == 0:
                y = r * sq + scroll
                d.rectangle([k * sq, y, (k + 1) * sq - 1, y + sq - 1], fill=(240, 240, 240, 255))
    d.rectangle([FW / 2 - 7, 0, FW / 2 + 6, FH], fill=(220, 36, 48, 255))
    d.rectangle([FW / 2 - 9, 0, FW / 2 - 8, FH], fill=(255, 255, 255, 255))
    d.rectangle([FW / 2 + 7, 0, FW / 2 + 8, FH], fill=(255, 255, 255, 255))
    digits = {"0": ["111", "101", "101", "101", "111"], "1": ["010", "110", "010", "010", "111"]}
    px, y = 2, FH * 0.36
    x = FW / 2 - 7 * px / 2
    for ch in "01":
        for ry, row in enumerate(digits[ch]):
            for rx, bit in enumerate(row):
                if bit == "1":
                    d.rectangle([x + rx * px, y + ry * px, x + rx * px + px - 1, y + ry * px + px - 1], fill=(255, 255, 255, 255))
        x += 4 * px
    shine(face, t, strength=100, width=6, start=0.1, length=0.35)
    mc.trim(face, (220, 36, 48, 255), 1)
    return face


_CACHE = {}


def vgrad_cached(key, make):
    if key not in _CACHE:
        _CACHE[key] = make()
    return _CACHE[key].copy()


CAPES = [("cape", lmg), ("cape_aurora", aurora), ("cape_royal", royal), ("cape_void", void), ("cape_sunset", sunset),
         ("cape_cherry", cherry), ("cape_ocean", ocean), ("cape_inferno", inferno), ("cape_rainbow", rainbow),
         ("cape_forest", forest), ("cape_racer", racer)]

if __name__ == "__main__":
    only = set(sys.argv[1:])
    OUT_MAIN.mkdir(parents=True, exist_ok=True)
    OUT_LEGACY.mkdir(parents=True, exist_ok=True)
    for name, fn in CAPES:
        if only and name not in only:
            continue
        strip = Image.new("RGBA", (mc.W, mc.H * FRAMES), (0, 0, 0, 0))
        for f in range(FRAMES):
            strip.paste(mc.base(fn(f / FRAMES)), (0, f * mc.H))
        strip.save(OUT_MAIN / f"{name}.png", optimize=True)
        strip.save(OUT_LEGACY / f"{name}.png", optimize=True)
        print("animated", name, f"{(OUT_MAIN / f'{name}.png').stat().st_size // 1024} KB")
