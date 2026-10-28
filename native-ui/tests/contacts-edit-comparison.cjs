const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict'),os=require('node:os'),path=require('node:path'),cp=require('node:child_process'),{parseHTML}=require('linkedom');
const a={email:'a@example.invalid',sender:'Alice',number:'+7 123'},b={email:'b@example.invalid',sender:'Bob',number:''};
const fixtures=[
 {current:{contacts:[]},replacement:{email:' new@example.invalid ',sender:' New name ',number:' 123 '}},
 {current:{contacts:[]},replacement:{email:'blank@example.invalid',sender:' ',number:''}},
 {current:{contacts:[a,b]},expected:a,replacement:{email:'renamed@example.invalid',sender:'Renamed',number:'456'}},
 {current:{contacts:[a,b]},expected:a,replacement:{email:'A@EXAMPLE.INVALID',sender:'Alice 2',number:''}},
 {current:{contacts:[a]},replacement:{email:'A@example.invalid',sender:'Duplicate',number:''}},
 {current:{contacts:[a,b]},expected:a,replacement:{email:b.email,sender:'Duplicate',number:''}},
 {current:{contacts:[]},replacement:{email:'broken',sender:'Bad',number:''}},
 {current:{contacts:[a,b]},expected:a,replacement:null}
];
for(const fixture of fixtures)Object.assign(fixture.current,{tasks:[],notes:[],events:[]});
function legacy(fixture){
 const {document,window}=parseHTML(fs.readFileSync('assets/index.html','utf8'));let stored=structuredClone(fixture.current);
 window.confirm=()=>true;window.setTimeout=()=>0;window.clearTimeout=()=>{};
 const reply=(r,data)=>window.onNativeResult({request:r,...data});
 const Pilot={account:()=> 'fixture@example.invalid',loadState:()=>JSON.stringify(stored),saveState:json=>{stored=JSON.parse(json);return true;},directoryAvailable:()=>false,folders:r=>reply(r,{folders:['INBOX']}),page:(f,o,s,r)=>reply(r,{messages:[],folder:f,total:0,hasMore:false})};
 vm.runInNewContext(fs.readFileSync('assets/app.js','utf8'),{document,window,Pilot,lucide:{createIcons(){}},console});
 const root=document.getElementById('mail-v2');
 const click=selector=>{const el=root.querySelector(selector);assert.ok(el,selector);el.dispatchEvent(new window.Event('click',{bubbles:true}));};
 click('[data-route="contacts"]');
 if(!fixture.replacement)click('[data-action="delete-contact"][data-id="'+fixture.expected.email+'"]');
 else {
  if(fixture.expected){click('[data-action="contact-card"][data-id="'+fixture.expected.email+'"]');click('[data-action="edit-contact"]');}else click('[data-action="new-contact"]');
  for(const key of ['email','sender','number'])root.querySelector('[name="'+key+'"]').value=fixture.replacement[key];
  click('[data-action="save-local"]');
 }
 return stored.contacts;
}
const temporary=fs.mkdtempSync(path.join(os.tmpdir(),'native-contact-comparison-'));
try {
 const file=path.join(temporary,'fixtures.json');fs.writeFileSync(file,JSON.stringify(fixtures));
 const run=out=>JSON.parse(cp.execFileSync('java',['-cp',out+':lib/*','ru.local.spbumailpilot.NativeContactEditsTest',file],{encoding:'utf8'}));
 const expected=fixtures.map(legacy),actual=run(process.argv[2]);
 const normalized=rows=>rows.map(group=>[...group].sort((a,b)=>a.email.localeCompare(b.email)));
 assert.deepEqual(normalized(actual),normalized(expected));
 // Compile a deliberately broken policy in an isolated directory. The real source is untouched.
 const original=fs.readFileSync('native-ui/app/src/main/java/ru/local/spbumailpilot/NativeContactEdits.java','utf8');
 const canary=original.replace('sender.isEmpty()?email:sender','sender.isEmpty()?"BROKEN":sender');assert.notEqual(canary,original);
 const source=path.join(temporary,'NativeContactEdits.java');fs.writeFileSync(source,canary);
 const jars=fs.readdirSync('lib').filter(x=>x.endsWith('.jar')).map(x=>'lib/'+x).join(':');
 cp.execFileSync('javac',['-encoding','UTF-8','-cp',jars,'-sourcepath','src:native-ui/app/src/main/java','-d',temporary,source,'native-ui/tests/NativeContactEditsTest.java'],{stdio:'pipe'});
 assert.throws(()=>assert.deepEqual(normalized(run(temporary)),normalized(expected)),assert.AssertionError);
 console.log('PASS: 8 actual WebView/native contact CRUD fixtures; isolated blank-name fallback canary detected.');
}finally{fs.rmSync(temporary,{recursive:true,force:true});}
