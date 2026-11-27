-- Crash reports from players who agreed to send them. One row per distinct crash (same error + same
-- top of the stack = same fingerprint), with how many times it happened. Nobody can read these
-- through the public API: only the "crash" edge function (service role) writes, and only the
-- project owner reads them.
create table if not exists public.lmg_crashes (
  id bigint generated always as identity primary key,
  fingerprint text not null unique,
  first_seen timestamptz not null default now(),
  last_seen timestamptz not null default now(),
  count integer not null default 1,
  installs integer not null default 1,
  version text not null default '',
  minecraft text not null default '',
  edition text not null default 'main',
  summary text not null default '',
  mentions_lmg boolean not null default false,
  report text not null default '',
  status text not null default 'new' check (status in ('new', 'looking', 'fixed', 'ignored')),
  notes text not null default ''
);
alter table public.lmg_crashes enable row level security;
revoke all on public.lmg_crashes from anon, authenticated;

-- Every upload, for rate limits and counting different players (install ids are hashed).
create table if not exists public.lmg_crash_events (
  id bigint generated always as identity primary key,
  fingerprint text not null,
  install_hash text not null,
  created_at timestamptz not null default now()
);
create index if not exists lmg_crash_events_recent on public.lmg_crash_events (created_at);
create index if not exists lmg_crash_events_install on public.lmg_crash_events (install_hash, created_at);
alter table public.lmg_crash_events enable row level security;
revoke all on public.lmg_crash_events from anon, authenticated;
