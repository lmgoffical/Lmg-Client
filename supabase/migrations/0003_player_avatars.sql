-- Pets and cosmetics that Lmg players chose to share, so other Lmg players can see them.
-- Readable by anyone (it's what other players see in game). Only the "avatar" edge function
-- writes here, after Mojang confirms the player really owns that Minecraft account.
create table if not exists public.player_avatars (
  uuid uuid primary key,
  name text not null check (name ~ '^[A-Za-z0-9_]{1,16}$'),
  data jsonb not null check (pg_column_size(data) < 6000),
  updated_at timestamptz not null default now()
);

alter table public.player_avatars enable row level security;

drop policy if exists "Anyone can see shared avatars" on public.player_avatars;
create policy "Anyone can see shared avatars" on public.player_avatars
  for select to anon, authenticated using (true);

revoke all on public.player_avatars from anon, authenticated;
grant select on public.player_avatars to anon, authenticated;
