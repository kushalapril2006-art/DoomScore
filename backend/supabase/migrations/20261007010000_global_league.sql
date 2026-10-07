-- Independent, opt-in league. Existing iOS friend-profile visibility is unchanged.
begin;
create table public.league_profiles (
  user_id uuid primary key references auth.users(id) on delete cascade,
  username extensions.citext not null unique check(username::text ~ '^[a-z0-9._]{3,20}$'),
  instagram_username text not null default '' check(instagram_username = '' or
    (instagram_username ~ '^[a-z0-9_]([a-z0-9._]{0,28}[a-z0-9_])?$' and instagram_username not like '%..%')),
  emoji text not null default '🫠' check(emoji in ('🫠','💀','🐸','🧊','👽','🔥')),
  visible boolean not null default true,
  suspended boolean not null default false,
  created_at timestamptz not null default now(),
  last_upload_at timestamptz
);
create table public.league_daily (
  user_id uuid not null references public.league_profiles(user_id) on delete cascade,
  day date not null,
  reels int not null check(reels between 0 and 100000),
  updated_at timestamptz not null default now(),
  primary key(user_id,day)
);
create index league_daily_month_idx on public.league_daily(day,user_id) include(reels);
create table public.league_seasons(month date primary key, finalized_at timestamptz not null default now());
create table public.league_champions (
  month date not null references public.league_seasons(month) on delete cascade,
  place int not null check(place between 1 and 3),
  user_id uuid not null references public.league_profiles(user_id) on delete cascade,
  username text not null, instagram_username text not null, emoji text not null,
  reels bigint not null,
  primary key(month,place)
);
create table public.league_limits (
  user_id uuid primary key references auth.users(id) on delete cascade,
  window_start timestamptz not null, hits int not null
);
create table public.league_blocks (
  user_id uuid not null references auth.users(id) on delete cascade,
  blocked_id uuid not null references public.league_profiles(user_id) on delete cascade,
  primary key(user_id,blocked_id), check(user_id<>blocked_id)
);
create table public.league_reports (
  reporter_id uuid not null references auth.users(id) on delete cascade,
  reported_id uuid not null references public.league_profiles(user_id) on delete cascade,
  reason text not null check(reason in ('impersonation','offensive','spam')),
  created_at timestamptz not null default now(),
  primary key(reporter_id,reported_id), check(reporter_id<>reported_id)
);
alter table public.league_profiles enable row level security;
alter table public.league_daily enable row level security;
alter table public.league_seasons enable row level security;
alter table public.league_champions enable row level security;
alter table public.league_limits enable row level security;
alter table public.league_blocks enable row level security;
alter table public.league_reports enable row level security;
revoke all on public.league_profiles,public.league_daily,public.league_seasons,public.league_champions,public.league_limits,public.league_blocks,public.league_reports from anon,authenticated;
grant all on public.league_profiles,public.league_daily,public.league_seasons,public.league_champions,public.league_limits,public.league_blocks,public.league_reports to service_role;

create function public.save_league_profile(p_username text,p_instagram text default '',p_emoji text default '🫠',p_visible boolean default true)
returns jsonb language plpgsql security definer set search_path='' as $$
declare v_me uuid:=auth.uid(); v_name text:=lower(btrim(p_username));
  v_instagram text:=lower(regexp_replace(btrim(coalesce(p_instagram,'')),'^@','')); v_hits int;
begin
  if v_me is null then raise exception 'not_authenticated' using errcode='28000'; end if;
  insert into public.league_limits as rl values(v_me,now(),1) on conflict(user_id) do update set
    hits=case when rl.window_start<now()-interval '1 hour' then 1 else least(rl.hits+1,31) end,
    window_start=case when rl.window_start<now()-interval '1 hour' then now() else rl.window_start end returning hits into v_hits;
  if v_hits>30 then perform set_config('response.status','429',true); return jsonb_build_object('error','rate_limited'); end if;
  if v_name is null or char_length(p_username)>80 or v_name !~ '^[a-z0-9._]{3,20}$' or
     char_length(coalesce(p_instagram,''))>64 or (v_instagram<>'' and
       (v_instagram !~ '^[a-z0-9_]([a-z0-9._]{0,28}[a-z0-9_])?$' or v_instagram like '%..%')) or
     p_emoji is null or p_emoji not in ('🫠','💀','🐸','🧊','👽','🔥') or p_visible is null then
    perform set_config('response.status','400',true); return jsonb_build_object('error','invalid_profile');
  end if;
  -- Catch the unique collision inside a subtransaction so the abuse counter survives.
  begin
    insert into public.league_profiles(user_id,username,instagram_username,emoji,visible)
      values(v_me,v_name,v_instagram,p_emoji,p_visible)
      on conflict(user_id) do update set username=excluded.username,instagram_username=excluded.instagram_username,
        emoji=excluded.emoji,visible=excluded.visible;
  exception when unique_violation then
    perform set_config('response.status','409',true); return jsonb_build_object('error','username_taken');
  end;
  -- An edited/removed Instagram handle must not leave a stale public link in a podium.
  update public.league_champions set instagram_username=v_instagram where user_id=v_me;
  return jsonb_build_object('username',v_name,'instagram_username',v_instagram,'emoji',p_emoji,'visible',p_visible);
end;
$$;
revoke execute on function public.save_league_profile(text,text,text,boolean) from public,anon;
grant execute on function public.save_league_profile(text,text,text,boolean) to authenticated;

create function public.get_my_league_profile() returns jsonb language sql stable security definer set search_path='' as $$
  select jsonb_build_object('username',username::text,'instagram_username',instagram_username,'emoji',emoji,'visible',visible)
  from public.league_profiles where user_id=auth.uid();
$$;
revoke execute on function public.get_my_league_profile() from public,anon;
grant execute on function public.get_my_league_profile() to authenticated;

create function public.league_profile_action(p_username text,p_action text,p_reason text default 'spam')
returns jsonb language plpgsql security definer set search_path='' as $$
declare v_me uuid:=auth.uid(); v_target uuid;
begin
  if v_me is null then raise exception 'not_authenticated' using errcode='28000'; end if;
  perform 1 from public.league_profiles where user_id=v_me for update;
  if not found then return jsonb_build_object('error','profile_required'); end if;
  if p_action is null or p_action not in ('block','unblock','report','unblock_all') or p_reason is null or p_reason not in ('impersonation','offensive','spam') then
    return jsonb_build_object('error','bad_action');
  end if;
  if p_action='unblock_all' then delete from public.league_blocks where user_id=v_me; return jsonb_build_object('ok',true); end if;
  if p_username is null or char_length(p_username)>20 or p_username !~ '^[a-z0-9._]{3,20}$' then return jsonb_build_object('error','bad_action'); end if;
  select user_id into v_target from public.league_profiles where username=p_username;
  if v_target is null or v_target=v_me then return jsonb_build_object('error','bad_action'); end if;
  if p_action='block' then
    if (select count(*) from public.league_blocks where user_id=v_me)>=200 then return jsonb_build_object('error','limit_reached'); end if;
    insert into public.league_blocks values(v_me,v_target) on conflict do nothing;
  elsif p_action='unblock' then delete from public.league_blocks where user_id=v_me and blocked_id=v_target;
  else
    if (select count(*) from public.league_reports where reporter_id=v_me and created_at>now()-interval '1 day')>=20 then
      return jsonb_build_object('error','limit_reached');
    end if;
    insert into public.league_reports(reporter_id,reported_id,reason) values(v_me,v_target,p_reason)
      on conflict(reporter_id,reported_id) do update set reason=excluded.reason,created_at=now();
  end if;
  return jsonb_build_object('ok',true);
end;
$$;
revoke execute on function public.league_profile_action(text,text,text) from public,anon;
grant execute on function public.league_profile_action(text,text,text) to authenticated;

create function public.submit_league_counts(p_rows jsonb) returns jsonb language plpgsql security definer set search_path='' as $$
declare v_me uuid:=auth.uid(); v_today date:=(now() at time zone 'utc')::date;
  v_month date:=date_trunc('month',now() at time zone 'utc')::date;
  v_row jsonb; v_day date; v_reels int; v_old int; v_updated timestamptz; v_last timestamptz; v_cap int; v_clamped int:=0;
begin
  if v_me is null then raise exception 'not_authenticated' using errcode='28000'; end if;
  if jsonb_typeof(p_rows) is distinct from 'array' then return jsonb_build_object('error','bad_rows'); end if;
  if jsonb_array_length(p_rows) not between 1 and 31 or
     (select count(distinct value->>'day') from jsonb_array_elements(p_rows))<>jsonb_array_length(p_rows) then
    return jsonb_build_object('error','bad_rows');
  end if;
  -- Validate every row before touching any score. No IDs, ranks or ownership fields accepted.
  for v_row in select value from jsonb_array_elements(p_rows) loop
    if jsonb_typeof(v_row) is distinct from 'object' then return jsonb_build_object('error','bad_rows'); end if;
    if exists(select 1 from jsonb_object_keys(v_row) as field(key) where key not in('day','reels')) or
      jsonb_typeof(v_row->'day') is distinct from 'string' or coalesce(v_row->>'day','') !~ '^\d{4}-\d{2}-\d{2}$' or
      jsonb_typeof(v_row->'reels') is distinct from 'number' or coalesce(v_row->>'reels','') !~ '^[0-9]{1,6}$' then
      return jsonb_build_object('error','bad_rows');
    end if;
    begin v_day:=(v_row->>'day')::date; exception when others then return jsonb_build_object('error','bad_rows'); end;
    if v_day<v_month or v_day>v_today or v_day::text<>v_row->>'day' or (v_row->>'reels')::int>100000 then
      return jsonb_build_object('error','bad_rows');
    end if;
  end loop;
  select last_upload_at into v_last from public.league_profiles where user_id=v_me and visible and not suspended for update;
  if not found then return jsonb_build_object('error','profile_required'); end if;
  if v_last>now()-interval '30 seconds' then return jsonb_build_object('error','rate_limited'); end if;
  update public.league_profiles set last_upload_at=now() where user_id=v_me;
  for v_row in select value from jsonb_array_elements(p_rows) loop
    v_day:=(v_row->>'day')::date; v_reels:=(v_row->>'reels')::int;
    select reels,updated_at into v_old,v_updated from public.league_daily where user_id=v_me and day=v_day for update;
    if found then
      v_cap:=least(100000,v_old+ceil(greatest(extract(epoch from now()-v_updated),0)/0.75)::int+30);
      v_reels:=greatest(v_old,v_reels);
    else
      v_cap:=least(100000,ceil(least(greatest(extract(epoch from now()-(v_day::timestamp at time zone 'utc')),0),86400)/0.75)::int+30);
    end if;
    if v_reels>v_cap then v_clamped:=v_clamped+1; v_reels:=v_cap; end if;
    insert into public.league_daily(user_id,day,reels) values(v_me,v_day,v_reels)
      on conflict(user_id,day) do update set reels=excluded.reels,updated_at=now();
  end loop;
  return jsonb_build_object('ok',true,'clamped',v_clamped);
end;
$$;
revoke execute on function public.submit_league_counts(jsonb) from public,anon;
grant execute on function public.submit_league_counts(jsonb) to authenticated;

-- A single deterministic ordering is used for rows, personal ranks and the podium.
create function public._league_ranked(p_month date)
returns table(user_id uuid,username text,instagram_username text,emoji text,reels bigint,place bigint)
language sql stable security definer set search_path='' as $$
  with totals as (
    select p.user_id,p.username::text,p.instagram_username,p.emoji,sum(d.reels)::bigint as reels
    from public.league_profiles p join public.league_daily d on d.user_id=p.user_id
    where p.visible and not p.suspended and d.day>=p_month and d.day<(p_month+interval '1 month')::date
    group by p.user_id,p.username,p.instagram_username,p.emoji having sum(d.reels)>0
  ) select *,row_number() over(order by reels desc,user_id asc) as place from totals;
$$;
revoke execute on function public._league_ranked(date) from public,anon,authenticated;

create function public.finalize_league_month(p_month date) returns void language plpgsql security definer set search_path='' as $$
begin
  if p_month is null or p_month<>date_trunc('month',p_month)::date or
     p_month>=date_trunc('month',now() at time zone 'utc')::date then
    raise exception 'invalid_month' using errcode='22023';
  end if;
  -- Completed seasons need no lock; ordinary board reads must not serialize globally.
  if exists(select 1 from public.league_seasons where month=p_month) then return; end if;
  perform pg_advisory_xact_lock(74891,(p_month-date '2000-01-01')::int);
  if exists(select 1 from public.league_seasons where month=p_month) then return; end if;
  insert into public.league_seasons(month) values(p_month);
  insert into public.league_champions(month,place,user_id,username,instagram_username,emoji,reels)
    select p_month,place::int,user_id,username,instagram_username,emoji,reels from public._league_ranked(p_month) where place<=3;
end;
$$;
revoke execute on function public.finalize_league_month(date) from public,anon,authenticated;
grant execute on function public.finalize_league_month(date) to service_role;

create function public._league_podium_at(p_now timestamptz) returns jsonb
language sql stable security definer set search_path='' as $$
  select case when extract(day from p_now at time zone 'utc')>7 then '[]'::jsonb else
    coalesce((select jsonb_agg(jsonb_build_object('rank',c.place,'username',c.username,'instagram_username',c.instagram_username,
      'emoji',c.emoji,'reels',c.reels) order by c.place)
      from public.league_champions c join public.league_profiles p on p.user_id=c.user_id
      where c.month=(date_trunc('month',p_now at time zone 'utc')-interval '1 month')::date
        and p.visible and not p.suspended and not exists(
          select 1 from public.league_blocks b where b.user_id=auth.uid() and b.blocked_id=c.user_id)),'[]'::jsonb) end;
$$;
revoke execute on function public._league_podium_at(timestamptz) from public,anon,authenticated;

create function public.get_global_leaderboard() returns jsonb language plpgsql security definer set search_path='' as $$
declare v_month date:=date_trunc('month',now() at time zone 'utc')::date;
  v_previous date:=(v_month-interval '1 month')::date;
  v_top jsonb; v_me jsonb; v_podium jsonb:='[]'::jsonb; v_total bigint;
begin
  perform public.finalize_league_month(v_previous);
  with ranked as materialized(select * from public._league_ranked(v_month))
  select coalesce((select jsonb_agg(jsonb_build_object('rank',place,'username',username,'instagram_username',instagram_username,
      'emoji',emoji,'reels',reels) order by place) from ranked r where place<=50 and not exists(
        select 1 from public.league_blocks b where b.user_id=auth.uid() and b.blocked_id=r.user_id)),'[]'::jsonb),
    (select count(*) from ranked),
    (select jsonb_build_object('rank',case when place<=200 then place else null end,
      'top_percent',ceil(place::numeric*100/(select count(*) from ranked))::int,'reels',reels)
      from ranked where user_id=auth.uid()) into v_top,v_total,v_me;
  v_podium:=public._league_podium_at(now());
  return jsonb_build_object('month',v_month,'previous_month',v_previous,'participants',v_total,'top',v_top,
    'me',v_me,'podium',v_podium,'spotlight_until',(v_month+interval '7 days') at time zone 'utc');
end;
$$;
revoke execute on function public.get_global_leaderboard() from public;
grant execute on function public.get_global_leaderboard() to anon,authenticated;
commit;
