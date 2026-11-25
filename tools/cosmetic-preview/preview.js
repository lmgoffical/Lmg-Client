// Cosmetic preview: renders a Minecraft-style player with Lmg Client cosmetics using the same
// textures, sizes (model pixels) and transform order as CosmeticsLayer.java, so designs can be
// checked without launching the game.
//
// Serve the repo root and open: tools/cosmetic-preview/index.html?view=back&t=40&wings=WHITE&halo=GOLD
// Params: view=front|back|side|three, t=ticks, wings/halo/ears/hat/shield=<colour name or OFF>,
//         crown=1, cape=1, moving=0..1, crouch=1
import * as THREE from "three";

const P = new URLSearchParams(location.search);
const TEX_ROOT = "../../src/main/resources/assets/lmgclient/textures/cosmetic/";
const COLORS = { WHITE: 0xF6F6FF, BLACK: 0x24222B, GOLD: 0xFFD36A, PINK: 0xFFB3CF, ACCENT: 0xB79CFF, RAINBOW: 0x9FD8FF };
const colorOf = name => (name && name !== "OFF") ? (COLORS[name] ?? 0xffffff) : null;
const T = Number(P.get("t") ?? 30);
const MOVING = Number(P.get("moving") ?? 0);
const CROUCH = P.get("crouch") === "1";

const renderer = new THREE.WebGLRenderer({ antialias: true, preserveDrawingBuffer: true });
renderer.setPixelRatio(1);
renderer.setSize(innerWidth, innerHeight);
renderer.outputColorSpace = THREE.SRGBColorSpace;
document.body.appendChild(renderer.domElement);
const scene = new THREE.Scene();
scene.background = new THREE.Color(P.get("bg") || "#5b6b80");
scene.add(new THREE.AmbientLight(0xffffff, 1.4));
const sun = new THREE.DirectionalLight(0xffffff, 1.6); sun.position.set(-2, 4, -3); scene.add(sun);
const sun2 = new THREE.DirectionalLight(0xffffff, 0.8); sun2.position.set(2, 3, 4); scene.add(sun2);

const loader = new THREE.TextureLoader();
function tex(name) {
  const t = loader.load(TEX_ROOT + name);
  t.magFilter = THREE.NearestFilter; t.minFilter = THREE.NearestMipmapLinearFilter;
  t.colorSpace = THREE.SRGBColorSpace;
  return t;
}
const MAT = {
  cutout: (t) => new THREE.MeshLambertMaterial({ map: tex(t), vertexColors: true, alphaTest: 0.1, side: THREE.DoubleSide }),
  translucent: (t) => new THREE.MeshLambertMaterial({ map: tex(t), vertexColors: true, transparent: true, side: THREE.DoubleSide, depthWrite: false }),
  emissive: (t) => new THREE.MeshBasicMaterial({ map: tex(t), vertexColors: true, transparent: true, side: THREE.DoubleSide, depthWrite: false }),
  solid: () => new THREE.MeshLambertMaterial({ map: tex("white.png"), vertexColors: true, side: THREE.DoubleSide }),
};

// ---- a tiny PoseStack ----
class Pose {
  constructor() { this.stack = [new THREE.Matrix4()]; }
  get m() { return this.stack[this.stack.length - 1]; }
  push() { this.stack.push(this.m.clone()); }
  pop() { this.stack.pop(); }
  translate(x, y, z) { this.m.multiply(new THREE.Matrix4().makeTranslation(x, y, z)); }
  rotX(a) { this.m.multiply(new THREE.Matrix4().makeRotationX(a)); }
  rotY(a) { this.m.multiply(new THREE.Matrix4().makeRotationY(a)); }
  rotZ(a) { this.m.multiply(new THREE.Matrix4().makeRotationZ(a)); }
  scale(s) { this.m.multiply(new THREE.Matrix4().makeScale(s, s, s)); }
}

// geometry builder: collects quads (in model pixels) and bakes the current pose in
class Geo {
  constructor() { this.pos = []; this.uv = []; this.col = []; }
  vert(m, x, y, z, u, v, argb) {
    const p = new THREE.Vector3(x, y, z).applyMatrix4(m);
    this.pos.push(p.x, p.y, p.z); this.uv.push(u, 1 - v);
    const a = ((argb >>> 24) & 255) / 255, r = ((argb >> 16) & 255) / 255, g = ((argb >> 8) & 255) / 255, b = (argb & 255) / 255;
    this.col.push(r, g, b, a);
  }
  quad(m, a, b, c, d, u0, v0, u1, v1, argb, argb2) {
    // two triangles a-b-c, a-c-d ; uv: a(u0,v0) b(u1,v0) c(u1,v1) d(u0,v1)
    const bottom = argb2 ?? argb;
    const V = [[a, u0, v0, argb], [b, u1, v0, argb], [c, u1, v1, bottom], [d, u0, v1, bottom]];
    for (const i of [0, 1, 2, 0, 2, 3]) this.vert(m, V[i][0][0], V[i][0][1], V[i][0][2], V[i][1], V[i][2], V[i][3]);
  }
  box(m, x0, y0, z0, x1, y1, z1, argb, shade = true) {
    const s = (f) => shade ? shadeArgb(argb, f) : argb;
    this.quad(m, [x0, y1, z0], [x1, y1, z0], [x1, y0, z0], [x0, y0, z0], 0, 0, 1, 1, s(0.8));
    this.quad(m, [x1, y1, z1], [x0, y1, z1], [x0, y0, z1], [x1, y0, z1], 0, 0, 1, 1, s(0.8));
    this.quad(m, [x0, y1, z1], [x0, y1, z0], [x0, y0, z0], [x0, y0, z1], 0, 0, 1, 1, s(0.6));
    this.quad(m, [x1, y1, z0], [x1, y1, z1], [x1, y0, z1], [x1, y0, z0], 0, 0, 1, 1, s(0.6));
    this.quad(m, [x0, y0, z0], [x1, y0, z0], [x1, y0, z1], [x0, y0, z1], 0, 0, 1, 1, s(1.0)); // top (y down = smaller y is up)
    this.quad(m, [x0, y1, z1], [x1, y1, z1], [x1, y1, z0], [x0, y1, z0], 0, 0, 1, 1, s(0.5));
  }
  mesh(material) {
    const g = new THREE.BufferGeometry();
    g.setAttribute("position", new THREE.Float32BufferAttribute(this.pos, 3));
    g.setAttribute("uv", new THREE.Float32BufferAttribute(this.uv, 2));
    g.setAttribute("color", new THREE.Float32BufferAttribute(this.col, 4));
    g.computeVertexNormals();
    return new THREE.Mesh(g, material);
  }
}
function shadeArgb(argb, f) {
  const a = argb >>> 24, r = Math.round(((argb >> 16) & 255) * f), g = Math.round(((argb >> 8) & 255) * f), b = Math.round((argb & 255) * f);
  return ((a << 24) | (r << 16) | (g << 8) | b) >>> 0;
}
const rad = d => d * Math.PI / 180;
const argb = (a, rgb) => ((Math.round(a * 255) << 24) | rgb) >>> 0;

// ---- the player (model space: pixels, y down, front faces -z) ----
const root = new THREE.Group();
root.rotation.z = Math.PI;               // == scale(-1,-1,1) like LivingEntityRenderer
root.scale.setScalar(1 / 16);
root.position.y = 1.501;
scene.add(root);
const solid = new THREE.MeshLambertMaterial({ vertexColors: true, map: tex("white.png") });

const headPose = new Pose();             // head pivot at the neck (0,0,0)
const bodyPose = new Pose();             // body pivot at the neck too
if (CROUCH) { bodyPose.rotX(0.5); headPose.translate(0, 4.2, 0); }

const body = new Geo();
body.box(headPose.m, -4, -8, -4, 4, 0, 4, 0xFFC69C6D);                        // head
body.box(headPose.m, -4.01, -8.01, -4.01, 4.01, -5, 4.01, 0xFF3B2A1E);        // hair
body.box(bodyPose.m, -4, 0, -2, 4, 12, 2, 0xFF2D3E6B);                        // shirt
const armSwing = Math.sin(T * 0.6662) * 0.6 * MOVING;
for (const side of [-1, 1]) {
  const arm = new Pose(); arm.translate(side * 5, 2, 0); arm.rotX(side * armSwing);
  body.box(arm.m, side < 0 ? -3 : -1, -2, -2, side < 0 ? 1 : 3, 10, 2, 0xFFC69C6D);
  const leg = new Pose(); leg.translate(side * 1.9, 12, 0); leg.rotX(-side * armSwing);
  body.box(leg.m, -2, 0, -2, 2, 12, 2, 0xFF1E2433);
}
root.add(body.mesh(solid));

// ---- cosmetics: mirror of CosmeticsLayer.java ----
const add = (geo, material) => root.add(geo.mesh(material));

if (P.get("cape") === "1") {
  const g = new Geo(); const p = new Pose();
  p.translate(0, 0, 2); p.rotX(rad(6 + 8 * MOVING));
  // vanilla cape box: x -5..5, y 0..16, z -1..0 ; outer face uses (1,1)-(11,17) of a 64x32 texture
  const W = 64, H = 32;
  g.quad(p.m, [5, 0, 0], [-5, 0, 0], [-5, 16, 0], [5, 16, 0], 1 / W, 1 / H, 11 / W, 17 / H, 0xFFFFFFFF);
  g.quad(p.m, [-5, 0, -1], [5, 0, -1], [5, 16, -1], [-5, 16, -1], 12 / W, 1 / H, 22 / W, 17 / H, 0xFFFFFFFF);
  add(g, MAT.cutout("cape.png"));
}

const wings = colorOf(P.get("wings"));
if (wings !== null) wingsCos(root, bodyPose, wings);
const halo = colorOf(P.get("halo"));
if (halo !== null) haloCos(root, headPose, halo);
if (P.get("crown") === "1") crownCos(root, headPose);
const ears = colorOf(P.get("ears"));
if (ears !== null) earsCos(root, headPose, ears);
const hat = colorOf(P.get("hat"));
if (hat !== null) hatCos(root, headPose, hat);
const shield = colorOf(P.get("shield"));
if (shield !== null) shieldCos(root, shield);

// ---- camera ----
const cam = new THREE.PerspectiveCamera(32, innerWidth / innerHeight, 0.05, 50);
const view = P.get("view") || "three";
const at = new THREE.Vector3(0, Number(P.get("aty") || 1.15), 0);
const dist = Number(P.get("dist") || 4.2);
const dirs = { front: [0, 0.15, -1], back: [0, 0.2, 1], side: [1, 0.15, 0], three: [0.75, 0.3, 0.9], threefront: [0.8, 0.25, -0.9] };
const d = new THREE.Vector3(...dirs[view]).normalize().multiplyScalar(dist);
cam.position.copy(at).add(d); cam.lookAt(at);
setTimeout(() => { renderer.render(scene, cam); window.READY = true; }, 600);




// =====================================================================================
// Cosmetics (keep in sync with src/main/java/dev/lmg/client/cosmetic/CosmeticsLayer.java)
// =====================================================================================

// ---- colour helpers (same as Java) ----
function mixRgb(a, b, t) {
  const c = (sh) => Math.round(((a >> sh) & 255) + ((((b >> sh) & 255) - ((a >> sh) & 255)) * t));
  return (c(16) << 16) | (c(8) << 8) | c(0);
}
function dark(rgb, f) { return mixRgb(0x000000, rgb, f); }

// Wings: two hinged panels per side (inner half + outer half that folds), plus a darker back
// layer for thickness. Swept back and up; flapping bends the outer half more than the inner.
function wingsCos(root, bodyPose, rgb) {
  const moving = Math.min(1, MOVING * 1.5);
  const phase = T * (0.11 + 0.12 * moving);
  const flap = Math.sin(phase);                      // -1..1
  const open = CROUCH ? 14 : 32 + 8 * moving;        // degrees swept back from the body plane
  const size = 20, hinge = 0.42, y0 = -0.09 * size, y1 = y0 + size;
  for (const sgn of [-1, 1]) {
    for (const under of [true, false]) {              // darker underside (nearer the body), then the top layer
      const p = new Pose(); p.m.copy(bodyPose.m);
      p.translate(sgn * 1.5, 1.2, under ? 2.6 : 3.1);
      p.rotY(rad(-sgn * (open + flap * 12)));
      p.rotZ(rad(-sgn * (30 + flap * 8)));
      if (under) p.scale(0.96);
      const color = argb(1, under ? dark(rgb, 0.6) : rgb);
      const g = new Geo();
      // inner panel (shoulder -> wrist)
      const xi = sgn * size * hinge;
      g.quad(p.m, [0, y0, 0], [xi, y0, 0], [xi, y1, 0], [0, y1, 0], 0, 0, hinge, 1, color);
      // outer panel, hinged at the wrist, folds back further on the upstroke
      p.translate(xi, 0, 0);
      p.rotY(rad(-sgn * (12 + flap * 16)));
      const xo = sgn * size * (1 - hinge);
      g.quad(p.m, [0, y0, 0], [xo, y0, 0], [xo, y1, 0], [0, y1, 0], hinge, 0, 1, 1, color);
      root.add(g.mesh(MAT.translucent("wing.png")));
    }
  }
}

// Halo: a solid glowing ring (with thickness) plus a soft glow disc, tilted, bobbing, spinning.
function haloCos(root, headPose, rgb) {
  const bob = Math.sin(T * 0.08) * 0.5;
  const p = new Pose(); p.m.copy(headPose.m);
  p.translate(0, -10.8 + bob, 0); p.rotX(rad(10)); p.rotY(T * 0.015);
  const R = 4.6, tw = 0.55, th = 0.45, segs = 40, g = new Geo();
  const light = argb(1, mixRgb(rgb, 0xFFFFFF, 0.45)), mid = argb(1, rgb), low = argb(1, dark(rgb, 0.75));
  for (let i = 0; i < segs; i++) {
    const a0 = Math.PI * 2 * i / segs, a1 = Math.PI * 2 * (i + 1) / segs;
    const c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
    const ro = R + tw, ri = R - tw;
    g.quad(p.m, [c0 * ri, -th, s0 * ri], [c1 * ri, -th, s1 * ri], [c1 * ro, -th, s1 * ro], [c0 * ro, -th, s0 * ro], 0, 0, 1, 1, light);   // top
    g.quad(p.m, [c0 * ro, -th, s0 * ro], [c1 * ro, -th, s1 * ro], [c1 * ro, th, s1 * ro], [c0 * ro, th, s0 * ro], 0, 0, 1, 1, mid, low); // outer
    g.quad(p.m, [c0 * ri, -th, s0 * ri], [c1 * ri, -th, s1 * ri], [c1 * ri, th, s1 * ri], [c0 * ri, th, s0 * ri], 0, 0, 1, 1, mid, low); // inner
    g.quad(p.m, [c0 * ri, th, s0 * ri], [c1 * ri, th, s1 * ri], [c1 * ro, th, s1 * ro], [c0 * ro, th, s0 * ro], 0, 0, 1, 1, low);        // bottom
  }
  root.add(g.mesh(MAT.emissive("white.png")));
  const glow = new Geo(), r = 8;
  glow.quad(p.m, [-r, 0, -r], [r, 0, -r], [r, 0, r], [-r, 0, r], 0, 0, 1, 1, argb(0.55, rgb));
  root.add(glow.mesh(MAT.emissive("halo.png")));
}

// Crown: a thick gold band with raised spikes, gold balls on top and gems on the band.
function crownCos(root, headPose) {
  const m = headPose.m, g = new Geo();
  const half = 4.55, yb = -7.6, yt = -9.9, t = 0.6;
  const gold = 0xFFE7B43C, goldDark = 0xFFB98620, tipGold = 0xFFFFE9A0;
  // band (four thick sides)
  g.box(m, -half, yt, -half, half, yb, -half + t, gold);
  g.box(m, -half, yt, half - t, half, yb, half, gold);
  g.box(m, -half, yt, -half + t, -half + t, yb, half - t, gold);
  g.box(m, half - t, yt, -half + t, half, yb, half - t, gold);
  g.box(m, -half - 0.15, yb - 0.5, -half - 0.15, half + 0.15, yb, half + 0.15, goldDark); // lower rim
  // spikes: 3 per side (middle one taller), pyramids with a ball on top
  const sides = [[0, -1], [0, 1], [-1, 0], [1, 0]];
  for (const [sx, sz] of sides) {
    for (const k of [-1, 0, 1]) {
      const along = k * 2.9, hgt = k === 0 ? 3.4 : 2.5, w = 0.95;
      const cx = sx !== 0 ? sx * (half - t / 2) : along;
      const cz = sz !== 0 ? sz * (half - t / 2) : along;
      const ax = sx !== 0 ? 0 : 1, az = sx !== 0 ? 1 : 0;        // axis along the side
      const b1 = [cx - ax * w, yt, cz - az * w], b2 = [cx + ax * w, yt, cz + az * w];
      const b3 = [cx + ax * w + sx * 0.3, yt, cz + az * w + sz * 0.3], b4 = [cx - ax * w + sx * 0.3, yt, cz - az * w + sz * 0.3];
      const apex = [cx + sx * 0.25, yt - hgt, cz + sz * 0.25];
      g.quad(m, apex, apex, b2, b1, 0, 0, 1, 1, gold);
      g.quad(m, apex, apex, b3, b2, 0, 0, 1, 1, goldDark);
      g.quad(m, apex, apex, b4, b3, 0, 0, 1, 1, gold);
      g.quad(m, apex, apex, b1, b4, 0, 0, 1, 1, goldDark);
      const bs = 0.45;
      g.box(m, apex[0] - bs, apex[1] - bs * 2, apex[2] - bs, apex[0] + bs, apex[1], apex[2] + bs, tipGold);
    }
  }
  // gems on the outside of the band
  const gem = (x, z, nx, nz, rgb) => {
    const s = 0.6, out = 0.35, y = (yb + yt) / 2;
    g.box(m, x - s + Math.min(0, nx) * out, y - s, z - s + Math.min(0, nz) * out, x + s + Math.max(0, nx) * out, y + s, z + s + Math.max(0, nz) * out, rgb);
  };
  gem(0, -half, 0, -1, 0xFFE0304A); gem(0, half, 0, 1, 0xFFE0304A);
  gem(-half, 0, -1, 0, 0xFF3A7BEB); gem(half, 0, 1, 0, 0xFF3A7BEB);
  root.add(g.mesh(MAT.solid()));
}

// Cat ears: 3D wedge ears with a pink inner ear; they twitch now and then.
function earsCos(root, headPose, rgb) {
  const inner = argb(1, mixRgb(rgb, 0xFFB3CF, 0.7));
  const outer = argb(1, rgb), side = argb(1, dark(rgb, 0.8));
  for (const s of [-1, 1]) {
    const phase = (T + (s > 0 ? 47 : 0)) % 110;
    const twitch = phase < 6 ? Math.sin(phase / 6 * Math.PI) * 14 : 0;
    const p = new Pose(); p.m.copy(headPose.m);
    p.translate(s * 2.3, -7.9, -0.3); p.rotZ(rad(s * (12 + twitch))); p.rotX(rad(-6));
    const m = p.m, g = new Geo();
    const bw = 1.75, h = 3.7, d = 0.6, ad = 0.2;
    const fl = [-bw, 0, -d], fr = [bw, 0, -d], bl = [-bw, 0, d], br = [bw, 0, d], af = [0, -h, -ad], ab = [0, -h, ad];
    g.quad(m, af, af, fr, fl, 0, 0, 1, 1, outer);          // front
    g.quad(m, ab, ab, bl, br, 0, 0, 1, 1, outer);          // back
    g.quad(m, af, ab, br, fr, 0, 0, 1, 1, side);           // right slope
    g.quad(m, ab, af, fl, bl, 0, 0, 1, 1, side);           // left slope
    const il = [-1.0, -0.35, -d - 0.04], ir = [1.0, -0.35, -d - 0.04], it = [0, -h * 0.72, -ad - 0.12];
    g.quad(m, it, it, ir, il, 0, 0, 1, 1, inner);          // inner ear
    root.add(g.mesh(MAT.solid()));
  }
}

// China hat: shallow cone with a lighter tip, faint ribs and a small brim edge.
function hatCos(root, headPose, rgb) {
  const segs = 32, radius = 7.5, baseY = -8.6, tipY = -12.4, brim = 0.45, g = new Geo(), m = headPose.m;
  const tip = mixRgb(rgb, 0xFFFFFF, 0.35);
  for (let i = 0; i < segs; i++) {
    const a0 = Math.PI * 2 * i / segs, a1 = Math.PI * 2 * (i + 1) / segs;
    const rib = i % 2 === 0 ? 1 : 0.9;
    const p0 = [Math.cos(a0) * radius, baseY, Math.sin(a0) * radius], p1 = [Math.cos(a1) * radius, baseY, Math.sin(a1) * radius];
    g.quad(m, [0, tipY, 0], [0, tipY, 0], p1, p0, 0, 0, 1, 1, argb(0.82, dark(tip, rib)), argb(0.82, dark(rgb, rib)));
    g.quad(m, p0, p1, [p1[0], baseY + brim, p1[2]], [p0[0], baseY + brim, p0[2]], 0, 0, 1, 1, argb(0.9, dark(rgb, 0.7)));
  }
  root.add(g.mesh(MAT.translucent("white.png")));
}

function shieldCos(root, rgb) {
  const lat = 14, lon = 28, r = 19, cy = 12, scan = Math.sin(T * 0.06), g = new Geo(), m = new THREE.Matrix4();
  const S = (th, ph) => [r * Math.sin(th) * Math.cos(ph), cy - r * Math.cos(th), r * Math.sin(th) * Math.sin(ph)];
  for (let i = 0; i < lat; i++) {
    const th0 = Math.PI * i / lat, th1 = Math.PI * (i + 1) / lat, yc = Math.cos((th0 + th1) / 2);
    let a = 0.06 + 0.30 * Math.exp(-Math.pow((yc - scan) / 0.14, 2)); if (i % 2 === 0) a += 0.03;
    for (let j = 0; j < lon; j++) {
      const p0 = Math.PI * 2 * j / lon, p1 = Math.PI * 2 * (j + 1) / lon;
      g.quad(m, S(th0, p0), S(th0, p1), S(th1, p1), S(th1, p0), 0, 0, 1, 1, argb(a, rgb));
    }
  }
  root.add(g.mesh(MAT.emissive("white.png")));
}
