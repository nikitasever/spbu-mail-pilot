const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const cases=JSON.parse(fs.readFileSync('native-ui/tests/mail-thread-fixtures.json','utf8'));
const app=fs.readFileSync('assets/app.js','utf8'),start=app.indexOf('    function mailGroups()'),end=app.indexOf('    const currentThread',start);
assert.ok(start>=0&&end>start,'maintained legacy grouping must exist');
const identity=fs.readFileSync('ui/mail/identity.mjs','utf8').match(/export const threadIds=([^;]+);/);
assert.ok(identity,'maintained legacy identifier parser must exist');
for(const test of cases){
 const program=`const messages=JSON.parse(input),local={snoozedMail:[]},mailFolder='INBOX',threadsEnabled=()=>true,uidKey=m=>JSON.stringify([m.folder||'INBOX',m.validity,m.uid]),threadIds=${identity[1]};${app.slice(start,end)};mailGroups().map(g=>g.members.map(m=>m.uid));`;
 const actual=vm.runInNewContext(program,{input:JSON.stringify(test.rows)});
 assert.equal(JSON.stringify(actual),JSON.stringify(test.expected),test.name);
}
console.log(`PASS: ${cases.length} expected groups verified against actual maintained WebView implementation.`);
