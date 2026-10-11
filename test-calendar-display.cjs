const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict'),{parseHTML}=require('linkedom');
const {document,window}=parseHTML(fs.readFileSync(__dirname+'/assets/index.html','utf8'));
const requests=[];
const Pilot={account:()=> 'calendar@example.invalid',loadState:()=>JSON.stringify({tasks:[],notes:[],events:[]}),saveState:()=>true,folders:key=>window.onNativeResult({request:key,folders:['INBOX']}),page:(folder,offset,snapshot,key)=>window.onNativeResult({request:key,folder,messages:[],total:0,nextOffset:0,hasMore:false,snapshot:'1'}),calendars:(month,refresh,key)=>requests.push({month,key})};
vm.runInNewContext(fs.readFileSync(process.env.CALENDAR_APP_SOURCE||__dirname+'/assets/app.js','utf8'),{document,window,Pilot,lucide:{createIcons(){}},console});
const root=document.getElementById('mail-v2');
const click=s=>{const el=root.querySelector(s);assert.ok(el,s);el.dispatchEvent(new window.Event('click',{bubbles:true}));};
click('[data-route="calendar"]');
const {month,key}=requests.at(-1),date=month+'-09',tomorrow=month+'-10',after=month+'-11';
const events=[
 {id:'ordinary',kind:'remote',title:'Обычное событие',date,time:'10:00',endDate:date,endTime:'11:45',location:'2013'},
 {id:'night',kind:'remote',title:'Ночная работа',date,time:'23:00',endDate:tomorrow,endTime:'02:00'},
 {id:'all-day',kind:'remote',title:'Два дня',date,endDate:after,allDay:true},
 {id:'single-all-day',kind:'remote',title:'Один день',date,endDate:tomorrow,allDay:true},
 {id:'no-end',kind:'remote',title:'Нет окончания',date,time:'08:30'},
 {id:'task-deadline',kind:'task',title:'Дедлайн задачи',date,time:'14:00',endDate:date,endTime:'15:00'},
 {id:'markup',kind:'remote',title:'Небезопасное место',date,time:'12:00',endDate:date,endTime:'13:00',location:'<img src=x onerror=evil()> & 2040'},
];
window.onNativeResult({request:key,calendars:[],events});
click('[data-action="calendar-day"][data-id="'+date+'"]');
const card=title=>[...root.querySelectorAll('.agenda-item')].find(e=>e.querySelector('h4').textContent===title);
const time=title=>card(title).querySelector('.agenda-time').textContent;
assert.equal(time('Обычное событие'),'10:00–11:45');
assert.equal(card('Обычное событие').querySelector('.agenda-location').textContent,'Место: 2013');
assert.equal(card('Обычное событие').querySelector('[data-lucide="map-pin"]').getAttribute('aria-hidden'),'true');
assert.ok(time('Ночная работа').includes('23:00')&&time('Ночная работа').includes('02:00'));
const dayLabel=day=>new Date(day+'T12:00:00Z').toLocaleDateString('ru-RU',{day:'numeric',month:'short',year:'numeric',timeZone:'UTC'});
assert.equal(time('Ночная работа'),dayLabel(date)+' 23:00 – '+dayLabel(tomorrow)+' 02:00');
assert.equal(time('Два дня'),'Весь день · '+dayLabel(date)+' – '+dayLabel(tomorrow),'all-day end is exclusive');
assert.equal(time('Один день'),'Весь день');
assert.equal(time('Нет окончания'),'08:30');
assert.equal(time('Дедлайн задачи'),'14:00','a task has a deadline, not an event duration');
assert.equal(card('Небезопасное место').querySelector('img'),null);
assert.ok(card('Небезопасное место').textContent.includes('Место: <img src=x onerror=evil()> & 2040'));
click('[data-action="close-day"]');
const search=root.querySelector('#calendar-query');search.value='Обычное событие';search.dispatchEvent(new window.Event('input',{bubbles:true}));
const result=root.querySelector('.calendar-search-result');assert.ok(result.textContent.includes('10:00–11:45'));assert.ok(result.textContent.includes('Место: 2013'));
// Following day retains the full overnight range instead of hiding the finish time.
click('[data-action="clear-calendar-search"]');click('[data-action="calendar-day"][data-id="'+tomorrow+'"]');
assert.ok(time('Ночная работа').includes('02:00'));assert.equal(card('Один день'),undefined);
console.log('PASS: end times in day and search, overnight dates, exclusive all-day end, optional end, labelled/escaped LOCATION.');
