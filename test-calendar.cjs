const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict'),{parseHTML}=require('linkedom');
const {document,window}=parseHTML(fs.readFileSync(__dirname+'/assets/index.html','utf8'));window.confirm=()=>true;let saved={},requests=[],added=0,removed=0,failSave=false;
const Pilot={account:()=> 'test@example.invalid',loadState:()=>JSON.stringify(saved),saveState:json=>{if(failSave)return false;saved=JSON.parse(json);return true;},folders:key=>window.onNativeResult({request:key,folders:['INBOX']}),page:(folder,offset,snapshot,key)=>window.onNativeResult({request:key,folder,messages:[],total:0,nextOffset:0,hasMore:false,snapshot:'1'}),calendars:(month,refresh,key)=>requests.push({month,refresh,key}),calendarSetup:(month,key)=>{added++;window.onNativeResult({request:key,added:true});},removeCalendar:(id,key)=>{assert.equal(id,'source');removed++;window.onNativeResult({request:key,removed:true});}};
vm.runInNewContext(fs.readFileSync(__dirname+'/assets/app.js','utf8'),{document,window,Pilot,lucide:{createIcons(){}},console});
const root=document.getElementById('mail-v2'),click=selector=>{const b=root.querySelector(selector);assert.ok(b,selector);b.dispatchEvent(new window.Event('click',{bubbles:true}));};
const response=(r,title)=>window.onNativeResult({request:r.key,month:r.month,calendars:[{id:'source',title:'Work',at:'2026-10-08T12:00:00Z',cached:true,error:''}],events:[{id:'e',calendarId:'source',calendarTitle:'Work',kind:'remote',date:r.month+'-09',time:'12:00',title,description:'<script>steal()</script>',location:'<img src=x onerror=evil()>'}]});
click('[data-route="calendar"]');assert.equal(requests.length,1);assert.equal(requests[0].refresh,true);response(requests[0],'Work meeting');click('[data-action="calendar-day"][data-id="'+requests[0].month+'-09"]');assert.ok(root.textContent.includes('Work meeting'));assert.equal(root.querySelector('main script'),null);assert.equal(root.querySelector('main img'),null);assert.equal(root.querySelector('[data-action="edit-event"]'),null);assert.equal(root.querySelector('[data-action="delete-event"]'),null);assert.ok(!JSON.stringify(saved).includes('Work meeting'));
click('[data-action="calendar-next"]');const next=requests.at(-1);click('[data-action="calendar-prev"]');const current=requests.at(-1);response(next,'STALE');assert.ok(!root.textContent.includes('STALE'));response(current,'Current');click('[data-action="calendar-day"][data-id="'+current.month+'-09"]');assert.ok(root.textContent.includes('Current'));
click('[data-action="refresh-calendar"]');window.onNativeResult({request:requests.at(-1).key,error:'Offline'});assert.ok(root.textContent.includes('Current'));assert.equal(document.getElementById('status').textContent,'Offline');
click('[data-action="connect-calendar"]');assert.equal(added,1);assert.equal(requests.at(-1).refresh,false);response(requests.at(-1),'Added');click('[data-action="remove-calendar"]');assert.equal(removed,1);window.onNativeResult({request:requests.at(-1).key,month:requests.at(-1).month,calendars:[],events:[]});assert.ok(!root.textContent.includes('Added'));
console.log('PASS: calendar month navigation, stale-result rejection, escaped remote events, read-only server events, offline preservation, native connect/remove and no remote credentials or events in JS storage.');

const fill=(name,value)=>{const el=root.querySelector('[name="'+name+'"]');el.value=value;el.dispatchEvent(new window.Event('input',{bubbles:true}));};
click('[data-action="calendar-day"][data-id="'+requests.at(-1).month+'-09"]');
assert.equal(root.querySelectorAll('.calendar-cell').length,42);
click('[data-action="new-task"]');assert.equal(root.querySelector('[name="date"]').value,requests.at(-1).month+'-09');fill('title','Своё дело');fill('time','14:30');fill('notes','Уточнить у коллег');
root.querySelector('form').dispatchEvent(new window.Event('submit',{bubbles:true,cancelable:true}));
assert.equal(saved.tasks.length,1);assert.equal(saved.tasks[0].mail,null);assert.equal(saved.tasks[0].notes,'Уточнить у коллег');assert.ok(root.querySelector('.calendar-grid'));assert.ok(root.querySelector('.agenda-item.task').textContent.includes('Своё дело'));
click('[data-action="edit-task"]');fill('title','Обновлённое дело');root.querySelector('form').dispatchEvent(new window.Event('submit',{bubbles:true,cancelable:true}));assert.equal(saved.tasks.length,1);assert.ok(root.querySelector('.agenda-item.task').textContent.includes('Обновлённое дело'));
click('[data-action="done"]');assert.equal(saved.tasks[0].done,true);assert.equal(root.querySelector('.agenda-item.task'),null);

console.log('PASS: month grid with 42 dates, selected-date task creation without a mail, comments, edit, completion and return to calendar.');

click('[data-action="refresh-calendar"]');const span=requests.at(-1),month=span.month;
window.onNativeResult({request:span.key,calendars:[],events:[{id:'multi',kind:'remote',title:'Два дня',date:month+'-09',endDate:month+'-11',allDay:true},{id:'night',kind:'remote',title:'Ночной монтаж',date:month+'-09',time:'23:00',endDate:month+'-10',endTime:'02:00'}]});
click('[data-action="calendar-day"][data-id="'+month+'-10"]');assert.ok(root.textContent.includes('Два дня')&&root.textContent.includes('Ночной монтаж'));
click('[data-action="calendar-day"][data-id="'+month+'-11"]');assert.ok(!root.textContent.includes('Два дня')&&!root.textContent.includes('Ночной монтаж'));
click('[data-action="new-task"]');fill('title','Без даты');fill('date','');fill('time','13:00');root.querySelector('form').dispatchEvent(new window.Event('submit',{bubbles:true,cancelable:true}));assert.ok(root.querySelector('form'));assert.equal(saved.tasks.length,1);
console.log('PASS: multi-day all-day end date is exclusive, overnight events remain visible, and a timed task requires a date.');

fill('time','');failSave=true;root.querySelector('form').dispatchEvent(new window.Event('submit',{bubbles:true,cancelable:true}));assert.ok(root.querySelector('form'));assert.equal(saved.tasks.length,1);assert.ok(document.getElementById('status').textContent.includes('Не удалось сохранить'));
failSave=false;root.querySelector('form').dispatchEvent(new window.Event('submit',{bubbles:true,cancelable:true}));assert.equal(saved.tasks.length,2);assert.equal(root.querySelector('form'),null);
console.log('PASS: failed persistence keeps the task form and retry saves exactly once.');
