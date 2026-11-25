import { Cosmetic, ring, argb, mix, shade, bar } from "../lib.mjs";

// Head space: head top at y = -8, sides at x/z = +-4, front of the face = -z.

// Crown: thick gold band, spikes topped with gold balls, gems, a red velvet cap and a little cross.
function crown() {
  const c = new Cosmetic("hat_crown", "Royal Crown", "hats", { anchor: "head", tintable: false, defaultColor: "#E7B43C",
    description: "Gold crown with gems and a velvet cap." });
  const p = c.part("crown", {});
  const half = 4.55, yb = -7.6, yt = -9.9, th = 0.6;
  const gold = 0xFFE7B43C, goldDark = 0xFFB98620, tipGold = 0xFFFFD45A, velvet = 0xFF8E1630;
  p.box(-3.95, -10.7, -3.95, 3.95, -7.9, 3.95, velvet, velvet, 0);                     // velvet cap
  p.box(-0.35, -12.7, -0.35, 0.35, -10.6, 0.35, gold, gold, 0);                        // cross
  p.box(-1.1, -12.1, -0.35, 1.1, -11.5, 0.35, gold, gold, 0);
  p.box(-half, yt, -half, half, yb, -half + th, gold, gold, 0);
  p.box(-half, yt, half - th, half, yb, half, gold, gold, 0);
  p.box(-half, yt, -half + th, -half + th, yb, half - th, gold, gold, 0);
  p.box(half - th, yt, -half + th, half, yb, half - th, gold, gold, 0);
  p.box(-half - 0.15, yb - 0.55, -half - 0.15, half + 0.15, yb, half + 0.15, goldDark, goldDark, 0);
  p.box(-half - 0.1, yt, -half - 0.1, half + 0.1, yt + 0.4, half + 0.1, tipGold, tipGold, 0);
  for (const [sx, sz] of [[0, -1], [0, 1], [-1, 0], [1, 0]]) {
    for (const k of [-1, 0, 1]) {
      const along = k * 2.9, hgt = k === 0 ? 3.4 : 2.5, w = 0.95;
      const cx = sx !== 0 ? sx * (half - th / 2) : along, cz = sz !== 0 ? sz * (half - th / 2) : along;
      const ax = sx !== 0 ? 0 : 1, az = sx !== 0 ? 1 : 0;
      const b1 = [cx - ax * w, yt, cz - az * w], b2 = [cx + ax * w, yt, cz + az * w];
      const b3 = [b2[0] + sx * 0.3, yt, b2[2] + sz * 0.3], b4 = [b1[0] + sx * 0.3, yt, b1[2] + sz * 0.3];
      const apex = [cx + sx * 0.25, yt - hgt, cz + sz * 0.25];
      p.tri(apex, b2, b1, gold, gold, 0); p.tri(apex, b3, b2, goldDark, goldDark, 0);
      p.tri(apex, b4, b3, gold, gold, 0); p.tri(apex, b1, b4, goldDark, goldDark, 0);
      const bs = 0.45;
      p.box(apex[0] - bs, apex[1] - bs * 2, apex[2] - bs, apex[0] + bs, apex[1], apex[2] + bs, tipGold, tipGold, 0);
    }
  }
  const gy = (yb + yt) / 2, gs = 0.6, out = 0.35;
  const gem = (x0, z0, x1, z1, col) => p.box(x0, gy - gs, z0, x1, gy + gs, z1, col, col, 0);
  gem(-gs, -half - out - gs, gs, -half + gs, 0xFFE0304A);
  gem(-2.9 - 0.45, -half - out - 0.45, -2.9 + 0.45, -half + 0.45, 0xFF2FBF71);
  gem(2.9 - 0.45, -half - out - 0.45, 2.9 + 0.45, -half + 0.45, 0xFF2FBF71);
  gem(-gs, half - gs, gs, half + out + gs, 0xFFE0304A);
  gem(-half - out - gs, -gs, -half + gs, gs, 0xFF3A7BEB);
  gem(half - gs, -gs, half + out + gs, gs, 0xFF3A7BEB);
  return c;
}

// Top hat: brim, tall crown, red band with a gold buckle; worn at a slight angle.
function topHat() {
  const c = new Cosmetic("hat_top", "Top Hat", "hats", { anchor: "head", defaultColor: "#1E1E26",
    description: "A tall classy top hat - the band stays red." });
  const p = c.part("hat", { pivot: [0, -8, 0], rot: [-4, 0, 7] });
  const body = 0xFFFFFFFF, under = argb(1, shade(0xffffff, 0.7));
  ring(p, { r0: 0, r1: 6.4, y0: -0.45, y1: -0.45, segs: 32, top: body, tint: 1 });          // brim top
  ring(p, { r0: 6.4, r1: 0, y0: 0, y1: 0, segs: 32, top: under, tint: 1 });                 // brim underside
  ring(p, { r0: 6.4, r1: 6.4, y0: -0.45, y1: 0, segs: 32, top: under, tint: 1 });           // brim edge
  ring(p, { r0: 4.3, r1: 4.3, y0: -8.2, y1: -0.45, segs: 28, top: body, bottom: body, tint: 1 }); // crown wall
  ring(p, { r0: 0, r1: 4.3, y0: -8.2, y1: -8.2, segs: 28, top: body, tint: 1 });           // crown top
  ring(p, { r0: 4.4, r1: 4.4, y0: -2.3, y1: -0.5, segs: 28, top: 0xFFB3243A, tint: 0 });   // band
  p.box(-0.9, -2.4, -4.75, 0.9, -0.4, -4.35, 0xFFE7B43C, 0xFFB98620, 0);                  // buckle
  p.box(-0.45, -1.95, -4.85, 0.45, -0.85, -4.7, 0xFFB3243A, 0xFFB3243A, 0);
  return c;
}

// China hat: shallow cone with a lighter tip, faint ribs and a brim edge. Spins very slowly.
function chinaHat() {
  const c = new Cosmetic("hat_china", "China Hat", "hats", { anchor: "head", defaultColor: "#E8C77A",
    description: "The classic see-through PvP cone." });
  const p = c.part("cone", { render: "translucent", anim: [{ type: "spin", axis: "y", speed: 0.6 }] });
  const segs = 32, radius = 7.5, baseY = -8.6, tipY = -12.4, brim = 0.45;
  for (let i = 0; i < segs; i++) {
    const a0 = Math.PI * 2 * i / segs, a1 = Math.PI * 2 * (i + 1) / segs, rib = i % 2 === 0 ? 1 : 0.88;
    const p0 = [Math.cos(a0) * radius, baseY, Math.sin(a0) * radius], p1 = [Math.cos(a1) * radius, baseY, Math.sin(a1) * radius];
    p.tri([0, tipY, 0], p1, p0, argb(0.84, shade(mix(0xffffff, 0xffffff, 1), rib)), argb(0.84, shade(0xd9d9d9, rib)), 1);
    p.quad([...p0, 0, 0], [...p1, 1, 0], [p1[0], baseY + brim, p1[2], 1, 1], [p0[0], baseY + brim, p0[2], 0, 1], argb(0.92, 0xb3b3b3), argb(0.92, 0xb3b3b3), 1);
  }
  return c;
}

// Beanie: knitted cap with a ribbed cuff and a fluffy pom-pom.
function beanie() {
  const c = new Cosmetic("hat_beanie", "Beanie", "hats", { anchor: "head", defaultColor: "#D94848",
    description: "Cosy knitted beanie with a pom-pom." });
  const p = c.part("beanie", {});
  const knit = 0xFFFFFFFF, dark = argb(1, shade(0xffffff, 0.82));
  // ribbed cuff: alternating light/dark strips around the head
  const y0 = -8.4, y1 = -5.7, r = 4.38;
  for (let i = -4; i < 4; i++) {
    const col = i % 2 === 0 ? knit : dark;
    p.box(i, y0, -r, i + 1, y1, -r + 0.5, col, col, 1);       // front
    p.box(i, y0, r - 0.5, i + 1, y1, r, col, col, 1);         // back
    p.box(-r, y0, i, -r + 0.5, y1, i + 1, col, col, 1);       // right
    p.box(r - 0.5, y0, i, r, y1, i + 1, col, col, 1);         // left
  }
  p.box(-4.2, -9.7, -4.2, 4.2, -8.4, 4.2, knit, dark, 1);     // cap
  p.box(-3.5, -10.5, -3.5, 3.5, -9.7, 3.5, knit, knit, 1);    // rounded top
  const pom = argb(1, 0xf2f2f2);
  p.box(-1.1, -12.6, -1.1, 1.1, -10.4, 1.1, pom, pom, 1);    // pom-pom
  p.box(-1.5, -12.2, -0.7, 1.5, -10.8, 0.7, pom, pom, 1);
  p.box(-0.7, -12.2, -1.5, 0.7, -10.8, 1.5, pom, pom, 1);
  p.box(-0.8, -13.0, -0.8, 0.8, -12.4, 0.8, pom, pom, 1);
  return c;
}

export default [crown(), topHat(), chinaHat(), beanie()];
