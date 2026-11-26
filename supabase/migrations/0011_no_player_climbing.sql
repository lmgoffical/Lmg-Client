-- Players climbing on each other is gone (pets climbing on you is a setting in the game and
-- never touches the server). Everything that was only for it goes: the setting, the checks,
-- and "who an emote is with".

drop function if exists public.lmg_climb_ok(text, uuid[]);
drop function if exists public.lmg_social_allow_climb(text, boolean);
drop function if exists public.lmg_social_climb_who(text, text);

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

drop function if exists public.lmg_may_climb(uuid, uuid);
alter table public.lmg_players drop column if exists climb_who;

drop function if exists public.lmg_active_emotes(uuid[], text);
create function public.lmg_active_emotes(p_uuids uuid[], p_room text)
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
revoke all on function public.lmg_active_emotes(uuid[], text) from public;
grant execute on function public.lmg_active_emotes(uuid[], text) to anon, authenticated;

update public.lmg_presence set emote = null, emote_room = null, emote_at = null
 where emote in ('perch_head', 'perch_shoulder', 'play');
alter table public.lmg_presence drop column if exists emote_with;
