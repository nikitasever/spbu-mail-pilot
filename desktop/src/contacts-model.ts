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

export type LocalPerson=Person&{id:string};
export type LocalBook={revision:string;people:LocalPerson[]};
export function validContact(sender:string,email:string){return Boolean(sender.trim())&&sender.length<=180&&!/[\x00-\x1f\x7f]/.test(sender)&&new TextEncoder().encode(email).length<=254&&/^[^\s<>@,;"]+@[^\s<>@,;"]+$/.test(email)}
export function parseLocalBook(raw:unknown):LocalBook{
 if(!raw||typeof raw!=='object')throw new MailFault('BAD_RESPONSE');const value=raw as LocalBook;
 if(typeof value.revision!=='string'||!(value.revision==='0'||/^[0-9a-f-]{36}$/.test(value.revision))||!Array.isArray(value.people)||value.people.length>5000)throw new MailFault('BAD_RESPONSE');
 const ids=new Set<string>(),emails=new Set<string>();const people=value.people.map(row=>{if(!row||typeof row.id!=='string'||!/^[0-9a-f-]{36}$/.test(row.id)||typeof row.sender!=='string'||typeof row.email!=='string'||!validContact(row.sender,row.email)||ids.has(row.id)||emails.has(row.email.toLowerCase()))throw new MailFault('BAD_RESPONSE');ids.add(row.id);emails.add(row.email.toLowerCase());return {id:row.id,sender:row.sender,email:row.email}});
 return {revision:value.revision,people};
}
export function localMatches(people:LocalPerson[],query:string){const needle=query.toLocaleUpperCase('ru-RU');return people.filter(row=>{const name=row.sender.toLocaleUpperCase('ru-RU'),email=row.email.toLocaleUpperCase('ru-RU');return needle.startsWith('^:')?needle==='^:#'?!/^[А-ЯЁA-Z]/.test(name):name.startsWith(needle.slice(2)):name.includes(needle)||email.includes(needle)}).sort((a,b)=>a.sender.localeCompare(b.sender,'ru')||a.email.localeCompare(b.email))}
