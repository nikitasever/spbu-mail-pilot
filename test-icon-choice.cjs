const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict'),{parseHTML}=require('linkedom');

const {document,window}=parseHTML(fs.readFileSync('assets/index.html','utf8'));
let selected='spbu',failure=false;
const calls=[];
const reply=(request,data)=>window.onNativeResult({request,...data});
const Pilot={
 account:()=> 'fixture@example.invalid',
 loadState:()=>JSON.stringify({tasks:[],notes:[],events:[],settings:{}}),
 saveState:()=>true,
 launcherIcon:()=>selected,
 setLauncherIcon:(value,request)=>{
  calls.push(value);
  if(failure){reply(request,{error:'Не удалось изменить иконку.'});return;}
  selected=value;reply(request,{icon:value});
 },
 folders:request=>reply(request,{folders:['INBOX']}),
 page:(folder,offset,snapshot,request)=>reply(request,{folder,messages:[],total:0,nextOffset:0,hasMore:false,snapshot:'1'})
};
vm.runInNewContext(fs.readFileSync('assets/app.js','utf8'),{document,window,Pilot,lucide:{createIcons(){}},console});
const root=document.getElementById('mail-v2');
const click=selector=>{
 const button=root.querySelector(selector);assert.ok(button,selector);
 button.dispatchEvent(new window.Event('click',{bubbles:true}));
};
const checked=()=>Array.from(root.querySelectorAll('[data-action="choose-icon"][aria-pressed="true"]')).map(button=>button.dataset.id);

click('[data-route="settings"]');
assert.equal(root.querySelectorAll('[data-action="choose-icon"]').length,2,'both launcher choices remain available');
assert.equal(root.querySelector('[data-launcher-icon]')===null,true,'there must be only one icon selector');
assert.deepEqual(checked(),['spbu']);
click('[data-action="choose-icon"][data-id="classic"]');
assert.deepEqual(calls,['classic'],'card passes the requested icon to the native bridge');
assert.deepEqual(checked(),['classic'],'native success updates the accessible selection');
assert.equal(document.getElementById('status').textContent,'Иконка изменена.');

failure=true;
click('[data-action="choose-icon"][data-id="spbu"]');
assert.deepEqual(calls,['classic','spbu']);
assert.equal(selected,'classic','failed operation does not change the native icon');
assert.deepEqual(checked(),['classic'],'failed operation preserves the selected card');
assert.equal(document.getElementById('status').textContent,'Не удалось изменить иконку.','native failure is visible');

failure=false;
click('[data-action="choose-icon"][data-id="spbu"]');
assert.deepEqual(checked(),['spbu'],'the user can retry after a failure');
click('[data-route="mail"]');click('[data-route="settings"]');
assert.deepEqual(checked(),['spbu'],'selection is read from the native bridge on returning to settings');
console.log('PASS: one launcher icon chooser, native success/error feedback, accessible selection and retry.');
