import { Cosmetic, argb, shade, bar, disc, twitch, wave, bob } from "../lib.mjs";

// Head space: head top at y = -8, sides at x = +-4, front of the face = -z.

// Bear ears: round and soft, with lighter insides.
function bear() {
  const c = new Cosmetic("ears_bear", "Bear Ears", "ears", { defaultColor: "#7A5234",
    description: "Round fuzzy bear ears. They wiggle a little." });
  for (const s of [-1, 1]) {
    const p = c.part(s < 0 ? "right" : "left", { pivot: [s * 3.0, -7.7, -0.4], rot: [0, 0, s * 10],
      anim: [twitch("z", s * 9, 160, 8, s < 0 ? 10 : 80)] });
    disc(p, 0, -1.2, 1.8, -0.5, 0.5, 0xFFFFFFFF, 1, 18, argb(1, shade(0xffffff, 0.85)));
    disc(p, 0, -1.0, 1.05, -0.62, -0.5, 0xFFE9C9A8, 0, 14);
  }
  return c;
}

// Devil horns: two curved horns that get thinner toward the tip.
function horns() {
  const c = new Cosmetic("ears_horns", "Devil Horns", "ears", { defaultColor: "#C2183A",
    description: "Curved little devil horns. Goes with the Devil Tail." });
  for (const s of [-1, 1]) {
    const p = c.part(s < 0 ? "right" : "left", { pivot: [s * 2.5, -7.9, -1.6], rot: [-10, 0, 0] });
    const pts = [[0, 0], [s * 0.7, -1.3], [s * 1.0, -2.5], [s * 0.75, -3.5], [s * 0.15, -4.2]];
    const w = [1.5, 1.2, 0.9, 0.6, 0.3];
    for (let i = 0; i < pts.length - 1; i++) {
      const t = i / (pts.length - 2);
      const col = argb(1, shade(0xffffff, 1 - t * 0.25));
      bar(p, [...pts[i], 0], [...pts[i + 1], 0], w[i], w[i], col, argb(1, shade(0xffffff, 0.95 - t * 0.25)), 1);
    }
  }
  return c;
}

// Bug antennae: thin stalks with glowing balls that bob around.
function antennae() {
  const c = new Cosmetic("ears_antennae", "Bug Antennae", "ears", { defaultColor: "#FFD23F",
    description: "Wobbly antennae with glowing tips." });
  const dark = 0xFF1E1A20;
  for (const s of [-1, 1]) {
    const stalk = c.part(s < 0 ? "right" : "left", { pivot: [s * 1.6, -7.95, -1.2], rot: [-14, 0, s * 22],
      anim: [wave("z", s * 6, 0.09, { phase: s < 0 ? 0 : 1.7, movingAmp: s * 8, movingSpeed: 0.12 }), wave("x", 4, 0.07, { phase: s })] });
    bar(stalk, [0, 0, 0], [s * 0.3, -2.2, 0], 0.32, 0.32, dark, dark, 0);
    const top = c.part((s < 0 ? "right" : "left") + "_top", { parent: c.index(stalk), pivot: [s * 0.3, -2.2, 0], rot: [0, 0, -s * 18],
      anim: [wave("z", s * 8, 0.11, { phase: s < 0 ? 0.6 : 2.3 })] });
    bar(top, [0, 0, 0], [0, -1.8, 0], 0.26, 0.26, dark, dark, 0);
    const ball = c.part((s < 0 ? "right" : "left") + "_ball", { parent: c.index(top), pivot: [0, -2.15, 0], render: "emissive",
      anim: [bob("y", 0.12, 0.2, s)] });
    ball.box(-0.55, -0.55, -0.55, 0.55, 0.55, 0.55, 0xFFFFFFFF, 0xFFFFFFFF, 1);
    ball.box(-0.75, -0.35, -0.35, 0.75, 0.35, 0.35, 0xFFFFFFFF, 0xFFFFFFFF, 1);
    ball.box(-0.35, -0.75, -0.35, 0.35, 0.75, 0.35, 0xFFFFFFFF, 0xFFFFFFFF, 1);
    ball.box(-0.35, -0.35, -0.75, 0.35, 0.35, 0.75, 0xFFFFFFFF, 0xFFFFFFFF, 1);
  }
  return c;
}

// Elf ears: long pointed ears that stick out sideways from your head.
function elf() {
  const c = new Cosmetic("ears_elf", "Elf Ears", "ears", { defaultColor: "#E9B894",
    description: "Long pointy elf ears. Pick your skin tone with the colour." });
  for (const s of [-1, 1]) {
    const p = c.part(s < 0 ? "right" : "left", { pivot: [s * 4.0, -4.3, 0.4], rot: [0, s * 18, s * -28],
      anim: [twitch("z", s * -8, 150, 7, s < 0 ? 0 : 70)] });
    // flat leaf shape lying out sideways (in the x/y plane), with a little thickness
    const L = 4.4, H = 1.7, d = 0.45, col = 0xFFFFFFFF, inner = argb(1, shade(0xffffff, 0.82));
    const A = [0, -H / 2], B = [s * L, -H * 0.9], C = [0, H / 2];
    for (const z of [-d / 2, d / 2]) {
      p.tri([B[0], B[1], z], [A[0], A[1], z], [C[0], C[1], z], col, col, 1);
      p.tri([B[0], B[1], z], [C[0], C[1], z], [A[0], A[1], z], col, col, 1);
    }
    p.quad([A[0], A[1], -d / 2, 0, 0], [B[0], B[1], -d / 2, 1, 0], [B[0], B[1], d / 2, 1, 1], [A[0], A[1], d / 2, 0, 1], col, col, 1);
    p.quad([B[0], B[1], -d / 2, 0, 0], [C[0], C[1], -d / 2, 1, 0], [C[0], C[1], d / 2, 1, 1], [B[0], B[1], d / 2, 0, 1], col, col, 1);
    p.tri([s * L * 0.7, -H * 0.62, -d / 2 - 0.05], [s * 0.3, -H * 0.3, -d / 2 - 0.05], [s * 0.3, H * 0.25, -d / 2 - 0.05], inner, inner, 1);
    p.tri([s * L * 0.7, -H * 0.62, -d / 2 - 0.05], [s * 0.3, H * 0.25, -d / 2 - 0.05], [s * 0.3, -H * 0.3, -d / 2 - 0.05], inner, inner, 1);
  }
  return c;
}

export default [bear(), horns(), antennae(), elf()];
