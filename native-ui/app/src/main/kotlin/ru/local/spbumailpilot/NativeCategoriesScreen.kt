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

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun NativeCategoriesScreen(model: NativeCategoriesModel) {
    val state=model.state;val editor=state.editor
    BackHandler {model.back()}
    Scaffold(topBar={TopAppBar(title={Text(if(editor==null)"Категории и правила" else "Настроить категорию")},
        navigationIcon={TextButton(onClick=model::back,enabled=!state.busy){Text("Назад")}},
        actions={if(editor==null)TextButton(onClick={model.load(state.owner)},enabled=!state.busy){Text("Обновить")}})},
        bottomBar={if(editor!=null)Surface(tonalElevation=2.dp){Button(onClick=model::save,enabled=!state.busy,
            modifier=Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal=16.dp,vertical=8.dp)){Text("Сохранить")}}}) {padding->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
            state.notice?.let {Text(it,color=MaterialTheme.colorScheme.primary)}
            if(editor==null) {
                Text("Выигрывает первая подходящая категория. Любое условие работает как «или». Письма на сервере не перемещаются.",fontSize=13.sp)
                state.rules.forEach {rule->OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(rule.optString("name"),style=MaterialTheme.typography.titleMedium,color=categoryColor(rule.optString("color")))
                        Text(NativeCategoryRules.summary(rule),fontSize=13.sp)
                        Text((if(rule.optBoolean("enabled",true))"Категория включена" else "Категория выключена")+" · "+(if(rule.optBoolean("notify",true))"Уведомления включены" else "Без уведомлений"),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick={model.edit(rule.optString("id"))},enabled=!state.busy){Text("Настроить")}
                    }
                }}
                Text("Правила действуют локально. Разрешение уведомлений Android и настройки ящика имеют приоритет.",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                listOf("name" to "Название","senders" to "Отправители: адреса через запятую","domains" to "Домены: например spbu.ru","subjects" to "Слова или фразы в теме","contacts" to "Контакты: адреса через запятую").forEach {(key,label)->
                    OutlinedTextField(editor.optString(key),{model.field(key,it)},label={Text(label)},enabled=!state.busy,modifier=Modifier.fillMaxWidth(),minLines=if(key=="name")1 else 2)
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Использовать категорию",Modifier.weight(1f));Switch(editor.optBoolean("enabled",true),{model.option("enabled",it)},enabled=!state.busy)}
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Уведомления о новых письмах",Modifier.weight(1f));Switch(editor.optBoolean("notify",true),{model.option("notify",it)},enabled=!state.busy)}
                Text("Адреса и домены сравниваются точно, фразы в теме — без учёта регистра. Разделитель условий — запятая, точка с запятой или новая строка.",fontSize=12.sp)
            }
        }
    }
    if(state.leaveOpen)AlertDialog(onDismissRequest={model.leave(false)},title={Text("Закрыть без сохранения?")},
        confirmButton={TextButton(onClick={model.leave(true)}){Text("Закрыть")}},dismissButton={TextButton(onClick={model.leave(false)}){Text("Продолжить")}})
}
@Composable internal fun categoryColor(color: String):androidx.compose.ui.graphics.Color {
    val dark=LocalInboxDark.current
    return when(color){
        "coral"->androidx.compose.ui.graphics.Color(if(dark)0xFFFFA397 else 0xFFB33F35)
        "blue"->androidx.compose.ui.graphics.Color(if(dark)0xFF99C6F5 else 0xFF30649E)
        "green"->androidx.compose.ui.graphics.Color(if(dark)0xFF87D4B5 else 0xFF286942)
        "pink"->androidx.compose.ui.graphics.Color(if(dark)0xFFF0A5CC else 0xFF9D3D74)
        else->MaterialTheme.colorScheme.primary
    }
}
