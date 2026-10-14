import {esc,icon,btn,page,field} from '../core/markup.mjs';
export function renderDraftItem(d,context){
 const {draftDeletions}=context;
  const deleting=!!draftDeletions.get(d.id)?.busy,title=d.subject?.trim()||'Без темы',preview=(d.body||'').replace(/\s+/g,' ').trim();
  const changed=new Date(d.updatedAt||NaN),time=Number.isFinite(changed.getTime())?'<time datetime="'+changed.toISOString()+'" title="Изменено '+esc(changed.toLocaleString('ru-RU',{timeZone:'Europe/Moscow'}))+'">'+esc(changed.toLocaleString('ru-RU',{day:'numeric',month:'short',hour:'2-digit',minute:'2-digit',timeZone:'Europe/Moscow'}))+'</time>':'';
  const content='<span class="draft-subject">'+esc(title)+'</span><span class="draft-recipient">'+esc(d.to?.trim()?'Кому: '+d.to:'Получатель не указан')+'</span><span class="draft-preview">'+esc(preview||'Пока без текста')+'</span><span class="draft-meta">'+time+((d.attachments||[]).length?'<span>'+icon('paperclip')+' '+d.attachments.length+' влож.</span>':'')+'</span>';
  return '<div class="draft-row">'+btn(content,'edit-draft',d.id,'draft-open').replace('<button ','<button '+(deleting?'disabled ':'')+'aria-label="Продолжить черновик: '+esc(title)+'" ')+btn(icon(deleting?'loader-circle':'trash-2'),'delete-draft',d.id,'draft-delete').replace('<button ','<button '+(deleting?'disabled aria-busy="true" ':'')+'aria-label="'+(deleting?'Удаляем черновик':'Удалить черновик: '+esc(title))+'" title="Удалить черновик" ')+'</div>';
 }
