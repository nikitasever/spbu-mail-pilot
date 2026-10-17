@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package ru.local.spbumailpilot
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.text.format.Formatter

@Composable internal fun NativeLegacyDraftsScreen(model:NativeDraftsModel,open:(String,String)->Unit){
    val s=model.state;val context=LocalContext.current
    BackHandler {if(s.importId!=null)model.dismissImport() else model.back()}
    Scaffold(topBar={TopAppBar(title={Text("Прежние черновики")},navigationIcon={TextButton(onClick=model::back,enabled=!s.busy){Text("Назад")}},actions={TextButton(onClick=model::load,enabled=!s.busy){Text("Обновить")}})}){padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            item {Text("Копирование сохраняет исходник, его файлы и серверную копию в прежнем интерфейсе. Не отправляйте обе копии. Повтор того же содержимого откроет уже созданный черновик, сохраняя ваши правки в нём.",style=MaterialTheme.typography.bodySmall)}
            if(s.legacyOmitted>0)item {Text("Не показано: ${s.legacyOmitted}. Исключены записи очереди и некорректные/повторяющиеся ID.",style=MaterialTheme.typography.bodySmall)}
            if(s.busy)item {Column {LinearProgressIndicator(Modifier.fillMaxWidth());if(s.copyBytes>0)Text("Скопировано: ${Formatter.formatShortFileSize(context,s.copyBytes)}",style=MaterialTheme.typography.bodySmall)}}
            if(s.copying)item {OutlinedButton(onClick=model::cancelImport,modifier=Modifier.fillMaxWidth()){Text("Остановить копирование")}}
            s.error?.let {item {Text(it,color=MaterialTheme.colorScheme.error)}}
            if(!s.busy&&s.error==null&&s.legacyItems.isEmpty())item {Text("Доступных прежних черновиков нет")}
            items(s.legacyItems.asReversed(),key={it.getString("id")}){draft->
                Surface(tonalElevation=2.dp,shape=MaterialTheme.shapes.large,modifier=Modifier.fillMaxWidth()){
                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                        Text(draft.optString("subject").ifBlank {"Без темы"},style=MaterialTheme.typography.titleMedium)
                        Text(draft.optString("to").ifBlank {"Получатель не указан"},style=MaterialTheme.typography.bodySmall)
                        Text("Вложений: ${draft.optJSONArray("attachments")?.length() ?: 0}",style=MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick={model.requestImport(draft.getString("id"))},enabled=!s.busy){Text("Скопировать в новые черновики")}
                    }
                }
            }
            item {Spacer(Modifier.height(24.dp))}
        }
    }
    if(s.importId!=null)AlertDialog(onDismissRequest=model::dismissImport,title={Text("Создать копию черновика?")},text={Text("Текст и доступные вложения будут скопированы. Исходник останется в прежнем интерфейсе и тоже сможет быть отправлен. Перед отправкой новой копии проверьте результат прежних попыток.")},confirmButton={TextButton(onClick={model.importLegacy(open)}){Text("Скопировать")}},dismissButton={TextButton(onClick=model::dismissImport){Text("Отмена")}})
}
