// Cosmetic preview: renders a Minecraft-style player with Lmg Client cosmetics using the same
// JSON, maths and transform order as CosmeticRenderer.java, so designs can be checked without
// launching the game.
//
// Serve the repo root and open: tools/cosmetic-preview/index.html?view=back&items=wings_angel,hat_crown&cape=cape_royal.png
// Params: view=front|back|side|three|threefront, t=ticks, items=<ids>, colors=<id>:#hex,...,
//         cape=0|1|<texture.png>, moving=0..1, crouch=1, player=1|head|0, bg=#hex|none, dist, aty
import * as THREE from "three";

const P = new URLSearchParams(location.search);
const TEX_ROOT = "../../src/main/resources/assets/lmgclient/textures/cosmetic/";
const COLORS = { WHITE: 0xF6F6FF, BLACK: 0x24222B, GOLD: 0xFFD36A, PINK: 0xFFB3CF, ACCENT: 0xB79CFF, RAINBOW: 0x9FD8FF };
const colorOf = name => (name && name !== "OFF") ? (COLORS[name] ?? 0xffffff) : null;
const T = Number(P.get("t") ?? 30);
const MOVING = Number(P.get("moving") ?? 0);
const CROUCH = P.get("crouch") === "1";

const renderer = new THREE.WebGLRenderer({ antialias: true, preserveDrawingBuffer: true, alpha: true });
renderer.setPixelRatio(1);
renderer.setSize(innerWidth, innerHeight);
renderer.outputColorSpace = THREE.SRGBColorSpace;
document.body.appendChild(renderer.domElement);
const scene = new THREE.Scene();
if (P.get("bg") === "none") document.documentElement.style.background = document.body.style.background = "transparent";
scene.background = P.get("bg") === "none" ? null : new THREE.Color(P.get("bg") || "#5b6b80");
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
  quad(m, a, b, c, d, u0, v0, u1, v1, argb, argb2, uvs) {
    // two triangles a-b-c, a-c-d ; uv: a(u0,v0) b(u1,v0) c(u1,v1) d(u0,v1) unless explicit uvs given
    const bottom = argb2 ?? argb;
    const U = uvs ?? [[u0, v0], [u1, v0], [u1, v1], [u0, v1]];
    const V = [[a, U[0][0], U[0][1], argb], [b, U[1][0], U[1][1], argb], [c, U[2][0], U[2][1], bottom], [d, U[3][0], U[3][1], bottom]];
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
const PLAYER = P.get("player") || "1";   // 1 = whole player, head = just the head, 0 = hidden (icons)
body.box(headPose.m, -4, -8, -4, 4, 0, 4, 0xFFC69C6D);                        // head
body.box(headPose.m, -4.01, -8.01, -4.01, 4.01, -5, 4.01, 0xFF3B2A1E);        // hair
if (PLAYER === "1") body.box(bodyPose.m, -4, 0, -2, 4, 12, 2, 0xFF2D3E6B);   // shirt
const armSwing = Math.sin(T * 0.6662) * 0.6 * MOVING;
for (const side of PLAYER === "1" ? [-1, 1] : []) {
  const arm = new Pose(); arm.translate(side * 5, 2, 0); arm.rotX(side * armSwing);
  body.box(arm.m, side < 0 ? -3 : -1, -2, -2, side < 0 ? 1 : 3, 10, 2, 0xFFC69C6D);
  const leg = new Pose(); leg.translate(side * 1.9, 12, 0); leg.rotX(-side * armSwing);
  body.box(leg.m, -2, 0, -2, 2, 12, 2, 0xFF1E2433);
}
if (PLAYER !== "0") root.add(body.mesh(solid));

// ---- cosmetics: mirror of CosmeticsLayer.java ----
const add = (geo, material) => root.add(geo.mesh(material));

const CAPE = P.get("cape");
if (CAPE && CAPE !== "0") {
  const g = new Geo(); const p = new Pose();
  p.translate(0, 0, 2); p.rotX(rad(6 + 8 * MOVING));
  // vanilla cape box: x -5..5, y 0..16, z -1..0 ; outer face uses (1,1)-(11,17) of a 64x32 texture
  const W = 64, H = 32;
  g.quad(p.m, [5, 0, 0], [-5, 0, 0], [-5, 16, 0], [5, 16, 0], 1 / W, 1 / H, 11 / W, 17 / H, 0xFFFFFFFF);
  g.quad(p.m, [-5, 0, -1], [5, 0, -1], [5, 16, -1], [-5, 16, -1], 12 / W, 1 / H, 22 / W, 17 / H, 0xFFFFFFFF);
  add(g, MAT.cutout(CAPE === "1" ? "cape.png" : CAPE));
}

// ---- cosmetics from JSON (same format + maths as CosmeticRenderer.java) ----
const COS_ROOT = "../../src/main/resources/assets/lmgclient/cosmetics/";
const ITEMS = (P.get("items") || "").split(",").filter(Boolean);
const COLOR_OVERRIDES = Object.fromEntries((P.get("colors") || "").split(",").filter(Boolean).map(kv => kv.split(":")));

function animValue(a, t, m) {
  switch (a.type) {
    case "wave": return (a.offset + a.movingOffset * m) + (a.amp + a.movingAmp * m) * Math.sin(t * (a.speed + a.movingSpeed * m) + a.phase);
    case "spin": return t * a.speed;
    case "bob": return a.amp * Math.sin(t * a.speed + a.phase);
    case "twitch": { const ph = ((t + a.offset) % a.period + a.period) % a.period; return ph < a.duration ? a.amp * Math.sin(ph / a.duration * Math.PI) : 0; }
  }
  return 0;
}

function renderCosmetic(cos, tintRgb) {
  const anchor = cos.anchor === "head" ? headPose.m : cos.anchor === "body" ? bodyPose.m : new THREE.Matrix4();
  const mats = [];
  const moving = Math.min(1, MOVING);
  cos.parts.forEach((part, i) => {
    const m = (part.parent >= 0 ? mats[part.parent] : anchor).clone();
    const rot = [...part.rot];
    if (CROUCH && part.crouchRot) for (let k = 0; k < 3; k++) rot[k] += part.crouchRot[k];
    const piv = [...part.pivot];
    for (const a of part.anim) {
      const k = { x: 0, y: 1, z: 2 }[a.axis];
      if (a.type === "bob") piv[k] += animValue(a, T, moving); else rot[k] += animValue(a, T, moving);
    }
    m.multiply(new THREE.Matrix4().makeTranslation(piv[0], piv[1], piv[2]));
    m.multiply(new THREE.Matrix4().makeRotationY(rad(rot[1])));
    m.multiply(new THREE.Matrix4().makeRotationZ(rad(rot[2])));
    m.multiply(new THREE.Matrix4().makeRotationX(rad(rot[0])));
    if (part.scale !== 1) m.multiply(new THREE.Matrix4().makeScale(part.scale, part.scale, part.scale));
    mats.push(m);
    if (!part.quads.length) return;
    const g = new Geo();
    for (const q of part.quads) {
      const col = (c, tint) => {
        if (!tint) return c >>> 0;
        const r = Math.round(((c >> 16) & 255) * ((tintRgb >> 16) & 255) / 255);
        const gg = Math.round(((c >> 8) & 255) * ((tintRgb >> 8) & 255) / 255);
        const b = Math.round((c & 255) * (tintRgb & 255) / 255);
        return (((c >>> 24) << 24) | (r << 16) | (gg << 8) | b) >>> 0;
      };
      const top = col(q[20], q[22]), bottom = col(q[21], q[22]);
      g.quad(m, [q[0], q[1], q[2]], [q[5], q[6], q[7]], [q[10], q[11], q[12]], [q[15], q[16], q[17]], 0, 0, 1, 1, top, bottom, [[q[3], q[4]], [q[8], q[9]], [q[13], q[14]], [q[18], q[19]]]);
    }
    const tex = part.texture || "white.png";
    root.add(g.mesh(part.render === "emissive" ? MAT.emissive(tex) : part.render === "translucent" ? MAT.translucent(tex) : MAT.cutout(tex)));
  });
}

const loaded = Promise.all(ITEMS.map(id => fetch(COS_ROOT + id + ".json").then(r => r.json()))).then(list => {
  for (const cos of list) {
    const c = COLOR_OVERRIDES[cos.id] || cos.defaultColor;
    renderCosmetic(cos, parseInt(String(c).replace("#", ""), 16));
  }
});

// ---- camera ----
const cam = new THREE.PerspectiveCamera(32, innerWidth / innerHeight, 0.05, 50);
const view = P.get("view") || "three";
const at = new THREE.Vector3(0, Number(P.get("aty") || 1.15), 0);
const dist = Number(P.get("dist") || 4.2);
const dirs = { front: [0, 0.15, -1], back: [0, 0.2, 1], side: [1, 0.15, 0], three: [0.75, 0.3, 0.9], threefront: [0.8, 0.25, -0.9] };
const d = new THREE.Vector3(...dirs[view]).normalize().multiplyScalar(dist);
cam.position.copy(at).add(d); cam.lookAt(at);
loaded.then(() => setTimeout(() => { renderer.render(scene, cam); window.READY = true; }, 700));




