export type Invoke = (command:string,args:{request:Request})=>Promise<unknown>;
export type Request = {id:string;method:string;args:Record<string,unknown>};
export class MailFault extends Error {code:string;constructor(code:string){super(code);this.code=code}}
export class StaleReply extends Error {}
export class Client {
  private serial=0;
  private invoke:Invoke;
  constructor(invoke:Invoke){this.invoke=invoke}
  start<T>(method:string,args:Record<string,unknown>={}){
    const id=`r${++this.serial}`;
    const promise=this.invoke('mail_request',{request:{id,method,args}}).then(raw=>{
      if(!raw||typeof raw!=='object')throw new MailFault('BAD_RESPONSE');
      const reply=raw as {id?:unknown;result?:unknown;error?:{code?:unknown}};
      if(reply.id!==id)throw new MailFault('BAD_RESPONSE');
      if(reply.error)throw new MailFault(typeof reply.error.code==='string'?reply.error.code:'UNAVAILABLE');
      return reply.result as T;
    },error=>{throw new MailFault(typeof error==='string'&&['BAD_REQUEST','BUSY','ENGINE_UNAVAILABLE'].includes(error)?error:'ENGINE_UNAVAILABLE')});
    return {id,promise};
  }
  cancel(id:string){void this.start('cancel',{requestId:id}).promise.catch(()=>{})}
}
export class Lane {
  private generation=0;
  private id:string|null=null;
  private client:Client;
  constructor(client:Client){this.client=client}
  cancel(){this.generation++;if(this.id)this.client.cancel(this.id);this.id=null}
  async run<T>(method:string,args:Record<string,unknown>={}):Promise<T>{
    this.cancel();const ticket=this.generation;const task=this.client.start<T>(method,args);this.id=task.id;
    try{const value=await task.promise;if(ticket!==this.generation)throw new StaleReply();return value}
    catch(error){if(ticket!==this.generation)throw new StaleReply();throw error}
    finally{if(ticket===this.generation)this.id=null}
  }
}
export function failure(error:unknown):string {
  const code=error instanceof MailFault?error.code:'';
  const messages:Record<string,string>={AUTH:'Сервер отклонил вход. Проверьте логин и пароль.',NETWORK:'Не удалось связаться с сервером. Проверьте сеть и настройки подключения.',NO_SESSION:'Подключитесь к ящику заново.',STALE_MAIL:'Письмо изменилось или удалено. Обновите список.',CANCELLED:'Операция остановлена.',BAD_REQUEST:'Проверьте введённые данные.',BUSY:'Дождитесь завершения текущего запроса.',ENGINE_UNAVAILABLE:'Почтовый движок недоступен. Закройте и откройте приложение.',RESPONSE_TOO_LARGE:'Ответ сервера слишком большой для отображения.',BAD_RESPONSE:'Не удалось прочитать ответ. Обновите список.'};
  return messages[code]??'Не удалось выполнить запрос. Проверьте подключение и повторите.';
}
export type Mail={uid:string;validity:string;sender:string;email:string;subject:string;preview:string;date:string;seen:boolean;hasAttachments:boolean};
export type Page={folder:string;messages:Mail[];snapshot:string;nextOffset:number;total:number;hasMore:boolean};
export function parsePage(value:unknown,folder:string,offset:number):Page {
  if(!value||typeof value!=='object')throw new MailFault('BAD_RESPONSE');const page=value as Page;
  if(page.folder!==folder||!Array.isArray(page.messages)||page.messages.length>50||typeof page.snapshot!=='string'||page.snapshot.length>4096||!Number.isSafeInteger(page.nextOffset)||page.nextOffset<offset||!Number.isSafeInteger(page.total)||page.total<0||typeof page.hasMore!=='boolean'||page.hasMore&&page.nextOffset<=offset)throw new MailFault('BAD_RESPONSE');
  const messages=page.messages.map(row=>{
    if(!row||!/^[1-9][0-9]{0,19}$/.test(String(row.uid))||!/^[1-9][0-9]{0,19}$/.test(String(row.validity)))throw new MailFault('BAD_RESPONSE');
    const text=(value:unknown,max:number)=>typeof value==='string'?value.slice(0,max):'';
    return {uid:String(row.uid),validity:String(row.validity),sender:text(row.sender,500),email:text(row.email,320),subject:text(row.subject,2000),preview:text(row.preview,500),date:text(row.date,100),seen:row.seen===true,hasAttachments:row.hasAttachments===true};
  });
  return {...page,messages};
}
export function folderLabel(name:string):string {
  const labels:Record<string,string>={inbox:'Входящие',sent:'Отправленные','sent items':'Отправленные','sent messages':'Отправленные',drafts:'Черновики на сервере',trash:'Корзина','deleted items':'Корзина',junk:'Спам',spam:'Спам',archive:'Архив',archives:'Архив'};
  return labels[name.toLowerCase()]??name;
}
