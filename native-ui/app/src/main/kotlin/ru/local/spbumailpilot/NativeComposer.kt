@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package ru.local.spbumailpilot

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import android.text.format.Formatter
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun NativeComposer(model: NativeComposerModel, fullClient: () -> Unit, pickFiles: () -> Unit) {
    val state=model.state
    val context=LocalContext.current
    var copies by rememberSaveable { mutableStateOf(false) }
    var confirmRetry by remember { mutableStateOf(false) }
    var confirmOriginalFiles by remember { mutableStateOf(false) }
    val unresolved=state.status == NativeDraft.DISPATCHING || state.status == NativeDraft.UNCERTAIN
    val editable=!state.conflicted && !state.serverDelete && !state.scheduleOpen && !state.aiOpen && !state.discardOpen && !state.writingOpen && !state.picking && !state.busy && state.remaining == 0 && state.status == NativeDraft.EDITING && state.from.isNotBlank() && state.accountValid
    if(state.aiOpen){NativeWritingAiScreen(model);return}
    BackHandler { if(state.serverDelete)model.cancelServerDelete() else if(state.discardOpen)model.cancelDiscard() else if(state.writingOpen)model.closeWritingTools() else if(model.recipients.state.request!=null)model.recipients.clear() else model.close() }
    Scaffold(modifier=Modifier.imePadding(), topBar={ TopAppBar(
        title={ Text(if(state.forwardTextOnly||state.forwardEml)"Пересылка" else "Черновик", fontWeight=FontWeight.SemiBold) },
        navigationIcon={ TextButton(onClick=model::close,enabled=!state.busy) { Text("Назад") } },
        actions={ TextButton(onClick=model::requestDiscard,enabled=editable) { Text("Удалить", color=if(editable) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) } }) },
        bottomBar={ Surface(tonalElevation=3.dp) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
                HorizontalDivider(thickness=0.5.dp, color=MaterialTheme.colorScheme.outlineVariant)
                Column(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp)) {
                    when {
                        state.conflicted -> {
                            Text("Это окно больше не может изменять черновик. Показанный текст можно скопировать; затем откройте нужный черновик из списка.", fontSize=13.sp, modifier=Modifier.padding(bottom=6.dp))
                            OutlinedButton(onClick=model::close,enabled=!state.busy,shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth()){Text("Закрыть")}
                        }
                        state.remaining > 0 -> {
                            Text("Отправим через ${state.remaining} с",fontSize=14.sp, modifier=Modifier.padding(bottom=6.dp))
                            OutlinedButton(onClick=model::cancelSend,shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth()) { Text("Отменить отправку") }
                        }
                        unresolved -> {
                            Text("Результат отправки неизвестен. Проверьте «Отправленные» и получателя перед повтором.",fontSize=13.sp, modifier=Modifier.padding(bottom=4.dp))
                            TextButton(onClick=fullClient,modifier=Modifier.fillMaxWidth()) { Text("Открыть почтовый клиент") }
                            OutlinedButton(onClick={confirmRetry=true},enabled=!state.busy,shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth()) { Text("Разрешить повтор после проверки") }
                        }
                        state.status == NativeDraft.SCHEDULING -> {
                            Text("Планирование требует сверки с очередью. Закройте и снова откройте черновик; отправка из него заблокирована.", fontSize=13.sp, modifier=Modifier.padding(bottom=6.dp))
                            OutlinedButton(onClick=model::close,enabled=!state.busy,shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth()){Text("Закрыть для сверки")}
                        }
                        state.status == NativeDraft.SCHEDULED -> {
                            Text("Письмо передано в очередь «По расписанию»", fontSize=13.sp, modifier=Modifier.padding(bottom=6.dp))
                            Button(onClick=model::close,enabled=!state.busy,shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth()){Text("Готово")}
                        }
                        state.status == NativeDraft.SENT -> {
                            Text("Письмо отправлено",color=MaterialTheme.colorScheme.primary, fontSize=13.sp, modifier=Modifier.padding(bottom=6.dp))
                            Button(onClick=model::close,enabled=!state.busy,shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth()) { Text("Готово") }
                        }
                        else -> {
                            Text(
                                if(state.importing) "Добавление файлов…" else if(state.busy) "Подготовка…" else if(state.saved) "Сохранено на телефоне" else "Сохранение…",
                                fontSize=11.sp,
                                color=MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier=Modifier.padding(bottom=4.dp)
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp), verticalAlignment=Alignment.CenterVertically) {
                                Button(
                                    onClick=model::send,
                                    enabled=editable&&!state.originalPending,
                                    shape=RoundedCornerShape(12.dp),
                                    modifier=Modifier.weight(1.25f).heightIn(min=44.dp)
                                ) {
                                    Text(if(state.busy) "Подождите…" else "Отправить")
                                }
                                OutlinedButton(
                                    onClick=model::openSchedule,
                                    enabled=editable&&!state.originalPending,
                                    shape=RoundedCornerShape(12.dp),
                                    modifier=Modifier.weight(1f).heightIn(min=44.dp)
                                ) {
                                    Text("По расписанию")
                                }
                            }
                        }
                    }
                }
            }
        } }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal=16.dp)) {
            if(state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(state.serverWorking){
                Text("Работа с серверной копией…",Modifier.padding(vertical=8.dp))
                if(state.serverTotal>0)Text("${Formatter.formatShortFileSize(context,state.serverBytes)} / ${Formatter.formatShortFileSize(context,state.serverTotal)}",fontSize=12.sp)
                TextButton(onClick=model::cancelServerTransfer){Text("Остановить соединение")}
            }
            val showCopyToggle = !copies && state.cc.isBlank() && state.bcc.isBlank()
            if(state.from.isNotBlank() || showCopyToggle) {
                Row(
                    Modifier.fillMaxWidth().padding(top=4.dp),
                    verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.SpaceBetween
                ) {
                    if(state.from.isNotBlank()) {
                        Text(
                            "От: ${state.from}",
                            fontSize=13.sp,
                            color=MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier=Modifier.weight(1f)
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    if(showCopyToggle) {
                        TextButton(
                            onClick={copies=true},
                            enabled=editable,
                            contentPadding=PaddingValues(horizontal=8.dp, vertical=0.dp),
                            modifier=Modifier.heightIn(min=32.dp)
                        ) {
                            Text("Копия и скрытая копия", fontSize=12.sp)
                        }
                    }
                }
            }
            state.error?.let { Text(it,Modifier.padding(vertical=6.dp),color=MaterialTheme.colorScheme.error,fontSize=13.sp) }
            state.notice?.let { Text(it,Modifier.padding(vertical=6.dp),fontSize=13.sp) }
            if(state.forwardTextOnly)Text(if(state.forwardFilesAdded)"Исходные вложения скопированы. Оформление и встроенные картинки не переносятся; удалённые вручную файлы не добавляются повторно." else "Пересылается текст без оформления и встроенных картинок. Исходные файлы добавляются отдельно.",Modifier.padding(vertical=6.dp),fontSize=13.sp)
            if(state.forwardEml)Text(if(state.originalPending)"Добавьте оригинал EML перед отправкой. Он содержит исходные заголовки, оформление и вложения; верхние Bcc/Resent-Bcc исключены." else "Оригинал EML прикреплён отдельным файлом. Комментарий выше него можно редактировать.",Modifier.padding(vertical=6.dp),fontSize=13.sp)
            if(state.originalFiles>0)OutlinedButton(onClick={confirmOriginalFiles=true},enabled=editable,shape=RoundedCornerShape(10.dp),modifier=Modifier.fillMaxWidth().padding(top=4.dp)){Text(if(state.forwardEml)"Добавить оригинал EML" else "Добавить вложения исходного письма · ${state.originalFiles}")}
            if(confirmOriginalFiles)AlertDialog(onDismissRequest={confirmOriginalFiles=false},title={Text(if(state.forwardEml)"Добавить оригинал EML?" else "Добавить исходные вложения?")},text={Text(if(state.forwardEml)"Будет скачано целое письмо до 100 МБ. Файл содержит исходные заголовки и все вложения. Верхние заголовки Bcc/Resent-Bcc будут исключены. Проверьте содержимое перед пересылкой. Нужны сеть и место на телефоне." else "Будут скачаны все ${state.originalFiles} файлов в этот черновик. Понадобятся сеть и место на телефоне. Исходное письмо не изменится.")},confirmButton={TextButton(onClick={confirmOriginalFiles=false;model.originalFiles()},enabled=editable){Text("Скачать и добавить")}},dismissButton={TextButton(onClick={confirmOriginalFiles=false}){Text("Отмена")}})
            RecipientField("Кому","to",state.to,editable,model)
            if(copies || state.cc.isNotBlank() || state.bcc.isNotBlank()) {
                RecipientField("Копия","cc",state.cc,editable,model)
                RecipientField("Скрытая копия","bcc",state.bcc,editable,model)
            }
            ComposeField("Тема",state.subject,editable,{model.edit("subject",it)},focus={model.recipients.clear()})
            Row(Modifier.fillMaxWidth().padding(top=8.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick=pickFiles,enabled=editable,shape=RoundedCornerShape(10.dp),contentPadding=PaddingValues(horizontal=10.dp,vertical=6.dp),modifier=Modifier.weight(1.2f).heightIn(min=38.dp)) {
                    Text("Прикрепить файл",fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                }
                OutlinedButton(onClick=model::showTemplates,enabled=editable,shape=RoundedCornerShape(10.dp),contentPadding=PaddingValues(horizontal=10.dp,vertical=6.dp),modifier=Modifier.weight(0.9f).heightIn(min=38.dp)) {
                    Text("Шаблоны",fontSize=12.sp,maxLines=1)
                }
                OutlinedButton(onClick=model::insertSignature,enabled=editable,shape=RoundedCornerShape(10.dp),contentPadding=PaddingValues(horizontal=10.dp,vertical=6.dp),modifier=Modifier.weight(0.9f).heightIn(min=38.dp)) {
                    Text("Подпись",fontSize=12.sp,maxLines=1)
                }
            }
            if(state.importing) {
                Text(state.transferName,Modifier.padding(top=8.dp))
                Text(Formatter.formatShortFileSize(context,state.transferBytes),fontSize=12.sp)
                TextButton(onClick=model::cancelImport) { Text("Отменить добавление") }
            }
            if(state.files.isNotEmpty()) Text("Вложения · ${state.files.size}/10",Modifier.padding(top=10.dp),fontSize=13.sp,fontWeight=FontWeight.Medium)
            state.files.forEach { file ->
                Surface(shape=RoundedCornerShape(10.dp),color=MaterialTheme.colorScheme.surface,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),modifier=Modifier.fillMaxWidth().padding(top=6.dp)) {
                    Column(Modifier.padding(10.dp)) {
                        Text(file.name,fontSize=13.sp,fontWeight=FontWeight.Medium)
                        Text(Formatter.formatShortFileSize(context,file.size),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick={model.removeFile(file.token)},enabled=editable) { Text("Убрать из письма") }
                    }
                }
            }
            if(state.aiEnabled)Row(Modifier.fillMaxWidth().padding(top=6.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick={model.openAi("check")},enabled=editable&&state.body.isNotBlank(),shape=RoundedCornerShape(10.dp),modifier=Modifier.weight(1f).heightIn(min=38.dp)){Text("Проверить текст",fontSize=12.sp)}
                OutlinedButton(onClick={model.openAi("reply")},enabled=editable,shape=RoundedCornerShape(10.dp),modifier=Modifier.weight(1f).heightIn(min=38.dp)){Text("Предложить ответ",fontSize=12.sp)}
            }
            ComposerBody(model,editable)
            if(state.status==NativeDraft.EDITING){
                Surface(
                    shape=RoundedCornerShape(12.dp),
                    color=MaterialTheme.colorScheme.surface,
                    border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),
                    modifier=Modifier.fillMaxWidth().padding(top=10.dp,bottom=12.dp)
                ) {
                    Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text(
                            when(state.serverBackup){
                                "saved"->"Серверная копия есть. Новые правки сохраняются на телефоне; для обновления копии загрузите их отдельно."
                                "uncertain"->"Серверная загрузка не подтверждена. Копия могла сохраниться; повторная явная загрузка сверит ту же ревизию."
                                else->"Копия черновика хранится только на телефоне."
                            },
                            fontSize=12.sp,
                            color=MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(onClick={model.serverCopy()},enabled=editable,shape=RoundedCornerShape(10.dp),modifier=Modifier.fillMaxWidth().heightIn(min=38.dp)){Text("Сохранить копию на сервере",fontSize=13.sp)}
                        if(state.serverReference)TextButton(onClick=model::requestServerDelete,enabled=editable,modifier=Modifier.fillMaxWidth()){Text("Удалить подтверждённую серверную копию")}
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
    if(state.serverDelete)AlertDialog(onDismissRequest=model::cancelServerDelete,title={Text("Удалить серверную копию?")},text={Text("Будет удалена только подтверждённая копия этого черновика на сервере. Текст и файлы на телефоне останутся. Неподтверждённая загрузка могла создать другую копию; её удаление не гарантируется.")},confirmButton={TextButton(onClick={model.serverCopy(true)}){Text("Удалить копию")}},dismissButton={TextButton(onClick=model::cancelServerDelete){Text("Отмена")}})
    if(state.scheduleOpen)AlertDialog(onDismissRequest=model::closeSchedule,title={Text("Отправить по расписанию")},text={
        Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text("Дата и время по Москве. Отправка выполняется с этого телефона при наличии сети; Android может её задержать.")
            WorkspaceDateTime("schedule-${state.draftId}",state.scheduleDay,state.scheduleTime,!state.busy,false,{field,value->
                if(field=="date")model.scheduleDate(value,state.scheduleTime) else model.scheduleDate(state.scheduleDay,value)
            },allowEmptyTime=false)
            state.error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        }
    },confirmButton={TextButton(onClick=model::schedule,enabled=!state.busy){Text("Запланировать")}},dismissButton={TextButton(onClick=model::closeSchedule,enabled=!state.busy){Text("Отмена")}})
    if(state.writingOpen) ModalBottomSheet(onDismissRequest=model::closeWritingTools,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.fillMaxWidth().heightIn(max=480.dp).verticalScroll(rememberScrollState()).padding(horizontal=16.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text("Шаблоны",style=MaterialTheme.typography.titleLarge)
                TextButton(onClick=model::closeWritingTools) {Text("Закрыть")}
            }
            when {
                state.writingLoading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                state.writingError!=null -> {Text(state.writingError,color=MaterialTheme.colorScheme.error);TextButton(onClick=model::showTemplates){Text("Повторить")}}
                state.templates.isEmpty() -> Text("Сохранённых шаблонов нет. Создание и настройка шаблонов пока доступны в прежнем почтовом клиенте.",Modifier.padding(vertical=16.dp))
                else -> state.templates.forEach { template ->
                    Surface(shape=RoundedCornerShape(10.dp),tonalElevation=2.dp,modifier=Modifier.fillMaxWidth().padding(vertical=4.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(template.title,style=MaterialTheme.typography.titleMedium)
                            Text(template.body,maxLines=3,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(top=4.dp))
                            TextButton(onClick={model.insertTemplate(template.id)}) {Text("Вставить")}
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    if(confirmRetry) AlertDialog(onDismissRequest={confirmRetry=false}, title={Text("Разрешить повторную отправку?")},
        text={Text("Предыдущая попытка могла дойти до получателя. Продолжайте, только если проверили результат: повтор может создать второе письмо.")},
        dismissButton={TextButton(onClick={confirmRetry=false}) {Text("Отмена")}},
        confirmButton={TextButton(onClick={confirmRetry=false;model.allowRetryAfterCheck()}) {Text("Я проверил, разрешить")}})
    if(state.discardOpen) AlertDialog(onDismissRequest=model::cancelDiscard,title={Text("Удалить этот черновик?")},
        text={Text("Текст и локальные копии вложений этого черновика Compose будут удалены с телефона. Восстановить их из приложения нельзя. Серверные черновики и очередь отправки сохранятся.")},
        dismissButton={TextButton(onClick=model::cancelDiscard) {Text("Оставить")}},
        confirmButton={TextButton(onClick=model::discard) {Text("Удалить черновик",color=MaterialTheme.colorScheme.error)}})
}
@Composable private fun ComposeField(label: String,value: String,editable: Boolean,change: (String)->Unit,email: Boolean=false,focus: ()->Unit={}) {
    OutlinedTextField(value=value,onValueChange=change,readOnly=!editable,label={Text(label)},singleLine=true,
        shape=RoundedCornerShape(12.dp),
        keyboardOptions=KeyboardOptions(keyboardType=if(email) KeyboardType.Email else KeyboardType.Text),
        modifier=Modifier.onFocusChanged { if(it.isFocused)focus() }.fillMaxWidth().padding(top=4.dp))
}

@Composable private fun RecipientField(label: String,field: String,value: String,editable: Boolean,model: NativeComposerModel) {
    var input by remember(field) { mutableStateOf(TextFieldValue(value,TextRange(value.length))) }
    LaunchedEffect(value) { if(input.text!=value)input=TextFieldValue(value,TextRange(value.length)) }
    OutlinedTextField(value=input,onValueChange={ next ->
        if(next.text.length<=4000&&!next.text.contains('\r')&&!next.text.contains('\n')) {
            input=next
            if(next.text!=value)model.edit(field,next.text)
            model.recipientInput(field,next.text,if(next.selection.collapsed)next.selection.end else -1,next.composition!=null)
        }
    },readOnly=!editable,label={Text(label)},singleLine=true,shape=RoundedCornerShape(12.dp),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email),
        modifier=Modifier.fillMaxWidth().padding(top=4.dp).onFocusChanged { focus ->
            if(focus.isFocused&&editable)model.recipientInput(field,input.text,if(input.selection.collapsed)input.selection.end else -1,input.composition!=null)
        })
    val suggestions=model.recipients.state
    if(suggestions.request?.field==field&&editable) Surface(shape=RoundedCornerShape(12.dp),tonalElevation=3.dp,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),modifier=Modifier.fillMaxWidth().padding(top=4.dp)) {
        Column(Modifier.heightIn(max=260.dp).verticalScroll(rememberScrollState()).padding(8.dp)) {
            if(suggestions.loading) Text("Поиск получателей…",Modifier.semantics { liveRegion=LiveRegionMode.Polite },fontSize=12.sp)
            suggestions.choices.forEach { person ->
                TextButton(onClick={model.chooseRecipient(person.email)},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                    Column(Modifier.fillMaxWidth()) {
                        if(person.name.isNotBlank()&&!person.name.equals(person.email,true))Text(person.name,maxLines=1,overflow=TextOverflow.Ellipsis)
                        Text(person.email,maxLines=1,overflow=TextOverflow.Ellipsis)
                        Text(if(person.origin=="directory")"Справочник СПбГУ" else "Контакты на телефоне",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            suggestions.message?.let { Text(it,Modifier.semantics { liveRegion=LiveRegionMode.Polite },fontSize=12.sp) }
            if(suggestions.retry)TextButton(onClick={model.recipients.retry()}) {Text("Повторить поиск")}
        }
    }
}

@Composable private fun ComposerBody(model: NativeComposerModel,editable: Boolean) {
    val state=model.state
    val focus=remember {FocusRequester()}
    var appliedInsertion by rememberSaveable(state.draftId) {mutableStateOf(state.insertion)}
    var input by remember(state.draftId) {mutableStateOf(TextFieldValue(state.body,TextRange(state.bodyStart,state.bodyEnd)))}
    LaunchedEffect(state.body,state.insertion) {
        val inserted=state.insertion!=appliedInsertion
        if(input.text!=state.body||inserted)input=TextFieldValue(state.body,TextRange(state.bodyStart,state.bodyEnd))
        if(inserted){appliedInsertion=state.insertion;focus.requestFocus()}
    }
    OutlinedTextField(value=input,onValueChange={ next ->
        if(next.text.length<=100000){input=next;model.bodyInput(next.text,next.selection.start,next.selection.end,next.composition!=null)}
    },readOnly=!editable,label={Text("Текст письма")},shape=RoundedCornerShape(14.dp),
        modifier=Modifier.focusRequester(focus).onFocusChanged {if(it.isFocused)model.recipients.clear()}.fillMaxWidth().heightIn(min=160.dp).padding(top=8.dp),minLines=6)
}
