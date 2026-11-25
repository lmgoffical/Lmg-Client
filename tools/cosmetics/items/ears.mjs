import { Cosmetic, argb, shade, twitch, wave } from "../lib.mjs";

// A thick triangular ear: front/back faces, side walls, and an inner coloured patch.
function ear(p, w, h, d, outer, inner, tipCol, innerTint, s = 0.62) {
  const z0 = -d / 2, z1 = d / 2;
  const A = [0, -h], L = [-w / 2, 0], R = [w / 2, 0];
  const tip = tipCol ?? outer;
  p.tri([A[0], A[1], z0], [R[0], R[1], z0], [L[0], L[1], z0], tip, outer, 1);        // front
  p.tri([A[0], A[1], z1], [L[0], L[1], z1], [R[0], R[1], z1], tip, outer, 1);        // back
  p.quad([A[0], A[1], z0, 0, 0], [L[0], L[1], z0, 1, 0], [L[0], L[1], z1, 1, 1], [A[0], A[1], z1, 0, 1], tip, outer, 1);
  p.quad([R[0], R[1], z0, 0, 0], [A[0], A[1], z0, 1, 0], [A[0], A[1], z1, 1, 1], [R[0], R[1], z1, 0, 1], outer, tip, 1);
  p.quad([L[0], L[1], z0, 0, 0], [R[0], R[1], z0, 1, 0], [R[0], R[1], z1, 1, 1], [L[0], L[1], z1, 0, 1], outer, outer, 1);
  // inner patch, slightly in front
  p.tri([0, -h * 0.82, z0 - 0.05], [w / 2 * s, -0.35, z0 - 0.05], [-w / 2 * s, -0.35, z0 - 0.05], inner, inner, innerTint);
}

function cat() {
  const c = new Cosmetic("ears_cat", "Cat Ears", "ears", { defaultColor: "#2B2530",
    description: "Soft cat ears that twitch every now and then." });
  const outer = 0xFFFFFFFF, inner = 0xFFF4A6BE;
  for (const s of [-1, 1]) {
    const p = c.part(s < 0 ? "right" : "left", { pivot: [s * 2.6, -7.8, -0.6], rot: [-8, 0, s * 14],
      anim: [twitch("z", s * 12, 140, 7, s < 0 ? 0 : 50)] });
    ear(p, 3.4, 3.4, 0.9, outer, inner, null, 0);
  }
  return c;
}

function fox() {
  const c = new Cosmetic("ears_fox", "Fox Ears", "ears", { defaultColor: "#E8752C",
    description: "Tall fox ears with dark tips and fluffy insides." });
  const outer = 0xFFFFFFFF, tip = 0xFF2A1E1A, inner = 0xFFF7EDE2;
  for (const s of [-1, 1]) {
    const p = c.part(s < 0 ? "right" : "left", { pivot: [s * 2.5, -7.8, -0.2], rot: [-6, 0, s * 18],
      anim: [twitch("x", -14, 170, 8, s < 0 ? 20 : 95)] });
    ear(p, 3.6, 4.8, 1.0, outer, inner, tip, 0, 0.45);
    // fluff tufts
    p.box(-0.9, -1.3, -0.75, 0.9, -0.2, -0.45, inner, inner, 0);
  }
  return c;
}

function bunny() {
  const c = new Cosmetic("ears_bunny", "Bunny Ears", "ears", { defaultColor: "#F2EEF4",
    description: "Long bunny ears - the left one flops over." });
  const outer = 0xFFFFFFFF, inner = 0xFFF2A0B8;
  const earBox = (p, len) => {
    p.box(-1.1, -len, -0.45, 1.1, 0, 0.45, outer, outer, 1);
    p.box(-0.65, -len + 0.5, -0.55, 0.65, -0.4, -0.45, inner, inner, 0);
  };
  const r = c.part("right", { pivot: [-1.8, -7.9, 0], rot: [-6, 0, -10], anim: [wave("z", 3, 0.07)] });
  earBox(r, 7.5);
  const l = c.part("left", { pivot: [1.8, -7.9, 0], rot: [-6, 0, 12], anim: [wave("z", 3, 0.07, { phase: 1.3 })] });
  earBox(l, 4);
  const flop = c.part("left_tip", { parent: c.index(l), pivot: [0, -4, 0], rot: [-15, 0, 128], anim: [wave("z", 7, 0.09)] });
  flop.box(-1.1, -3.6, -0.45, 1.1, 0, 0.45, outer, outer, 1);
  flop.box(-0.65, -3.2, -0.55, 0.65, 0, -0.45, inner, inner, 0);
  return c;
}

export default [cat(), fox(), bunny()];
