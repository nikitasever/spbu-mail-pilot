package ru.local.spbumailpilot

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun NativeWorkspaceScreen(model: NativeWorkspaceModel,openCalendarDay: (String)->Unit = {},openMail: (String)->Unit = {}) {
    val state=model.state;val editor=state.editor
    BackHandler {model.back()}
    Scaffold(topBar={TopAppBar(title={Text(if(editor!=null)when(state.kind){"tasks"->"Задача";"events"->"Своё событие";else->"Заметка"} else if(state.eventRoute)"Своё событие" else "Личные записи")},navigationIcon={TextButton(onClick=model::back,enabled=!state.busy){Text("Назад")}},actions={
        if(editor==null)TextButton(onClick=model::load,enabled=!state.busy){Text("Обновить")}
    })},bottomBar={Surface(tonalElevation=2.dp){Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=16.dp,vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        if(editor==null&&!state.eventRoute&&state.taskMail==null&&state.eventMail==null)Button(onClick={model.edit()},enabled=!state.busy,modifier=Modifier.fillMaxWidth()){Text(if(state.kind=="tasks")"Добавить задачу" else "Добавить заметку")}
        else if(editor!=null){
            Button(onClick=model::save,enabled=!state.busy,modifier=Modifier.weight(1f)){Text("Сохранить")}
            if(state.expected!=null)OutlinedButton(onClick=model::requestDelete,enabled=!state.busy){Text("Удалить",color=MaterialTheme.colorScheme.error)}
        }
    }}}) {padding->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let {Text(it,Modifier.padding(16.dp),color=MaterialTheme.colorScheme.error)}
            state.notice?.let {Text(it,Modifier.padding(horizontal=16.dp,vertical=4.dp),fontSize=12.sp)}
            if(editor!=null)Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                fun value(key: String)=editor.optString(key)
                Text("Изменения сохраняются кнопкой «Сохранить».",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                (state.eventMail ?: editor.optJSONObject("mail"))?.let {source->
                    Text("Исходное письмо: "+source.optString("subject").ifBlank {"Без темы"},maxLines=2,overflow=TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.primary)
                }
                editor.optJSONObject("calendarSource")?.let {source->
                    Text("Из события календаря: "+source.optString("calendarTitle","Календарь"),maxLines=2,overflow=TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.primary)
                }
                OutlinedTextField(value("title"),{model.field("title",it)},label={Text("Название")},modifier=Modifier.fillMaxWidth(),enabled=!state.busy)
                if(state.kind!="notes") {
                    Text(if(state.kind=="events")"Локальное событие на этом телефоне. Дата: 1970–2100, время Москвы." else "Дата и время Москвы. Можно оставить срок пустым.",fontSize=12.sp)
                    WorkspaceDateTime(editor.optString("id"),value("date"),value("time"),!state.busy,state.kind=="tasks",model::field)
                    if(state.kind=="events"&&state.expected!=null)TextButton(onClick={openCalendarDay(value("date"))},
                        enabled=!state.busy&&SharedStateMerge.same(editor,state.expected)){Text("Показать в календаре")}
                    if(state.kind=="tasks") {
                    WorkspaceChoice("Приоритет",value("priority").ifBlank {"normal"},listOf("normal" to "Обычный","important" to "Важно","urgent" to "Срочно"),!state.busy){model.field("priority",it)}
                    WorkspaceChoice("Напоминание",value("reminder").ifBlank {"auto"},listOf("auto" to "По умолчанию","15" to "За 15 минут","30" to "За 30 минут","60" to "За час","1440" to "За день","none" to "Не напоминать"),!state.busy){model.field("reminder",it)}
                    Text("По умолчанию — за час; без времени — в 09:00 в день срока. Для уведомлений нужны разрешение Android и включённые уведомления задач.",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                }
                if(state.kind!="events") {
                val body=if(state.kind=="tasks")"notes" else "body"
                OutlinedTextField(value(body),{model.field(body,it)},label={Text(if(body=="notes")"Комментарий" else "Текст")},minLines=6,modifier=Modifier.fillMaxWidth(),enabled=!state.busy)
                }
                if(editor.has("mail")||editor.has("calendarSource"))Text("Связь с письмом или календарём сохранится. Общее событие не меняется.",fontSize=12.sp)
            } else if(!state.eventRoute&&state.taskMail==null&&state.eventMail==null){
                Row(Modifier.padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected=state.kind=="tasks",onClick={model.kind("tasks")},label={Text("Задачи")},enabled=!state.busy&&state.tasksEnabled)
                    FilterChip(selected=state.kind=="notes",onClick={model.kind("notes")},label={Text("Заметки")},enabled=!state.busy)
                }
                OutlinedTextField(state.query,model::query,label={Text("Поиск в записях")},singleLine=true,modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp))
                if(state.kind=="tasks")Row(Modifier.padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf("active" to "В работе","done" to "Готово","all" to "Все").forEach {(value,label)->FilterChip(selected=state.filter==value,onClick={model.filter(value)},label={Text(label)})}
                }
                val needle=state.query.trim().lowercase(Locale.ROOT)
                val visible=state.rows.filter {row->(state.kind!="tasks"||state.filter=="all"||row.optBoolean("done")== (state.filter=="done"))&&listOf("title","body","notes").joinToString(" "){row.optString(it)}.lowercase(Locale.ROOT).contains(needle)}.sortedWith(compareBy({if(state.kind=="notes")0 else when(it.optString("priority")){"urgent"->0;"important"->1;else->2}},{it.optString("date").ifBlank {"9999"}}))
                LazyColumn(Modifier.weight(1f)) {
                    if(visible.isEmpty()&&!state.busy)item {Text("Здесь пока нет записей по выбранным условиям.",Modifier.padding(20.dp))}
                    items(visible,key={it.optString("id")}) {row->
                        Column(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=12.dp)) {
                            Text(row.optString("title"),style=MaterialTheme.typography.titleSmall)
                            if(state.kind=="tasks") {
                                Text((row.optString("date")+" "+row.optString("time")).trim().ifBlank {"Без срока"},fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                row.optJSONObject("calendarSource")?.let {source->
                                    Text("Календарь: "+source.optString("calendarTitle","Календарь"),fontSize=12.sp,color=MaterialTheme.colorScheme.primary)
                                }
                            }
                            val text=row.optString(if(state.kind=="tasks")"notes" else "body")
                            if(text.isNotBlank())Text(text,maxLines=3,overflow=TextOverflow.Ellipsis,fontSize=14.sp)
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick={model.edit(row.optString("id"))},enabled=!state.busy){Text("Открыть")}
                                if(state.kind=="tasks")TextButton(onClick={model.done(row.optString("id"))},enabled=!state.busy){Text(if(row.optBoolean("done"))"Вернуть в работу" else "Выполнить")}
                            }
                            if(state.kind=="tasks"&&row.optJSONObject("mail")!=null)TextButton(onClick={openMail(row.optString("id"))},enabled=!state.busy){Text("Открыть исходное письмо")}
                        }
                        HorizontalDivider(Modifier.padding(horizontal=16.dp),thickness=.5.dp)
                    }
                }
            }
        }
    }
    if(state.deleteOpen)AlertDialog(onDismissRequest=model::cancelDelete,title={Text("Удалить запись?")},text={Text("Запись будет удалена с телефона. Письма и общие события календаря останутся.")},confirmButton={TextButton(onClick=model::delete){Text("Удалить",color=MaterialTheme.colorScheme.error)}},dismissButton={TextButton(onClick=model::cancelDelete){Text("Оставить")}})
    if(state.leaveOpen)AlertDialog(onDismissRequest={model.leave(false)},title={Text("Закрыть без сохранения?")},text={Text("Изменения этой формы не будут сохранены.")},confirmButton={TextButton(onClick={model.leave(true)}){Text("Закрыть")}},dismissButton={TextButton(onClick={model.leave(false)}){Text("Продолжить")}})
}
@Composable private fun WorkspaceChoice(title: String,value: String,options: List<Pair<String,String>>,enabled: Boolean,select: (String)->Unit) {
    var expanded by remember {mutableStateOf(false)}
    Box {
        OutlinedButton(onClick={expanded=true},enabled=enabled,modifier=Modifier.fillMaxWidth()){Text("$title: ${options.find {it.first==value}?.second ?: value}")}
        DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}){options.forEach {(key,label)->DropdownMenuItem(text={Text(label)},onClick={expanded=false;select(key)})}}
    }
}
