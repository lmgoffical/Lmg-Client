-- Climbing is a setting now, not a question: "Who can climb on me" - nobody, my partner, my
-- friends, or everyone. No asking, no answers, nothing stored per person. The asking tables
-- from 0009 go away.

alter table public.lmg_players add column if not exists climb_who text not null default 'nobody'
  check (climb_who in ('nobody', 'partner', 'friends', 'everyone'));

-- people who had said yes to their partner keep exactly that
update public.lmg_players pl set climb_who = 'partner'
 where exists (select 1 from public.lmg_partners p where p.from_uuid = pl.uuid and p.allow_climb);

drop function if exists public.lmg_social_climb_asks(text, boolean);
drop function if exists public.lmg_social_climb_revoke(text, uuid);
drop table if exists public.lmg_climb_asks;
drop table if exists public.lmg_climb_allows;
alter table public.lmg_players drop column if exists climb_asks;

-- Can this climber sit on / play around this owner, by the owner's setting?
create or replace function public.lmg_may_climb(p_climber uuid, p_owner uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select case
    when p_climber is null or p_owner is null or p_climber = p_owner then false
    else coalesce((
      select case pl.climb_who
        when 'everyone' then true
        when 'nobody' then false
        when 'partner' then exists (select 1 from public.lmg_partners a join public.lmg_partners b
                                     on b.from_uuid = a.to_uuid and b.to_uuid = a.from_uuid
                                    where a.from_uuid = p_owner and a.to_uuid = p_climber)
        else exists (select 1 from public.lmg_friend_links a join public.lmg_friend_links b
                      on b.from_uuid = a.to_uuid and b.to_uuid = a.from_uuid
                     where a.from_uuid = p_owner and a.to_uuid = p_climber)
          or exists (select 1 from public.lmg_partners a join public.lmg_partners b
                      on b.from_uuid = a.to_uuid and b.to_uuid = a.from_uuid
                     where a.from_uuid = p_owner and a.to_uuid = p_climber)
      end
      from public.lmg_players pl where pl.uuid = p_owner), false)
  end;
$$;
revoke all on function public.lmg_may_climb(uuid, uuid) from public, anon, authenticated;

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
    -- climbing: who you let climb on you, and whether your partner lets you
    'climbWho', coalesce((select pl.climb_who from public.lmg_players pl where pl.uuid = p_me), 'nobody'),
    'iAllowClimb', coalesce((select pl.climb_who <> 'nobody' from public.lmg_players pl where pl.uuid = p_me), false),
    'partnerAllowsClimb', coalesce(public.lmg_may_climb(p_me, (select other from partner limit 1)), false),
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

-- Your setting. Anyone it no longer covers gets down straight away.
create or replace function public.lmg_social_climb_who(p_token text, p_who text)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_me uuid := public.lmg_session_uuid(p_token);
begin
  if p_who not in ('nobody', 'partner', 'friends', 'everyone') then
    raise exception 'bad setting' using errcode = 'P0001';
  end if;
  update public.lmg_players set climb_who = p_who where uuid = v_me;
  update public.lmg_presence pr set emote = null, emote_with = null, emote_room = null, emote_at = null
   where pr.emote_with = v_me and not public.lmg_may_climb(pr.uuid, v_me);
  return jsonb_build_object('state', public.lmg_social_state(v_me));
end;
$$;
revoke all on function public.lmg_social_climb_who(text, text) from public;
grant execute on function public.lmg_social_climb_who(text, text) to anon, authenticated;

-- Older game versions' "Let my partner climb on me" switch: on = partner, off = nobody.
create or replace function public.lmg_social_allow_climb(p_token text, p_allow boolean)
returns jsonb
language sql
volatile
security definer
set search_path = ''
as $$
  select public.lmg_social_climb_who(p_token, case when coalesce(p_allow, false) then 'partner' else 'nobody' end);
$$;
revoke all on function public.lmg_social_allow_climb(text, boolean) from public;
grant execute on function public.lmg_social_allow_climb(text, boolean) to anon, authenticated;

-- Which of these players (standing near you) let you climb on them.
create or replace function public.lmg_climb_ok(p_token text, p_uuids uuid[])
returns uuid[]
language sql
stable
security definer
set search_path = ''
as $$
  select coalesce(array_agg(u), '{}') from unnest(p_uuids[1:40]) u
   where public.lmg_may_climb(public.lmg_session_uuid(p_token), u);
$$;
revoke all on function public.lmg_climb_ok(text, uuid[]) from public;
grant execute on function public.lmg_climb_ok(text, uuid[]) to anon, authenticated;

alter table public.lmg_partners drop column if exists allow_climb;
