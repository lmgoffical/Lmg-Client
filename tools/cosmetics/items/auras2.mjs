import { Cosmetic, argb, ring, spin, bob, fan, heartPts, dquad, wave } from "../lib.mjs";

// Heart orbit: four glowing hearts circling around you, each turning on its own.
function hearts() {
  const c = new Cosmetic("aura_hearts", "Heart Orbit", "auras", { anchor: "body", defaultColor: "#FF5C8A",
    description: "Four glowing hearts float around you." });
  const orbit = c.part("orbit", { pivot: [0, 5, 0], rot: [0, 0, -6], anim: [spin("y", 2.0)] });
  for (let i = 0; i < 4; i++) {
    const a = Math.PI * 2 * i / 4, R = 10;
    const h = c.part("heart" + i, { parent: c.index(orbit), pivot: [Math.cos(a) * R, 0, Math.sin(a) * R], render: "emissive",
      anim: [spin("y", -5), bob("y", 1.3, 0.08, i * 1.6)] });
    const pts = heartPts(0, 0, 4.4, 30).map(p => [p[0], p[1], 0]);
    fan(h, pts, [0, 0.2, 0], argb(1, 0xffffff), argb(0.95, 0xffffff), 1);
    const g = c.part("glow" + i, { parent: c.index(h), render: "emissive" });
    const big = heartPts(0, 0, 7.0, 30).map(p => [p[0], p[1], 0.06]);
    fan(g, big, [0, 0.2, 0.06], argb(0.35, 0xffffff), argb(0, 0xffffff), 1);
  }
  return c;
}

// Planet rings: two tilted rings spinning round you, with a little moon riding the inner one.
function rings() {
  const c = new Cosmetic("aura_rings", "Planet Rings", "auras", { anchor: "body", defaultColor: "#9AD7FF",
    description: "Two tilted rings spin around you, with a tiny moon riding along." });
  const tilt1 = c.part("tilt1", { pivot: [0, 7, 0], rot: [18, 0, 12] });
  const r1 = c.part("ring1", { parent: c.index(tilt1), render: "emissive", anim: [spin("y", 1.4)] });
  ring(r1, { r0: 9.0, r1: 10.6, y0: 0, y1: 0, segs: 48, top: argb(0.85, 0xffffff), bottom: argb(0.55, 0xffffff), tint: 1 });
  ring(r1, { r0: 10.6, r1: 9.0, y0: 0, y1: 0, segs: 48, top: argb(0.55, 0xffffff), bottom: argb(0.85, 0xffffff), tint: 1 });
  r1.box(9.35, -0.6, -0.6, 10.55, 0.6, 0.6, 0xFFFFFFFF, 0xFFFFFFFF, 0);    // moon
  const tilt2 = c.part("tilt2", { pivot: [0, 7, 0], rot: [-14, 0, -22] });
  const r2 = c.part("ring2", { parent: c.index(tilt2), render: "emissive", anim: [spin("y", -0.9)] });
  ring(r2, { r0: 11.6, r1: 12.4, y0: 0, y1: 0, segs: 48, top: argb(0.55, 0xffffff), tint: 1 });
  ring(r2, { r0: 12.4, r1: 11.6, y0: 0, y1: 0, segs: 48, top: argb(0.55, 0xffffff), tint: 1 });
  for (let i = 0; i < 6; i++) {   // dust specks on the outer ring
    const a = Math.PI * 2 * i / 6 + 0.3, x = Math.cos(a) * 12, z = Math.sin(a) * 12;
    r2.box(x - 0.25, -0.25, z - 0.25, x + 0.25, 0.25, z + 0.25, 0xFFFFFFFF, 0xFFFFFFFF, 0);
  }
  return c;
}

// Leaf swirl: leaves spiralling up around you like a little whirlwind.
function leaves() {
  const c = new Cosmetic("aura_leaves", "Leaf Swirl", "auras", { anchor: "body", defaultColor: "#6CCB5F",
    description: "Leaves spiral round you like a tiny whirlwind." });
  const orbit = c.part("swirl", { pivot: [0, 6, 0], anim: [spin("y", 3.0)] });
  const n = 14;
  for (let i = 0; i < n; i++) {
    const a = Math.PI * 2 * i / n * 2.0, R = 8 + (i % 3) * 1.4, y = 16 - i * 2.2;
    const l = c.part("leaf" + i, { parent: c.index(orbit), pivot: [Math.cos(a) * R, y, Math.sin(a) * R], rot: [30, -a * 57.3, 20],
      anim: [bob("y", 1.4, 0.07, i * 0.7), wave("x", 25, 0.12, { phase: i })] });
    const col = i % 3 === 1 ? 0xFFE3C14A : 0xFFFFFFFF, tint = i % 3 === 1 ? 0 : 1;
    const dark = i % 3 === 1 ? 0xFFC0962E : argb(1, 0xc8c8c8);
    // a leaf: pointed oval with a stem line down the middle
    const L = 3.6, W = 1.5;
    dquad(l, [0, -L, 0, 0, 0], [W, -L * 0.35, 0, 1, 0], [0, L * 0.6, 0, 1, 1], [-W, -L * 0.35, 0, 0, 1], col, dark, tint);
    l.box(-0.08, -L * 0.8, -0.06, 0.08, L * 0.9, 0.06, dark, dark, tint);
  }
  return c;
}

// Spirit flames: soft flickering flames that circle your feet. (Root space: y = 24 is the ground.)
function flames() {
  const c = new Cosmetic("aura_flames", "Spirit Flames", "auras", { anchor: "root", defaultColor: "#6BB8FF",
    description: "Flickering ghost-fire licks around your feet." });
  const base = c.part("base", { pivot: [0, 23.8, 0], anim: [spin("y", 1.1)] });
  const n = 10;
  for (let i = 0; i < n; i++) {
    const a = Math.PI * 2 * i / n, R = 7.5;
    const f = c.part("flame" + i, { parent: c.index(base), pivot: [Math.cos(a) * R, 0, Math.sin(a) * R], rot: [0, -a * 57.3 + 90, 0], render: "emissive",
      anim: [wave("z", 10, 0.3, { phase: i * 1.7 }), { type: "bob", axis: "y", amp: 0.6, speed: 0.35, phase: i }] });
    const h = 7 + (i % 3) * 2.2, w = 2.0 + (i % 2) * 0.7;
    const core = argb(0.95, 0xffffff), edge = argb(0, 0xffffff), midc = argb(0.6, 0xffffff);
    // flame: a tall diamond, bright at the base, fading to nothing at the tip (both faces + crossed)
    for (const rotq of [0, 1]) {
      const P = (x, y) => rotq ? [0, y, x] : [x, y, 0];
      dquad(f, [...P(0, -h), 0, 0], [...P(w, -h * 0.35), 1, 0], [...P(0, 0), 1, 1], [...P(-w, -h * 0.35), 0, 1], edge, core, 1);
      dquad(f, [...P(0, -h * 0.6), 0, 0], [...P(w * 0.5, -h * 0.25), 1, 0], [...P(0, 0), 1, 1], [...P(-w * 0.5, -h * 0.25), 0, 1], midc, 0xFFFFFFFF, 0);
    }
  }
  return c;
}

export default [hearts(), rings(), leaves(), flames()];
