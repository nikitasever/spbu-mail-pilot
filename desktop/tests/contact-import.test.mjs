import test from 'node:test';import assert from 'node:assert/strict';import {parseImport} from '../src/contact-import-model.ts';
const row={index:0,sender:'Имя',email:'one@example.invalid',source:2,issue:''};
test('import preview is bounded, identities are unique and issues/selection capacity are explicit',()=>{
 const value={token:'preview',name:'Контакты.csv',capacity:1,rows:[row]};assert.equal(parseImport(value).rows[0].email,row.email);
 for(const changed of [{...value,capacity:-1},{...value,rows:[row,row]},{...value,rows:[{...row,issue:'selectable-anyway'}]},{...value,rows:Array(501).fill(row)}])assert.throws(()=>parseImport(changed));
});
