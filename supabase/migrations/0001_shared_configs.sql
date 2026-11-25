-- Shared Lmg Client configs. Clients never touch the table directly:
-- they can only call share_config() and get_config() below.
create table public.shared_configs (
  code        text primary key,
  name        text not null,
  data        text not null,
  data_hash   text not null unique,
  ip_hash     text,
  created_at  timestamptz not null default now(),
  downloads   integer not null default 0
);

alter table public.shared_configs enable row level security;
-- (no policies on purpose: no direct reads/writes for anon or authenticated users)
revoke all on table public.shared_configs from anon, authenticated;

create index shared_configs_ip_recent on public.shared_configs (ip_hash, created_at);

-- Upload a config code ("LMG1:...") and get back a short code like "ABC234".
create or replace function public.share_config(p_name text, p_data text)
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

  v_name := left(coalesce(nullif(regexp_replace(coalesce(p_name, ''), '[^A-Za-z0-9 _-]', '', 'g'), ''), 'Shared config'), 24);

  -- same config shared before? hand back the same code
  v_hash := md5(p_data);
  select code into v_existing from shared_configs where data_hash = v_hash;
  if v_existing is not null then
    return v_existing;
  end if;

  -- simple spam limit: 20 new shares per hour per network address (stored hashed)
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
      insert into shared_configs (code, name, data, data_hash, ip_hash)
        values (v_code, v_name, p_data, v_hash, v_ip_hash);
      return v_code;
    exception when unique_violation then
      -- extremely rare code clash (or same config inserted at the same moment)
      select code into v_existing from shared_configs where data_hash = v_hash;
      if v_existing is not null then
        return v_existing;
      end if;
    end;
  end loop;
end;
$$;

-- Download a shared config by its short code (accepts "LMG-ABC234", "abc234", ...).
create or replace function public.get_config(p_code text)
returns table (name text, data text)
language plpgsql
security definer
set search_path = public
as $$
declare
  v_code text;
begin
  v_code := upper(regexp_replace(coalesce(p_code, ''), '[^A-Za-z0-9]', '', 'g'));
  if left(v_code, 3) = 'LMG' and length(v_code) = 9 then
    v_code := substr(v_code, 4);
  end if;
  return query
    update shared_configs s set downloads = s.downloads + 1
    where s.code = v_code
    returning s.name, s.data;
end;
$$;

revoke all on function public.share_config(text, text) from public;
revoke all on function public.get_config(text) from public;
grant execute on function public.share_config(text, text) to anon, authenticated;
grant execute on function public.get_config(text) to anon, authenticated;
