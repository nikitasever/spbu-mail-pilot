import {esc,icon,btn,page,field} from '../core/markup.mjs';
export function renderProfileView(context){
 const {account}=context;return page('<div class="profile-avatar">'+esc(account.charAt(0).toUpperCase())+'</div><h3>'+esc(account)+'</h3><p class="note">Данные входа сохранены на телефоне в зашифрованном виде. «Выйти» закрывает ящик, сохраняя пароль. Удалить сохранённый вход можно в списке аккаунтов.</p><div class="actions">'+btn('Сменить аккаунт','switch-account','','primary')+btn('Выйти из текущего ящика','logout')+'</div>');}
