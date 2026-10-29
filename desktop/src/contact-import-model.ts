import {MailFault} from './protocol.ts';
export type ImportRow={index:number;sender:string;email:string;source:number;issue:''|'invalid'|'existing'|'duplicate'};
export type ImportPreview={token:string;name:string;capacity:number;rows:ImportRow[]};
export function parseImport(raw:unknown):ImportPreview{
 if(!raw||typeof raw!=='object')throw new MailFault('BAD_RESPONSE');const value=raw as ImportPreview;
 if(typeof value.token!=='string'||value.token.length>100||typeof value.name!=='string'||value.name.length>320||!Number.isInteger(value.capacity)||value.capacity<0||value.capacity>5000||!Array.isArray(value.rows)||!value.rows.length||value.rows.length>500)throw new MailFault('BAD_RESPONSE');
 const ids=new Set<number>();for(const row of value.rows){if(!row||!Number.isInteger(row.index)||row.index<0||row.index>=500||ids.has(row.index)||typeof row.sender!=='string'||row.sender.length>2000||typeof row.email!=='string'||row.email.length>2000||!Number.isInteger(row.source)||row.source<1||!['','invalid','existing','duplicate'].includes(row.issue))throw new MailFault('BAD_RESPONSE');ids.add(row.index)}
 return {token:value.token,name:value.name,capacity:value.capacity,rows:value.rows.map(row=>({...row}))};
}
