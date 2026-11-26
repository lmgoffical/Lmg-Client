import { Cosmetic } from "../lib.mjs";

// Capes are just textures: the game's own cape layer draws them with vanilla cloth physics.
// The pictures are drawn by tools/make_capes.py.
const cape = (id, name, texture, description) =>
  new Cosmetic(id, name, "capes", { anchor: "body", tintable: false, capeTexture: texture, description });

export default [
  cape("cape_lmg", "Lmg Cape", "cape.png", "The original - a glowing Star of David over LMG."),
  cape("cape_aurora", "Aurora", "cape_aurora.png", "Northern lights over snowy peaks."),
  cape("cape_royal", "Royal", "cape_royal.png", "Purple velvet, ermine collar and gold trim."),
  cape("cape_void", "Void", "cape_void.png", "A purple galaxy swirling into the dark."),
  cape("cape_sunset", "Sunset", "cape_sunset.png", "Synthwave sun over a neon grid."),
  cape("cape_cherry", "Cherry Blossom", "cape_cherry.png", "Pink blossoms under the moon."),
  cape("cape_ocean", "Moonlit Sea", "cape_ocean.png", "The moon shining on night waves."),
  cape("cape_inferno", "Inferno", "cape_inferno.png", "Flames licking up from the bottom, with floating embers."),
  cape("cape_rainbow", "Rainbow", "cape_rainbow.png", "Bright rainbow stripes with fluffy clouds."),
  cape("cape_forest", "Night Forest", "cape_forest.png", "Pine trees, a crescent moon and fireflies."),
  cape("cape_racer", "Racer", "cape_racer.png", "A checkered flag with a red racing stripe."),
];
