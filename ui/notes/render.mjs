import {esc,icon,btn,page} from '../core/markup.mjs';
export function renderNotesView(context){
 const {local}=context;return page(btn('Новая заметка','new-note','','primary')+(local.notes.length?local.notes.map(n=>`<div class="list-item"><h4>${esc(n.title)}</h4><p>${esc(n.body)}</p><div class="inline-actions">${btn('Изменить','edit-note',n.id)}${btn('Удалить','delete-note',n.id)}</div></div>`).join(''):'<p class="note">Заметки хранятся только на этом телефоне.</p>'));}
