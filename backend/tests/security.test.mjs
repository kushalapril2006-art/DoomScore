import { PGlite } from '@electric-sql/pglite';
import { citext } from '@electric-sql/pglite/contrib/citext';
import { pgcrypto } from '@electric-sql/pglite/contrib/pgcrypto';
import assert from 'node:assert/strict';
import { test, before, after } from 'node:test';
import { readFile } from 'node:fs/promises';
import { parse, readBody } from '../supabase/functions/ingest/payload.ts';

const db = new PGlite({ extensions: { citext, pgcrypto } });
const ids = ['00000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000003','00000000-0000-0000-0000-000000000004'];
async function asUser(id, action, role='authenticated') {
  await db.exec(`set role ${role}`);
  await db.query("select set_config('request.jwt.claim.sub',$1,false)", [id ?? '']);
  try { return await action(); }
  finally { await db.exec('reset role'); await db.query("select set_config('request.jwt.claim.sub','',false)"); }
}
async function invitation(user, code) {
  return asUser(user, () => db.transaction(async tx => {
    const result = await tx.query('select * from public.accept_invite($1)', [code]);
    const status = await tx.query("select current_setting('response.status',true) as status");
    return { rows: result.rows, status: status.rows[0].status || '200' };
  }));
}
before(async () => {
  await db.exec(`create schema auth; create schema extensions;
    create role anon; create role authenticated; create role service_role bypassrls;
    create table auth.users(id uuid primary key);
    create function auth.uid() returns uuid language sql stable as $$select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid$$;
    grant usage on schema auth to anon,authenticated,service_role;
    grant execute on function auth.uid() to anon,authenticated,service_role;`);
  for (const id of ids) await db.query('insert into auth.users values($1)', [id]);
  await db.exec(await readFile(new URL('../supabase/migrations/20260930000000_doomscore_init.sql', import.meta.url), 'utf8'));
  await db.exec(await readFile(new URL('../supabase/migrations/20261007000000_security_hardening.sql', import.meta.url), 'utf8'));
  for (let index=0;index<3;index++) await db.query('insert into public.profiles(id,handle,display_name) values($1,$2,$3)', [ids[index], 'person'+index, 'Person '+index]);
});
after(() => db.close());

test('unauthenticated callers cannot read profile records', async () => {
  await assert.rejects(asUser(null, () => db.query('select * from public.profiles'), 'anon'), /permission denied/);
});
test('users see only themselves and expressly connected friends', async () => {
  assert.deepEqual((await asUser(ids[0], () => db.query('select id from public.profiles order by id'))).rows.map(x=>x.id), [ids[0]]);
  await db.query('insert into public.friendships(user_id,friend_id) values($1,$2),($2,$1)', [ids[0],ids[1]]);
  assert.deepEqual((await asUser(ids[0], () => db.query('select id from public.profiles order by id'))).rows.map(x=>x.id), [ids[0],ids[1]]);
  await db.query('delete from public.friendships where user_id=$1 or friend_id=$1', [ids[0]]);
});
test('ownership cannot be changed or claimed through an upsert', async () => {
  await assert.rejects(asUser(ids[0], () => db.query('update public.profiles set id=$1 where id=$2', [ids[3],ids[0]])), /immutable_profile_field/);
  await assert.rejects(asUser(ids[0], () => db.query("insert into public.profiles(id,handle,display_name) values($1,'hacked','Hacked') on conflict(id) do update set display_name=excluded.display_name", [ids[1]])), /row-level security/);
  assert.equal((await db.query('select display_name from public.profiles where id=$1', [ids[1]])).rows[0].display_name, 'Person 1');
});
test('server timestamps are not writable but normal profile edits and upserts work', async () => {
  for (const field of ['created_at','updated_at']) await assert.rejects(asUser(ids[0], () => db.query(`update public.profiles set ${field}='2000-01-01' where id=$1`, [ids[0]])), /permission denied/);
  const literal = "x'; DROP TABLE profiles;--";
  await asUser(ids[0], () => db.query('update public.profiles set display_name=$1 where id=$2', [literal,ids[0]]));
  assert.equal((await db.query('select display_name from public.profiles where id=$1', [ids[0]])).rows[0].display_name, literal);
  await asUser(ids[0], () => db.query("insert into public.profiles(id,handle,display_name,emoji,color,daily_goal) values($1,'person0','Person 0','🧊','#C6FF3D',100) on conflict(id) do update set id=excluded.id,handle=excluded.handle,display_name=excluded.display_name,emoji=excluded.emoji,color=excluded.color,daily_goal=excluded.daily_goal", [ids[0]]));
  await assert.rejects(asUser(ids[3], () => db.query("insert into public.profiles(id,handle,display_name,created_at) values($1,'person3','Person 3','2000-01-01')", [ids[3]])), /permission denied/);
});
test('private credentials, direct stats writes and internal RPCs are unavailable to clients', async () => {
  await assert.rejects(asUser(ids[0], () => db.query('select * from public.device_credentials')), /permission denied/);
  await assert.rejects(asUser(ids[0], () => db.query("insert into public.daily_stats(user_id,day,app,reels,flags) values($1,current_date,'instagram',9999,0)", [ids[0]])), /permission denied/);
  await assert.rejects(asUser(ids[0], () => db.query("select public.ingest_stats($1,$2,current_date,'[]'::jsonb)", [ids[0],'a'.repeat(64)])), /permission denied/);
  await assert.rejects(asUser(ids[0], () => db.query('select public._invite_attempt_allowed($1)', [ids[1]])), /permission denied/);
});
test('failed invite guesses remain charged after their RPC transaction commits', async () => {
  for(let i=0;i<31;i++) { const result=await invitation(ids[2],'ABCD234567'); assert.equal(result.status,i<30?'400':'429'); }
  assert.equal((await db.query("select hits from public.rate_limits where user_id=$1 and bucket='accept_invite'", [ids[2]])).rows[0].hits,31);
});
test('successful invites retain the iOS contract and repeated acceptance does not consume uses', async () => {
  await db.query("insert into public.invites(code,inviter_id) values('ABCD234567',$1)",[ids[0]]);
  const first=await invitation(ids[1],'abcd234567'); assert.equal(first.status,'200'); assert.equal(first.rows[0].friend_id,ids[0]);
  assert.equal((await invitation(ids[1],'ABCD234567')).status,'200');
  assert.equal((await db.query("select uses from public.invites where code='ABCD234567'")).rows[0].uses,1);
  assert.equal((await db.query('select count(*)::int as count from public.friendships')).rows[0].count,2);
});
test('credential revocation is restricted to the signed-in owner', async () => {
  await db.query("insert into public.device_credentials(user_id,token_hash) values($1,decode($2,'hex')),($3,decode($4,'hex'))",[ids[0],'a'.repeat(64),ids[1],'b'.repeat(64)]);
  await asUser(ids[0],()=>db.query('select public.revoke_device($1)',['b'.repeat(64)]));
  assert.equal((await db.query("select revoked_at from public.device_credentials where token_hash=decode($1,'hex')",['b'.repeat(64)])).rows[0].revoked_at,null);
  await asUser(ids[0],()=>db.query('select public.revoke_device($1)',['a'.repeat(64)]));
  assert.notEqual((await db.query("select revoked_at from public.device_credentials where token_hash=decode($1,'hex')",['a'.repeat(64)])).rows[0].revoked_at,null);
});
test('ingestion rejects ownership substitution, revoked credentials, duplicate apps and protected fields', async () => {
  const row={app:'instagram',reels:1,watchSeconds:2,adsSkipped:0};
  async function ingest(user,hash,rows) {return (await db.query('select public.ingest_stats($1,$2,current_date,$3::jsonb) as result',[user,hash,JSON.stringify(rows)])).rows[0].result;}
  assert.equal((await ingest(ids[0],'b'.repeat(64),[row])).error,'unauthorized');
  assert.equal((await ingest(ids[0],'a'.repeat(64),[row])).error,'unauthorized');
  assert.equal((await ingest(ids[1],'b'.repeat(64),[row,row])).error,'bad_rows');
  assert.equal((await ingest(ids[1],'b'.repeat(64),[{...row,flags:0}])).error,'bad_rows');
  assert.equal((await ingest(ids[1],'b'.repeat(64),[{...row,reels:'100'}])).error,'bad_rows');
  assert.equal((await ingest(ids[1],'b'.repeat(64),[row])).ok,true);
});
test('HTTP payload validation rejects impossible dates, overflow, duplicates and actor fields',()=>{
  const base={day:'2026-10-07',apps:[{app:'instagram',reels:1,watchSeconds:2,adsSkipped:0}]};
  assert.equal(typeof parse(base),'object');
  for(const invalid of [{...base,day:'2026-02-30'},{...base,user_id:ids[1]},{...base,apps:[...base.apps,...base.apps]},{...base,apps:[{...base.apps[0],flags:0}]},{...base,apps:[{...base.apps[0],reels:1.5}]},{...base,apps:[{...base.apps[0],watchSeconds:86401}]},{...base,tzOffsetMinutes:9999}]) assert.equal(typeof parse(invalid),'string');
});
test('request reader caps bytes, including chunked multibyte UTF-8, and rejects invalid encoding',async()=>{
  assert.equal(await readBody(new Request('https://example.test',{method:'POST',headers:{'content-type':'application/json'},body:'{}'})),'{}');
  await assert.rejects(readBody(new Request('https://example.test',{method:'POST',headers:{'content-type':'application/json'},body:'😀'.repeat(3000)})),/payload_too_large/);
  const stream=new ReadableStream({start(controller){controller.enqueue(new Uint8Array(5000));controller.enqueue(new Uint8Array(5000));controller.close();}});
  await assert.rejects(readBody(new Request('https://example.test',{method:'POST',headers:{'content-type':'application/json'},body:stream,duplex:'half'})),/payload_too_large/);
  await assert.rejects(readBody(new Request('https://example.test',{method:'POST',headers:{'content-type':'application/json'},body:new Uint8Array([0xc3,0x28])})),/bad_encoding/);
});
