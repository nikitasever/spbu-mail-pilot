import test from 'node:test';import assert from 'node:assert/strict';
import {parseContacts,mergeContacts} from '../src/contacts-model.ts';
const person={sender:'Имя',email:'name@example.invalid'};
test('directory cursors bind to coherent pages and refuse loops/malformed replies',()=>{
 assert.equal(parseContacts({people:[person],cookie:'next',hasMore:true},'',new Set()).people.length,1);
 for(const raw of [{people:[person],cookie:'next',hasMore:false},{people:[person],cookie:'next',hasMore:true},{people:[{...person,email:'a@example.invalid\r\nBcc: x@x.invalid'}],cookie:'',hasMore:false}])assert.throws(()=>parseContacts(raw,'next',new Set(['next'])));
 assert.throws(()=>parseContacts({people:Array(51).fill(person),cookie:'',hasMore:false},'',new Set()));
});
test('case-insensitive merge preserves earlier names and enforces the bounded session',()=>{
 assert.deepEqual(mergeContacts([person],[{sender:'Other',email:'NAME@example.invalid'}]),[person]);
 assert.throws(()=>mergeContacts([],Array.from({length:5001},(_,n)=>({sender:'Name',email:`${n}@example.invalid`}))));
});
