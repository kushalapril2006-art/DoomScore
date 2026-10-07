-- Apply AFTER 20260930000000_doomscore_init.sql to the team's Supabase project.
-- Prepared locally; this file does not itself change the live database.
begin;

-- RLS limits rows. Column grants limit fields. Keep id writable for the existing
-- iOS/Android ON CONFLICT upsert, but forbid changing it with an immutable-field trigger.
revoke insert, update on public.profiles from anon, authenticated;
grant insert (id, handle, display_name, emoji, color, daily_goal) on public.profiles to authenticated;
grant update (id, handle, display_name, emoji, color, daily_goal) on public.profiles to authenticated;

create or replace function public.protect_profile_fields()
returns trigger language plpgsql set search_path = '' as $$
begin
  if current_user = 'authenticated' and
     (new.id is distinct from old.id or new.created_at is distinct from old.created_at) then
    raise exception 'immutable_profile_field' using errcode = '42501';
  end if;
  return new;
end;
$$;
drop trigger if exists profiles_protect_fields on public.profiles;
create trigger profiles_protect_fields before update on public.profiles
for each row execute function public.protect_profile_fields();
revoke execute on function public.protect_profile_fields() from public, anon, authenticated;

-- Existing rows are retained; subsequent writes must meet the additional text checks.
alter table public.profiles add constraint profiles_safe_name
  check (display_name = btrim(display_name) and display_name !~ '[[:cntrl:]]' and
         display_name !~ U&'[\202A-\202E\2066-\2069]') not valid;
alter table public.profiles add constraint profiles_safe_emoji
  check (emoji !~ '[[:cntrl:]]' and emoji !~ U&'[\202A-\202E\2066-\2069]') not valid;

-- No client access to credentials, push tokens, rate counters or direct stats writes.
alter table public.profiles enable row level security;
alter table public.friendships enable row level security;
alter table public.invites enable row level security;
alter table public.daily_stats enable row level security;
alter table public.device_credentials enable row level security;
alter table public.live_activity_tokens enable row level security;
alter table public.rate_limits enable row level security;
revoke all on public.device_credentials, public.live_activity_tokens, public.rate_limits from anon, authenticated;
revoke insert, update, delete on public.friendships, public.invites, public.daily_stats from anon, authenticated;

-- Do not RAISE after updating an abuse counter: an exception rolls back that update.
-- Return a custom HTTP status via PostgREST instead, so failed attempts stay charged.
create or replace function public._invite_attempt_allowed(p_user uuid)
returns boolean language plpgsql security definer set search_path = '' as $$
declare v_hits int;
begin
  if p_user is null then return false; end if;
  insert into public.rate_limits as rl(user_id,bucket,window_start,hits)
  values(p_user,'accept_invite',now(),1)
  on conflict(user_id,bucket) do update set
    hits = case when rl.window_start < now()-interval '1 hour' then 1 else least(rl.hits+1,31) end,
    window_start = case when rl.window_start < now()-interval '1 hour' then now() else rl.window_start end
  returning hits into v_hits;
  return v_hits <= 30;
end;
$$;
revoke execute on function public._invite_attempt_allowed(uuid) from public, anon, authenticated;

create or replace function public.accept_invite(p_code text)
returns table(friend_id uuid,friend_handle text,friend_display_name text,friend_emoji text)
language plpgsql security definer set search_path = '' as $$
declare
  v_me uuid := auth.uid();
  v_code text := upper(btrim(coalesce(p_code,'')));
  v_inviter uuid; v_expires timestamptz; v_uses int; v_max int;
begin
  if v_me is null then
    perform set_config('response.status','401',true); return;
  end if;
  if not public._invite_attempt_allowed(v_me) then
    perform set_config('response.status','429',true);
    perform set_config('response.headers','[{"x-doomscore-error":"rate_limited"}]',true); return;
  end if;
  if not exists(select 1 from public.profiles where id=v_me) then
    perform set_config('response.status','400',true);
    perform set_config('response.headers','[{"x-doomscore-error":"profile_required"}]',true); return;
  end if;
  if v_code !~ '^[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{10}$' then
    perform set_config('response.status','400',true);
    perform set_config('response.headers','[{"x-doomscore-error":"invite_invalid"}]',true); return;
  end if;
  select inv.inviter_id,inv.expires_at,inv.uses,inv.max_uses
  into v_inviter,v_expires,v_uses,v_max from public.invites inv where inv.code=v_code for update;
  if v_inviter is null or v_expires<=now() or v_uses>=v_max then
    perform set_config('response.status','400',true);
    perform set_config('response.headers','[{"x-doomscore-error":"invite_invalid"}]',true); return;
  end if;
  if v_inviter=v_me then
    perform set_config('response.status','400',true);
    perform set_config('response.headers','[{"x-doomscore-error":"invite_self"}]',true); return;
  end if;
  -- A consistent row-lock order prevents simultaneous invites exceeding either friend cap.
  perform 1 from public.profiles where id in(v_me,v_inviter) order by id for update;
  if not exists(select 1 from public.friendships fr where fr.user_id=v_me and fr.friend_id=v_inviter) then
    if (select count(*) from public.friendships where user_id=v_me)>=200 or
       (select count(*) from public.friendships where user_id=v_inviter)>=200 then
      perform set_config('response.status','400',true);
      perform set_config('response.headers','[{"x-doomscore-error":"too_many_friends"}]',true); return;
    end if;
    insert into public.friendships(user_id,friend_id) values(v_me,v_inviter),(v_inviter,v_me) on conflict do nothing;
    update public.invites set uses=uses+1 where code=v_code;
  end if;
  return query select p.id,p.handle::text,p.display_name,p.emoji from public.profiles p where p.id=v_inviter;
end;
$$;
revoke execute on function public.accept_invite(text) from public, anon;
grant execute on function public.accept_invite(text) to authenticated;

create or replace function public.revoke_device(p_token_hash text)
returns void language plpgsql security definer set search_path = '' as $$
begin
  if auth.uid() is null then raise exception 'not_authenticated' using errcode='28000'; end if;
  if p_token_hash is null or p_token_hash !~ '^[0-9a-f]{64}$' then
    raise exception 'invalid_device' using errcode='22023';
  end if;
  update public.device_credentials set revoked_at=now()
  where user_id=auth.uid() and token_hash=decode(p_token_hash,'hex') and revoked_at is null;
end;
$$;
revoke execute on function public.revoke_device(text) from public, anon;
grant execute on function public.revoke_device(text) to authenticated;

-- Revalidate device ownership/revocation at the write boundary and reject duplicate app rows.
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
  v_owner uuid;
begin
  if p_user is null or p_device_hash is null or p_device_hash !~ '^[0-9a-f]{64}$' then
    return jsonb_build_object('ok', false, 'error', 'unauthorized');
  end if;
  if p_day is null or p_day < v_today - 2 or p_day > v_today + 1 then
    return jsonb_build_object('ok', false, 'error', 'day_out_of_range');
  end if;
  if jsonb_typeof(p_rows) is distinct from 'array' then
    return jsonb_build_object('ok', false, 'error', 'bad_rows');
  end if;
  if jsonb_array_length(p_rows) not between 1 and 5 or
     (select count(distinct item->>'app') from jsonb_array_elements(p_rows) item) <> jsonb_array_length(p_rows) then
    return jsonb_build_object('ok', false, 'error', 'bad_rows');
  end if;
  for v_row in select value from jsonb_array_elements(p_rows) loop
    if jsonb_typeof(v_row) is distinct from 'object' then
      return jsonb_build_object('ok', false, 'error', 'bad_rows');
    end if;
    if exists(select 1 from jsonb_object_keys(v_row) as field(key)
              where key not in ('app','reels','watchSeconds','adsSkipped')) or
       v_row->>'app' is null or v_row->>'app' not in ('instagram','youtube','tiktok','snapchat','other') or
       jsonb_typeof(v_row->'reels') is distinct from 'number' or
       jsonb_typeof(v_row->'watchSeconds') is distinct from 'number' or
       jsonb_typeof(v_row->'adsSkipped') is distinct from 'number' or
       coalesce(v_row->>'reels','') !~ '^[0-9]{1,5}$' or
       coalesce(v_row->>'watchSeconds','') !~ '^[0-9]{1,5}$' or
       coalesce(v_row->>'adsSkipped','') !~ '^[0-9]{1,5}$' then
      return jsonb_build_object('ok', false, 'error', 'bad_rows');
    end if;
    if (v_row->>'reels')::int > 20000 or (v_row->>'watchSeconds')::int > 86400 or (v_row->>'adsSkipped')::int > 20000 then
      return jsonb_build_object('ok', false, 'error', 'bad_rows');
    end if;
  end loop;

  select dc.user_id, dc.last_ingest_at into v_owner, v_last
    from public.device_credentials dc
   where p_device_hash ~ '^[0-9a-f]{64}$'
     and dc.token_hash = decode(p_device_hash, 'hex')
     and dc.user_id = p_user and dc.revoked_at is null
   for update;
  if not found then return jsonb_build_object('ok', false, 'error', 'unauthorized'); end if;
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
revoke execute on function public.ingest_stats(uuid,text,date,jsonb) from public,anon,authenticated;
grant execute on function public.ingest_stats(uuid,text,date,jsonb) to service_role;

commit;
