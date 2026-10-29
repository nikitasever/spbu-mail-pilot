import './styles.css';
import {Composer,type ReplyMail} from './compose';
import {Attachments,parseAttachments} from './attachments';
import {invoke} from '@tauri-apps/api/core';
import {Client,Lane,StaleReply,MailFault,failure,parsePage,folderLabel,type Mail,type Page} from './protocol';
const $=<T extends HTMLElement>(id:string)=>document.getElementById(id) as T;
const text=(id:string,value:string)=>{$(id).textContent=value};
const show=(id:string,visible:boolean)=>{$(id).hidden=!visible};
const client=new Client((command,args)=>invoke(command,args));
const composer=new Composer(client);let replyMail:ReplyMail|null=null;
const attachmentUI=new Attachments(client,$('transfers'));
const connectLane=new Lane(client),folderLane=new Lane(client),listLane=new Lane(client),bodyLane=new Lane(client);
let remembered:{host:string;port:number;user:string}|null=null;let canRemembered=false;
let connected=false,account='',folder='INBOX',rows:Mail[]=[],page:Page|null=null,selected:Mail|null=null,query='',epoch=0;
let listBusy=false,bodyBusy=false,listGeneration=0,bodyGeneration=0;
const form=$<HTMLFormElement>('login-form'),user=$<HTMLInputElement>('user'),password=$<HTMLInputElement>('password'),host=$<HTMLInputElement>('host'),port=$<HTMLInputElement>('port'),search=$<HTMLInputElement>('query');
const list=$('letters'),folders=$('folders');
function avatar(name:string){const words=name.trim().split(/[\s@._-]+/).filter(Boolean);return words.slice(0,2).map(x=>x[0]).join('').toUpperCase()||'П'}
function tone(name:string){let hash=0;for(const c of name)hash=(hash*31+c.charCodeAt(0))|0;return String(Math.abs(hash)%5)}
function span(className:string,value:string){const node=document.createElement('span');node.className=className;node.textContent=value;return node}
function date(value:string,full=false){const parsed=new Date(value);return Number.isNaN(parsed.getTime())?'':new Intl.DateTimeFormat('ru-RU',{timeZone:'Europe/Moscow',...(full?{dateStyle:'long',timeStyle:'short'}:{day:'numeric',month:'short'})}).format(parsed)}
function listState(){show('cancel-list',listBusy);show('more',Boolean(page?.hasMore)&&!listBusy);$<HTMLButtonElement>('refresh').disabled=listBusy;text('list-status',listBusy?'Загрузка…':'');text('list-count',page?`${rows.length} из ${page.total} писем`:listBusy?'Получаем письма…':'Писем пока нет')}
function resetReader(){replyMail=null;show('reply-mail',false);bodyGeneration++;bodyLane.cancel();selected=null;bodyBusy=false;show('message',false);show('reader-empty',true);$('workspace').classList.remove('reading');$('body').replaceChildren();$('sender').replaceChildren();$('attachment-list').replaceChildren();text('subject','');text('reader-folder','');show('body-error',false);renderRows()}
function renderRows(){
  list.replaceChildren();
  if(rows.length===0){const empty=document.createElement('p');empty.className='empty-list';empty.textContent=listBusy?'Получаем письма с сервера…':query?'По запросу писем не найдено.':'В этой папке пока нет писем.';list.append(empty);return}
  for(const row of rows){
    const button=document.createElement('button');button.type='button';button.className=`letter${row.seen?'':' unread'}`;button.setAttribute('aria-pressed',String(selected?.uid===row.uid&&selected.validity===row.validity));
    const image=span('avatar',avatar(row.sender||row.email));image.dataset.tone=tone(row.sender||row.email);const copy=span('letter-copy',''),top=span('letter-top','');
    top.append(span('sender-name',row.sender||row.email||'Неизвестный отправитель'));const time=document.createElement('time');time.textContent=date(row.date);time.dateTime=row.date;top.append(time);if(!row.seen)top.append(span('unread-dot',''));
    copy.append(top,span('letter-subject',`${row.hasAttachments?'📎 ':''}${row.subject||'Без темы'}`),span('letter-preview',row.preview));button.append(image,copy);button.addEventListener('click',()=>void openMail(row));const item=document.createElement('div');item.setAttribute('role','listitem');item.append(button);list.append(item);
  }
}
function renderFolders(names:string[]){
  folders.replaceChildren();for(const name of names){const button=document.createElement('button');button.type='button';button.append(span('folder-symbol','▱'),span('folder-label',folderLabel(name)));button.title=name;if(name===folder)button.setAttribute('aria-current','page');button.addEventListener('click',()=>void chooseFolder(name));folders.append(button)}
}
async function loadFolders(){
  const ticket=epoch;show('folders-status',true);text('folders-status','Получаем список с сервера…');$<HTMLButtonElement>('refresh-folders').disabled=true;
  try{const names=await folderLane.run<unknown>('folders');if(ticket!==epoch||!connected)return;if(!Array.isArray(names)||names.length>2000||names.some(n=>typeof n!=='string'||n.length>1024||/[\x00-\x1f\x7f]/.test(n)))throw new MailFault('BAD_RESPONSE');renderFolders([...new Set(['INBOX',...names])]);text('folders-status','')}
  catch(error){if(!(error instanceof StaleReply)&&ticket===epoch)text('folders-status',failure(error))}
  finally{if(ticket===epoch)$<HTMLButtonElement>('refresh-folders').disabled=false}
}
async function chooseFolder(name:string){if(name===folder&&!listBusy)return;folder=name;for(const button of folders.querySelectorAll('button')){if(button.title===folder)button.setAttribute('aria-current','page');else button.removeAttribute('aria-current')};text('folder-title',folderLabel(name));query='';search.value='';show('clear-search',false);await loadPage(false)}
async function loadPage(more:boolean){
  if(!connected||more&&(!page?.hasMore||listBusy))return;
  const run=++listGeneration;const ticket=epoch,currentFolder=folder,offset=more?page!.nextOffset:0,snapshot=more?page!.snapshot:'';
  listBusy=true;show('list-error',false);if(!more){resetReader();rows=[];page=null;renderRows()}listState();
  try{
    const raw=await listLane.run('page',{folder:currentFolder,offset,snapshot,query});if(ticket!==epoch||currentFolder!==folder||run!==listGeneration)return;
    const next=parsePage(raw,currentFolder,offset);const merged=more?[...rows,...next.messages]:next.messages;const seen=new Set<string>();rows=merged.filter(row=>{const key=`${row.validity}:${row.uid}`;if(seen.has(key))return false;seen.add(key);return true});page=next;renderRows();
  }catch(error){if(!(error instanceof StaleReply)&&ticket===epoch&&currentFolder===folder&&run===listGeneration){text('list-error',failure(error));show('list-error',true)}}
  finally{if(ticket===epoch&&currentFolder===folder&&run===listGeneration){listBusy=false;listState();renderRows()}}
}
async function openMail(row:Mail){
  if(!connected)return;replyMail=null;show('reply-mail',false);const run=++bodyGeneration;const ticket=epoch,currentFolder=folder;selected=row;bodyBusy=true;renderRows();show('reader-empty',false);show('message',true);$('workspace').classList.add('reading');text('reader-folder',folderLabel(folder));text('subject',row.subject||'Без темы');text('sender',[row.sender,row.email,date(row.date,true)].filter(Boolean).join(' · '));text('body','');show('body-error',false);show('attachments',false);show('cancel-body',true);text('reader-status','Получаем письмо…');
  try{
    const raw=await bodyLane.run<{text?:unknown;attachments?:unknown;message?:{replyTo?:unknown;messageId?:unknown;references?:unknown;subject?:unknown}}>('body',{folder:currentFolder,uid:row.uid,validity:row.validity});if(ticket!==epoch||folder!==currentFolder||selected!==row||run!==bodyGeneration)return;
    if(typeof raw?.text!=='string')throw new MailFault('BAD_RESPONSE');text('body',raw.text);
    const info=raw.message;const target=typeof info?.replyTo==='string'?info.replyTo:row.email;
    replyMail={replyTo:target,subject:row.subject,messageId:typeof info?.messageId==='string'?info.messageId:'',references:Array.isArray(info?.references)?info.references.filter((x):x is string=>typeof x==='string').slice(0,100):[]};show('reply-mail',Boolean(target));
    const attachments=parseAttachments(raw.attachments??[]);attachmentUI.render($('attachment-list'),attachments,{folder:currentFolder,uid:row.uid,validity:row.validity});show('attachments',attachments.length>0);text('reader-status','Статус прочтения не изменён');
  }catch(error){if(!(error instanceof StaleReply)&&ticket===epoch&&selected===row&&run===bodyGeneration){text('body-error',failure(error));show('body-error',true);text('reader-status','Не удалось загрузить письмо')}}
  finally{if(ticket===epoch&&selected===row&&run===bodyGeneration){bodyBusy=false;show('cancel-body',false)}}
}
function hidePassword(){password.type='password';text('toggle-password','Показать');$('toggle-password').setAttribute('aria-pressed','false')}
function loginBusy(busy:boolean){for(const input of [user,password,host,port,$<HTMLInputElement>('remember-account')])input.disabled=busy;$<HTMLButtonElement>('saved-login').disabled=busy||!canRemembered;$<HTMLButtonElement>('forget-account').disabled=busy;$<HTMLButtonElement>('connect').disabled=busy;show('cancel-connect',busy)}
form.addEventListener('submit',event=>{event.preventDefault();void connect()});
async function connect(saved=false){
  for(const input of [user,password,host,port])input.removeAttribute('aria-invalid');show('login-error',false);
  let invalid:HTMLInputElement|null=null;let message='';
  if(!saved&&(!user.value.trim()||/[\x00-\x1f\x7f]/.test(user.value))){invalid=user;message='Укажите логин почтового ящика.'}
  else if(!saved&&!password.value){invalid=password;message='Введите пароль почтового ящика.'}
  else if(!saved&&(!host.value.trim()||/[\s/:\x00-\x1f\x7f]/.test(host.value))){invalid=host;message='Укажите адрес IMAP-сервера без https:// и пути.'}
  else if(!saved&&(!/^\d{1,5}$/.test(port.value)||+port.value<1||+port.value>65535)){invalid=port;message='Порт должен быть числом от 1 до 65535.'}
  if(invalid){text('login-error',message);show('login-error',true);invalid.setAttribute('aria-invalid','true');if(invalid===host||invalid===port)form.querySelector('details')!.open=true;invalid.focus();return}
  if(saved&&(!remembered||!canRemembered))return;
  const ticket=++epoch;loginBusy(true);text('login-status','Подключаемся к почтовому серверу…');
  const args={host:saved?remembered!.host:host.value.trim(),port:saved?remembered!.port:Number(port.value),user:saved?remembered!.user:user.value.trim(),password:saved?'':password.value,remember:!saved&&$<HTMLInputElement>('remember-account').checked};password.value='';hidePassword();
  try{
    const result=await connectLane.run<{account?:string;host?:string;remembered?:boolean}>(saved?'connectSaved':'connect',saved?{}:args);args.password='';if(ticket!==epoch)return;connected=true;account=result.account??args.user;composer.setup(account,result.host??args.host);void composer.restore();text('account-note',result.remembered===false?'Не удалось запомнить аккаунт. Текущий вход работает; пароль потребуется после закрытия.':'');show('account-note',result.remembered===false);if(args.remember)void rememberedStatus();show('login-screen',false);show('workspace',true);text('account-name',account);text('account-avatar',avatar(account));folder='INBOX';query='';rows=[];page=null;renderFolders(['INBOX']);text('folder-title','Входящие');await loadFolders();if(ticket===epoch)await loadPage(false);
  }catch(error){if(!(error instanceof StaleReply)&&ticket===epoch){text('login-error',failure(error));show('login-error',true);password.focus()}}
  finally{args.password='';if(ticket===epoch){loginBusy(false);text('login-status','')}}
}
$('cancel-connect').addEventListener('click',()=>{epoch++;connectLane.cancel();loginBusy(false);text('login-status','Подключение остановлено.');password.value='';hidePassword()});
$('toggle-password').addEventListener('click',()=>{const reveal=password.type==='password';password.type=reveal?'text':'password';text('toggle-password',reveal?'Скрыть':'Показать');$('toggle-password').setAttribute('aria-pressed',String(reveal))});
$('refresh-folders').addEventListener('click',()=>void loadFolders());$('refresh').addEventListener('click',()=>void loadPage(false));$('more').addEventListener('click',()=>void loadPage(true));
$('cancel-list').addEventListener('click',()=>{listGeneration++;listLane.cancel();listBusy=false;listState();text('list-status','Загрузка остановлена. Обновите папку перед продолжением.');show('more',false)});
$('cancel-body').addEventListener('click',()=>{bodyGeneration++;bodyLane.cancel();bodyBusy=false;show('cancel-body',false);text('reader-status','Загрузка остановлена. Выберите письмо повторно.')});
$('close-reader').addEventListener('click',resetReader);
search.addEventListener('input',()=>show('clear-search',search.value.length>0));
$('search-form').addEventListener('submit',event=>{event.preventDefault();query=search.value.trim();void loadPage(false)});
$('clear-search').addEventListener('click',()=>{search.value='';query='';show('clear-search',false);search.focus();void loadPage(false)});
$('new-mail').addEventListener('click',()=>void composer.open());$('reply-mail').addEventListener('click',()=>{if(replyMail)void composer.open(replyMail)});
$('logout').addEventListener('click',()=>void composer.leave(()=>{void composer.reset();attachmentUI.reset();epoch++;connectLane.cancel();folderLane.cancel();listLane.cancel();bodyLane.cancel();connected=false;account='';rows=[];page=null;resetReader();folders.replaceChildren();text('account-name','');password.value='';hidePassword();search.value='';query='';show('clear-search',false);text('account-avatar','');text('folder-title','');show('workspace',false);show('login-screen',true);void client.start('disconnect').promise.catch(()=>{});user.focus()}));
async function rememberedStatus(){
  try{remembered=await invoke('account_status');canRemembered=Boolean(remembered);show('saved-account',canRemembered);if(remembered)text('saved-account-name',`${remembered.user} · ${remembered.host}`);$<HTMLButtonElement>('saved-login').disabled=!canRemembered}
  catch{remembered=null;canRemembered=false;show('saved-account',true);text('saved-account-name','Не удалось открыть сохранённый аккаунт. Можно войти вручную или забыть сохранённый пароль.');$<HTMLButtonElement>('saved-login').disabled=true}
}
$('saved-login').addEventListener('click',()=>void connect(true));$('forget-account').addEventListener('click',async()=>{try{await invoke('account_forget');remembered=null;canRemembered=false;show('saved-account',false);$<HTMLInputElement>('remember-account').checked=false;void rememberedStatus()}catch{text('login-error','Не удалось удалить сохранённый пароль. Повторите попытку.');show('login-error',true)}});
if('__TAURI_INTERNALS__' in window)void rememberedStatus();
if(!('__TAURI_INTERNALS__' in window)){text('login-error','Это предпросмотр интерфейса. Для подключения почты откройте установленное приложение Windows.');show('login-error',true);$<HTMLButtonElement>('connect').disabled=true}
