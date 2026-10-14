export const ruleParts=value=>String(value||'').length>1000?[]:String(value||'').normalize('NFC').toLocaleLowerCase('ru-RU').split(/[,;\n]+/).map(x=>x.trim()).filter(Boolean).slice(0,50);

