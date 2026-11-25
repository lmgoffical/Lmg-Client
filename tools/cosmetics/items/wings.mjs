import { Cosmetic, wave, shade, argb, bar, polar, mix } from "../lib.mjs";

// Angel wings: each side = darker underside layer + bright top layer, each split into an inner
// panel (shoulder -> wrist) and an outer panel hinged at the wrist that folds while flapping.
function angel() {
  const c = new Cosmetic("wings_angel", "Angel Wings", "wings", {
    anchor: "body", defaultColor: "#F6F6FF", hideWithElytra: true,
    description: "Feathered wings that fold at the wrist when they flap.",
  });
  const size = 20, hinge = 0.42, y0 = -0.09 * size, y1 = y0 + size;
  for (const sgn of [-1, 1]) {
    for (const under of [true, false]) {
      const base = c.part(`${sgn < 0 ? "R" : "L"}_${under ? "under" : "top"}_inner`, {
        pivot: [sgn * 1.5, 1.2, under ? 2.6 : 3.1],
        rot: [0, -sgn * 32, -sgn * 30],
        crouchRot: [0, sgn * 18, 0],
        scale: under ? 0.96 : 1,
        texture: "wing.png", render: "translucent",
        anim: [
          wave("y", -sgn * 12, 0.11, { movingSpeed: 0.12, movingOffset: -sgn * 8 }),
          wave("z", -sgn * 8, 0.11, { movingSpeed: 0.12 }),
        ],
      });
      const color = under ? argb(1, shade(0xffffff, 0.6)) : 0xffffffff;
      const xi = sgn * size * hinge;
      base.quad([0, y0, 0, 0, 0], [xi, y0, 0, hinge, 0], [xi, y1, 0, hinge, 1], [0, y1, 0, 0, 1], color, color, 1);
      const outer = c.part(`${sgn < 0 ? "R" : "L"}_${under ? "under" : "top"}_outer`, {
        parent: c.index(base), pivot: [xi, 0, 0], rot: [0, -sgn * 12, 0],
        texture: "wing.png", render: "translucent",
        anim: [wave("y", -sgn * 16, 0.11, { movingSpeed: 0.12 })],
      });
      const xo = sgn * size * (1 - hinge);
      outer.quad([0, y0, 0, hinge, 0], [xo, y0, 0, 1, 0], [xo, y1, 0, 1, 1], [0, y1, 0, hinge, 1], color, color, 1);
    }
  }
  return c;
}

// Demon wings: a bony arm, a wrist that folds, four long fingers and a scalloped membrane
// stretched between them (veined skin texture). Slow, heavy flaps.
function demon() {
  const c = new Cosmetic("wings_demon", "Demon Wings", "wings", {
    anchor: "body", defaultColor: "#5E1828", hideWithElytra: true,
    description: "Bat-style wings with bones, claws and a stretched membrane.",
  });
  const bone = argb(1, shade(0xffffff, 0.32)), skin = argb(0.94, 0xffffff), claw = 0xFFEDE3CF;
  for (const sgn of [-1, 1]) {
    const side = sgn < 0 ? "R" : "L";
    const arm = c.part(`${side}_arm`, {
      pivot: [sgn * 1.6, 1.4, 2.9], rot: [0, -sgn * 26, -sgn * 30], crouchRot: [0, sgn * 16, 0],
      anim: [wave("y", -sgn * 14, 0.09, { movingSpeed: 0.08, movingOffset: -sgn * 6 }), wave("z", -sgn * 9, 0.09, { movingSpeed: 0.08 })],
    });
    bar(arm, [0, 0, 0], [sgn * 8, -2.2, 0], 1.0, 1.0, bone, bone);
    // membrane from the arm down to the body
    const memArm = c.part(`${side}_arm_skin`, { parent: c.index(arm), texture: "membrane.png", render: "translucent" });
    const uvA = (x, y) => [Math.abs(x) / 18, (y + 3) / 16];
    const A = [0, 0.4], B = [sgn * 8, -1.8], Cc = [sgn * 6.5, 6.5], D = [0, 10];
    memArm.quad([A[0], A[1], 0.15, ...uvA(...A)], [B[0], B[1], 0.15, ...uvA(...B)], [Cc[0], Cc[1], 0.15, ...uvA(...Cc)], [D[0], D[1], 0.15, ...uvA(...D)], skin, skin, 1);

    const wrist = c.part(`${side}_wrist`, {
      parent: c.index(arm), pivot: [sgn * 8, -2.2, 0], rot: [0, -sgn * 12, 0],
      anim: [wave("y", -sgn * 12, 0.09, { movingSpeed: 0.08, phase: -0.6 })],
    });
    // thumb claw pointing up off the wrist
    bar(wrist, [0, 0, 0], [sgn * 0.6, -2.4, 0], 0.7, 0.7, claw, claw, 0);
    const fingers = [[-28, 12.5], [4, 13.5], [36, 11.5], [68, 9]];
    const tips = fingers.map(([deg, len]) => { const [x, y] = polar(len, deg); return [sgn * x, y]; });
    tips.forEach((t, i) => {
      bar(wrist, [0, 0, 0], [t[0], t[1], 0], 0.75 - i * 0.08, 0.7, bone, bone);
      bar(wrist, [t[0], t[1], 0], [t[0] * 1.06, t[1] * 1.06 + 0.4, 0], 0.4, 0.4, claw, claw, 0);
    });
    // scalloped skin between fingers (each edge sags toward the wrist), then back to the body
    const mem = c.part(`${side}_wrist_skin`, { parent: c.index(wrist), texture: "membrane.png", render: "translucent" });
    const uv = (x, y) => [Math.min(1, Math.abs(x) / 16 + 0.1), Math.min(1, (y + 8) / 22)];
    const body = [-sgn * 8, 12.2];
    const pts = [...tips, body];
    for (let i = 0; i < pts.length - 1; i++) {
      const a = pts[i], b = pts[i + 1];
      const sag = [(a[0] + b[0]) / 2 * 0.7, (a[1] + b[1]) / 2 * 0.7 + (i === pts.length - 2 ? 2 : 0)];
      mem.quad([0, 0, 0.12, ...uv(0, 0)], [a[0], a[1], 0.12, ...uv(...a)], [sag[0], sag[1], 0.12, ...uv(...sag)], [b[0], b[1], 0.12, ...uv(...b)], skin, skin, 1);
    }
  }
  return c;
}

// Butterfly wings: big upper + smaller lower wing per side, quick fluttering, patterned texture.
function butterfly() {
  const c = new Cosmetic("wings_butterfly", "Butterfly Wings", "wings", {
    anchor: "body", defaultColor: "#4FB6FF", hideWithElytra: true,
    description: "Fluttering patterned wings - pick any colour.",
  });
  for (const sgn of [-1, 1]) {
    const side = sgn < 0 ? "R" : "L";
    const upper = c.part(`${side}_upper`, {
      pivot: [sgn * 0.5, 5.2, 2.5], rot: [0, -sgn * 22, -sgn * 6], texture: "butterfly_upper.png", render: "translucent",
      anim: [wave("y", -sgn * 28, 0.3, { offset: -sgn * 18, movingSpeed: 0.2 })],
    });
    upper.plane(0, -8.4, sgn * 11.5, 2.8, 0, 0, 1, 1, 0xffffffff, 1);
    const lower = c.part(`${side}_lower`, {
      parent: c.index(upper), pivot: [0, 0, 0.08], rot: [0, 0, sgn * 4], texture: "butterfly_lower.png", render: "translucent",
      anim: [wave("y", -sgn * 6, 0.3, { phase: -0.5, movingSpeed: 0.2 })],
    });
    lower.plane(0, 0.6, sgn * 8.2, 8.8, 0, 0, 1, 1, 0xffffffff, 1);
  }
  // little body + antennae between the wings
  const bodyPart = c.part("thorax", { pivot: [0, 4.6, 2.6] });
  bar(bodyPart, [0, -1.5, 0], [0, 4.5, 0], 1.1, 1.1, 0xFF1A1A22, 0xFF1A1A22, 0);
  return c;
}

// Neon wings: glowing blades of light with a soft halo around each one. Slow, floaty motion.
function neon() {
  const c = new Cosmetic("wings_neon", "Neon Wings", "wings", {
    anchor: "body", defaultColor: "#45E0FF", hideWithElytra: true,
    description: "Blades of light that glow in the dark.",
  });
  const blades = [[-24, 15], [-4, 18], [16, 17], [36, 14], [56, 10]];
  for (const sgn of [-1, 1]) {
    const side = sgn < 0 ? "R" : "L";
    const root = c.part(`${side}_root`, {
      pivot: [sgn * 1.4, 1.8, 3.1], rot: [0, -sgn * 30, -sgn * 26],
      anim: [wave("y", -sgn * 7, 0.06, { movingSpeed: 0.05 }), wave("z", -sgn * 5, 0.06, { phase: 1 })],
    });
    const core = c.part(`${side}_blades`, { parent: c.index(root), render: "emissive" });
    const glow = c.part(`${side}_glow`, { parent: c.index(root), render: "emissive", texture: "glow_bar.png" });
    for (const [deg, len] of blades) {
      const [dx, dy] = polar(1, deg);
      const dir = [sgn * dx, dy], nrm = [-dir[1], dir[0]];
      const at = (r, w) => [dir[0] * r + nrm[0] * w, dir[1] * r + nrm[1] * w];
      const base = 2, tip = len, wid = 0.9;
      const p = (r, w, z) => [...at(r, w), z];
      // diamond-shaped blade: bright root -> white-hot tip
      const rootC = argb(0.95, 0xffffff), tipC = argb(0.9, mix(0xffffff, 0xffffff, 1));
      core.quad([...p(base, 0, 0), 0, 0], [...p(base + (tip - base) * 0.35, wid, 0), 1, 0], [...p(tip, 0, 0), 1, 1], [...p(base + (tip - base) * 0.35, -wid, 0), 0, 1], rootC, tipC, 1);
      // soft glow strip behind the blade
      const gw = 2.6;
      glow.quad([...p(base - 0.5, gw, 0.2), 0, 0], [...p(base - 0.5, -gw, 0.2), 1, 0], [...p(tip + 1, -gw * 0.6, 0.2), 1, 1], [...p(tip + 1, gw * 0.6, 0.2), 0, 1], argb(0.55, 0xffffff), argb(0.15, 0xffffff), 1);
    }
  }
  return c;
}

export default [angel(), demon(), butterfly(), neon()];
