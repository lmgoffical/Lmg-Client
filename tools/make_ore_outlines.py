"""Builds the "Lmg Ore Outlines" resource pack: vanilla ore textures with a bright,
colored outline around the ore so it's easy to spot. It does NOT show ores through walls.

Run from the repo root (needs Pillow and a Gradle build done once, so the game jar is cached):
    python tools/make_ore_outlines.py [path/to/minecraft-client.jar]
"""
import glob
import io
import json
import sys
import zipfile
from pathlib import Path
from PIL import Image

OUT = Path("src/main/resources/resourcepacks/ore_outlines")
TEX = OUT / "assets/minecraft/textures/block"

# ore texture -> (base texture it sits on, outline color)
ORES = {
    "coal_ore": ("stone", (20, 20, 20)),
    "iron_ore": ("stone", (236, 178, 140)),
    "copper_ore": ("stone", (240, 120, 60)),
    "gold_ore": ("stone", (255, 220, 40)),
    "redstone_ore": ("stone", (255, 30, 30)),
    "emerald_ore": ("stone", (30, 240, 90)),
    "lapis_ore": ("stone", (40, 90, 255)),
    "diamond_ore": ("stone", (60, 240, 240)),
    "deepslate_coal_ore": ("deepslate", (10, 10, 10)),
    "deepslate_iron_ore": ("deepslate", (236, 178, 140)),
    "deepslate_copper_ore": ("deepslate", (240, 120, 60)),
    "deepslate_gold_ore": ("deepslate", (255, 220, 40)),
    "deepslate_redstone_ore": ("deepslate", (255, 30, 30)),
    "deepslate_emerald_ore": ("deepslate", (30, 240, 90)),
    "deepslate_lapis_ore": ("deepslate", (40, 90, 255)),
    "deepslate_diamond_ore": ("deepslate", (60, 240, 240)),
    "nether_gold_ore": ("netherrack", (255, 220, 40)),
    "nether_quartz_ore": ("netherrack", (255, 255, 255)),
    "ancient_debris_side": (None, (200, 120, 255)),
    "ancient_debris_top": (None, (200, 120, 255)),
}


def find_jar() -> str:
    if len(sys.argv) > 1:
        return sys.argv[1]
    hits = glob.glob(str(Path.home() / ".gradle/caches/fabric-loom/*/minecraft-client.jar"))
    if not hits:
        raise SystemExit("Minecraft client jar not found - run gradlew build first or pass its path")
    return sorted(hits)[-1]


def load(jar: zipfile.ZipFile, name: str) -> Image.Image:
    return Image.open(io.BytesIO(jar.read(f"assets/minecraft/textures/block/{name}.png"))).convert("RGBA")


def ore_mask(ore: Image.Image, base: Image.Image | None) -> list:
    w, h = ore.size
    mask = [[False] * w for _ in range(h)]
    for y in range(h):
        for x in range(w):
            r, g, b, _ = ore.getpixel((x, y))
            if base is not None:
                br, bg, bb, _ = base.getpixel((x, y))
                mask[y][x] = abs(r - br) + abs(g - bg) + abs(b - bb) > 40
            else:  # ancient debris: no plain base texture, pick the bright/saturated swirls
                mask[y][x] = max(r, g, b) - min(r, g, b) > 28 or (r + g + b) > 330
    return mask


def outline(ore: Image.Image, mask: list, color) -> Image.Image:
    w, h = ore.size
    out = ore.copy()
    for y in range(h):
        for x in range(w):
            if mask[y][x]:
                continue
            near = any(0 <= x + dx < w and 0 <= y + dy < h and mask[y + dy][x + dx]
                       for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            if near:
                out.putpixel((x, y), color + (255,))
    return out


def frame(img: Image.Image, color) -> Image.Image:
    out = img.copy()
    w, h = out.size
    for i in range(w):
        for x, y in ((i, 0), (i, h - 1), (0, i), (w - 1, i)):
            out.putpixel((x, y), color + (255,))
    return out


def main() -> None:
    TEX.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(find_jar()) as jar:
        version = json.loads(jar.read("version.json"))
        fmt = version["pack_version"]["resource_major"]
        bases = {n: load(jar, n) for n in ("stone", "deepslate", "netherrack")}
        for name, (base, color) in ORES.items():
            ore = load(jar, name)
            if base is None:
                out = frame(ore, color)  # ancient debris has no plain base texture to compare with
            else:
                out = outline(ore, ore_mask(ore, bases[base]), color)
            out.save(TEX / f"{name}.png")
    meta = {"pack": {"description": "Lmg Client - bright outlines around ores", "min_format": fmt, "max_format": fmt}}
    (OUT / "pack.mcmeta").write_text(json.dumps(meta, indent=2) + "\n")
    print(f"Wrote {len(ORES)} ore textures (pack format {fmt})")


if __name__ == "__main__":
    main()
