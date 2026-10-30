const {chromium}=require('../node_modules/playwright');
const assert=require('node:assert/strict');
const path=require('node:path');
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:process.env.CHROMIUM_EXECUTABLE});
 const page=await browser.newPage({viewport:{width:1360,height:860}});
 try{
 await page.addInitScript(()=>{
  let serial=0;window.flagRequests=[];window.flagMode='success';window.flags={};window.flagDelay=10;
  window.__TAURI_INTERNALS__={metadata:{currentWindow:{label:'main'},currentWebview:{label:'main',windowLabel:'main'}},transformCallback:()=>++serial,invoke:async(command,params)=>{
   if(command==='account_status'||command==='draft_restore')return null;
   if(command==='plugin:event|listen')return 1;
   const {id,method,args}=params.request;let result;
   const rows=[{uid:'42',validity:'7',sender:'Учебный отдел',email:'office@example.invalid',subject:'Материалы к заседанию',date:'2026-10-28T07:10:00Z',seen:window.flags[args.folder]??false},{uid:'43',validity:'7',sender:'Коллега',email:'person@example.invalid',subject:'Второе письмо',date:'2026-10-28T07:00:00Z',seen:true}];
   if(method==='connect')result={connected:true,epoch:17};
   else if(method==='folders')result=['INBOX','Sent'];
   else if(method==='page')result={folder:args.folder,messages:rows,snapshot:'one',nextOffset:2,total:2,hasMore:false};
   else if(method==='body')result={text:'Текст письма без автоматического изменения статуса.',attachments:[]};
   else if(method==='setSeen'){
    window.flagRequests.push(args);const mode=window.flagMode;await new Promise(r=>setTimeout(r,window.flagDelay));
    if(mode==='error')return {id,error:{code:'NETWORK'}};
    window.flags[args.folder]=args.seen;
    result={folder:args.folder,uid:args.uid,validity:mode==='wrong'?'8':args.validity,seen:args.seen};
   }else result={connected:false};
   return {id,result};
  }};
 });
 await page.goto('http://127.0.0.1:1420');
 const login=async()=>{await page.locator('#user').fill('fixture@example.invalid');await page.locator('#password').fill('fixture');await page.locator('#connect').click();await page.locator('.letter').first().waitFor()};
 const open=async()=>{await page.locator('.letter').first().click();await page.locator('#toggle-seen').waitFor()};
 const waitLabel=label=>page.waitForFunction(label=>{const b=document.getElementById('toggle-seen');return b.textContent===label&&!b.disabled},label);
 await login();await open();assert.equal(await page.evaluate(()=>window.flagRequests.length),0);
 await page.evaluate(()=>window.flagDelay=200);
 await page.locator('#toggle-seen').click();assert(await page.locator('#toggle-seen').isDisabled());
 await page.locator('#toggle-seen').evaluate(b=>b.click());
 await waitLabel('Отметить непрочитанным');assert.equal(await page.evaluate(()=>window.flagRequests.length),1);
 assert.equal(await page.locator('.letter').first().evaluate(b=>b.classList.contains('unread')),false);
 assert.deepEqual(await page.evaluate(()=>window.flagRequests[0]),{epoch:17,folder:'INBOX',uid:'42',validity:'7',seen:true});
 await page.locator('#toggle-seen').click();await waitLabel('Отметить прочитанным');
 assert(await page.locator('.letter').first().evaluate(b=>b.classList.contains('unread')));
 // Failure preserves visible flags, freezes further writes until explicit refresh.
 await page.evaluate(()=>window.flagMode='error');await page.locator('#toggle-seen').click();
 await page.locator('#body-error').waitFor();assert((await page.locator('#body-error').textContent()).includes('Обновите папку'));
 assert(await page.locator('#toggle-seen').isDisabled());assert(await page.locator('.letter').first().evaluate(b=>b.classList.contains('unread')));
 await page.locator('#close-reader').click();await open();assert(await page.locator('#toggle-seen').isDisabled());
 assert((await page.locator('#reader-status').textContent()).includes('Обновите папку'));
 await page.locator('#refresh').click();await open();await waitLabel('Отметить прочитанным');
 // A response for the old message cannot change another reader's status.
 await page.evaluate(()=>{window.flagMode='success';window.flagDelay=250});await page.locator('#toggle-seen').click();
 await page.locator('.letter').nth(1).click();await page.waitForTimeout(350);
 assert.equal(await page.locator('#subject').textContent(),'Второе письмо');assert.equal(await page.locator('#reader-status').textContent(),'Статус прочтения не изменён');
 // Folder reads wait for flag write completion and then fetch the confirmed state.
 await open();await page.locator('#toggle-seen').click();await page.locator('#refresh').click();
 await page.locator('#list-status').filter({hasText:'Загрузка'}).waitFor();await page.waitForTimeout(350);
 assert(await page.locator('.letter').first().evaluate(b=>b.classList.contains('unread')));
 // Wrong UIDVALIDITY in the reply is never accepted.
 await open();await page.evaluate(()=>window.flagMode='wrong');await page.locator('#toggle-seen').click();await page.locator('#body-error').waitFor();
 assert(await page.locator('#toggle-seen').isDisabled());assert(await page.locator('.letter').first().evaluate(b=>b.classList.contains('unread')));
 await page.setViewportSize({width:760,height:600});
 await page.screenshot({path:path.join(__dirname,'../build/screenshots/mail-flags.png')});
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
 await page.setViewportSize({width:1360,height:860});
 // Logout removes private content and ignores an already pending flag response.
 await page.locator('#refresh').click();await open();await page.evaluate(()=>{window.flagMode='success';window.flagDelay=400});
 await page.locator('#toggle-seen').click();await page.locator('#logout').click();await page.waitForTimeout(500);
 assert.equal(await page.locator('#body').textContent(),'');assert(await page.locator('#message').isHidden());assert(await page.locator('#toggle-seen').isHidden());
 }finally{await browser.close()}
 console.log('PASS mail flags: explicit writes, server acknowledgement, duplicate guard, failure recovery, UIDVALIDITY rejection, navigation/refresh ordering, logout isolation and narrow viewport (fixture).');
})().catch(error=>{console.error(error);process.exitCode=1});
