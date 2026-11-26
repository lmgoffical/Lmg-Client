// Lmg Client: save or remove the pets and cosmetics a player chose to share.
//
// Proof of account: the game calls Mojang's session "join" with a random serverId (the same
// thing it does when you join a server). We then ask Mojang "hasJoined" with that username
// and serverId. Only the real owner of the account can make that succeed, and their login
// token never leaves the game (it only goes to Mojang). JWT checking is off because this
// Mojang check is the authentication.
//
// Deployed as the "avatar" edge function (verify_jwt: false). Table: migrations/0003_player_avatars.sql
import { createClient } from "npm:@supabase/supabase-js@2.49.4";

const PET_TYPES = new Set([
  "CAT", "WOLF", "FOX", "PARROT", "AXOLOTL", "BEE", "FROG", "RABBIT", "CHICKEN", "PIG", "SHEEP", "COW",
  "MOOSHROOM", "PANDA", "POLAR_BEAR", "OCELOT", "GOAT", "LLAMA", "TURTLE", "ARMADILLO", "SNIFFER", "ALLAY",
  "BAT", "COPPER_GOLEM", "HAPPY_GHAST", "MINI_PLAYER",
]);
const MODES = new Set(["FOLLOW", "LEFT_SHOULDER", "RIGHT_SHOULDER", "HEAD"]);
const CATEGORIES = new Set(["capes", "wings", "hats", "glasses", "ears", "tails", "auras"]);
const USERNAME = /^[A-Za-z0-9_]{1,16}$/;
const ITEM_ID = /^[a-z0-9_]{1,40}$/;
const COLOR = /^(#[0-9A-Fa-f]{6}|accent|rainbow)$/;

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "content-type, apikey, authorization",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const reply = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { ...cors, "Content-Type": "application/json" } });

const int = (v: unknown, min: number, max: number, fallback: number) =>
  typeof v === "number" && Number.isInteger(v) && v >= min && v <= max ? v : fallback;

// Printable text only, no line breaks or invisible characters, at most 24 characters.
function cleanName(v: unknown): string {
  if (typeof v !== "string") return "";
  return v.normalize("NFKC").replace(/[\p{C}\p{Zl}\p{Zp}]/gu, "").replace(/\s+/g, " ").trim().slice(0, 24);
}

function cleanData(raw: any) {
  const pets = Array.isArray(raw?.pets) ? raw.pets.slice(0, 3) : [];
  const cleanPets = [];
  for (const p of pets) {
    if (!p || !PET_TYPES.has(p.type)) continue;
    const player = typeof p.player === "string" && USERNAME.test(p.player) ? p.player : "";
    cleanPets.push({
      enabled: p.enabled !== false,
      type: p.type,
      name: cleanName(p.name),
      mode: MODES.has(p.mode) ? p.mode : "FOLLOW",
      size: int(p.size, 0, 3, 1),
      baby: p.baby === true,
      variant: int(p.variant, 0, 63, 0),
      collar: int(p.collar, 0, 15, 14),
      mine: p.mine !== false,
      player,
      cosmetics: p.cosmetics !== false,
      climb: p.climb === true,
    });
  }
  const equipped: Record<string, string> = {};
  for (const [k, v] of Object.entries(raw?.equipped ?? {})) {
    if (CATEGORIES.has(k) && typeof v === "string" && ITEM_ID.test(v)) equipped[k] = v;
  }
  const colors: Record<string, string> = {};
  for (const [k, v] of Object.entries(raw?.colors ?? {})) {
    if (Object.keys(colors).length >= 12) break;
    if (ITEM_ID.test(k) && typeof v === "string" && COLOR.test(v)) colors[k] = v;
  }
  return { v: 1, pets: cleanPets, equipped, colors };
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
  const username = String(body?.username ?? "");
  const serverId = String(body?.serverId ?? "");
  if (!USERNAME.test(username) || !/^-?[0-9a-f]{1,40}$/.test(serverId)) return reply(400, { error: "Bad request" });

  // Ask Mojang whether this player really just joined with this serverId.
  const check = await fetch(
    `https://sessionserver.mojang.com/session/minecraft/hasJoined?username=${encodeURIComponent(username)}&serverId=${encodeURIComponent(serverId)}`,
  );
  if (check.status !== 200) return reply(403, { error: "Couldn't verify your Minecraft account" });
  const profile = await check.json();
  const id = String(profile?.id ?? "");
  if (!/^[0-9a-f]{32}$/.test(id)) return reply(403, { error: "Couldn't verify your Minecraft account" });
  const uuid = `${id.slice(0, 8)}-${id.slice(8, 12)}-${id.slice(12, 16)}-${id.slice(16, 20)}-${id.slice(20)}`;
  const name = String(profile.name ?? username);

  const db = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, {
    auth: { persistSession: false },
  });

  if (body.remove === true) {
    const { error } = await db.from("player_avatars").delete().eq("uuid", uuid);
    if (error) return reply(500, { error: "Couldn't remove it" });
    return reply(200, { ok: true, removed: true });
  }

  // one save every 10 seconds per account is plenty
  const { data: existing } = await db.from("player_avatars").select("updated_at").eq("uuid", uuid).maybeSingle();
  if (existing && Date.now() - new Date(existing.updated_at).getTime() < 10_000) {
    return reply(429, { error: "Too fast - try again in a few seconds" });
  }

  const data = cleanData(body.data);
  const { error } = await db.from("player_avatars").upsert({ uuid, name, data, updated_at: new Date().toISOString() });
  if (error) return reply(500, { error: "Couldn't save it" });
  return reply(200, { ok: true, uuid, name });
});
