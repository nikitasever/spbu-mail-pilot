package ru.local.spbumailpilot
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

@Composable internal fun NativeRelatedResults(model:NativeRelatedSearchModel,compose:(String)->Unit,event:(JSONObject)->Unit){
    val s=model.state;if(!s.enabled||s.criteria==null)return
    var peopleOpen by remember(s.owner,s.criteria){mutableStateOf(false)}
    var eventsOpen by remember(s.owner,s.criteria){mutableStateOf(false)}
    Column(Modifier.fillMaxWidth().padding(horizontal=16.dp)){
        Text("Общий поиск: «${s.criteria.query}» · "+if(s.online)"сервер и телефон" else "на телефоне",fontSize=12.sp)
        Text("События: ${s.rangeLabel}. Отправитель и вложения ограничивают только почту.",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick={peopleOpen=!peopleOpen},modifier=Modifier.fillMaxWidth().semantics {stateDescription=if(peopleOpen)"Развёрнуто" else "Свёрнуто"}){
            Text("Контакты · ${s.people.size} ${if(peopleOpen)"⌃" else "⌄"}",Modifier.weight(1f))
            if(s.peopleBusy)CircularProgressIndicator(Modifier.size(16.dp),strokeWidth=2.dp)
        }
        if(peopleOpen){
            s.peopleNotice?.let {Text(it,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            s.peopleError?.let {Text(it,fontSize=12.sp,color=MaterialTheme.colorScheme.error)}
            if(s.people.isEmpty()&&!s.peopleBusy)Text(if(s.criteria.query.isEmpty())"Введите имя или адрес. Справочник доступен от двух символов." else "Контакты не найдены в проверенной части справочника и сохранённых адресах.",fontSize=12.sp)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max=200.dp)){
                items(s.people,key={it.getString("email").lowercase(java.util.Locale.ROOT)}){p->
                    TextButton(onClick={model.email(p.getString("email"))?.let(compose)},modifier=Modifier.fillMaxWidth().heightIn(min=44.dp)){
                        Column(Modifier.weight(1f)){Text(p.getString("sender"));Text(p.getString("email"),fontSize=12.sp)}
                        Text("Написать",fontSize=12.sp)
                    }
                }
                if(s.peopleMore)item {TextButton(onClick=model::moreContacts,enabled=!s.peopleBusy){Text("Показать ещё контакты")}}
            }
        }
        TextButton(onClick={eventsOpen=!eventsOpen},modifier=Modifier.fillMaxWidth().semantics {stateDescription=if(eventsOpen)"Развёрнуто" else "Свёрнуто"}){
            Text("События и задачи · ${s.events.size} ${if(eventsOpen)"⌃" else "⌄"}",Modifier.weight(1f))
            if(s.eventsBusy)CircularProgressIndicator(Modifier.size(16.dp),strokeWidth=2.dp)
        }
        if(eventsOpen){
            s.eventsError?.let {Text(it,fontSize=12.sp,color=MaterialTheme.colorScheme.error)}
            if(s.events.isEmpty()&&!s.eventsBusy)Text("События не найдены в выбранном диапазоне.",fontSize=12.sp)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max=200.dp)){
                items(s.events,key={NativeRelatedSearchModel.eventKey(it)}){e->
                    TextButton(onClick={model.event(NativeRelatedSearchModel.eventKey(e))?.let(event)},modifier=Modifier.fillMaxWidth().heightIn(min=44.dp)){
                        Column(Modifier.fillMaxWidth()){
                            Text(e.optString("title").ifBlank {"Без названия"})
                            Text(e.optString("date")+" · "+NativeAgenda.timeRange(e),fontSize=12.sp)
                        }
                    }
                }
            }
        }
        HorizontalDivider()
    }
}
