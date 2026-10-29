import {MailFault} from './protocol.ts';
export type Person={email:string;sender:string};
export type ContactPage={people:Person[];cookie:string;hasMore:boolean;notice:string};
export const ALPHABET='АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯABCDEFGHIJKLMNOPQRSTUVWXYZ#';
export function parseContacts(raw:unknown,previous:string,seen:Set<string>):ContactPage{
 if(!raw||typeof raw!=='object')throw new MailFault('BAD_RESPONSE');const value=raw as ContactPage;
 if(!Array.isArray(value.people)||value.people.length>50||typeof value.cookie!=='string'||value.cookie.length>4096||/[\x00-\x1f\x7f]/.test(value.cookie)||typeof value.hasMore!=='boolean'||value.hasMore!==Boolean(value.cookie)||value.cookie&&(value.cookie===previous||seen.has(value.cookie)))throw new MailFault('BAD_RESPONSE');
 const people=value.people.map(row=>{if(!row||typeof row.email!=='string'||row.email.length>254||!/^[^\s<>@,;]+@[^\s<>@,;]+$/.test(row.email)||typeof row.sender!=='string'||row.sender.length>180||/[\x00-\x1f\x7f]/.test(row.sender))throw new MailFault('BAD_RESPONSE');return {email:row.email,sender:row.sender||row.email}});
 return {people,cookie:value.cookie,hasMore:value.hasMore,notice:typeof value.notice==='string'?value.notice.slice(0,1000):''};
}
export function mergeContacts(previous:Person[],incoming:Person[]){const people=new Map<string,Person>();for(const row of [...previous,...incoming])if(!people.has(row.email.toLowerCase()))people.set(row.email.toLowerCase(),row);if(people.size>5000)throw new MailFault('CONTACTS_LIMIT');return [...people.values()]}
