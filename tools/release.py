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
import urllib.error
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
    # what the last release resolved, in case Modrinth is down (the pinned versions don't change)
    previous = {}
    try:
        previous = {m["name"]: m for m in json.loads((ROOT / "download" / "manifest.json").read_text())["mods"]}
    except Exception:
        pass
    mods = []
    for mod in pack["mods"]:
        url = f"https://api.modrinth.com/v2/version/{mod['modrinthVersion']}"
        request = urllib.request.Request(url, headers={"User-Agent": "lmg-client-release-tool"})
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                version = json.load(response)
        except (urllib.error.URLError, TimeoutError) as e:
            old = previous.get(mod["name"])
            if old and f"/versions/{mod['modrinthVersion']}/" in old.get("url", ""):
                mods.append({**old, "pattern": mod["pattern"]})
                print(f"  {mod['name']}: {old['file']} (Modrinth unreachable: {e}; reused the last release's entry)")
                continue
            raise
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


def changelog(limit: int = 6) -> list:
    """The newest entries of the website changelog (docs/app.js), as plain text, for the app's "What's new"."""
    text = (ROOT / "docs" / "app.js").read_text(encoding="utf-8")
    block = text[text.index("const CHANGELOG = ["):]
    block = block[:block.index("];")]
    out = []
    for version, what in re.findall(r'\["([0-9.]+)", "((?:[^"\\]|\\.)*)"\]', block)[:limit]:
        what = re.sub(r"<[^>]+>", "", what).replace("\\'", "'").replace('\\"', '"').replace("&amp;", "&")
        out.append({"version": version, "text": what})
    return out


def launcher(download: Path) -> dict | None:
    """The desktop app (built by launcher/build.sh), so it can update itself."""
    exe = download / "Lmg-Client.exe"
    version_file = ROOT / "launcher" / "VERSION"
    if not exe.exists() or not version_file.exists():
        return None
    return {"version": version_file.read_text().strip(), "file": exe.name, "sha1": hashlib.sha1(exe.read_bytes()).hexdigest()}


def installer_zip(download: Path) -> None:
    """Lmg-Client-Installer.zip: the one file people download. It holds the app."""
    import zipfile
    exe = download / "Lmg-Client.exe"
    if not exe.exists():
        return
    path = download / "Lmg-Client-Installer.zip"
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
        info = zipfile.ZipInfo("Lmg Client.exe", date_time=(2026, 1, 1, 0, 0, 0))
        info.compress_type = zipfile.ZIP_DEFLATED
        z.writestr(info, exe.read_bytes())
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    app_js = ROOT / "docs" / "app.js"
    text = app_js.read_text(encoding="utf-8")
    text = re.sub(r"Its SHA-256 is <code>[0-9a-f]{64}</code>", f"Its SHA-256 is <code>{digest}</code>", text)
    app_js.write_text(text, encoding="utf-8")
    print(f"  installer zip sha256 {digest}")


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
        "changelog": changelog(),
    }
    app = launcher(download)
    if app:
        manifest["launcher"] = app
        print(f"  app {app['version']}")
    installer_zip(download)
    (download / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print(f"Released {jar_name} for Minecraft {minecraft}")


if __name__ == "__main__":
    main()
