const {chromium}=require('../node_modules/playwright');
const assert=require('node:assert/strict');
const path=require('node:path'),fs=require('node:fs');const output=path.join(__dirname,'../build/screenshots');fs.mkdirSync(output,{recursive:true});
(async()=>{
 const browser=await chromium.launch({headless:true,...(process.env.CHROMIUM_EXECUTABLE?{executablePath:process.env.CHROMIUM_EXECUTABLE}:{})});const page=await browser.newPage({viewport:{width:1360,height:860}});
 await page.addInitScript(()=>{
  window.__TAURI_INTERNALS__={invoke:async(command,{request})=>{
   const {id,method,args}=request;let result;
   if(method==='connect'){if(args.password!=='fixture-password')return {id,error:{code:'AUTH'}};result={connected:true}}
   else if(method==='folders')result=['INBOX','Sent Items','Drafts','Архив/2026'];
   else if(method==='page')result={folder:args.folder,messages:[{uid:'42',validity:'7',sender:'Учебный отдел',email:'fixture@example.invalid',subject:'Материалы к заседанию',preview:'Коллеги, направляем документы.',date:'2026-10-28T07:10:00Z',seen:false,hasAttachments:true},{uid:'43',validity:'7',sender:'<img src=x onerror="window.pwned=1">',subject:'Письмо с буквальным HTML',preview:'Текст письма',date:'2026-10-27T07:10:00Z',seen:true}],snapshot:'one',nextOffset:2,total:2,hasMore:false};
   else if(method==='body'){
    if(args.uid==='42')await new Promise(r=>setTimeout(r,250));
    result={text:args.uid==='42'?'Коллеги, добрый день!\n\nНаправляем материалы к заседанию. Просим ознакомиться с документами до пятницы.\n\nС уважением,\nУчебный отдел':'<script>window.pwned=1</script>Текущий текст',attachments:[]};
   }else if(method==='cancel')result={cancelled:true};else result={connected:false};
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
 await page.setViewportSize({width:800,height:650});await page.screenshot({path:path.join(output,'narrow.png')});
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
 await page.locator('#logout').click();assert.equal(await page.locator('#body').textContent(),'');assert.equal(await page.locator('#folders button').count(),0);
 await browser.close();console.log('PASS desktop UI: real component rendering, folder selection data, empty/password states, late read ignored, literal HTML, logout cleanup and narrow layout (fixture transport).');
})().catch(error=>{console.error(error);process.exitCode=1});
