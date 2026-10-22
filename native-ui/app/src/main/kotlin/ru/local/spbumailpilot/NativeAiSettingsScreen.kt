@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package ru.local.spbumailpilot

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable internal fun NativeAiSettingsScreen(model:NativeAiSettingsModel) {
    val s=model.state
    BackHandler {model.back()}
    Scaffold(topBar={TopAppBar(title={Text("ИИ · Gemini")},navigationIcon={TextButton(onClick=model::back,enabled=!s.busy){Text("Назад")}})}){padding->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            if(s.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            s.error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
            s.notice?.let {Text(it)}
            Row(Modifier.fillMaxWidth().heightIn(min=56.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){Text("Разрешить нейроанализ для этого ящика",Modifier.weight(1f));Switch(s.enabled,model::toggle,enabled=s.ready&&!s.busy)}
            Text("При явном запросе сводки выбранные тексты писем передаются Google Gemini. Вложения не отправляются. Открытие и сохранение настроек не отправляет письма или ключ на сервер.",style=MaterialTheme.typography.bodySmall)
            Text("Сводка доступна из списка почты: «Ещё → Сводка почты».",style=MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text("Модель · отдельно для этого ящика",style=MaterialTheme.typography.titleMedium)
            OutlinedTextField(s.model,model::model,label={Text("Идентификатор модели Gemini")},singleLine=true,enabled=s.ready&&!s.busy,modifier=Modifier.fillMaxWidth())
            Text("Укажите доступный вашему ключу идентификатор без models/ и URL. Список моделей автоматически не запрашивается; доступность и квота могут зависеть от аккаунта и региона.",style=MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick=model::saveModel,enabled=s.ready&&!s.busy&&s.model!=s.initialModel,modifier=Modifier.fillMaxWidth()){Text("Сохранить модель")}
            HorizontalDivider()
            Text("API-ключ · общий для всех ящиков",style=MaterialTheme.typography.titleMedium)
            Text(if(s.configured)"Ключ сохранён на телефоне. Работоспособность не проверялась." else "Сохранённого ключа нет.")
            Text("Существующий ключ не показывается. Новый ключ хранится зашифрованным на телефоне; ключ шифрования — в Android Keystore. Введённое значение очищается при уходе в фон.",style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(s.key,model::key,label={Text(if(s.configured)"Новый ключ для замены" else "API-ключ Gemini")},singleLine=true,visualTransformation=PasswordVisualTransformation(),
                keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Password),enabled=s.ready&&!s.busy,modifier=Modifier.fillMaxWidth())
            Button(onClick=model::saveKey,enabled=s.ready&&!s.busy&&s.key.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text("Сохранить ключ")}
            OutlinedButton(onClick={model.confirmRemove(true)},enabled=s.ready&&!s.busy&&s.configured,modifier=Modifier.fillMaxWidth()){Text("Удалить общий ключ",color=MaterialTheme.colorScheme.error)}
            HorizontalDivider()
            Text("Прокси только для Gemini",style=MaterialTheme.typography.titleMedium)
            Text(if(s.proxyConfigured)"Включён · Финляндия · ${GeminiProxyTransport.HOST}:${GeminiProxyTransport.PORT}" else "Выключен · прямое подключение к Google")
            Text("Почта и календарь идут напрямую. Прокси защищён TLS с закреплённым сертификатом. Логин: gemini. Пароль хранится зашифрованным на телефоне и не отображается после сохранения. При ошибке прокси прямого повтора нет.",style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(s.proxyPassword,model::proxyPassword,label={Text("Пароль прокси")},singleLine=true,visualTransformation=PasswordVisualTransformation(),keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Password),enabled=s.ready&&!s.busy,modifier=Modifier.fillMaxWidth())
            Text("Введите текущий пароль пользователя gemini на сервере. Допускаются буквы, цифры и специальные символы; формат из 48 символов больше не требуется.",style=MaterialTheme.typography.bodySmall)
            Button(onClick=model::saveProxy,enabled=s.ready&&!s.busy&&GeminiProxyTransport.validPassword(s.proxyPassword),modifier=Modifier.fillMaxWidth()){Text("Сохранить и включить прокси")}
            OutlinedButton(onClick=model::disableProxy,enabled=s.ready&&!s.busy&&s.proxyConfigured,modifier=Modifier.fillMaxWidth()){Text("Отключить прокси")}
            TextButton(onClick=model::load,enabled=!s.busy){Text("Перечитать статус")}
        }
    }
    if(s.removeConfirm)AlertDialog(onDismissRequest={model.confirmRemove(false)},title={Text("Удалить общий ключ Gemini?")},text={Text("Умные сводки перестанут работать во всех ящиках до добавления ключа. Текущие запросы будут остановлены. Письма и черновики останутся.")},confirmButton={TextButton(onClick=model::removeKey,enabled=!s.busy){Text("Удалить")}},dismissButton={TextButton(onClick={model.confirmRemove(false)},enabled=!s.busy){Text("Отмена")}})
    if(s.discard)AlertDialog(onDismissRequest={model.discard(false)},title={Text("Закрыть без сохранения?")},text={Text("Введённый ключ и несохранённый идентификатор модели будут потеряны.")},confirmButton={TextButton(onClick={model.discard(true)}){Text("Закрыть")}},dismissButton={TextButton(onClick={model.discard(false)}){Text("Остаться")}})
}
