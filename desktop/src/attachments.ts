import {invoke} from '@tauri-apps/api/core';
import {listen} from '@tauri-apps/api/event';
import {Client,MailFault,failure} from './protocol';
export type Attachment={name:string;path:string;size:number};
export function parseAttachments(raw:unknown):Attachment[]{
  if(!Array.isArray(raw)||raw.length>500)throw new MailFault('BAD_RESPONSE');
  return raw.map(item=>{
    if(!item||typeof item.name!=='string'||item.name.length>500||typeof item.path!=='string'||item.path.length>160||! /^(root|(?:[0-9]{1,5}|m)(?:\.(?:[0-9]{1,5}|m)){0,12})$/.test(item.path))throw new MailFault('BAD_RESPONSE');
    return {name:item.name,path:item.path,size:Number.isSafeInteger(item.size)&&item.size>=0?item.size:-1};
  });
}
function bytes(value:number){return new Intl.NumberFormat('ru-RU',{maximumFractionDigits:1}).format(value/1048576)+' МБ'}
type Job={id:string;key:string;card:HTMLElement;status:HTMLElement;meter:HTMLProgressElement;stop:HTMLButtonElement;done:boolean;started:boolean;stopping:boolean};
export class Attachments {
  private jobs=new Map<string,Job>();private generation=0;private ready:Promise<unknown>;
  constructor(private client:Client,private panel:HTMLElement){
    this.ready='__TAURI_INTERNALS__' in window?listen<{id:string;bytes:number;total:number}>('attachment-progress',event=>{
      const value=event.payload,job=this.jobs.get(value.id);if(!job||job.done)return;
      if(!Number.isSafeInteger(value.bytes)||value.bytes<0||!Number.isSafeInteger(value.total))return;
      job.started=true;job.stop.hidden=false;
      if(job.stopping)return;
      if(value.total>0&&value.bytes<=value.total){job.meter.max=value.total;job.meter.value=value.bytes;job.status.textContent=`Получено ${bytes(value.bytes)} из ${bytes(value.total)}`}
      else {job.meter.removeAttribute('value');job.status.textContent=value.bytes?`Получено ${bytes(value.bytes)} · размер уточняется`:'Ожидаем загрузку файла…'}
    }):Promise.resolve();
    // Keep listener failures handled, but do not start an unobservable transfer.
    void this.ready.catch(()=>{});
  }
  reset(){this.generation++;for(const job of this.jobs.values())if(!job.done)this.client.cancel(job.id);this.jobs.clear();this.panel.replaceChildren();this.panel.hidden=true}
  render(list:HTMLElement,items:Attachment[],context:{folder:string;uid:string;validity:string}){
    list.replaceChildren();for(const item of items){
      const row=document.createElement('li'),name=document.createElement('span'),button=document.createElement('button');
      name.textContent=item.name;name.className='attachment-name';button.type='button';button.className='text-button';button.textContent='Сохранить…';button.setAttribute('aria-label',`Сохранить ${item.name}`);
      row.append(name,button);list.append(row);button.addEventListener('click',()=>void this.save(item,context));
    }
  }
  private async save(item:Attachment,context:{folder:string;uid:string;validity:string}){
    const key=JSON.stringify([context,item.path]);const old=[...this.jobs.values()].find(job=>job.key===key&&!job.done);
    if(old){old.card.scrollIntoView({block:'nearest'});old.stop.focus();return}
    const generation=this.generation;const card=document.createElement('div'),title=document.createElement('strong'),status=document.createElement('p'),meter=document.createElement('progress'),stop=document.createElement('button');
    card.className='transfer';title.textContent=item.name;status.className='small muted';status.setAttribute('role','status');status.textContent='Выберите место сохранения в окне Windows…';meter.setAttribute('aria-label',`Загрузка ${item.name}`);stop.type='button';stop.className='text-button';stop.textContent='Остановить';stop.hidden=true;
    card.append(title,status,meter,stop);this.panel.hidden=false;this.panel.append(card);
    // Reserve before awaiting event registration, preventing duplicate clicks.
    const reservation=`pending-${Date.now()}-${this.jobs.size}`;
    const job:Job={id:reservation,key,card,status,meter,stop,done:false,started:false,stopping:false};this.jobs.set(reservation,job);
    let task:ReturnType<Client['start']>|null=null;
    stop.addEventListener('click',()=>{if(task&&!job.done){job.stopping=true;stop.disabled=true;status.textContent='Останавливаем загрузку…';this.client.cancel(task.id)}});
    try{
      await this.ready;if(generation!==this.generation)return;
      task=this.client.start<{saved?:boolean;cancelled?:boolean;token?:string;openable?:boolean}>('attachment',{...context,path:item.path,name:item.name});
      this.jobs.delete(reservation);job.id=task.id;this.jobs.set(task.id,job);
      const result=await task.promise as {saved?:boolean;cancelled?:boolean;token?:string;openable?:boolean};
      if(generation!==this.generation)return;
      if(result?.cancelled){status.textContent='Сохранение отменено.'}
      else if(result?.saved&&typeof result.token==='string'){
        status.textContent='Сохранено в выбранную папку.';meter.max=1;meter.value=1;
        if(result.openable){const open=document.createElement('button');open.type='button';open.className='text-button';open.textContent='Открыть файл';open.addEventListener('click',async()=>{open.disabled=true;try{await invoke('open_saved',{token:result.token});status.textContent='Файл передан приложению Windows.'}catch{status.textContent='Не удалось открыть файл. Откройте его из выбранной папки.'}finally{open.disabled=false}});card.append(open)}
      }else throw new MailFault('BAD_RESPONSE');
    }catch(error){if(generation!==this.generation)return;status.textContent=failure(error);if(!job.stopping){const retry=document.createElement('button');retry.type='button';retry.className='text-button';retry.textContent='Повторить';retry.addEventListener('click',()=>{card.remove();this.jobs.delete(job.id);void this.save(item,context)});card.append(retry)}}
    finally{job.done=true;stop.hidden=true;if(meter.value!==1||meter.max!==1)meter.hidden=true;this.prune()}
  }
  private prune(){const completed=[...this.jobs.values()].filter(job=>job.done);for(const job of completed.slice(0,Math.max(0,completed.length-8))){job.card.remove();this.jobs.delete(job.id)}}
}
