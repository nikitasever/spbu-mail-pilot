@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package ru.local.spbumailpilot

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun NativeAccountsScreen(model:NativeAccountsModel) {
    val s=model.state
    BackHandler {model.back()}
    Scaffold(
        topBar={
            Column {
                TopAppBar(
                    colors=TopAppBarDefaults.topAppBarColors(containerColor=MaterialTheme.colorScheme.background),
                    title={Text(if(s.editor=="login")"Добавить аккаунт" else if(s.editor=="smtp")"Отправка · SMTP" else if(s.editor=="password")"Обновить сохранённый пароль" else "Аккаунты",fontWeight=FontWeight.SemiBold)},
                    navigationIcon={TextButton(onClick=model::back,enabled=!s.busy){Text("Назад")}}
                )
                HorizontalDivider(thickness=0.5.dp,color=MaterialTheme.colorScheme.outlineVariant)
            }
        }
    ) {padding->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(s.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            s.error?.let {
                Surface(shape=RoundedCornerShape(10.dp),color=MaterialTheme.colorScheme.errorContainer,modifier=Modifier.fillMaxWidth()) {
                    Text(it,Modifier.padding(12.dp),color=MaterialTheme.colorScheme.error,fontSize=13.sp)
                }
            }
            s.notice?.let {
                Surface(shape=RoundedCornerShape(10.dp),color=MaterialTheme.colorScheme.primaryContainer,modifier=Modifier.fillMaxWidth()) {
                    Text(it,Modifier.padding(12.dp),color=MaterialTheme.colorScheme.onPrimaryContainer,fontSize=13.sp)
                }
            }
            if(s.editor!=null){
                if(s.editor=="login"){
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        listOf("mail.spbu.ru" to "СПбГУ","imap.mail.ru" to "Mail.ru","imap.yandex.ru" to "Яндекс").forEach {(presetHost,label)->
                            val selected=s.host.equals(presetHost,ignoreCase=true)
                            FilterChip(
                                selected=selected,
                                onClick={model.field("host",presetHost);model.field("port","993")},
                                label={Text(label,fontSize=12.sp)},
                                shape=RoundedCornerShape(10.dp),
                                enabled=!s.busy
                            )
                        }
                    }
                    Surface(shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.surface,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),modifier=Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            Text("Пароль хранится в зашифрованном хранилище Android. Вход проверяется по IMAP с TLS. После ухода в фон пароль нужно ввести снова.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Для Mail.ru и Яндекса используйте полный адрес почты и пароль для внешнего приложения.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    OutlinedTextField(s.user,{model.field("user",it)},label={Text("Логин или полный email")},singleLine=true,shape=RoundedCornerShape(12.dp),enabled=!s.busy,modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(s.password,{model.field("password",it)},label={Text("Пароль")},singleLine=true,shape=RoundedCornerShape(12.dp),visualTransformation=PasswordVisualTransformation(),keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Password),enabled=!s.busy,modifier=Modifier.fillMaxWidth())
                }
                if(s.editor=="password"){
                    Surface(shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.surface,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),modifier=Modifier.fillMaxWidth()) {
                        Text("Обновление сохранённого пароля для ${s.user} (${s.host}). Пароль на самом почтовом сервере не меняется; новый пароль проверяется по IMAP с TLS перед сохранением на телефоне.",Modifier.padding(12.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedTextField(s.password,{model.field("password",it)},label={Text("Новый пароль")},singleLine=true,shape=RoundedCornerShape(12.dp),visualTransformation=PasswordVisualTransformation(),keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Password),enabled=!s.busy,modifier=Modifier.fillMaxWidth())
                } else {
                    OutlinedTextField(s.host,{model.field("host",it)},label={Text(if(s.editor=="smtp")"Сервер SMTP" else "Сервер IMAP")},singleLine=true,shape=RoundedCornerShape(12.dp),enabled=!s.busy,modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(s.port,{model.field("port",it)},label={Text("Порт")},singleLine=true,shape=RoundedCornerShape(12.dp),enabled=!s.busy,modifier=Modifier.fillMaxWidth())
                }
                if(s.editor=="smtp"){
                    OutlinedTextField(s.from,{model.field("from",it)},label={Text("Адрес отправителя")},singleLine=true,shape=RoundedCornerShape(12.dp),enabled=!s.busy,modifier=Modifier.fillMaxWidth())
                    Row(verticalAlignment=Alignment.CenterVertically){Switch(s.ssl,model::ssl,enabled=!s.busy);Text(if(s.ssl)"TLS сразу · обычно 465" else "Обязательный STARTTLS · обычно 587",Modifier.padding(start=12.dp))}
                    Text("Используются логин и пароль выбранного ящика. Сертификат сервера проверяется. Сохранение не отправляет тестовое письмо.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick=model::save,enabled=!s.busy,shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth().heightIn(min=44.dp)){Text(if(s.editor=="login")"Подключить ящик" else if(s.editor=="password")"Проверить и сохранить пароль" else "Сохранить SMTP")}
            }else{
                Text("Переключайте ящики в одно нажатие прямо из верхней панели приложения или управляйте сохранёнными входами ниже.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                for(row in s.rows) {
                    val palette=NativeIcons.avatarPalette(row.user)
                    Surface(
                        shape=RoundedCornerShape(16.dp),
                        color=MaterialTheme.colorScheme.surface,
                        border=BorderStroke(if(row.active) 1.5.dp else 1.dp, if(row.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                        modifier=Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                Surface(shape=androidx.compose.foundation.shape.CircleShape,color=palette.container,border=BorderStroke(1.dp,palette.content.copy(alpha=0.3f)),modifier=Modifier.size(40.dp)) {
                                    Box(contentAlignment=Alignment.Center) {
                                        Text(avatarLetters(row.user),fontSize=14.sp,fontWeight=FontWeight.Bold,color=palette.content)
                                    }
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(row.user,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                                    Text(row.host+(if(row.active)" · Текущий ящик" else ""),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if(row.active) {
                                    Surface(shape=RoundedCornerShape(8.dp),color=MaterialTheme.colorScheme.primaryContainer) {
                                        Text("Активен",Modifier.padding(horizontal=9.dp,vertical=4.dp),fontSize=11.sp,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                if(!row.active) Button(onClick={model.switch(row.id)},enabled=!s.busy,shape=RoundedCornerShape(10.dp)){Text("Переключить")}
                                else OutlinedButton(onClick=model::smtp,enabled=!s.busy,shape=RoundedCornerShape(10.dp)){Text("Настроить SMTP")}
                                OutlinedButton(onClick={model.changePassword(row.id)},enabled=!s.busy,shape=RoundedCornerShape(10.dp)){Text("Обновить сохранённый пароль")}
                            }
                            TextButton(onClick={model.confirm(row.id)},enabled=!s.busy,contentPadding=PaddingValues(horizontal=4.dp,vertical=0.dp)){Text("Удалить сохранённый вход",color=MaterialTheme.colorScheme.error,fontSize=12.sp)}
                        }
                    }
                }
                Button(onClick=model::add,enabled=s.ready&&!s.busy,shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth().heightIn(min=44.dp)){Text("Добавить аккаунт")}
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    if(s.owner.isNotEmpty())OutlinedButton(onClick={model.confirm("logout")},enabled=!s.busy,shape=RoundedCornerShape(12.dp),modifier=Modifier.weight(1f).heightIn(min=42.dp)){Text("Выйти из текущего ящика")}
                    OutlinedButton(onClick=model::load,enabled=!s.busy,shape=RoundedCornerShape(12.dp),modifier=Modifier.heightIn(min=42.dp)){Text("Обновить список")}
                }
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

