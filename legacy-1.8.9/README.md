# Lmg Client - 1.8.9 PvP edition (Forge)

A smaller Lmg Client for Minecraft 1.8.9 on Forge 11.15.1.2318: the Right Shift menu (tile home + pages),
a draggable HUD (FPS, CPS, ping, coordinates, clock, sprint status, keystrokes, armour, potions), zoom with
your own amount and speed, a custom crosshair, toggle sprint, fullbright, the Lmg capes, halo / China hat /
cat ears, and the Lmg title screen. No mixins - only Forge events - so it sits happily next to OptiFine
and other 1.8.9 mods.

## Build

Needs Java 17 to run Gradle and Java 8 for the game (Gradle downloads Java 8 by itself if it's missing).

    ./gradlew build          # -> build/libs/lmg-client-1.8.9-<version>.jar  (the one to ship)

`tools/release.py` in the main project copies the newest jar into `download/` and lists it in the manifest.

## Run it

    ./gradlew runClient      # once, so loom writes .gradle/loom-cache/launch.cfg
    ./gradlew runLegacy      # starts 1.8.9 through LaunchWrapper + FMLTweaker

`LMG_TEST=1 ./gradlew runLegacy` walks through every screen and feature, saves `run/screenshots/lmg-*.png`
and quits (see `TestDriver`).
