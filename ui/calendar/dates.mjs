export const dateText=value=>value?new Date(value).toLocaleDateString('ru-RU',{day:'numeric',month:'short',timeZone:'Europe/Moscow'}):'';
export const calendarDate=date=>date.toISOString().slice(0,10);
export const shiftCalendarDate=(day,amount)=>{const date=new Date(day+'T12:00:00Z');date.setUTCDate(date.getUTCDate()+amount);return calendarDate(date);};
export const calendarDayLabel=day=>new Date(day+'T12:00:00Z').toLocaleDateString('ru-RU',{day:'numeric',month:'short',year:'numeric',timeZone:'UTC'});
export function calendarPeriodLabel(bounds){const first=new Date(bounds.start+'T12:00:00Z'),last=new Date(shiftCalendarDate(bounds.end,-1)+'T12:00:00Z');if(bounds.start.slice(0,7)===calendarDate(last).slice(0,7))return first.getUTCDate()+'–'+last.toLocaleDateString('ru-RU',{day:'numeric',month:'long',year:'numeric',timeZone:'UTC'});return first.toLocaleDateString('ru-RU',{day:'numeric',month:'short',...(first.getUTCFullYear()!==last.getUTCFullYear()?{year:'numeric'}:{}),timeZone:'UTC'})+' – '+calendarDayLabel(calendarDate(last));}
export function calendarTimeRange(t){
  const dateLabel=day=>{const date=new Date(day+'T12:00:00Z');return Number.isNaN(date.getTime())?day:date.toLocaleDateString('ru-RU',{day:'numeric',month:'short',year:'numeric',timeZone:'UTC'});};
  if(t.kind==='task')return t.time||'Весь день';
  if(t.allDay||!t.time){
   if(t.allDay&&t.endDate>t.date){const last=new Date(t.endDate+'T12:00:00Z');if(!Number.isNaN(last.getTime())){last.setUTCDate(last.getUTCDate()-1);const end=calendarDate(last);if(end>t.date)return 'Весь день · '+dateLabel(t.date)+' – '+dateLabel(end);}}
   return 'Весь день';
  }
  if(t.endDate>t.date)return dateLabel(t.date)+' '+t.time+' – '+dateLabel(t.endDate)+(t.endTime?' '+t.endTime:'');
  return t.endTime&&t.endTime>t.time?t.time+'–'+t.endTime:t.time;
 }
export const snoozeInput=at=>new Date(at+3*3600000).toISOString().slice(0,16);
