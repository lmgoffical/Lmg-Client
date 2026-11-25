import { Cosmetic, argb, shade, mix, bar } from "../lib.mjs";

// Head space: face is the plane z = -4, eyes sit around y = -4..-3, sides at x = +-4.
const FZ = -4.35;

// flat polygon (fan) in the face plane
function poly(p, pts, z, top, bottom, tint) {
  const cx = pts.reduce((s, q) => s + q[0], 0) / pts.length, cy = pts.reduce((s, q) => s + q[1], 0) / pts.length;
  for (let i = 0; i < pts.length; i++) {
    const a = pts[i], b = pts[(i + 1) % pts.length];
    p.tri([cx, cy, z], [b[0], b[1], z], [a[0], a[1], z], top, bottom, tint);
  }
}
// outline of a polygon as thin bars
function rim(p, pts, z, w, d, col, tint) {
  for (let i = 0; i < pts.length; i++) {
    const a = pts[i], b = pts[(i + 1) % pts.length];
    bar(p, [a[0], a[1], z], [b[0], b[1], z], w, d, col, col, tint);
  }
}
function arms(p, y, col, tint, w = 0.35) {
  for (const s of [-1, 1]) {
    p.box(s * 4.25 - 0.18, y - w / 2, FZ, s * 4.25 + 0.18, y + w / 2, 1.2, col, col, tint);
    p.box(s * 4.25 - 0.18, y - w / 2, 1.2, s * 4.25 + 0.18, y + 1.1, 1.6, col, col, tint);   // curl behind the ear
  }
}

// Aviators: thin gold wire, teardrop lenses fading from dark to the chosen tint.
function aviators() {
  const c = new Cosmetic("glasses_aviator", "Aviators", "glasses", { defaultColor: "#3A2A1A",
    description: "Gold aviators - the colour sets the lens tint." });
  const lens = c.part("lenses", { render: "translucent" });
  const frame = c.part("frame", {});
  const gold = 0xFFE2B851;
  const shape = (s) => {
    const pts = [];
    for (let i = 0; i < 20; i++) {
      const a = Math.PI * 2 * i / 20;
      let x = Math.cos(a) * 1.65, y = Math.sin(a) * 1.2;
      if (y > 0) { y *= 1.45; x *= 1 - y * 0.12; x -= s * y * 0.25; }   // teardrop drop toward the nose
      pts.push([s * 2.05 + x, -4.1 + y]);
    }
    return pts;
  };
  for (const s of [-1, 1]) {
    poly(lens, shape(s), FZ, argb(0.9, 0xffffff), argb(0.55, 0xffffff), 1);
    rim(frame, shape(s), FZ - 0.02, 0.22, 0.3, gold, 0);
  }
  bar(frame, [-0.45, -5.15, FZ], [0.45, -5.15, FZ], 0.22, 0.3, gold, gold, 0);     // double bridge
  bar(frame, [-0.4, -4.3, FZ], [0.4, -4.3, FZ], 0.2, 0.3, gold, gold, 0);
  arms(frame, -4.9, gold, 0, 0.25);
  return c;
}

// Pixel shades: chunky black 8-bit sunglasses with white glints.
function pixelShades() {
  const c = new Cosmetic("glasses_pixel", "Pixel Shades", "glasses", { defaultColor: "#101014",
    description: "Deal with it." });
  const p = c.part("shades", {});
  const k = 0xFFFFFFFF;
  const px = 0.62, z0 = FZ - 0.35, z1 = FZ + 0.05;
  // design grid 13 wide, centred, origin at top-left of the frame
  const grid = [
    "XXXXXXXXXXXXX",
    "XwwXXXXXwwXXX",
    "XwXXXX.XwXXXX",
    ".XXXX...XXXX.",
  ];
  const x0 = -13 * px / 2, y0 = -5.2;
  grid.forEach((row, r) => [...row].forEach((ch, i) => {
    if (ch === ".") return;
    const white = ch === "w";
    const col = white ? 0xFFF4F4F4 : k;
    p.box(x0 + i * px, y0 + r * px, z0, x0 + (i + 1) * px, y0 + (r + 1) * px, z1, col, col, white ? 0 : 1);
  }));
  for (const s of [-1, 1]) p.box(s * 4.25 - 0.2, y0, z1, s * 4.25 + 0.2, y0 + px, 1.0, k, k, 1);
  return c;
}

// 3D glasses: white card frame, red and cyan see-through lenses.
function glasses3d() {
  const c = new Cosmetic("glasses_3d", "3D Glasses", "glasses", { tintable: false, defaultColor: "#FFFFFF",
    description: "Retro cinema red/cyan glasses." });
  const lens = c.part("lenses", { render: "translucent" });
  const f = c.part("frame", {});
  const white = 0xFFF2F2F2;
  for (const s of [-1, 1]) {
    lens.box(s * 2.1 - 1.55, -4.95, FZ - 0.05, s * 2.1 + 1.55, -3.05, FZ + 0.05, argb(0.72, s < 0 ? 0xE0303A : 0x30C8E8), argb(0.72, s < 0 ? 0xB01E2A : 0x1E9CC0), 0);
    f.box(s * 2.1 - 1.85, -5.35, FZ - 0.2, s * 2.1 + 1.85, -4.95, FZ + 0.1, white, white, 0);
    f.box(s * 2.1 - 1.85, -3.05, FZ - 0.2, s * 2.1 + 1.85, -2.7, FZ + 0.1, white, white, 0);
    f.box(s * 3.95 - 0.2, -5.35, FZ - 0.2, s * 3.95 + 0.2, -2.7, FZ + 0.1, white, white, 0);
  }
  f.box(-0.55, -5.35, FZ - 0.2, 0.55, -3.9, FZ + 0.1, white, white, 0);
  arms(f, -5.1, white, 0, 0.4);
  return c;
}

// Cyber visor: a glowing wrap-around band with a brighter scan line that sweeps across.
function cyberVisor() {
  const c = new Cosmetic("glasses_visor", "Cyber Visor", "glasses", { defaultColor: "#FF3B6B",
    description: "Glowing wrap-around visor with a sweeping scan line." });
  const glow = c.part("glow", { render: "emissive" });
  const frame = c.part("frame", {});
  const y0 = -5.1, y1 = -3.0, z = FZ - 0.05;
  const top = argb(0.92, 0xffffff), bot = argb(0.55, 0xffffff);
  glow.quad([-4.1, y0, z, 0, 0], [4.1, y0, z, 1, 0], [4.1, y1, z, 1, 1], [-4.1, y1, z, 0, 1], top, bot, 1);
  for (const s of [-1, 1]) {   // wrap around the sides
    const x = s * 4.4;
    glow.quad([x, y0, -4.1, 0, 0], [x, y0, -1.2, 1, 0], [x, y1 + 0.5, -1.2, 1, 1], [x, y1, -4.1, 0, 1], top, bot, 1);
  }
  const dark = 0xFF16161C;
  frame.box(-4.4, y0 - 0.4, FZ - 0.25, 4.4, y0, FZ + 0.1, dark, dark, 0);
  frame.box(-4.4, y1, FZ - 0.25, 4.4, y1 + 0.35, FZ + 0.1, dark, dark, 0);
  for (const s of [-1, 1]) frame.box(s * 4.35 - 0.2, y0 - 0.4, -4.4, s * 4.35 + 0.2, y0, 0.6, dark, dark, 0);
  // scan line: a thin bright bar that slides left/right
  const scan = c.part("scan", { render: "emissive", anim: [{ type: "bob", axis: "x", amp: 3.2, speed: 0.12, phase: 0 }] });
  scan.quad([-0.25, y0 + 0.1, z - 0.06, 0, 0], [0.25, y0 + 0.1, z - 0.06, 1, 0], [0.25, y1 - 0.1, z - 0.06, 1, 1], [-0.25, y1 - 0.1, z - 0.06, 0, 1], 0xFFFFFFFF, 0xFFFFFFFF, 0);
  return c;
}

export default [aviators(), pixelShades(), glasses3d(), cyberVisor()];
