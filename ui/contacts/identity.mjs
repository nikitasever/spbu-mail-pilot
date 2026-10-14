export const contactAlphabet=Array.from('АБВГДЕЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯABCDEFGHIJKLMNOPQRSTUVWXYZ#');
export const contactLetter=p=>{const name=String(p.sender?.trim()||p.email||'').normalize('NFC').replace(/^[\s"'«»()[\]{}]+/u,''),letter=Array.from(name)[0]?.toLocaleUpperCase('ru-RU');return letter==='Ё'?'Е':contactAlphabet.includes(letter)?letter:'#';};
export const contactSort=(a,b)=>contactAlphabet.indexOf(contactLetter(a))-contactAlphabet.indexOf(contactLetter(b))||(a.sender?.trim()||a.email).localeCompare(b.sender?.trim()||b.email,'ru',{sensitivity:'base',numeric:true});
