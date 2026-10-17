@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package ru.local.spbumailpilot

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable internal fun NativeWritingAiScreen(model:NativeComposerModel) {
    val s=model.state;val context=LocalContext.current
    BackHandler {if(s.aiDiscard)model.discardAi(false) else model.closeAi()}
    fun handoff(copy:Boolean){model.handoffAi(copy){text,isCopy->
        if(isCopy){
            val clip=android.content.ClipData.newPlainText("Текст для ИИ",text)
            clip.description.extras=android.os.PersistableBundle().apply {putBoolean("android.content.extra.IS_SENSITIVE",true)}
            context.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(clip)
        }else ShareActions.text(context,text)
    }}
    Scaffold(topBar={TopAppBar(title={Text("Запрос для ИИ")},navigationIcon={TextButton(onClick=model::closeAi,enabled=!s.aiBusy){Text("Назад")}})}){padding->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Text("Черновик сохранён. Проверьте и отредактируйте текст запроса перед копированием или передачей выбранному приложению. Вложения и поля адресатов не добавляются.",style=MaterialTheme.typography.bodySmall)
            Text("Этот инструмент не отправляет запрос через API Gemini. Ответ ИИ при необходимости перенесите в письмо вручную; исходный текст автоматически не меняется.",style=MaterialTheme.typography.bodySmall)
            s.error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
            s.notice?.let {Text(it,style=MaterialTheme.typography.bodySmall)}
            if(s.aiBusy)LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedTextField(s.aiText,model::aiText,label={Text("Подготовленный запрос")},enabled=!s.aiBusy&&!s.aiDiscard,modifier=Modifier.fillMaxWidth().heightIn(min=280.dp))
            Text("${s.aiText.length} / 120 000",style=MaterialTheme.typography.labelSmall)
            Button(onClick={handoff(false)},enabled=!s.aiBusy&&s.aiText.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text("Выбрать приложение ИИ")}
            OutlinedButton(onClick={handoff(true)},enabled=!s.aiBusy&&s.aiText.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text("Скопировать запрос")}
        }
    }
    if(s.aiDiscard)AlertDialog(onDismissRequest={model.discardAi(false)},title={Text("Закрыть запрос без сохранения?")},text={Text("Изменения запроса будут потеряны. Исходный черновик и его вложения останутся.")},confirmButton={TextButton(onClick={model.discardAi(true)}){Text("Закрыть")}},dismissButton={TextButton(onClick={model.discardAi(false)}){Text("Продолжить")}})
}
