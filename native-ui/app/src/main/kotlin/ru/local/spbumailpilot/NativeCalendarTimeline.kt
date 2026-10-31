package ru.local.spbumailpilot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

/** Shared full-title timeline for agenda and the selected day's sheet. */
@Composable internal fun NativeCalendarCard(
    entry:CalendarEntry,enabled:Boolean,links:JSONArray,edit:()->Unit,
    openMail:(String)->Unit,unlinkMail:(String)->Unit,createTask:()->Unit,sheet:Boolean=false
){
    val data=entry.data;val kind=data.optString("kind")
    val dark=MaterialTheme.colorScheme.background.luminance()<.5f
    val accent=when(kind){"remote"->if(dark)Color(0xFF93BFFA) else Color(0xFF376AA1);"event"->if(dark)Color(0xFF87D4B5) else Color(0xFF287052);else->MaterialTheme.colorScheme.primary}
    val source=data.optString("calendarTitle").ifBlank {when(kind){"remote"->"Серверный календарь";"task"->"Задача";else->"Свои события"}}
    val description=listOf(data.optString("description"),data.optString("notes")).filter {it.isNotBlank()}.distinct().joinToString("\n\n")
    var expanded by remember(entry.key){mutableStateOf(false)}
    Surface(shape=if(sheet)MaterialTheme.shapes.extraLarge else androidx.compose.ui.graphics.RectangleShape,
        color=if(sheet)MaterialTheme.colorScheme.surfaceContainerLow else Color.Transparent,
        modifier=Modifier.fillMaxWidth().padding(horizontal=if(sheet)16.dp else 12.dp,vertical=if(sheet)6.dp else 0.dp)){
        Column(Modifier.fillMaxWidth().padding(horizontal=if(sheet)12.dp else 4.dp,vertical=16.dp)){
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),verticalAlignment=Alignment.Top){
                Text(NativeAgenda.timeRange(data).replace(" – ","–"),modifier=Modifier.width(90.dp).padding(end=10.dp,top=2.dp),fontSize=12.sp,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Box(Modifier.width(3.dp).fillMaxHeight().background(accent))
                Column(Modifier.weight(1f).padding(start=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Text(data.optString("title").ifBlank {"Без названия"},style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                    Text(source,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    data.optString("location").takeIf {it.isNotBlank()}?.let {Text("Место: $it",style=MaterialTheme.typography.bodyMedium)}
                    if(description.isNotBlank()){
                        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                        TextButton(onClick={expanded=!expanded},modifier=Modifier.heightIn(min=44.dp)){Text(if(expanded)"Скрыть описание" else "Описание")}
                        if(expanded)Text(description,style=MaterialTheme.typography.bodyMedium)
                    }
                    if(kind=="remote"||kind=="event")TextButton(onClick=createTask,enabled=enabled,modifier=Modifier.heightIn(min=44.dp)){
                        Text("Это моя задача")
                    }
                    if(kind=="event")TextButton(onClick=edit,enabled=enabled){Text("Изменить событие")}
                    for(i in 0 until links.length()){
                        val link=links.getJSONObject(i);val subject=link.optJSONObject("mail")?.optString("subject").orEmpty().ifBlank {"Открыть письмо"}
                        TextButton(onClick={openMail(link.toString())},enabled=enabled){Text(subject)}
                        TextButton(onClick={unlinkMail(link.optString("id"))},enabled=enabled){Text("Убрать связь с письмом")}
                    }
                }
            }
            if(!sheet)HorizontalDivider(Modifier.padding(top=16.dp),color=MaterialTheme.colorScheme.outlineVariant)
        }
    }
}
