import { Cosmetic, argb, shade, fan, outline, heartPts, starPts, ellipsePts, bar } from "../lib.mjs";

// Head space: face is the plane z = -4, eyes sit around y = -4..-3, sides at x = +-4.
const FZ = -4.35;

function arms(p, y, col, tint, w = 0.35) {
  for (const s of [-1, 1]) {
    p.box(s * 4.25 - 0.18, y - w / 2, FZ, s * 4.25 + 0.18, y + w / 2, 1.2, col, col, tint);
    p.box(s * 4.25 - 0.18, y - w / 2, 1.2, s * 4.25 + 0.18, y + 1.1, 1.6, col, col, tint);
  }
}
const at = (pts, z) => pts.map(q => [q[0], q[1], z]);
const mid = (pts, z) => [pts.reduce((s, q) => s + q[0], 0) / pts.length, pts.reduce((s, q) => s + q[1], 0) / pts.length, z];

// Heart shades: heart-shaped see-through lenses in a chunky frame.
function hearts() {
  const c = new Cosmetic("glasses_heart", "Heart Shades", "glasses", { defaultColor: "#FF3B7A",
    description: "Heart-shaped shades. The colour sets the frame and the lens tint." });
  const lens = c.part("lenses", { render: "translucent" });
  const frame = c.part("frame", {});
  for (const s of [-1, 1]) {
    const pts = heartPts(s * 2.1, -4.1, 3.4);
    fan(lens, at(pts, FZ), [s * 2.1, -4.2, FZ], argb(0.55, shade(0xffffff, 0.7)), argb(0.8, shade(0xffffff, 0.55)), 1);
    outline(frame, pts, FZ - 0.02, 0.42, 0.42, 0xFFFFFFFF, 1);
  }
  bar(frame, [-0.6, -4.9, FZ], [0.6, -4.9, FZ], 0.35, 0.4, 0xFFFFFFFF, 0xFFFFFFFF, 1);
  arms(frame, -5.0, 0xFFFFFFFF, 1);
  return c;
}

// Round specs: thin wire circles with clear glass. The colour sets the wire.
function round() {
  const c = new Cosmetic("glasses_round", "Round Specs", "glasses", { defaultColor: "#2A2A2E",
    description: "Thin round wire glasses with clear lenses. Smart look." });
  const lens = c.part("lenses", { render: "translucent" });
  const frame = c.part("frame", {});
  for (const s of [-1, 1]) {
    const pts = ellipsePts(s * 2.05, -4.05, 1.55, 1.55, 0, 22);
    fan(lens, at(pts, FZ), [s * 2.05, -4.05, FZ], argb(0.18, 0xdff2ff), argb(0.32, 0xdff2ff), 0);
    outline(frame, pts, FZ - 0.02, 0.2, 0.24, 0xFFFFFFFF, 1);
  }
  // little arched bridge
  bar(frame, [-0.55, -4.45, FZ], [0, -4.8, FZ], 0.2, 0.24, 0xFFFFFFFF, 0xFFFFFFFF, 1);
  bar(frame, [0, -4.8, FZ], [0.55, -4.45, FZ], 0.2, 0.24, 0xFFFFFFFF, 0xFFFFFFFF, 1);
  arms(frame, -4.35, 0xFFFFFFFF, 1, 0.2);
  return c;
}

// Star shades: glowing star lenses that light up in the dark.
function stars() {
  const c = new Cosmetic("glasses_star", "Star Shades", "glasses", { defaultColor: "#FFD23F",
    description: "Glowing star-shaped lenses. They light up at night." });
  const lens = c.part("lenses", { render: "emissive" });
  const frame = c.part("frame", {});
  const white = 0xFFF4F4F4;
  for (const s of [-1, 1]) {
    const pts = starPts(s * 2.15, -4.05, 2.0, 0.95, 5, s * 0.12);
    fan(lens, at(pts, FZ), [s * 2.15, -4.05, FZ], argb(0.95, 0xffffff), argb(0.75, 0xffffff), 1);
    outline(frame, pts, FZ - 0.03, 0.22, 0.3, white, 0);
  }
  bar(frame, [-0.5, -4.35, FZ], [0.5, -4.35, FZ], 0.25, 0.3, white, white, 0);
  arms(frame, -4.6, white, 0, 0.25);
  return c;
}

// Ski goggles: a wide strap round the head and a big mirrored lens.
function goggles() {
  const c = new Cosmetic("glasses_goggles", "Snow Goggles", "glasses", { defaultColor: "#FF8A3D",
    description: "Big mirrored snow goggles. The colour sets the mirror." });
  const lensC = c.part("lens", { render: "translucent" });
  const frame = c.part("frame", {});
  const dark = 0xFF1C1C22, strap = 0xFF2E2E3A;
  const pts = [];
  for (let i = 0; i < 28; i++) {
    const a = Math.PI * 2 * i / 28;
    let x = Math.cos(a) * 3.9, y = Math.sin(a) * 1.45;
    if (y > 0 && Math.abs(x) < 1.1) y -= (1.1 - Math.abs(x)) * 0.7;   // nose notch
    pts.push([x, -4.25 + y]);
  }
  fan(lensC, at(pts, FZ - 0.2), mid(pts, FZ - 0.2), argb(0.9, 0xffffff), argb(0.75, shade(0xffffff, 0.6)), 1);
  outline(frame, pts, FZ - 0.15, 0.55, 0.7, dark, 0);
  // glare stripe
  const glare = c.part("glare", { render: "translucent" });
  fan(glare, [[-2.6, -5.3, FZ - 0.26], [-1.6, -5.3, FZ - 0.26], [-2.4, -3.3, FZ - 0.26], [-3.2, -3.5, FZ - 0.26]], [-2.4, -4.4, FZ - 0.26], argb(0.5, 0xffffff), argb(0.3, 0xffffff), 0);
  // strap all the way round
  frame.box(-4.4, -5.2, FZ + 0.1, -4.15, -3.4, 4.4, strap, strap, 0);
  frame.box(4.15, -5.2, FZ + 0.1, 4.4, -3.4, 4.4, strap, strap, 0);
  frame.box(-4.4, -5.2, 4.15, 4.4, -3.4, 4.4, strap, strap, 0);
  return c;
}

// Monocle: one gold-rimmed lens and a chain hanging down to your collar.
function monocle() {
  const c = new Cosmetic("glasses_monocle", "Monocle", "glasses", { tintable: false, defaultColor: "#E2B851",
    description: "A gold monocle on a little chain. Quite right." });
  const lens = c.part("lens", { render: "translucent" });
  const frame = c.part("frame", {});
  const gold = 0xFFE2B851, dk = 0xFFB98A2A;
  const pts = ellipsePts(-2.05, -4.1, 1.7, 1.7, 0, 22);
  fan(lens, at(pts, FZ), [-2.05, -4.1, FZ], argb(0.2, 0xfff6dc), argb(0.35, 0xfff6dc), 0);
  outline(frame, pts, FZ - 0.03, 0.32, 0.36, gold, 0);
  // chain: little links swinging gently
  const chain = c.part("chain", { pivot: [-3.4, -3.1, FZ], anim: [{ type: "wave", axis: "z", amp: 4, speed: 0.07, phase: 0, movingAmp: 6, movingSpeed: 0.1, offset: 0, movingOffset: 0 }] });
  for (let i = 0; i < 7; i++) {
    const y = i * 0.7, x = -Math.sin(i * 0.35) * 0.6;
    chain.box(x - 0.12, y, -0.12, x + 0.12, y + 0.5, 0.12, i % 2 ? gold : dk, i % 2 ? gold : dk, 0);
  }
  return c;
}

export default [hearts(), round(), stars(), goggles(), monocle()];
