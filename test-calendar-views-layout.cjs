const fs=require('node:fs'),path=require('node:path'),http=require('node:http'),assert=require('node:assert/strict'),{chromium}=require('playwright');
const server=http.createServer((req,res)=>{const name=req.url==='/'?'index.html':req.url.slice(1);if(!['index.html','app.js','app.css','lucide.min.js'].includes(name)){res.writeHead(404).end();return;}res.setHeader('Content-Type',name.endsWith('.css')?'text/css':name.endsWith('.js')?'application/javascript':'text/html');res.end(fs.readFileSync(path.join(__dirname,'assets',name)));});
(async()=>{await new Promise(r=>server.listen(0,'127.0.0.1',r));const browser=await chromium.launch({headless:true,...(process.env.CHROMIUM_PATH?{executablePath:process.env.CHROMIUM_PATH}:{})});try{
 for(const [width,height,large] of [[320,568,false],[393,850,false],[320,568,true],[640,360,false]]){
  const page=await browser.newPage({viewport:{width,height}}),errors=[];page.on('pageerror',error=>errors.push(error.message));
  await page.addInitScript(()=>{const today=new Date().toLocaleDateString('en-CA',{timeZone:'Europe/Moscow'});const reply=(request,result)=>window.onNativeResult({request,...result});window.Pilot={account:()=> 'fixture@example.invalid',loadState:()=>JSON.stringify({tasks:[{id:'t',date:today,time:'14:00',title:'Моя задача',done:false}],notes:[],events:[],settings:{theme:'dark'}}),saveState:()=>true,folders:r=>reply(r,{folders:['INBOX']}),page:(folder,offset,snapshot,r)=>reply(r,{folder,messages:[],total:0,nextOffset:0,hasMore:false,snapshot:'1'}),calendars:(month,refresh,r)=>reply(r,{calendars:[],events:Array.from({length:12},(_,i)=>({calendarId:'c',id:'e'+i,kind:'remote',date:today,time:'10:00',endDate:today,endTime:'11:45',title:i===11?'Последнее событие':'Обсуждение документов '+i,location:'2013',description:'Описание без пробелов '+('ДлинноеСлово'.repeat(30))}))})};});
  await page.goto('http://127.0.0.1:'+server.address().port);await page.locator('.bottom [data-route="calendar"]').click();
  if(large)await page.evaluate(()=>{const sheet=document.styleSheets[0];for(const rule of ['#mail-v2 .calendar-page {font-size:32px}','#mail-v2 .calendar-page button {font-size:24px}','#mail-v2 .calendar-page .agenda-content h4 {font-size:30px}','#mail-v2 .calendar-page .agenda-content p {font-size:26px}','#mail-v2 .calendar-page .agenda-time {font-size:24px}'])sheet.insertRule(rule,sheet.cssRules.length);});
  for(const mode of ['week','agenda']){
   await page.locator('[data-action="calendar-view"][data-id="'+mode+'"]').click();assert.equal(await page.locator('.calendar-cell').count(),mode==='week'?7:0);
   if(width===393&&!large){fs.mkdirSync(path.join(__dirname,'build'),{recursive:true});await page.screenshot({path:path.join(__dirname,'build','calendar-'+mode+'-0.42.png')});}
   await page.locator('.calendar-period-list details').evaluateAll(items=>items.forEach(el=>el.open=true));
   assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true,'no horizontal overflow '+JSON.stringify({width,large,mode}));
   const last=page.locator('.calendar-period-list .agenda-item').filter({hasText:'Последнее событие'});await last.scrollIntoViewIfNeeded();assert.ok(await last.isVisible());
   await page.locator('.calendar-period-list .agenda-item.task [data-action="edit-task"]').click();assert.equal(await page.locator('[name="title"]').inputValue(),'Моя задача');await page.locator('[data-action="cancel"]').first().click();
   await page.locator('[data-action="calendar-day"][data-id]').last().click();assert.ok(await page.locator('#day-popup').evaluate(el=>el.matches(':modal')));await page.locator('[data-action="close-day"]').click();assert.equal(await page.locator('[data-action="calendar-view"][data-id="'+mode+'"]').getAttribute('aria-pressed'),'true');
  }
  assert.deepEqual(errors,[]);await page.close();
 }
 console.log('PASS: week/agenda in real Chromium at 320/393px and landscape, enlarged text, expanded descriptions, last event, task edit and day sheet without horizontal overflow.');
}finally{await browser.close();server.close();}})().catch(error=>{console.error(error);server.close();process.exit(1);});
