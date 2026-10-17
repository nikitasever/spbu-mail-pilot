// Runs the actual bundled legacy UI locally. No network or real account access.
const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const {parseHTML}=require('../../node_modules/linkedom');
class FixtureDate extends Date {constructor(...args){super(...(args.length?args:['2026-10-15T09:00:00Z']));}static now(){return Date.parse('2026-10-15T09:00:00Z');}}
const cases=JSON.parse(fs.readFileSync(process.argv[2],'utf8'));
function run(c){
 let stored=structuredClone(c.state),attempts=0;
 const {document,window}=parseHTML(fs.readFileSync('assets/index.html','utf8'));
 window.confirm=()=>c.operation!=='cancel-delete';
 const reply=(request,data)=>window.onNativeResult({request,...data});
 const Pilot={account:()=> 'fixture@example.invalid',loadState:()=>JSON.stringify(stored),saveState:j=>{attempts++;if(c.fail)return false;stored=JSON.parse(j);return true;},folders:r=>reply(r,{folders:['INBOX']}),page:(f,o,s,r)=>reply(r,{messages:[],total:0,nextOffset:0,hasMore:false,snapshot:'1'}),calendars:(month,refresh,r)=>reply(r,{month,calendars:[],events:[]})};
 vm.runInNewContext(fs.readFileSync('assets/app.js','utf8'),{document,window,Pilot,Date:FixtureDate,lucide:{createIcons(){}},console});
 const root=document.getElementById('mail-v2');
 const click=q=>{const b=root.querySelector(q);assert.ok(b,q);b.dispatchEvent(new window.Event('click',{bubbles:true}));};
 click('[data-route="calendar"]');click('[data-action="calendar-day"][data-id="2026-10-15"]');
 if(c.operation==='edit'){
  click('[data-action="edit-event"][data-id="local-event"]');
  for(const key of ['title','date','time']){const el=root.querySelector('[name="'+key+'"]');el.value=c.replacement[key];el.dispatchEvent(new window.Event('input',{bubbles:true}));}
  click('[data-action="save-local"]');
 }else click('[data-action="delete-event"][data-id="local-event"]');
 return {name:c.name,state:Object.fromEntries(['events','eventMailLinks','tasks','notes','unknown'].map(k=>[k,stored[k]]))};
}
const result=cases.map(run);fs.writeFileSync(process.argv[3],JSON.stringify(result,null,2)+'\n');console.log('Legacy event cases executed: '+result.length);
