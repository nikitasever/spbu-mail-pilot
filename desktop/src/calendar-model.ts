import {MailFault} from './protocol.ts';
export type CalendarEvent={id:string;title:string;date:string;time:string;endDate:string;endTime:string;allDay:boolean;location:string;description:string};
export type CalendarProfile={profileId:string;server:string;user:string;selected:string;calendars:{id:string;title:string}[]};
export type CalendarView='day'|'week'|'month'|'agenda';
const DAY=86400000;
export function ordinal(date:string):number{if(!/^\d{4}-\d{2}-\d{2}$/.test(date))throw new MailFault('CALENDAR_DATA');const value=Date.parse(date+'T00:00:00Z');if(!Number.isFinite(value)||new Date(value).toISOString().slice(0,10)!==date)throw new MailFault('CALENDAR_DATA');return value}
export function addDays(date:string,n:number){return new Date(ordinal(date)+n*DAY).toISOString().slice(0,10)}
export function addMonths(date:string,n:number){const current=new Date(ordinal(date)),day=current.getUTCDate();current.setUTCDate(1);current.setUTCMonth(current.getUTCMonth()+n);const last=new Date(Date.UTC(current.getUTCFullYear(),current.getUTCMonth()+1,0)).getUTCDate();current.setUTCDate(Math.min(day,last));return current.toISOString().slice(0,10)}
export function monday(date:string){return addDays(date,-((new Date(ordinal(date)).getUTCDay()+6)%7))}
export function todayMoscow(now=new Date()){const parts=new Intl.DateTimeFormat('en-CA',{timeZone:'Europe/Moscow',year:'numeric',month:'2-digit',day:'2-digit'}).formatToParts(now);const value=(type:string)=>parts.find(p=>p.type===type)!.value;return `${value('year')}-${value('month')}-${value('day')}`}
export function dateLabel(date:string,full=false){return new Intl.DateTimeFormat('ru-RU',{timeZone:'UTC',day:'numeric',month:'long',...(full?{weekday:'long' as const,year:'numeric' as const}:{})}).format(new Date(ordinal(date)))}
export function monthLabel(date:string){const label=new Intl.DateTimeFormat('ru-RU',{timeZone:'UTC',month:'long',year:'numeric'}).format(new Date(ordinal(date)));return label[0].toUpperCase()+label.slice(1)}
export function requiredMonths(date:string,view:CalendarView){const result=new Set<string>();const first=view==='week'?monday(date):date;const last=view==='week'?addDays(first,6):date;result.add(first.slice(0,7));result.add(last.slice(0,7));return [...result].sort()}
function timePart(value:string){if(!/^(?:[01]\d|2[0-3]):[0-5]\d$/.test(value))throw new MailFault('CALENDAR_DATA');return (Number(value.slice(0,2))*60+Number(value.slice(3)))*60000}
function bounds(event:CalendarEvent){return [ordinal(event.date)+(event.allDay?0:timePart(event.time)),ordinal(event.endDate)+(event.allDay?0:timePart(event.endTime))]}
export function eventsForDay(events:CalendarEvent[],date:string){const start=ordinal(date),end=start+DAY;return events.filter(event=>{const[a,b]=bounds(event);return a===b?a>=start&&a<end:a<end&&b>start}).sort((a,b)=>Number(b.allDay)-Number(a.allDay)||a.date.localeCompare(b.date)||a.time.localeCompare(b.time)||a.title.localeCompare(b.title,'ru'))}
export function parseEvents(raw:unknown):CalendarEvent[]{
 if(!Array.isArray(raw)||raw.length>5000)throw new MailFault('CALENDAR_DATA');
 const text=(v:unknown,max:number)=>typeof v==='string'?v.slice(0,max):'';
 return raw.map(row=>{
  if(!row||typeof row.id!=='string'||row.id.length>4096||typeof row.allDay!=='boolean'||typeof row.date!=='string'||typeof row.endDate!=='string')throw new MailFault('CALENDAR_DATA');
  const event:CalendarEvent={id:row.id,title:text(row.title,2000)||'Без названия',date:row.date,time:text(row.time,5),endDate:row.endDate,endTime:text(row.endTime,5),allDay:row.allDay,location:text(row.location,4000),description:text(row.description,30000)};const[a,b]=bounds(event);if(b<a||event.allDay&&b===a)throw new MailFault('CALENDAR_DATA');return event;
 });
}
export function parseProfile(raw:unknown):CalendarProfile|null{
 if(raw===null)return null;if(!raw||typeof raw!=='object')throw new MailFault('CALENDAR_DATA');const value=raw as CalendarProfile;
 if(typeof value.profileId!=='string'||value.profileId.length>100||typeof value.server!=='string'||typeof value.user!=='string'||!Array.isArray(value.calendars)||!value.calendars.length||value.calendars.length>100)throw new MailFault('CALENDAR_DATA');
 const ids=new Set<string>();for(const row of value.calendars){if(typeof row?.id!=='string'||!row.id||row.id.length>128||ids.has(row.id)||typeof row.title!=='string'||row.title.length>2048)throw new MailFault('CALENDAR_DATA');ids.add(row.id)}
 return {profileId:value.profileId,server:value.server,user:value.user,calendars:value.calendars.map(({id,title})=>({id,title})),selected:ids.has(value.selected)?value.selected:value.calendars[0].id};
}
export function mergeEvents(groups:CalendarEvent[][]){const values=new Map<string,CalendarEvent>();for(const group of groups)for(const event of group)values.set(event.id,event);return [...values.values()]}
