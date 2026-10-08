import {readFile} from 'node:fs/promises';
import {before,after,beforeEach,test} from 'node:test';
import {strict as assert} from 'node:assert';
import {initializeTestEnvironment,assertSucceeds,assertFails} from '@firebase/rules-unit-testing';
import {doc,setDoc,getDoc,deleteDoc,writeBatch,collection,collectionGroup,query,where,orderBy,documentId,limit,getDocs,serverTimestamp,Timestamp} from 'firebase/firestore';
let env;
const month=new Date().toISOString().slice(0,7);
const device='a'.repeat(32)+'_'+month;
const person=(name,visible=true)=>({username:name,instagram:'',emoji:'🫠',visible,updatedAt:serverTimestamp()});
const db=(uid='alice',verified=true)=>env.authenticatedContext(uid,{email_verified:verified,firebase:{sign_in_provider:'google.com'}}).firestore();
before(async()=>{env=await initializeTestEnvironment({projectId:'demo-doomscore',firestore:{host:'127.0.0.1',port:8187,rules:await readFile(new URL('../firestore.rules',import.meta.url),'utf8')}})});
after(async()=>{await env.cleanup()});
beforeEach(async()=>{await env.clearFirestore()});
function join(database,uid,username,total=1,visible=true){
 const b=writeBatch(database);const p=person(username,visible);
 b.set(doc(database,'profiles',uid),p);
 b.set(doc(database,'usernames',username),{uid,updatedAt:serverTimestamp()});
 b.set(doc(database,'contributions',uid,'devices',device),{month,total,updatedAt:serverTimestamp()});
 b.set(doc(database,'seasons',month,'entries',uid),{...p,uid,reels:total,contribution:device});return b.commit();
}
test('Google-verified identity can atomically reserve a unique username and publish a score',async()=>{await assertSucceeds(join(db(),'alice','alice'));assert.equal((await getDoc(doc(db(),'profiles','alice'))).data().username,'alice')});
test('unsigned and unverified users cannot publish',async()=>{await assertFails(join(env.unauthenticatedContext().firestore(),'alice','alice'));await assertFails(join(db('alice',false),'alice','alice'))});
test('public board exposes only opted-in profile fields, including zero-score entrants',async()=>{
 await join(db(),'alice','alice',0);const anon=env.unauthenticatedContext().firestore();
 const q=query(collection(anon,'seasons',month,'entries'),where('visible','==',true),orderBy('reels','desc'),orderBy(documentId()),limit(50));
 const result=await assertSucceeds(getDocs(q));assert.equal(result.size,1);
 assert.deepEqual(Object.keys(result.docs[0].data()).sort(),['contribution','emoji','instagram','reels','uid','updatedAt','username','visible'].sort());
});
test('emails, Google names/photos, passwords and rank fields are rejected',async()=>{
 for(const key of ['email','googleName','photoURL','password','rank','isAdmin']) {
  const database=db();const b=writeBatch(database);b.set(doc(database,'profiles','alice'),{...person('alice'),[key]:'secret'});b.set(doc(database,'usernames','alice'),{uid:'alice',updatedAt:serverTimestamp()});await assertFails(b.commit());
 }
});
test('another account cannot read private profiles or credentials or modify/delete scores',async()=>{
 await join(db(),'alice','alice');const other=db('bob');
 await assertFails(getDoc(doc(other,'profiles','alice')));await assertFails(getDoc(doc(other,'contributions','alice','devices',device)));
 await assertFails(deleteDoc(doc(other,'seasons',month,'entries','alice')));
 await assertFails(setDoc(doc(other,'seasons',month,'entries','alice'),{...person('bob'),uid:'alice',reels:100,contribution:device}));
 await assertFails(getDoc(doc(other,'unknown','credentials')));
});
test('a username cannot be stolen or shared by two identities',async()=>{await join(db(),'alice','taken');await assertFails(join(db('bob'),'bob','taken'));await assertFails(setDoc(doc(db('bob'),'usernames','taken'),{uid:'bob',updatedAt:serverTimestamp()}))});
test('hidden participants do not appear in public queries and cannot be directly read',async()=>{
 await join(db(),'alice','alice',1,false);const anon=env.unauthenticatedContext().firestore();
 await assertFails(getDoc(doc(anon,'seasons',month,'entries','alice')));
 assert.equal((await getDocs(query(collection(anon,'seasons',month,'entries'),where('visible','==',true)))).size,0);
 await assertSucceeds(getDoc(doc(db(),'seasons',month,'entries','alice')));
});
test('score edits require an atomic matching device contribution and are bounded by elapsed time',async()=>{
 await join(db(),'alice','alice',10);
 await env.withSecurityRulesDisabled(async c=>{const d=c.firestore();const old=Timestamp.fromMillis(Date.now()-60000);await setDoc(doc(d,'seasons',month,'entries','alice'),{updatedAt:old},{merge:true});await setDoc(doc(d,'contributions','alice','devices',device),{updatedAt:old},{merge:true})});
 const database=db();const score=doc(database,'seasons',month,'entries','alice');const p=(await getDoc(score)).data();
 await assertFails(setDoc(score,{...p,reels:60,updatedAt:serverTimestamp()}));
 const b=writeBatch(database);b.set(score,{...p,reels:60,updatedAt:serverTimestamp()});b.set(doc(database,'contributions','alice','devices',device),{month,total:60,updatedAt:serverTimestamp()});await assertSucceeds(b.commit());
 const forged=writeBatch(database);forged.set(score,{...p,reels:200000,updatedAt:serverTimestamp()});forged.set(doc(database,'contributions','alice','devices',device),{month,total:200000,updatedAt:serverTimestamp()});await assertFails(forged.commit());
});
test('private contribution cannot be reset while its season score exists',async()=>{await join(db(),'alice','alice');await assertFails(deleteDoc(doc(db(),'contributions','alice','devices',device)))});
test('owner-only collection-group reads support privacy edits and deletion',async()=>{
 await join(db(),'alice','alice');await join(db('bob'),'bob','bob');
 const records=await assertSucceeds(getDocs(query(collectionGroup(db(),'entries'),where('uid','==','alice'),orderBy(documentId()),limit(50))));assert.equal(records.size,1);
 await assertFails(getDocs(query(collectionGroup(db('bob'),'entries'),where('uid','==','alice'),limit(50))));
});
test('reports require a rate-limited action bound to one target',async()=>{
 await join(db(),'alice','alice');await join(db('bob'),'bob','bob');const database=db('bob');
 await assertFails(setDoc(doc(database,'reports','bob','items','alice'),{reason:'spam',updatedAt:serverTimestamp()}));
 const b=writeBatch(database);b.set(doc(database,'reports','bob','items','alice'),{reason:'spam',updatedAt:serverTimestamp()});b.set(doc(database,'limits','bob'),{kind:'report',target:'alice',updatedAt:serverTimestamp()});await assertSucceeds(b.commit());
 const repeat=writeBatch(database);repeat.set(doc(database,'reports','bob','items','alice'),{reason:'spam',updatedAt:serverTimestamp()});repeat.set(doc(database,'limits','bob'),{kind:'report',target:'alice',updatedAt:serverTimestamp()});await assertFails(repeat.commit());
 await assertFails(getDoc(doc(db(),'reports','bob','items','alice')));
});
test('account data can be deleted by its owner in an atomic batch',async()=>{
 await join(db(),'alice','alice');const database=db();const b=writeBatch(database);
 for(const path of [`seasons/${month}/entries/alice`,`contributions/alice/devices/${device}`,'profiles/alice','usernames/alice']) b.delete(doc(database,path));await assertSucceeds(b.commit());
});

test('anonymous guests can join while unsupported auth providers cannot',async()=>{
 const guest=env.authenticatedContext('guest',{firebase:{sign_in_provider:'anonymous'}}).firestore();
 await assertSucceeds(join(guest,'guest','guest'));
 const other=env.authenticatedContext('other',{email_verified:true,firebase:{sign_in_provider:'custom'}}).firestore();
 await assertFails(join(other,'other','other'));
});
test('historic season totals cannot be rewritten, while privacy edits remain possible',async()=>{
 await join(db(),'alice','alice');
 const previous=new Date();previous.setUTCMonth(previous.getUTCMonth()-1);const old=previous.toISOString().slice(0,7);
 await env.withSecurityRulesDisabled(async c=>{await setDoc(doc(c.firestore(),'seasons',old,'entries','alice'),{...(await getDoc(doc(c.firestore(),'seasons',month,'entries','alice'))).data()})});
 const database=db();const path=doc(database,'seasons',old,'entries','alice');const data=(await getDoc(path)).data();
 await assertFails(setDoc(path,{...data,reels:999,updatedAt:serverTimestamp()}));
 const b=writeBatch(database);b.set(doc(database,'profiles','alice'),person('alice',false));b.set(path,{...data,visible:false,updatedAt:serverTimestamp()});await assertSucceeds(b.commit());
});
test('contribution growth cannot outrun elapsed viewing time',async()=>{
 await join(db(),'alice','alice');
 await assertFails(setDoc(doc(db(),'contributions','alice','devices',device),{month,total:100000,updatedAt:serverTimestamp()}));
});

test('another installation or local reset adds its contribution without replacing retained totals',async()=>{
 await join(db(),'alice','alice',10);
 await env.withSecurityRulesDisabled(async c=>{await setDoc(doc(c.firestore(),'seasons',month,'entries','alice'),{updatedAt:Timestamp.fromMillis(Date.now()-60000)},{merge:true})});
 const database=db();const score=doc(database,'seasons',month,'entries','alice');const data=(await getDoc(score)).data();const fresh='b'.repeat(32)+'_'+month;
 const b=writeBatch(database);b.set(doc(database,'contributions','alice','devices',fresh),{month,total:5,updatedAt:serverTimestamp()});b.set(score,{...data,reels:15,contribution:fresh,updatedAt:serverTimestamp()});await assertSucceeds(b.commit());
 assert.equal((await getDoc(score)).data().reels,15);assert.equal((await getDoc(doc(database,'contributions','alice','devices',device))).data().total,10);
});
