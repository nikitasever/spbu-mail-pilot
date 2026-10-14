export const esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
export const icon=name=>`<i data-lucide="${name}" aria-hidden="true"></i>`;
export const btn=(text,action,id='',cls='')=>`<button type="button" data-action="${action}" data-id="${esc(id)}" class="${cls}">${text}</button>`;
export const page=html=>'<div class="page">'+html+'</div>';
export const field=(label,name,value='',type='text',max=180)=>`<label>${label}<input name="${name}" type="${type}" maxlength="${max}" value="${esc(value)}"></label>`;
export const area=(label,name,value='',max=2000)=>`<label>${label}<textarea name="${name}" maxlength="${max}">${esc(value)}</textarea></label>`;
