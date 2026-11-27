// Lmg Client: receives a crash report a player chose to send, stores it, and tells the developer on Discord.
//
// - No login: anyone can send, so everything is size-checked, cleaned and rate-limited.
// - Same crash (same error + top of the stack) = one row with a count, so a crash loop doesn't flood anything.
// - Discord (secret DISCORD_CRASH_WEBHOOK, never in the game) hears about a crash the first time, and again
//   when it reaches 3, 10, 25, 100... reports. Uploads are capped at 5 an hour per install and 300 an hour in total.
// Deployed as the "crash" edge function (verify_jwt: false). Tables: migrations/0012_crash_reports.sql
import { createClient } from "npm:@supabase/supabase-js@2.49.4";

const MAX_REPORT = 200_000;
const NOTIFY_AT = new Set([1, 3, 10, 25, 100, 250, 1000]);

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "content-type, apikey, authorization",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const reply = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { ...cors, "Content-Type": "application/json" } });

const short = (v: unknown, n: number) => (typeof v === "string" ? v : "").replace(/[\u0000-\u001f]/g, " ").trim().slice(0, n);

async function sha256(text: string): Promise<string> {
  const bytes = new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text)));
  return [...bytes].map((b) => b.toString(16).padStart(2, "0")).join("");
}

/** Second clean-up on the server (the game already does this): paths, IPs, emails, tokens. */
function scrub(text: string): string {
  return text
    .replace(/([A-Za-z]:[\\/]+(?:Users|Documents and Settings)[\\/]+)[^\\/\s"']+/gi, "$1<user>")
    .replace(/(\/(?:home|Users)\/)[^/\s"']+/g, "$1<user>")
    .replace(/\b\d{1,3}(?:\.\d{1,3}){3}(?::\d+)?\b/g, "<ip>")
    .replace(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/g, "<email>")
    .replace(/(--accessToken\s+)\S+/gi, "$1<hidden>")
    .replace(/(token["'=:\s]+)[A-Za-z0-9._-]{20,}/gi, "$1<hidden>")
    .replace(/\beyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{5,}/g, "<hidden>");
}

/** The error line ("java.lang.NullPointerException: ...") and the classes/methods of the first six "at ..." lines. */
function fingerprintSource(report: string): { summary: string; key: string; lmg: boolean } {
  const lines = report.split(/\r?\n/);
  let i = lines.findIndex((l) => /^Description:/.test(l));
  let errorLine = "";
  for (let k = Math.max(0, i); k < lines.length; k++) {
    if (/^[\w.$]+(Exception|Error|Throwable)\b/.test(lines[k].trim())) {
      errorLine = lines[k].trim();
      i = k;
      break;
    }
  }
  const frames: string[] = [];
  for (let k = i + 1; k < lines.length && frames.length < 6; k++) {
    const m = lines[k].trim().match(/^at\s+([\w.$<>]+)/);
    if (m) frames.push(m[1]);
    else if (frames.length) break;
  }
  const type = errorLine.split(":")[0] || "unknown";
  const description = (lines.find((l) => /^Description:/.test(l)) ?? "").replace(/^Description:\s*/, "");
  return {
    summary: short(errorLine || description || "Crash", 300),
    key: type + "|" + frames.join("|"),
    lmg: /dev\.lmg|lmgclient/i.test(report),
  };
}

async function tellDiscord(row: {
  id: number; count: number; installs: number; version: string; minecraft: string; edition: string;
  summary: string; mentions_lmg: boolean; report: string; first: boolean;
}) {
  const hook = Deno.env.get("DISCORD_CRASH_WEBHOOK");
  if (!hook || !/^https:\/\/(discord|discordapp)\.com\/api\/webhooks\//.test(hook)) return;
  const title = row.first ? `New crash #${row.id}` : `Crash #${row.id} happened again (${row.count} times)`;
  const embed = {
    title,
    color: row.mentions_lmg ? 0xff5a5a : 0xf2c14e,
    description: "```\n" + row.summary.replace(/```/g, "'''").slice(0, 900) + "\n```",
    fields: [
      { name: "Lmg Client", value: `${row.version || "?"} (${row.edition})`, inline: true },
      { name: "Minecraft", value: row.minecraft || "?", inline: true },
      { name: "Seen", value: `${row.count}x by ${row.installs} player${row.installs === 1 ? "" : "s"}`, inline: true },
      { name: "Lmg in the stack?", value: row.mentions_lmg ? "Yes - probably ours" : "No - maybe another mod or Minecraft", inline: false },
    ],
    footer: { text: "Full report attached (names, paths and IPs removed)" },
  };
  const form = new FormData();
  form.append("payload_json", JSON.stringify({ username: "Lmg crash reports", embeds: [embed], allowed_mentions: { parse: [] } }));
  form.append("files[0]", new Blob([row.report], { type: "text/plain" }), `crash-${row.id}.txt`);
  try {
    await fetch(hook, { method: "POST", body: form });
  } catch (_) {
    // Discord being down never loses the report: it's already stored.
  }
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response(null, { headers: cors });
  if (req.method !== "POST") return reply(405, { error: "POST only" });
  const len = Number(req.headers.get("content-length") ?? "0");
  if (len > MAX_REPORT * 2 + 4096) return reply(413, { error: "Too big" });

  let body: any;
  try {
    body = await req.json();
  } catch {
    return reply(400, { error: "Bad JSON" });
  }
  const install = short(body?.installId, 64);
  const raw = typeof body?.report === "string" ? body.report : "";
  if (!/^[0-9a-f-]{16,64}$/i.test(install) || raw.length < 50) return reply(400, { error: "Bad request" });
  const report = scrub(raw.slice(0, MAX_REPORT));
  const version = short(body?.version, 32);
  const minecraft = short(body?.minecraft, 32);
  const edition = body?.edition === "1.8.9" ? "1.8.9" : "main";

  const db = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, {
    auth: { persistSession: false },
  });
  const installHash = await sha256("lmg-crash|" + install);
  const hourAgo = new Date(Date.now() - 3600_000).toISOString();

  // limits: 5 reports an hour from one install, 300 an hour from everyone
  const { count: mine } = await db.from("lmg_crash_events").select("id", { count: "exact", head: true })
    .eq("install_hash", installHash).gte("created_at", hourAgo);
  if ((mine ?? 0) >= 5) return reply(429, { error: "Thanks - we already have your recent crashes." });
  const { count: everyone } = await db.from("lmg_crash_events").select("id", { count: "exact", head: true })
    .gte("created_at", hourAgo);
  if ((everyone ?? 0) >= 300) return reply(429, { error: "Too many reports right now - try later." });

  const fp = fingerprintSource(report);
  const fingerprint = await sha256(edition + "|" + fp.key);
  const { data: seenBefore } = await db.from("lmg_crash_events").select("id").eq("fingerprint", fingerprint)
    .eq("install_hash", installHash).limit(1);
  await db.from("lmg_crash_events").insert({ fingerprint, install_hash: installHash });

  const { data: existing } = await db.from("lmg_crashes").select("id, count, installs, status").eq("fingerprint", fingerprint).maybeSingle();
  let row;
  if (existing) {
    const { data, error } = await db.from("lmg_crashes").update({
      count: existing.count + 1,
      installs: existing.installs + (seenBefore && seenBefore.length ? 0 : 1),
      last_seen: new Date().toISOString(),
      version, minecraft, report,
      // a crash marked fixed that comes back is new again (its version shows whether the fix shipped)
      status: existing.status === "fixed" ? "new" : existing.status,
    }).eq("id", existing.id).select("id, count, installs").single();
    if (error) return reply(500, { error: "Couldn't save it" });
    row = { ...data, first: false };
  } else {
    const { data, error } = await db.from("lmg_crashes").insert({
      fingerprint, version, minecraft, edition, summary: fp.summary, mentions_lmg: fp.lmg, report,
    }).select("id, count, installs").single();
    if (error) return reply(500, { error: "Couldn't save it" });
    row = { ...data, first: true };
  }

  if (NOTIFY_AT.has(row.count)) {
    await tellDiscord({ ...row, version, minecraft, edition, summary: fp.summary, mentions_lmg: fp.lmg, report });
  }
  return reply(200, { ok: true, id: row.id, seen: row.count });
});
