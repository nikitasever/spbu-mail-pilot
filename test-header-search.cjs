const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict'),{parseHTML}=require('linkedom');
const {document,window}=parseHTML(fs.readFileSync('assets/index.html','utf8'));
const reply=(request,data)=>window.onNativeResult({request,...data});
const Pilot={account:()=> 'fixture@example.invalid',loadState:()=>JSON.stringify({tasks:[],notes:[],events:[],settings:{neuralAnalysis:false}}),
 saveState:()=>true,folders:r=>reply(r,{folders:['INBOX']}),page:(f,o,s,r)=>reply(r,{messages:[],total:0,hasMore:false}),
 calendars:(m,f,r)=>reply(r,{events:[],calendars:[]}),send:()=>{throw Error('Unexpected SMTP');}};
vm.runInNewContext(fs.readFileSync('assets/app.js','utf8'),{document,window,Pilot,lucide:{createIcons(){}},console});
const root=document.getElementById('mail-v2'),click=s=>{const el=root.querySelector(s);assert.ok(el,s);el.dispatchEvent(new window.Event('click',{bubbles:true}));};
const search=()=>root.querySelector('.header-actions [data-action="global-search"],.header-actions [data-action="toggle-inbox-search"]');
assert.equal(search().hidden,false);
for(const route of ['calendar','contacts']){
 click('[data-route="'+route+'"]');assert.equal(search().hidden,true,'inline search replaces header icon in '+route);
 assert.ok(root.querySelector(route==='calendar'?'#calendar-query':'#contacts-query'));
 click('[data-route="mail"]');assert.equal(search().hidden,false,'mail search returns after '+route);
 assert.equal(search().dataset.action,'toggle-inbox-search');
 click('.header-actions [data-action="toggle-inbox-search"]');
 assert.equal(root.querySelector('[data-mail-search]').hasAttribute('hidden'),false);
 assert.equal(root.querySelector('#heading').textContent,'Входящие','mail search stays in the inbox');
 click('.header-actions [data-action="toggle-inbox-search"]');
}
click('[data-route="calendar"]');click('[data-route="settings"]');
assert.equal(search().hidden,false);assert.equal(search().dataset.action,'global-search');
click('.header-actions [data-action="global-search"]');assert.ok(root.querySelector('#smart-query'));
console.log('PASS: local inbox search returns after calendar/contacts, inline searches stay unduplicated, and global search remains accessible from settings.');
