import {esc,icon,btn,page,field,area} from '../core/markup.mjs';
export function renderBatchDialog(context){
 const {bulkState,batchProgressView,batchProgressMarkup,fileProgressMarkup}=context;
  const state=bulkState,result=state.result,failures=result?.failures||[],v=batchProgressView();let content='';
  if(state.busy)content=batchProgressMarkup()+'<div class="batch-file-status"><p id="batch-caption" role="status">'+esc(v.caption)+'</p><strong id="batch-name">'+esc(state.stage==='folder'?'':v.name)+'</strong><div id="batch-file-progress">'+(state.stage==='folder'||state.stage==='finished'?'':fileProgressMarkup(state))+'</div><p id="batch-size" class="note" hidden>'+esc(v.size)+'</p></div>'+btn('Отменить скачивание','cancel-batch','','full');
  else if(result?.error)content='<p role="alert">'+esc(result.error)+'</p>'+btn('Повторить','retry-batch');
  else if(result?.total===undefined)content='<p>Выбор папки отменён.</p>';
  else content=batchProgressMarkup()+'<p>Сохранено: '+(result.saved||0)+' из '+result.total+'.'+(result.cancelled?' Скачивание остановлено.':'')+'</p>'+(failures.length?'<p>Не удалось скачать: '+failures.length+'.</p>'+failures.slice(0,30).map(f=>'<p class="note">'+esc(f.name)+(f.reason==='unsupported_destination'?' — выберите папку с поддержкой переименования':'')+'</p>').join('')+btn('Повторить несохранённые','retry-batch'):'');
  return '<dialog id="quick-popup" class="batch-popup" aria-labelledby="quick-title"><div class="quick-head"><h3 id="quick-title">Скачивание вложений</h3>'+(!state.busy?btn(icon('x'),'close-quick'):'')+'</div>'+content+(!state.busy?btn('Готово','close-quick','','primary'):'')+'<p id="quick-message" role="status"></p></dialog>';
 }
