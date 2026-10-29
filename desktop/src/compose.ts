import {invoke} from '@tauri-apps/api/core';
import {listen} from '@tauri-apps/api/event';
import {getCurrentWindow} from '@tauri-apps/api/window';
import {Client,MailFault,failure} from './protocol';
const $=<T extends HTMLElement>(id:string)=>document.getElementById(id) as T;
type FileEntry={token:string;name:string;size:number};
export type ReplyMail={replyTo:string;subject:string;messageId:string;references:string[]};
export class Composer {
  private id='';private dirty=false;private picking=false;private state:'editing'|'countdown'|'sending'|'sent'|'unknown'='editing';private timer:ReturnType<typeof setInterval>|null=null;private request='';private parent='';private references:string[]=[];private files:FileEntry[]=[];private generation=0;private creating=false;private closing=false;private confirmAction:(()=>void)|null=null;private account='';private host='';
  constructor(private client:Client){
    $('compose-form').addEventListener('submit',event=>{event.preventDefault();void this.startSend()});
    $('compose-form').addEventListener('input',()=>{this.dirty=true});
    $('compose-close').addEventListener('click',()=>void this.guard(()=>void this.discard()));
    $('compose-attach').addEventListener('click',()=>void this.attach());
    $('compose-undo').addEventListener('click',()=>this.undo());
    $('keep-draft').addEventListener('click',()=>{$<HTMLDialogElement>('discard-dialog').close();this.confirmAction=null});
    $('discard-draft').addEventListener('click',()=>{const action=this.confirmAction;this.confirmAction=null;$<HTMLDialogElement>('discard-dialog').close();action?.()});
    $('discard-dialog').addEventListener('cancel',()=>{this.confirmAction=null});
    for(const input of document.querySelectorAll<HTMLInputElement>('input[name="smtp-security"]'))input.addEventListener('change',()=>{$<HTMLInputElement>('smtp-port').value=input.value==='ssl'?'465':'587'});
    if('__TAURI_INTERNALS__' in window){
      void listen<{id:string;stage:string;bytes:number;total:number}>('send-progress',event=>{
        const p=event.payload;if(p.id!==this.request||this.state!=='sending')return;
        const stages:Record<string,string>={connecting:'Подключаемся к SMTP-серверу…',confirming:'Ожидаем подтверждение SMTP-сервера…',complete:'SMTP-сервер завершил передачу. Подтверждаем результат…',accepted:'SMTP-сервер принял письмо. Сохраняем копию в «Отправленных»…'};
        const meter=$<HTMLProgressElement>('send-meter');meter.hidden=false;
        if(p.stage==='uploading'&&p.total>0&&p.bytes>=0&&p.bytes<=p.total){meter.max=p.total;meter.value=p.bytes;this.status(`Передаём письмо: ${new Intl.NumberFormat('ru-RU').format(Math.floor(p.bytes*100/p.total))}%`)}
        else{meter.removeAttribute('value');this.status(stages[p.stage]??'Подготавливаем письмо…')}
      }).catch(()=>{});
      const current=getCurrentWindow();void current.onCloseRequested(event=>{
        if(this.dirty||this.picking||this.state==='countdown'||this.state==='sending'){
          event.preventDefault();this.ask(this.state==='sending'?'Письмо уже передаётся. Закрытие не отменяет отправку; её результат может остаться неизвестным.':'Черновик хранится только в памяти. Закрытие приложения удалит текст и выбранные вложения.',()=>{this.undo();this.closing=true;void current.destroy().catch(()=>{this.closing=false;this.error('Не удалось закрыть окно приложения.')})},'Закрыть приложение');
        }
      }).catch(()=>{});
    }
    window.addEventListener('beforeunload',event=>{if(!this.closing&&(this.dirty||this.picking||this.state==='countdown'||this.state==='sending')){event.preventDefault();event.returnValue=''}});
  }
  setup(account:string,host:string){this.account=account;this.host=host;const from=account.includes('@')?account:`${account}@${host.replace(/^mail\./i,'')}`;$<HTMLInputElement>('mail-from').value=from;$<HTMLInputElement>('smtp-host').value=host.toLowerCase()==='imap.mail.ru'?'smtp.mail.ru':host;$<HTMLInputElement>('smtp-port').value='465';$<HTMLInputElement>('smtp-ssl').checked=true}
  async open(reply?:ReplyMail){
    if(this.creating)return;
    if(this.id){if(!reply){$('composer').hidden=false;$('compose-body').focus();return}this.guard(()=>void this.replace(reply));return}
    await this.replace(reply);
  }
  private async replace(reply?:ReplyMail){
    if(this.id&&!await this.discard())return;this.creating=true;const generation=this.generation;
    try{const result=await invoke<{id:string}>('draft_begin');if(generation!==this.generation){void invoke('draft_discard',{id:result.id}).catch(()=>{});return}this.id=result.id;this.state='editing';this.files=[];this.parent=reply?.messageId??'';this.references=this.parent?(reply?.references??[]):[];
      for(const id of ['compose-to','compose-cc','compose-bcc','compose-subject','compose-body'])$<HTMLInputElement>(id).value='';
      $<HTMLInputElement>('compose-to').value=reply?.replyTo??'';$<HTMLInputElement>('compose-subject').value=reply?(/^(re|ответ):/i.test(reply.subject)?reply.subject:`Re: ${reply.subject}`).slice(0,500):'';
      $('compose-title').textContent=reply?'Ответить на письмо':'Новое письмо';this.dirty=Boolean(reply);$('composer').hidden=false;$('compose-error').hidden=true;$('send-meter').hidden=true;this.status('Черновик хранится только до закрытия приложения.');this.renderFiles();this.controls();$(reply?'compose-body':'compose-to').focus();
    }catch{this.status('Не удалось создать письмо. Попробуйте снова.');$('composer').hidden=false;this.controls()}
    finally{this.creating=false}
  }
  guard(action:()=>void){
    if(this.state==='sending'||this.picking){$('composer').hidden=false;this.error('Дождитесь завершения текущей операции.');return}
    if(this.dirty||this.state==='unknown'||this.state==='countdown'){this.ask(this.state==='unknown'?'Результат отправки неизвестен. Проверьте «Отправленные» или уточните у получателя. Повторная отправка может создать дубликат. Текст этого письма будет удалён.':'Текст и выбранные вложения не сохранены. Удалить этот черновик?',()=>{this.undo();action()},'Удалить черновик');return}action();
  }
  private ask(message:string,action:()=>void,label:string){this.confirmAction=action;$('discard-message').textContent=message;$('discard-draft').textContent=label;const dialog=$<HTMLDialogElement>('discard-dialog');if(!dialog.open)dialog.showModal();$('keep-draft').focus()}
  async reset(){this.generation++;this.undo();if(this.id)void invoke('draft_discard',{id:this.id}).catch(()=>{});this.id='';this.files=[];this.dirty=false;this.state='editing';this.request='';this.account='';this.host='';$('composer').hidden=true;for(const input of document.querySelectorAll<HTMLInputElement|HTMLTextAreaElement>('#compose-form input:not([type="radio"]),#compose-form textarea'))input.value='';this.renderFiles()}
  private async discard(){if(this.picking||this.state==='sending')return false;this.undo();if(this.id){try{await invoke('draft_discard',{id:this.id})}catch{this.error('Не удалось закрыть черновик. Попробуйте снова.');return false}}this.id='';this.dirty=false;this.files=[];this.state='editing';$('composer').hidden=true;for(const id of ['compose-to','compose-cc','compose-bcc','compose-subject','compose-body'])$<HTMLInputElement>(id).value='';this.renderFiles();return true}
  private controls(){const frozen=!this.id||this.state!=='editing'||this.picking;for(const input of document.querySelectorAll<HTMLInputElement|HTMLTextAreaElement>('#compose-form input,#compose-form textarea')){if(input.type==='radio')input.disabled=frozen;else input.readOnly=frozen}for(const button of document.querySelectorAll<HTMLButtonElement>('#compose-form button'))button.disabled=frozen;$('compose-undo').hidden=this.state!=='countdown';$<HTMLButtonElement>('compose-undo').disabled=false;this.renderFiles()}
  private status(message:string){$('compose-status').textContent=message}
  private error(message:string){$('compose-error').textContent=message;$('compose-error').hidden=false}
  private renderFiles(){const list=$('compose-files');list.replaceChildren();for(const file of this.files){const item=document.createElement('li'),name=document.createElement('span'),remove=document.createElement('button');name.textContent=file.name;remove.type='button';remove.className='text-button';remove.textContent='Убрать';remove.setAttribute('aria-label',`Убрать ${file.name}`);remove.disabled=this.state!=='editing'||this.picking;remove.addEventListener('click',async()=>{this.picking=true;this.controls();try{const value=await invoke<{files:FileEntry[]}>('draft_remove',{id:this.id,file:file.token});this.files=value.files;this.dirty=true}catch{this.error('Не удалось убрать вложение. Повторите.')}finally{this.picking=false;this.controls()}});item.append(name,remove);list.append(item)}}
  private async attach(){if(!this.id||this.state!=='editing'||this.picking)return;this.picking=true;this.controls();this.status('Выберите файлы в окне Windows. Подготавливаем локальные копии…');const generation=this.generation;try{const result=await invoke<{cancelled?:boolean;files:FileEntry[]}>('draft_attach',{id:this.id});if(generation!==this.generation)return;if(!result.cancelled){this.files=result.files;this.dirty=true}this.status('Файлы готовы. До 10 вложений, суммарно до 100 МБ.')}catch(error){this.error(typeof error==='string'&&error==='TOO_LARGE'?'Вложения суммарно превышают 100 МБ.':typeof error==='string'&&error==='TOO_MANY_FILES'?'Можно прикрепить не более 10 файлов.':'Не удалось подготовить файлы. Проверьте доступ и повторите выбор.')}finally{this.picking=false;this.controls()}}
  private validate(){for(const input of document.querySelectorAll('#compose-form [aria-invalid]'))input.removeAttribute('aria-invalid');let bad='';let message='';if(!$<HTMLInputElement>('compose-to').value.trim()){bad='compose-to';message='Укажите хотя бы одного получателя.'}else if(!/^\d{1,5}$/.test($<HTMLInputElement>('smtp-port').value)||+($<HTMLInputElement>('smtp-port').value)<1||+($<HTMLInputElement>('smtp-port').value)>65535){bad='smtp-port';message='SMTP-порт должен быть числом от 1 до 65535.'}else if(! $<HTMLInputElement>('smtp-host').value.trim()||/[\s/:\x00-\x1f]/.test($<HTMLInputElement>('smtp-host').value)){bad='smtp-host';message='Укажите SMTP-сервер без https:// и пути.'}else if(! $<HTMLInputElement>('mail-from').value.trim()){bad='mail-from';message='Укажите адрес отправителя.'}if(bad){this.error(message);$(bad).setAttribute('aria-invalid','true');if(bad.startsWith('smtp-')||bad==='mail-from')$<HTMLDetailsElement>('smtp-settings').open=true;$(bad).focus();return false}return true}
  private async startSend(){if(!this.id||this.state!=='editing'||this.picking||!this.validate())return;$('compose-error').hidden=true;this.state='countdown';this.controls();let seconds=10;const deadline=Date.now()+10000;this.status(`Отправка через ${seconds} с. Можно отменить.`);this.timer=setInterval(()=>{seconds=Math.max(0,Math.ceil((deadline-Date.now())/1000));if(seconds<=0){if(this.timer)clearInterval(this.timer);this.timer=null;void this.transmit()}else this.status(`Отправка через ${seconds} с. Можно отменить.`)},1000)}
  private undo(){if(this.state!=='countdown')return;if(this.timer)clearInterval(this.timer);this.timer=null;this.state='editing';this.status('Отправка отменена до подключения к SMTP. Черновик сохранён в памяти.');this.controls()}
  private async transmit(){if(this.state!=='countdown')return;this.state='sending';this.controls();this.status('Подготавливаем письмо к отправке…');$('send-meter').hidden=false;
    const args:Record<string,unknown>={draftId:this.id,inReplyTo:this.parent,references:this.references,smtpHost:$<HTMLInputElement>('smtp-host').value.trim(),smtpPort:Number($<HTMLInputElement>('smtp-port').value),smtpSsl:$<HTMLInputElement>('smtp-ssl').checked,from:$<HTMLInputElement>('mail-from').value.trim()};
    for(const [field,id] of Object.entries({to:'compose-to',cc:'compose-cc',bcc:'compose-bcc',subject:'compose-subject',body:'compose-body'}))args[field]=$<HTMLInputElement>(id).value;
    const task=this.client.start<{accepted:boolean;copySaved:boolean|null;replayed?:boolean}>('send',args);this.request=task.id;
    try{const result=await task.promise;if(!result?.accepted)throw new MailFault('SEND_UNKNOWN');this.state='sent';this.dirty=false;this.status(result.copySaved===true?'SMTP-сервер принял письмо. Копия сохранена в «Отправленных».':result.replayed?'Эта попытка уже принята SMTP-сервером. Повторной отправки не было; наличие копии в «Отправленных» не проверено.':'SMTP-сервер принял письмо, но копия в «Отправленных» не подтверждена. Не отправляйте письмо повторно.')}catch(error){const code=error instanceof MailFault?error.code:'';if(['SEND_UNKNOWN','SEND_LOCKED','ENGINE_UNAVAILABLE','BAD_RESPONSE'].includes(code)){this.state='unknown';this.error('Результат отправки неизвестен. Проверьте «Отправленные» или уточните у получателя. Повтор заблокирован, чтобы не отправить дубликат.')}else{this.state='editing';const fields:Record<string,string>={BAD_TO:'compose-to',BAD_CC:'compose-cc',BAD_BCC:'compose-bcc',BAD_FROM:'mail-from'};const invalid=fields[code];if(invalid){if(code==='BAD_CC'||code==='BAD_BCC')$<HTMLInputElement>('compose-cc').closest('details')!.open=true;if(code==='BAD_FROM')$<HTMLDetailsElement>('smtp-settings').open=true;$(invalid).setAttribute('aria-invalid','true');$(invalid).focus()}this.error(invalid?'Укажите корректные полные адреса. Несколько получателей разделяйте запятой или точкой с запятой.':code==='RECIPIENTS_LIMIT'?'Допускается не более 50 получателей суммарно.':code==='AUTH'?'SMTP-сервер отклонил вход. Проверьте параметры SMTP; используется пароль активного ящика.':code==='BAD_DRAFT'?'Проверьте адреса получателей, отправителя и параметры SMTP. Письмо не передано.':failure(error));this.status('Текст и вложения остались в черновике.')}}finally{this.request='';$('send-meter').hidden=true;this.controls()}
  }
}
