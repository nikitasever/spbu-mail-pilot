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

@Composable internal fun NativeDraftsScreen(model:NativeDraftsModel,open:(String,String)->Unit,create:(String)->Unit,server:()->Unit){
    val s=model.state
    if(s.legacyOpen){NativeLegacyDraftsScreen(model,open);return}
    BackHandler {model.back()}
    Scaffold(topBar={TopAppBar(title={Text("Черновики на телефоне")},navigationIcon={TextButton(onClick=model::back,enabled=!s.busy){Text("Назад")}},actions={TextButton(onClick=model::load,enabled=!s.busy){Text("Обновить")}})}){padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            item {Text("${s.items.size} из ${NativeDraftBook.LIMIT}. Здесь черновики Compose этого ящика. Черновики прежнего клиента и произвольные серверные черновики пока показаны отдельно.",style=MaterialTheme.typography.bodySmall)}
            item {OutlinedButton(onClick=server,enabled=!s.busy,modifier=Modifier.fillMaxWidth()){Text("Черновики на сервере")}}
            item {OutlinedButton(onClick=model::legacy,enabled=!s.busy,modifier=Modifier.fillMaxWidth()){Text("Черновики прежнего интерфейса")}}
            item {Button(onClick={model.create(create)},enabled=!s.busy&&s.error==null&&s.items.size<NativeDraftBook.LIMIT,modifier=Modifier.fillMaxWidth()){Text("Новое письмо")}}
            if(s.busy)item {LinearProgressIndicator(Modifier.fillMaxWidth())}
            s.error?.let {item {Text(it,color=MaterialTheme.colorScheme.error)}}
            if(s.items.isEmpty()&&!s.busy&&s.error==null)item {Text("Сохранённых черновиков пока нет")}
            items(s.items.asReversed(),key={it.mail.getString("id")}){draft->
                Surface(tonalElevation=2.dp,shape=MaterialTheme.shapes.large,modifier=Modifier.fillMaxWidth()){
                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                        Text(draft.mail.optString("subject").ifBlank {"Без темы"},style=MaterialTheme.typography.titleMedium)
                        Text(draft.mail.optString("to").ifBlank {"Получатель не указан"},style=MaterialTheme.typography.bodySmall)
                        Text(when(draft.status){NativeDraft.EDITING->"Черновик";NativeDraft.SENT->"Отправлено";NativeDraft.SCHEDULED->"Передано в очередь";NativeDraft.SCHEDULING->"Нужна сверка планирования";else->"Отправка не подтверждена — проверьте результат перед повтором"},style=MaterialTheme.typography.bodySmall)
                        val count=draft.mail.getJSONArray("attachments").length()
                        if(count>0)Text("Вложений: $count",style=MaterialTheme.typography.bodySmall)
                        if(draft.mail.getString("id")==s.active)Text("Последний открытый",style=MaterialTheme.typography.labelSmall)
                        OutlinedButton(onClick={model.choose(draft.mail.getString("id"),open)},enabled=!s.busy){Text("Открыть")}
                    }
                }
            }
            item {Spacer(Modifier.height(24.dp))}
        }
    }
}
