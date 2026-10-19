@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package ru.local.spbumailpilot
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable internal fun NativeScheduledScreen(model:NativeScheduledModel,onRestored:(String,String)->Unit){
    val s=model.state
    BackHandler {if(s.confirm!=null||s.returnId!=null)model.dismiss() else model.back()}
    Scaffold(topBar={TopAppBar(title={Text("По расписанию")},navigationIcon={TextButton(onClick=model::back,enabled=!s.busy){Text("Назад")}},actions={TextButton(onClick=model::load,enabled=!s.busy){Text("Обновить")}})}){padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            item {Text("Очередь этого ящика на телефоне. Android может задержать отправку без сети или при ограничениях фоновой работы.",style=MaterialTheme.typography.bodySmall)}
            if(s.busy)item {LinearProgressIndicator(Modifier.fillMaxWidth())}
            s.error?.let {item {Text(it,color=MaterialTheme.colorScheme.error)}}
            s.notice?.let {item {Text(it)}}
            if(!s.busy&&s.items.isEmpty())item {Text("Запланированных писем нет")}
            items(s.items,key={it.getString("id")}){row->
                Surface(tonalElevation=2.dp,shape=MaterialTheme.shapes.large,modifier=Modifier.fillMaxWidth()){
                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                        Text(row.optString("subject").ifBlank {"Без темы"},style=MaterialTheme.typography.titleMedium)
                        Text(row.optString("to"),style=MaterialTheme.typography.bodySmall)
                        Text(responseTime(row.optLong("at")))
                        val phase=row.optString("state")
                        Text(when(phase){"pending"->"Ожидает отправки";"preparing"->"Подготовка";"transmitting"->"Отправка началась — отменить уже нельзя";"sent"->"Отправлено";"cancelled"->"Отменено";"uncertain"->"Результат неизвестен — проверьте получателя и «Отправленные». Автоповтора нет.";"failed"->"Ошибка — автоматическая отправка остановлена";else->"Неизвестный статус; отправка не запускается этим экраном"})
                        if(phase=="pending"&&row.optLong("next")>row.optLong("at"))Text("Следующая попытка: ${responseTime(row.optLong("next"))}",style=MaterialTheme.typography.bodySmall)
                        row.optString("warning").takeIf {it.isNotBlank()}?.let {Text(it,color=MaterialTheme.colorScheme.error)}
                        if(ScheduledPolicy.cancellable(phase))OutlinedButton(onClick={model.requestCancel(row.getString("id"))},enabled=!s.busy){Text("Отменить отправку")}
                        if(row.optBoolean("recoverable"))OutlinedButton(onClick={model.requestReturn(row.getString("id"))},enabled=!s.busy){Text("Вернуть в редактор")}
                    }
                }
            }
            item {Spacer(Modifier.height(24.dp))}
        }
    }
    if(s.returnId!=null)AlertDialog(onDismissRequest=model::dismiss,title={Text("Вернуть письмо в редактор?")},text={Text("Будет создан черновик на этом телефоне. Другие черновики не перезаписываются. Отправка останется отменённой, пока вы сами не отправите письмо. Если результат прежней попытки неизвестен, проверьте получателя: повтор может создать дубликат.")},confirmButton={TextButton(onClick={model.restore(onRestored)}){Text("Вернуть")}},dismissButton={TextButton(onClick=model::dismiss){Text("Отмена")}})
    if(s.confirm!=null)AlertDialog(onDismissRequest=model::dismiss,title={Text("Отменить отложенную отправку?")},text={Text("Если передача уже началась, отмена может оказаться невозможной. При неизвестном результате сначала проверьте получателя и «Отправленные».")},confirmButton={TextButton(onClick=model::cancel){Text("Отменить отправку")}},dismissButton={TextButton(onClick=model::dismiss){Text("Оставить")}})
}
