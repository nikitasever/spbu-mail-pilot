const assert = require('node:assert/strict'), fs = require('node:fs'), os = require('node:os'), path = require('node:path');
const {bundleUi, buildUi} = require('./scripts/build_ui.cjs');

(async () => {
  const html = await import('./ui/core/markup.mjs');
  const mail = await import('./ui/mail/identity.mjs');
  const files = await import('./ui/mail/attachments.mjs');
  const dates = await import('./ui/calendar/dates.mjs');
  const {inferMailDateHint} = await import('./ui/mail/date-hint.mjs');
  const {copyAge} = await import('./ui/mail/copy-age.mjs');
  const contacts = await import('./ui/contacts/identity.mjs');
  const {ruleParts} = await import('./ui/mail/category-rules.mjs');
  const {renderComposeView} = await import('./ui/compose/render.mjs');
  const {renderMailRow} = await import('./ui/mail/row.mjs');
  const {renderReaderView} = await import('./ui/mail/reader.mjs');

  assert.equal(html.esc('<&"\'😀>'), '&lt;&amp;&quot;&#39;😀&gt;');
  assert.ok(html.btn('Действие', 'open', '" onfocus="bad').includes('&quot; onfocus=&quot;bad'));
  const reference = {folder: 'INBOX', validity: '7', uid: '42'};
  for (const changed of [{...reference,folder:'Archive'}, {...reference,validity:'8'}, {...reference,uid:'43'}])
    assert.notEqual(mail.uidKey(reference), mail.uidKey(changed));
  assert.deepEqual(mail.threadIds('<one@example.invalid> bad <two@example.invalid>'), ['<one@example.invalid>','<two@example.invalid>']);
  assert.equal(mail.resolveSenderName({sender:'Олегови��',email:'office@example.invalid'}, ()=>[{email:'office@example.invalid',sender:'Полное имя'}]), 'Полное имя');
  assert.equal(mail.resolveSenderName({sender:'=?UTF-8?B?broken?=',email:'office@example.invalid'}), 'office@example.invalid');
  assert.ok(mail.avatarMarkup({email:'s.demin@spbu.ru'},'s.demin@spbu.ru').includes('>SD</span>'));
  assert.ok(mail.avatarMarkup({},'😀 Иванов').includes('>😀И</span>'));
  assert.equal(mail.folderLabel('Junk'), 'Спам');
  assert.equal(contacts.contactLetter({sender:'«Ёлкин'}), 'Е');
  assert.equal(contacts.contactLetter({sender:'😀'}), '#');
  assert.deepEqual(ruleParts(' ЗАЯВКА ; Ticket#\nзаявка'), ['заявка','ticket#','заявка']);

  const now = Date.parse('2099-03-31T08:00:00+03:00');
  const hint = inferMailDateHint({date:'2099-03-31T23:30:00-03:00',subject:'Защита завтра в 15-00'}, ()=>'', ()=>now);
  assert.deepEqual(hint, {date:'2099-04-02',time:'15:00',relative:true});
  assert.equal(inferMailDateHint({date:'2099-03-31',subject:'Сегодня или завтра в 15:00'}, ()=>'', ()=>now), null);
  assert.equal(inferMailDateHint({subject:'31.02.2099 15:00'}, ()=>'', ()=>now), null);
  assert.equal(inferMailDateHint({subject:'01.01.2000 15:00'}, ()=>'', ()=>now), null);
  assert.equal(dates.shiftCalendarDate('2100-02-28',1), '2100-03-01');
  assert.equal(dates.calendarTimeRange({kind:'task',time:'15:00'}), '15:00');
  assert.equal(dates.calendarTimeRange({date:'2099-04-02',time:'10:00',endTime:'17:00'}), '10:00–17:00');
  assert.equal(copyAge(now-20*60000,()=>now), '20 минут назад');
  assert.equal(copyAge(NaN,()=>now), 'время копии неизвестно');
  assert.equal(files.uniqueAttachments([{path:'1',name:'a.pdf'},{path:'1',name:'b.pdf'},null]).length,1);
  assert.equal(files.attachmentType({name:'Файл.DOCX'}).label, 'DOCX · текст в приложении');
  assert.equal(files.bytesText(1024), '1 КБ');

  // Source render modules are importable without document, window or Pilot.
  assert.equal(typeof renderMailRow, 'function'); assert.equal(typeof renderReaderView, 'function');
  const compose = renderComposeView({draft:{to:'a@example.invalid',subject:'<img>',body:'<script>bad</script>',attachments:[]},
    signatureBlock:()=>'',aiEnabled:()=>false,composeSaveNotice:'Сохранено',sendBusy:false,canScheduleSend:false});
  assert.ok(compose.includes('&lt;script&gt;bad&lt;/script&gt;'));
  assert.ok(!compose.includes('data-action="schedule-send"'));
  assert.ok(!compose.includes('data-action="ai-check"'));
  assert.ok(compose.includes('type="submit"'));

  const first = await bundleUi(), second = await bundleUi();
  assert.ok(Buffer.from(first).equals(Buffer.from(second)), 'bundle must be deterministic');
  assert.ok(Buffer.from(first).equals(fs.readFileSync(path.join(__dirname,'assets/app.js'))), 'tracked bundle must match sources');
  const dir=fs.mkdtempSync(path.join(os.tmpdir(),'pilot-ui-build-'));
  try {
    const target=path.join(dir,'app.js');fs.writeFileSync(target,'stale');
    await assert.rejects(buildUi(true,target), /stale/);assert.equal(fs.readFileSync(target,'utf8'),'stale');
    assert.equal(await buildUi(false,target),true);
    const time=fs.statSync(target).mtimeMs;assert.equal(await buildUi(false,target),false);
    assert.equal(fs.statSync(target).mtimeMs,time);
  } finally {fs.rmSync(dir,{recursive:true,force:true});}
  console.log('PASS: independently importable UI modules, Unicode/escaped markup, exact mail references, safe sender fallback, Moscow dates, scoped compose controls and deterministic/stale/idempotent offline bundle.');
})().catch(error=>{console.error(error);process.exitCode=1;});
