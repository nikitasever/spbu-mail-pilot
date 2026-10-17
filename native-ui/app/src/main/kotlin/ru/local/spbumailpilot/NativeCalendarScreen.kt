package ru.local.spbumailpilot

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun NativeCalendarScreen(model: NativeCalendarModel,fullClient: ()->Unit,editEvent: (String,String?)->Unit = {_,_->},openMail: (String)->Unit = {}) {
    val state=model.state
    val date=LocalDate.parse(state.day)
    val month=YearMonth.from(date)
    val label=DateTimeFormatter.ofPattern("d MMMM, EEEE",Locale.forLanguageTag("ru"))
    BackHandler(onBack=model::close)
    Scaffold(topBar={TopAppBar(title={Text("Календарь")},navigationIcon={TextButton(onClick=model::close){Text("Почта")}},actions={TextButton(onClick={model.load(true)},enabled=!state.busy){Text("Обновить")}})},
        bottomBar={Surface(tonalElevation=2.dp){Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Button(onClick={editEvent(state.day,null)},enabled=!state.busy,modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=6.dp)){Text("Своё событие")}
            TextButton(onClick=fullClient,modifier=Modifier.fillMaxWidth()){Text("Все возможности почты")}
        }}}) {padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    listOf("day" to "День","week" to "Неделя","month" to "Месяц","agenda" to "Повестка").forEach {(mode,text)->
                        FilterChip(selected=state.mode==mode,onClick={model.mode(mode)},label={Text(text)})
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                    IconButton(onClick={model.shift(-1)},modifier=Modifier.semantics {contentDescription="Предыдущий период"}){Text("‹",fontSize=24.sp)}
                    Text(if(state.mode=="day")date.format(label) else month.format(DateTimeFormatter.ofPattern("LLLL yyyy",Locale.forLanguageTag("ru"))),Modifier.weight(1f),style=MaterialTheme.typography.titleSmall)
                    IconButton(onClick={model.shift(1)},modifier=Modifier.semantics {contentDescription="Следующий период"}){Text("›",fontSize=24.sp)}
                    TextButton(onClick=model::today){Text("Сегодня")}
                }
                if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error?.let {Text(it,Modifier.padding(16.dp),color=MaterialTheme.colorScheme.error)}
                state.actionNotice?.let {Text(it,Modifier.padding(horizontal=16.dp,vertical=4.dp),fontSize=12.sp)}
                if(state.mode=="week"||state.mode=="month") {
                    val start=if(state.mode=="week")NativeAgenda.first(state.day,"week") else month.atDay(1).minusDays(month.atDay(1).dayOfWeek.value-1L)
                    val cells=if(state.mode=="week")7 else ((month.atDay(1).dayOfWeek.value-1+month.lengthOfMonth()+6)/7)*7
                    Row(Modifier.fillMaxWidth().padding(horizontal=4.dp)) {
                        listOf("Пн","Вт","Ср","Чт","Пт","Сб","Вс").forEach {Text(it,Modifier.weight(1f),textAlign=androidx.compose.ui.text.style.TextAlign.Center,fontSize=11.sp)}
                    }
                    for(week in 0 until cells/7)Row(Modifier.fillMaxWidth().padding(horizontal=4.dp)) {
                        for(index in 0..6) {
                            val day=start.plusDays((week*7+index).toLong())
                            val present=state.mode=="week"||YearMonth.from(day)==month
                            if(!present)Spacer(Modifier.weight(1f).height(64.dp)) else {
                                val entries=state.entries.filter {NativeAgenda.onDay(it.data,day.toString())}
                                val selected=day==date
                                val background=if(selected)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background
                                Surface(onClick={model.select(day.toString())},modifier=Modifier.weight(1f).height(64.dp).semantics {contentDescription=day.format(label)+", событий и задач: "+entries.size},color=background,shape=androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) {
                                    Column(Modifier.fillMaxSize(),horizontalAlignment=androidx.compose.ui.Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                                        Text(day.dayOfMonth.toString(),style=MaterialTheme.typography.titleSmall)
                                        if(entries.isNotEmpty()) {
                                            Row(horizontalArrangement=Arrangement.spacedBy(2.dp)) {
                                                entries.map {it.data.optString("kind")}.distinct().forEach {kind->
                                                    Text("•",color=calendarColor(kind),fontSize=12.sp)
                                                }
                                            }
                                            Text(entries.size.toString(),fontSize=10.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    listOf("remote" to "Сервер","event" to "События","task" to "Задачи").forEach {(kind,text)->Text("• $text",color=calendarColor(kind),fontSize=11.sp)}
                }
                state.notice?.let {Text(it,Modifier.padding(horizontal=16.dp,vertical=4.dp),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                Text("Время Москвы · Свои события редактируются на телефоне. Серверные события доступны для чтения.",Modifier.padding(horizontal=16.dp,vertical=4.dp),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val days=if(state.mode=="agenda")generateSequence(date){it.plusDays(1)}.takeWhile {YearMonth.from(it)==month}.toList() else listOf(date)
            days.forEach {day->
                val entries=state.entries.filter {NativeAgenda.onDay(it.data,day.toString())}
                if(day==date||entries.isNotEmpty()) {
                    item(key="day:$day") {Text(day.format(label),Modifier.padding(horizontal=16.dp,vertical=12.dp),style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.primary)}
                    if(entries.isEmpty())item(key="empty:$day") {Text(if(state.busy)"Загружаем…" else "Нет событий и задач в доступных данных.",Modifier.padding(horizontal=16.dp,vertical=8.dp),fontSize=13.sp)}
                    entries.forEach {entry->item(key="$day:${entry.key}") {NativeCalendarCard(entry,!state.busy,remember(entry.key,state.links){
                        val all=org.json.JSONArray();state.links.forEach {all.put(it)}
                        runCatching {NativeMailLinks.forEvent(entry.data,all)}.getOrDefault(org.json.JSONArray())
                    },{editEvent(entry.data.optString("date"),entry.data.optString("id"))},openMail)}}
                }
            }
            item {Spacer(Modifier.height(16.dp))}
        }
    }
}
@Composable private fun calendarColor(kind: String): Color {
    val dark=androidx.compose.foundation.isSystemInDarkTheme()
    return when(kind){"remote"->if(dark)Color(0xFF99C6F5) else Color(0xFF355E8E);"event"->if(dark)Color(0xFF87D4B5) else Color(0xFF287052);else->MaterialTheme.colorScheme.primary}
}
@Composable private fun NativeCalendarCard(entry: CalendarEntry,enabled: Boolean,links: org.json.JSONArray,edit: ()->Unit,openMail: (String)->Unit) {
    val data=entry.data
    var expanded by remember(entry.key) {mutableStateOf(false)}
    Card(onClick={expanded=!expanded},colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(NativeAgenda.timeRange(data),fontSize=12.sp,color=calendarColor(data.optString("kind")))
            Text(data.optString("title","Без названия"),style=MaterialTheme.typography.titleSmall)
            if(data.optString("calendarTitle").isNotBlank())Text(data.optString("calendarTitle"),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(expanded) {
                listOf("location","description","notes").forEach {field->if(data.optString(field).isNotBlank())Text(data.optString(field),Modifier.padding(top=8.dp),fontSize=14.sp)}
            }
            if(data.optString("kind")=="event")TextButton(onClick=edit,enabled=enabled){Text("Изменить своё событие")}
            if(links.length()>0) {
                Text("Связанные письма · ${links.length()}",fontSize=12.sp,color=MaterialTheme.colorScheme.primary)
                for(index in 0 until links.length()) {
                    val link=links.getJSONObject(index);val mail=link.optJSONObject("mail")
                    TextButton(onClick={openMail(link.toString())},enabled=enabled) {
                        Text(mail?.optString("subject")?.ifBlank {"Открыть письмо"} ?: "Повреждённая ссылка",maxLines=2,overflow=TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
