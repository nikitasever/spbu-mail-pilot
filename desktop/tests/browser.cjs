const {chromium}=require('../node_modules/playwright');
const assert=require('node:assert/strict');
const path=require('node:path'),fs=require('node:fs');const output=path.join(__dirname,'../build/screenshots');fs.mkdirSync(output,{recursive:true});
(async()=>{
 const browser=await chromium.launch({headless:true,...(process.env.CHROMIUM_EXECUTABLE?{executablePath:process.env.CHROMIUM_EXECUTABLE}:{})});const page=await browser.newPage({viewport:{width:1360,height:860}});
 try {
 await page.addInitScript(()=>{
  const callbacks=new Map();let callbackSerial=0,progressCallback,activeDownload;
  window.__TAURI_INTERNALS__={metadata:{currentWindow:{label:'main'},currentWebview:{label:'main',windowLabel:'main'}},transformCallback:handler=>{const id=++callbackSerial;callbacks.set(id,handler);return id},invoke:async(command,params)=>{
   if(command==='plugin:event|listen'){if(params.event==='attachment-progress')progressCallback=callbacks.get(params.handler);return 1}
   if(command==='open_saved'){window.openedToken=params.token;return}
   const {request}=params;
   const {id,method,args}=request;let result;
   if(method==='connect'){if(args.password!=='fixture-password')return {id,error:{code:'AUTH'}};result={connected:true}}
   else if(method==='folders')result=['INBOX','Sent Items','Drafts','Архив/2026'];
   else if(method==='page')result={folder:args.folder,messages:[{uid:'42',validity:'7',sender:'Учебный отдел',email:'fixture@example.invalid',subject:'Материалы к заседанию',preview:'Коллеги, направляем документы.',date:'2026-10-28T07:10:00Z',seen:false,hasAttachments:true},{uid:'43',validity:'7',sender:'<img src=x onerror="window.pwned=1">',subject:'Письмо с буквальным HTML',preview:'Текст письма',date:'2026-10-27T07:10:00Z',seen:true}],snapshot:'one',nextOffset:2,total:2,hasMore:false};
   else if(method==='body'){
    if(args.uid==='42')await new Promise(r=>setTimeout(r,250));
    result={text:args.uid==='42'?'Коллеги, добрый день!\n\nНаправляем материалы к заседанию. Просим ознакомиться с документами до пятницы.\n\nС уважением,\nУчебный отдел':'<script>window.pwned=1</script>Текущий текст',attachments:args.uid==='42'?[{name:'Материалы.pdf',path:'1',size:100},{name:'Пересланное письмо.eml',path:'2',size:300}]:[]};
    }else if(method==='attachment'){
     if(args.path==='2'){window.failedAttempts=(window.failedAttempts||0)+1;if(window.failedAttempts===1)return {id,error:{code:'NETWORK'}}}
     let release;const wait=new Promise(resolve=>release=resolve);activeDownload={id,release};
     await new Promise(resolve=>setTimeout(resolve,10));progressCallback({payload:{id,bytes:25,total:100}});await Promise.race([wait,new Promise(resolve=>setTimeout(resolve,600))]);
     if(activeDownload?.cancelled)return {id,error:{code:'CANCELLED'}};
     result={saved:true,token:id,openable:true};
   }else if(method==='cancel'){if(activeDownload?.id===args.requestId){activeDownload.cancelled=true;activeDownload.release()}result={cancelled:true}}else result={connected:false};
   return {id,result};
  }};
 });
 await page.goto('http://127.0.0.1:1420');await page.screenshot({path:path.join(output,'login.png')});
 await page.locator('#toggle-password').click();assert.equal(await page.locator('#password').getAttribute('type'),'text');
 await page.locator('#user').fill('fixture@example.invalid');await page.locator('#password').fill('fixture-password');await page.locator('#connect').click();
 await page.locator('.letter').first().waitFor();assert.equal(await page.locator('#password').inputValue(),'');assert.equal(await page.locator('#password').getAttribute('type'),'password');
 assert.equal(await page.locator('#folders button').count(),4);assert.equal(await page.locator('#letters img').count(),0);
 await page.locator('.letter').nth(0).click();await page.locator('.letter').nth(1).click();await page.waitForTimeout(400);
 assert((await page.locator('#body').textContent()).includes('Текущий текст'));assert.equal(await page.evaluate(()=>window.pwned),undefined);
 await page.screenshot({path:path.join(output,'security-text.png')});
 await page.locator('.letter').nth(0).click();await page.waitForTimeout(350);await page.screenshot({path:path.join(output,'workspace.png')});
 assert.equal(await page.locator('#attachment-list li').count(),2);
 assert(await page.locator('#attachments').evaluate(el=>el.compareDocumentPosition(document.getElementById('body'))&Node.DOCUMENT_POSITION_FOLLOWING));
 await page.getByRole('button',{name:'Сохранить Материалы.pdf',exact:true}).click();
 await page.locator('.transfer progress[value="25"]').waitFor();
 await page.locator('#folders button').nth(1).click();await page.locator('.letter').first().waitFor();
 assert.equal(await page.locator('.transfer').count(),1); // navigation remains available during transfer
 await page.getByRole('button',{name:'Открыть файл',exact:true}).waitFor();await page.getByRole('button',{name:'Открыть файл',exact:true}).click();
 assert(await page.evaluate(()=>typeof window.openedToken==='string'));
 await page.locator('.letter').first().click();await page.waitForTimeout(350);
 await page.getByRole('button',{name:'Сохранить Пересланное письмо.eml',exact:true}).click();
 await page.getByRole('button',{name:'Повторить',exact:true}).waitFor();await page.getByRole('button',{name:'Повторить',exact:true}).click();
 await page.locator('.transfer progress[value="25"]').waitFor();
 await page.locator('.transfer').last().getByRole('button',{name:'Остановить',exact:true}).click();
 await page.getByText('Операция остановлена.',{exact:true}).waitFor();
 await page.screenshot({path:path.join(output,'attachments.png')});
 await page.setViewportSize({width:800,height:650});await page.screenshot({path:path.join(output,'narrow.png')});
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
 await page.locator('#logout').click();assert.equal(await page.locator('#body').textContent(),'');assert.equal(await page.locator('#transfers').textContent(),'');assert.equal(await page.locator('#folders button').count(),0);
 }finally{await browser.close()}
 console.log('PASS desktop UI: real component rendering, folder selection data, empty/password states, late read ignored, literal HTML, attachment progress/navigation/open/cancel/retry, logout cleanup and narrow layout (fixture transport).');
})().catch(error=>{console.error(error);process.exitCode=1});
