"""Draws the Lmg Client cape textures into src/main/resources/assets/lmgclient/textures/cosmetic/.
Vanilla cape layout (64x32, drawn here at 4x): outer face x1..11 y1..17, inner face x12..22 y1..17."""
import math
import random
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter

OUT = Path(__file__).resolve().parents[1] / "src/main/resources/assets/lmgclient/textures/cosmetic"
S = 4
W, H = 64 * S, 32 * S
FACE = (1 * S, 1 * S, 11 * S, 17 * S)       # outer face box
FW, FH = FACE[2] - FACE[0], FACE[3] - FACE[1]


def vgrad(w, h, stops):
    img = Image.new("RGBA", (w, h))
    px = img.load()
    for y in range(h):
        t = y / max(1, h - 1)
        for i in range(len(stops) - 1):
            t0, c0 = stops[i]
            t1, c1 = stops[i + 1]
            if t0 <= t <= t1:
                k = (t - t0) / max(1e-6, t1 - t0)
                c = tuple(int(c0[j] + (c1[j] - c0[j]) * k) for j in range(3)) + (255,)
                break
        for x in range(w):
            px[x, y] = c
    return img


def hexagram(d, cx, cy, r, width, fill):
    for rot in (0, math.pi):
        pts = [(cx + math.sin(rot + math.pi * 2 * k / 3) * r, cy - math.cos(rot + math.pi * 2 * k / 3) * r) for k in range(3)]
        d.line(pts + [pts[0]], fill=fill, width=width, joint="curve")


def stars(img, box, n, seed, color=(255, 255, 255)):
    rnd = random.Random(seed)
    d = ImageDraw.Draw(img)
    for _ in range(n):
        x = rnd.uniform(box[0], box[2]); y = rnd.uniform(box[1], box[3])
        a = rnd.randint(70, 220)
        d.point((x, y), fill=color + (a,))


def base(face):
    """Puts a face design on a full cape: the face on the outside, a darker copy inside and on the edges."""
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    edge = face.resize((1, 1), Image.BILINEAR).getpixel((0, 0))
    dark = tuple(int(c * 0.55) for c in edge[:3]) + (255,)
    ImageDraw.Draw(img).rectangle([0, 0, 22 * S - 1, 17 * S - 1], fill=dark)
    inner = face.transpose(Image.FLIP_LEFT_RIGHT).point(lambda v: int(v * 0.6))
    inner.putalpha(255)
    img.paste(inner, (12 * S, 1 * S))
    img.paste(face, (FACE[0], FACE[1]))
    return img


def trim(face, color, width=2):
    d = ImageDraw.Draw(face)
    d.rectangle([0, 0, FW - 1, FH - 1], outline=color, width=width)


def glow_layer(face, layer, blur=4):
    face.alpha_composite(layer.filter(ImageFilter.GaussianBlur(blur)))
    face.alpha_composite(layer)


def lmg():
    face = vgrad(FW, FH, [(0, (32, 34, 52)), (1, (8, 8, 14))])
    st = Image.new("RGBA", face.size, (0, 0, 0, 0))
    hexagram(ImageDraw.Draw(st), FW / 2, FH * 0.38, 15, 3, (255, 255, 255, 255))
    glow_layer(face, st, 3)
    letters = {"L": ["10", "10", "10", "10", "11"], "M": ["10001", "11011", "10101", "10001", "10001"], "G": ["111", "100", "101", "101", "111"]}
    d = ImageDraw.Draw(face); px = 2; x = FW / 2 - 12 * px / 2; y = FH * 0.7
    for ch in "LMG":
        for ry, row in enumerate(letters[ch]):
            for rx, bit in enumerate(row):
                if bit == "1":
                    d.rectangle([x + rx * px, y + ry * px, x + rx * px + px - 1, y + ry * px + px - 1], fill=(235, 235, 245, 255))
        x += (len(letters[ch][0]) + 1) * px
    trim(face, (235, 235, 245, 255))
    return face


def aurora():
    face = vgrad(FW, FH, [(0, (6, 10, 28)), (0.6, (10, 24, 44)), (1, (4, 8, 16))])
    stars(face, (0, 0, FW, FH * 0.6), 35, 3)
    rib = Image.new("RGBA", face.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(rib)
    for k, (col, off, amp) in enumerate([((60, 255, 170), 0.32, 5), ((70, 200, 255), 0.42, 6), ((170, 110, 255), 0.24, 4)]):
        for x in range(FW):
            y = FH * off + math.sin(x / 7 + k * 2) * amp + math.sin(x / 3.1 + k) * 1.5
            for h in range(22):
                a = int(220 * (1 - h / 22) ** 1.3)
                d.point((x, y - h), fill=col + (a,))
                if h < 3:
                    d.point((x, y + h), fill=col + (int(a * 0.6),))
    face.alpha_composite(rib.filter(ImageFilter.GaussianBlur(1.4)))
    # snowy mountains at the bottom
    d = ImageDraw.Draw(face)
    pts = [(0, FH)]
    rnd = random.Random(9)
    for x in range(0, FW + 6, 6):
        pts.append((x, FH * 0.8 + rnd.uniform(-6, 4)))
    pts.append((FW, FH))
    d.polygon(pts, fill=(10, 16, 28, 255))
    trim(face, (120, 230, 220, 255), 1)
    return face


def royal():
    face = vgrad(FW, FH, [(0, (96, 22, 70)), (1, (40, 6, 34))])
    # velvet sheen: soft vertical folds
    sheen = Image.new("RGBA", face.size, (0, 0, 0, 0)); d = ImageDraw.Draw(sheen)
    for x in range(FW):
        a = int(40 * (0.5 + 0.5 * math.sin(x / FW * math.pi * 5)))
        d.line([(x, 0), (x, FH)], fill=(255, 200, 240, a))
    face.alpha_composite(sheen.filter(ImageFilter.GaussianBlur(2)))
    d = ImageDraw.Draw(face)
    gold, goldd = (240, 196, 84, 255), (170, 120, 40, 255)
    # ermine collar
    d.rectangle([0, 0, FW, 9], fill=(246, 244, 240, 255))
    for i, x in enumerate(range(3, FW, 8)):
        y = 3 + (i % 2) * 2
        d.rectangle([x, y, x + 1, y + 3], fill=(20, 20, 24, 255)); d.point((x - 1, y + 1), fill=(20, 20, 24, 255)); d.point((x + 2, y + 1), fill=(20, 20, 24, 255))
    d.line([(0, 10), (FW, 10)], fill=gold, width=2)
    # gold border and emblem
    d.rectangle([1, 10, FW - 2, FH - 2], outline=gold, width=2)
    d.rectangle([4, 13, FW - 5, FH - 5], outline=goldd, width=1)
    st = Image.new("RGBA", face.size, (0, 0, 0, 0))
    hexagram(ImageDraw.Draw(st), FW / 2, FH * 0.52, 13, 3, gold)
    glow_layer(face, st, 2)
    # fleur dots
    for y in range(18, FH - 6, 9):
        for x in (8, FW - 9):
            d.ellipse([x - 1, y - 1, x + 1, y + 1], fill=gold)
    return face


def void():
    face = vgrad(FW, FH, [(0, (6, 2, 14)), (1, (2, 0, 6))])
    neb = Image.new("RGBA", face.size, (0, 0, 0, 0)); d = ImageDraw.Draw(neb)
    rnd = random.Random(4)
    for _ in range(60):
        x = FW / 2 + rnd.gauss(0, FW * 0.22); y = FH * 0.45 + rnd.gauss(0, FH * 0.2); r = rnd.uniform(3, 9)
        col = rnd.choice([(150, 60, 255), (90, 40, 200), (255, 70, 200), (60, 120, 255)])
        d.ellipse([x - r, y - r, x + r, y + r], fill=col + (rnd.randint(20, 60),))
    face.alpha_composite(neb.filter(ImageFilter.GaussianBlur(5)))
    stars(face, (0, 0, FW, FH), 40, 8)
    # spiral swirl
    sw = Image.new("RGBA", face.size, (0, 0, 0, 0)); d = ImageDraw.Draw(sw)
    cx, cy = FW / 2, FH * 0.45
    for arm in range(2):
        prev = None
        for i in range(160):
            t = i / 160; a = arm * math.pi + t * math.pi * 3.2; r = 2 + t * 18
            p = (cx + math.cos(a) * r, cy + math.sin(a) * r * 0.9)
            if prev: d.line([prev, p], fill=(220, 170, 255, int(230 * (1 - t))), width=2)
            prev = p
    glow_layer(face, sw, 2)
    d = ImageDraw.Draw(face); d.ellipse([cx - 2, cy - 2, cx + 2, cy + 2], fill=(255, 255, 255, 255))
    trim(face, (160, 90, 255, 255), 1)
    return face


def sunset():
    face = vgrad(FW, FH, [(0, (40, 12, 70)), (0.45, (230, 70, 120)), (0.62, (255, 160, 80)), (0.63, (24, 8, 40)), (1, (10, 4, 20))])
    d = ImageDraw.Draw(face)
    cx, cy, r = FW / 2, FH * 0.5, 15
    sun = Image.new("RGBA", face.size, (0, 0, 0, 0)); sd = ImageDraw.Draw(sun)
    for y in range(int(cy - r), int(cy) + 1):
        t = (y - (cy - r)) / r
        col = (255, int(230 - 120 * t), int(90 + 40 * t), 255)
        half = math.sqrt(max(0, r * r - (y - cy) ** 2))
        stripe = t > 0.45 and int((y - cy) * 1.0) % 4 in (0, 1) and t > 0.55
        if not stripe:
            sd.line([(cx - half, y), (cx + half, y)], fill=col)
    face.alpha_composite(sun.filter(ImageFilter.GaussianBlur(4)))
    face.alpha_composite(sun)
    # synthwave grid
    hz = FH * 0.63
    for i in range(1, 9):
        y = hz + (FH - hz) * (i / 8) ** 1.8
        d.line([(0, y), (FW, y)], fill=(255, 80, 200, 200), width=1)
    for k in range(-8, 9):
        d.line([(cx + k * 2, hz), (cx + k * 14, FH)], fill=(255, 80, 200, 200), width=1)
    trim(face, (255, 120, 190, 255), 1)
    return face


def cherry():
    face = vgrad(FW, FH, [(0, (52, 20, 44)), (1, (22, 8, 22))])
    d = ImageDraw.Draw(face)
    rnd = random.Random(12)
    # branch from the top-left corner
    branch = [(0, 8), (10, 14), (18, 13), (26, 20), (32, 30), (36, 44)]
    d.line(branch, fill=(96, 56, 52, 255), width=3, joint="curve")
    d.line([(18, 13), (30, 9), (40, 12)], fill=(96, 56, 52, 255), width=2, joint="curve")
    d.line([(26, 20), (20, 32)], fill=(96, 56, 52, 255), width=2)
    flowers = Image.new("RGBA", face.size, (0, 0, 0, 0)); fd = ImageDraw.Draw(flowers)

    def blossom(x, y, r):
        for k in range(5):
            a = math.pi * 2 * k / 5 + rnd.random()
            px, py = x + math.cos(a) * r * 0.75, y + math.sin(a) * r * 0.75
            fd.ellipse([px - r * 0.6, py - r * 0.6, px + r * 0.6, py + r * 0.6], fill=(255, 183, 214, 255))
        fd.ellipse([x - r * 0.35, y - r * 0.35, x + r * 0.35, y + r * 0.35], fill=(255, 120, 170, 255))

    for (x, y) in [(10, 14), (18, 12), (30, 9), (40, 12), (26, 21), (21, 31), (33, 32), (36, 43), (6, 10)]:
        blossom(x + rnd.uniform(-1, 1), y + rnd.uniform(-1, 1), rnd.uniform(2.0, 2.8))
    for _ in range(16):   # falling petals
        x, y = rnd.uniform(4, FW - 4), rnd.uniform(FH * 0.4, FH - 3)
        fd.point((x, y), fill=(255, 190, 220, 230)); fd.point((x + 1, y), fill=(255, 190, 220, 160))
    glow_layer(face, flowers, 2)
    # moon
    d.ellipse([FW - 14, 4, FW - 4, 14], fill=(255, 236, 220, 255))
    trim(face, (255, 170, 205, 255), 1)
    return face


def ocean():
    face = vgrad(FW, FH, [(0, (10, 22, 58)), (0.5, (24, 70, 130)), (0.52, (12, 48, 98)), (1, (4, 16, 40))])
    d = ImageDraw.Draw(face)
    stars(face, (0, 0, FW, FH * 0.45), 26, 4)
    moon = Image.new("RGBA", face.size, (0, 0, 0, 0)); md = ImageDraw.Draw(moon)
    md.ellipse([FW / 2 - 7, 7, FW / 2 + 7, 21], fill=(255, 246, 220, 255))
    glow_layer(face, moon, 4)
    hz = FH * 0.51
    for i in range(14):   # moonlight on the water
        y = hz + 2 + i * 2.2
        half = 6 - i * 0.3 + (i % 2) * 1.5
        d.line([(FW / 2 - half, y), (FW / 2 + half, y)], fill=(255, 240, 200, max(40, 200 - i * 12)), width=1)
    for row in range(6):  # wave crests
        y = hz + 6 + row * 5
        for x in range(-4, FW + 4, 8):
            off = (row % 2) * 4
            d.arc([x + off, y, x + off + 8, y + 4], 200, 340, fill=(140, 200, 255, 150 - row * 15), width=1)
    trim(face, (120, 190, 255, 255), 1)
    return face


def inferno():
    face = vgrad(FW, FH, [(0, (20, 4, 4)), (0.45, (90, 14, 6)), (1, (255, 120, 20))])
    rnd = random.Random(7)
    fire = Image.new("RGBA", face.size, (0, 0, 0, 0)); fd = ImageDraw.Draw(fire)
    for layer, (col, scale) in enumerate([((255, 70, 20), 1.0), ((255, 150, 30), 0.75), ((255, 230, 120), 0.45)]):
        for k in range(9):
            x = (k + 0.5) * FW / 9 + rnd.uniform(-2, 2)
            h = FH * (0.45 + rnd.uniform(0, 0.35)) * scale
            w = FW / 9 * (1.2 - layer * 0.25)
            pts = [(x - w / 2, FH), (x - w / 4, FH - h * 0.55), (x + rnd.uniform(-2, 2), FH - h), (x + w / 4, FH - h * 0.5), (x + w / 2, FH)]
            fd.polygon(pts, fill=col + (235,))
    glow_layer(face, fire, 3)
    for _ in range(22):   # embers
        x, y = rnd.uniform(2, FW - 2), rnd.uniform(2, FH * 0.6)
        ImageDraw.Draw(face).point((x, y), fill=(255, rnd.randint(150, 230), 80, 255))
    trim(face, (255, 140, 40, 255), 1)
    return face


def rainbow():
    cols = [(232, 64, 72), (247, 141, 54), (250, 214, 70), (88, 196, 104), (64, 150, 232), (138, 92, 220)]
    face = Image.new("RGBA", (FW, FH), (255, 255, 255, 255))
    d = ImageDraw.Draw(face)
    band = FH / len(cols)
    for i, c in enumerate(cols):
        d.rectangle([0, i * band, FW, (i + 1) * band], fill=c + (255,))
    clouds = Image.new("RGBA", face.size, (0, 0, 0, 0)); cd = ImageDraw.Draw(clouds)
    for (cx, cy) in [(12, 14), (31, 40), (14, 58)]:
        for dx, dy, r in [(-5, 1, 4), (0, -2, 5), (5, 1, 4), (0, 2, 4)]:
            cd.ellipse([cx + dx - r, cy + dy - r, cx + dx + r, cy + dy + r], fill=(255, 255, 255, 245))
    glow_layer(face, clouds, 1)
    trim(face, (255, 255, 255, 255), 1)
    return face


def forest():
    face = vgrad(FW, FH, [(0, (14, 18, 44)), (0.6, (34, 52, 86)), (1, (12, 26, 22))])
    d = ImageDraw.Draw(face)
    stars(face, (0, 0, FW, FH * 0.5), 30, 9)
    d.ellipse([FW - 15, 5, FW - 6, 14], fill=(240, 240, 255, 255))
    d.ellipse([FW - 12, 4, FW - 4, 12], fill=(20, 26, 54, 255))   # crescent
    rnd = random.Random(3)
    for layer, (col, base, hmin) in enumerate([((40, 78, 74), FH * 0.62, 14), ((26, 56, 50), FH * 0.78, 18), ((14, 32, 28), FH, 24)]):
        x = -4
        while x < FW + 4:
            h = rnd.uniform(hmin, hmin + 10); w = h * 0.42
            d.polygon([(x - w / 2, base), (x, base - h), (x + w / 2, base)], fill=col + (255,))
            x += w * rnd.uniform(0.55, 0.85)
        d.rectangle([0, base - 1, FW, FH], fill=col + (255,))
    flies = Image.new("RGBA", face.size, (0, 0, 0, 0)); fl = ImageDraw.Draw(flies)
    for _ in range(14):
        x, y = rnd.uniform(3, FW - 3), rnd.uniform(FH * 0.55, FH - 4)
        fl.ellipse([x - 0.8, y - 0.8, x + 0.8, y + 0.8], fill=(255, 240, 120, 255))
    glow_layer(face, flies, 2)
    trim(face, (120, 170, 140, 255), 1)
    return face


def racer():
    face = Image.new("RGBA", (FW, FH), (20, 20, 24, 255))
    d = ImageDraw.Draw(face)
    sq = FW / 8
    for r in range(int(FH / sq) + 1):
        for k in range(8):
            if (r + k) % 2 == 0:
                d.rectangle([k * sq, r * sq, (k + 1) * sq - 1, (r + 1) * sq - 1], fill=(240, 240, 240, 255))
    # a red stripe down the middle with a number
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
    trim(face, (220, 36, 48, 255), 1)
    return face


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    for name, fn in [("cape", lmg), ("cape_aurora", aurora), ("cape_royal", royal), ("cape_void", void), ("cape_sunset", sunset), ("cape_cherry", cherry),
                     ("cape_ocean", ocean), ("cape_inferno", inferno), ("cape_rainbow", rainbow), ("cape_forest", forest), ("cape_racer", racer)]:
        base(fn()).save(OUT / f"{name}.png")
        print("wrote", name)
