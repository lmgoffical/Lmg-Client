// Building blocks for Lmg Client cosmetics.
//
// A cosmetic is a list of PARTS. Each part has a pivot, a base rotation, optional animations and
// a list of QUADS (4 corners: x,y,z,u,v) in MODEL PIXELS (y points DOWN, front of the player = -z).
// The same JSON is drawn by the mod (CosmeticRenderer.java) and the browser preview, so whatever
// you see in tools/cosmetic-preview is what you get in game.
//
// Part transform:  parent * translate(pivot + bob) * rotY * rotZ * rotX * scale   (angles in degrees)

export const rgb = (hex) => typeof hex === "number" ? hex : parseInt(hex.replace("#", ""), 16);
export const argb = (a, color) => (((Math.round(a * 255) & 255) << 24) | (rgb(color) & 0xffffff)) >>> 0;

export function mix(a, b, t) {
  a = rgb(a); b = rgb(b);
  const c = (sh) => Math.round(((a >> sh) & 255) + ((((b >> sh) & 255) - ((a >> sh) & 255)) * t));
  return (c(16) << 16) | (c(8) << 8) | c(0);
}
export const shade = (color, f) => mix(0x000000, color, f);

export class Part {
  constructor(name, opts = {}) {
    this.name = name;
    this.parent = opts.parent ?? -1;
    this.pivot = opts.pivot ?? [0, 0, 0];
    this.rot = opts.rot ?? [0, 0, 0];
    this.scale = opts.scale ?? 1;
    this.crouchRot = opts.crouchRot;
    this.anim = opts.anim ?? [];
    this.texture = opts.texture ?? null;      // null = plain white texture
    this.render = opts.render ?? "cutout";    // cutout | translucent | emissive
    this.quads = [];
  }
  // a, b, c, d = [x, y, z, u, v]; top colours a/b, bottom colours c/d; tint = multiply by the chosen colour
  quad(a, b, c, d, top = 0xffffffff, bottom = top, tint = 1) {
    this.quads.push([...a, ...b, ...c, ...d, top >>> 0, bottom >>> 0, tint]);
    return this;
  }
  tri(apex, b, c, top, bottom, tint = 1) {
    return this.quad([...apex, 0.5, 0], [...apex, 0.5, 0], [...b, 1, 1], [...c, 0, 1], top, bottom, tint);
  }
  // axis-aligned box, all faces the same colours (lighting does the shading)
  box(x0, y0, z0, x1, y1, z1, top, bottom = top, tint = 1) {
    const q = (a, b, c, d) => this.quad([...a, 0, 0], [...b, 1, 0], [...c, 1, 1], [...d, 0, 1], top, bottom, tint);
    q([x0, y0, z0], [x1, y0, z0], [x1, y1, z0], [x0, y1, z0]);   // front (-z)
    q([x1, y0, z1], [x0, y0, z1], [x0, y1, z1], [x1, y1, z1]);   // back (+z)
    q([x0, y0, z1], [x0, y0, z0], [x0, y1, z0], [x0, y1, z1]);   // -x
    q([x1, y0, z0], [x1, y0, z1], [x1, y1, z1], [x1, y1, z0]);   // +x
    q([x0, y0, z1], [x1, y0, z1], [x1, y0, z0], [x0, y0, z0]);   // top (smaller y = up)
    q([x0, y1, z0], [x1, y1, z0], [x1, y1, z1], [x0, y1, z1]);   // bottom
    return this;
  }
  // a textured plane in the XY plane (z = 0), from (x0,y0) to (x1,y1) with uv rect
  plane(x0, y0, x1, y1, u0 = 0, v0 = 0, u1 = 1, v1 = 1, color = 0xffffffff, tint = 1) {
    return this.quad([x0, y0, 0, u0, v0], [x1, y0, 0, u1, v0], [x1, y1, 0, u1, v1], [x0, y1, 0, u0, v1], color, color, tint);
  }
  toJSON() {
    const o = { name: this.name, parent: this.parent, pivot: this.pivot, rot: this.rot, scale: this.scale,
      render: this.render, texture: this.texture, anim: this.anim, quads: this.quads.map(q => q.map(n => Number.isInteger(n) ? n : Math.round(n * 1000) / 1000)) };
    if (this.crouchRot) o.crouchRot = this.crouchRot;
    return o;
  }
}

export class Cosmetic {
  constructor(id, name, category, opts = {}) {
    this.id = id; this.name = name; this.category = category;
    this.anchor = opts.anchor ?? "head";          // head | body | root
    this.tintable = opts.tintable ?? true;
    this.defaultColor = opts.defaultColor ?? "#FFFFFF";
    this.hideWithElytra = opts.hideWithElytra ?? false;
    this.description = opts.description ?? "";
    this.capeTexture = opts.capeTexture;
    this.parts = [];
  }
  part(name, opts) { const p = new Part(name, opts); this.parts.push(p); return p; }
  index(part) { return this.parts.indexOf(part); }
  toJSON() {
    return { format: 1, id: this.id, name: this.name, category: this.category, description: this.description,
      anchor: this.anchor, tintable: this.tintable, defaultColor: this.defaultColor, hideWithElytra: this.hideWithElytra,
      ...(this.capeTexture ? { capeTexture: this.capeTexture } : {}),
      parts: this.parts.map(p => p.toJSON()) };
  }
}

// ---- animation helpers (angles in degrees, speed in radians per tick) ----
export const wave = (axis, amp, speed, extra = {}) => ({ type: "wave", axis, amp, speed, phase: 0, movingAmp: 0, movingSpeed: 0, offset: 0, movingOffset: 0, ...extra });
export const spin = (axis, degPerTick) => ({ type: "spin", axis, speed: degPerTick });
export const bob = (axis, amp, speed, phase = 0) => ({ type: "bob", axis, amp, speed, phase });
export const twitch = (axis, amp, period, duration, offset = 0) => ({ type: "twitch", axis, amp, period, duration, offset });

// ring of quads around the Y axis (used for halos, brims, bands)
export function ring(part, { r0, r1, y0, y1, segs = 32, top, bottom = top, tint = 1, start = 0, end = Math.PI * 2 }) {
  for (let i = 0; i < segs; i++) {
    const a0 = start + (end - start) * i / segs, a1 = start + (end - start) * (i + 1) / segs;
    const p = (r, a, y) => [Math.cos(a) * r, y, Math.sin(a) * r];
    if (r0 === r1) {
      part.quad([...p(r0, a0, y0), 0, 0], [...p(r0, a1, y0), 1, 0], [...p(r0, a1, y1), 1, 1], [...p(r0, a0, y1), 0, 1], top, bottom, tint);
    } else {
      part.quad([...p(r0, a0, y0), 0, 0], [...p(r0, a1, y0), 1, 0], [...p(r1, a1, y1), 1, 1], [...p(r1, a0, y1), 0, 1], top, bottom, tint);
    }
  }
}

// a solid bar (rectangular prism) from p0 to p1 in the XY plane: width w (in XY), depth d (in z)
export function bar(part, p0, p1, w, d, top, bottom = top, tint = 1) {
  const dx = p1[0] - p0[0], dy = p1[1] - p0[1], len = Math.hypot(dx, dy) || 1;
  const nx = -dy / len * w / 2, ny = dx / len * w / 2;        // sideways in XY
  const z0 = (p0[2] ?? 0) - d / 2, z1 = (p0[2] ?? 0) + d / 2;
  const zz0 = (p1[2] ?? 0) - d / 2, zz1 = (p1[2] ?? 0) + d / 2;
  const A = [p0[0] + nx, p0[1] + ny], B = [p0[0] - nx, p0[1] - ny], C = [p1[0] - nx, p1[1] - ny], D = [p1[0] + nx, p1[1] + ny];
  const q = (a, b, c, e) => part.quad([...a, 0, 0], [...b, 1, 0], [...c, 1, 1], [...e, 0, 1], top, bottom, tint);
  q([A[0], A[1], z0], [B[0], B[1], z0], [C[0], C[1], zz0], [D[0], D[1], zz0]);   // front
  q([B[0], B[1], z1], [A[0], A[1], z1], [D[0], D[1], zz1], [C[0], C[1], zz1]);   // back
  q([A[0], A[1], z1], [A[0], A[1], z0], [D[0], D[1], zz0], [D[0], D[1], zz1]);   // side
  q([B[0], B[1], z0], [B[0], B[1], z1], [C[0], C[1], zz1], [C[0], C[1], zz0]);   // side
  q([A[0], A[1], z0], [A[0], A[1], z1], [B[0], B[1], z1], [B[0], B[1], z0]);     // cap
  q([D[0], D[1], zz1], [D[0], D[1], zz0], [C[0], C[1], zz0], [C[0], C[1], zz1]); // cap
  return part;
}

// rotate a 2D point (degrees) - handy for laying out fans of bones/feathers
export const polar = (len, deg) => [Math.cos(deg * Math.PI / 180) * len, Math.sin(deg * Math.PI / 180) * len];
