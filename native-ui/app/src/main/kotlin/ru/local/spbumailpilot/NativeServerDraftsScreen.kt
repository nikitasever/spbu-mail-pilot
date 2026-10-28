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

@Composable internal fun NativeServerDraftsScreen(model:NativeServerDraftsModel,open:(String,String)->Unit){
    val s=model.state;val context=LocalContext.current
    BackHandler {if(s.confirm!=null)model.dismiss() else model.back()}
    Scaffold(topBar={TopAppBar(title={Text("Черновики на сервере")},navigationIcon={TextButton(onClick=model::back){Text(if(s.busy)"Остановить" else "Назад")}},actions={TextButton(onClick={model.load()},enabled=!s.busy){Text("Обновить")}})}){padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            item {Text("Открытие создаёт текстовую копию с вложениями на телефоне. Серверный исходник остаётся; не отправляйте обе версии. Если есть связанный нативный черновик, откроется он с вашими последними правками.",style=MaterialTheme.typography.bodySmall)}
            if(s.folder.isNotEmpty())item {Text("${s.folder} · Загружено черновиков: ${s.rows.size}. Записей в папке: ${s.total}.",style=MaterialTheme.typography.bodySmall)}
            if(s.busy)item {Column {LinearProgressIndicator(Modifier.fillMaxWidth());if(s.bytes>0)Text("Получено: ${Formatter.formatShortFileSize(context,s.bytes)}",style=MaterialTheme.typography.bodySmall)}}
            s.error?.let {item {Text(it,color=MaterialTheme.colorScheme.error)}}
            s.notice?.let {item {Text(it,style=MaterialTheme.typography.bodySmall)}}
            if(s.rows.isEmpty()&&!s.busy&&s.error==null)item {Text(if(s.more)"В загруженной части черновиков нет. Продолжите загрузку." else "Доступных серверных черновиков нет")}
            items(s.rows,key={it.getString("uid")}){row->
                Surface(tonalElevation=2.dp,shape=MaterialTheme.shapes.large,modifier=Modifier.fillMaxWidth()){
                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                        Text(row.optString("subject").ifBlank {"Без темы"},style=MaterialTheme.typography.titleMedium)
                        row.optString("date").takeIf {it.isNotBlank()}?.let {Text(mailTime(it),style=MaterialTheme.typography.bodySmall)}
                        if(row.optBoolean("hasAttachments"))Text("Есть вложения",style=MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick={model.request(row.getString("uid"))},enabled=!s.busy&&!s.expired){Text("Открыть для редактирования")}
                    }
                }
            }
            if(s.more)item {OutlinedButton(onClick={model.load(true)},enabled=!s.busy&&!s.expired,modifier=Modifier.fillMaxWidth()){Text("Показать ещё")}}
            item {Spacer(Modifier.height(24.dp))}
        }
    }
    if(s.confirm!=null)AlertDialog(onDismissRequest=model::dismiss,title={Text("Открыть серверный черновик?")},text={Text("Будут загружены текст, адресаты и вложения. Редактор работает с текстом: оформление HTML и встроенные картинки не переносятся. Исходник останется на сервере. Отправитель — текущий ящик. Перед отправкой проверьте результат прежних попыток и не отправляйте обе копии.")},confirmButton={TextButton(onClick={model.copy(open)}){Text("Открыть копию")}},dismissButton={TextButton(onClick=model::dismiss){Text("Отмена")}})
}
