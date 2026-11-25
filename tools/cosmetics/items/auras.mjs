import { Cosmetic, argb, ring, spin, bob, wave } from "../lib.mjs";

// Halo: two glowing rings (bright core + soft outer glow), floating and bobbing above the head.
function halo() {
  const c = new Cosmetic("aura_halo", "Halo", "auras", { defaultColor: "#FFE08A",
    description: "A glowing halo that floats above your head." });
  const p = c.part("halo", { pivot: [0, -11.2, 0], rot: [8, 0, 0], render: "emissive", anim: [bob("y", 0.35, 0.08)] });
  ring(p, { r0: 3.2, r1: 4.2, y0: 0, y1: 0, segs: 40, top: argb(1, 0xffffff), tint: 1 });
  ring(p, { r0: 4.2, r1: 3.2, y0: 0.01, y1: 0.01, segs: 40, top: argb(1, 0xffffff), tint: 1 });
  ring(p, { r0: 4.2, r1: 4.2, y0: -0.35, y1: 0.35, segs: 40, top: argb(1, 0xffffff), tint: 1 });
  ring(p, { r0: 3.2, r1: 3.2, y0: 0.35, y1: -0.35, segs: 40, top: argb(1, 0xffffff), tint: 1 });
  const g = c.part("glow", { parent: 0, render: "emissive" });
  ring(g, { r0: 4.2, r1: 5.6, y0: 0, y1: 0, segs: 40, top: argb(0.45, 0xffffff), bottom: argb(0, 0xffffff), tint: 1 });
  ring(g, { r0: 3.2, r1: 2.2, y0: 0, y1: 0, segs: 40, top: argb(0.4, 0xffffff), bottom: argb(0, 0xffffff), tint: 1 });
  return c;
}

// Energy shield: a slowly turning cage of glowing hexagon panels around your body.
function shield() {
  const c = new Cosmetic("aura_shield", "Energy Shield", "auras", { anchor: "body", defaultColor: "#4FC3FF",
    description: "A turning cage of glowing hex panels." });
  const p = c.part("cage", { pivot: [0, 6, 0], render: "emissive", anim: [spin("y", 1.2)] });
  const R = 10.5, rows = [-2.5, 2.5], cols = 9;
  rows.forEach((y, ri) => {
    for (let i = 0; i < cols; i++) {
      const a = Math.PI * 2 * (i + (ri % 2) * 0.5) / cols, hs = 2.7;
      const cx = Math.cos(a) * R, cz = Math.sin(a) * R;
      const tx = -Math.sin(a), tz = Math.cos(a);   // tangent
      const pts = [];
      for (let k = 0; k < 6; k++) {
        const b = Math.PI / 3 * k + Math.PI / 6;
        pts.push([cx + tx * Math.cos(b) * hs, y + Math.sin(b) * hs, cz + tz * Math.cos(b) * hs]);
      }
      const fade = 0.16;
      for (let k = 0; k < 6; k++) {                 // hex outline as thin quads toward the centre
        const a0 = pts[k], a1 = pts[(k + 1) % 6];
        const i0 = a0.map((v, j) => v + ([cx, y, cz][j] - v) * 0.18), i1 = a1.map((v, j) => v + ([cx, y, cz][j] - v) * 0.18);
        p.quad([...a0, 0, 0], [...a1, 1, 0], [...i1, 1, 1], [...i0, 0, 1], argb(0.95, 0xffffff), argb(0.6, 0xffffff), 1);
        p.quad([...i0, 0, 0], [...i1, 1, 0], [cx, y, cz, 1, 1], [cx, y, cz, 0, 1], argb(fade, 0xffffff), argb(fade * 0.4, 0xffffff), 1);
      }
    }
  });
  return c;
}

// Star orbit: three Stars of David circling you, each spinning on its own.
function starOrbit() {
  const c = new Cosmetic("aura_stars", "Star Orbit", "auras", { anchor: "body", defaultColor: "#6AA8FF",
    description: "Three glowing Stars of David orbit around you." });
  const orbit = c.part("orbit", { pivot: [0, 5, 0], rot: [0, 0, 8], anim: [spin("y", 2.4)] });
  const o = c.index(orbit);
  for (let i = 0; i < 3; i++) {
    const a = Math.PI * 2 * i / 3, R = 10;
    const s = c.part("star" + i, { parent: o, pivot: [Math.cos(a) * R, 0, Math.sin(a) * R], render: "emissive",
      anim: [spin("y", -6), bob("y", 1.2, 0.09, i * 2.1)] });
    const tri = (rot, r, col, z) => {
      const pt = (k) => [Math.sin(rot + Math.PI * 2 * k / 3) * r, -Math.cos(rot + Math.PI * 2 * k / 3) * r, z];
      return [pt(0), pt(1), pt(2)];
    };
    // each triangle drawn as a thick outline (3 bars) on both sides
    for (const [rot] of [[0], [Math.PI]]) {
      const outer = tri(rot, 2.6, 0, 0), inner = tri(rot, 1.75, 0, 0);
      for (let k = 0; k < 3; k++) {
        const a0 = outer[k], a1 = outer[(k + 1) % 3], b0 = inner[k], b1 = inner[(k + 1) % 3];
        s.quad([...a0, 0, 0], [...a1, 1, 0], [...b1, 1, 1], [...b0, 0, 1], argb(1, 0xffffff), argb(1, 0xffffff), 1);
      }
    }
    // soft glow disc behind
    const g = c.part("glow" + i, { parent: c.index(s), render: "emissive" });
    for (let k = 0; k < 16; k++) {
      const a0 = Math.PI * 2 * k / 16, a1 = Math.PI * 2 * (k + 1) / 16;
      g.quad([0, 0, 0.05, 0, 0], [0, 0, 0.05, 1, 0], [Math.cos(a1) * 3.6, Math.sin(a1) * 3.6, 0.05, 1, 1], [Math.cos(a0) * 3.6, Math.sin(a0) * 3.6, 0.05, 0, 1],
        argb(0.35, 0xffffff), argb(0, 0xffffff), 1);
    }
  }
  return c;
}

export default [halo(), shield(), starOrbit()];
