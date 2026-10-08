const fs=require('node:fs'),path=require('node:path'),http=require('node:http'),assert=require('node:assert/strict');
const {chromium}=require('playwright');
const root=path.join(__dirname,'assets');
const server=http.createServer((req,res)=>{const name=req.url==='/'?'index.html':req.url.slice(1);if(!['index.html','app.js','app.css','lucide.min.js'].includes(name)){res.writeHead(404).end();return;}res.setHeader('Content-Type',name.endsWith('.css')?'text/css':name.endsWith('.js')?'application/javascript':'text/html');res.end(fs.readFileSync(path.join(root,name)));});
function bridge(defer){
 const long='Длинноеназваниебезпробелов'.repeat(35);
 const m={uid:'1',folder:'INBOX',validity:'7',subject:long,sender:long,email:'test'+('x'.repeat(180))+'@example.invalid',date:'2026-10-08T09:00:00Z',seen:true};
 const respond=(request,result)=>window.onNativeResult({request,...result});
 window.Pilot={account:()=>m.email,loadState:()=>JSON.stringify({tasks:[{id:'task-demo',title:'Подготовить документы к встрече',date:new Date().toLocaleDateString('en-CA',{timeZone:'Europe/Moscow'}),time:'15:00',done:false}],notes:[],events:[]}),saveState:()=>true,calendars:(month,refresh,request)=>respond(request,{calendars:[{id:'work',title:'Публичные мероприятия',at:new Date().toISOString()}],events:[{id:'calendar-demo',kind:'remote',calendarTitle:'Публичные мероприятия',title:'Совещание отдела мультимедиа',date:new Date().toLocaleDateString('en-CA',{timeZone:'Europe/Moscow'}),time:'10:00',endDate:new Date().toLocaleDateString('en-CA',{timeZone:'Europe/Moscow'}),endTime:'11:00',location:'Университетская наб., 11'}]}),folders:request=>{const reply=()=>respond(request,{folders:['INBOX',long]});if(defer)window.finishFolders=reply;else reply();},page:(folder,offset,snapshot,request)=>{const reply=()=>respond(request,{folder,messages:[m],total:1,nextOffset:1,hasMore:false,snapshot:'1'});if(defer)window.finishPage=reply;else reply();},body:(folder,uid,validity,request)=>{window.bodyCalls=(window.bodyCalls||0)+1;respond(request,{message:m,text:long+'\nhttps://example.invalid/'+long,attachments:[{name:long+'.pdf',path:'1'}]});},seen:(folder,uid,validity,value,request)=>respond(request,{seen:value})};
}
(async()=>{
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 const edge='C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe';
 const browser=await chromium.launch({headless:true,...(process.platform==='win32'&&fs.existsSync(edge)?{executablePath:edge}:{})});
 try{
  const page=await browser.newPage();await page.addInitScript(bridge,false);
  const origin='http://127.0.0.1:'+server.address().port;
  async function fits(screen){
   const sizes=await page.evaluate(()=>({viewport:innerWidth,page:document.documentElement.scrollWidth,body:document.body.scrollWidth,overflow:[...document.querySelectorAll('#mail-v2 *')].filter(e=>{const r=e.getBoundingClientRect();return r.width&&r.right>innerWidth+1;}).slice(0,4).map(e=>e.className||e.tagName)}));
   assert.ok(sizes.page<=sizes.viewport&&sizes.body<=sizes.viewport,screen+': '+JSON.stringify(sizes));
   assert.deepEqual(sizes.overflow,[],screen+' has content outside the screen');
   await page.evaluate(()=>scrollTo(200,scrollY));assert.equal(await page.evaluate(()=>scrollX),0,screen+' allows horizontal movement');
  }
  for(const width of [320,360,393,412]){
   await page.setViewportSize({width,height:850});await page.goto(origin);await fits('inbox '+width);
   await page.locator('[data-action="open"]').click();await fits('message/attachment '+width);await page.locator('.header-actions [data-action="refresh"]').click();assert.equal(await page.evaluate(()=>window.bodyCalls),2,'message refresh must fetch fresh content');
   await page.locator('[data-action="reply"]').click();await fits('composer '+width);
   for(const route of ['profile','contacts','calendar','settings']){await page.evaluate(route=>document.querySelector('#menu [data-route="'+route+'"]').click(),route);await fits(route+' '+width);}
   await page.evaluate(()=>document.querySelector('#menu [data-route="calendar"]').click());if(width===393){const label=await page.locator('#account-label').textContent();await page.locator('#account-label').evaluate(e=>e.textContent='test@example.invalid ▾');await page.screenshot({path:path.join(__dirname,'build/calendar-0.15.png')});await page.locator('#account-label').evaluate((e,text)=>e.textContent=text,label);}const height=await page.evaluate(()=>document.documentElement.scrollHeight);const navigation=await page.locator('.month-navigation').evaluate(e=>[...e.children].map(x=>x.getBoundingClientRect().top));assert.ok(Math.max(...navigation)-Math.min(...navigation)<20,'month arrows wrapped into separate rows');await page.locator('.folder-menu summary').click();await fits('menu '+width);assert.equal(await page.evaluate(()=>document.documentElement.scrollHeight),height,'opening menu changed page height');
  }
  const delayed=await browser.newPage({viewport:{width:393,height:850}});await delayed.addInitScript(bridge,true);await delayed.goto(origin);
  await delayed.locator('[data-action="compose"]').click();await delayed.locator('[name="to"]').fill('recipient@example.invalid');
  await delayed.evaluate(()=>{window.originalInput=document.querySelector('[name="to"]');window.finishPage();window.finishFolders();});
  assert.ok(await delayed.evaluate(()=>document.activeElement===window.originalInput&&document.querySelector('[name="to"]')===window.originalInput),'background mail loading replaced focused composer');
  assert.equal(await delayed.locator('[name="to"]').inputValue(),'recipient@example.invalid');
  console.log('PASS: actual Chromium layout at 320/360/393/412px, long senders/subjects/URLs/attachments, inbox/detail/composer/profile/contacts/calendar/settings/menu, no horizontal scrolling and composer focus survives background loading.');
 }finally{await browser.close();server.close();}
})().catch(error=>{console.error(error);server.close();process.exitCode=1;});
