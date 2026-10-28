const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict'),{parseHTML}=require('linkedom');
let instant='2026-12-31T20:59:00Z';
class ClockDate extends Date {constructor(...args){super(...(args.length?args:[instant]));}static now(){return Date.parse(instant);}}
const {document,window}=parseHTML(fs.readFileSync('assets/index.html','utf8'));
const before='2026-12-31',after='2027-01-01',base={folder:'INBOX',validity:'7',sender:'Fixture',email:'fixture@example.invalid',seen:true};
const messages=[{...base,uid:'2',date:after+'T00:01:00+03:00',subject:'New day'},
 {...base,uid:'1',date:before+'T23:59:00+03:00',subject:'Old day'}];
let writes=0;const reply=(request,result)=>window.onNativeResult({request,...result});
const Pilot={account:()=>base.email,loadState:()=>JSON.stringify({tasks:[{id:'due',title:'Deadline',date:before,done:false}],notes:[],events:[],settings:{neuralAnalysis:false}}),
 saveState:()=>{writes++;return true;},folders:r=>reply(r,{folders:['INBOX']}),
 page:(f,o,s,r)=>reply(r,{messages,total:2,nextOffset:2,hasMore:false}),calendars:(m,f,r)=>reply(r,{events:[],calendars:[]}),
 send:()=>{throw Error('Unexpected SMTP');}};
vm.runInNewContext(fs.readFileSync('assets/app.js','utf8'),{document,window,Pilot,Date:ClockDate,lucide:{createIcons(){}},console});
const root=document.getElementById('mail-v2'),click=s=>{const el=root.querySelector(s);assert.ok(el,s);el.dispatchEvent(new window.Event('click',{bubbles:true}));};
assert.equal(root.querySelectorAll('.mail-date-group')[1].textContent,'Сегодня');
click('[data-route="calendar"]');assert.equal(root.querySelector('.calendar-cell.today').dataset.id,before);
const baselineWrites=writes;
instant='2026-12-31T21:01:00Z'; // Moscow is already in the next day, month and year; UTC is not.
click('[data-action="calendar-today"]');
assert.equal(root.querySelector('.calendar-cell.today').dataset.id,after,'Today follows Moscow midnight without restarting');
assert.equal(root.querySelector('.calendar-cell[aria-pressed="true"]').dataset.id,after);
click('[data-route="tasks"]');assert.ok(root.querySelector('.deadline').textContent.includes('Просрочено'));
click('[data-route="mail"]');assert.equal(root.querySelector('.mail-date-group').textContent,'Сегодня');
assert.ok(!root.querySelectorAll('.mail-date-group')[1].textContent.includes('Сегодня'));
assert.equal(writes,baselineWrites,'clock rollover does not add state writes');
console.log('PASS: Moscow midnight/year rollover updates calendar Today, selected day, overdue tasks and inbox date groups without restart or state writes.');
