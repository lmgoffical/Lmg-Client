// Renders a 128x128 thumbnail for every cosmetic into textures/cosmetic/icons/<id>.png
// (used by the in-game Cosmetics screen). Needs Playwright and a static server on the repo root:
//   python -m http.server 8766 --bind 127.0.0.1      then      node tools/cosmetic-preview/icons.js
const { chromium } = require("playwright");
const fs = require("fs");
const path = require("path");

const ROOT = path.resolve(__dirname, "../..");
const ASSETS = path.join(ROOT, "src/main/resources/assets/lmgclient");
const OUT = path.join(ASSETS, "textures/cosmetic/icons");
const index = JSON.parse(fs.readFileSync(path.join(ASSETS, "cosmetics/index.json"), "utf8"));

const CAMERA = {
  capes: "view=back&player=0&dist=2.05&aty=1.0",
  wings: "view=back&player=0&dist=4.3&aty=1.2",
  hats: "view=threefront&player=head&dist=2.2&aty=1.82",
  glasses: "view=threefront&player=head&dist=1.75&aty=1.72",
  ears: "view=threefront&player=head&dist=2.0&aty=1.86",
  auras: "view=threefront&player=1&dist=4.0&aty=1.15",
  tails: "view=three&player=1&dist=3.3&aty=0.72",
};
const OVERRIDE = { aura_halo: "view=threefront&player=head&dist=2.3&aty=1.9", hat_top: "view=threefront&player=head&dist=2.5&aty=1.9" };

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const browser = await chromium.launch({ args: ["--use-gl=swiftshader", "--enable-unsafe-swiftshader"],
    proxy: process.env.HTTPS_PROXY ? { server: process.env.HTTPS_PROXY, bypass: "127.0.0.1,localhost" } : undefined });
  const page = await browser.newPage({ viewport: { width: 128, height: 128 }, deviceScaleFactor: 2 });
  const only = process.argv[2] ? new Set(process.argv[2].split(",")) : null;   // optional: just these ids
  for (const item of index.items.filter(i => !only || only.has(i.id))) {
    const json = JSON.parse(fs.readFileSync(path.join(ASSETS, "cosmetics", item.id + ".json"), "utf8"));
    let q = (OVERRIDE[item.id] || CAMERA[item.category] || CAMERA.hats) + "&bg=none&t=30";
    q += json.capeTexture ? "&cape=" + json.capeTexture : "&cape=0&items=" + item.id;
    await page.goto("http://127.0.0.1:8766/tools/cosmetic-preview/index.html?" + q);
    await page.waitForFunction(() => window.READY === true, null, { timeout: 30000 });
    await page.screenshot({ path: path.join(OUT, item.id + ".png"), omitBackground: true });
    console.log("icon", item.id);
  }
  await browser.close();
})();
