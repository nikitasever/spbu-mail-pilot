@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package ru.local.spbumailpilot

import androidx.lifecycle.repeatOnLifecycle
import android.provider.Settings
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable internal fun NativeSettingsScreen(
    model: NativeSettingsModel,
    activity: ComposeInboxActivity,
    accounts: () -> Unit,
    writing: () -> Unit,
    ai: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {}
) {
    val s = model.state
    var backgroundStatus by remember {mutableStateOf(BackgroundMailService.status(activity))}
    LaunchedEffect(activity){activity.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED){while(true){backgroundStatus=BackgroundMailService.status(activity);kotlinx.coroutines.delay(5000)}}}
    BackHandler { model.back() }
    Scaffold(
        bottomBar = bottomBar,
        topBar = {
            Column {
                TopAppBar(
                    colors=TopAppBarDefaults.topAppBarColors(containerColor=MaterialTheme.colorScheme.background),
                    title={Text("Настройки",fontWeight=FontWeight.SemiBold)},
                    navigationIcon={TextButton(onClick=model::back,enabled=!s.busy){Text("Назад")}}
                )
                HorizontalDivider(thickness=0.5.dp,color=MaterialTheme.colorScheme.outlineVariant)
            }
        }
    ){padding->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            if(s.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            s.error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
            s.notice?.let {Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)}
            Text("Настройки сохраняются отдельно для каждого ящика.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick=accounts,enabled=!s.busy,shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth().heightIn(min=42.dp)){Text("Аккаунты и SMTP")}
            OutlinedButton(onClick=writing,enabled=!s.busy,shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth().heightIn(min=42.dp)){Text("Шаблоны и подпись")}
            OutlinedButton(onClick=ai,enabled=s.ready&&!s.busy,shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth().heightIn(min=42.dp)){Text("ИИ · Gemini")}
            HorizontalDivider(thickness=0.5.dp,color=MaterialTheme.colorScheme.outlineVariant)
            Text("При запуске",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf("mail" to "Почта","calendar" to "Календарь").forEach {(route,title)->
                    FilterChip(selected=s.options.optString("startScreen","mail")==route,onClick={model.change("startScreen",route)},enabled=s.ready&&!s.busy,shape=RoundedCornerShape(10.dp),label={Text(title)})
                }
            }
            Text("Выбор применяется при новом запуске. Открытое письмо, восстановленный экран и переход из уведомления имеют приоритет.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Иконка приложения · общая для всех ящиков",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf("spbu" to "СПбГУ","classic" to "Конверт").forEach {(icon,title)->
                    FilterChip(selected=s.icon==icon,onClick={model.icon(icon)},enabled=s.ready&&!s.busy,shape=RoundedCornerShape(10.dp),label={Text(title)})
                }
            }
            Text("Оформление",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf("system" to "Как в системе","light" to "Светлая","dark" to "Тёмная").forEach {(mode,title)->
                    FilterChip(selected=s.options.optString("theme","system")==mode,onClick={model.change("theme",mode)},enabled=s.ready&&!s.busy,shape=RoundedCornerShape(10.dp),label={Text(title)})
                }
            }
            @Composable fun option(key:String,title:String,default:Boolean=false){
                Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
                    Text(title,Modifier.weight(1f));Switch(s.options.optBoolean(key,default),{model.change(key,it)},enabled=s.ready&&!s.busy)
                }
            }
            option("large","Крупнее текст")
            option("compact","Компактный список писем")
            option("mailThreads","Объединять письма в цепочки",true)
            option("showTasks","Раздел «Задачи»",true)
            Text("Задачи и события в календаре сохраняются независимо от видимости раздела.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            option("offlineMail","Открывать сохранённую почту",true)
            Text("Отключение офлайн-доступа не удаляет сохранённые письма. Вложения скачиваются отдельно при подключении.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Офлайн-почта · "+(s.cacheBytes?.let {java.text.NumberFormat.getIntegerInstance(java.util.Locale("ru")).format(it)+" байт"} ?: "размер недоступен"),style=MaterialTheme.typography.bodySmall,fontWeight=FontWeight.Medium)
            Text("Размер зашифрованного кэша этого ящика, включая служебные копии. Вложения и черновики сюда не входят.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick={model.confirmClear(true)},enabled=s.ready&&!s.busy,shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth().heightIn(min=42.dp)){Text("Удалить офлайн-копии")}
            HorizontalDivider(thickness=0.5.dp,color=MaterialTheme.colorScheme.outlineVariant)
            Text("Уведомления",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
            option("mailNotifications","Новые письма",true)
            option("backgroundMail","Частая фоновая проверка",true)
            option("taskNotifications","Задачи и напоминания",true)
            Text("Фоновая проверка использует постоянное уведомление. Частота зависит от ограничений Android; настройка не гарантирует доставку в реальном времени.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if(NoticeCenter.allowed(activity))"Android разрешает уведомления" else "Уведомления ограничены в Android",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)
            OutlinedButton(onClick={model.action {activity.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,activity.packageName))}},enabled=!s.busy,shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth().heightIn(min=42.dp)){Text("Уведомления в Android")}
            Text(backgroundStatus,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick={model.action {activity.testNotifications()}},enabled=!s.busy,shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth().heightIn(min=42.dp)){Text("Проверить уведомление")}
            OutlinedButton(onClick={model.action {activity.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))}},enabled=!s.busy,shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth().heightIn(min=42.dp)){Text("Ограничения фоновой работы")}
            HorizontalDivider(thickness=0.5.dp,color=MaterialTheme.colorScheme.outlineVariant)
            Text("Безопасность и приложение",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
            OutlinedButton(onClick={model.action {AppLock.settings(activity)}},enabled=!s.busy,shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth().heightIn(min=42.dp)){Text(if(AppLock.enabled(activity))"Блокировка приложения · включена" else "Блокировка приложения")}
            Text("Блокировка общая для всех ящиков. Нативные экраны защищены от снимков и превью недавних приложений.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick={model.action {AppUpdater.check(activity)}},enabled=!s.busy,shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth().heightIn(min=42.dp)){Text("Проверить обновление")}
            TextButton(onClick=model::load,enabled=!s.busy){Text("Перечитать настройки")}
        }
    }
    if(s.clearConfirm)AlertDialog(onDismissRequest={model.confirmClear(false)},title={Text("Удалить офлайн-почту?")},
        text={Text("Удалятся сохранённые письма только текущего ящика. Серверные письма, черновики, вложения, задачи и календари останутся. До следующей загрузки почта может быть недоступна без сети.")},
        confirmButton={TextButton(onClick=model::clearOffline,enabled=!s.busy){Text("Удалить копии")}},dismissButton={TextButton(onClick={model.confirmClear(false)},enabled=!s.busy){Text("Отмена")}})

}


