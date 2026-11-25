// Builds every cosmetic in items/ into src/main/resources/assets/lmgclient/cosmetics/<id>.json
// and writes index.json (the catalog the mod and preview read).   Run: node tools/cosmetics/build.mjs
import { readdirSync, writeFileSync, mkdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const out = join(here, "../../src/main/resources/assets/lmgclient/cosmetics");
mkdirSync(out, { recursive: true });
const catalog = [];
for (const file of readdirSync(join(here, "items")).filter(f => f.endsWith(".mjs")).sort()) {
  const mod = await import(pathToFileURL(join(here, "items", file)).href);
  for (const cosmetic of [].concat(mod.default)) {
    const json = cosmetic.toJSON();
    writeFileSync(join(out, json.id + ".json"), JSON.stringify(json));
    const quads = json.parts.reduce((n, p) => n + p.quads.length, 0);
    catalog.push({ id: json.id, name: json.name, category: json.category, description: json.description, tintable: json.tintable, defaultColor: json.defaultColor });
    console.log(`${json.id.padEnd(18)} ${json.parts.length} parts, ${quads} quads`);
  }
}
writeFileSync(join(out, "index.json"), JSON.stringify({ format: 1, items: catalog }, null, 1));
console.log(`${catalog.length} cosmetics`);
