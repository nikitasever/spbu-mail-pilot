// Real-browser regression: the last event and both day actions remain reachable.
const fs=require('node:fs'),path=require('node:path'),http=require('node:http'),assert=require('node:assert/strict');
const {chromium}=require('playwright');
const assets=path.join(__dirname,'assets');
const allowed=new Set(['index.html','app.js','app.css','lucide.min.js','icon-spbu.png','icon-plain.png']);
const server=http.createServer((req,res)=>{const name=req.url==='/'?'index.html':req.url.slice(1);if(!allowed.has(name)){res.writeHead(404).end();return;}res.setHeader('Content-Type',name.endsWith('.css')?'text/css':name.endsWith('.js')?'application/javascript':name.endsWith('.png')?'image/png':'text/html');res.end(fs.readFileSync(path.join(assets,name)));});
function bridge(large){
 const reply=(request,value)=>window.onNativeResult({request,...value});
 window.Pilot={account:()=> 'calendar@example.invalid',loadState:()=>JSON.stringify({tasks:[],notes:[],events:[],settings:{large}}),saveState:()=>true,folders:request=>reply(request,{folders:['INBOX']}),page:(folder,offset,snapshot,request)=>reply(request,{folder,messages:[],total:0,nextOffset:0,hasMore:false,snapshot:'1'}),calendars:(month,refresh,request)=>reply(request,{calendars:[],events:Array.from({length:20},(_,i)=>({id:'long-'+i,kind:'remote',date:month+'-09',time:'10:00',endDate:month+'-09',endTime:'11:45',title:'Событие '+(i+1),description:'Подробное описание '.repeat(10),location:'2013'}))})};
}
(async()=>{
 let browser;
 try{
  await new Promise((resolve,reject)=>{server.once('error',reject);server.listen(0,'127.0.0.1',resolve);});
  browser=await chromium.launch({headless:true,...(process.env.CHROMIUM_PATH?{executablePath:process.env.CHROMIUM_PATH}:{})});
  for(const large of [false,true])for(const viewport of [{width:320,height:568},{width:393,height:850},{width:640,height:360}]){
   const page=await browser.newPage({viewport});await page.addInitScript(bridge,large);
   await page.goto('http://127.0.0.1:'+server.address().port);await page.locator('.bottom [data-route="calendar"]').click();
   await page.locator('[data-action="calendar-day"][data-id$="-09"]').click();
   const popup=page.locator('#day-popup');assert.ok(await popup.evaluate(e=>e.matches(':modal')));
   if(large)assert.ok(await page.locator('.mobile').evaluate(e=>e.classList.contains('text-size-large')));
   if(large)await popup.evaluate(root=>{
    const elements=[root,...root.querySelectorAll('*')].filter(el=>el instanceof HTMLElement);
    const sizes=elements.map(el=>[el,parseFloat(getComputedStyle(el).fontSize)]);
    for(const [el,size] of sizes)el.style.fontSize=(size*2)+'px';
   });
   const list=popup.locator('.day-popup-list');
   // Expand descriptions to exercise unusually long days, not just many short rows.
   await list.locator('details').evaluateAll(items=>items.forEach(e=>e.open=true));
   const geometry=await list.evaluate(e=>{const box=e.getBoundingClientRect(),footer=e.nextElementSibling.getBoundingClientRect();return {client:e.clientHeight,scroll:e.scrollHeight,listBottom:box.bottom,footerTop:footer.top,footerBottom:footer.bottom,height:innerHeight};});
   assert.ok(geometry.client>0,JSON.stringify({large,viewport,geometry}));assert.ok(geometry.scroll>geometry.client,'long day must scroll');
   assert.ok(geometry.listBottom<=geometry.footerTop+1,'footer overlaps list');assert.ok(geometry.footerBottom<=geometry.height,'footer below viewport');
   await list.evaluate(e=>e.scrollTop=e.scrollHeight);
   const last=list.locator('.agenda-item').last();
   const bounds=await last.evaluate(e=>{const r=e.getBoundingClientRect(),list=e.parentElement.getBoundingClientRect();return {bottom:r.bottom,listBottom:list.bottom};});
   assert.ok(bounds.bottom<=bounds.listBottom+1,'last event remains hidden after scrolling to end');
   for(const action of ['new-task','new-event']){
    const button=popup.locator('.day-popup-actions [data-action="'+action+'"]');assert.ok(await button.isVisible());
    const box=await button.boundingBox();assert.ok(box&&box.y>=0&&box.y+box.height<=viewport.height,'action outside viewport');
   }
   await popup.locator('.day-popup-actions [data-action="new-event"]').click();assert.ok(await page.locator('form [name="title"]').isVisible());
   await page.close();
  }
  console.log('PASS: 20-event day, expanded descriptions, both actions, last item reachable at 320/393px and landscape, normal/200% text.');
 }finally{if(browser)await browser.close();await new Promise(resolve=>server.close(resolve));}
})().catch(error=>{console.error(error);process.exitCode=1;});
