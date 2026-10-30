const {chromium}=require('../node_modules/playwright');
const assert=require('node:assert/strict'),path=require('node:path');
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:process.env.CHROMIUM_EXECUTABLE});const page=await browser.newPage({viewport:{width:1360,height:860}});
 try{
 await page.addInitScript(()=>{
  const callbacks=new Map(),handlers=new Map();let serial=0;
  window.moves=[];window.moveMode='success';window.moveDelay=200;window.destroyed=0;window.locationOfMail='INBOX';
  window.fireClose=()=>handlers.get('tauri://close-requested')?.({event:'tauri://close-requested',id:1,payload:{}});
  window.__TAURI_INTERNALS__={metadata:{currentWindow:{label:'main'},currentWebview:{label:'main',windowLabel:'main'}},transformCallback:cb=>{callbacks.set(++serial,cb);return serial},invoke:async(command,params={})=>{
   if(command==='plugin:event|listen'){handlers.set(params.event,callbacks.get(params.handler));return ++serial}
   if(command==='plugin:window|destroy'){window.destroyed++;return}
   if(command==='account_status'||command==='draft_restore')return null;
   if(command!=='mail_request')return null;
   const {id,method,args}=params.request;let result;
   if(method==='connect')result={connected:true,epoch:23};
   else if(method==='folders')result=['INBOX','Sent','Проекты/2026','<img src=x onerror="window.pwned=1">'];
   else if(method==='page'){
    const rows=[];
    if(args.folder===window.locationOfMail)rows.push({uid:'42',validity:'7',sender:'Учебный отдел',email:'office@example.invalid',subject:'Материалы к заседанию',seen:false});
    if(args.folder==='INBOX')rows.push({uid:'43',validity:'7',sender:'Коллега',email:'other@example.invalid',subject:'Другое письмо',seen:true});
    result={folder:args.folder,messages:rows,snapshot:'one',nextOffset:rows.length,total:rows.length,hasMore:false};
   }else if(method==='body')result={text:args.uid==='42'?'Материалы письма':'Текст другого письма',attachments:[]};
   else if(method==='moveMail'){
    window.moves.push(args);const mode=window.moveMode;if(mode==='error')await new Promise(resolve=>{window.releaseMove=resolve});else await new Promise(r=>setTimeout(r,window.moveDelay));
    if(mode==='unsupported')return {id,error:{code:'MOVE_UNSUPPORTED'}};
    if(mode==='error')return {id,error:{code:'NETWORK'}};
    window.locationOfMail=args.destination;
    result={folder:args.folder,uid:args.uid,validity:mode==='wrong'?'99':args.validity,moved:true,destination:args.destination};
   }else result={connected:false};
   return {id,result};
  }};
 });
 await page.goto('http://127.0.0.1:1420');await page.locator('#user').fill('fixture@example.invalid');await page.locator('#password').fill('fixture');await page.locator('#connect').click();await page.locator('.letter').first().waitFor();
 const open=async()=>{await page.locator('.letter').first().click();await page.locator('#move-mail').waitFor()};
 const picker=async()=>{await page.locator('#move-mail').click();await page.locator('#mail-move-dialog').waitFor()};
 const choose=async()=>{await picker();await page.locator('#mail-move-destination').selectOption('Проекты/2026')};
 const done=()=>page.waitForFunction(()=>!document.getElementById('move-mail').getAttribute('aria-busy')||document.getElementById('move-mail').getAttribute('aria-busy')==='false');
 await open();await picker();assert(await page.locator('#mail-move-submit').isDisabled());assert(await page.locator('#mail-move-cancel').evaluate(b=>b===document.activeElement));
 assert.equal(await page.locator('#mail-move-destination option[value="INBOX"]').count(),0);
 assert.equal(await page.locator('#mail-move-dialog img').count(),0);assert.equal(await page.evaluate(()=>window.pwned),undefined);
 await page.keyboard.press('Escape');assert(await page.locator('#mail-move-dialog').isHidden());assert(await page.locator('#move-mail').evaluate(b=>b===document.activeElement));assert.equal(await page.evaluate(()=>window.moves.length),0);
 // A lost reply keeps the message and blocks automatic/manual repeats until refresh.
 await page.evaluate(()=>window.moveMode='error');await choose();await page.locator('#mail-move-submit').click();
 assert(await page.locator('#mail-move-dialog').isHidden());assert(await page.locator('#move-mail').isDisabled());
 await page.locator('#mail-move-form').evaluate(f=>f.dispatchEvent(new Event('submit',{bubbles:true,cancelable:true})));
 await page.locator('#logout').click();assert(await page.locator('#workspace').isVisible());
 await page.evaluate(()=>window.fireClose());assert.equal(await page.evaluate(()=>window.destroyed),0);await page.evaluate(()=>window.releaseMove());
 await done();assert.equal(await page.evaluate(()=>window.moves.length),1);assert(await page.locator('#move-mail').isDisabled());assert(await page.locator('#toggle-seen').isDisabled());
 assert((await page.locator('#mail-action-status').textContent()).includes('автоматического повтора нет'));assert.equal(await page.locator('.letter').count(),2);
 await page.locator('#refresh').click();await open();assert.equal(await page.locator('#move-mail').isDisabled(),false);
 // Unsupported MOVE has a specific message and never simulates copy/delete.
 await page.evaluate(()=>window.moveMode='unsupported');await choose();await page.locator('#mail-move-submit').click();await done();assert((await page.locator('#mail-action-status').textContent()).includes('не поддерживает безопасное перемещение'));
 await page.locator('#refresh').click();await open();await page.evaluate(()=>{window.moveMode='success';window.moveDelay=350});
 await choose();await page.locator('#mail-move-submit').click();await page.locator('.letter').nth(1).click();
 await page.waitForFunction(()=>document.getElementById('mail-action-status').textContent.includes('Письмо перемещено'));
 await page.waitForFunction(()=>document.querySelectorAll('.letter').length===1);
 assert.equal(await page.locator('#subject').textContent(),'Другое письмо');assert.equal(await page.locator('#body').textContent(),'Текст другого письма');
 assert.deepEqual(await page.evaluate(()=>window.moves[2]),{epoch:23,folder:'INBOX',uid:'42',validity:'7',destination:'Проекты/2026'});
 // The destination list is fetched from the server, not patched with a fake UID.
 await page.locator('#folders button[title="Проекты/2026"]').click();await page.locator('.letter').first().waitFor();await open();await picker();
 assert.equal(await page.locator('#mail-move-destination option[value="Проекты/2026"]').count(),0);
 await page.setViewportSize({width:760,height:600});await page.screenshot({path:path.join(__dirname,'../build/screenshots/mail-move.png')});
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);await page.keyboard.press('Escape');
 // A mismatched acknowledgement is never accepted as successful.
 await page.locator('#move-mail').click();await page.locator('#mail-move-destination').selectOption('INBOX');await page.evaluate(()=>window.moveMode='wrong');await page.locator('#mail-move-submit').click();await done();assert(await page.locator('#move-mail').isDisabled());
 assert((await page.locator('#mail-action-status').textContent()).includes('не подтверждено'));
 await page.locator('#logout').click();await page.locator('#login-screen').waitFor();assert.equal(await page.locator('#mail-action-status').textContent(),'');assert.equal(await page.locator('#mail-move-description').textContent(),'');assert.equal(await page.locator('#mail-move-destination option').count(),0);
 }finally{await browser.close()}
 console.log('PASS mail move UI: server folders, explicit destination/confirmation, focus/Escape, pending close/logout/duplicate guards, failure freezes, capability errors, navigation preserves another reader, fresh source/destination, identity checks, narrow dialog and logout cleanup (fixture).');
})().catch(error=>{console.error(error);process.exitCode=1});
