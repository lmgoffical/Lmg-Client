import { Cosmetic, argb, shade, wave, twitch, fan, dquad } from "../lib.mjs";

// Body space: the neck is (0,0,0), the waist is y = 12, the back of the body is z = +2.
// A tail is a chain of segments, each one a child of the one before, so a small swing on every
// segment (each a little later than the last) adds up to a whip-like swish.

const BASE = [0, 10.6, 1.7];

function chain(c, n, { len, width, lift, curl, sway, speed, tip = null, tipFrom = n, fluffy = false, moveLift = 14 }) {
  let parent = -1;
  const parts = [];
  for (let i = 0; i < n; i++) {
    const w = typeof width === "function" ? width(i / (n - 1)) : width;
    const p = c.part("seg" + i, {
      parent,
      pivot: i === 0 ? BASE : [0, 0, len * 0.92],
      rot: [i === 0 ? lift : curl, 0, 0],
      crouchRot: i === 0 ? [-18, 0, 0] : undefined,
      anim: [
        wave("y", sway, speed, { phase: -i * 0.55, movingAmp: sway * 0.4, movingSpeed: speed }),
        ...(i === 0 ? [wave("x", 3, speed * 0.5, { movingOffset: moveLift })] : [wave("x", 1.5, speed * 0.5, { phase: -i * 0.4 })]),
      ],
    });
    const col = tip && i >= tipFrom ? tip : 0xFFFFFFFF;
    const tint = tip && i >= tipFrom ? 0 : 1;
    const h = w / 2;
    p.box(-h, -h, 0, h, h, len, col, col, tint);
    if (fluffy) {   // a few tufts so it isn't a plain tube
      const f = h * 1.18;
      p.box(-f, -h * 0.7, len * 0.2, f, h * 0.7, len * 0.8, col, col, tint);
      p.box(-h * 0.7, -f, len * 0.2, h * 0.7, f, len * 0.8, col, col, tint);
    }
    parent = c.index(p);
    parts.push(p);
  }
  return parts;
}

function cat() {
  const c = new Cosmetic("tail_cat", "Cat Tail", "tails", { anchor: "body", defaultColor: "#2B2530",
    description: "A long cat tail that swishes and curls up when you walk. Goes with Cat Ears." });
  chain(c, 9, { len: 1.7, width: (t) => 1.05 - t * 0.25, lift: 16, curl: 8, sway: 4, speed: 0.07 });
  return c;
}

function fox() {
  const c = new Cosmetic("tail_fox", "Fox Tail", "tails", { anchor: "body", defaultColor: "#E8752C",
    description: "Big fluffy fox tail with a white tip. Goes with Fox Ears." });
  const widths = [1.3, 2.0, 2.7, 3.1, 3.2, 3.0, 2.5, 1.8, 1.0];
  chain(c, 9, { len: 1.55, width: (t) => widths[Math.round(t * 8)], lift: -12, curl: 7, sway: 3, speed: 0.05,
    tip: 0xFFF7F1EA, tipFrom: 6, fluffy: true, moveLift: 18 });
  return c;
}

function devil() {
  const c = new Cosmetic("tail_devil", "Devil Tail", "tails", { anchor: "body", defaultColor: "#C2183A",
    description: "A thin, twitchy tail with a spade on the end. Goes with Devil Horns." });
  const segs = chain(c, 10, { len: 1.5, width: 0.55, lift: 4, curl: 6, sway: 5, speed: 0.09 });
  const last = segs[segs.length - 1];
  // spade: a flat diamond with a little thickness, at the end of the last segment
  const z0 = 1.2, w = 2.3, l = 4.0, th = 0.35;
  const left = [-w, 0, z0 + l * 0.42], right = [w, 0, z0 + l * 0.42], back = [0, 0, z0], tipP = [0, 0, z0 + l];
  const up = [0, -th, z0 + l * 0.42], down = [0, th, z0 + l * 0.42];
  const col = 0xFFFFFFFF, dark = argb(1, shade(0xffffff, 0.75));
  for (const mid of [up, down]) {
    last.tri(mid, left, tipP, col, col, 1); last.tri(mid, tipP, right, col, col, 1);
    last.tri(mid, right, back, dark, dark, 1); last.tri(mid, back, left, dark, dark, 1);
    last.tri(mid, tipP, left, col, col, 1); last.tri(mid, right, tipP, col, col, 1);
    last.tri(mid, back, right, dark, dark, 1); last.tri(mid, left, back, dark, dark, 1);
  }
  return c;
}

function dragon() {
  const c = new Cosmetic("tail_dragon", "Dragon Tail", "tails", { anchor: "body", defaultColor: "#3A8A5C",
    description: "A heavy scaled tail with spikes down its back. It drags low and sways slowly." });
  const segs = chain(c, 9, { len: 1.9, width: (t) => 2.4 - t * 1.7, lift: -30, curl: 5, sway: 2.5, speed: 0.04, moveLift: 10 });
  const spike = argb(1, shade(0xffffff, 0.62));
  segs.forEach((p, i) => {
    const w = 2.4 - (i / 8) * 1.7, h = w / 2, s = 0.9 + w * 0.35;
    // a fin on top of each segment, drawn both sides
    p.tri([0, -h - s, 1.2], [0, -h + 0.05, 1.7], [0, -h + 0.05, 0.1], spike, spike, 1);
    p.tri([0, -h - s, 1.2], [0, -h + 0.05, 0.1], [0, -h + 0.05, 1.7], spike, spike, 1);
    // lighter belly scales
    p.box(-h * 0.7, h - 0.02, 0.15, h * 0.7, h + 0.06, 1.75, 0xFFF0E2B0, 0xFFF0E2B0, 0);
  });
  return c;
}

function bunny() {
  const c = new Cosmetic("tail_bunny", "Bunny Tail", "tails", { anchor: "body", defaultColor: "#F2EEF4",
    description: "A round cotton puff that wiggles now and then. Goes with Bunny Ears." });
  const p = c.part("puff", { pivot: [0, 10.4, 2.4], anim: [twitch("y", 18, 90, 8), twitch("x", -10, 130, 6, 40)] });
  const col = 0xFFFFFFFF, soft = argb(1, shade(0xffffff, 0.9));
  p.box(-1.3, -1.3, 0, 1.3, 1.3, 2.2, col, soft, 1);
  p.box(-1.7, -0.9, 0.3, 1.7, 0.9, 1.9, col, soft, 1);
  p.box(-0.9, -1.7, 0.3, 0.9, 1.7, 1.9, col, soft, 1);
  p.box(-0.9, -0.9, 2.1, 0.9, 0.9, 2.6, col, soft, 1);
  return c;
}

function robot() {
  const c = new Cosmetic("tail_robot", "Robo Tail", "tails", { anchor: "body", defaultColor: "#45E0FF",
    description: "Metal links with glowing joints and a blinking light on the end." });
  let parent = -1;
  const metal = 0xFF8C93A0, dark = 0xFF4A505C;
  for (let i = 0; i < 8; i++) {
    const p = c.part("link" + i, { parent, pivot: i === 0 ? BASE : [0, 0, 1.75], rot: [i === 0 ? 10 : 7, 0, 0],
      crouchRot: i === 0 ? [-18, 0, 0] : undefined,
      anim: [wave("y", 3.5, 0.06, { phase: -i * 0.5, movingAmp: 1.5 }), ...(i === 0 ? [wave("x", 2, 0.03, { movingOffset: 12 })] : [])] });
    const w = 0.95 - i * 0.05;
    p.box(-w / 2, -w / 2, 0.25, w / 2, w / 2, 1.6, metal, dark, 0);
    const g = c.part("joint" + i, { parent: c.index(p), render: "emissive" });
    const j = w * 0.62;
    g.box(-j, -j, -0.05, j, j, 0.3, 0xFFFFFFFF, 0xFFFFFFFF, 1);
    parent = c.index(p);
  }
  const light = c.part("light", { parent, pivot: [0, 0, 1.6], render: "emissive" });
  light.box(-0.55, -0.55, 0, 0.55, 0.55, 1.1, 0xFFFFFFFF, 0xFFFFFFFF, 1);
  const glow = c.part("glow", { parent: c.index(light), render: "emissive" });
  const r = 1.6;
  fan(glow, [[-r, -r, 0.55], [r, -r, 0.55], [r, r, 0.55], [-r, r, 0.55]], [0, 0, 0.55], argb(0.4, 0xffffff), argb(0, 0xffffff), 1);
  dquad(glow, [-r, 0, -0.5, 0, 0], [r, 0, -0.5, 1, 0], [r, 0, 1.6, 1, 1], [-r, 0, 1.6, 0, 1], argb(0, 0xffffff), argb(0, 0xffffff), 1);
  return c;
}

export default [cat(), fox(), bunny(), devil(), dragon(), robot()];
