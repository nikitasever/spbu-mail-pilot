package ru.local.spbumailpilot

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow

/** Uses the Activity's existing purple InboxTheme and platform-scaled Material3 text. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun NativeWritingSettingsScreen(model: NativeWritingSettingsModel) {
    val state=model.state
    val signature=state.editor=="signature"
    BackHandler {model.back()}
    Scaffold(topBar={TopAppBar(title={Text(if(signature)"Подпись" else if(state.editor!=null)"Шаблон письма" else "Подпись и шаблоны")},
        navigationIcon={TextButton(onClick=model::back,enabled=!state.busy){Text("Назад")}},
        actions={if(state.editor==null)TextButton(onClick=model::load,enabled=!state.busy){Text("Обновить")}})},
        bottomBar={if(state.editor!=null)Surface(tonalElevation=2.dp){Button(onClick=model::save,enabled=!state.busy&&state.ready,
            modifier=Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp)){Text("Сохранить")}}}) {padding->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let {Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.semantics {liveRegion=LiveRegionMode.Polite})}
            state.notice?.let {Text(it,color=MaterialTheme.colorScheme.primary,modifier=Modifier.semantics {liveRegion=LiveRegionMode.Polite})}
            if(state.editor==null){
                Text("Настройки для этого ящика",style=MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick=model::editSignature,enabled=state.ready&&!state.busy,modifier=Modifier.fillMaxWidth()){Text("Настроить подпись")}
                HorizontalDivider()
                Text("Шаблоны · ${state.templates.size}",style=MaterialTheme.typography.titleMedium)
                Button(onClick={model.editTemplate()},enabled=state.ready&&!state.busy,modifier=Modifier.fillMaxWidth()){Text("Новый шаблон")}
                if(state.ready&&state.templates.isEmpty())Text("Сохраните текст, который часто используете в письмах. Шаблоны можно вставлять в редакторе письма.")
                state.templates.forEach {item->OutlinedCard(Modifier.fillMaxWidth()){
                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                        Text(item.title,style=MaterialTheme.typography.titleMedium)
                        Text(item.body,maxLines=3,overflow=TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        // Separate full-width controls remain reachable with large fonts and narrow windows.
                        TextButton(onClick={model.editTemplate(item.id)},enabled=!state.busy,modifier=Modifier.fillMaxWidth()){Text("Изменить «${item.title}»")}
                        TextButton(onClick={model.requestDelete(item.id)},enabled=!state.busy,modifier=Modifier.fillMaxWidth()){Text("Удалить «${item.title}»",color=MaterialTheme.colorScheme.error)}
                    }
                }}
            }else{
                if(!signature)OutlinedTextField(state.title,model::title,label={Text("Название шаблона")},supportingText={Text("До 180 символов")},
                    isError=state.title.length>180,enabled=!state.busy,modifier=Modifier.fillMaxWidth())
                OutlinedTextField(state.body,model::body,label={Text(if(signature)"Текст подписи" else "Текст шаблона")},
                    supportingText={Text(if(signature)"До 4000 символов · обычный текст" else "До 100 000 символов · обычный текст")},
                    isError=state.body.length>(if(signature)4000 else 100000),enabled=!state.busy,modifier=Modifier.fillMaxWidth(),minLines=8)
                if(signature){
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                        Text("Добавлять автоматически в новые письма и ответы",Modifier.weight(1f))
                        Switch(state.auto,model::automatic,enabled=!state.busy)
                    }
                    Text("Пустая подпись отключает вставку. Существующие черновики сохраняют свой текст.",style=MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    if(state.discard){
        val safe=remember {FocusRequester()}
        AlertDialog(onDismissRequest={model.discard(false)},title={Text("Закрыть без сохранения?")},text={Text("Введённые изменения будут потеряны.")},
            confirmButton={TextButton(onClick={model.discard(true)}){Text("Закрыть")}},
            dismissButton={TextButton(onClick={model.discard(false)},modifier=Modifier.focusRequester(safe)){Text("Продолжить редактирование")}})
        LaunchedEffect(Unit){safe.requestFocus()}
    }
    state.deleting?.let {item->
        val safe=remember {FocusRequester()}
        AlertDialog(onDismissRequest=model::cancelDelete,title={Text("Удалить шаблон?")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text(item.title);state.error?.let {Text(it,color=MaterialTheme.colorScheme.error)};if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        }},confirmButton={TextButton(onClick=model::confirmDelete,enabled=!state.busy){Text("Удалить")}},
            dismissButton={TextButton(onClick=model::cancelDelete,enabled=!state.busy,modifier=Modifier.focusRequester(safe)){Text("Отмена")}})
        LaunchedEffect(Unit){safe.requestFocus()}
    }
}
