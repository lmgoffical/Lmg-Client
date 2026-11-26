// Lmg Client: sign in for friends, and send emotes.
//
// login:  the game asks Mojang to "join" a random serverId (what it does for any server),
//         then we ask Mojang "hasJoined?" with that name and id. Only the real owner of the
//         account can make that succeed; their Minecraft login token never comes here.
//         We hand back a random session token and keep only its SHA-256.
// emote:  checks the session token, saves the emote (for players who walk up later) and
//         broadcasts it to everyone on the same server, signed with an Ed25519 key, so
//         nobody can make your character do something you didn't. Climbing emotes (sitting on
//         someone's head or shoulder, playing around them) are only sent if that person's
//         "Who can climb on me" setting includes you, and carry who they're with.
// pubkey: the public half of that key (it's built into the mod).
// uid:    your Lmg number (the next free one the first time), after the same Mojang check.
// forget: deletes everything we hold for your Minecraft account, after the same Mojang check:
//         friends (sessions, status, links, partner, invites), your UID and your shared
//         pets and cosmetics. Nothing about you is left.
//
// Deployed as "social" (verify_jwt: false: the session token / Mojang check is the auth).
// Tables and the friends functions: migrations/0004_social.sql
import { createClient } from "npm:@supabase/supabase-js@2.49.4";

const USERNAME = /^[A-Za-z0-9_]{1,16}$/;
const ROOM = /^[0-9a-f]{16}$/;
const EMOTES = new Set(["wave", "hug", "sit", "dance", "clap", "cheer", "point", "bow"]);
// done with someone: only if their "Who can climb on me" setting includes you
const PARTNER_EMOTES = new Set(["perch_head", "perch_shoulder", "play"]);
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const SESSION_DAYS = 30;
const MAX_SESSIONS = 5;

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "content-type, apikey, authorization",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const reply = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { ...cors, "Content-Type": "application/json" } });

const URL_ = Deno.env.get("SUPABASE_URL")!;
const SERVICE = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const db = createClient(URL_, SERVICE, { auth: { persistSession: false } });

const hex = (buf: ArrayBuffer) => [...new Uint8Array(buf)].map((b) => b.toString(16).padStart(2, "0")).join("");
const b64url = (buf: ArrayBuffer) =>
  btoa(String.fromCharCode(...new Uint8Array(buf))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
const b64 = (buf: ArrayBuffer) => btoa(String.fromCharCode(...new Uint8Array(buf)));
const sha256 = async (s: string) => hex(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s)));

async function sessionUuid(token: unknown): Promise<string | null> {
  if (typeof token !== "string" || token.length < 32 || token.length > 128) return null;
  const { data } = await db.from("lmg_sessions").select("uuid, expires_at").eq("token_hash", await sha256(token)).maybeSingle();
  if (!data || new Date(data.expires_at).getTime() < Date.now()) return null;
  return data.uuid as string;
}

// ---- signing key ---------------------------------------------------------------------------

let signingKey: CryptoKey | null = null;
let publicKey = "";

async function loadKey() {
  if (signingKey) return;
  let { data } = await db.from("lmg_keys").select("private_jwk, public_key").eq("id", "emote").maybeSingle();
  if (!data) {
    // first use: make a key pair and store it (if two requests race, the first one wins)
    const pair = (await crypto.subtle.generateKey({ name: "Ed25519" }, true, ["sign", "verify"])) as CryptoKeyPair;
    const jwk = await crypto.subtle.exportKey("jwk", pair.privateKey);
    const spki = b64(await crypto.subtle.exportKey("spki", pair.publicKey));
    await db.from("lmg_keys").insert({ id: "emote", private_jwk: jwk, public_key: spki });
    ({ data } = await db.from("lmg_keys").select("private_jwk, public_key").eq("id", "emote").single());
  }
  signingKey = await crypto.subtle.importKey("jwk", data!.private_jwk, { name: "Ed25519" }, false, ["sign"]);
  publicKey = data!.public_key;
}

/** Does {@code owner}'s "Who can climb on me" setting include {@code climber}? */
async function mayClimb(climber: string, owner: string): Promise<boolean> {
  const { data, error } = await db.rpc("lmg_may_climb", { p_climber: climber, p_owner: owner });
  return !error && data === true;
}

// ---- actions ---------------------------------------------------------------------------------

/** Asks Mojang whether this player really just "joined" with this serverId. */
async function mojang(body: any): Promise<{ uuid: string; name: string } | Response> {
  const username = String(body?.username ?? "");
  const serverId = String(body?.serverId ?? "");
  if (!USERNAME.test(username) || !/^-?[0-9a-f]{1,40}$/.test(serverId)) return reply(400, { error: "Bad request" });
  const check = await fetch(
    `https://sessionserver.mojang.com/session/minecraft/hasJoined?username=${encodeURIComponent(username)}&serverId=${encodeURIComponent(serverId)}`,
  );
  if (check.status !== 200) return reply(403, { error: "Couldn't verify your Minecraft account" });
  const profile = await check.json();
  const id = String(profile?.id ?? "");
  if (!/^[0-9a-f]{32}$/.test(id)) return reply(403, { error: "Couldn't verify your Minecraft account" });
  const uuid = `${id.slice(0, 8)}-${id.slice(8, 12)}-${id.slice(12, 16)}-${id.slice(16, 20)}-${id.slice(20)}`;
  return { uuid, name: String(profile.name ?? username) };
}

/** Your UID: the one you have, or the next free number. */
async function claimUid(uuid: string): Promise<number | null> {
  await db.from("lmg_uids").upsert({ uuid }, { onConflict: "uuid", ignoreDuplicates: true });
  const { data } = await db.from("lmg_uids").select("uid").eq("uuid", uuid).maybeSingle();
  return data ? Number(data.uid) : null;
}

/** Just the UID, for people who don't use friends (nothing else is stored). */
async function uidOnly(body: any) {
  const who = await mojang(body);
  if (who instanceof Response) return who;
  const uid = await claimUid(who.uuid);
  if (uid === null) return reply(500, { error: "Couldn't get your UID" });
  return reply(200, { uuid: who.uuid, uid });
}

async function login(body: any) {
  const who = await mojang(body);
  if (who instanceof Response) return who;
  const { uuid, name } = who;

  const { error: pe } = await db.from("lmg_players").upsert({ uuid, name, last_seen: new Date().toISOString() });
  if (pe) return reply(500, { error: "Couldn't sign you in" });

  const token = b64url(crypto.getRandomValues(new Uint8Array(32)).buffer);
  const expires = new Date(Date.now() + SESSION_DAYS * 86400_000).toISOString();
  const { error: se } = await db.from("lmg_sessions").insert({ token_hash: await sha256(token), uuid, expires_at: expires });
  if (se) return reply(500, { error: "Couldn't sign you in" });

  // keep only the newest few sessions per account
  const { data: old } = await db.from("lmg_sessions").select("token_hash").eq("uuid", uuid)
    .order("created_at", { ascending: false }).range(MAX_SESSIONS, 100);
  if (old?.length) await db.from("lmg_sessions").delete().in("token_hash", old.map((o) => o.token_hash));

  const uid = await claimUid(uuid);
  return reply(200, { token, uuid, name, expires, uid });
}

/** "Delete all my Lmg data": everything tied to this Minecraft account goes. */
async function forgetAll(body: any) {
  const who = await mojang(body);
  if (who instanceof Response) return who;
  const uuid = who.uuid;
  // lmg_players takes sessions, presence (and any emote), friend links both ways, partner and
  // invites both ways with it (foreign keys cascade)
  const friends = await db.from("lmg_players").delete({ count: "exact" }).eq("uuid", uuid);
  const uid = await db.from("lmg_uids").delete({ count: "exact" }).eq("uuid", uuid);
  const avatar = await db.from("player_avatars").delete({ count: "exact" }).eq("uuid", uuid);
  if (friends.error || uid.error || avatar.error) return reply(500, { error: "Couldn't delete everything - try again" });
  return reply(200, {
    ok: true,
    removed: { friends: friends.count ?? 0, uid: uid.count ?? 0, avatar: avatar.count ?? 0 },
  });
}

async function emote(body: any) {
  const uuid = await sessionUuid(body?.token);
  if (!uuid) return reply(401, { error: "bad_session" });
  const room = String(body?.room ?? "");
  const what = body?.emote === null ? null : String(body?.emote ?? "");
  if (!ROOM.test(room) || (what !== null && !EMOTES.has(what) && !PARTNER_EMOTES.has(what))) return reply(400, { error: "Bad request" });

  // climbing / playing: with whom (your partner if you don't say), and does their setting allow it?
  let withUuid: string | null = null;
  if (what !== null && PARTNER_EMOTES.has(what)) {
    let target = typeof body?.with === "string" && UUID_RE.test(body.with) ? body.with : null;
    if (!target) {
      const { data: mine } = await db.from("lmg_partners").select("to_uuid").eq("from_uuid", uuid).maybeSingle();
      if (!mine) return reply(403, { error: "no_partner" });
      target = mine.to_uuid as string;
    }
    if (target === uuid || !(await mayClimb(uuid, target))) return reply(403, { error: "not_allowed" });
    withUuid = target;
  }

  // at most a few emotes a second per player
  const { data: pr } = await db.from("lmg_presence").select("emote_at").eq("uuid", uuid).maybeSingle();
  if (what !== null && pr?.emote_at && Date.now() - new Date(pr.emote_at).getTime() < 400) {
    return reply(429, { error: "Too fast" });
  }
  const now = new Date();
  await db.from("lmg_presence").upsert({
    uuid, emote: what, emote_room: what ? room : null, emote_at: what ? now.toISOString() : null,
    emote_with: withUuid, updated_at: now.toISOString(),
  }, { onConflict: "uuid" });

  await loadKey();
  const t = now.getTime();
  // partner emotes sign who they're with too (older clients just skip them)
  const message = `lmg-emote|${uuid}|${what ?? "stop"}|${room}|${t}` + (withUuid ? `|${withUuid}` : "");
  const sig = b64(await crypto.subtle.sign({ name: "Ed25519" }, signingKey!, new TextEncoder().encode(message)));
  const res = await fetch(`${URL_}/realtime/v1/api/broadcast`, {
    method: "POST",
    headers: { apikey: SERVICE, Authorization: `Bearer ${SERVICE}`, "Content-Type": "application/json" },
    body: JSON.stringify({
      messages: [{ topic: `lmg-emotes-${room}`, event: "emote",
        payload: { u: uuid, e: what ?? "stop", r: room, t, s: sig, ...(withUuid ? { w: withUuid } : {}) } }],
    }),
  });
  if (!res.ok) return reply(502, { error: "Couldn't send it" });
  return reply(200, { ok: true, t, with: withUuid });
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response(null, { headers: cors });
  if (req.method !== "POST") return reply(405, { error: "POST only" });
  let body: any;
  try {
    body = await req.json();
  } catch {
    return reply(400, { error: "Bad JSON" });
  }
  try {
    switch (body?.action) {
      case "login":
        return await login(body);
      case "emote":
        return await emote(body);
      case "uid":
        return await uidOnly(body);
      case "forget":
        return await forgetAll(body);
      case "pubkey":
        await loadKey();
        return reply(200, { key: publicKey });
      default:
        return reply(400, { error: "Unknown action" });
    }
  } catch (e) {
    console.error(e);
    return reply(500, { error: "Something went wrong" });
  }
});
