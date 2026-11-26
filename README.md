# Lmg Client

A Fabric mod for Minecraft Java **26.3** that gives the game a custom look:

- **Splash screen**: a glowing Star of David with "Press to enter". Clicking spins the star, throws out sparkles and fades into the menu.
- **Main menu**: text-only buttons with a soft hover bar, your 3D skin that slowly turns (drag it to spin it), and drifting light curtains with star sparkles.
- **Themed menus**: the server list, world list and options get the same dark background (outside of a world).
- **HUD bar**: a smooth, animated bar showing your name, server, FPS, ping, clock and (optionally) coordinates.
- **Lmg menu**: press **Right Shift** anywhere (or main menu → **Client**). A compact panel with a sidebar; drag its title bar to move it (double-click the title bar to re-centre). **Edit HUD** lets you drag the HUD bar, target info and potion list anywhere (right-click one to reset it). Categories:
  - **Configs**: save as many named setups as you like (stored in `config/lmgclient-profiles/`), load or delete them, and share them: **Share** gives a short code like `LMG-ABC234` (stored online, see `supabase/`), or copy a long offline code (`LMG1:...`). Friends type or paste either kind and press *Import*
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

## Website

Community site with accounts, a forum, announcements and a config gallery: <https://lmgoffical.github.io/Lmg-Client/> (source in `docs/`).

## Installing it (Windows), the easy way

1. Open the Minecraft Launcher once and sign in, then close it.
2. Download [**Lmg-Client-Installer.zip**](https://github.com/lmgoffical/Lmg-Client/raw/claude/modest-cray-bg98zr/download/Lmg-Client-Installer.zip), open it and double-click **Lmg Client.exe**.
   If Windows shows "Windows protected your PC", click **More info → Run anyway**.
3. The app puts itself in `%LOCALAPPDATA%\Programs\Lmg Client` with Desktop and Start menu shortcuts. Press **Play**: the first time it installs everything, then the Minecraft Launcher opens with **Lmg Client** picked. Press Play there.

The app installs Fabric, Lmg Client, Fabric API, **Sodium** (FPS), **Lithium** (smoother ticks) and **Iris** (shaders) at versions pinned to work together, checks every download against its SHA-1, and keeps everything in its own folder, `%appdata%\.lmgclient`, so it never clashes with other mods. Worlds and resource packs are shared with normal Minecraft.

**What's in the app:** your Minecraft account and skin face (name and ID read from the Minecraft Launcher's account file, skin from Mojang), a Play button that shows *Update & Play* when there's an update and *Playing* while the game runs, download sizes while installing, a heads-up when Minecraft crashed with a link to the report, What's new, a **Mods** tab (switch mods on/off, with dependency checks), and **Settings** (memory slider, shaders on/off, close on launch, repair, remove the app). It remembers where you put the window. Enter = Play, Esc = Home, Ctrl+1/2/3 = tabs.

**Updates are automatic:** each time you open the app it updates Lmg Client and the mods, and updates itself (the manifest lists the newest app with its SHA-1). It never signs you in or sees your password: Mojang's launcher does that.

The old `installer/Install-Lmg-Client.bat` still works and does the same install.

### The app's source

`launcher/` is the app (C#, .NET Framework 4.8, so nothing to install on Windows 10/11). `launcher/build.sh` builds it into `download/Lmg-Client.exe` (needs the .NET SDK), and `tools/release.py` puts its version and SHA-1 in the manifest and rebuilds the zip. Bump `launcher/VERSION` to ship an app update.

### Manual install

1. Install **Fabric Loader** for Minecraft 26.3 from <https://fabricmc.net/use/installer/>.
2. Download the jars listed in [`download/manifest.json`](download/manifest.json): `lmg-client-*.jar` from the [`download`](download) folder, plus the other mods from the URLs in the manifest.
3. Put them into `%appdata%\.minecraft\mods`, then launch the **fabric-loader-26.3** profile.

## Pets, mods and Minecraft settings

- **Pets:** Cosmetics → Pets (or Right Shift → Avatar → Pets). Up to three at once. Pick an animal (cat, dog, fox, parrot, axolotl, bee, frog, bunny, sheep, panda, allay, copper golem and more) or a **mini player**: your own skin (it wears your cosmetics too) or anyone's, by username. Choose where it goes (follow, left/right shoulder, on your head), its size, baby or grown-up, its look (cat and dog breeds, parrot colours...) and a collar colour, and give it a name. Pets only exist on players' screens: nothing is spawned or sent to the Minecraft server.
- **Seeing each other's pets and cosmetics:** switch on **Share pets & cosmetics** (Pets screen or Tools tab, off by default). Your pets, equipped Lmg cosmetics and cape go into the `player_avatars` table, and Lmg players near you download theirs. To prove the account is yours, the game does Mojang's session "join" with a random id, and the `avatar` edge function (`supabase/functions/avatar`) asks Mojang `hasJoined`; your login token only goes to Mojang. Anyone can read the table; only that function can write it. Switching sharing off deletes your entry. Imported Blockbench models and custom skin files are never shared.
- **Mods:** main menu → Mods (or Tools → Mods). Lists everything in your mods folder with its version and description, marks the Lmg pack, and lets you switch mods on or off. Changes apply the next time you start the game. On Windows a running mod's file is locked, so switching it off finishes right after the game closes.
- **Minecraft settings:** with "Themed vanilla menus" on, Minecraft's own buttons, sliders and lists use the Lmg look (following your menu style), and Options opens a hub with FOV, render distance, brightness and volume up top and a card for each settings page. "Classic options" opens the original screen.

## FPS

The FPS tab collects everything that affects performance:

- **FPS boost** turns down Minecraft's heaviest settings (entity shadows, biome blend, particles, improved transparency, render distance 12, simulation distance 8, weather radius 5). Your old values are saved and come back when you switch it off.
- **Trail** only spawns in third person or freecam (you can't see it in first person). Lmg's particles follow Minecraft's *Particles* setting (Minimal = none, Decreased = half).
- **Ore glow** scans a chunk section at a time and skips sections with no ores, and only rescans when you've moved 2 blocks (or every 2 seconds).
- **Minimap** only redraws when you move or turn (and once a second for new map data). *Minimap refresh* and *Map scanning* trade smoothness for speed.
- **Other players' pets and cosmetics** are only drawn within a set distance.

## Cinematic, colours and target highlight

- **Cinematic tab:** *Shaders* switches between Off, **Golden haze** (Complementary Unbound, the warm foggy look with light rays) and **Lighter** (Complementary Reimagined). Packs are downloaded from Modrinth at pinned versions, checked against their SHA-1, and turned on through Iris. *Shader settings* opens Iris's own menu. **Cinematic mode** (F7) hides the HUD, turns on the smooth camera and adds film bars and a vignette. *Golden hour* locks the sun low (client-side), and *Smooth freecam* makes freecam glide.
- **Colour picker:** accent, block overlay, highlight colours and cosmetics use a picker with a drag-the-dot box, a hue slider, hex and quick picks (block overlay and cosmetics can also be rainbow).
- **Target highlight:** the outline and reticle only show within the *Highlight distance* (16 blocks by default), and switch to the *In-reach color* once the target is within your attack reach.

## Keys, maps and tools

- **Keys:** Right Shift → Keys. Click a key, press the new one (Esc clears it). Defaults: Right Shift menu, C zoom (scroll while holding), B fullbright, F6 freecam, M world map. Minimap, waypoint, ore glow, hide cosmetics and HUD bar start unbound.
- **Maps:** the minimap and world map fill in from chunks your game already loaded. Saved in `config/lmgclient-maps/<world>/<dimension>/` with your waypoints. Right-click the world map to drop a waypoint, right-click one to remove it. A "Last death" waypoint is added when you die.
- **Freecam** only works in singleplayer unless you turn on "Freecam on servers" in Tools - some servers ban it.

## Cosmetics

Open them from the main menu (**Cosmetics**) or Right Shift → Avatar → Cosmetics & skins. Only you can see them.

- **Built-in items** are written as small scripts in `tools/cosmetics/items/*.mjs` (capes, wings, hats, glasses, ears, auras). `node tools/cosmetics/build.mjs` turns them into `src/main/resources/assets/lmgclient/cosmetics/*.json`, which the mod draws with `CosmeticRenderer.java`. Cape pictures come from `tools/make_capes.py`, wing/membrane textures from `tools/make_cosmetics.py`.
- **Preview** them in a browser: serve the repo root (`python -m http.server 8766`) and open `tools/cosmetic-preview/index.html?view=back&items=wings_angel,hat_crown&cape=cape_royal.png`. The preview uses the same maths as the game, so what you see there is what you get.
- **Card pictures** for the in-game menu: `node tools/cosmetic-preview/icons.js` (needs Playwright and the server above).
- **Your own skins:** put 64x64 PNGs in `.minecraft/config/lmgclient-skins` (the Lmg install uses `%appdata%\.lmgclient\config`), then pick one in the Skins tab.
- **Your own models:** in Blockbench make a "Java Block/Item" model around the head (the head is the box from 4,0,4 to 12,8,12, face pointing north), export it, and put `name.json` + `name.png` in `config/lmgclient-cosmetics`. Start the name with `body_` to wear it on your body, add `_glow` to make it glow. An example (`devil_horns`) is copied there the first time.

## Releasing a new version

1. Bump `version` in `gradle.properties`.
2. `gradlew build`
3. If the app changed: bump `launcher/VERSION` and run `launcher/build.sh`.
4. `python tools/release.py`: copies the jar to `download/`, rewrites `download/manifest.json` (pinned mods from `tools/modpack.json`, the changelog, the app) and rebuilds the download zip.
5. Commit and push. Players get it the next time they open the app.

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
| Keys, zoom, freecam | `src/main/java/dev/lmg/client/feature/` |
| Minimap, world map, waypoints | `src/main/java/dev/lmg/client/map/`, `hud/MinimapHud.java`, `screen/WorldMapScreen.java` |
| Pets | `src/main/java/dev/lmg/client/pet/`, `screen/PetsScreen.java` |
| Mods screen | `src/main/java/dev/lmg/client/mods/ModList.java`, `screen/ModsScreen.java` |
| Minecraft settings look | `screen/VanillaSkin.java`, `screen/MinecraftSettingsScreen.java`, `mixin/AbstractButtonMixin.java`, `SliderMixin`, `SelectionListMixin` |
| Cosmetics menu | `src/main/java/dev/lmg/client/screen/CosmeticsScreen.java` |
| Cosmetic engine | `src/main/java/dev/lmg/client/cosmetic/` (`CosmeticRenderer`, `CosmeticRegistry`, `CustomSkins`, `CustomModels`) |
| Logo and sparkle images | `src/main/resources/assets/lmgclient/textures/gui/` (generated by `tools/make_textures.py`) |

## Legal and safety

- **Licence:** [`LICENSE`](LICENSE). Free to use, not for sale, not to be modified or redistributed as copies. Third-party components keep their own licences: see [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).
- **Not affiliated:** Lmg Client isn't made by, connected to or endorsed by Mojang AB or Microsoft. Minecraft is a trademark of Mojang AB.
- **Servers:** server-safe mode is on by default. On multiplayer servers it keeps freecam and ore glow off. Ore edges is a texture pack, so it has to be switched off by hand on strict servers.
- **Data:** the mod has no analytics or tracking. It checks GitHub for updates, and uploads a config only when someone presses Share.
- **Website:** [Terms](https://lmgoffical.github.io/Lmg-Client/#/terms), [Privacy](https://lmgoffical.github.io/Lmg-Client/#/privacy), [Community Rules](https://lmgoffical.github.io/Lmg-Client/#/rules) and [Licences](https://lmgoffical.github.io/Lmg-Client/#/licences) are linked from every page.

### Before a public launch (for the owner)

These can't be done from the code. Check each one before telling lots of people about it.

1. **Get a lawyer to read the Terms, Privacy and Licence.** The website text is a plain-English draft, not legal advice. Pick a governing law and a real contact address for legal requests (it's currently the GitHub issues page).
2. **Check the third-party licences** in `THIRD_PARTY_NOTICES.md` against each project's own page.
3. **Code-sign the installer** so Windows stops warning about it. Until then, the download page explains the warning.
4. **Confirm the account deletion process.** Privacy says requests are handled through a GitHub issue labelled `privacy` within 30 days. Someone needs to actually do that.
5. **Check the Supabase project settings**: email confirmation, password rules, and whether the database region matches the Privacy page.
6. **Decide how the Star of David logo and totem read to a wide audience**, given the name the project started with.

## Roadmap

- Restyled server list rows, hotbar and nametags
- Draggable HUD position
- Custom font
- Glass-blur client panel
- Custom sky
- Kill effects
- Modpack / launcher packaging
