create extension if not exists pgcrypto;

create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  display_name text not null default 'My Profile',
  avatar_url text,
  is_kids boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.watchlist (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  media_kind text not null check (media_kind in ('movie','series','episode','live','sport','song','podcast')),
  media_id text not null,
  title text not null,
  poster_url text,
  created_at timestamptz not null default now(),
  unique(user_id, media_kind, media_id)
);

create table if not exists public.playback_progress (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  media_kind text not null,
  media_id text not null,
  season integer,
  episode integer,
  position_ms bigint not null default 0,
  duration_ms bigint not null default 0,
  updated_at timestamptz not null default now(),
  unique(user_id, media_kind, media_id, season, episode)
);

create table if not exists public.favorite_teams (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  provider text not null,
  team_id text not null,
  team_name text not null,
  created_at timestamptz not null default now(),
  unique(user_id, provider, team_id)
);

create table if not exists public.user_sources (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  source_type text not null check (source_type in ('m3u','xtream','plex','jellyfin','emby','stremio','nuvio','cloudstream','debrid','radio','podcast')),
  name text not null,
  endpoint text,
  username text,
  secret_ref text,
  enabled boolean not null default true,
  priority integer not null default 100,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.subscription_entitlements (
  user_id uuid primary key references auth.users(id) on delete cascade,
  plan text not null default 'free' check (plan in ('free','plus','pro')),
  status text not null default 'active',
  renews_at timestamptz,
  updated_at timestamptz not null default now()
);

alter table public.profiles enable row level security;
alter table public.watchlist enable row level security;
alter table public.playback_progress enable row level security;
alter table public.favorite_teams enable row level security;
alter table public.user_sources enable row level security;
alter table public.subscription_entitlements enable row level security;

grant select, insert, update, delete on public.profiles, public.watchlist, public.playback_progress, public.favorite_teams, public.user_sources to authenticated;
grant select on public.subscription_entitlements to authenticated;

create policy profiles_owner_select on public.profiles for select to authenticated using ((select auth.uid()) = id);
create policy profiles_owner_insert on public.profiles for insert to authenticated with check ((select auth.uid()) = id);
create policy profiles_owner_update on public.profiles for update to authenticated using ((select auth.uid()) = id) with check ((select auth.uid()) = id);

create policy watchlist_owner_all_select on public.watchlist for select to authenticated using ((select auth.uid()) = user_id);
create policy watchlist_owner_insert on public.watchlist for insert to authenticated with check ((select auth.uid()) = user_id);
create policy watchlist_owner_update on public.watchlist for update to authenticated using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy watchlist_owner_delete on public.watchlist for delete to authenticated using ((select auth.uid()) = user_id);

create policy progress_owner_select on public.playback_progress for select to authenticated using ((select auth.uid()) = user_id);
create policy progress_owner_insert on public.playback_progress for insert to authenticated with check ((select auth.uid()) = user_id);
create policy progress_owner_update on public.playback_progress for update to authenticated using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy progress_owner_delete on public.playback_progress for delete to authenticated using ((select auth.uid()) = user_id);

create policy teams_owner_select on public.favorite_teams for select to authenticated using ((select auth.uid()) = user_id);
create policy teams_owner_insert on public.favorite_teams for insert to authenticated with check ((select auth.uid()) = user_id);
create policy teams_owner_delete on public.favorite_teams for delete to authenticated using ((select auth.uid()) = user_id);

create policy sources_owner_select on public.user_sources for select to authenticated using ((select auth.uid()) = user_id);
create policy sources_owner_insert on public.user_sources for insert to authenticated with check ((select auth.uid()) = user_id);
create policy sources_owner_update on public.user_sources for update to authenticated using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy sources_owner_delete on public.user_sources for delete to authenticated using ((select auth.uid()) = user_id);

create policy entitlements_owner_select on public.subscription_entitlements for select to authenticated using ((select auth.uid()) = user_id);

create or replace function public.handle_new_user() returns trigger language plpgsql security invoker set search_path = '' as $$
begin
  insert into public.profiles (id, display_name) values (new.id, coalesce(new.raw_user_meta_data ->> 'display_name', 'My Profile'));
  insert into public.subscription_entitlements (user_id, plan, status) values (new.id, 'free', 'active');
  return new;
end;
$$;

revoke execute on function public.handle_new_user() from public, anon, authenticated;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created after insert on auth.users for each row execute procedure public.handle_new_user();
