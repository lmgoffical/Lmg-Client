-- Invites: "come and play with me". You can invite friends while you're on a server and
-- sharing it; they see a pop-up with a Join button for 5 minutes. Nothing else is stored, and
-- old invites are cleaned up. Turning Friends off deletes yours (they hang off lmg_players).

create table if not exists public.lmg_invites (
  from_uuid uuid not null references public.lmg_players on delete cascade,
  to_uuid uuid not null references public.lmg_players on delete cascade,
  created_at timestamptz not null default now(),
  primary key (from_uuid, to_uuid)
);
alter table public.lmg_invites enable row level security;
revoke all on public.lmg_invites from anon, authenticated;

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

-- Invite some friends (up to 30 at once). At most one invite to the same friend every 30 seconds.
create or replace function public.lmg_social_invite(p_token text, p_uuids uuid[])
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_me uuid := public.lmg_session_uuid(p_token);
  v_sent int;
begin
  if not exists (select 1 from public.lmg_presence pr where pr.uuid = v_me and pr.activity = 'server'
                   and pr.share_server and not pr.appear_offline and pr.server is not null
                   and pr.updated_at > now() - interval '100 seconds') then
    return jsonb_build_object('error', 'not_on_server', 'state', public.lmg_social_state(v_me));
  end if;
  with mine as (select to_uuid as other from public.lmg_friend_links where from_uuid = v_me),
       theirs as (select from_uuid as other from public.lmg_friend_links where to_uuid = v_me),
       friends as (select other from mine intersect select other from theirs)
  insert into public.lmg_invites as i (from_uuid, to_uuid, created_at)
  select v_me, f.other, now() from friends f where f.other = any (p_uuids[1:30])
  on conflict (from_uuid, to_uuid) do update set created_at = now()
    where i.created_at < now() - interval '30 seconds';
  get diagnostics v_sent = row_count;
  delete from public.lmg_invites where created_at < now() - interval '1 hour';
  return jsonb_build_object('sent', v_sent, 'state', public.lmg_social_state(v_me));
end;
$$;
revoke all on function public.lmg_social_invite(text, uuid[]) from public;
grant execute on function public.lmg_social_invite(text, uuid[]) to anon, authenticated;
