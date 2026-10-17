package ru.local.spbumailpilot

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun NativeMailExtrasScreen(model: NativeMailExtrasModel,openMail: (JSONObject)->Unit) {
    val state=model.state
    BackHandler {model.back()}
    Scaffold(topBar={TopAppBar(title={Text("Письма на потом")},navigationIcon={TextButton(onClick=model::back,enabled=!state.busy){Text("Назад")}},
        actions={TextButton(onClick=model::load,enabled=!state.busy){Text("Обновить")}})}) {padding->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
            state.notice?.let {Text(it,color=MaterialTheme.colorScheme.primary)}
            Column {
                listOf("response" to "Мне нужно ответить","snooze" to "Отложенные","followup" to "Жду ответа").forEach {(mode,label)->
                    FilterChip(selected=state.listMode==mode,onClick={model.selectList(mode)},label={Text(label)},enabled=!state.busy)
                }
            }
            Text(when(state.listMode){"snooze"->"Письма скрыты в нативном списке до срока. На сервере ничего не перемещается.";"followup"->"В срок проверим ответ по Message-ID на сервере. Ниже — состояние последнего обновления.";else->"Личные напоминания на этом телефоне. Письма остаются во входящих, прочитанность не меняется."},fontSize=13.sp)
            val records=when(state.listMode){"snooze"->state.snoozed;"followup"->state.followups;else->state.responses}
            if(records.isEmpty()&&!state.busy)Text("Список пуст. Добавьте срок из меню открытого письма.")
            records.forEach {record->OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(record.optString("subject").ifBlank {"Без темы"},style=MaterialTheme.typography.titleSmall)
                    Text(record.optString("sender"),fontSize=12.sp)
                    Text((if(record.optLong("at")<=System.currentTimeMillis())"Срок наступил · " else "Напомнить · ")+responseTime(record.optLong("at")),color=MaterialTheme.colorScheme.primary,fontSize=13.sp)
                    if(state.listMode=="followup") {
                        val status=state.replyStatus[NativeMailExtras.key(state.owner,record.getJSONObject("mail"))]
                        Text(when(status){"answered"->"Ответ найден";"notified"->"Уведомление показано";else->if(record.optLong("at")<=System.currentTimeMillis())"Ожидается фоновая проверка" else "Проверка запланирована"},fontSize=12.sp)
                    }
                    TextButton(onClick={openMail(record)},enabled=!state.busy){Text("Открыть письмо")}
                    TextButton(onClick={model.openResponse(record,true)},enabled=!state.busy){Text("Изменить / отменить")}
                }
            }}
            Text("Для уведомлений нужны разрешение Android и доступность системных напоминаний. Новое письмо не отправляется.",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable internal fun NativeMailExtrasDialogs(model: NativeMailExtrasModel) {
    val state=model.state;val mail=state.target ?: return
    if(state.mode=="label")AlertDialog(onDismissRequest=model::dismiss,title={Text("Метка письма")},text={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(mail.optString("subject").ifBlank {"Без темы"})
            Text("Метка хранится на телефоне и не меняет письмо на сервере.",fontSize=12.sp)
            state.error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
            if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            listOf("important","urgent","waiting","").forEach {value->OutlinedButton(onClick={model.label(value)},enabled=!state.busy,modifier=Modifier.fillMaxWidth()) {
                Text((if(state.expectedLabel==value)"✓ " else "")+NativeMailExtras.labelName(value).ifEmpty {"Убрать метку"})
            }}
        }
    },confirmButton={},dismissButton={TextButton(onClick=model::dismiss,enabled=!state.busy){Text("Закрыть")}})
    else AlertDialog(onDismissRequest=model::dismiss,title={Text(when(state.mode){"snooze"->"Отложить письмо";"followup"->"Проверить ответ собеседника";else->"Напомнить ответить"})},text={
        Column(Modifier.fillMaxWidth().heightIn(max=480.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(mail.optString("subject").ifBlank {"Без темы"})
            Text(when(state.mode){"snooze"->"До срока письмо будет скрыто в нативном списке. Отмена вернёт его сразу. Время Москвы.";"followup"->"Проверка References/In-Reply-To по Message-ID. В срок нужны сеть и доступ к ящику. Время Москвы.";else->"Время Москвы. Письмо останется в списке, прочитанность не изменится."},fontSize=12.sp)
            state.error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
            if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                TextButton(onClick={model.preset(false)},enabled=!state.busy){Text("Через час")}
                TextButton(onClick={model.preset(true)},enabled=!state.busy){Text("Завтра, 09:00")}
            }
            WorkspaceDateTime(NativeMailEvents.key(mail),state.day,state.time,!state.busy,false,model::field,allowEmptyTime=false)
            if(state.expectedResponse!=null)TextButton(onClick=model::removeResponse,enabled=!state.busy){Text(when(state.mode){"snooze"->"Вернуть письмо сейчас";"followup"->"Отменить проверку";else->"Отменить напоминание"},color=MaterialTheme.colorScheme.error)}
        }
    },confirmButton={TextButton(onClick=model::saveResponse,enabled=!state.busy){Text("Сохранить")}},dismissButton={TextButton(onClick=model::dismiss,enabled=!state.busy){Text("Закрыть")}})
}
internal fun responseTime(at: Long):String=runCatching {DateTimeFormatter.ofPattern("d MMM, HH:mm 'МСК'",Locale.forLanguageTag("ru")).withZone(ZoneId.of("Europe/Moscow")).format(Instant.ofEpochMilli(at))}.getOrDefault("")
