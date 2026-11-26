-- Deleting your data deletes all of it.
--
-- In game, "Delete all my Lmg data" goes through the "social" edge function (after Mojang
-- confirms the account) and removes: your friends account (sessions, status, friend links,
-- partner, invites - they all hang off lmg_players), your UID, and your shared pets and
-- cosmetics. It also calls delete_my_configs() below for the config codes you shared.
-- On the website, delete_my_account() removes your login, profile, threads and replies.

-- ---- shared config codes ----------------------------------------------------------------------
-- New shares carry the hash of a secret only the sharer's game knows, so they can delete them.
-- (Older shares were never linked to anyone, so there is nothing to tie them to a person.)
alter table public.shared_configs add column if not exists owner_hash text;
create index if not exists shared_configs_owner on public.shared_configs (owner_hash);

-- The network-address hash was only ever for the "20 shares an hour" limit: keep it an hour, no longer.
update public.shared_configs set ip_hash = null where ip_hash is not null and created_at < now() - interval '1 hour';

create or replace function public.share_config(p_name text, p_data text, p_owner text)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
  v_alphabet constant text := '23456789ABCDEFGHJKMNPQRSTUVWXYZ';
  v_hash text;
  v_existing text;
  v_ip text;
  v_ip_hash text;
  v_recent integer;
  v_code text;
  v_name text;
  v_owner text;
  i integer;
begin
  if p_data is null or left(p_data, 5) <> 'LMG1:' then
    raise exception 'not an Lmg config code';
  end if;
  if length(p_data) > 12000 then
    raise exception 'config code is too long';
  end if;
  if p_data !~ '^LMG1:[A-Za-z0-9_-]+$' then
    raise exception 'config code has invalid characters';
  end if;
  if p_owner is not null and p_owner !~ '^[A-Za-z0-9_-]{32,64}$' then
    raise exception 'bad owner key';
  end if;
  v_owner := case when p_owner is null then null else encode(extensions.digest(p_owner, 'sha256'), 'hex') end;

  v_name := left(coalesce(nullif(regexp_replace(coalesce(p_name, ''), '[^A-Za-z0-9 _-]', '', 'g'), ''), 'Shared config'), 24);

  -- forget network-address hashes older than an hour (they're only for the spam limit)
  update shared_configs set ip_hash = null where ip_hash is not null and created_at < now() - interval '1 hour';

  -- same config shared before? hand back the same code
  v_hash := md5(p_data);
  select code into v_existing from shared_configs where data_hash = v_hash;
  if v_existing is not null then
    return v_existing;
  end if;

  -- simple spam limit: 20 new shares per hour per network address (stored hashed, for an hour)
  v_ip := split_part(coalesce(current_setting('request.headers', true)::json ->> 'x-forwarded-for', ''), ',', 1);
  v_ip_hash := case when v_ip = '' then null else md5('lmg-salt:' || trim(v_ip)) end;
  if v_ip_hash is not null then
    select count(*) into v_recent from shared_configs
      where ip_hash = v_ip_hash and created_at > now() - interval '1 hour';
    if v_recent >= 20 then
      raise exception 'too many shares, try again later';
    end if;
  end if;

  loop
    v_code := '';
    for i in 1..6 loop
      v_code := v_code || substr(v_alphabet, 1 + floor(random() * length(v_alphabet))::int, 1);
    end loop;
    begin
      insert into shared_configs (code, name, data, data_hash, ip_hash, owner_hash)
        values (v_code, v_name, p_data, v_hash, v_ip_hash, v_owner);
      return v_code;
    exception when unique_violation then
      select code into v_existing from shared_configs where data_hash = v_hash;
      if v_existing is not null then
        return v_existing;
      end if;
    end;
  end loop;
end;
$$;

-- older game versions call it without an owner key
create or replace function public.share_config(p_name text, p_data text)
returns text
language sql
security definer
set search_path = public
as $$ select public.share_config(p_name, p_data, null); $$;

-- Deletes every config code shared with one of these secrets. Returns how many went.
create or replace function public.delete_my_configs(p_keys text[])
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  v_n integer;
begin
  delete from shared_configs
   where owner_hash is not null
     and owner_hash = any (select encode(extensions.digest(k, 'sha256'), 'hex') from unnest(p_keys[1:500]) k
                           where k ~ '^[A-Za-z0-9_-]{32,64}$');
  get diagnostics v_n = row_count;
  return v_n;
end;
$$;

revoke all on function public.share_config(text, text, text) from public;
revoke all on function public.share_config(text, text) from public;
revoke all on function public.delete_my_configs(text[]) from public;
grant execute on function public.share_config(text, text, text) to anon, authenticated;
grant execute on function public.share_config(text, text) to anon, authenticated;
grant execute on function public.delete_my_configs(text[]) to anon, authenticated;

-- ---- website account --------------------------------------------------------------------------
-- Deletes the signed-in user's login. Their profile goes with it, and with the profile all their
-- threads and replies (foreign keys cascade).
create or replace function public.delete_my_account()
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_me uuid := auth.uid();
begin
  if v_me is null then
    raise exception 'not signed in';
  end if;
  delete from auth.users where id = v_me;
end;
$$;
revoke all on function public.delete_my_account() from public, anon;
grant execute on function public.delete_my_account() to authenticated;
