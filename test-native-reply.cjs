const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict'),{parseHTML}=require('linkedom');
const {document,window}=parseHTML(fs.readFileSync('assets/index.html','utf8'));
window.setTimeout=()=>1;window.clearTimeout=()=>{};
let saved={tasks:[],notes:[],events:[],settings:{neuralAnalysis:false,signatureAuto:false},drafts:[]},sends=0,reads=0;
const result=(request,data)=>window.onNativeResult({request,...data});
const Pilot={appVisible:()=>true,account:()=> 'fixture@example.invalid',loadState:()=>JSON.stringify(saved),saveState:json=>{saved=JSON.parse(json);return true;},
 folders:r=>result(r,{folders:['INBOX']}),page:(f,o,s,r)=>result(r,{messages:[],total:0,hasMore:false}),
 send:()=>{sends++;},body:()=>{reads++;throw Error('Reply must reuse loaded message');}};
vm.runInNewContext(fs.readFileSync('assets/app.js','utf8'),{document,window,Pilot,lucide:{createIcons(){}},console});
const payload={text:'Конфиденциальный исходный текст',message:{email:'sender@example.invalid',replyTo:'reply@example.invalid',subject:'Исходная тема',messageId:'<parent@example.invalid>',references:['<root@example.invalid>']}};
assert.equal(window.composeNativeReply(payload),true);
const root=document.getElementById('mail-v2');
assert.equal(root.querySelector('[name="to"]').value,'reply@example.invalid');
assert.equal(root.querySelector('[name="subject"]').value,'Re: Исходная тема');
assert.equal(root.querySelector('[name="body"]').value,'');
assert.equal(saved.drafts.length,1);
assert.equal(saved.drafts[0].inReplyTo,payload.message.messageId);
assert.deepEqual(saved.drafts[0].references,payload.message.references);
assert.deepEqual(saved.drafts[0].attachments,[],'received attachments are not automatically forwarded');
root.querySelector('[name="body"]').value='Мой неотправленный ответ';
root.querySelector('[name="body"]').dispatchEvent(new window.Event('input',{bubbles:true}));
assert.equal(window.composeNativeReply({...payload,message:{...payload.message,subject:'Второй'}}),true);
assert.equal(saved.drafts.length,2,'opening another reply preserves previous draft');
assert.ok(saved.drafts.some(d=>d.body==='Мой неотправленный ответ'));
for(const invalid of [null,{}, {...payload,message:{...payload.message,replyTo:'bad\r\nBcc: other@example.invalid'}}, {...payload,message:{...payload.message,messageId:'bad'}}, {...payload,text:'x'.repeat(100001)}]){
 assert.equal(window.composeNativeReply(invalid),false);assert.equal(saved.drafts.length,2);
}
root.querySelector('form').dispatchEvent(new window.Event('submit',{bubbles:true,cancelable:true}));
assert.ok(root.querySelector('.send-delay'));
assert.equal(window.composeNativeReply(payload),false,'a pending send cannot be replaced by a native reply');
assert.equal(saved.drafts.length,2);
assert.equal(sends,0,'preparing reply never sends');assert.equal(reads,0,'handoff never changes read flags by fetching mail');
console.log('PASS native reply: saved draft, Reply-To and thread preserved, old draft retained, invalid payload rejected, no SMTP or mail fetch.');
