import test from 'node:test';import assert from 'node:assert/strict';
import {parseContacts,mergeContacts,parseLocalBook,localMatches,validContact} from '../src/contacts-model.ts';
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

test('local book schema rejects duplicate/invalid identities; filter alphabet, email and other initials',()=>{
 const a={id:'11111111-1111-4111-8111-111111111111',sender:'Ёлкина',email:'one@example.invalid'},b={id:'22222222-2222-4222-8222-222222222222',sender:'1 Отдел',email:'two@example.invalid'};
 const book=parseLocalBook({revision:'0',people:[a,b]});assert.equal(localMatches(book.people,'^:Ё').length,1);assert.equal(localMatches(book.people,'^:#')[0].id,b.id);assert.equal(localMatches(book.people,'ONE@EXAMPLE').length,1);
 assert.throws(()=>parseLocalBook({revision:'0',people:[a,{...b,email:a.email.toUpperCase()}]}));assert.throws(()=>parseLocalBook({revision:'invalid',people:[]}));assert.equal(validContact('Имя','x@example.invalid\r\nBcc: y@x.invalid'),false);
});
