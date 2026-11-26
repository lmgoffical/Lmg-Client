import { Cosmetic, ring, argb, shade, mix, dquad, bob, spin, wave } from "../lib.mjs";

// Head space: head top at y = -8, sides at x/z = +-4, front of the face = -z.

// frustum wall: radius rTop at yTop down to rBot at yBot (outward-facing, like the top hat's wall)
const wall = (p, rTop, yTop, rBot, yBot, segs, top, bottom = top, tint = 1) =>
  ring(p, { r0: rTop, r1: rBot, y0: yTop, y1: yBot, segs, top, bottom, tint });

// Witch hat: a wide brim and a tall cone that bends over at the top, with a band and a buckle.
function witch() {
  const c = new Cosmetic("hat_witch", "Witch Hat", "hats", { defaultColor: "#3B2A5A",
    description: "Wide brim, crooked tip, orange band. The tip flops when you move." });
  const body = 0xFFFFFFFF, under = argb(1, shade(0xffffff, 0.68));
  const brim = c.part("brim", { pivot: [0, -8.1, 0], rot: [-3, 0, 4] });
  ring(brim, { r0: 0, r1: 7.6, y0: -0.35, y1: -0.35, segs: 36, top: body });
  ring(brim, { r0: 7.6, r1: 0, y0: 0, y1: 0, segs: 36, top: under });
  ring(brim, { r0: 7.6, r1: 7.6, y0: -0.35, y1: 0, segs: 36, top: under });
  wall(brim, 3.7, -3.6, 4.5, -0.35, 28, body, body);
  ring(brim, { r0: 4.6, r1: 4.6, y0: -1.6, y1: -0.4, segs: 28, top: 0xFFE8892C, tint: 0 });   // band
  brim.box(-0.9, -1.85, -4.95, 0.9, -0.15, -4.55, 0xFFE7C04A, 0xFFB98620, 0);                 // buckle
  brim.box(-0.45, -1.4, -5.05, 0.45, -0.6, -4.9, 0xFF2A2030, 0xFF2A2030, 0);
  // the cone, in three bending pieces
  const pieces = [[3.7, 2.6, 3.0, -14], [2.6, 1.4, 2.6, -22], [1.4, 0.0, 2.6, -30]];
  let parent = c.index(brim), y = -3.6;
  pieces.forEach(([r0, r1, h, bend], i) => {
    const p = c.part("cone" + i, { parent, pivot: [0, y, 0], rot: [bend * 0.45, 0, bend],
      anim: i > 0 ? [wave("z", 3, 0.06, { phase: -i, movingAmp: 4, movingSpeed: 0.1 })] : [] });
    wall(p, r1, -h, r0, 0, 24, body, body);
    parent = c.index(p);
    y = -h + 0.05;
  });
  return c;
}

// Cowboy hat: brim curled up at the sides, a pinched crown and a leather band.
function cowboy() {
  const c = new Cosmetic("hat_cowboy", "Cowboy Hat", "hats", { defaultColor: "#9A6434",
    description: "Curled brim, pinched crown, leather band. Yeehaw." });
  const p = c.part("hat", { pivot: [0, -8.2, 0], rot: [-5, 0, 0] });
  const body = 0xFFFFFFFF, under = argb(1, shade(0xffffff, 0.66));
  const segs = 40, rIn = 4.2, rOut = 7.6;
  const lift = (a) => -1.9 * Math.abs(Math.cos(a)) ** 2.5 + 0.35 * Math.sin(a) ** 2;
  for (let i = 0; i < segs; i++) {
    const a0 = Math.PI * 2 * i / segs, a1 = Math.PI * 2 * (i + 1) / segs;
    const P = (r, a, dy = 0) => [Math.cos(a) * r, (r === rIn ? 0 : lift(a)) + dy, Math.sin(a) * r];
    p.quad([...P(rIn, a0, -0.35), 0, 0], [...P(rIn, a1, -0.35), 1, 0], [...P(rOut, a1, -0.35), 1, 1], [...P(rOut, a0, -0.35), 0, 1], body, body, 1);
    p.quad([...P(rOut, a0), 0, 0], [...P(rOut, a1), 1, 0], [...P(rIn, a1), 1, 1], [...P(rIn, a0), 0, 1], under, under, 1);
    p.quad([...P(rOut, a0, -0.35), 0, 0], [...P(rOut, a1, -0.35), 1, 0], [...P(rOut, a1), 1, 1], [...P(rOut, a0), 0, 1], under, under, 1);
  }
  // crown: an oval, a bit narrower at the top, with a dent along the middle
  const cs = 28;
  for (let i = 0; i < cs; i++) {
    const a0 = Math.PI * 2 * i / cs, a1 = Math.PI * 2 * (i + 1) / cs;
    const O = (a, r, y) => [Math.cos(a) * r * 0.92, y, Math.sin(a) * r * 1.08];
    const topY = (a) => -5.0 + 0.7 * (1 - Math.abs(Math.sin(a)));
    p.quad([...O(a0, 3.55, topY(a0)), 0, 0], [...O(a1, 3.55, topY(a1)), 1, 0], [...O(a1, 4.25, -0.3), 1, 1], [...O(a0, 4.25, -0.3), 0, 1], body, body, 1);
    // top: pinched down the middle (the crease runs front to back)
    p.quad([...O(a0, 0.01, -4.1), 0, 0], [...O(a1, 0.01, -4.1), 1, 0], [...O(a1, 3.55, topY(a1)), 1, 1], [...O(a0, 3.55, topY(a0)), 0, 1], body, body, 1);
  }
  ring(p, { r0: 4.3, r1: 4.3, y0: -1.5, y1: -0.35, segs: 28, top: 0xFF3A2416, tint: 0 });
  p.box(-0.6, -1.6, -4.75, 0.6, -0.25, -4.35, 0xFFD9D2C0, 0xFFA59C88, 0);   // silver concho
  return c;
}

// Party hat: a striped cone worn at an angle, with a pom-pom and a little frill.
function party() {
  const c = new Cosmetic("hat_party", "Party Hat", "hats", { defaultColor: "#FF5FA2",
    description: "Striped party cone with a pom-pom. Every day is a party." });
  const p = c.part("hat", { pivot: [1.2, -8, -0.4], rot: [-8, 0, 14] });
  const segs = 20, bands = 6, R = 3.0, H = 7.5, white = 0xFFFFF6EC;
  for (let b = 0; b < bands; b++) {
    const y0 = -H * b / bands, y1 = -H * (b + 1) / bands, r0 = R * (1 - b / bands), r1 = R * (1 - (b + 1) / bands);
    for (let i = 0; i < segs; i++) {
      const a0 = Math.PI * 2 * i / segs, a1 = Math.PI * 2 * (i + 1) / segs;
      const striped = ((i + b * 2) % 6) < 3;
      const col = striped ? 0xFFFFFFFF : white, tint = striped ? 1 : 0;
      const P = (r, a, y) => [Math.cos(a) * r, y, Math.sin(a) * r];
      p.quad([...P(r1, a0, y1), 0, 0], [...P(r1, a1, y1), 1, 0], [...P(r0, a1, y0), 1, 1], [...P(r0, a0, y0), 0, 1], col, col, tint);
    }
  }
  ring(p, { r0: R, r1: R + 0.8, y0: 0, y1: 0.25, segs: 24, top: 0xFFFFE27A, tint: 0 });   // frill
  ring(p, { r0: R + 0.8, r1: R, y0: 0.3, y1: 0.05, segs: 24, top: 0xFFE8C050, tint: 0 });
  const pom = c.part("pom", { parent: 0, pivot: [0, -H, 0], anim: [bob("y", 0.25, 0.15)] });
  const gold = 0xFFFFE27A;
  pom.box(-0.9, -1.8, -0.9, 0.9, 0, 0.9, gold, gold, 0);
  pom.box(-1.2, -1.5, -0.55, 1.2, -0.3, 0.55, gold, gold, 0);
  pom.box(-0.55, -1.5, -1.2, 0.55, -0.3, 1.2, gold, gold, 0);
  return c;
}

// Flower crown: a leafy vine around your head with little flowers all the way round.
function flowers() {
  const c = new Cosmetic("hat_flowers", "Flower Crown", "hats", { defaultColor: "#FF9BC8",
    description: "A vine of little flowers and leaves. The colour picks the flowers." });
  const p = c.part("crown", {});
  const vine = 0xFF4E8A3A, leaf = 0xFF6DB34E, centre = 0xFFFFD84A, white = 0xFFFFF6FA;
  ring(p, { r0: 4.45, r1: 4.45, y0: -8.6, y1: -7.9, segs: 32, top: vine, tint: 0 });
  ring(p, { r0: 3.9, r1: 4.45, y0: -8.6, y1: -8.6, segs: 32, top: vine, tint: 0 });
  const n = 10;
  for (let i = 0; i < n; i++) {
    const a = Math.PI * 2 * i / n + 0.15, r = 4.55;
    const cx = Math.cos(a) * r, cz = Math.sin(a) * r, cy = -8.55;
    const tx = -Math.sin(a), tz = Math.cos(a);
    const big = i % 3 !== 1, s = big ? 0.62 : 0.5;
    const col = i % 4 === 3 ? white : 0xFFFFFFFF, tint = i % 4 === 3 ? 0 : 1;
    for (let k = 0; k < 5; k++) {   // five petals around the centre, in the tangent/up plane
      const b = Math.PI * 2 * k / 5 + i;
      const px = cx + tx * Math.cos(b) * s * 1.25, pz = cz + tz * Math.cos(b) * s * 1.25, py = cy + Math.sin(b) * s * 1.25;
      p.box(px - s / 2, py - s / 2, pz - s / 2, px + s / 2, py + s / 2, pz + s / 2, col, col, tint);
    }
    const cs = s * 0.5, ox = Math.cos(a) * 0.25, oz = Math.sin(a) * 0.25;
    p.box(cx + ox - cs, cy - cs, cz + oz - cs, cx + ox + cs, cy + cs, cz + oz + cs, centre, centre, 0);
    // a leaf between this flower and the next
    const la = a + Math.PI / n, lx = Math.cos(la) * 4.6, lz = Math.sin(la) * 4.6;
    p.box(lx - 0.35, -8.25, lz - 0.35, lx + 0.35, -7.75, lz + 0.35, leaf, leaf, 0);
    p.box(lx + Math.cos(la) * 0.3 - 0.25, -8.05, lz + Math.sin(la) * 0.3 - 0.25, lx + Math.cos(la) * 0.3 + 0.25, -7.55, lz + Math.sin(la) * 0.3 + 0.25, leaf, leaf, 0);
  }
  return c;
}

// Frog hat: a green bucket hat with big frog eyes on top.
function frog() {
  const c = new Cosmetic("hat_frog", "Frog Hat", "hats", { defaultColor: "#6BCB5A",
    description: "A bucket hat with frog eyes that blink every so often." });
  const p = c.part("hat", {});
  const body = 0xFFFFFFFF, under = argb(1, shade(0xffffff, 0.72));
  wall(p, 3.9, -10.7, 4.45, -7.7, 28, body, body);
  ring(p, { r0: 0, r1: 3.9, y0: -10.7, y1: -10.7, segs: 28, top: body });
  ring(p, { r0: 4.45, r1: 6.3, y0: -7.7, y1: -6.6, segs: 32, top: body });
  ring(p, { r0: 6.3, r1: 4.45, y0: -6.45, y1: -7.55, segs: 32, top: under });
  ring(p, { r0: 6.3, r1: 6.3, y0: -6.6, y1: -6.45, segs: 32, top: under });
  const white = 0xFFF8F8F4, black = 0xFF141414, blush = 0xFFFF9AA8;
  for (const s of [-1, 1]) {
    p.box(s * 2.0 - 1.3, -12.6, -2.9, s * 2.0 + 1.3, -10.4, -0.5, body, body, 1);   // eye bump
    p.box(s * 2.0 - 1.0, -12.3, -3.05, s * 2.0 + 1.0, -10.7, -2.85, white, white, 0); // eye white
    p.box(s * 2.0 - 0.5, -11.9, -3.15, s * 2.0 + 0.5, -10.9, -3.0, black, black, 0); // pupil
    p.box(s * 2.0 - 0.15, -11.85, -3.2, s * 2.0 + 0.15, -11.55, -3.14, white, white, 0); // glint
    p.box(s * 2.9 - 0.55, -8.9, -4.35, s * 2.9 + 0.55, -8.4, -4.2, blush, blush, 0);   // cheeks
  }
  p.box(-1.4, -8.75, -4.4, 1.4, -8.5, -4.2, 0xFF2D5A26, 0xFF2D5A26, 0);              // smile
  p.box(-1.7, -9.05, -4.4, -1.4, -8.6, -4.2, 0xFF2D5A26, 0xFF2D5A26, 0);
  p.box(1.4, -9.05, -4.4, 1.7, -8.6, -4.2, 0xFF2D5A26, 0xFF2D5A26, 0);
  // blinking lids (they drop over the eye for a moment)
  for (const s of [-1, 1]) {
    const lid = c.part(s < 0 ? "blinkR" : "blinkL", { pivot: [s * 2.0, -12.62, -3.28],
      anim: [{ type: "twitch", axis: "x", amp: -90, period: 120, duration: 6, offset: 0 }], rot: [90, 0, 0] });
    lid.box(-1.05, 0, -0.1, 1.05, 1.7, 0.08, body, body, 1);
  }
  return c;
}

// Headphones: a padded band over the top with two big cups; the cups glow softly.
function headphones() {
  const c = new Cosmetic("hat_headphones", "Headphones", "hats", { defaultColor: "#7A5CFF",
    description: "Chunky headphones with glowing rings on the cups." });
  const p = c.part("band", {});
  const dark = 0xFF24242C, pad = 0xFF3A3A46, body = 0xFFFFFFFF;
  // band: a thin arch over the head
  const n = 12;
  for (let i = 0; i < n; i++) {
    const a0 = Math.PI * i / n, a1 = Math.PI * (i + 1) / n, r = 4.9;
    const P = (a, rr) => [Math.cos(a) * rr, -4.6 - Math.sin(a) * rr * 0.86];
    const [x0, y0] = P(a0, r), [x1, y1] = P(a1, r), [x2, y2] = P(a1, r + 0.7), [x3, y3] = P(a0, r + 0.7);
    for (const z of [-0.9, 0.9]) {
      dquad(p, [x0, y0, z, 0, 0], [x1, y1, z, 1, 0], [x2, y2, z, 1, 1], [x3, y3, z, 0, 1], body, body, 1);
    }
    dquad(p, [x0, y0, -0.9, 0, 0], [x1, y1, -0.9, 1, 0], [x1, y1, 0.9, 1, 1], [x0, y0, 0.9, 0, 1], pad, pad, 0);
    dquad(p, [x3, y3, -0.9, 0, 0], [x2, y2, -0.9, 1, 0], [x2, y2, 0.9, 1, 1], [x3, y3, 0.9, 0, 1], body, body, 1);
  }
  for (const s of [-1, 1]) {
    p.box(s * 4.0 - (s < 0 ? 0.9 : 0), -6.4, -2.2, s * 4.0 + (s > 0 ? 0.9 : 0), -2.0, 2.2, pad, pad, 0);     // ear pad
    p.box(s * 4.9 - (s < 0 ? 1.0 : 0), -6.8, -2.6, s * 4.9 + (s > 0 ? 1.0 : 0), -1.6, 2.6, body, body, 1);  // cup
    p.box(s * 4.9 - 0.25, -4.9, -2.8, s * 4.9 + 0.25, -4.4, -2.6, dark, dark, 0);                            // mic stub
  }
  const g = c.part("glow", { render: "emissive" });
  for (const s of [-1, 1]) {
    const x = s * 5.95;
    for (let i = 0; i < 16; i++) {
      const a0 = Math.PI * 2 * i / 16, a1 = Math.PI * 2 * (i + 1) / 16;
      const Q = (a, r) => [x, -4.2 + Math.sin(a) * r, Math.cos(a) * r];
      dquad(g, [...Q(a0, 2.0), 0, 0], [...Q(a1, 2.0), 1, 0], [...Q(a1, 1.4), 1, 1], [...Q(a0, 1.4), 0, 1], 0xFFFFFFFF, 0xFFFFFFFF, 1);
    }
  }
  return c;
}

// Crown of stars: little glowing stars circling above your head.
function starCrown() {
  const c = new Cosmetic("hat_starcrown", "Star Crown", "hats", { defaultColor: "#FFE27A",
    description: "Tiny glowing stars that circle above your head." });
  const orbit = c.part("orbit", { pivot: [0, -10.2, 0], rot: [6, 0, 0], anim: [spin("y", 1.6)] });
  const n = 7;
  for (let i = 0; i < n; i++) {
    const a = Math.PI * 2 * i / n, r = 4.6;
    const s = c.part("star" + i, { parent: c.index(orbit), pivot: [Math.cos(a) * r, 0, Math.sin(a) * r], render: "emissive",
      anim: [spin("y", -4), bob("y", 0.35, 0.12, i * 0.9)] });
    const o = 0.95, k = 0.38;
    const pts = [];
    for (let j = 0; j < 10; j++) {
      const b = -Math.PI / 2 + Math.PI * j / 5, rr = j % 2 === 0 ? o : k;
      pts.push([Math.cos(b) * rr, Math.sin(b) * rr, 0]);
    }
    for (let j = 0; j < 10; j++) {
      const A = pts[j], B = pts[(j + 1) % 10];
      s.tri([0, 0, 0], B, A, 0xFFFFFFFF, 0xFFFFFFFF, 1);
      s.tri([0, 0, 0], A, B, 0xFFFFFFFF, 0xFFFFFFFF, 1);
    }
    const g = c.part("starglow" + i, { parent: c.index(s), render: "emissive" });
    for (let j = 0; j < 12; j++) {
      const a0 = Math.PI * 2 * j / 12, a1 = Math.PI * 2 * (j + 1) / 12;
      dquad(g, [0, 0, 0.02, 0, 0], [0, 0, 0.02, 1, 0], [Math.cos(a1) * 1.9, Math.sin(a1) * 1.9, 0.02, 1, 1], [Math.cos(a0) * 1.9, Math.sin(a0) * 1.9, 0.02, 0, 1],
        argb(0.4, 0xffffff), argb(0, 0xffffff), 1);
    }
  }
  return c;
}

export default [witch(), cowboy(), party(), flowers(), frog(), headphones(), starCrown()];
