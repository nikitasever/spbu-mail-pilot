package ru.local.spbumailpilot

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.*
import java.util.Locale

/** Picker dates are UTC calendar labels, never device-zone instants. Times represent Moscow wall time. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun WorkspaceDateTime(id: String,day: String,time: String,enabled: Boolean,allowEmpty: Boolean,field: (String,String)->Unit,allowEmptyTime: Boolean=true) {
    var dateOpen by remember(id){mutableStateOf(false)}
    var timeOpen by remember(id){mutableStateOf(false)}
    LaunchedEffect(enabled){if(!enabled){dateOpen=false;timeOpen=false}}
    OutlinedTextField(day,{field("date",it)},label={Text("Дата: ГГГГ-ММ-ДД")},singleLine=true,modifier=Modifier.fillMaxWidth(),enabled=enabled)
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick={dateOpen=true},enabled=enabled){Text("Выбрать дату")}
        if(allowEmpty)TextButton(onClick={field("time","");field("date","")},enabled=enabled&&day.isNotEmpty()){Text("Убрать срок")}
    }
    OutlinedTextField(time,{field("time",it)},label={Text("Время: ЧЧ:ММ (Москва)")},singleLine=true,modifier=Modifier.fillMaxWidth(),enabled=enabled)
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick={timeOpen=true},enabled=enabled&&runCatching {NativeAgenda.date(day)}.isSuccess){Text("Выбрать время")}
        if(allowEmptyTime)TextButton(onClick={field("time","")},enabled=enabled&&time.isNotEmpty()){Text("Без времени")}
    }
    if(dateOpen&&enabled) {
        val date=runCatching {NativeAgenda.date(day)}.getOrDefault(LocalDate.now(ZoneId.of("Europe/Moscow")))
        val state=rememberDatePickerState(initialSelectedDateMillis=date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),yearRange=1970..2100)
        DatePickerDialog(onDismissRequest={dateOpen=false},confirmButton={TextButton(onClick={
            state.selectedDateMillis?.let {field("date",Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString())};dateOpen=false
        },enabled=state.selectedDateMillis!=null){Text("Выбрать")}},dismissButton={TextButton(onClick={dateOpen=false}){Text("Отмена")}}){DatePicker(state)}
    }
    if(timeOpen&&enabled) {
        val timeValue=runCatching {LocalTime.parse(time)}.getOrDefault(LocalTime.of(9,0))
        val state=rememberTimePickerState(initialHour=timeValue.hour,initialMinute=timeValue.minute,is24Hour=true)
        AlertDialog(onDismissRequest={timeOpen=false},title={Text("Время Москвы")},text={TimeInput(state)},
            confirmButton={TextButton(onClick={field("time",String.format(Locale.ROOT,"%02d:%02d",state.hour,state.minute));timeOpen=false}){Text("Выбрать")}},
            dismissButton={TextButton(onClick={timeOpen=false}){Text("Отмена")}})
    }
}
