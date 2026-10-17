@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package ru.local.spbumailpilot

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable internal fun NativeDigestScreen(model:NativeDigestModel,settings:()->Unit,source:(Int)->Unit) {
    val s=model.state
    BackHandler {model.back()}
    Scaffold(topBar={TopAppBar(title={Text("Сводка почты")},navigationIcon={TextButton(onClick=model::back){Text("Назад")}},actions={TextButton(onClick=settings,enabled=!s.busy){Text("ИИ")}})}){padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            item {
                Text("Папка: ${NativeFolders.label(s.folder)}",style=MaterialTheme.typography.titleMedium)
                Text("Выбирайте из загруженных писем этой папки. Подготовка требует сети и не отмечает письма прочитанными. Тексты передаются Google только отдельной кнопкой; вложения не передаются.",style=MaterialTheme.typography.bodySmall)
                s.error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
                s.notice?.let {Text(it,style=MaterialTheme.typography.bodySmall)}
            }
            if(s.busy)item {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(if(s.phase=="loading")"Подготовка писем: ${s.done} из ${s.total}" else "Gemini готовит сводку…")
                OutlinedButton(onClick=model::cancel,modifier=Modifier.fillMaxWidth()){Text("Отменить")}
            }
            if(s.phase=="choose"){
                item {
                    Text("Выбрано: ${s.selected.size} из 20")
                    TextButton(onClick=model::firstTwenty,enabled=s.candidates.isNotEmpty()){Text("Выбрать первые 20 загруженных")}
                    Button(onClick=model::prepare,enabled=s.selected.isNotEmpty(),modifier=Modifier.fillMaxWidth()){Text("Подготовить выбранные письма")}
                    if(s.candidates.isEmpty())Text("Сначала загрузите письма в списке почты.")
                }
                items(s.candidates,key={it.ref.validity+":"+it.ref.uid}){mail->
                    Card(Modifier.fillMaxWidth()) {Row(Modifier.padding(12.dp)){
                        Checkbox(mail.ref in s.selected,{model.select(mail.ref)})
                        Column(Modifier.weight(1f)){
                            Text(mail.subject.ifBlank {"Без темы"},style=MaterialTheme.typography.titleSmall)
                            Text(mail.sender,style=MaterialTheme.typography.bodySmall)
                            Text(mailTime(mail.date),style=MaterialTheme.typography.bodySmall)
                        }
                    }}
                }
            }
            if(s.items.isNotEmpty()&&!s.busy){
                item {
                    Text("Подготовлено: ${s.items.size}; недоступно: ${s.failures.size}",style=MaterialTheme.typography.titleMedium)
                    Text("Модель: ${s.model}",style=MaterialTheme.typography.bodySmall)
                    Text("Перед отправкой можно просмотреть тексты ниже. Длинный текст ограничен; обрезка помечена. При ошибке Google автоматического повтора нет.",style=MaterialTheme.typography.bodySmall)
                    Button(onClick=model::analyze,modifier=Modifier.fillMaxWidth()){Text(if(s.phase=="result")"Повторить анализ ${s.items.size} писем" else "Отправить ${s.items.size} писем в Gemini")}
                    OutlinedButton(onClick=model::editSelection,modifier=Modifier.fillMaxWidth()){Text("Изменить выбор")}
                }
                if(s.phase=="result"){
                    item {SelectionContainer {Text(s.overview,style=MaterialTheme.typography.bodyLarge)}}
                    items(s.points){point->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                        Text(when(point.kind){"action"->"Требует внимания";"reply"->"Ответ";"deadline"->"Срок";else->"Информация"},style=MaterialTheme.typography.labelMedium)
                        SelectionContainer {Column {Text(point.title,style=MaterialTheme.typography.titleMedium);Text(point.detail);if(point.deadline.isNotBlank())Text("Срок: ${point.deadline}")}}
                        point.sources.forEach {number->OutlinedButton(onClick={source(number)},modifier=Modifier.fillMaxWidth()){Text("Письмо-источник $number")}}
                    }}}
                    item {Text("Сводка может ошибаться. Проверьте сроки и ответственность по исходным письмам. Задачи и ответы автоматически не создаются.",style=MaterialTheme.typography.bodySmall)}
                }
                items(s.items.size){index->val item=s.items[index];val header=item.getJSONObject("message")
                    var expanded by remember(item){mutableStateOf(false)}
                    Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){
                        Text("${index+1}. ${header.optString("subject").ifBlank {"Без темы"}}",style=MaterialTheme.typography.titleSmall)
                        Text(header.optString("sender"),style=MaterialTheme.typography.bodySmall)
                        if(item.optBoolean("truncated"))Text("Текст обрезан",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelMedium)
                        TextButton(onClick={expanded=!expanded}){Text(if(expanded)"Скрыть текст" else "Показать подготовленный текст")}
                        if(expanded)SelectionContainer {Text(item.getString("text"))}
                    }}
                }
            }
            if(s.failures.isNotEmpty())item {Text("Не удалось подготовить:",style=MaterialTheme.typography.titleSmall);s.failures.forEach {Text(it.subject.ifBlank {"Без темы"},style=MaterialTheme.typography.bodySmall)}}
        }
    }
}
