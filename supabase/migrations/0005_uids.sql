-- UIDs: every Minecraft account that claims one gets the next Lmg number, for good.
-- Low numbers are the rare ones (1-10 Founder, 11-100 OG, 101-1000 Early).
-- Only the "social" edge function hands them out, after Mojang confirms the account.
-- Anyone can look up the number for a list of accounts (it's shown on name tags).

create table if not exists public.lmg_uids (
  uuid uuid primary key,
  uid bigint generated always as identity unique,
  claimed_at timestamptz not null default now()
);
alter table public.lmg_uids enable row level security;
revoke all on public.lmg_uids from anon, authenticated;

create or replace function public.lmg_uids_of(p_uuids uuid[])
returns table (uuid uuid, uid bigint)
language sql
stable
security definer
set search_path = ''
as $$
  select u.uuid, u.uid from public.lmg_uids u where u.uuid = any (p_uuids[1:100]);
$$;
revoke all on function public.lmg_uids_of(uuid[]) from public;
grant execute on function public.lmg_uids_of(uuid[]) to anon, authenticated;
