const {chromium}=require('../node_modules/playwright');
const assert=require('node:assert/strict');const path=require('node:path');
(async()=>{
 const browser=await chromium.launch({headless:true,...(process.env.CHROMIUM_EXECUTABLE?{executablePath:process.env.CHROMIUM_EXECUTABLE}:{})});
 try{
 const page=await browser.newPage({viewport:{width:1360,height:860}});
 await page.addInitScript(()=>{
   let serial=0,draftSerial=0;const callbacks=new Map(),handlers=new Map();window.smtpCalls=[];window.fixtureSendMode='copy-failure';
   const emit=(event,payload)=>handlers.get(event)?.({payload});
   window.__TAURI_INTERNALS__={metadata:{currentWindow:{label:'main'},currentWebview:{label:'main',windowLabel:'main'}},transformCallback:callback=>{callbacks.set(++serial,callback);return serial},invoke:async(command,params={})=>{
     if(command==='plugin:event|listen'){handlers.set(params.event,callbacks.get(params.handler));return ++serial}
     if(command==='draft_begin')return {id:`draft-${++draftSerial}`};
     if(command==='draft_attach')return {files:[{token:'copy-token.bin',name:'Материалы.pdf',size:100}]};
     if(command==='draft_remove')return {files:[]};
     if(command==='draft_discard')return;
     if(command!=='mail_request')throw Error('Unexpected fixture command '+command);
     const {id,method,args}=params.request;let result;
     if(method==='connect')result={connected:true};
     else if(method==='folders')result=['INBOX','Sent'];
     else if(method==='page')result={folder:args.folder,messages:[{uid:'42',validity:'7',sender:'Учебный отдел',email:'sender@fixture.invalid',subject:'Заседание',preview:'Документы',date:'2026-10-29T10:00:00Z',seen:true,hasAttachments:false}],snapshot:'a',total:1,nextOffset:1,hasMore:false};
     else if(method==='body')result={text:'Материалы к заседанию',attachments:[],message:{replyTo:'reply@fixture.invalid',messageId:'<parent@fixture.invalid>',references:['<root@fixture.invalid>']}};
     else if(method==='send'){
       window.smtpCalls.push(args);
       await new Promise(r=>setTimeout(r,10));emit('send-progress',{id,stage:'uploading',bytes:25,total:100});await new Promise(r=>setTimeout(r,100));
       if(window.fixtureSendMode==='unknown')return {id,error:{code:'SEND_UNKNOWN'}};
       result={accepted:true,copySaved:window.fixtureSendMode!=='copy-failure'};
     }else result={connected:false};return {id,result};
   }};
 });
 await page.goto('http://127.0.0.1:1420');await page.locator('#user').fill('fixture@example.invalid');await page.locator('#password').fill('fixture');await page.locator('#connect').click();await page.locator('.letter').first().waitFor();
 await page.locator('#new-mail').click();await page.locator('#compose-to').fill('to@fixture.invalid');await page.locator('#compose-subject').fill('Новое письмо');await page.locator('#compose-body').fill('Текст сохраняется при навигации.');
 await page.locator('#compose-attach').click();await page.getByRole('button',{name:'Убрать Материалы.pdf'}).waitFor();await page.getByRole('button',{name:'Убрать Материалы.pdf'}).click();assert.equal(await page.locator('#compose-files li').count(),0);await page.locator('#compose-attach').click();await page.getByRole('button',{name:'Убрать Материалы.pdf'}).waitFor();
 await page.clock.install();
 await page.locator('#compose-send').click();await page.locator('#compose-undo').click();await page.clock.fastForward(11000);assert.equal(await page.evaluate(()=>smtpCalls.length),0);assert.equal(await page.locator('#compose-body').inputValue(),'Текст сохраняется при навигации.');
 await page.locator('#folders button').nth(1).click();await page.locator('.letter').first().waitFor();assert.equal(await page.locator('#compose-body').inputValue(),'Текст сохраняется при навигации.');
 await page.locator('#compose-close').click();assert.equal(await page.locator('#discard-dialog').evaluate(el=>el.open),true);assert.equal(await page.locator('#keep-draft').evaluate(el=>el===document.activeElement),true);await page.keyboard.press('Escape');assert.equal(await page.locator('#composer').isVisible(),true);
 await page.locator('#compose-close').click();await page.locator('#discard-draft').click();await page.locator('#composer').waitFor({state:'hidden'});
 await page.locator('.letter').first().click();await page.locator('#reply-mail').waitFor();await page.locator('#reply-mail').click();await page.locator('#compose-to').waitFor();assert.equal(await page.locator('#compose-to').inputValue(),'reply@fixture.invalid');assert.equal(await page.locator('#compose-subject').inputValue(),'Re: Заседание');
 await page.locator('#compose-body').fill('Ответ на письмо');await page.locator('#smtp-settings summary').click();await page.locator('#smtp-port').fill('0');await page.locator('#compose-send').click();assert.equal(await page.locator('#smtp-port').getAttribute('aria-invalid'),'true');await page.locator('#smtp-port').fill('465');
 await page.locator('#compose-send').click();await page.clock.fastForward(10001);await page.clock.runFor(200);await page.getByText('SMTP-сервер принял письмо, но копия в «Отправленных» не подтверждена. Не отправляйте письмо повторно.',{exact:true}).waitFor();
 const sent=await page.evaluate(()=>smtpCalls);assert.equal(sent.length,1);assert.equal(sent[0].inReplyTo,'<parent@fixture.invalid>');assert.deepEqual(sent[0].references,['<root@fixture.invalid>']);assert.equal(sent[0].smtpSsl,true);assert.equal(sent[0].password,undefined);
 await page.locator('#compose-form').evaluate(form=>form.dispatchEvent(new Event('submit',{cancelable:true})));assert.equal(await page.evaluate(()=>smtpCalls.length),1);assert.equal(await page.locator('#compose-send').isDisabled(),true);
 await page.locator('#compose-close').click();await page.locator('#new-mail').click();await page.locator('#compose-to').fill('next@fixture.invalid');await page.locator('#compose-body').fill('Неизвестный результат не должен повторяться');await page.evaluate(()=>window.fixtureSendMode='unknown');
 await page.locator('#compose-send').click();await page.clock.fastForward(10001);await page.clock.runFor(200);await page.getByText(/Результат отправки неизвестен\. Проверьте/).waitFor();assert.equal(await page.locator('#compose-send').isDisabled(),true);await page.locator('#compose-form').evaluate(form=>form.dispatchEvent(new Event('submit',{cancelable:true})));assert.equal(await page.evaluate(()=>smtpCalls.length),2);
 await page.setViewportSize({width:800,height:650});assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);await page.screenshot({path:path.join(__dirname,'../build/screenshots/compose-unknown.png')});
 await page.locator('#logout').click();assert.equal(await page.locator('#discard-dialog').evaluate(el=>el.open),true);await page.locator('#keep-draft').click();assert.equal(await page.locator('#compose-body').inputValue(),'Неизвестный результат не должен повторяться');
 console.log('PASS compose UI: files, navigation, unsaved guard/focus/Escape, reply headers, SMTP validation, grace cancellation, Sent-copy warning, no repeat after success/unknown, narrow viewport (fixture transport).');
 }finally{await browser.close()}
})().catch(error=>{console.error(error);process.exitCode=1});
