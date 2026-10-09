const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict'),{parseHTML}=require('linkedom');
let saved={tasks:[],notes:[],events:[],settings:{}},fail=false,permissions=0;
function app(){
 const {document,window}=parseHTML(fs.readFileSync('assets/index.html','utf8'));
 const Pilot={account:()=> 'fixture@example.invalid',loadState:()=>JSON.stringify(saved),saveState:s=>{if(fail)return false;saved=JSON.parse(s);return true;},backgroundMailStatus:()=>'<unsafe> stopped',allowBackgroundMail:()=>permissions++,folders:r=>window.onNativeResult({request:r,folders:['INBOX']}),page:(folder,offset,snapshot,r)=>window.onNativeResult({request:r,folder,messages:[],total:0,nextOffset:0,hasMore:false,snapshot:'0'})};
 vm.runInNewContext(fs.readFileSync('assets/app.js','utf8'),{document,window,Pilot,lucide:{createIcons(){}},console});
 const root=document.getElementById('mail-v2'),click=s=>{const el=root.querySelector(s);assert.ok(el,s);el.dispatchEvent(new window.Event('click',{bubbles:true}));};click('[data-route="settings"]');
 const toggle=value=>{const el=root.querySelector('[data-setting="backgroundMail"]');el.checked=value;el.dispatchEvent(new window.Event('change',{bubbles:true}));};
 return {root,click,toggle};
}
let a=app();assert.ok(a.root.querySelector('[data-setting="backgroundMail"]').hasAttribute("checked"),'background checks enabled by default');assert.equal(a.root.querySelector('unsafe'),null,'native status escaped');
a.click('[data-action="allow-background-mail"]');assert.equal(permissions,1);a.toggle(false);assert.equal(saved.settings.backgroundMail,false);a=app();assert.ok(!a.root.querySelector('[data-setting="backgroundMail"]').hasAttribute("checked"),'off survives restart');
fail=true;a.toggle(true);assert.equal(saved.settings.backgroundMail,false,'failed save preserves choice');fail=false;a.toggle(true);assert.equal(saved.settings.backgroundMail,true);
console.log('PASS: background mode default, persisted opt-out, failed-save rollback, escaped status and explicit Android permission action.');
