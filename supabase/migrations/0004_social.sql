-- Friends, online status, partners and emotes for Lmg Client.
--
-- Nothing here is readable directly: the tables have row level security on and no grants.
-- The game talks to them through the functions at the bottom, which need a session token.
-- A session token is only handed out by the "social" edge function after Mojang confirms
-- the player owns the account (same check as player_avatars). We store a SHA-256 of the
-- token, never the token itself.

create extension if not exists pgcrypto with schema extensions;

create table if not exists public.lmg_players (
  uuid uuid primary key,
  name text not null check (name ~ '^[A-Za-z0-9_]{1,16}$'),
  first_seen timestamptz not null default now(),
  last_seen timestamptz not null default now()
);
create index if not exists lmg_players_name_idx on public.lmg_players (lower(name));

create table if not exists public.lmg_sessions (
  token_hash text primary key check (token_hash ~ '^[0-9a-f]{64}$'),
  uuid uuid not null references public.lmg_players on delete cascade,
  created_at timestamptz not null default now(),
  expires_at timestamptz not null
);
create index if not exists lmg_sessions_uuid_idx on public.lmg_sessions (uuid);

create table if not exists public.lmg_presence (
  uuid uuid primary key references public.lmg_players on delete cascade,
  activity text not null default 'menu' check (activity in ('menu', 'singleplayer', 'server', 'realms', 'lan')),
  server text check (char_length(server) <= 255),
  server_name text check (char_length(server_name) <= 64),
  share_server boolean not null default true,
  appear_offline boolean not null default false,
  emote text check (emote ~ '^[a-z_]{1,24}$'),
  emote_room text check (emote_room ~ '^[0-9a-f]{16}$'),
  emote_at timestamptz,
  updated_at timestamptz not null default now()
);

-- A friendship is two links, one each way. One link on its own is a request.
create table if not exists public.lmg_friend_links (
  from_uuid uuid not null references public.lmg_players on delete cascade,
  to_uuid uuid not null references public.lmg_players on delete cascade,
  created_at timestamptz not null default now(),
  primary key (from_uuid, to_uuid),
  check (from_uuid <> to_uuid)
);
create index if not exists lmg_friend_links_to_idx on public.lmg_friend_links (to_uuid);

-- Partners: also two rows pointing at each other.
create table if not exists public.lmg_partners (
  from_uuid uuid primary key references public.lmg_players on delete cascade,
  to_uuid uuid not null references public.lmg_players on delete cascade,
  created_at timestamptz not null default now(),
  check (from_uuid <> to_uuid)
);
create index if not exists lmg_partners_to_idx on public.lmg_partners (to_uuid);

-- Signing key for emotes (made by the edge function on first use; only it can read this).
create table if not exists public.lmg_keys (
  id text primary key,
  private_jwk jsonb not null,
  public_key text not null,
  created_at timestamptz not null default now()
);

alter table public.lmg_players enable row level security;
alter table public.lmg_sessions enable row level security;
alter table public.lmg_presence enable row level security;
alter table public.lmg_friend_links enable row level security;
alter table public.lmg_partners enable row level security;
alter table public.lmg_keys enable row level security;
revoke all on public.lmg_players, public.lmg_sessions, public.lmg_presence,
  public.lmg_friend_links, public.lmg_partners, public.lmg_keys from anon, authenticated;

-- ---- helpers ------------------------------------------------------------------------------------

create or replace function public.lmg_session_uuid(p_token text)
returns uuid
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v uuid;
begin
  if p_token is null or char_length(p_token) < 32 or char_length(p_token) > 128 then
    raise exception 'bad_session' using errcode = 'P0001';
  end if;
  select s.uuid into v from public.lmg_sessions s
   where s.token_hash = encode(extensions.digest(p_token, 'sha256'), 'hex') and s.expires_at > now();
  if v is null then
    raise exception 'bad_session' using errcode = 'P0001';
  end if;
  return v;
end;
$$;
revoke all on function public.lmg_session_uuid(text) from public, anon, authenticated;

-- Everything the game shows in the Friends screen, for one player.
create or replace function public.lmg_social_state(p_me uuid)
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  with mine as (select to_uuid as other from public.lmg_friend_links where from_uuid = p_me),
       theirs as (select from_uuid as other from public.lmg_friend_links where to_uuid = p_me),
       friends as (select other from mine intersect select other from theirs),
       incoming as (select other from theirs except select other from mine),
       outgoing as (select other from mine except select other from theirs),
       partner as (
         select p.to_uuid as other from public.lmg_partners p
           join public.lmg_partners q on q.from_uuid = p.to_uuid and q.to_uuid = p_me
          where p.from_uuid = p_me),
       asked_partner as (select p.from_uuid as other from public.lmg_partners p where p.to_uuid = p_me
                          and not exists (select 1 from public.lmg_partners q where q.from_uuid = p_me and q.to_uuid = p.from_uuid))
  select jsonb_build_object(
    'me', p_me,
    'partner', (select other from partner limit 1),
    'partnerRequest', (select other from asked_partner a where a.other in (select other from friends) limit 1),
    'friends', coalesce((
      select jsonb_agg(jsonb_build_object(
        'uuid', pl.uuid,
        'name', pl.name,
        'online', pr.updated_at > now() - interval '100 seconds' and not pr.appear_offline,
        'activity', case when pr.updated_at > now() - interval '100 seconds' and not pr.appear_offline then pr.activity end,
        'server', case when pr.updated_at > now() - interval '100 seconds' and not pr.appear_offline
                        and pr.share_server and pr.activity = 'server' then pr.server end,
        'serverName', case when pr.updated_at > now() - interval '100 seconds' and not pr.appear_offline
                            and pr.share_server and pr.activity = 'server' then pr.server_name end,
        'lastSeen', case when pr.appear_offline then null else pl.last_seen end
      ) order by pl.name)
      from friends f join public.lmg_players pl on pl.uuid = f.other
      left join public.lmg_presence pr on pr.uuid = f.other), '[]'::jsonb),
    'incoming', coalesce((select jsonb_agg(jsonb_build_object('uuid', pl.uuid, 'name', pl.name) order by pl.name)
      from incoming i join public.lmg_players pl on pl.uuid = i.other), '[]'::jsonb),
    'outgoing', coalesce((select jsonb_agg(jsonb_build_object('uuid', pl.uuid, 'name', pl.name) order by pl.name)
      from outgoing o join public.lmg_players pl on pl.uuid = o.other), '[]'::jsonb)
  );
$$;
revoke all on function public.lmg_social_state(uuid) from public, anon, authenticated;

-- ---- what the game calls ------------------------------------------------------------------------

-- Heartbeat: "I'm online, doing this". Returns the whole friends state.
create or replace function public.lmg_social_sync(
  p_token text, p_activity text, p_server text, p_server_name text,
  p_share_server boolean, p_appear_offline boolean)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_me uuid := public.lmg_session_uuid(p_token);
  v_activity text := case when p_activity in ('menu', 'singleplayer', 'server', 'realms', 'lan') then p_activity else 'menu' end;
begin
  insert into public.lmg_presence as pr (uuid, activity, server, server_name, share_server, appear_offline, updated_at)
  values (v_me, v_activity,
          case when v_activity = 'server' then left(p_server, 255) end,
          case when v_activity = 'server' then left(p_server_name, 64) end,
          coalesce(p_share_server, true), coalesce(p_appear_offline, false), now())
  on conflict (uuid) do update set
    activity = excluded.activity, server = excluded.server, server_name = excluded.server_name,
    share_server = excluded.share_server, appear_offline = excluded.appear_offline, updated_at = now();
  if not coalesce(p_appear_offline, false) then
    update public.lmg_players set last_seen = now() where uuid = v_me;
  end if;
  return public.lmg_social_state(v_me);
end;
$$;

-- Leaving the game: show as offline straight away.
create or replace function public.lmg_social_offline(p_token text)
returns void
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_me uuid := public.lmg_session_uuid(p_token);
begin
  update public.lmg_presence set updated_at = 'epoch', emote = null where uuid = v_me;
end;
$$;

-- Send a friend request by name (or accept one, if they already asked you).
create or replace function public.lmg_social_add(p_token text, p_name text)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_me uuid := public.lmg_session_uuid(p_token);
  v_other uuid;
  v_name text;
begin
  if p_name is null or p_name !~ '^[A-Za-z0-9_]{1,16}$' then
    return jsonb_build_object('error', 'bad_name');
  end if;
  select uuid, name into v_other, v_name from public.lmg_players where lower(name) = lower(p_name)
   order by last_seen desc limit 1;
  if v_other is null then
    return jsonb_build_object('error', 'not_found');
  end if;
  if v_other = v_me then
    return jsonb_build_object('error', 'self');
  end if;
  if (select count(*) from public.lmg_friend_links where from_uuid = v_me) >= 300 then
    return jsonb_build_object('error', 'too_many');
  end if;
  insert into public.lmg_friend_links (from_uuid, to_uuid) values (v_me, v_other) on conflict do nothing;
  return jsonb_build_object(
    'name', v_name,
    'friends', exists (select 1 from public.lmg_friend_links where from_uuid = v_other and to_uuid = v_me),
    'state', public.lmg_social_state(v_me));
end;
$$;

-- Accept a request from someone (by uuid, from the Requests list).
create or replace function public.lmg_social_accept(p_token text, p_uuid uuid)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_me uuid := public.lmg_session_uuid(p_token);
begin
  if not exists (select 1 from public.lmg_friend_links where from_uuid = p_uuid and to_uuid = v_me) then
    return jsonb_build_object('error', 'no_request', 'state', public.lmg_social_state(v_me));
  end if;
  insert into public.lmg_friend_links (from_uuid, to_uuid) values (v_me, p_uuid) on conflict do nothing;
  return jsonb_build_object('state', public.lmg_social_state(v_me));
end;
$$;

-- Remove a friend, decline a request, or cancel your own request. Also ends a partnership.
create or replace function public.lmg_social_remove(p_token text, p_uuid uuid)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_me uuid := public.lmg_session_uuid(p_token);
begin
  delete from public.lmg_friend_links
   where (from_uuid = v_me and to_uuid = p_uuid) or (from_uuid = p_uuid and to_uuid = v_me);
  delete from public.lmg_partners
   where (from_uuid = v_me and to_uuid = p_uuid) or (from_uuid = p_uuid and to_uuid = v_me);
  return jsonb_build_object('state', public.lmg_social_state(v_me));
end;
$$;

-- Ask a friend to be your partner (or say yes to them). Null ends it.
create or replace function public.lmg_social_partner(p_token text, p_uuid uuid)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_me uuid := public.lmg_session_uuid(p_token);
begin
  if p_uuid is null then
    delete from public.lmg_partners where from_uuid = v_me;
    return jsonb_build_object('state', public.lmg_social_state(v_me));
  end if;
  if not (exists (select 1 from public.lmg_friend_links where from_uuid = v_me and to_uuid = p_uuid)
      and exists (select 1 from public.lmg_friend_links where from_uuid = p_uuid and to_uuid = v_me)) then
    return jsonb_build_object('error', 'not_friends', 'state', public.lmg_social_state(v_me));
  end if;
  insert into public.lmg_partners (from_uuid, to_uuid) values (v_me, p_uuid)
  on conflict (from_uuid) do update set to_uuid = excluded.to_uuid, created_at = now();
  return jsonb_build_object('state', public.lmg_social_state(v_me));
end;
$$;

-- Emotes that are still playing (like sitting), for players who walk up later.
-- Readable by anyone, like shared avatars: only the edge function writes emotes.
create or replace function public.lmg_active_emotes(p_uuids uuid[], p_room text)
returns table (uuid uuid, emote text, started_at timestamptz)
language sql
stable
security definer
set search_path = ''
as $$
  select pr.uuid, pr.emote, pr.emote_at from public.lmg_presence pr
   where pr.uuid = any (p_uuids[1:60]) and pr.emote is not null and pr.emote_room = p_room
     and pr.emote_at > now() - interval '10 minutes' and pr.updated_at > now() - interval '100 seconds';
$$;

revoke all on function public.lmg_social_sync(text, text, text, text, boolean, boolean),
  public.lmg_social_offline(text), public.lmg_social_add(text, text), public.lmg_social_accept(text, uuid),
  public.lmg_social_remove(text, uuid), public.lmg_social_partner(text, uuid),
  public.lmg_active_emotes(uuid[], text) from public;
grant execute on function public.lmg_social_sync(text, text, text, text, boolean, boolean),
  public.lmg_social_offline(text), public.lmg_social_add(text, text), public.lmg_social_accept(text, uuid),
  public.lmg_social_remove(text, uuid), public.lmg_social_partner(text, uuid),
  public.lmg_active_emotes(uuid[], text) to anon, authenticated;

-- Turning friends off deletes everything about you: status, friends, requests, partner, sessions.
create or replace function public.lmg_social_forget(p_token text)
returns void
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_me uuid := public.lmg_session_uuid(p_token);
begin
  delete from public.lmg_players where uuid = v_me;   -- presence, links, partners and sessions go with it
end;
$$;
revoke all on function public.lmg_social_forget(text) from public;
grant execute on function public.lmg_social_forget(text) to anon, authenticated;
