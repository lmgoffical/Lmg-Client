-- Climbing on someone without being friends or partners. Any two Lmg players standing together
-- can do it, but only with a yes: one asks, the other answers "yes this time" (2 hours),
-- "always" or "no". Asks can be switched off. Everything here hangs off lmg_players, so turning
-- Friends off or "Delete all my Lmg data" removes it.

create table if not exists public.lmg_climb_allows (
  owner uuid not null references public.lmg_players on delete cascade,     -- the one being climbed on
  climber uuid not null references public.lmg_players on delete cascade,   -- the one allowed to climb
  until timestamptz,                                                         -- null = always
  created_at timestamptz not null default now(),
  primary key (owner, climber),
  check (owner <> climber)
);
alter table public.lmg_climb_allows enable row level security;
revoke all on public.lmg_climb_allows from anon, authenticated;

-- asks, so nobody can ask the same person over and over
create table if not exists public.lmg_climb_asks (
  asker uuid not null references public.lmg_players on delete cascade,
  asked uuid not null references public.lmg_players on delete cascade,
  at timestamptz not null default now(),
  primary key (asker, asked)
);
alter table public.lmg_climb_asks enable row level security;
revoke all on public.lmg_climb_asks from anon, authenticated;

alter table public.lmg_players add column if not exists climb_asks boolean not null default true;

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
    -- climbing: what you allow your partner, and what your partner allows you
    'iAllowClimb', coalesce((select p.allow_climb from public.lmg_partners p where p.from_uuid = p_me
                              and p.to_uuid = (select other from partner limit 1)), false),
    'partnerAllowsClimb', coalesce((select p.allow_climb from public.lmg_partners p
                              where p.from_uuid = (select other from partner limit 1) and p.to_uuid = p_me), false),
    -- climbing without being friends: who said yes to you, and who you said yes to
    'climbAllowedBy', coalesce((select jsonb_agg(jsonb_build_object('uuid', a.owner, 'name', pl.name, 'until', a.until))
      from public.lmg_climb_allows a join public.lmg_players pl on pl.uuid = a.owner
      where a.climber = p_me and (a.until is null or a.until > now())), '[]'::jsonb),
    'climbers', coalesce((select jsonb_agg(jsonb_build_object('uuid', a.climber, 'name', pl.name, 'until', a.until) order by pl.name)
      from public.lmg_climb_allows a join public.lmg_players pl on pl.uuid = a.climber
      where a.owner = p_me and (a.until is null or a.until > now())), '[]'::jsonb),
    'climbAsks', coalesce((select pl.climb_asks from public.lmg_players pl where pl.uuid = p_me), true),
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
      from outgoing o join public.lmg_players pl on pl.uuid = o.other), '[]'::jsonb),
    -- friends asking you to come and play with them (for 5 minutes, while they're still there)
    'invites', coalesce((select jsonb_agg(jsonb_build_object(
        'uuid', pl.uuid, 'name', pl.name, 'server', pr.server, 'serverName', pr.server_name, 'at', i.created_at)
        order by i.created_at desc)
      from public.lmg_invites i
      join friends f on f.other = i.from_uuid
      join public.lmg_players pl on pl.uuid = i.from_uuid
      join public.lmg_presence pr on pr.uuid = i.from_uuid
      where i.to_uuid = p_me and i.created_at > now() - interval '5 minutes'
        and pr.updated_at > now() - interval '100 seconds' and not pr.appear_offline
        and pr.share_server and pr.activity = 'server' and pr.server is not null), '[]'::jsonb)
  );
$$;
revoke all on function public.lmg_social_state(uuid) from public, anon, authenticated;

-- Let people ask to climb on you (or not).
create or replace function public.lmg_social_climb_asks(p_token text, p_on boolean)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_me uuid := public.lmg_session_uuid(p_token);
begin
  update public.lmg_players set climb_asks = coalesce(p_on, true) where uuid = v_me;
  return jsonb_build_object('state', public.lmg_social_state(v_me));
end;
$$;
revoke all on function public.lmg_social_climb_asks(text, boolean) from public;
grant execute on function public.lmg_social_climb_asks(text, boolean) to anon, authenticated;

-- Take back a yes (one person, or everyone when p_climber is null). Gets them down straight away.
create or replace function public.lmg_social_climb_revoke(p_token text, p_climber uuid)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_me uuid := public.lmg_session_uuid(p_token);
begin
  delete from public.lmg_climb_allows where owner = v_me and (p_climber is null or climber = p_climber);
  -- get them down, unless they're your partner and you've said yes to that separately
  update public.lmg_presence pr set emote = null, emote_with = null, emote_room = null, emote_at = null
   where pr.emote_with = v_me and (p_climber is null or pr.uuid = p_climber)
     and not exists (select 1 from public.lmg_partners p
                      join public.lmg_partners q on q.from_uuid = p.to_uuid and q.to_uuid = p.from_uuid
                     where p.from_uuid = v_me and p.to_uuid = pr.uuid and p.allow_climb);
  return jsonb_build_object('state', public.lmg_social_state(v_me));
end;
$$;
revoke all on function public.lmg_social_climb_revoke(text, uuid) from public;
grant execute on function public.lmg_social_climb_revoke(text, uuid) to anon, authenticated;
