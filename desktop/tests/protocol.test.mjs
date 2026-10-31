import {test} from 'node:test';
import assert from 'node:assert/strict';
import {Client,Lane,StaleReply,MailFault,failure,parsePage,folderLabel} from '../src/protocol.ts';
test('a late reply cannot replace a newer route, cancellation names the original request',async()=>{
  const pending=new Map(),seen=[];
  const client=new Client((command,{request})=>{seen.push(request);return new Promise(resolve=>pending.set(request.id,resolve))});
  const lane=new Lane(client);const first=lane.run('body',{uid:'1'});const rejected=assert.rejects(first,StaleReply);const second=lane.run('body',{uid:'2'});
  assert.equal(seen[1].method,'cancel');assert.equal(seen[1].args.requestId,'r1');
  pending.get('r3')({id:'r3',result:'current'});assert.equal(await second,'current');pending.get('r1')({id:'r1',result:'stale'});await rejected;
});
test('protocol identity and safe failures',async()=>{const client=new Client(async()=>({id:'wrong',result:'secret'}));await assert.rejects(client.start('folders').promise,MailFault);assert(!failure(new Error('private password')).includes('private'));assert.equal(folderLabel('Sent Items'),'Отправленные')});
test('page snapshots reject inconsistent pagination and retain exact UID validity',()=>{
 const raw={folder:'INBOX',messages:[{uid:'42',validity:'7',subject:'<script>literal</script>'}],snapshot:'one',nextOffset:1,total:2,hasMore:true};
 const page=parsePage(raw,'INBOX',0);assert.equal(page.messages[0].validity,'7');assert.equal(page.messages[0].subject,'<script>literal</script>');assert.throws(()=>parsePage(raw,'Sent',0));assert.throws(()=>parsePage({...raw,nextOffset:0},'INBOX',0));
});
test('native busy code remains actionable without echoing arbitrary native errors',async()=>{
 const client=new Client(async()=>{throw 'BUSY'});await assert.rejects(client.start('folders').promise,error=>error instanceof MailFault&&error.code==='BUSY');
 const unknown=new Client(async()=>{throw 'password=not-public'});await assert.rejects(unknown.start('folders').promise,error=>error instanceof MailFault&&error.code==='ENGINE_UNAVAILABLE');
});
