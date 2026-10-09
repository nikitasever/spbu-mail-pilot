const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict'),{parseHTML}=require('linkedom');
const {document,window}=parseHTML(fs.readFileSync('assets/index.html','utf8')),requests=[];
const message={folder:'INBOX',uid:'42',validity:'7',sender:'Отправитель',email:'sender@example.invalid',subject:'Письмо с документами',seen:true};
const reply=(request,data)=>window.onNativeResult({request,...data});
const Pilot={account:()=> 'fixture@example.invalid',loadState:()=> '{}',saveState:()=>true,folders:r=>reply(r,{folders:['INBOX']}),page:(folder,offset,snapshot,r)=>reply(r,{folder,messages:[message],total:1,nextOffset:1,hasMore:false,snapshot:'1'}),body:(folder,uid,validity,r)=>requests.push(r)};
vm.runInNewContext(fs.readFileSync('assets/app.js','utf8'),{document,window,Pilot,lucide:{createIcons(){}},console});
const root=document.getElementById('mail-v2'),click=s=>{const el=root.querySelector(s);assert.ok(el,s);el.dispatchEvent(new window.Event('click',{bubbles:true}));};
click('[data-action="open"]');click('[data-action="back"]');click('[data-action="open"]');assert.equal(requests.length,1,'repeated tap must share pending body request');
reply(requests[0],{cancelled:true});assert.equal(requests.length,2,'cancelled request releases pending state and retries selected mail');
reply(requests[1],{message,text:'Текст',attachments:[{path:'1',name:'Документ.pdf',size:5},{path:'1',name:'Документ.pdf',size:5},{path:'2',name:'Документ.pdf',size:6}]});
assert.equal(root.querySelectorAll('.attachment-card').length,2,'same MIME path once; same name with different paths remains two files');
for(const card of root.querySelectorAll('.attachment-card')){assert.equal(card.querySelectorAll('strong').length,1);assert.equal(card.querySelectorAll('[data-action="attachment"]').length,1);assert.equal(card.querySelectorAll('[data-action="quick-file"]').length,1);assert.ok(!card.querySelector('.attachment-more').hasAttribute('open'));}
click('.header-actions [data-action="refresh"]');assert.equal(requests.length,3);reply(requests[2],{error:'Сеть недоступна'});click('.header-actions [data-action="refresh"]');assert.equal(requests.length,4,'error allows retry');reply(requests[3],{message,text:'Повторно загружено',attachments:[]});assert.ok(root.querySelector('.body-text').textContent.includes('Повторно загружено'));
console.log('PASS: coalesced body requests, cancellation/error retry, one card per MIME path, distinct same-name files retained and single open/download action.');
