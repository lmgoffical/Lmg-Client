import { Cosmetic, argb, shade, wave, fan, flatOutline, ellipsePts, bar, polar, dquad } from "../lib.mjs";

// Body space: neck at (0,0,0), back of the body at z = +2. Wings hang off the upper back.

// Fairy wings: two see-through lobes a side with glowing veins, fluttering fast.
function fairy() {
  const c = new Cosmetic("wings_fairy", "Fairy Wings", "wings", { anchor: "body", defaultColor: "#B98CFF",
    hideWithElytra: true, description: "See-through glittery wings that flutter fast." });
  for (const sgn of [-1, 1]) {
    const side = sgn < 0 ? "R" : "L";
    const root = c.part(`${side}_root`, { pivot: [sgn * 1.0, 3.2, 2.3], rot: [0, -sgn * 28, -sgn * 6], scale: 1.45,
      crouchRot: [0, sgn * 14, 0],
      anim: [wave("y", -sgn * 16, 0.5, { movingSpeed: 0.25, movingAmp: -sgn * 6 })] });
    const film = c.part(`${side}_film`, { parent: c.index(root), render: "translucent" });
    const glow = c.part(`${side}_veins`, { parent: c.index(root), render: "emissive" });
    const upper = ellipsePts(sgn * 5.4, -4.2, 5.2, 3.0, sgn * -35, 26);
    const lower = ellipsePts(sgn * 3.6, 3.4, 3.4, 2.1, sgn * 40, 22);
    for (const [pts, cx, cy] of [[upper, sgn * 5.4, -4.2], [lower, sgn * 3.6, 3.4]]) {
      fan(film, pts.map(q => [q[0], q[1], 0]), [cx * 0.35, cy * 0.35, 0], argb(0.25, 0xffffff), argb(0.62, 0xffffff), 1);
      flatOutline(glow, pts, 0.01, 0.2, argb(0.95, 0xffffff), 1);
    }
    for (const deg of [-58, -40, -22, 30, 50]) {   // veins from the root
      const len = deg < 0 ? 8.5 : 5.2;
      const [dx, dy] = polar(len, deg);
      bar(glow, [0, 0, 0.02], [sgn * dx, dy, 0.02], 0.14, 0.1, argb(0.8, 0xffffff), argb(0.4, 0xffffff), 1);
    }
    // a few sparkles on the film
    for (const [x, y] of [[6.5, -5.6], [4.0, -3.0], [8.6, -3.8], [3.6, 3.8], [5.0, 2.7]]) {
      const s = 0.22;
      dquad(glow, [sgn * x - s, y, 0.05, 0, 0], [sgn * x, y - s, 0.05, 1, 0], [sgn * x + s, y, 0.05, 1, 1], [sgn * x, y + s, 0.05, 0, 1], 0xFFFFFFFF, 0xFFFFFFFF, 0);
    }
  }
  return c;
}

// Dragon wings: big leathery wings with a bony arm and fingers, slow heavy flaps.
function dragon() {
  const c = new Cosmetic("wings_dragon", "Dragon Wings", "wings", { anchor: "body", defaultColor: "#3A8A5C",
    hideWithElytra: true, description: "Huge scaled dragon wings with slow, heavy flaps. Goes with the Dragon Tail." });
  for (const sgn of [-1, 1]) {
    const side = sgn < 0 ? "R" : "L";
    const root = c.part(`${side}_arm`, { pivot: [sgn * 1.4, 1.6, 2.4], rot: [0, -sgn * 30, -sgn * 20],
      crouchRot: [0, sgn * 22, 0],
      anim: [wave("y", -sgn * 14, 0.05, { movingSpeed: 0.05, movingOffset: -sgn * 6 }), wave("z", -sgn * 10, 0.05, { phase: 0.6 })] });
    const bone = 0xFFFFFFFF, mem = argb(1, shade(0xffffff, 0.62)), memDark = argb(1, shade(0xffffff, 0.45));
    // the arm: shoulder -> wrist (along x), then fingers fan out downward
    const wrist = [sgn * 9.5, -5.5];
    bar(root, [0, 0, 0], [...wrist, 0], 1.1, 1.1, bone, bone, 1);
    const fingers = [[16, 4], [12.5, 9.5], [8.5, 13], [4.2, 14.5]];
    const tips = fingers.map(([x, y]) => [sgn * x, y]);
    tips.forEach((t) => bar(root, [...wrist, 0], [...t, 0], 0.55, 0.55, bone, bone, 1));
    // claw at the wrist
    root.tri([wrist[0] + sgn * 0.6, wrist[1] - 2.0, 0], [wrist[0] - sgn * 0.4, wrist[1], 0.3], [wrist[0] + sgn * 0.6, wrist[1], -0.3], 0xFFE8E2D0, 0xFFE8E2D0, 0);
    root.tri([wrist[0] + sgn * 0.6, wrist[1] - 2.0, 0], [wrist[0] + sgn * 0.6, wrist[1], -0.3], [wrist[0] - sgn * 0.4, wrist[1], 0.3], 0xFFE8E2D0, 0xFFE8E2D0, 0);
    // membrane between the fingers
    const edge = [[0, 1.2], ...tips, [sgn * 1.2, 8]];
    for (let i = 0; i < edge.length - 1; i++) {
      const a = edge[i], b = edge[i + 1];
      const col = i % 2 ? mem : memDark;
      root.tri([...wrist, 0.05], [...b, 0.05], [...a, 0.05], col, col, 1);
      root.tri([...wrist, 0.05], [...a, 0.05], [...b, 0.05], col, col, 1);
    }
  }
  return c;
}

// Crystal wings: floating glowing shards that drift apart and back together.
function crystal() {
  const c = new Cosmetic("wings_crystal", "Crystal Wings", "wings", { anchor: "body", defaultColor: "#7FE7FF",
    hideWithElytra: true, description: "Floating glowing crystal shards that drift in and out." });
  const shards = [[-38, 12, 1.9], [-18, 15, 2.2], [4, 14, 2.1], [26, 11, 1.8], [50, 8, 1.4]];
  for (const sgn of [-1, 1]) {
    const side = sgn < 0 ? "R" : "L";
    const root = c.part(`${side}_root`, { pivot: [sgn * 1.6, 2.5, 3.4], rot: [0, -sgn * 24, -sgn * 18],
      anim: [wave("y", -sgn * 6, 0.05), wave("z", -sgn * 4, 0.05, { phase: 1 })] });
    shards.forEach(([deg, len, wid], i) => {
      const [dx, dy] = polar(1, deg);
      const dir = [sgn * dx, dy];
      const p = c.part(`${side}_shard${i}`, { parent: c.index(root), pivot: [dir[0] * 3.5, dir[1] * 3.5, i * 0.15], render: "emissive",
        anim: [{ type: "bob", axis: "x", amp: sgn * 0.6, speed: 0.07, phase: i * 0.8 }, { type: "bob", axis: "y", amp: 0.5, speed: 0.06, phase: i * 1.3 }] });
      const n = [-dir[1], dir[0]];
      const P = (r, w, z) => [dir[0] * r + n[0] * w, dir[1] * r + n[1] * w, z];
      const tip = P(len, 0, 0), base = P(0, 0, 0), l = P(len * 0.3, wid, 0), r = P(len * 0.3, -wid, 0);
      const f = P(len * 0.3, 0, -0.5), b = P(len * 0.3, 0, 0.5);
      const bright = argb(0.95, 0xffffff), mid = argb(0.8, shade(0xffffff, 0.8)), dim = argb(0.7, shade(0xffffff, 0.6));
      for (const z of [f, b]) {
        p.tri(z, tip, l, bright, mid, 1); p.tri(z, l, tip, bright, mid, 1);
        p.tri(z, r, tip, bright, mid, 1); p.tri(z, tip, r, bright, mid, 1);
        p.tri(z, base, r, mid, dim, 1); p.tri(z, r, base, mid, dim, 1);
        p.tri(z, l, base, mid, dim, 1); p.tri(z, base, l, mid, dim, 1);
      }
    });
  }
  return c;
}

export default [fairy(), dragon(), crystal()];
