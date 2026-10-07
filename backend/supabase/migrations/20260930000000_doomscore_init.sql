-- ════════════════════════════════════════════════════════════════════════════
-- Doomscore — initial schema for YOUR Supabase project.
-- Security model:
--   • Row-Level Security on every table. Clients can read their own rows and
--     their friends' rows only. No client can write stats directly.
--   • Stats arrive through the `ingest` Edge Function, authenticated by a
--     scoped, revocable device token (only its SHA-256 hash is stored), and
--     are validated here (monotonic totals, rate caps, day window).
--   • Friendships are created only through invite codes (RPC), both ways.
-- ════════════════════════════════════════════════════════════════════════════

create extension if not exists pgcrypto with schema extensions;
create extension if not exists citext with schema extensions;

-- ─── Tables ─────────────────────────────────────────────────────────────────

create table public.profiles (
  id            uuid primary key references auth.users (id) on delete cascade,
  handle        extensions.citext not null unique
                check (handle::text ~ '^[a-z0-9_.]{3,20}$'),
  display_name  text not null check (char_length(display_name) between 1 and 30),
  emoji         text not null default '🫠' check (char_length(emoji) between 1 and 8),
  color         text not null default '#8A5CFF' check (color ~ '^#[0-9A-Fa-f]{6}$'),
  daily_goal    int  not null default 100 check (daily_goal between 5 and 2000),
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now()
);

create table public.friendships (
  user_id    uuid not null references public.profiles (id) on delete cascade,
  friend_id  uuid not null references public.profiles (id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (user_id, friend_id),
  check (user_id <> friend_id)
);
create index friendships_friend_idx on public.friendships (friend_id);

create table public.invites (
  code        text primary key,
  inviter_id  uuid not null references public.profiles (id) on delete cascade,
  created_at  timestamptz not null default now(),
  expires_at  timestamptz not null default now() + interval '14 days',
  max_uses    int not null default 25,
  uses        int not null default 0
);
create index invites_inviter_idx on public.invites (inviter_id);

create table public.daily_stats (
  user_id        uuid not null references public.profiles (id) on delete cascade,
  day            date not null,
  app            text not null check (app in ('instagram', 'youtube', 'tiktok', 'snapchat', 'other')),
  reels          int  not null default 0 check (reels between 0 and 20000),
  watch_seconds  int  not null default 0 check (watch_seconds between 0 and 86400),
  ads_skipped    int  not null default 0 check (ads_skipped between 0 and 20000),
  flags          int  not null default 0,
  updated_at     timestamptz not null default now(),
  primary key (user_id, day, app)
);
create index daily_stats_day_idx on public.daily_stats (day);

create table public.device_credentials (
  id            uuid primary key default gen_random_uuid(),
  user_id       uuid not null references public.profiles (id) on delete cascade,
  token_hash    bytea not null unique,
  label         text,
  created_at    timestamptz not null default now(),
  last_used_at  timestamptz,
  last_ingest_at timestamptz,
  revoked_at    timestamptz
);
create index device_credentials_user_idx on public.device_credentials (user_id);

create table public.live_activity_tokens (
  token                  text primary key check (token ~ '^[0-9a-f]{32,400}$'),
  user_id                uuid not null references auth.users (id) on delete cascade,
  kind                   text not null check (kind in ('update', 'start')),
  apns_env               text not null check (apns_env in ('sandbox', 'production')),
  updated_at             timestamptz not null default now(),
  last_push_at           timestamptz,
  last_high_priority_at  timestamptz
);
create index live_activity_tokens_user_idx on public.live_activity_tokens (user_id, kind);

create table public.rate_limits (
  user_id       uuid not null references auth.users (id) on delete cascade,
  bucket        text not null,
  window_start  timestamptz not null default now(),
  hits          int not null default 0,
  primary key (user_id, bucket)
);

-- ─── Row-Level Security ─────────────────────────────────────────────────────

alter table public.profiles             enable row level security;
alter table public.friendships          enable row level security;
alter table public.invites              enable row level security;
alter table public.daily_stats          enable row level security;
alter table public.device_credentials   enable row level security;
alter table public.live_activity_tokens enable row level security;
alter table public.rate_limits          enable row level security;

-- Profiles: yourself + your friends.
create policy profiles_select on public.profiles
  for select to authenticated
  using (
    id = (select auth.uid())
    or exists (
      select 1 from public.friendships f
      where f.user_id = (select auth.uid()) and f.friend_id = profiles.id
    )
  );

create policy profiles_insert on public.profiles
  for insert to authenticated
  with check (id = (select auth.uid()));

create policy profiles_update on public.profiles
  for update to authenticated
  using (id = (select auth.uid()))
  with check (id = (select auth.uid()));

-- Friendships: read your own edges only; writes go through RPCs.
create policy friendships_select on public.friendships
  for select to authenticated
  using (user_id = (select auth.uid()));

-- Invites: read your own codes only.
create policy invites_select on public.invites
  for select to authenticated
  using (inviter_id = (select auth.uid()));

-- Stats: yourself + your friends. No client writes.
create policy stats_select on public.daily_stats
  for select to authenticated
  using (
    user_id = (select auth.uid())
    or exists (
      select 1 from public.friendships f
      where f.user_id = (select auth.uid()) and f.friend_id = daily_stats.user_id
    )
  );

-- device_credentials, live_activity_tokens, rate_limits: no policies → no
-- client access at all (service role / security-definer functions only).

-- Least privilege: start from nothing, then grant exactly what the policies need.
revoke all on all tables in schema public from anon, authenticated;
grant usage on schema public to anon, authenticated, service_role;
grant select, insert, update on public.profiles to authenticated;
grant select on public.friendships, public.invites, public.daily_stats to authenticated;
-- Edge Functions use the service role (bypasses RLS); make its access explicit
-- so it doesn't depend on the project's "expose new tables" default.
grant all on all tables in schema public to service_role;

-- ─── Helpers ────────────────────────────────────────────────────────────────

create or replace function public.touch_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.updated_at := now();
  return new;
end;
$$;

create trigger profiles_touch before update on public.profiles
  for each row execute function public.touch_updated_at();

-- Fixed-window rate limiter. Raises 'rate_limited' when exceeded.
create or replace function public._hit_rate_limit(p_user uuid, p_bucket text, p_max int, p_window interval)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_hits int;
begin
  insert into public.rate_limits as rl (user_id, bucket, window_start, hits)
  values (p_user, p_bucket, now(), 1)
  on conflict (user_id, bucket) do update set
    hits = case when rl.window_start < now() - p_window then 1 else rl.hits + 1 end,
    window_start = case when rl.window_start < now() - p_window then now() else rl.window_start end
  returning hits into v_hits;

  if v_hits > p_max then
    raise exception 'rate_limited' using errcode = 'P0001';
  end if;
end;
$$;

-- ─── Client RPCs (authenticated) ────────────────────────────────────────────

-- Issues a scoped device token for the broadcast extension. The plaintext is
-- returned once; only its SHA-256 is stored. Max 5 active devices per user.
create or replace function public.register_device(p_label text default null)
returns text
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_me uuid := auth.uid();
  v_token text;
begin
  if v_me is null then raise exception 'not_authenticated' using errcode = '28000'; end if;
  if not exists (select 1 from public.profiles where id = v_me) then
    raise exception 'profile_required' using errcode = 'P0001';
  end if;
  perform public._hit_rate_limit(v_me, 'register_device', 20, interval '1 day');

  v_token := encode(extensions.gen_random_bytes(32), 'hex');
  insert into public.device_credentials (user_id, token_hash, label)
  values (v_me, extensions.digest(v_token, 'sha256'), left(p_label, 60));

  update public.device_credentials
     set revoked_at = now()
   where user_id = v_me
     and revoked_at is null
     and id not in (
       select id from public.device_credentials
        where user_id = v_me and revoked_at is null
        order by created_at desc
        limit 5
     );
  return v_token;
end;
$$;

create or replace function public.create_invite()
returns table (invite_code text, invite_expires_at timestamptz)
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_me uuid := auth.uid();
  v_alphabet constant text := 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';
  v_code text;
  v_expires timestamptz;
  v_bytes bytea;
  i int;
begin
  if v_me is null then raise exception 'not_authenticated' using errcode = '28000'; end if;
  if not exists (select 1 from public.profiles where id = v_me) then
    raise exception 'profile_required' using errcode = 'P0001';
  end if;

  -- Reuse a fresh active invite instead of minting new ones.
  select inv.code, inv.expires_at into v_code, v_expires
    from public.invites inv
   where inv.inviter_id = v_me
     and inv.expires_at > now() + interval '2 days'
     and inv.uses < inv.max_uses
   order by inv.created_at desc
   limit 1;

  if v_code is null then
    perform public._hit_rate_limit(v_me, 'create_invite', 30, interval '1 day');
    loop
      v_bytes := extensions.gen_random_bytes(10);
      v_code := '';
      for i in 0..9 loop
        v_code := v_code || substr(v_alphabet, (get_byte(v_bytes, i) % length(v_alphabet)) + 1, 1);
      end loop;
      exit when not exists (select 1 from public.invites inv where inv.code = v_code);
    end loop;
    v_expires := now() + interval '14 days';
    insert into public.invites (code, inviter_id, expires_at) values (v_code, v_me, v_expires);
  end if;

  return query select v_code, v_expires;
end;
$$;

create or replace function public.accept_invite(p_code text)
returns table (friend_id uuid, friend_handle text, friend_display_name text, friend_emoji text)
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_me uuid := auth.uid();
  v_code text := upper(regexp_replace(coalesce(p_code, ''), '[^A-Za-z0-9]', '', 'g'));
  v_inviter uuid;
  v_expires timestamptz;
  v_uses int;
  v_max int;
  v_count int;
begin
  if v_me is null then raise exception 'not_authenticated' using errcode = '28000'; end if;
  if not exists (select 1 from public.profiles where id = v_me) then
    raise exception 'profile_required' using errcode = 'P0001';
  end if;
  -- Throttle guessing.
  perform public._hit_rate_limit(v_me, 'accept_invite', 30, interval '1 hour');

  select inv.inviter_id, inv.expires_at, inv.uses, inv.max_uses
    into v_inviter, v_expires, v_uses, v_max
    from public.invites inv
   where inv.code = v_code
   for update;

  if v_inviter is null or v_expires < now() or v_uses >= v_max then
    raise exception 'invite_invalid' using errcode = 'P0001';
  end if;
  if v_inviter = v_me then
    raise exception 'invite_self' using errcode = 'P0001';
  end if;

  select count(*) into v_count from public.friendships fr where fr.user_id = v_me;
  if v_count >= 200 then
    raise exception 'too_many_friends' using errcode = 'P0001';
  end if;

  insert into public.friendships (user_id, friend_id)
  values (v_me, v_inviter), (v_inviter, v_me)
  on conflict do nothing;

  update public.invites inv set uses = inv.uses + 1 where inv.code = v_code;

  return query
    select p.id, p.handle::text, p.display_name, p.emoji
      from public.profiles p
     where p.id = v_inviter;
end;
$$;

create or replace function public.remove_friend(p_friend uuid)
returns void
language sql
security definer
set search_path = ''
as $$
  delete from public.friendships
   where (user_id = auth.uid() and friend_id = p_friend)
      or (user_id = p_friend and friend_id = auth.uid());
$$;

-- Friends leaderboard for a day / ISO week / month. Includes the caller.
create or replace function public.get_leaderboard(p_period text, p_day date)
returns table (
  user_id uuid, handle text, display_name text, emoji text, color text, reels bigint, is_me boolean
)
language sql
stable
security definer
set search_path = ''
as $$
  with members as (
    select auth.uid() as uid
    union
    select f.friend_id from public.friendships f where f.user_id = auth.uid()
  ),
  bounds as (
    select
      case p_period
        when 'week'  then date_trunc('week', p_day::timestamp)::date
        when 'month' then date_trunc('month', p_day::timestamp)::date
        else p_day
      end as d0,
      case p_period
        when 'week'  then (date_trunc('week', p_day::timestamp) + interval '6 days')::date
        when 'month' then (date_trunc('month', p_day::timestamp) + interval '1 month' - interval '1 day')::date
        else p_day
      end as d1
  )
  select p.id,
         p.handle::text,
         p.display_name,
         p.emoji,
         p.color,
         coalesce(sum(s.reels) filter (where (s.flags & 1) = 0 or s.user_id = auth.uid()), 0)::bigint,
         p.id = auth.uid()
    from members m
    join public.profiles p on p.id = m.uid
    cross join bounds b
    left join public.daily_stats s
      on s.user_id = p.id and s.day between b.d0 and b.d1
   where auth.uid() is not null
   group by p.id, p.handle, p.display_name, p.emoji, p.color
   order by 6 desc, 2 asc;
$$;

create or replace function public.delete_account()
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  if auth.uid() is null then raise exception 'not_authenticated' using errcode = '28000'; end if;
  delete from auth.users where id = auth.uid();
end;
$$;

-- ─── Server-only functions (service role, used by Edge Functions) ───────────

-- Resolves a device token hash to its user; bumps last_used_at.
create or replace function public.device_user(p_token_hash text)
returns uuid
language sql
security definer
set search_path = ''
as $$
  update public.device_credentials
     set last_used_at = now()
   where token_hash = decode(p_token_hash, 'hex')
     and revoked_at is null
  returning user_id;
$$;

-- Validates and stores absolute per-app totals for one day.
--   • day must be within [UTC today − 2, UTC today + 1] (time zones + offline)
--   • totals never decrease (idempotent retries, no "undo" cheating)
--   • growth is capped at ~1 reel / 1.2 s since the last update (+ burst);
--     clamped rows are flagged and hidden from friends' leaderboards
--   • ≤ 1 request / 1.5 s per device
create or replace function public.ingest_stats(p_user uuid, p_device_hash text, p_day date, p_rows jsonb)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_today date := (now() at time zone 'utc')::date;
  v_row jsonb;
  v_app text;
  v_reels int;
  v_watch int;
  v_ads int;
  v_old_reels int;
  v_old_updated timestamptz;
  v_allowed int;
  v_row_clamped boolean;
  v_clamped int := 0;
  v_last timestamptz;
begin
  if p_day < v_today - 2 or p_day > v_today + 1 then
    return jsonb_build_object('ok', false, 'error', 'day_out_of_range');
  end if;
  if jsonb_typeof(p_rows) <> 'array' or jsonb_array_length(p_rows) > 8 then
    return jsonb_build_object('ok', false, 'error', 'bad_rows');
  end if;

  select dc.last_ingest_at into v_last
    from public.device_credentials dc
   where dc.token_hash = decode(p_device_hash, 'hex')
   for update;
  if v_last is not null and v_last > now() - interval '1500 milliseconds' then
    return jsonb_build_object('ok', false, 'error', 'rate_limited');
  end if;
  update public.device_credentials dc
     set last_ingest_at = now()
   where dc.token_hash = decode(p_device_hash, 'hex');

  for v_row in select value from jsonb_array_elements(p_rows) loop
    v_app := v_row ->> 'app';
    continue when v_app is null or v_app not in ('instagram', 'youtube', 'tiktok', 'snapchat', 'other');
    v_reels := least(greatest(coalesce((v_row ->> 'reels')::int, 0), 0), 20000);
    v_watch := least(greatest(coalesce((v_row ->> 'watchSeconds')::int, 0), 0), 86400);
    v_ads   := least(greatest(coalesce((v_row ->> 'adsSkipped')::int, 0), 0), 20000);
    v_row_clamped := false;

    select ds.reels, ds.updated_at into v_old_reels, v_old_updated
      from public.daily_stats ds
     where ds.user_id = p_user and ds.day = p_day and ds.app = v_app
     for update;

    if found then
      v_reels := greatest(v_reels, v_old_reels);
      v_allowed := v_old_reels
        + ceil(least(greatest(extract(epoch from now() - v_old_updated), 0), 86400) / 1.2)::int
        + 30;
      if v_reels > v_allowed then
        v_reels := v_allowed;
        v_row_clamped := true;
        v_clamped := v_clamped + 1;
      end if;
      update public.daily_stats ds
         set reels = v_reels,
             watch_seconds = greatest(ds.watch_seconds, v_watch),
             ads_skipped = greatest(ds.ads_skipped, v_ads),
             flags = case when v_row_clamped then ds.flags | 1 else ds.flags end,
             updated_at = now()
       where ds.user_id = p_user and ds.day = p_day and ds.app = v_app;
    else
      -- First upload of the day (maybe after offline scrolling): allow what
      -- was physically possible since that day started.
      v_allowed := ceil(least(greatest(extract(epoch from now() - p_day::timestamp), 0), 86400 * 2) / 1.2)::int + 200;
      if v_reels > v_allowed then
        v_reels := v_allowed;
        v_row_clamped := true;
        v_clamped := v_clamped + 1;
      end if;
      insert into public.daily_stats (user_id, day, app, reels, watch_seconds, ads_skipped, flags)
      values (p_user, p_day, v_app, v_reels, v_watch, v_ads, case when v_row_clamped then 1 else 0 end);
    end if;
  end loop;

  return jsonb_build_object('ok', true, 'clamped', v_clamped);
end;
$$;

-- ─── Grants ─────────────────────────────────────────────────────────────────

revoke execute on function public._hit_rate_limit(uuid, text, int, interval) from public, anon, authenticated;
revoke execute on function public.device_user(text) from public, anon, authenticated;
revoke execute on function public.ingest_stats(uuid, text, date, jsonb) from public, anon, authenticated;
grant execute on function public.device_user(text) to service_role;
grant execute on function public.ingest_stats(uuid, text, date, jsonb) to service_role;

revoke execute on function public.register_device(text) from public, anon;
revoke execute on function public.create_invite() from public, anon;
revoke execute on function public.accept_invite(text) from public, anon;
revoke execute on function public.remove_friend(uuid) from public, anon;
revoke execute on function public.get_leaderboard(text, date) from public, anon;
revoke execute on function public.delete_account() from public, anon;
grant execute on function public.register_device(text) to authenticated;
grant execute on function public.create_invite() to authenticated;
grant execute on function public.accept_invite(text) to authenticated;
grant execute on function public.remove_friend(uuid) to authenticated;
grant execute on function public.get_leaderboard(text, date) to authenticated;
grant execute on function public.delete_account() to authenticated;
