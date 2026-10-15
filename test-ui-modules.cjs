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

  const {renderThreadView} = await import('./ui/mail/thread.mjs');
  const {renderInboxView} = await import('./ui/mail/inbox.mjs');
  const {renderCalendarView} = await import('./ui/calendar/render.mjs');
  const {renderCalendarSearch} = await import('./ui/calendar/search.mjs');
  const {renderCalendarItem} = await import('./ui/calendar/item.mjs');
  const {parseHTML} = require('linkedom');
  const doc = markup => parseHTML('<html><body>'+markup+'</body></html>').document;
  const messages=[{subject:'<script>unread</script>',date:'2099-04-02',seen:false},
    {subject:'Read',date:'2099-04-01',seen:true}];
  const inboxContext={mailGroups:()=>messages.map(m=>({members:[m]})),folders:['Junk','INBOX'],mailFolder:'INBOX',
    localDraftFolder:()=>':local',local:{drafts:[],settings:{}},scheduledProtected:new Set(),messages,total:2,
    offlinePrefetchState:null,categoryFilter:'',mailCategory:()=>null,mailFilter:'unread',hasMailFiles:()=>false,
    today:'2099-04-02',mailRow:m=>'<article>'+html.esc(m.subject)+'</article>',categoryList:()=>[],
    hasMore:false,loading:false,mailDirty:false,aiEnabled:()=>false,greeting:()=>'',inboxSearchOpen:false,
    mailQuery:'',inboxSearchDraft:null,searchClear:()=>'',responseItems:()=>[],renderMailSelection:()=>'',
    renderResponses:()=>'',renderScheduled:()=>'',renderSnoozes:()=>'',renderReplyReminders:()=>'',
    mailListError:'',mailOffline:false};
  let inboxDoc=doc(renderInboxView(inboxContext));
  assert.equal(inboxDoc.querySelectorAll('article').length,1);
  assert.equal(inboxDoc.querySelector('article').textContent,messages[0].subject);
  assert.equal(inboxDoc.querySelector('#mail-folder option').value,'INBOX');
  inboxContext.mailFilter='all';inboxDoc=doc(renderInboxView(inboxContext));
  assert.equal(inboxDoc.querySelectorAll('article').length,2, 'each render reads the current filter');
  const thread=doc(renderThreadView({currentThread:()=>({members:messages}),mailRow:inboxContext.mailRow,hasMore:false,loading:false}));
  assert.equal(thread.querySelector('article').textContent,'Read');
  const events=[{id:'first',date:'2099-04-01',title:'Other day',kind:'event'},
    {id:'selected',date:'2099-04-02',title:'<img> selected',kind:'event'}];
  const calendarContext={calendarBounds:()=>({start:'2099-04-01',end:'2099-05-01'}),calendarView:'month',
    calendarMonth:'2099-04',calendarEntries:()=>events,onCalendarDay:(e,day)=>e.date===day,
    today:'2099-04-02',calendarDay:'2099-04-02',calendarItem:e=>'<article>'+html.esc(e.title)+'</article>',
    calendarLoading:false,calendarData:{calendars:[]},calendarQuery:'',calendarSearchContent:()=>'',dayPopup:false};
  let calDoc=doc(renderCalendarView(calendarContext));
  assert.equal(calDoc.querySelector('.calendar-selected-day article').textContent,events[1].title);
  assert.equal(calDoc.querySelector('[aria-pressed="true"][data-action="calendar-day"]').dataset.id,'2099-04-02');
  calendarContext.calendarDay='2099-04-01';calendarContext.dayPopup=true;
  calDoc=doc(renderCalendarView(calendarContext));
  assert.equal(calDoc.querySelector('#day-popup article').textContent,'Other day');
  assert.equal(calDoc.querySelector('.calendar-selected-day'),null);
  const search=doc(renderCalendarSearch({calendarQuery:'selected',calendarMatches:()=>[events[1]],
    calendarView:'month',calendarLoading:false,calendarBounds:calendarContext.calendarBounds}));
  assert.equal(search.querySelector('[data-action="calendar-day"]').dataset.id,'2099-04-02');
  const item=doc(renderCalendarItem(events[1],{calendarMyTask:()=>null,eventLinks:()=>[],mailCategory:()=>null,
    calendarSourceKey:()=>'',eventLinkKey:()=>''}));
  assert.equal(item.querySelector('h4').textContent,events[1].title);
  assert.equal(item.querySelector('[data-action="edit-event"]').dataset.id,'selected');

  const {renderContactsList} = await import('./ui/contacts/list.mjs');
  const {renderContactCardView} = await import('./ui/contacts/card.mjs');
  const {renderContactsView} = await import('./ui/contacts/render.mjs');
  const {renderTaskItem} = await import('./ui/tasks/item.mjs');
  const {renderTasksView} = await import('./ui/tasks/render.mjs');
  const {renderNotesView} = await import('./ui/notes/render.mjs');
  const person={sender:'<img> Имя',email:'test@example.invalid'};
  let people=[person];
  const contactContext={contactPeople:()=>people,contactsServer:()=>true,contactsBook:'company',contactsMore:true,
    contactsPrefix:'',contactsLetter:'',contactsNotice:'',contactsError:'',local:{contacts:[],phoneContacts:[]},
    bodyCache:{},avatar:()=>'',contactsLoading:false,contactsQuery:''};
  let contactDoc=doc(renderContactsList(contactContext));
  assert.ok(contactDoc.querySelector('[data-action="contact-letter"][data-id="Я"]'), 'server index is not limited to loaded names');
  assert.equal(contactDoc.querySelector('.directory-person strong').textContent,person.sender);
  assert.equal(contactDoc.querySelector('[data-action="compose-to"]').dataset.id,person.email);
  people=[];contactDoc=doc(renderContactsList(contactContext));
  assert.equal(contactDoc.querySelector('.contact-empty'),null, 'more pages do not claim an empty directory');
  assert.ok(contactDoc.querySelector('[data-action="contacts-more"]'));
  contactContext.contactsError='Ошибка <script>';contactDoc=doc(renderContactsList(contactContext));
  assert.equal(contactDoc.querySelector('[role="alert"]').textContent,'Ошибка <script>Повторить');
  assert.equal(contactDoc.querySelector('script'),null);
  const cardContext={contactCard:person,contactsBook:'company',contactNumber:()=>'+70000000000',
    local:{contacts:[]},avatar:()=>''};
  let cardDoc=doc(renderContactCardView(cardContext));
  assert.equal(cardDoc.querySelector('[data-action="dial"]').dataset.id,'+70000000000');
  assert.ok(cardDoc.querySelector('[data-action="save-contact"]'));
  cardContext.local.contacts.push(person);cardDoc=doc(renderContactCardView(cardContext));
  assert.ok(cardDoc.querySelector('[data-action="edit-contact"]'), 'saved status reflects current contact data');
  const contactsView=doc(renderContactsView({contactsBook:'history',contactsQuery:'"<query>',contactsContent:()=>''}));
  assert.equal(contactsView.querySelector('#contacts-query').value,'"<query>');
  assert.equal(contactsView.querySelector('option[selected]').value,'history');
  const tasks=[{id:'normal',title:'Ordinary',priority:'normal',date:'2099-04-02'},
    {id:'urgent',title:'<b> Urgent',notes:'Find Me',priority:'urgent',date:'2099-04-01',mail:reference},
    {id:'done',title:'Done',done:true,priority:'urgent'}];
  const tasksContext={taskQuery:'',taskFilter:'active',local:{tasks},searchClear:()=>'',
    taskRow:t=>renderTaskItem(t,{labelChip:()=>'',today:'2099-04-02',taskDate:()=>''})};
  let taskDoc=doc(renderTasksView(tasksContext));
  assert.equal(taskDoc.querySelector('.task-text h3').textContent,'<b> Urgent');
  assert.equal(taskDoc.querySelectorAll('.task-row').length,2);
  assert.ok(taskDoc.querySelector('[data-action="task-source"][data-id="urgent"]'));
  assert.ok(taskDoc.querySelector('.deadline').textContent.includes('Просрочено'));
  tasksContext.taskQuery='find me';taskDoc=doc(renderTasksView(tasksContext));
  assert.equal(taskDoc.querySelectorAll('.task-row').length,1, 'search includes task notes');
  tasksContext.taskQuery='';tasksContext.taskFilter='done';taskDoc=doc(renderTasksView(tasksContext));
  assert.equal(taskDoc.querySelector('.task-text h3').textContent,'Done');
  assert.deepEqual(tasks.map(t=>t.id),['normal','urgent','done'], 'rendering does not reorder stored tasks');
  const notesContext={local:{notes:[{id:'note',title:'<img>',body:'<script> literal'}]}};
  const notesDoc=doc(renderNotesView(notesContext));
  assert.equal(notesDoc.querySelector('h4').textContent,'<img>');
  assert.equal(notesDoc.querySelector('.list-item p').textContent,'<script> literal');
  assert.equal(notesDoc.querySelector('[data-action="edit-note"]').dataset.id,'note');

  const {renderDraftItem} = await import('./ui/drafts/item.mjs');
  const {renderDraftsView} = await import('./ui/drafts/render.mjs');
  const {renderCategoriesView} = await import('./ui/mail/categories.mjs');
  const {renderCategoryDialog} = await import('./ui/mail/category-dialog.mjs');
  const {renderScheduledList} = await import('./ui/mail/scheduled-list.mjs');
  const {renderScheduledPreviewDialog} = await import('./ui/mail/scheduled-preview.mjs');
  const {renderScheduleDialog} = await import('./ui/mail/schedule-dialog.mjs');
  const {renderSnoozeDialog} = await import('./ui/mail/snooze-dialog.mjs');
  const draftDeletions=new Map(), savedDrafts=[{id:'old',subject:'<b> old',updatedAt:1},
    {id:'new',subject:'New',updatedAt:2},{id:'protected',subject:'Scheduled',updatedAt:3}];
  const draftsContext={local:{drafts:savedDrafts},scheduledProtected:new Set(['protected']),
    draftRow:d=>renderDraftItem(d,{draftDeletions})};
  let draftDoc=doc(renderDraftsView(draftsContext));
  assert.equal(draftDoc.querySelectorAll('.draft-row').length,2);
  assert.equal(draftDoc.querySelector('[data-action="edit-draft"]').dataset.id,'new');
  assert.deepEqual(savedDrafts.map(d=>d.id),['old','new','protected']);
  draftDeletions.set('new',{busy:true});draftDoc=doc(renderDraftsView(draftsContext));
  assert.ok(draftDoc.querySelector('[data-action="edit-draft"][data-id="new"]').disabled);
  assert.ok(draftDoc.querySelector('[data-action="delete-draft"][data-id="new"]').disabled);
  const category={id:'rule',color:'blue',name:'<script> Rule',subjects:'<topic>',enabled:false,notify:false};
  const catDoc=doc(renderCategoriesView({account:'a@example.invalid',categoryList:()=>[category],categoryRuleSummary:()=>''}));
  assert.equal(catDoc.querySelector('h4').textContent,category.name);
  const catDialog=doc(renderCategoryDialog({categoryEdit:category}));
  assert.equal(catDialog.querySelector('[name="category-subjects"]').value,'<topic>');
  assert.equal(catDialog.querySelector('[name="category-enabled"]').hasAttribute('checked'),false);
  let nativeCalls=0;
  const inertPilot={scheduledMail:()=>{nativeCalls++;},scheduledSendDetails:()=>{nativeCalls++;}};
  const scheduleContext={Pilot:inertPilot,scheduledItems:[],scheduledLoading:false,scheduledError:'',
    scheduledExpanded:false,account:'a@example.invalid',scheduledLabels:{uncertain:'Результат неизвестен'},snoozeDate:()=>''};
  assert.equal(renderScheduledList(scheduleContext),'');
  scheduleContext.scheduledItems=[{id:'uncertain',state:'uncertain',subject:'<script>',to:'a@example.invalid',at:1}];
  const queue=doc(renderScheduledList(scheduleContext));
  assert.ok(queue.querySelector('[role="alert"]').textContent.includes('Автоматического повтора не будет'));
  assert.equal(queue.querySelector('[data-action="cancel-scheduled"]').dataset.id,'uncertain');
  assert.equal(nativeCalls,0, 'rendering does not fetch or send scheduled mail');
  const preview=doc(renderScheduledPreviewDialog({scheduledPreview:{draft:{subject:'<img>',body:'<script> literal',attachments:[]},from:'a@example.invalid'}}));
  assert.equal(preview.querySelector('.body-text').textContent,'<script> literal');
  assert.equal(preview.querySelector('[data-action="confirm-schedule"]'),null);
  const schedule=doc(renderScheduleDialog({schedulePopup:{busy:true,value:'2099-04-02T15:00'},draft:{subject:'<b>',to:'a@example.invalid'},account:'a@example.invalid'}));
  assert.equal(schedule.querySelector('dialog').getAttribute('aria-busy'),'true');
  assert.equal(schedule.querySelector('[name="schedule-at"]').value,'2099-04-02T15:00');
  const snoozeContext={snoozePopup:{personal:true,subject:'<img>',value:'2099-04-02T15:00'},Pilot:{}};
  let snooze=doc(renderSnoozeDialog(snoozeContext));
  assert.equal(snooze.querySelector('#quick-title').textContent,'Мне нужно ответить');
  assert.equal(snooze.querySelector('[data-action="notification-settings"]'),null);
  snoozeContext.Pilot={notificationsAllowed:()=>false,notificationSettings:()=>{nativeCalls++;}};
  snooze=doc(renderSnoozeDialog(snoozeContext));
  assert.ok(snooze.querySelector('[data-action="notification-settings"]'));
  assert.equal(nativeCalls,0, 'rendering exposes notification settings without opening Android');

  const {renderSearchResults} = await import('./ui/search/results.mjs');
  const {renderSearchView} = await import('./ui/search/render.mjs');
  const {renderSettingsView} = await import('./ui/settings/render.mjs');
  const {renderProfileView} = await import('./ui/settings/profile.mjs');
  const {renderFilesView} = await import('./ui/files/render.mjs');
  const searchState={criteria:{query:'"<pending>',from:'',start:'',end:'',folder:'INBOX',attachments:false},
    committed:null,mail:[],events:[],people:[]};
  const searchContext={smart:searchState,smartMonths:()=>({start:'2099-04-01',end:'2099-04-30'}),contactsServer:()=>false};
  assert.ok(renderSearchResults(searchContext).includes('Введите слово'));
  const searchForm=doc(renderSearchView({smart:searchState,folders:['INBOX'],searchClear:()=>'',
    smartResults:()=>renderSearchResults(searchContext)}));
  assert.equal(searchForm.querySelector('#smart-query').value,'"<pending>');
  searchState.committed={query:'confirmed',folder:'Junk'};searchState.more=true;
  let searchDoc=doc(renderSearchResults(searchContext));
  assert.ok(searchDoc.querySelector('.smart-section .note').textContent.includes('Спам'));
  assert.ok(searchDoc.body.textContent.includes('В проверенной порции совпадений нет'));
  assert.ok(searchDoc.querySelector('[data-action="smart-more-mail"]'));
  searchState.mailError='Ошибка <script>';searchDoc=doc(renderSearchResults(searchContext));
  assert.equal(searchDoc.querySelector('[role="alert"]').textContent,'Ошибка <script>');
  assert.ok(searchDoc.querySelector('[data-action="smart-retry-mail"]'));
  assert.equal(searchDoc.querySelector('script'),null);
  searchState.mailError='';searchState.mail=[{...reference,subject:'<b> Mail'}];
  searchDoc=doc(renderSearchResults(searchContext));
  assert.equal(searchDoc.querySelector('[data-action="smart-open-mail"]').dataset.id,mail.uidKey(reference));
  let settingsWrites=0;
  const settingsContext={account:'<account>',local:{settings:{theme:'dark',startScreen:'calendar',offlineMail:false}},
    Pilot:{appLockSettings:()=>{settingsWrites++;},launcherIcon:()=>'classic',backgroundMailStatus:()=>'<script> status'},
    tasksEnabled:()=>false,threadsEnabled:()=>true,undoSendEnabled:()=>true,aiEnabled:()=>false};
  let settingsDoc=doc(renderSettingsView(settingsContext));
  assert.equal(settingsDoc.querySelector('[data-theme] option[selected]').value,'dark');
  assert.equal(settingsDoc.querySelector('[data-start-screen] option[selected]').value,'calendar');
  assert.equal(settingsDoc.querySelector('[data-setting="offlineMail"]').hasAttribute('checked'),false);
  assert.equal(settingsDoc.querySelector('[data-setting="neuralAnalysis"]').hasAttribute('checked'),false);
  assert.equal(settingsDoc.querySelector('[data-action="choose-icon"][aria-pressed="true"]').dataset.id,'classic');
  assert.equal(settingsWrites,0, 'showing settings does not open native dialogs');
  assert.equal(settingsDoc.querySelector('script'),null);
  settingsContext.local.settings.theme='light';settingsDoc=doc(renderSettingsView(settingsContext));
  assert.equal(settingsDoc.querySelector('[data-theme] option[selected]').value,'light');
  const profile=doc(renderProfileView({account:'<account>'}));
  assert.equal(profile.querySelector('h3').textContent,'<account>');
  assert.ok(profile.querySelector('[data-action="switch-account"]'));
  let fileRequests=0;
  const filesContext={Pilot:{downloadAttachments:()=>{fileRequests++;}},fileEntries:[],
    bodyCache:{[mail.uidKey(reference)]:{attachments:[{path:'1',name:'Report.PDF'}]}},fileQuery:'pdf',
    searchClear:()=>'',folders:['INBOX'],fileFolder:'INBOX',fileOffset:1,fileTotal:1,fileLoading:false,
    local:{fileLabels:{}},labelChip:()=>'',attachmentButton:()=>'',downloadTransferMarkup:()=>'',
    fileDownloadButton:()=>'',labelOptions:()=>'',fileMore:false,fileListError:''};
  const fileDoc=doc(renderFilesView(filesContext));
  assert.equal(fileDoc.querySelector('h4').textContent,'Report.PDF');
  assert.equal(fileDoc.querySelector('[data-action="file-source"]').dataset.id,mail.uidKey(reference));
  assert.equal(fileRequests,0, 'rendering cached file metadata does not download files');
  filesContext.fileQuery='absent';filesContext.fileListError='offline';
  const fileError=doc(renderFilesView(filesContext));
  assert.ok(fileError.querySelector('[data-action="retry-files"]'));

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
  console.log('PASS: independently importable UI modules, Unicode/escaped markup, exact mail references, safe sender fallback, Moscow dates, live view contexts, scoped compose controls and deterministic/stale/idempotent offline bundle.');
})().catch(error=>{console.error(error);process.exitCode=1;});
