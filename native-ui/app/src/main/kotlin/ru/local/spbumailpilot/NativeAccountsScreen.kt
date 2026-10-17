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

@Composable internal fun NativeAccountsScreen(model:NativeAccountsModel) {
    val s=model.state
    BackHandler {model.back()}
    Scaffold(topBar={TopAppBar(title={Text(if(s.editor=="login")"Добавить аккаунт" else if(s.editor=="smtp")"Отправка · SMTP" else if(s.editor=="password")"Обновить сохранённый пароль" else "Аккаунты")},navigationIcon={TextButton(onClick=model::back,enabled=!s.busy){Text("Назад")}})}) {padding->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(s.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            s.error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
            s.notice?.let {Text(it)}
            if(s.editor!=null){
                if(s.editor=="login"){
                    Text("Пароль хранится в зашифрованном хранилище Android. Вход проверяется по IMAP с TLS. После ухода в фон пароль нужно ввести снова.",style=MaterialTheme.typography.bodySmall)
                    Text("Для Mail.ru: imap.mail.ru, порт 993 и пароль для внешнего приложения.",style=MaterialTheme.typography.bodySmall)
                    OutlinedTextField(s.user,{model.field("user",it)},label={Text("Логин или полный email")},singleLine=true,enabled=!s.busy,modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(s.password,{model.field("password",it)},label={Text("Пароль")},singleLine=true,visualTransformation=PasswordVisualTransformation(),keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Password),enabled=!s.busy,modifier=Modifier.fillMaxWidth())
                }
                if(s.editor=="password"){
                    Text("Обновление сохранённого пароля для ${s.user} (${s.host}). Пароль на самом почтовом сервере не меняется; новый пароль проверяется по IMAP с TLS перед сохранением на телефоне.",style=MaterialTheme.typography.bodySmall)
                    OutlinedTextField(s.password,{model.field("password",it)},label={Text("Новый пароль")},singleLine=true,visualTransformation=PasswordVisualTransformation(),keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Password),enabled=!s.busy,modifier=Modifier.fillMaxWidth())
                } else {
                    OutlinedTextField(s.host,{model.field("host",it)},label={Text(if(s.editor=="smtp")"Сервер SMTP" else "Сервер IMAP")},singleLine=true,enabled=!s.busy,modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(s.port,{model.field("port",it)},label={Text("Порт")},singleLine=true,enabled=!s.busy,modifier=Modifier.fillMaxWidth())
                }
                if(s.editor=="smtp"){
                    OutlinedTextField(s.from,{model.field("from",it)},label={Text("Адрес отправителя")},singleLine=true,enabled=!s.busy,modifier=Modifier.fillMaxWidth())
                    Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){Switch(s.ssl,model::ssl,enabled=!s.busy);Text(if(s.ssl)"TLS сразу · обычно 465" else "Обязательный STARTTLS · обычно 587",Modifier.padding(start=12.dp))}
                    Text("Используются логин и пароль выбранного ящика. Сертификат сервера проверяется. Сохранение не отправляет тестовое письмо.",style=MaterialTheme.typography.bodySmall)
                }
                Button(onClick=model::save,enabled=!s.busy,modifier=Modifier.fillMaxWidth()){Text(if(s.editor=="login")"Подключить ящик" else if(s.editor=="password")"Проверить и сохранить пароль" else "Сохранить SMTP")}
            }else{
                Text("Выход сохраняет пароль и локальные данные. Другой ящик открывается с отдельным состоянием экранов.",style=MaterialTheme.typography.bodySmall)
                for(row in s.rows)Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Text(row.user,style=MaterialTheme.typography.titleMedium)
                    Text(row.host+(if(row.active)" · Текущий" else ""),style=MaterialTheme.typography.bodySmall)
                    if(!row.active)OutlinedButton(onClick={model.switch(row.id)},enabled=!s.busy){Text("Открыть ящик")}
                    else OutlinedButton(onClick=model::smtp,enabled=!s.busy){Text("Настроить SMTP")}
                    TextButton(onClick={model.changePassword(row.id)},enabled=!s.busy){Text("Обновить сохранённый пароль")}
                    TextButton(onClick={model.confirm(row.id)},enabled=!s.busy){Text("Удалить сохранённый вход",color=MaterialTheme.colorScheme.error)}
                }}
                Button(onClick=model::add,enabled=s.ready&&!s.busy,modifier=Modifier.fillMaxWidth()){Text("Добавить аккаунт")}
                if(s.owner.isNotEmpty())OutlinedButton(onClick={model.confirm("logout")},enabled=!s.busy,modifier=Modifier.fillMaxWidth()){Text("Выйти из текущего ящика")}
                TextButton(onClick=model::load,enabled=!s.busy){Text("Обновить список")}
            }
        }
    }
    if(s.discard)AlertDialog(onDismissRequest={model.discard(false)},title={Text("Закрыть без сохранения?")},text={Text("Введённые настройки будут потеряны.")},confirmButton={TextButton(onClick={model.discard(true)}){Text("Закрыть")}},dismissButton={TextButton(onClick={model.discard(false)}){Text("Остаться")}})
    s.confirmation?.let {id->val logout=id=="logout"
        AlertDialog(onDismissRequest={if(!s.busy)model.confirm(null)},title={Text(if(logout)"Выйти из ящика?" else "Удалить сохранённый вход?")},
            text={Text(if(logout)"Пароль, офлайн-почта, задачи, заметки и черновики останутся на телефоне. Отложенная отправка продолжит работать." else "Для ${s.rows.find {it.id==id}?.user ?: "этого ящика"} удалятся пароль, офлайн-почта и подключения календарей; отложенная отправка остановится. Серверная почта, задачи, заметки и нативный черновик останутся.")},
            confirmButton={TextButton(onClick=model::confirmed,enabled=!s.busy){Text(if(logout)"Выйти" else "Удалить")}},dismissButton={TextButton(onClick={model.confirm(null)},enabled=!s.busy){Text("Отмена")}})
    }
}
