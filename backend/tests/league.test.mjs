import { PGlite } from '@electric-sql/pglite';
import { citext } from '@electric-sql/pglite/contrib/citext';
import { pgcrypto } from '@electric-sql/pglite/contrib/pgcrypto';
import assert from 'node:assert/strict';
import { test, before, after } from 'node:test';
import { readFile } from 'node:fs/promises';

const db = new PGlite({ extensions: { citext, pgcrypto } });
const id = n => `10000000-0000-0000-0000-${String(n).padStart(12,'0')}`;
async function asUser(n, action, role='authenticated') {
  await db.exec(`set role ${role}`);
  await db.query("select set_config('request.jwt.claim.sub',$1,false)", [n == null ? '' : id(n)]);
  try { return await action(); }
  finally { await db.exec('reset role'); await db.query("select set_config('request.jwt.claim.sub','',false)"); }
}
const board = n => asUser(n, async () => (await db.query('select public.get_global_leaderboard() as board')).rows[0].board, n == null ? 'anon' : 'authenticated');
async function save(n, username, ig='') {
  return asUser(n, async () => (await db.query('select public.save_league_profile($1,$2) as result',[username,ig])).rows[0].result);
}
before(async () => {
  await db.exec(`create schema auth; create schema extensions;
    create role anon; create role authenticated; create role service_role bypassrls;
    create table auth.users(id uuid primary key);
    create function auth.uid() returns uuid language sql stable as $$select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid$$;
    grant usage on schema auth to anon,authenticated,service_role;
    grant execute on function auth.uid() to anon,authenticated,service_role;`);
  for(const file of ['20260930000000_doomscore_init.sql','20261007000000_security_hardening.sql','20261007010000_global_league.sql','20261007020000_trophy_cabinet.sql'])
    await db.exec(await readFile(new URL('../supabase/migrations/'+file,import.meta.url),'utf8'));
  await db.exec(`insert into auth.users select ('10000000-0000-0000-0000-'||lpad(n::text,12,'0'))::uuid from generate_series(1,503) n;
    insert into public.league_profiles(user_id,username,instagram_username)
      select id,'goblin'||right(id::text,6),'insta_'||right(id::text,6) from auth.users where right(id::text,12)::bigint<=500;
    insert into public.league_daily(user_id,day,reels)
      select user_id,(now() at time zone 'utc')::date,1001-right(user_id::text,12)::int from public.league_profiles;`);
});
after(() => db.close());

test('public top 50 exposes only opted-in display fields and no private IDs',async()=>{
  const result=await board(null);
  assert.equal(result.top.length,50);assert.equal(result.participants,500);assert.equal(result.me,null);
  assert.equal(result.top[0].username,'goblin000001');assert.equal(result.top[49].rank,50);
  assert.deepEqual(Object.keys(result.top[0]).sort(),['emoji','instagram_username','rank','reels','username']);
  for(const table of ['league_profiles','league_daily','league_champions','league_limits','league_seasons'])
    await assert.rejects(asUser(null,()=>db.query(`select * from public.${table}`),'anon'),/permission denied/);
});
test('personal exact rank is returned through 200 and then only top percentage',async()=>{
  assert.equal((await board(50)).me.rank,50);
  assert.equal((await board(200)).me.rank,200);
  const below=(await board(201)).me;assert.equal(below.rank,null);assert.equal(below.top_percent,41);
  assert.equal((await board(500)).me.top_percent,100);
});
test('unique normalized usernames are owned by anonymous auth IDs, never by typed names',async()=>{
  assert.equal((await save(501,' New.Name ',' @My.Insta ')).username,'new.name');
  assert.equal((await save(502,'NEW.NAME')).error,'username_taken');
  assert.equal((await db.query('select user_id from public.league_profiles where username=$1',['new.name'])).rows[0].user_id,id(501));
  await assert.rejects(asUser(502,()=>db.query('update public.league_profiles set user_id=$1 where username=$2',[id(502),'new.name'])),/permission denied/);
  const own=await asUser(502,()=>db.query('select public.get_my_league_profile() as profile'));
  assert.equal(own.rows[0].profile,null);
  assert.equal((await db.query('select hits from public.league_limits where user_id=$1',[id(502)])).rows[0].hits,1);
});
test('profile validation rejects links, malformed handles and unwanted script fields',async()=>{
  for(const [name,ig] of [['bad name',''],['valid','https://instagram.com/xx'],['valid','a..b'],['valid','x'.repeat(31)]])
    assert.equal((await save(502,name,ig)).error,'invalid_profile');
  assert.equal((await save(502,'validname')).username,'validname');
});
test('score uploads cannot tamper with ownership, rank or prior seasons',async()=>{
  const today=(await db.query("select (now() at time zone 'utc')::date::text as day")).rows[0].day;
  const upload=rows=>asUser(501,async()=>(await db.query('select public.submit_league_counts($1::jsonb) as result',[JSON.stringify(rows)])).rows[0].result);
  const row={day:today,reels:30};
  for(const rows of [[{...row,user_id:id(1)}],[{...row,rank:1}],[row,row],[{...row,reels:1.5}],[{...row,reels:'20'}],[{day:'2026-02-30',reels:2}],[{day:'2000-01-01',reels:2}]])
    assert.equal((await upload(rows)).error,'bad_rows');
  assert.equal((await upload([row])).ok,true);
  assert.equal((await upload([{...row,reels:100000}])).error,'rate_limited');
  assert.equal((await db.query('select reels from public.league_daily where user_id=$1',[id(501)])).rows[0].reels,30);
  await assert.rejects(asUser(501,()=>db.query('update public.league_daily set reels=99999 where user_id=$1',[id(1)])),/permission denied/);
  await assert.rejects(asUser(null,()=>db.query('select public.submit_league_counts($1::jsonb)',['[]']),'anon'),/permission denied/);
});
test('monthly aggregation includes the whole calendar month, with stable tie ranks',async()=>{
  await db.query("insert into public.league_daily(user_id,day,reels) values($1,(date_trunc('month',now() at time zone 'utc')-interval '1 day')::date,99999)",[id(1)]);
  const original=(await board(1)).me.reels;assert.equal(original,1000);
  await db.query('update public.league_daily set reels=1000 where user_id=$1 and day=(now() at time zone \'utc\')::date',[id(2)]);
  assert.equal((await board(1)).me.rank,1);assert.equal((await board(2)).me.rank,2);
});
test('podium snapshots are idempotent and not recomputed by late data changes',async()=>{
  const previous=(await db.query("select (date_trunc('month',now() at time zone 'utc')-interval '1 month')::date::text as month")).rows[0].month;
  // Earlier board reads finalized this empty season. Use an earlier, independently closed month.
  const earlier=(await db.query("select ($1::date-interval '1 month')::date::text as month",[previous])).rows[0].month;
  await db.query('insert into public.league_daily(user_id,day,reels) values($1,$4,900),($2,$4,800),($3,$4,700)',[id(1),id(2),id(3),earlier]);
  await db.query('select public.finalize_league_month($1)',[earlier]);
  const first=(await db.query('select place,username,reels from public.league_champions where month=$1 order by place',[earlier])).rows;
  assert.deepEqual(first.map(x=>x.place),[1,2,3]);
  await db.query('update public.league_daily set reels=1 where user_id=$1 and day=$2',[id(1),earlier]);
  await db.query('select public.finalize_league_month($1)',[earlier]);
  assert.deepEqual((await db.query('select place,username,reels from public.league_champions where month=$1 order by place',[earlier])).rows,first);
  const spotlight=await db.query("select public._league_podium_at(($1::date+interval '1 month') at time zone 'utc') as first, public._league_podium_at(($1::date+interval '1 month 6 days 23 hours 59 minutes 59 seconds') at time zone 'utc') as seventh, public._league_podium_at(($1::date+interval '1 month 7 days') at time zone 'utc') as eighth",[earlier]);
  assert.equal(spotlight.rows[0].first.length,3);assert.equal(spotlight.rows[0].seventh.length,3);assert.equal(spotlight.rows[0].eighth.length,0);
  await assert.rejects(asUser(1,()=>db.query('select public._league_podium_at(now())')),/permission denied/);
  await assert.rejects(asUser(1,()=>db.query('select public.finalize_league_month($1)',[earlier])),/permission denied/);
});
test('private profiles disappear from public rankings and data deletion cascades',async()=>{
  const first=(await board(null)).participants;
  await asUser(3,()=>db.query("select public.save_league_profile('goblin000003','', '🫠',false)"));
  assert.equal((await board(null)).participants,first-1);
  assert.equal((await board(3)).me,null);
  assert.equal((await db.query('select instagram_username from public.league_champions where user_id=$1',[id(3)])).rows[0].instagram_username,'');
  await asUser(3,()=>db.query('select public.delete_account()'));
  for(const table of ['league_profiles','league_daily','league_champions'])
    assert.equal((await db.query(`select count(*)::int as count from public.${table} where user_id=$1`,[id(3)])).rows[0].count,0);
});
test('reporting and blocking are private, bounded actions and do not alter global rank',async()=>{
  const action=(name,verb,reason='spam')=>asUser(10,async()=>(await db.query('select public.league_profile_action($1,$2,$3) as result',[name,verb,reason])).rows[0].result);
  assert.equal((await action('goblin000001','block')).ok,true);
  const hidden=await board(10);
  assert.equal(hidden.top.some(row=>row.username==='goblin000001'),false);
  assert.equal(hidden.top[0].rank,2);
  assert.equal((await board(null)).top[0].rank,1);
  assert.equal((await action('goblin000001','report','impersonation')).ok,true);
  assert.equal((await db.query('select reason from public.league_reports where reporter_id=$1',[id(10)])).rows[0].reason,'impersonation');
  assert.equal((await action('goblin000010','report')).error,'bad_action');
  await assert.rejects(asUser(10,()=>db.query('select * from public.league_reports')),/permission denied/);
  await assert.rejects(asUser(10,()=>db.query('update public.league_profiles set suspended=true')),/permission denied/);
  assert.equal((await action('','unblock_all')).ok,true);
  assert.equal((await board(10)).top[0].rank,1);
});
test('trophy results and awards cannot be forged or read directly by clients',async()=>{
  for(const table of ['trophy_battle_results','trophy_online_awards']) {
    await assert.rejects(asUser(10,()=>db.query(`select * from public.${table}`)),/permission denied/);
    await assert.rejects(asUser(10,()=>db.query(`delete from public.${table}`)),/permission denied/);
  }
  await assert.rejects(asUser(10,()=>db.query("select public.record_trophy_battle_result($1,$2,'win',now())",[id(900),id(10)])),/permission denied/);
  await assert.rejects(asUser(null,()=>db.query('select public.get_my_trophy_proofs()'),'anon'),/permission denied/);
});
test('top-10 achievement is minted from actual own rank and persists after dropping out',async()=>{
  const proof=async n=>(await asUser(n,()=>db.query('select public.get_my_trophy_proofs() as proof'))).rows[0].proof;
  assert.equal((await proof(200)).global_top10,false);
  assert.equal((await proof(1)).global_top10,true);
  await db.query("update public.league_daily set reels=1 where user_id=$1 and day=(now() at time zone 'utc')::date",[id(1)]);
  assert.equal((await proof(1)).global_top10,true);
  assert.equal((await proof(200)).global_top10,false);
  assert.equal((await proof(1)).owner_id,id(1));
});
test('Battle proof counts only completed outcomes and loss/draw break a five-win run',async()=>{
  const stamp=(await db.query("select (now()-interval '1 day')::text as stamp")).rows[0].stamp;
  const result=async(n,outcome)=>asUser(null,()=>db.query('select public.record_trophy_battle_result($1,$2,$3,$4::timestamptz+($5::int*interval \'1 minute\'))',[id(900+n),id(20),outcome,stamp,n]),'service_role');
  const proof=async()=>(await asUser(20,()=>db.query('select public.get_my_trophy_proofs() as proof'))).rows[0].proof;
  assert.equal((await proof()).battle_wins,0);
  for(let n=1;n<=4;n++) await result(n,'win');
  await result(5,'draw');await result(6,'win');await result(7,'loss');
  assert.equal((await proof()).battle_wins,5);assert.equal((await proof()).best_win_streak,4);
  for(let n=8;n<=12;n++) await result(n,'win');
  await result(13,'loss');
  assert.equal((await proof()).battle_wins,10);assert.equal((await proof()).best_win_streak,5);
  assert.deepEqual((await db.query('select badge from public.trophy_online_awards where user_id=$1 order by badge',[id(20)])).rows.map(x=>x.badge),['outscrolled','unemployed']);
  await result(12,'win');assert.equal((await proof()).battle_wins,10);
  await assert.rejects(result(12,'loss'),/immutable_result/);
  assert.equal((await asUser(21,()=>db.query('select public.get_my_trophy_proofs() as proof'))).rows[0].proof.battle_wins,0);
});
test('deleting an online identity removes its badge proofs and finalized result records',async()=>{
  await asUser(20,()=>db.query('select public.delete_account()'));
  for(const table of ['trophy_battle_results','trophy_online_awards'])
    assert.equal((await db.query(`select count(*)::int as count from public.${table} where user_id=$1`,[id(20)])).rows[0].count,0);
});
