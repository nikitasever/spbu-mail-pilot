import {snoozeInput} from '../calendar/dates.mjs';
export function inferMailDateHint(m,previewText,clock=Date.now){
  const subject=String(m.subject||''),pattern=/\b(\d{2})\.(\d{2})\.(\d{4})[ ,г.\s]+(\d{2}):(\d{2})\b/,words=[...subject.matchAll(/(?:^|[\s,.!?])(послезавтра|завтра|сегодня)(?=$|[\s,.!?])/giu)];let match=subject.match(pattern);if(!match&&!words.length)match=previewText(m).match(pattern);let day,time,relative=false;
  if(match){day=match[3]+'-'+match[2]+'-'+match[1];time=match[4]+':'+match[5];}
  else{const times=[...subject.matchAll(/\b([01]?\d|2[0-3])[:-]([0-5]\d)\b/g)];const source=Date.parse(m.date);if(words.length!==1||times.length!==1||!Number.isFinite(source))return null;const date=new Date(snoozeInput(source).slice(0,10)+'T12:00:00Z');date.setUTCDate(date.getUTCDate()+({сегодня:0,завтра:1,послезавтра:2}[words[0][1].toLocaleLowerCase('ru-RU')]));day=date.toISOString().slice(0,10);time=times[0][1].padStart(2,'0')+':'+times[0][2];relative=true;}
  const at=Date.parse(day+'T'+time+':00+03:00');return Number.isFinite(at)&&at>clock()&&snoozeInput(at)===day+'T'+time?{date:day,time,relative}:null;
 }

