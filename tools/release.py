"""Publishes a new Lmg Client build to the download/ folder.

Run from the repo root after `gradlew build`:

    python tools/release.py

It copies build/libs/lmg-client-<version>.jar into download/, removes older
lmg-client jars there, and rewrites download/manifest.json, which the
Windows installer and the in-game update check read.
"""
import hashlib
import json
import re
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def prop(name: str) -> str:
    text = (ROOT / "gradle.properties").read_text()
    match = re.search(rf"^{re.escape(name)}=(.+)$", text, re.MULTILINE)
    if not match:
        raise SystemExit(f"{name} not found in gradle.properties")
    return match.group(1).strip()


def resolve_modpack(minecraft: str) -> list:
    """Looks up the pinned Modrinth versions in tools/modpack.json (file name, URL, SHA-1)."""
    pack = json.loads((ROOT / "tools" / "modpack.json").read_text())
    mods = []
    for mod in pack["mods"]:
        url = f"https://api.modrinth.com/v2/version/{mod['modrinthVersion']}"
        request = urllib.request.Request(url, headers={"User-Agent": "lmg-client-release-tool"})
        with urllib.request.urlopen(request, timeout=60) as response:
            version = json.load(response)
        if minecraft not in version["game_versions"]:
            raise SystemExit(f"{mod['name']} {version['version_number']} is not for Minecraft {minecraft}")
        file = next((f for f in version["files"] if f["primary"]), version["files"][0])
        mods.append({
            "name": mod["name"],
            "pattern": mod["pattern"],
            "file": file["filename"],
            "url": file["url"],
            "sha1": file["hashes"]["sha1"],
        })
        print(f"  {mod['name']}: {file['filename']}")
    return mods


def main() -> None:
    version = prop("version")
    minecraft = prop("minecraft_version")
    jar_name = f"lmg-client-{version}.jar"
    built = ROOT / "build" / "libs" / jar_name
    if not built.exists():
        raise SystemExit(f"{built} not found - run gradlew build first")

    download = ROOT / "download"
    download.mkdir(exist_ok=True)
    for old in download.glob("lmg-client-*.jar"):
        if old.name != jar_name:
            old.unlink()
    data = built.read_bytes()
    (download / jar_name).write_bytes(data)

    manifest = {
        "version": version,
        "minecraft": minecraft,
        "lmgClient": {"file": jar_name, "sha1": hashlib.sha1(data).hexdigest()},
        "mods": resolve_modpack(minecraft),
    }
    (download / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print(f"Released {jar_name} for Minecraft {minecraft}")


if __name__ == "__main__":
    main()
