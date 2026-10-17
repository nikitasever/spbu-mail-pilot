package ru.local.spbumailpilot

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun NativeContactsScreen(model: NativeContactsModel,write: (String)->Unit,fullClient: ()->Unit) {
    val state=model.state
    val fileLauncher=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->model.importFileFromUri(uri)}
    val contactsPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->model.systemPermissionResult(granted)}
    if(state.editor!=null){NativeContactEditor(model);return}
    if(state.detail!=null){NativeContactDetail(model,write);return}
    val clipboard=LocalClipboardManager.current
    val focus=androidx.compose.ui.platform.LocalFocusManager.current
    var books by remember {mutableStateOf(false)}
    var copied by remember(state.owner,state.book,state.applied) {mutableStateOf(false)}
    val alphabet=rememberLazyListState(initialFirstVisibleItemIndex=(NativeContacts.ALPHABET.indexOf(state.letter).takeIf {state.letter.isNotEmpty()} ?: 0).minus(1).coerceAtLeast(0))
    LaunchedEffect(state.letter){if(state.letter.isNotEmpty())alphabet.animateScrollToItem((NativeContacts.ALPHABET.indexOf(state.letter)-1).coerceAtLeast(0))}
    val names=mapOf("local" to "На телефоне","history" to "История получателей","personal" to "Личная книга","team" to "Подразделение","company" to "Университет")
    BackHandler(onBack=model::close)
    if(state.importOpen)NativeContactImportDialog(model,{model.pickImportFile {fileLauncher.launch(arrayOf("text/*","application/octet-stream"))}},{model.requestSystemContacts {contactsPermission.launch(android.Manifest.permission.READ_CONTACTS)}})
    if(state.clearHistoryOpen)AlertDialog(onDismissRequest=model::cancelHistory,title={Text("Очистить историю получателей?")},text={Text("Будет удалена вся история адресов этого аккаунта, включая скрытую текущим поиском. Сохранённые контакты и системная книга останутся.")},confirmButton={TextButton(onClick=model::clearHistory,enabled=!state.busy){Text("Очистить историю")}},dismissButton={TextButton(onClick=model::cancelHistory,enabled=!state.busy){Text("Отмена")}})
    Scaffold(topBar={TopAppBar(title={Column {
        Text("Контакты")
        if(state.account.isNotBlank())Text(state.account,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
    }},navigationIcon={TextButton(onClick=model::close){Text("Почта")}})},bottomBar={
        Surface(tonalElevation=2.dp){Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=16.dp)) {
            Button(onClick=model::create,enabled=!state.busy&&!state.importOpen,modifier=Modifier.fillMaxWidth()){Text("Добавить контакт")}
            OutlinedButton(onClick=model::startImport,enabled=!state.busy&&!state.importOpen,modifier=Modifier.fillMaxWidth()){Text("Импорт контактов")}
            TextButton(onClick=fullClient,enabled=!state.busy&&!state.importOpen,modifier=Modifier.fillMaxWidth()){Text("Все возможности почты")}
        }}
    }) {padding->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxWidth().padding(horizontal=16.dp)) {
                Box {
                    OutlinedButton(onClick={books=true},modifier=Modifier.fillMaxWidth()){Text("${names[state.book]} ⌄")}
                    DropdownMenu(expanded=books,onDismissRequest={books=false}) {
                        names.forEach {(book,label)->DropdownMenuItem(text={Text(label)},onClick={books=false;model.book(book)},enabled=book in listOf("local","history")||state.supported)}
                    }
                }
                OutlinedTextField(value=state.query,onValueChange=model::text,label={Text("Имя или адрес")},singleLine=true,modifier=Modifier.fillMaxWidth(),trailingIcon={if(state.query.isNotEmpty())TextButton(onClick={model.text("");model.search()},enabled=!state.busy){Text("Очистить")}})
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                    TextButton(onClick={focus.clearFocus();model.search()},enabled=!state.busy){Text(if(state.book in listOf("local","history"))"Найти на телефоне" else "Найти на сервере")}
                    if(state.book !in listOf("local","history"))TextButton(onClick={focus.clearFocus();model.load(true)},enabled=!state.busy){Text("Загрузить")}
                }
                Text(if(state.book=="local")"Сохранённые адреса и история получателей на этом телефоне." else if(state.book=="history")"Недавние получатели этого аккаунта на телефоне." else "Справочник СПбГУ · поиск и переход по букве выполняются на сервере.",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if(state.book=="history")TextButton(onClick=model::askClearHistory,enabled=!state.busy&&state.people.isNotEmpty()){Text("Очистить историю")}
            LazyRow(state=alphabet,modifier=Modifier.fillMaxWidth(),contentPadding=PaddingValues(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                items(NativeContacts.ALPHABET.toList(),key={it.toString()}) {letter->
                    FilterChip(selected=state.letter==letter.toString(),onClick={focus.clearFocus();model.letter(letter.toString())},label={Text(letter.toString())})
                }
            }
            if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let {Text(it,Modifier.padding(horizontal=16.dp,vertical=8.dp),color=MaterialTheme.colorScheme.error)}
            state.notice?.let {Text(it,Modifier.padding(horizontal=16.dp,vertical=8.dp),fontSize=13.sp)}
            if(copied)Text("Адрес скопирован",Modifier.padding(horizontal=16.dp),fontSize=12.sp)
            key(state.owner,state.book,state.applied) {
                LazyColumn(Modifier.weight(1f)) {
                    if(state.people.isEmpty()&&!state.busy&&state.error==null)item {Text(if(state.book !in listOf("local","history")&&state.applied.isEmpty())"Нажмите «Загрузить», введите запрос или выберите букву." else "Совпадений нет.",Modifier.padding(20.dp))}
                    items(state.people,key={it.email.lowercase(java.util.Locale.ROOT)}) {person->
                        Column(Modifier.fillMaxWidth().clickable(enabled=!state.busy){model.show(person.email)}.padding(horizontal=16.dp,vertical=10.dp)) {
                            Text(person.name,style=MaterialTheme.typography.titleSmall,maxLines=2,overflow=TextOverflow.Ellipsis)
                            if(person.name!=person.email)Text(person.email,fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick={model.person(person.email)?.let {write(it.email)}},enabled=!state.busy){Text("Написать")}
                                TextButton(onClick={model.person(person.email)?.let {clipboard.setText(AnnotatedString(it.email));copied=true}},enabled=!state.busy){Text("Копировать адрес")}
                            }
                        }
                        HorizontalDivider(Modifier.padding(horizontal=16.dp),thickness=.5.dp,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.07f))
                    }
                    if(state.people.isNotEmpty())item {Text("Загружено: ${state.people.size}",Modifier.padding(16.dp),fontSize=12.sp)}
                    if(state.more)item {OutlinedButton(onClick={model.load(true,more=true)},enabled=!state.busy,modifier=Modifier.fillMaxWidth().padding(16.dp)){Text("Показать ещё")}}
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun NativeContactDetail(model: NativeContactsModel,write: (String)->Unit) {
    val state=model.state
    val person=state.detail ?: return
    val clipboard=LocalClipboardManager.current
    val context=LocalContext.current
    var status by remember(state.owner,person.email){mutableStateOf<String?>(null)}
    BackHandler(onBack=model::close)
    Scaffold(topBar={TopAppBar(title={Text("Контакт")},navigationIcon={TextButton(onClick=model::close,enabled=!state.busy){Text("Назад")}})}) {padding->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(person.name,style=MaterialTheme.typography.headlineSmall)
            Text(person.email,style=MaterialTheme.typography.bodyLarge)
            if(person.number.isNotBlank())Text(person.number)
            Text(if(person.local)"Контакт на этом телефоне" else if(state.book=="local")"Из сохранённых адресов и истории получателей" else "Справочник университета",color=MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick={model.detail()?.let {write(it.email)}},enabled=!state.busy,modifier=Modifier.fillMaxWidth()){Text("Написать")}
            if(person.number.isNotBlank())OutlinedButton(onClick={
                model.detail()?.let {contact->
                    if(contact.number.length<=60&&contact.number.matches(Regex("[+0-9() .-]+"))) {
                        status=runCatching<String?> {context.startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL,android.net.Uri.fromParts("tel",contact.number,null)));null}.getOrElse {"Не удалось открыть набор номера."}
                    }else status="Этот номер нельзя передать в набор номера."
                }
            },enabled=!state.busy,modifier=Modifier.fillMaxWidth()){Text("Позвонить")}
            OutlinedButton(onClick={model.detail()?.let {clipboard.setText(AnnotatedString(it.email));status="Адрес скопирован."}},enabled=!state.busy,modifier=Modifier.fillMaxWidth()){Text("Копировать адрес")}
            OutlinedButton(onClick=model::edit,enabled=!state.busy,modifier=Modifier.fillMaxWidth()){Text(if(person.local)"Изменить" else "Сохранить на телефоне")}
            Text("Изменения сохраняются для текущего ящика на этом телефоне. Серверная адресная книга не изменяется.",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            state.error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
            status?.let {Text(it)}
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun NativeContactEditor(model: NativeContactsModel) {
    val state=model.state
    val editor=state.editor ?: return
    BackHandler(onBack=model::close)
    Scaffold(topBar={TopAppBar(title={Text(if(state.expected==null)"Новый контакт" else "Изменить контакт")},navigationIcon={TextButton(onClick=model::close,enabled=!state.busy){Text("Отмена")}})},bottomBar={
        Surface(tonalElevation=2.dp){Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp)) {
            Button(onClick=model::save,enabled=!state.busy&&!state.leaveOpen&&!state.deleteOpen,modifier=Modifier.fillMaxWidth()){Text("Сохранить на телефоне")}
            if(state.expected!=null)TextButton(onClick=model::askDelete,enabled=!state.busy,modifier=Modifier.fillMaxWidth()){Text("Удалить контакт",color=MaterialTheme.colorScheme.error)}
        }}
    }) {padding->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            for((key,label) in listOf("sender" to "Имя","email" to "Электронная почта","number" to "Телефон")) {
                OutlinedTextField(value=editor.optString(key),onValueChange={model.field(key,it)},label={Text(label)},singleLine=true,
                    enabled=!state.busy&&!state.leaveOpen&&!state.deleteOpen,modifier=Modifier.fillMaxWidth(),
                    keyboardOptions=KeyboardOptions(keyboardType=when(key){"email"->KeyboardType.Email;"number"->KeyboardType.Phone;else->KeyboardType.Text}))
            }
            Text("Сохранение на этом телефоне для текущего ящика. Серверная адресная книга не изменяется.",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        }
    }
    if(state.leaveOpen)AlertDialog(onDismissRequest={model.leave(false)},title={Text("Отменить изменения?")},text={Text("Введённые изменения не будут сохранены.")},
        confirmButton={TextButton(onClick={model.leave(true)}){Text("Не сохранять")}},dismissButton={TextButton(onClick={model.leave(false)}){Text("Продолжить редактирование")}})
    if(state.deleteOpen)AlertDialog(onDismissRequest=model::cancelDelete,title={Text("Удалить контакт?")},text={Text("${editor.optString("sender")} (${state.expected?.optString("email")}) будет удалён с этого телефона. Запись в справочнике не изменится.")},
        confirmButton={TextButton(onClick=model::delete){Text("Удалить",color=MaterialTheme.colorScheme.error)}},dismissButton={TextButton(onClick=model::cancelDelete){Text("Отмена")}})
}

@Composable private fun NativeContactImportDialog(model:NativeContactsModel,pickFile:()->Unit,readAndroid:()->Unit) {
    val state=model.state
    if(!state.importOpen)return
    if(state.leaveOpen) {
        AlertDialog(onDismissRequest={model.leave(false)},title={Text("Отменить импорт?")},text={Text(when(state.importSource){"file"->"Выбранный файл не будет импортирован.";"system"->"Контакты Android не будут импортированы.";else->"Вставленный текст не будет импортирован."})},
            confirmButton={TextButton(onClick={model.leave(true)}){Text("Не импортировать")}},dismissButton={TextButton(onClick={model.leave(false)}){Text("Продолжить")}})
        return
    }
    val preview=state.importPreview
    AlertDialog(onDismissRequest=model::close,title={Text("Импорт контактов")},text={
        Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(selected=state.importSource=="text",onClick={model.importSource("text")},label={Text("Текст")},modifier=Modifier.weight(1f),enabled=!state.busy&&!state.importPicking)
                FilterChip(selected=state.importSource=="system",onClick={model.importSource("system")},label={Text("Android")},modifier=Modifier.weight(1f),enabled=!state.busy&&!state.importPicking)
                FilterChip(selected=state.importSource=="file",onClick={model.importSource("file")},label={Text("Файл")},modifier=Modifier.weight(1f),enabled=!state.busy&&!state.importPicking)
            }
            if(state.importSource!="system")Text("Формат UTF-8: «адрес; имя; телефон», «Имя <адрес>» или «адрес». По одной записи в строке, без CSV-кавычек и vCard. Максимум 200 КиБ, 500 непустых строк.",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(state.importSource=="text") {
                OutlinedTextField(value=state.importText,onValueChange=model::importInput,label={Text("Вставьте текст")},enabled=!state.busy&&!state.importPicking,minLines=4,maxLines=10,modifier=Modifier.fillMaxWidth())
            }else if(state.importSource=="system"){
                Text("Импортируются адреса e-mail и первый телефон контакта. Несколько адресов одного человека показаны отдельно. До 5000 адресов; системная книга не изменяется.",style=MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick=readAndroid,enabled=!state.busy&&!state.importPicking,modifier=Modifier.fillMaxWidth()){Text("Прочитать контакты Android")}
            }else {
                OutlinedButton(onClick={pickFile()},enabled=!state.busy&&!state.importPicking,modifier=Modifier.fillMaxWidth()) {
                    Text(state.importFileName?.let { "Файл: $it" } ?: "Выбрать текстовый файл",maxLines=2,overflow=TextOverflow.Ellipsis)
                }
            }
            preview?.let {
                Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text("Готово к импорту:",style=MaterialTheme.typography.titleSmall)
                    Text("• Новых контактов: ${it.added}",fontSize=13.sp)
                    if(it.skipped>0) {
                        Text("• Пропущено (всего): ${it.skipped}",fontSize=13.sp)
                        if(it.duplicateCount>0) Text("  - Повторов в пакете: ${it.duplicateCount}",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        if(it.existingCount>0) Text("  - Уже сохранены на телефоне: ${it.existingCount}",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if(it.invalidLines.isNotEmpty())Text("Некорректных системных записей: ${it.invalidLines.size}. Они не будут импортированы.",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                    Text("Выбрано: ${state.importSelected.size}. Свободных мест: ${it.capacity}.",style=MaterialTheme.typography.bodySmall)
                    if(it.added==0)Text("Новых записей для импорта нет.",fontSize=12.sp)
                    if(state.importSelected.size>it.capacity)Text("Уменьшите выбор: предел 5000 сохранённых контактов.",color=MaterialTheme.colorScheme.error)
                    Row {TextButton(onClick={model.selectAllImport(true)},enabled=!state.busy&&!state.importPicking){Text("Выбрать все")};TextButton(onClick={model.selectAllImport(false)},enabled=!state.busy&&!state.importPicking){Text("Снять выбор")}}
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max=240.dp)){
                        items(it.candidates.size){index->
                            val candidate=it.candidates[index];val chosen=candidate.email.lowercase(java.util.Locale.ROOT) in state.importSelected
                            Row(Modifier.fillMaxWidth().selectable(selected=chosen&&candidate.isImportable,enabled=candidate.isImportable&&!state.busy&&!state.importPicking,role=Role.Checkbox,onClick={model.toggleImportSelection(candidate.email)}).padding(vertical=4.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
                                Checkbox(checked=chosen&&candidate.isImportable,onCheckedChange=null,enabled=candidate.isImportable&&!state.busy&&!state.importPicking)
                                Column(Modifier.weight(1f)){
                                    Text(candidate.name,maxLines=2,overflow=TextOverflow.Ellipsis)
                                    if(candidate.name!=candidate.email)Text(candidate.email,style=MaterialTheme.typography.bodySmall,maxLines=2,overflow=TextOverflow.Ellipsis)
                                    if(candidate.number.isNotBlank())Text("Телефон: ${candidate.number}",style=MaterialTheme.typography.bodySmall,maxLines=2,overflow=TextOverflow.Ellipsis)
                                    if(!candidate.isImportable)Text(if(candidate.alreadyExists)"Уже сохранён" else "Повтор",style=MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
            state.importError?.let {Text(it,color=MaterialTheme.colorScheme.error)}
            if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(state.importReading)TextButton(onClick=model::stopImportRead){Text("Остановить чтение")}
        }
    },confirmButton={
        if(preview==null) {
            TextButton(onClick=model::previewImport,enabled=!state.busy&&!state.importPicking&&(if(state.importSource=="text")state.importText.isNotBlank() else if(state.importSource=="file")state.importFileSelected else false)){Text("Предпросмотр")}
        } else {
            Button(onClick=model::confirmImport,enabled=!state.busy&&!state.importPicking&&state.importSelected.isNotEmpty()&&state.importSelected.size<=preview.capacity){Text("Импортировать (${state.importSelected.size})")}
        }
    },dismissButton={TextButton(onClick=model::close,enabled=!state.busy&&!state.importPicking){Text("Отмена")}})
}
