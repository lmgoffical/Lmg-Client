# Lmg Client

A Fabric mod for Minecraft Java **26.3** that gives the game a custom look:

- **Splash screen**: a glowing Star of David with "Press to enter". Clicking spins the star, throws out sparkles and fades into the menu.
- **Main menu**: text-only buttons with a soft hover bar, your 3D skin that slowly turns (drag it to spin it), and drifting light curtains with star sparkles.
- **Themed menus**: the server list, world list and options get the same dark background (outside of a world).
- **HUD bar**: a smooth, animated bar showing your name, server, FPS, ping, clock and (optionally) coordinates.
- **Lmg menu**: press **Right Shift** anywhere (or main menu → **Client**). A compact panel with a sidebar; drag its title bar to move it (double-click the title bar to re-centre). **Edit HUD** lets you drag the HUD bar, target info and potion list anywhere (right-click one to reset it). Categories:
  - **Configs**: save as many named setups as you like (stored in `config/lmgclient-profiles/`), load or delete them, and share them as codes (`LMG1:...`) - copy a code, send it to a friend, they press *Import from clipboard*
  - **Style**: a dropdown to pick the menu look - Lmg Client (original), Skeet or Neverlose - plus whether the menu uses your accent color
  - **Theme**: accent color, background style, animation speed
  - **World**: sky theme, time of day, fog, clouds, stars, rain & snow, animated aurora
  - **Avatar**: Lmg Client cape, wings, halo, crown, cat ears, China hat, energy shield (only you see them)
  - **Effects**: walking trails and ambient particles (fireflies, petals, stardust, snow, ash, spores)
  - **Target**: glowing outline on what you aim at (up to 32 blocks, line of sight only), target info panel (health, absorption, armor, held items, known effects), target reticle, hit & kill effects
  - **PvP**: custom block overlay, fullbright, ore edges (built-in resource pack, made by `tools/make_ore_outlines.py`), ore glow in the dark (only ores you can see), potion effects HUD, low fire, no hurt-camera shake, smaller / lower held items, Lmg totem (Star of David, built-in pack made by `tools/make_totem.py`, gold totem particles)
  - **Menus**: splash screen, themed menus, light curtains, sparkle amount, skin spin
  - **HUD**: on/off, position, size, background opacity, and which info to show

  Switches turn things on/off; for options click `<` / `>` (or left/right-click the row). Settings are saved in `.minecraft/config/lmgclient.json`. The key can be changed in Minecraft's **Controls** settings.

## Installing it (Windows), the easy way

1. Open the Minecraft Launcher once and sign in, then close it.
2. Download [`installer/Install-Lmg-Client.bat`](https://github.com/lmgoffical/Lmg-Client/raw/claude/modest-cray-bg98zr/installer/Install-Lmg-Client.bat) and double-click it.
   If Windows shows "Windows protected your PC", click **More info → Run anyway**.
3. Open the Minecraft Launcher, pick **Lmg Client** next to the Play button, and press Play.

The installer sets up Fabric and downloads Lmg Client, Fabric API, **Sodium** (FPS), **Lithium** (smoother ticks) and **Iris** (shaders). The mod versions are pinned to a set known to work together. Everything goes into its own folder, `%appdata%\.lmgclient`, so it never clashes with other mods. Your worlds and resource packs are shared with normal Minecraft.

**To update**, run `Install-Lmg-Client.bat` again. It always fetches the newest version and removes old files. The main menu tells you when an update is out.

### Manual install

1. Install **Fabric Loader** for Minecraft 26.3 from <https://fabricmc.net/use/installer/>.
2. Download the jars listed in [`download/manifest.json`](download/manifest.json): `lmg-client-*.jar` from the [`download`](download) folder, plus the other mods from the URLs in the manifest.
3. Put them into `%appdata%\.minecraft\mods`, then launch the **fabric-loader-26.3** profile.

## Releasing a new version

1. Bump `version` in `gradle.properties`.
2. `gradlew build`
3. `python tools/release.py`: copies the jar to `download/` and rewrites `download/manifest.json`, including the pinned mods from `tools/modpack.json`.
4. Commit and push. Players get it by rerunning the installer.

## Building it yourself (Windows)

You need **JDK 25**, for example from <https://adoptium.net/temurin/releases/?version=25>.

```bat
gradlew.bat build
```

The jar ends up in `build\libs\`.

To test in a development copy of the game without touching your real install:

```bat
gradlew.bat runClient
```

### Using IntelliJ IDEA (recommended)

1. Install IntelliJ IDEA Community Edition.
2. **File → Open** and pick this folder. Let Gradle finish importing.
3. Set the project SDK to JDK 25 (**File → Project Structure**).
4. Open the Gradle panel → `Tasks → fabric → runClient`.

## Where things live

| What | File |
|---|---|
| Client name | `src/main/java/dev/lmg/client/LmgClient.java` (`CLIENT_NAME`) |
| Splash screen | `src/main/java/dev/lmg/client/screen/SplashScreen.java` |
| Main menu | `src/main/java/dev/lmg/client/screen/LmgTitleScreen.java` |
| Menu button style | `src/main/java/dev/lmg/client/screen/MenuTextButton.java` |
| Background, curtains | `src/main/java/dev/lmg/client/render/MenuBackground.java` |
| Sparkle particles | `src/main/java/dev/lmg/client/render/Sparkles.java` |
| HUD bar | `src/main/java/dev/lmg/client/hud/HudPill.java` |
| Settings | `src/main/java/dev/lmg/client/LmgConfig.java`, `screen/LmgSettingsScreen.java` |
| Logo and sparkle images | `src/main/resources/assets/lmgclient/textures/gui/` (generated by `tools/make_textures.py`) |

## Roadmap

- Restyled server list rows, hotbar and nametags
- Draggable HUD position
- Custom font
- Glass-blur client panel
- Custom sky
- Kill effects
- Modpack / launcher packaging
