-- Private, server-verified online badge proofs. Apply after the global league migration.
begin;
create table public.trophy_battle_results (
  battle_id uuid not null,
  user_id uuid not null references auth.users(id) on delete cascade,
  outcome text not null check(outcome in ('win','loss','draw')),
  completed_at timestamptz not null,
  primary key(battle_id,user_id)
);
create index trophy_results_user_order on public.trophy_battle_results(user_id,completed_at,battle_id);
create table public.trophy_online_awards (
  user_id uuid not null references auth.users(id) on delete cascade,
  badge text not null check(badge in ('grass','outscrolled','unemployed')),
  earned_at timestamptz not null default now(),
  primary key(user_id,badge)
);
alter table public.trophy_battle_results enable row level security;
alter table public.trophy_online_awards enable row level security;
revoke all on public.trophy_battle_results,public.trophy_online_awards from public,anon,authenticated,service_role;
grant select on public.trophy_battle_results,public.trophy_online_awards to service_role;

-- Called only by the trusted Battle finalizer, after validating participants and winners.
-- Retries are idempotent; a completed outcome cannot be silently rewritten.
create function public.record_trophy_battle_result(p_battle_id uuid,p_user_id uuid,p_outcome text,p_completed_at timestamptz)
returns void language plpgsql security definer set search_path='' as $$
begin
  if p_battle_id is null or p_user_id is null or p_outcome is null or p_outcome not in ('win','loss','draw') or
     p_completed_at is null or p_completed_at>now() or p_completed_at<date '2020-01-01' then
    raise exception 'invalid_result' using errcode='22023';
  end if;
  insert into public.trophy_battle_results(battle_id,user_id,outcome,completed_at)
    values(p_battle_id,p_user_id,p_outcome,p_completed_at) on conflict(battle_id,user_id) do nothing;
  if not exists(select 1 from public.trophy_battle_results where battle_id=p_battle_id and user_id=p_user_id
      and outcome=p_outcome and completed_at=p_completed_at) then
    raise exception 'immutable_result' using errcode='22023';
  end if;
end;
$$;
revoke execute on function public.record_trophy_battle_result(uuid,uuid,text,timestamptz) from public,anon,authenticated;
grant execute on function public.record_trophy_battle_result(uuid,uuid,text,timestamptz) to service_role;

create function public.get_my_trophy_proofs() returns jsonb
language plpgsql security definer set search_path='' as $$
declare v_me uuid:=auth.uid(); v_wins int:=0; v_run int:=0; v_best int:=0; v_result record; v_rank bigint;
begin
  if v_me is null then raise exception 'not_authenticated' using errcode='28000'; end if;
  for v_result in select outcome,completed_at from public.trophy_battle_results
    where user_id=v_me order by completed_at,battle_id loop
    if v_result.outcome='win' then v_wins:=v_wins+1;v_run:=v_run+1;v_best:=greatest(v_best,v_run);
    else v_run:=0; end if;
  end loop;
  if v_wins>0 then insert into public.trophy_online_awards(user_id,badge) values(v_me,'outscrolled') on conflict do nothing; end if;
  if v_best>=5 then insert into public.trophy_online_awards(user_id,badge) values(v_me,'unemployed') on conflict do nothing; end if;
  select place into v_rank from public._league_ranked(date_trunc('month',now() at time zone 'utc')::date) where user_id=v_me;
  if v_rank between 1 and 10 then insert into public.trophy_online_awards(user_id,badge) values(v_me,'grass') on conflict do nothing; end if;
  return jsonb_build_object('owner_id',v_me,'battle_wins',v_wins,'best_win_streak',v_best,
    'global_top10',exists(select 1 from public.trophy_online_awards where user_id=v_me and badge='grass'));
end;
$$;
revoke execute on function public.get_my_trophy_proofs() from public,anon;
grant execute on function public.get_my_trophy_proofs() to authenticated;
commit;
