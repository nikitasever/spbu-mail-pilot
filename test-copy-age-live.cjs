const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict'),{parseHTML}=require('linkedom');
let now=Date.parse('2027-01-01T12:00:00Z'),next=0;const timers=new Map();
class ClockDate extends Date{constructor(...args){super(...(args.length?args:[now]));}static now(){return now;}}
const {document,window}=parseHTML(fs.readFileSync('assets/index.html','utf8'));
window.setInterval=(fn,ms)=>{const id=++next;timers.set(id,{fn,ms});return id;};window.clearInterval=id=>timers.delete(id);
Object.defineProperty(document,'hidden',{configurable:true,writable:true,value:false});
let requests=0,offline=true,nativeVisible=true;const reply=(request,data)=>window.onNativeResult({request,...data});
const Pilot={appVisible:()=>nativeVisible,account:()=> 'fixture@example.invalid',loadState:()=>JSON.stringify({tasks:[],notes:[],events:[],settings:{neuralAnalysis:false}}),saveState:()=>true,
 folders:r=>reply(r,{folders:['INBOX']}),page:(f,o,s,r)=>{requests++;reply(r,{messages:[],total:0,hasMore:false,offline,cachedAt:now-20*60000});},send:()=>{throw Error('Unexpected SMTP');}};
vm.runInNewContext(fs.readFileSync('assets/app.js','utf8'),{document,window,Pilot,Date:ClockDate,lucide:{createIcons(){}},console});
const root=document.getElementById('mail-v2'),click=s=>root.querySelector(s).dispatchEvent(new window.Event('click',{bubbles:true}));
assert.equal(root.querySelector('[data-copy-at]').textContent,'20 минут назад');
assert.equal(timers.size,1,'one live-copy timer is active');assert.equal([...timers.values()][0].ms,60000);
click('.header-actions [data-action="toggle-inbox-search"]');
const input=root.querySelector('[name="mail-query"]');input.value='Unsaved query';
now+=61000;[...timers.values()][0].fn();
assert.equal(root.querySelector('[data-copy-at]').textContent,'21 минуту назад');
assert.equal(root.querySelector('[name="mail-query"]'),input,'age updates do not recreate input');assert.equal(input.value,'Unsaved query');assert.equal(timers.size,1);
document.hidden=true;document.dispatchEvent(new window.Event('visibilitychange'));assert.equal(timers.size,0,'no timer in background');
now+=10*60000;document.hidden=false;document.dispatchEvent(new window.Event('visibilitychange'));
assert.equal(root.querySelector('[data-copy-at]').textContent,'31 минуту назад');assert.equal(timers.size,1);
nativeVisible=false;window.sendWindowHidden();assert.equal(timers.size,0,'Android pause stops the timer even when document.hidden is false');
window.systemThemeChanged();assert.equal(timers.size,0,'rendering in paused WebView does not restart the timer');
nativeVisible=true;window.systemThemeChanged();assert.equal(timers.size,1,'Android resume restarts one timer');
assert.equal(requests,1,'clock updates do not load mail');
offline=false;click('.header-actions [data-action="refresh"]');
assert.equal(root.querySelector('[data-copy-at]'),null);assert.equal(timers.size,0,'no timer without copy-age labels');assert.equal(requests,2,'only explicit refresh loads mail');
console.log('PASS: copy age advances without scrolling, keeps unsaved search DOM/text, uses one timer only while visible and makes no mail requests.');
