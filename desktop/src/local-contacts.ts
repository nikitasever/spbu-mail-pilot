import {Client,Lane,MailFault,StaleReply,failure} from './protocol';
import {confirmAction,cancelConfirmation,confirmationOpen} from './confirmation';
import {parseLocalBook,type LocalBook,type LocalPerson,validContact} from './contacts-model';
const $=<T extends HTMLElement>(id:string)=>document.getElementById(id) as T;
export class LocalContacts{
 private trigger:HTMLElement|null=null;private serial=0;private lane:Lane;private epoch=0;private generation=0;private book:LocalBook|null=null;private id='';private initial='';private pending=false;private frozen=false;private composing=false;
 constructor(private client:Client,private changed:(action:'saved'|'deleted')=>void,private status:(text:string)=>void,private redraw:()=>void){
  this.lane=new Lane(client);$('contact-editor-form').addEventListener('submit',event=>{event.preventDefault();if(!this.composing)void this.save()});$('contact-editor-form').addEventListener('compositionstart',()=>this.composing=true);$('contact-editor-form').addEventListener('compositionend',()=>this.composing=false);
  $('contact-cancel').addEventListener('click',()=>void this.mayClose());$('contact-editor').addEventListener('cancel',event=>{event.preventDefault();void this.mayClose()});
  $('contact-editor').addEventListener('close',()=>{if(this.trigger?.isConnected)this.trigger.focus();else $('contacts-new').focus();this.trigger=null});
 }
 get busy(){return this.pending}
 get people(){return this.book?.people??[]}
 get ready(){return this.book!==null}
 setup(epoch:number){this.reset();this.epoch=epoch}
 reset(){this.generation++;this.serial++;this.lane.cancel();this.book=null;this.pending=false;this.frozen=false;this.composing=false;cancelConfirmation();this.closeEditor()}
 cancelRead(){this.lane.cancel()}
 async read(){const epoch=this.epoch,generation=this.generation,serial=this.serial;const value=parseLocalBook(await this.lane.run('localContactsRead',{epoch}));if(epoch!==this.epoch||generation!==this.generation||serial!==this.serial)throw new StaleReply();this.book=value;return value}
 needsCloseGuard(){return this.pending||$<HTMLDialogElement>('contact-editor').open||confirmationOpen()}
 async mayClose(){
  if(this.pending||confirmationOpen())return false;const editor=$<HTMLDialogElement>('contact-editor');if(!editor.open)return true;
  const generation=this.generation;if(this.fields()!==this.initial&&!await confirmAction('Не сохранять контакт?','Изменения этой формы будут потеряны. Уже сохранённая адресная книга останется на компьютере.','Не сохранять'))return false;
  if(generation!==this.generation)return false;this.closeEditor();return true;
 }
 edit(person?:LocalPerson){
  if(!this.book||this.pending)return;this.trigger=document.activeElement instanceof HTMLElement?document.activeElement:null;this.id=person?.id??'';$<HTMLInputElement>('contact-name').value=person?.sender??'';$<HTMLInputElement>('contact-email').value=person?.email??'';this.initial=this.fields();this.frozen=false;this.composing=false;
  $('contact-editor-title').textContent=person?'Изменить контакт':'Новый контакт';$('contact-error').hidden=true;for(const id of ['contact-name','contact-email'])$(id).removeAttribute('aria-invalid');this.controls();$<HTMLDialogElement>('contact-editor').showModal();$('contact-name').focus();
 }
 private fields(){return JSON.stringify([$<HTMLInputElement>('contact-name').value,$<HTMLInputElement>('contact-email').value])}
 private closeEditor(){$<HTMLDialogElement>('contact-editor').close();for(const id of ['contact-name','contact-email'])$<HTMLInputElement>(id).value='';this.id='';this.initial='';$('contact-error').hidden=true}
 private controls(){for(const input of document.querySelectorAll<HTMLInputElement|HTMLButtonElement>('#contact-editor-form input,#contact-editor-form button'))input.disabled=this.pending;$<HTMLButtonElement>('contact-save').disabled=this.pending||this.frozen;$('contact-editor-form').setAttribute('aria-busy',String(this.pending));this.redraw()}
 private error(text:string,field?:string){$('contact-error').textContent=text;$('contact-error').hidden=false;if(field){$(field).setAttribute('aria-invalid','true');$(field).focus()}}
 private async save(){
  if(!this.book||this.pending||this.frozen)return;const sender=$<HTMLInputElement>('contact-name').value.trim(),email=$<HTMLInputElement>('contact-email').value.trim();
  for(const id of ['contact-name','contact-email'])$(id).removeAttribute('aria-invalid');if(!sender||sender.length>180||/[\x00-\x1f\x7f]/.test(sender)){this.error('Укажите имя длиной до 180 символов без управляющих символов.','contact-name');return}if(!validContact(sender,email)){this.error('Укажите один полный адрес без пробелов, запятых и переносов строк.','contact-email');return}
  const epoch=this.epoch,generation=this.generation;this.pending=true;this.serial++;this.lane.cancel();this.controls();
  try{const value=await this.client.start('localContactsSave',{epoch,revision:this.book.revision,id:this.id,sender,email}).promise;if(epoch!==this.epoch||generation!==this.generation)return;this.book=parseLocalBook(value);this.pending=false;this.closeEditor();this.changed('saved');this.status('Контакт сохранён на этом компьютере.')}catch(error){if(epoch!==this.epoch||generation!==this.generation)return;this.pending=false;this.controls();const conflict=error instanceof MailFault&&error.code==='CONTACTS_CONFLICT';this.frozen=conflict;this.error((error instanceof MailFault&&error.code==='CONTACTS_LIMIT'?'В локальной книге достигнут предел: 5000 контактов. Удалите ненужные записи перед добавлением новых.':failure(error))+(conflict?' Правки остались в форме для копирования. Закройте её, перечитайте книгу и откройте контакт заново.':''),error instanceof MailFault&&error.code==='CONTACTS_EXISTS'?'contact-email':undefined)}finally{if(epoch===this.epoch&&generation===this.generation){this.pending=false;this.controls()}}
 }
 async remove(person:LocalPerson){
  if(!this.book||this.pending)return;const epoch=this.epoch,generation=this.generation,revision=this.book.revision;
  if(!await confirmAction('Удалить контакт?',`${person.sender} (${person.email}) будет удалён из локальной книги этого ящика. Серверные контакты не изменятся.`,'Удалить контакт'))return;
  if(epoch!==this.epoch||generation!==this.generation)return;this.pending=true;this.serial++;this.lane.cancel();this.redraw();this.status('Удаляем контакт…');let removed=false;
  try{const value=await this.client.start('localContactsDelete',{epoch,revision,id:person.id}).promise;if(epoch!==this.epoch||generation!==this.generation)return;this.book=parseLocalBook(value);this.changed('deleted');removed=true;this.status('Контакт удалён из локальной книги.')}catch(error){if(epoch===this.epoch&&generation===this.generation)this.status(failure(error))}finally{if(epoch===this.epoch&&generation===this.generation){this.pending=false;this.redraw();if(removed)$('contacts-new').focus()}}
 }
}
