const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const {parseHTML}=require('linkedom');
const html=fs.readFileSync(__dirname+'/assets/index.html','utf8'),code=fs.readFileSync(__dirname+'/assets/app.js','utf8');
const fixture=(uid,folder)=>({uid:String(uid),folder,validity:'7',subject:folder+' письмо '+uid,sender:'Отправитель',email:'test@example.org',date:'2026-10-07T12:00:00+03:00',seen:false});
function setup({defer=false,state={}}={}) {
 const {document,window}=parseHTML(html);const queue=[],calls=[];let failNext=false;
 const boxes={INBOX:Array.from({length:137},(_,i)=>fixture(i*7+1,'INBOX')).reverse(),Sent:[fixture(1,'Sent'),fixture(2,'Sent')]};
 const respond=result=>window.onNativeResult(result);
 const Pilot={account:()=> 'test@example.org',loadState:()=>JSON.stringify(state),saveState:v=>{state=JSON.parse(v);},folders:key=>respond({request:key,folders:['INBOX','Sent']}),page:(folder,offset,snapshot,key)=>{
  calls.push({folder,offset,snapshot});const fn=()=>{if(failNext){failNext=false;respond({request:key,error:'Ошибка сети'});return;}const all=boxes[folder],nextOffset=Math.min(offset+50,all.length);respond({request:key,folder,messages:all.slice(offset,nextOffset).map(m=>({...m})),total:all.length,nextOffset,hasMore:nextOffset<all.length,snapshot:folder+'-snapshot'});};
  if(defer)queue.push(fn);else fn();
 },seen:(folder,uid,validity,seen,key)=>respond({request:key,seen}),body:(folder,uid,validity,key)=>{calls.push({body:true,folder,uid});respond({request:key,uid,message:fixture(uid,folder),text:'Текст '+folder+' '+uid,attachments:[]});},logout:()=>{}};
 vm.runInNewContext(code,{document,window,Pilot,lucide:{createIcons(){}},console});
 const root=document.getElementById('mail-v2');
 const click=selector=>{const b=root.querySelector(selector);assert.ok(b,selector);b.dispatchEvent(new window.Event('click',{bubbles:true}));};
 const folder=name=>{const select=root.querySelector('#mail-folder');for(const o of select.querySelectorAll('option'))o.selected=o.value===name;select.dispatchEvent(new window.Event('change',{bubbles:true}));};
 return {root,click,folder,calls,queue,fail:()=>{failNext=true;},state:()=>state};
}
let a=setup();assert.equal(a.root.querySelectorAll('.mail-row').length,50);assert.equal(a.root.querySelector('.inline-hint').textContent,'50 из 137');
a.click('[data-action="more"]');assert.equal(a.root.querySelectorAll('.mail-row').length,100);
a.fail();a.click('[data-action="more"]');assert.equal(a.root.querySelectorAll('.mail-row').length,100);assert.equal(a.root.querySelector('#status').textContent,'Ошибка сети');
a.click('[data-action="more"]');assert.equal(a.root.querySelectorAll('.mail-row').length,137);assert.equal(new Set([...a.root.querySelectorAll('.mail-row')].map(b=>b.dataset.id)).size,137);assert.equal(a.root.querySelector('[data-action="more"]'),null);assert.deepEqual(a.calls.map(c=>c.offset),[0,50,100,100]);
a.folder('Sent');assert.equal(a.root.querySelectorAll('.mail-row').length,2);assert.equal(a.root.querySelector('#heading').textContent,'Sent');a.click('[data-action="open"]');assert.ok(a.root.querySelector('.body-text').textContent.includes('Sent'));
a=setup({defer:true});assert.equal(a.queue.length,1);a.folder('Sent');assert.equal(a.queue.length,2);a.queue[1]();assert.equal(a.root.querySelectorAll('.mail-row').length,2);a.queue[0]();assert.equal(a.root.querySelectorAll('.mail-row').length,2);assert.equal(a.root.querySelector('#heading').textContent,'Sent');
a=setup({state:{tasks:[{id:'old',title:'Старое поручение',mail:{uid:'1',validity:'7'},done:false},{id:'sent',title:'Исходящее',mail:{uid:'1',validity:'7',folder:'Sent'},done:false}],notes:[],events:[]}});
a.click('[data-route="tasks"]');a.click('[data-action="task-source"][data-id="old"]');assert.equal(a.root.querySelector('.detail h3').textContent,'INBOX письмо 1');assert.equal(a.calls.at(-1).folder,'INBOX');
a.click('[data-route="tasks"]');a.click('[data-action="task-source"][data-id="sent"]');assert.equal(a.root.querySelector('.detail h3').textContent,'Sent письмо 1');assert.equal(a.calls.at(-1).folder,'Sent');
console.log('PASS: 137 messages accessible without duplicates, retry keeps cursor, folder switch, stale callbacks ignored, direct old-task links, legacy task compatibility and same-UID folder separation.');
