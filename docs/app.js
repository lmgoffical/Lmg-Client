// Lmg Client website: home, download, forum (threads + replies), config gallery,
// accounts and roles. All permissions are enforced by the database (row-level
// security); this file only decides what to show.
"use strict";

const SUPABASE_URL = "https://fieakrewvbyubtyxpqhr.supabase.co";
const SUPABASE_KEY = "sb_publishable__GJAqv2bWvhtt3UdGgAr2Q_NFI8TyG1"; // publishable key: meant to be public
const REPO_RAW = "https://raw.githubusercontent.com/lmgoffical/Lmg-Client/claude/modest-cray-bg98zr";
const INSTALLER_URL = REPO_RAW + "/installer/Install-Lmg-Client.bat";
const CODE_RE = /^LMG-[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{6}$/;

const sb = supabase.createClient(SUPABASE_URL, SUPABASE_KEY);
const app = document.getElementById("app");
const state = { session: null, me: null };

// ---------------------------------------------------------------------------
// helpers
// ---------------------------------------------------------------------------

function esc(s) {
  return String(s ?? "").replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}

/** Escaped text with clickable links (only http/https). */
function richText(s) {
  return esc(s).replace(/https?:\/\/[^\s<]+/g, url => {
    const clean = url.replace(/[.,!?)]+$/, "");
    const tail = url.slice(clean.length);
    return `<a href="${clean}" target="_blank" rel="noopener nofollow ugc">${clean}</a>${tail}`;
  });
}

function timeAgo(iso) {
  const s = Math.max(1, (Date.now() - new Date(iso).getTime()) / 1000);
  const units = [[31536000, "y"], [2592000, "mo"], [86400, "d"], [3600, "h"], [60, "m"]];
  for (const [n, u] of units) if (s >= n) return Math.floor(s / n) + u + " ago";
  return "just now";
}

function roleBadge(role) {
  return role && role !== "member" ? `<span class="badge ${esc(role)}">${esc(role)}</span>` : "";
}

function userLink(p) {
  if (!p) return `<span class="muted">deleted user</span>`;
  return `<a class="username ${esc(p.role)}" href="#/u/${encodeURIComponent(p.username)}">${esc(p.username)}</a>${roleBadge(p.role)}`;
}

function avatar(name) {
  let hash = 0;
  for (const ch of String(name)) hash = (hash * 31 + ch.charCodeAt(0)) | 0;
  const hue = Math.abs(hash) % 360;
  return `<div class="avatar" style="background:linear-gradient(135deg,hsl(${hue} 70% 45%),hsl(${(hue + 40) % 360} 70% 30%))">${esc(String(name)[0] || "?").toUpperCase()}</div>`;
}

function isStaff() { return !!state.me && ["owner", "admin", "moderator"].includes(state.me.role); }
function isOwner() { return !!state.me && state.me.role === "owner"; }

function normalizeCode(text) {
  let s = String(text || "").toUpperCase().replace(/[^A-Z0-9]/g, "");
  if (s.length === 9 && s.startsWith("LMG")) s = s.slice(3);
  return s.length === 6 ? "LMG-" + s : null;
}

function toast(msg) {
  const el = document.createElement("div");
  el.className = "toast";
  el.textContent = msg;
  document.body.appendChild(el);
  setTimeout(() => el.remove(), 2600);
}

async function copy(text) {
  try { await navigator.clipboard.writeText(text); toast("Copied " + text + " - paste it in Lmg Client > Configs"); }
  catch { toast("Copy this code: " + text); }
}

function friendlyError(error) {
  const m = (error && (error.message || error.error_description)) || "Something went wrong";
  if (/row-level security|permission denied/i.test(m)) return "You don't have permission to do that.";
  return m;
}

function render(html) { app.innerHTML = html; }
function setActiveNav(name) {
  document.querySelectorAll("[data-nav]").forEach(a => a.classList.toggle("active", a.dataset.nav === name));
}

// ---------------------------------------------------------------------------
// auth
// ---------------------------------------------------------------------------

async function loadMe() {
  state.me = null;
  if (!state.session) return;
  const { data } = await sb.from("profiles").select("*").eq("id", state.session.user.id).maybeSingle();
  state.me = data;
}

function renderUserbox() {
  const box = document.getElementById("userbox");
  if (state.me) {
    box.innerHTML = `
      ${isOwner() ? `<a class="btn small" href="#/admin">Owner panel</a>` : ""}
      <a href="#/u/${encodeURIComponent(state.me.username)}">${userLink(state.me).replace(/<a /, "<span ").replace(/<\/a>/, "</span>")}</a>
      <button class="btn small" id="logout">Log out</button>`;
    document.getElementById("logout").onclick = async () => { await sb.auth.signOut(); location.hash = "#/"; };
  } else {
    box.innerHTML = `<a class="btn small" href="#/login">Log in</a><a class="btn small primary" href="#/signup">Sign up</a>`;
  }
}

// ---------------------------------------------------------------------------
// pages
// ---------------------------------------------------------------------------

// Real release history, newest first (keep this updated when you release).
const CHANGELOG = [
  ["0.16", "<b>Better cosmetics.</b> Wings bend and fold when they flap, a real 3D halo, crown and cat ears, and a nicer China hat."],
  ["0.15", "<b>Website + forum.</b> Accounts, threads, a config gallery, and a link to it from the in-game Configs menu."],
  ["0.14", "<b>Short config codes.</b> Share a setup as <code>LMG-ABC234</code> instead of a wall of text."],
  ["0.13", "<b>Saved configs.</b> Save as many setups as you want and swap between them."],
  ["0.12", "Skeet and Neverlose styles redone after comparing them with reference screenshots."],
  ["0.10", "<b>Menu styles.</b> The Lmg menu can now look like Skeet or Neverlose."],
  ["0.9", "Smaller, cleaner menu. Drag your HUD anywhere."],
  ["0.8", "Smaller held items for PvP, and a Star of David totem."],
  ["0.7", "Glowing outline on whatever you're aiming at, plus their health and gear."],
  ["0.6", "Block overlay, aurora in the night sky, kill effects, fullbright."],
  ["0.5", "Wings, halo, crown, cat ears and the Lmg cape."],
];

const FAQ = [
  ["Is this a hack client?", "No. Everything is visual and client-side: skies, cosmetics, HUD, menus. There's no aim assist, reach or anything that plays the game for you."],
  ["Will I get banned for using it?", "Visual mods are fine on almost every server. A few competitive servers don't allow fullbright or ore textures, so turn those off there if their rules say so."],
  ["Is it free?", "Yes, and it's going to stay free."],
  ["Can other people see my wings and cape?", "Not yet - right now cosmetics only show on your screen. Showing them to other Lmg users is on the list."],
  ["Which version of Minecraft?", "Java Edition 26.3 with Fabric. The installer sets everything up for you."],
];

async function pageHome() {
  setActiveNav("home");
  render(`
    <section class="hero">
      <div>
        <div class="meta-line" id="meta-line">free &middot; java edition &middot; fabric</div>
        <h1>Minecraft, but it looks <span class="squiggle">how you want.<svg viewBox="0 0 200 14" preserveAspectRatio="none" aria-hidden="true"><path d="M2 9 C 30 2, 55 13, 85 7 S 140 2, 170 8 S 192 10, 198 5" fill="none" stroke="#f2c14e" stroke-width="3" stroke-linecap="round"/></svg></span></h1>
        <p>I got bored of every client looking the same, so I made my own. Change the sky, put wings on your back, restyle the whole menu &mdash; then send your setup to a friend with one short code.</p>
        <div class="actions">
          <a class="btn primary" href="#/download">Download for Windows</a>
          <a href="#/configs">or browse people's configs &rarr;</a>
        </div>
        <div class="stat-line" id="stat-line"></div>
      </div>
      <div class="shot" aria-label="Screenshot-style mockup of the Lmg menu">
        <div class="hud"><span style="color:#f2c14e">&#10017;</span><b>lmg</b><i></i>hypixel.net<i></i>240 fps<i></i>21:04</div>
        <div class="menu">
          <div class="rainbow"></div>
          <div class="side"><span>Style</span><span>Configs</span><span>Theme</span><span class="on">World</span><span>Avatar</span><span>Effects</span><span>PvP</span></div>
          <div class="body">
            <div class="box"><b>World</b>
              <div class="opt"><span class="cb"></span>Sky theme</div><div class="dd"><span>Midnight</span><span>v</span></div>
              <div class="opt"><span class="cb"></span>Fog</div><div class="dd"><span>None</span><span>v</span></div>
              <div class="opt"><span class="cb on"></span>Always show stars</div>
            </div>
            <div class="box"><b>More</b>
              <div class="opt"><span class="cb"></span>Aurora</div><div class="dd"><span>Purple</span><span>v</span></div>
              <div class="opt"><span class="cb on"></span>Rain &amp; snow</div>
              <div class="opt"><span class="cb"></span>Clouds</div>
            </div>
          </div>
        </div>
        <div class="sticker">it's free</div>
      </div>
    </section>

    <section class="section" id="announce-section" hidden>
      <div class="section-head"><h2>News</h2><a class="small" href="#/c/announcements">all announcements</a></div>
      <div class="card list" id="announcements"></div>
    </section>

    <section class="section">
      <div class="section-head"><h2>What you can change</h2><span class="label">press right shift in game</span></div>
      <div class="does">
        <div><h3>The sky</h3><p>Sunset, inferno, midnight or a void. Pin the time to night, kill the fog, add an aurora.</p></div>
        <div><h3>Your character</h3><p>Wings that flap faster when you sprint, a halo, a crown, cat ears and the Lmg cape.</p></div>
        <div><h3>PvP stuff</h3><p>Smaller swords, a cleaner block outline, a glow on whoever you're fighting and their health next to your crosshair.</p></div>
        <div><h3>The menu itself</h3><p>Keep the Lmg look or switch it to a Skeet or Neverlose style. It's only looks, the settings are the same.</p></div>
        <div><h3>Your HUD</h3><p>FPS, ping and potion timers that don't jitter. Drag everything where you want it.</p></div>
        <div><h3>Sharing</h3><p>Save setups, then share them as <code>LMG-ABC234</code>. Your friend types it in and they've got your look.</p></div>
      </div>
    </section>

    <section class="section grid two">
      <div>
        <div class="section-head"><h2>Recent updates</h2><a class="small" href="https://github.com/lmgoffical/Lmg-Client/commits">full history</a></div>
        <ul class="changelog">${CHANGELOG.map(([v, what]) => `<li><span class="v">v${v}</span><span class="what">${what}</span></li>`).join("")}</ul>
      </div>
      <div>
        <div class="section-head"><h2>Questions</h2></div>
        ${FAQ.map(([q, a]) => `<details><summary>${esc(q)}</summary><p>${esc(a)}</p></details>`).join("")}
      </div>
    </section>`);

  sb.rpc("site_stats").then(({ data }) => {
    const s = data || {};
    const parts = [];
    if (s.members > 0) parts.push(`${s.members} ${s.members === 1 ? "person has" : "people have"} joined`);
    if (s.configs > 0) parts.push(`${s.configs} configs shared`);
    const el = document.getElementById("stat-line");
    if (el) el.textContent = parts.join(" · ");
  });
  fetch(REPO_RAW + "/download/manifest.json", { cache: "no-store" }).then(r => r.json()).then(m => {
    const el = document.getElementById("meta-line");
    if (el) el.innerHTML = `v${esc(m.version)} &middot; free &middot; minecraft ${esc(m.minecraft)} &middot; fabric`;
  }).catch(() => {});

  const { data } = await sb.from("threads")
    .select("id,title,created_at,pinned,locked,config_code,reply_count,author:profiles(username,role)")
    .eq("category_id", "announcements").order("pinned", { ascending: false }).order("created_at", { ascending: false }).limit(3);
  if (data && data.length && document.getElementById("announcements")) {
    document.getElementById("announcements").innerHTML = data.map(threadRow).join("");
    document.getElementById("announce-section").hidden = false;
  }
}

async function pageDownload() {
  setActiveNav("download");
  render(`
    <div class="grid two">
      <div>
        <div class="label" id="version">java edition</div>
        <h1 style="margin-top:8px">Get Lmg Client</h1>
        <p class="muted">One small installer. It sets up Fabric, adds an <b>Lmg Client</b> profile to your launcher, and grabs Sodium, Lithium and Iris so the game runs well. Your normal Minecraft isn't touched.</p>
        <a class="btn primary" href="${INSTALLER_URL}">Download installer (.bat)</a>
        <p class="muted small" style="margin-top:14px">Windows may say "Windows protected your PC" because the script isn't signed &mdash; click <i>More info &rarr; Run anyway</i>.</p>
      </div>
      <div class="card">
        <h3>Installing</h3>
        <ol class="muted" style="padding-left:20px;margin:0">
          <li>Open the Minecraft Launcher once and sign in, then close it.</li>
          <li>Run <b>Install-Lmg-Client.bat</b>.</li>
          <li>In the launcher, pick <b>Lmg Client</b> next to Play.</li>
          <li>In game, press <b>Right Shift</b>.</li>
        </ol>
        <p class="muted small" style="margin-bottom:0">To update later, just run the installer again.</p>
      </div>
    </div>`);
  try {
    const m = await (await fetch(REPO_RAW + "/download/manifest.json", { cache: "no-store" })).json();
    document.getElementById("version").textContent = `v${m.version} · minecraft ${m.minecraft}`;
  } catch { /* keep the default label */ }
}

// little hand-drawn style icons for the forum categories
const CAT_ICONS = {
  announcements: `<svg viewBox="0 0 24 24" fill="none" stroke="#f2c14e" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 11v2a1 1 0 0 0 1 1h2l5 4V6L6 10H4a1 1 0 0 0-1 1z"/><path d="M15.5 8.5a5 5 0 0 1 0 7"/><path d="M18.5 5.5a9 9 0 0 1 0 13"/></svg>`,
  configs: `<svg viewBox="0 0 24 24" fill="none" stroke="#3d9bff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 6h10M18 6h2M4 12h4M12 12h8M4 18h12M20 18h0"/><circle cx="16" cy="6" r="2"/><circle cx="10" cy="12" r="2"/><circle cx="18" cy="18" r="2"/></svg>`,
  general: `<svg viewBox="0 0 24 24" fill="none" stroke="#a3b0c4" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 12a8 8 0 0 1-11.6 7.1L4 20l1-4.6A8 8 0 1 1 21 12z"/></svg>`,
  support: `<svg viewBox="0 0 24 24" fill="none" stroke="#4fd49a" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9"/><path d="M9.5 9a2.5 2.5 0 1 1 3.5 2.3c-.6.3-1 .9-1 1.5V14"/><path d="M12 17.5h0"/></svg>`,
  suggestions: `<svg viewBox="0 0 24 24" fill="none" stroke="#ff8fa3" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 18h6M10 21h4"/><path d="M12 3a6 6 0 0 0-3.5 10.9c.6.4 1 1.1 1 1.8V16h5v-.3c0-.7.4-1.4 1-1.8A6 6 0 0 0 12 3z"/></svg>`,
};

async function pageForum() {
  setActiveNav("forum");
  render(`<div class="section-head" style="margin-top:0"><h1 style="margin:0">Forum</h1><span class="muted small">Be decent. Staff can lock or remove anything that isn't.</span></div><div class="card list" id="cats"><div class="empty">Loading...</div></div>`);
  const [{ data: cats }, { data: recent }] = await Promise.all([
    sb.from("categories").select("*").order("sort"),
    sb.from("threads").select("id,title,category_id,last_activity").order("last_activity", { ascending: false }).limit(60),
  ]);
  const counts = await Promise.all((cats || []).map(c =>
    sb.from("threads").select("id", { count: "exact", head: true }).eq("category_id", c.id).then(r => r.count || 0)));
  const latest = {};
  for (const t of recent || []) if (!latest[t.category_id]) latest[t.category_id] = t;
  document.getElementById("cats").innerHTML = (cats || []).map((c, i) => `
    <a class="cat-row" href="#/c/${esc(c.id)}">
      <div class="cat-icon">${CAT_ICONS[c.id] || CAT_ICONS.general}</div>
      <div class="grow">
        <div class="title-line">${esc(c.name)} ${c.staff_only ? `<span class="tag pinned">staff</span>` : ""}</div>
        <div class="meta">${esc(c.description)}</div>
      </div>
      <div class="count">${counts[i]} threads<br>${latest[c.id] ? esc(timeAgo(latest[c.id].last_activity)) : ""}</div>
    </a>`).join("");
}

function threadRow(t) {
  return `
    <a class="thread-row" href="#/t/${t.id}">
      ${avatar(t.author ? t.author.username : "?")}
      <div class="grow">
        <div class="title-line">${t.pinned ? `<span class="tag pinned">pinned</span>` : ""}${t.locked ? `<span class="tag locked">locked</span>` : ""}${t.config_code ? `<span class="tag config">config</span>` : ""}${esc(t.title)}</div>
        <div class="meta">by ${t.author ? esc(t.author.username) : "deleted user"}${t.author ? roleBadge(t.author.role) : ""} &middot; ${esc(timeAgo(t.created_at))}</div>
      </div>
      <div class="count">${t.reply_count ?? 0} replies</div>
    </a>`;
}

async function pageCategory(id) {
  setActiveNav("forum");
  const { data: cat } = await sb.from("categories").select("*").eq("id", id).maybeSingle();
  if (!cat) return render(`<div class="empty">That category doesn't exist.</div>`);
  const canPost = state.me && (!cat.staff_only || isStaff());
  render(`
    <div class="row between"><div><a class="muted small" href="#/forum">&larr; Forum</a><h1>${esc(cat.name)}</h1><div class="muted">${esc(cat.description)}</div></div>
    ${canPost ? `<a class="btn primary" href="#/new/${esc(cat.id)}">New thread</a>` : state.me ? "" : `<a class="btn" href="#/login">Log in to post</a>`}</div>
    <div class="card list" style="margin-top:16px" id="threads"><div class="empty">Loading...</div></div>`);
  const { data, error } = await sb.from("threads")
    .select("id,title,pinned,locked,config_code,reply_count,created_at,last_activity,author:profiles(username,role)")
    .eq("category_id", id).order("pinned", { ascending: false }).order("last_activity", { ascending: false }).limit(100);
  document.getElementById("threads").innerHTML = error ? `<div class="empty">${esc(friendlyError(error))}</div>`
    : data.length ? data.map(threadRow).join("") : `<div class="empty">Quiet in here. Start something?</div>`;
}

async function pageThread(id) {
  setActiveNav("forum");
  const { data: t, error } = await sb.from("threads")
    .select("*, author:profiles(username,role,created_at), category:categories(id,name)")
    .eq("id", id).maybeSingle();
  if (error || !t) return render(`<div class="empty">That thread doesn't exist (or was deleted).</div>`);
  const { data: posts } = await sb.from("posts").select("*, author:profiles(username,role)").eq("thread_id", id).order("created_at");
  const mine = state.me && t.author_id === state.me.id;
  const canReply = state.me && (!t.locked || isStaff());

  render(`
    <a class="muted small" href="#/c/${esc(t.category.id)}">&larr; ${esc(t.category.name)}</a>
    <h1 style="margin-top:6px">${t.pinned ? `<span class="tag pinned">pinned</span>` : ""}${t.locked ? `<span class="tag locked">locked</span>` : ""}${esc(t.title)}</h1>
    <div class="card" id="op">
      <div class="post">
        ${avatar(t.author ? t.author.username : "?")}
        <div class="grow">
          <div class="row between"><div>${userLink(t.author)} <span class="meta">&middot; ${esc(timeAgo(t.created_at))}${t.updated_at !== t.created_at ? " (edited)" : ""}</span></div></div>
          <div class="post-body" id="op-body">${richText(t.body)}</div>
          ${t.config_code ? `<div class="code-box"><div class="grow"><div class="meta">Config code</div><div class="code">${esc(t.config_code)}</div></div><button class="btn primary small" id="copy-code">Copy code</button></div>` : ""}
          ${mine || isStaff() ? `<div class="staff-bar">
            ${mine ? `<button class="btn small" id="edit-thread">Edit</button>` : ""}
            ${isStaff() ? `<button class="btn small" id="pin">${t.pinned ? "Unpin" : "Pin"}</button><button class="btn small" id="lock">${t.locked ? "Unlock" : "Lock"}</button>` : ""}
            <button class="btn small danger" id="delete-thread">Delete thread</button>
          </div>` : ""}
        </div>
      </div>
    </div>
    <div class="section-title">${(posts || []).length} ${(posts || []).length === 1 ? "reply" : "replies"}</div>
    ${(posts || []).map(p => `
      <div class="card" data-post="${p.id}">
        <div class="post">${avatar(p.author ? p.author.username : "?")}
          <div class="grow">
            <div class="row between"><div>${userLink(p.author)} <span class="meta">&middot; ${esc(timeAgo(p.created_at))}</span></div>
              ${state.me && (p.author_id === state.me.id || isStaff()) ? `<button class="btn small danger" data-delete-post="${p.id}">Delete</button>` : ""}</div>
            <div class="post-body">${richText(p.body)}</div>
          </div>
        </div>
      </div>`).join("")}
    ${canReply ? `<div class="card"><label>Write a reply</label><textarea id="reply" maxlength="5000" placeholder="Say something..."></textarea>
      <div class="row" style="margin-top:10px"><button class="btn primary" id="send-reply">Reply</button><span class="error" id="reply-error"></span></div></div>`
      : state.me ? `<div class="empty">This thread is locked.</div>` : `<div class="empty"><a href="#/login">Log in</a> to reply.</div>`}`);

  const on = (sel, fn) => { const el = document.getElementById(sel); if (el) el.onclick = fn; };
  on("copy-code", () => copy(t.config_code));
  on("pin", async () => { const { error } = await sb.rpc("set_thread_flags", { p_thread: t.id, p_pinned: !t.pinned, p_locked: null }); error ? toast(friendlyError(error)) : route(); });
  on("lock", async () => { const { error } = await sb.rpc("set_thread_flags", { p_thread: t.id, p_pinned: null, p_locked: !t.locked }); error ? toast(friendlyError(error)) : route(); });
  on("delete-thread", async () => {
    if (!confirm("Delete this thread and all its replies?")) return;
    const { error } = await sb.from("threads").delete().eq("id", t.id);
    if (error) toast(friendlyError(error)); else location.hash = "#/c/" + t.category.id;
  });
  on("edit-thread", () => {
    document.getElementById("op-body").innerHTML = `<textarea id="edit-body" maxlength="10000">${esc(t.body)}</textarea>
      <div class="row" style="margin-top:8px"><button class="btn primary small" id="save-edit">Save</button><button class="btn small" id="cancel-edit">Cancel</button><span class="error" id="edit-error"></span></div>`;
    on("cancel-edit", route);
    on("save-edit", async () => {
      const body = document.getElementById("edit-body").value.trim();
      const { error } = await sb.from("threads").update({ body }).eq("id", t.id);
      if (error) document.getElementById("edit-error").textContent = friendlyError(error); else route();
    });
  });
  on("send-reply", async () => {
    const body = document.getElementById("reply").value.trim();
    if (!body) return;
    document.getElementById("send-reply").disabled = true;
    const { error } = await sb.from("posts").insert({ thread_id: t.id, body });
    if (error) { document.getElementById("reply-error").textContent = friendlyError(error); document.getElementById("send-reply").disabled = false; }
    else route();
  });
  document.querySelectorAll("[data-delete-post]").forEach(b => b.onclick = async () => {
    if (!confirm("Delete this reply?")) return;
    const { error } = await sb.from("posts").delete().eq("id", b.dataset.deletePost);
    error ? toast(friendlyError(error)) : route();
  });
}

async function pageNewThread(catId) {
  setActiveNav("forum");
  if (!state.me) return pageLogin("Log in to start a thread.");
  const { data: cat } = await sb.from("categories").select("*").eq("id", catId).maybeSingle();
  if (!cat) return render(`<div class="empty">That category doesn't exist.</div>`);
  if (cat.staff_only && !isStaff()) return render(`<div class="empty">Only staff can post in ${esc(cat.name)}.</div>`);
  render(`
    <div class="card" style="max-width:760px;margin:0 auto">
      <a class="muted small" href="#/c/${esc(cat.id)}">&larr; ${esc(cat.name)}</a>
      <h2 style="margin-top:6px">New thread in ${esc(cat.name)}</h2>
      <label>Title</label><input id="title" maxlength="120" placeholder="Give it a clear title">
      <label>Message</label><textarea id="body" maxlength="10000" placeholder="Write your post..."></textarea>
      <label>Config code ${cat.id === "configs" ? "" : "(optional)"}</label>
      <input id="code" maxlength="12" placeholder="LMG-ABC234 - get one in game: Right Shift > Configs > Share">
      <div class="row" style="margin-top:14px"><button class="btn primary" id="post">Post thread</button><span class="error" id="err"></span></div>
    </div>`);
  document.getElementById("post").onclick = async () => {
    const title = document.getElementById("title").value.trim();
    const body = document.getElementById("body").value.trim();
    const rawCode = document.getElementById("code").value.trim();
    const err = document.getElementById("err");
    let config_code = null;
    if (rawCode) {
      config_code = normalizeCode(rawCode);
      if (!config_code || !CODE_RE.test(config_code)) return err.textContent = "That doesn't look like a config code (LMG-ABC234).";
    } else if (cat.id === "configs") {
      return err.textContent = "Add the config code you want to share.";
    }
    if (title.length < 3) return err.textContent = "Title is too short.";
    if (!body) return err.textContent = "Write a message.";
    document.getElementById("post").disabled = true;
    const { data, error } = await sb.from("threads").insert({ category_id: cat.id, title, body, config_code }).select("id").single();
    if (error) { err.textContent = friendlyError(error); document.getElementById("post").disabled = false; }
    else location.hash = "#/t/" + data.id;
  };
}

async function pageConfigs() {
  setActiveNav("configs");
  render(`
    <div class="row between"><div><h1>Shared configs</h1><div class="muted">Copy a code, then in game: Right Shift &rarr; Configs &rarr; Import.</div></div>
    ${state.me ? `<a class="btn primary" href="#/new/configs">Share yours</a>` : `<a class="btn" href="#/login">Log in to share</a>`}</div>
    <div class="grid three" style="margin-top:16px" id="grid"><div class="empty">Loading...</div></div>`);
  const { data, error } = await sb.from("threads")
    .select("id,title,body,config_code,created_at,reply_count,author:profiles(username,role)")
    .not("config_code", "is", null).order("created_at", { ascending: false }).limit(60);
  const grid = document.getElementById("grid");
  if (error) { grid.innerHTML = `<div class="empty">${esc(friendlyError(error))}</div>`; return; }
  grid.innerHTML = data.length ? data.map(t => `
    <div class="card config-card">
      <a href="#/t/${t.id}"><h3>${esc(t.title)}</h3></a>
      <div class="meta">by ${t.author ? esc(t.author.username) : "deleted user"}${t.author ? roleBadge(t.author.role) : ""} &middot; ${esc(timeAgo(t.created_at))}</div>
      <p class="muted small">${esc(t.body.length > 140 ? t.body.slice(0, 140) + "..." : t.body)}</p>
      <div class="row between"><span class="code">${esc(t.config_code)}</span><button class="btn small primary" data-copy="${esc(t.config_code)}">Copy</button></div>
    </div>`).join("") : `<div class="empty">No configs yet &mdash; share yours and you'll be first on the board.</div>`;
  grid.querySelectorAll("[data-copy]").forEach(b => b.onclick = () => copy(b.dataset.copy));
}

function pageLogin(message) {
  setActiveNav("");
  render(`
    <div class="card form">
      <h2>Log in</h2>
      ${message ? `<p class="muted">${esc(message)}</p>` : ""}
      <label>Email</label><input id="email" type="email" autocomplete="email">
      <label>Password</label><input id="password" type="password" autocomplete="current-password">
      <div class="row" style="margin-top:16px"><button class="btn primary" id="go">Log in</button><a href="#/signup" class="small">No account? Sign up</a></div>
      <div class="error" id="err"></div>
    </div>`);
  document.getElementById("go").onclick = async () => {
    const { error } = await sb.auth.signInWithPassword({
      email: document.getElementById("email").value.trim(),
      password: document.getElementById("password").value,
    });
    if (error) document.getElementById("err").textContent = friendlyError(error);
    else location.hash = "#/forum";
  };
}

function pageSignup() {
  setActiveNav("");
  render(`
    <div class="card form">
      <h2>Create an account</h2>
      <label>Username</label><input id="username" maxlength="20" placeholder="3-20 letters, numbers or _">
      <label>Email</label><input id="email" type="email" autocomplete="email">
      <label>Password</label><input id="password" type="password" autocomplete="new-password" placeholder="At least 8 characters">
      <div class="row" style="margin-top:16px"><button class="btn primary" id="go">Sign up</button><a href="#/login" class="small">Have an account? Log in</a></div>
      <div class="error" id="err"></div><div class="notice" id="ok"></div>
    </div>`);
  document.getElementById("go").onclick = async () => {
    const username = document.getElementById("username").value.trim();
    const email = document.getElementById("email").value.trim();
    const password = document.getElementById("password").value;
    const err = document.getElementById("err");
    err.textContent = "";
    if (!/^[A-Za-z0-9_]{3,20}$/.test(username)) return err.textContent = "Username: 3-20 letters, numbers or _.";
    if (password.length < 8) return err.textContent = "Password must be at least 8 characters.";
    const { data: free } = await sb.rpc("username_available", { p_name: username });
    if (!free) return err.textContent = "That username is taken.";
    const { data, error } = await sb.auth.signUp({
      email, password,
      options: { data: { username }, emailRedirectTo: location.origin + location.pathname },
    });
    if (error) return err.textContent = friendlyError(error);
    if (data.session) location.hash = "#/forum";
    else document.getElementById("ok").textContent = "Almost done! Check your email and click the link to confirm your account.";
  };
}

async function pageProfile(name) {
  setActiveNav("");
  const pattern = name.replace(/[\\%_]/g, c => "\\" + c);
  const { data: p } = await sb.from("profiles").select("*").ilike("username", pattern).maybeSingle();
  if (!p) return render(`<div class="empty">No user called ${esc(name)}.</div>`);
  const { data: threads } = await sb.from("threads")
    .select("id,title,pinned,locked,config_code,reply_count,created_at,author:profiles(username,role)")
    .eq("author_id", p.id).order("created_at", { ascending: false }).limit(30);
  const mine = state.me && state.me.id === p.id;
  render(`
    <div class="card"><div class="post">${avatar(p.username)}
      <div class="grow"><h2>${userLink(p)}</h2><div class="meta">Joined ${esc(new Date(p.created_at).toLocaleDateString())}</div>
        <div class="post-body" id="bio">${p.bio ? richText(p.bio) : `<span class="muted">No bio yet.</span>`}</div>
        ${mine ? `<div class="row" style="margin-top:10px"><button class="btn small" id="edit-bio">Edit bio</button></div>` : ""}
      </div></div></div>
    <div class="section-title">Threads by ${esc(p.username)}</div>
    <div class="card list">${(threads || []).length ? threads.map(threadRow).join("") : `<div class="empty">No threads yet.</div>`}</div>`);
  const btn = document.getElementById("edit-bio");
  if (btn) btn.onclick = () => {
    document.getElementById("bio").innerHTML = `<textarea id="bio-text" maxlength="300" style="min-height:80px">${esc(p.bio)}</textarea>
      <div class="row" style="margin-top:8px"><button class="btn primary small" id="save-bio">Save</button><span class="error" id="bio-err"></span></div>`;
    document.getElementById("save-bio").onclick = async () => {
      const { error } = await sb.from("profiles").update({ bio: document.getElementById("bio-text").value.trim() }).eq("id", p.id);
      if (error) document.getElementById("bio-err").textContent = friendlyError(error); else route();
    };
  };
}

async function pageAdmin() {
  setActiveNav("");
  if (!isOwner()) return render(`<div class="empty">Owner only.</div>`);
  const { data: staff } = await sb.from("profiles").select("*").neq("role", "member").order("role");
  render(`
    <h1>Owner panel</h1>
    <div class="grid two">
      <div class="card">
        <h3>Give a role</h3>
        <label>Username</label><input id="who" maxlength="20">
        <label>Role</label>
        <select id="role"><option value="admin">Admin</option><option value="moderator">Moderator</option><option value="member">Member (remove role)</option></select>
        <div class="row" style="margin-top:14px"><button class="btn primary" id="set">Set role</button><span class="error" id="err"></span><span class="notice" id="ok"></span></div>
        <p class="muted small" style="margin-top:14px">Admins and moderators can pin, lock and delete threads, delete replies, and post announcements. Only you can change roles.</p>
      </div>
      <div class="card"><h3>Staff</h3>${(staff || []).map(p => `<div style="padding:6px 0">${userLink(p)}</div>`).join("") || `<div class="muted">Nobody yet.</div>`}
        <div class="row" style="margin-top:14px"><a class="btn small" href="#/new/announcements">Post an announcement</a></div></div>
    </div>`);
  document.getElementById("set").onclick = async () => {
    const { error } = await sb.rpc("set_user_role", { p_username: document.getElementById("who").value.trim(), p_role: document.getElementById("role").value });
    document.getElementById("err").textContent = error ? friendlyError(error) : "";
    document.getElementById("ok").textContent = error ? "" : "Done!";
    if (!error) setTimeout(route, 600);
  };
}

// ---------------------------------------------------------------------------
// router
// ---------------------------------------------------------------------------

async function route() {
  const parts = (location.hash.replace(/^#\/?/, "") || "").split("/").map(decodeURIComponent);
  const [page, arg] = parts;
  window.scrollTo(0, 0);
  try {
    switch (page) {
      case "": case undefined: return await pageHome();
      case "download": return await pageDownload();
      case "forum": return await pageForum();
      case "c": return await pageCategory(arg);
      case "t": return await pageThread(Number(arg));
      case "new": return await pageNewThread(arg);
      case "configs": return await pageConfigs();
      case "login": return pageLogin();
      case "signup": return pageSignup();
      case "u": return await pageProfile(arg || "");
      case "admin": return await pageAdmin();
      default: return render(`<div class="empty">Page not found. <a href="#/">Go home</a></div>`);
    }
  } catch (e) {
    console.error(e);
    render(`<div class="empty">Couldn't load this page. ${esc(friendlyError(e))}</div>`);
  }
}

window.addEventListener("hashchange", route);

(async () => {
  const { data } = await sb.auth.getSession();
  state.session = data.session;
  await loadMe();
  renderUserbox();
  await route();
  sb.auth.onAuthStateChange(async (event, session) => {
    if (event === "INITIAL_SESSION" || event === "TOKEN_REFRESHED") return;
    state.session = session;
    await loadMe();
    renderUserbox();
    if (event === "SIGNED_IN" || event === "SIGNED_OUT") route();
  });
})();
