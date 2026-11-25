"""Draws the Lmg Client totem (a golden Star of David) into the built-in "Lmg Totem" pack.

Run from the repo root:  python tools/make_totem.py   (needs Pillow; reads pack format from the game jar)
"""
import glob
import json
import math
import zipfile
from pathlib import Path
from PIL import Image, ImageDraw

OUT = Path("src/main/resources/resourcepacks/lmg_totem")
SIZE, SS = 64, 8


def triangle(c, r, up):
    return [(c + r * math.cos(math.radians((-90 if up else 90) + i * 120)),
             c + r * math.sin(math.radians((-90 if up else 90) + i * 120))) for i in range(3)]


def star(draw, c, r, width, fill):
    for up in (True, False):
        pts = triangle(c, r, up)
        for i in range(3):
            draw.line([pts[i], pts[(i + 1) % 3]], fill=fill, width=int(width))
        for p in pts:
            draw.ellipse([p[0] - width / 2, p[1] - width / 2, p[0] + width / 2, p[1] + width / 2], fill=fill)


def main():
    big = SIZE * SS
    c, r = big / 2, big * 0.40
    # 1) dark outline, 2) gold body, 3) light highlight band
    shape = Image.new("L", (big, big), 0)
    star(ImageDraw.Draw(shape), c, r, big * 0.15, 255)
    body = Image.new("L", (big, big), 0)
    star(ImageDraw.Draw(body), c, r, big * 0.105, 255)
    shine = Image.new("L", (big, big), 0)
    star(ImageDraw.Draw(shine), c, r, big * 0.035, 255)

    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    outline_col = (92, 56, 0, 255)
    img.paste(Image.new("RGBA", (big, big), outline_col), mask=shape)
    gold = Image.new("RGBA", (big, big))
    gd = ImageDraw.Draw(gold)
    for y in range(big):  # vertical gold gradient
        t = y / big
        gd.line([(0, y), (big, y)], fill=(int(255 - 30 * t), int(222 - 70 * t), int(110 - 80 * t), 255))
    img.paste(gold, mask=body)
    img.paste(Image.new("RGBA", (big, big), (255, 248, 200, 255)), mask=shine)

    img = img.resize((SIZE, SIZE), Image.LANCZOS)
    # item textures are drawn "cutout": make every pixel fully solid or fully clear
    px = img.load()
    for y in range(SIZE):
        for x in range(SIZE):
            rr, gg, bb, aa = px[x, y]
            px[x, y] = (rr, gg, bb, 255) if aa >= 110 else (0, 0, 0, 0)

    tex = OUT / "assets/minecraft/textures/item"
    tex.mkdir(parents=True, exist_ok=True)
    img.save(tex / "totem_of_undying.png")

    jars = sorted(glob.glob(str(Path.home() / ".gradle/caches/fabric-loom/*/minecraft-client.jar")))
    fmt = json.loads(zipfile.ZipFile(jars[-1]).read("version.json"))["pack_version"]["resource_major"] if jars else 97
    meta = {"pack": {"description": "Lmg Client - Star of David totem", "min_format": fmt, "max_format": fmt}}
    (OUT / "pack.mcmeta").write_text(json.dumps(meta, indent=2) + "\n")
    print("Wrote totem texture (pack format %d)" % fmt)


if __name__ == "__main__":
    main()
