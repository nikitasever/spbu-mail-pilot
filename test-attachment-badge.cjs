const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict'),{parseHTML}=require('linkedom');
const {document,window}=parseHTML(fs.readFileSync('assets/index.html','utf8'));let reads=0;
const messages=[true,false,undefined].map((presence,i)=>({folder:'INBOX',uid:String(i+1),validity:'7',sender:'Отправитель',email:'sender@example.invalid',subject:'Письмо '+i,seen:true,...(presence===undefined?{}:{hasAttachments:presence})}));
const reply=(request,result)=>window.onNativeResult({request,...result});
const Pilot={account:()=> 'fixture@example.invalid',loadState:()=> '{}',saveState:()=>true,folders:r=>reply(r,{folders:['INBOX']}),page:(folder,offset,snapshot,r)=>reply(r,{folder,messages,total:3,nextOffset:3,hasMore:false,snapshot:'1'}),body:(folder,uid,validity,r)=>{reads++;reply(r,{message:messages.find(m=>m.uid===uid),text:'Текст',attachments:[{path:'1',name:'Документ.pdf'}]})}};
vm.runInNewContext(fs.readFileSync('assets/app.js','utf8'),{document,window,Pilot,lucide:{createIcons(){}},console});
const root=document.getElementById('mail-v2'),click=s=>{const el=root.querySelector(s);assert.ok(Boolean(el),s);el.dispatchEvent(new window.Event('click',{bubbles:true}))};
const badge=uid=>Boolean(root.querySelector('.mail-row[data-id="'+uid+'"] .mail-file'));
assert.equal(reads,0,'listing must not read body');assert.equal(badge('1'),true,'server metadata available before opening');assert.equal(badge('2'),false);assert.equal(badge('3'),false,'unknown structure does not invent attachment');
assert.equal(root.querySelector('.mail-file').getAttribute('aria-label'),'Есть вложения');
for(const uid of ['2','3']){click('.mail-row[data-id="'+uid+'"]');click('[data-action="back"]');}
assert.equal(badge('2'),false,'explicit metadata false overrides decorative cached files');assert.equal(badge('3'),true,'older native bridge can fall back to cached attachment list');assert.equal(badge('1'),true);
assert.equal(reads,2,'only explicit opening reads body');
console.log('PASS: attachment badge before reading, false/unknown distinction, accessible label and cache fallback without automatic body downloads.');
