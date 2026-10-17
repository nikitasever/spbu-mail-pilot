@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package ru.local.spbumailpilot

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
    val state = model.state
    val context = LocalContext.current
    var copies by rememberSaveable { mutableStateOf(false) }
    var confirmRetry by remember { mutableStateOf(false) }
    var confirmOriginalFiles by remember { mutableStateOf(false) }
    val unresolved = state.status == NativeDraft.DISPATCHING || state.status == NativeDraft.UNCERTAIN
    val editable = !state.conflicted && !state.serverDelete && !state.scheduleOpen && !state.aiOpen && !state.discardOpen && !state.writingOpen && !state.picking && !state.busy && state.remaining == 0 && state.status == NativeDraft.EDITING && state.from.isNotBlank() && state.accountValid
    if (state.aiOpen) { NativeWritingAiScreen(model); return }
    BackHandler {
        if (state.serverDelete) model.cancelServerDelete()
        else if (state.discardOpen) model.cancelDiscard()
        else if (state.writingOpen) model.closeWritingTools()
        else if (model.recipients.state.request != null) model.recipients.clear()
        else model.close()
    }
    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = Color(0xFF0B0C10),
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF0B0C10),
                        titleContentColor = Color(0xFFF3F4F8)
                    ),
                    navigationIcon = {
                        IconButton(onClick = model::close, enabled = !state.busy) {
                            Icon(NativeIcons.ArrowBack, contentDescription = "Назад", tint = Color(0xFFF3F4F8), modifier = Modifier.size(22.dp))
                        }
                    },
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                if (state.forwardTextOnly || state.forwardEml) "Пересылка" else "Новое письмо",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 17.sp,
                                color = Color(0xFFF3F4F8)
                            )
                            Surface(
                                shape = CircleShape,
                                color = if (state.saved) Color(0xFF34D399) else Color(0xFF8B5CF6),
                                modifier = Modifier.size(6.dp)
                            ) {}
                        }
                    },
                    actions = {
                        IconButton(onClick = model::requestDiscard, enabled = editable) {
                            Icon(
                                NativeIcons.Trash,
                                contentDescription = "Удалить черновик",
                                tint = if (editable) Color(0xFF8E929F) else Color(0xFF4B5060),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        Surface(
                            onClick = model::send,
                            enabled = editable && !state.originalPending,
                            shape = CircleShape,
                            color = if (editable && !state.originalPending) Color(0xFF7C5CFF) else Color(0xFF1C1F2A),
                            shadowElevation = if (editable && !state.originalPending) 8.dp else 0.dp,
                            modifier = Modifier.padding(end = 8.dp).size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    NativeIcons.Send,
                                    contentDescription = "Отправить",
                                    tint = if (editable && !state.originalPending) Color.White else Color(0xFF5A5F72),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                )
                HorizontalDivider(thickness = 0.5.dp, color = Color(0x0FFFFFFF))
            }
        },
        bottomBar = {
            Surface(color = Color(0xFF14161D), tonalElevation = 0.dp) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
                    HorizontalDivider(thickness = 0.5.dp, color = Color(0x0FFFFFFF))
                    when {
                        state.conflicted -> {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                                Text("Черновик изменён в другом окне.", fontSize = 13.sp, color = Color(0xFF8E929F), modifier = Modifier.padding(bottom = 6.dp))
                                Button(onClick = model::close, enabled = !state.busy, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) { Text("Закрыть") }
                            }
                        }
                        state.remaining > 0 -> {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Отправка через ${state.remaining} с…", fontSize = 14.sp, color = Color(0xFFF3F4F8))
                                TextButton(onClick = model::cancelSend) { Text("Отменить", color = Color(0xFF8B5CF6), fontWeight = FontWeight.SemiBold) }
                            }
                        }
                        unresolved -> {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Статус отправки требует проверки.", fontSize = 13.sp, color = Color(0xFF8E929F))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = fullClient, modifier = Modifier.weight(1f)) { Text("Проверить") }
                                    Button(onClick = { confirmRetry = true }, enabled = !state.busy, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)) { Text("Повторить") }
                                }
                            }
                        }
                        state.status == NativeDraft.SCHEDULING -> {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                                Button(onClick = model::close, enabled = !state.busy, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) { Text("Закрыть для сверки") }
                            }
                        }
                        state.status == NativeDraft.SCHEDULED -> {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Запланировано к отправке", fontSize = 13.sp, color = Color(0xFF34D399))
                                Button(onClick = model::close, enabled = !state.busy, shape = RoundedCornerShape(10.dp)) { Text("Готово") }
                            }
                        }
                        state.status == NativeDraft.SENT -> {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Письмо отправлено", color = Color(0xFF34D399), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Button(onClick = model::close, enabled = !state.busy, shape = RoundedCornerShape(10.dp)) { Text("Готово") }
                            }
                        }
                        else -> {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                GlassIconButton(
                                    icon = NativeIcons.Paperclip,
                                    contentDescription = "Прикрепить файл",
                                    onClick = pickFiles,
                                    enabled = editable,
                                    size = 38.dp
                                )
                                GlassIconButton(
                                    icon = NativeIcons.Template,
                                    contentDescription = "Шаблоны",
                                    onClick = model::showTemplates,
                                    enabled = editable,
                                    size = 38.dp
                                )
                                GlassIconButton(
                                    icon = NativeIcons.Signature,
                                    contentDescription = "Подпись",
                                    onClick = model::insertSignature,
                                    enabled = editable,
                                    size = 38.dp
                                )
                                GlassIconButton(
                                    icon = NativeIcons.Clock,
                                    contentDescription = "Отправить по расписанию",
                                    onClick = model::openSchedule,
                                    enabled = editable && !state.originalPending,
                                    size = 38.dp
                                )
                                if (state.status == NativeDraft.EDITING) {
                                    GlassIconButton(
                                        icon = NativeIcons.Cloud,
                                        contentDescription = "Сохранить копию на сервере",
                                        onClick = { model.serverCopy() },
                                        enabled = editable && !state.serverWorking,
                                        active = state.serverBackup == "saved",
                                        tint = if (state.serverBackup == "saved") Color(0xFF34D399) else Color(0xFFCBD5E1),
                                        size = 38.dp
                                    )
                                }
                                Spacer(Modifier.weight(1f))
                                AiParticleOrb(
                                    size = 38.dp,
                                    label = "AI",
                                    onClick = { if (editable && state.aiEnabled) model.openAi(if (state.body.isNotBlank()) "check" else "reply") }
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Color(0xFF7C5CFF))
            if (state.serverWorking) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        if (state.serverTotal > 0) "Синхронизация: ${Formatter.formatShortFileSize(context, state.serverBytes)} / ${Formatter.formatShortFileSize(context, state.serverTotal)}"
                        else "Синхронизация с сервером…",
                        fontSize = 12.sp,
                        color = Color(0xFF8E929F)
                    )
                    TextButton(onClick = model::cancelServerTransfer) { Text("Стоп", fontSize = 12.sp) }
                }
            }
            state.error?.let {
                Text(it, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
            state.notice?.let {
                Text(it, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), color = Color(0xFF8E929F), fontSize = 12.sp)
            }
            if (state.originalFiles > 0) {
                Surface(
                    onClick = { if (editable) confirmOriginalFiles = true },
                    enabled = editable,
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF1C1F2A),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(NativeIcons.Paperclip, contentDescription = null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(16.dp))
                        Text(
                            if (state.forwardEml) "Прикрепить оригинал .eml" else "Прикрепить исходные файлы (${state.originalFiles})",
                            fontSize = 13.sp,
                            color = Color(0xFFF3F4F8),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            if (confirmOriginalFiles) {
                AlertDialog(
                    onDismissRequest = { confirmOriginalFiles = false },
                    title = { Text(if (state.forwardEml) "Добавить оригинал EML?" else "Добавить исходные вложения?") },
                    text = { Text(if (state.forwardEml) "Будет скачано целое письмо до 100 МБ. Верхние заголовки Bcc/Resent-Bcc будут исключены." else "Будут скачаны все ${state.originalFiles} файлов в этот черновик.") },
                    confirmButton = { TextButton(onClick = { confirmOriginalFiles = false; model.originalFiles() }, enabled = editable) { Text("Скачать и добавить") } },
                    dismissButton = { TextButton(onClick = { confirmOriginalFiles = false }) { Text("Отмена") } }
                )
            }

            // Sender row (subtle, single line)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("От", modifier = Modifier.width(58.dp), fontSize = 13.sp, color = Color(0xFF8E929F))
                Text(
                    state.from,
                    fontSize = 13.sp,
                    color = Color(0xFF8E929F),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (!copies && state.cc.isBlank() && state.bcc.isBlank()) {
                    Surface(
                        onClick = { if (editable) copies = true },
                        enabled = editable,
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF1C1F2A)
                    ) {
                        Text(
                            "Копия / Скр.",
                            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            fontSize = 11.sp,
                            color = Color(0xFF8E929F)
                        )
                    }
                }
            }
            HorizontalDivider(thickness = 0.5.dp, color = Color(0x0FFFFFFF))

            // Borderless recipient & subject rows with hairline dividers
            RecipientField("Кому", "to", state.to, editable, model)
            HorizontalDivider(thickness = 0.5.dp, color = Color(0x0FFFFFFF))
            if (copies || state.cc.isNotBlank() || state.bcc.isNotBlank()) {
                RecipientField("Копия", "cc", state.cc, editable, model)
                HorizontalDivider(thickness = 0.5.dp, color = Color(0x0FFFFFFF))
                RecipientField("Скр.", "bcc", state.bcc, editable, model)
                HorizontalDivider(thickness = 0.5.dp, color = Color(0x0FFFFFFF))
            }
            ComposeField("Тема", state.subject, editable, { model.edit("subject", it) }, focus = { model.recipients.clear() })
            HorizontalDivider(thickness = 0.5.dp, color = Color(0x0FFFFFFF))

            if (state.importing) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${state.transferName} (${Formatter.formatShortFileSize(context, state.transferBytes)})", fontSize = 12.sp, color = Color(0xFF8E929F))
                    IconButton(onClick = model::cancelImport, modifier = Modifier.size(28.dp)) {
                        Icon(NativeIcons.Close, contentDescription = "Отменить добавление", tint = Color(0xFF8E929F), modifier = Modifier.size(16.dp))
                    }
                }
            }

            if (state.files.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.files.forEach { file ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF1C1F2A),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Row(
                                Modifier.padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(NativeIcons.Paperclip, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF8B5CF6))
                                Column(Modifier.widthIn(max = 180.dp)) {
                                    Text(file.name, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFFF3F4F8), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(Formatter.formatShortFileSize(context, file.size), fontSize = 10.sp, color = Color(0xFF8E929F))
                                }
                                IconButton(
                                    onClick = { model.removeFile(file.token) },
                                    enabled = editable,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(NativeIcons.Close, contentDescription = "Убрать из письма", modifier = Modifier.size(14.dp), tint = Color(0xFF8E929F))
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(thickness = 0.5.dp, color = Color(0x0FFFFFFF))
            }

            // Full-screen open space for email body
            ComposerBody(model, editable)
        }
    }
    if (state.serverDelete) AlertDialog(
        onDismissRequest = model::cancelServerDelete,
        title = { Text("Удалить серверную копию?") },
        text = { Text("Будет удалена только подтверждённая копия этого черновика на сервере. Текст и файлы на телефоне останутся.") },
        confirmButton = { TextButton(onClick = { model.serverCopy(true) }) { Text("Удалить копию") } },
        dismissButton = { TextButton(onClick = model::cancelServerDelete) { Text("Отмена") } }
    )
    if (state.scheduleOpen) AlertDialog(
        onDismissRequest = model::closeSchedule,
        title = { Text("Отправить по расписанию") },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WorkspaceDateTime("schedule-${state.draftId}", state.scheduleDay, state.scheduleTime, !state.busy, false, { field, value ->
                    if (field == "date") model.scheduleDate(value, state.scheduleTime) else model.scheduleDate(state.scheduleDay, value)
                }, allowEmptyTime = false)
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = model::schedule, enabled = !state.busy) { Text("Запланировать") } },
        dismissButton = { TextButton(onClick = model::closeSchedule, enabled = !state.busy) { Text("Отмена") } }
    )
    if (state.writingOpen) ModalBottomSheet(
        onDismissRequest = model::closeWritingTools,
        containerColor = Color(0xFF14161D),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Шаблоны", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = model::closeWritingTools) {
                    Icon(NativeIcons.Close, contentDescription = "Закрыть", tint = Color(0xFF8E929F))
                }
            }
            when {
                state.writingLoading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                state.writingError != null -> {
                    Text(state.writingError, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = model::showTemplates) { Text("Повторить") }
                }
                state.templates.isEmpty() -> Text("Нет сохранённых шаблонов", Modifier.padding(vertical = 16.dp), color = Color(0xFF8E929F))
                else -> state.templates.forEach { template ->
                    Surface(
                        onClick = { model.insertTemplate(template.id) },
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1C1F2A),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(template.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(template.body, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = Color(0xFF8E929F), modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (confirmRetry) AlertDialog(
        onDismissRequest = { confirmRetry = false },
        title = { Text("Разрешить повторную отправку?") },
        text = { Text("Предыдущая попытка могла дойти до получателя. Продолжайте, только если проверили результат.") },
        dismissButton = { TextButton(onClick = { confirmRetry = false }) { Text("Отмена") } },
        confirmButton = { TextButton(onClick = { confirmRetry = false; model.allowRetryAfterCheck() }) { Text("Разрешить") } }
    )
    if (state.discardOpen) AlertDialog(
        onDismissRequest = model::cancelDiscard,
        title = { Text("Удалить черновик?") },
        text = { Text("Черновик и прикреплённые к нему файлы будут удалены с устройства.") },
        dismissButton = { TextButton(onClick = model::cancelDiscard) { Text("Отмена") } },
        confirmButton = { TextButton(onClick = model::discard) { Text("Удалить", color = MaterialTheme.colorScheme.error) } }
    )
}

@Composable private fun ComposeField(
    label: String,
    value: String,
    editable: Boolean,
    change: (String) -> Unit,
    email: Boolean = false,
    focus: () -> Unit = {}
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.width(58.dp), fontSize = 13.sp, color = Color(0xFF8E929F))
        TextField(
            value = value,
            onValueChange = change,
            readOnly = !editable,
            placeholder = { Text(label, fontSize = 14.sp, color = Color(0xFF565B6E)) },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            ),
            keyboardOptions = KeyboardOptions(keyboardType = if (email) KeyboardType.Email else KeyboardType.Text),
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { if (it.isFocused) focus() }
        )
    }
}

@Composable private fun RecipientField(
    label: String,
    field: String,
    value: String,
    editable: Boolean,
    model: NativeComposerModel
) {
    var input by remember(field) { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    LaunchedEffect(value) { if (input.text != value) input = TextFieldValue(value, TextRange(value.length)) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.width(58.dp), fontSize = 13.sp, color = Color(0xFF8E929F))
        TextField(
            value = input,
            onValueChange = { next ->
                if (next.text.length <= 4000 && !next.text.contains('\r') && !next.text.contains('\n')) {
                    input = next
                    if (next.text != value) model.edit(field, next.text)
                    model.recipientInput(field, next.text, if (next.selection.collapsed) next.selection.end else -1, next.composition != null)
                }
            },
            readOnly = !editable,
            placeholder = { Text("Получатели", fontSize = 14.sp, color = Color(0xFF565B6E)) },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { focus ->
                    if (focus.isFocused && editable) model.recipientInput(field, input.text, if (input.selection.collapsed) input.selection.end else -1, input.composition != null)
                }
        )
    }
    val suggestions = model.recipients.state
    if (suggestions.request?.field == field && editable) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF14161D),
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()).padding(8.dp)) {
                if (suggestions.loading) Text("Поиск…", Modifier.semantics { liveRegion = LiveRegionMode.Polite }, fontSize = 12.sp, color = Color(0xFF8E929F))
                suggestions.choices.forEach { person ->
                    Surface(
                        onClick = { model.chooseRecipient(person.email) },
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Transparent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            GlassSphereAvatar(person.name.ifBlank { person.email }, size = 32.dp)
                            Column(Modifier.weight(1f)) {
                                if (person.name.isNotBlank() && !person.name.equals(person.email, true)) {
                                    Text(person.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(person.email, fontSize = 12.sp, color = Color(0xFF8E929F), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                suggestions.message?.let { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, fontSize = 12.sp, color = Color(0xFF8E929F)) }
                if (suggestions.retry) TextButton(onClick = { model.recipients.retry() }) { Text("Повторить") }
            }
        }
    }
}

@Composable private fun ComposerBody(model: NativeComposerModel, editable: Boolean) {
    val state = model.state
    val focus = remember { FocusRequester() }
    var appliedInsertion by rememberSaveable(state.draftId) { mutableStateOf(state.insertion) }
    var input by remember(state.draftId) { mutableStateOf(TextFieldValue(state.body, TextRange(state.bodyStart, state.bodyEnd))) }
    LaunchedEffect(state.body, state.insertion) {
        val inserted = state.insertion != appliedInsertion
        if (input.text != state.body || inserted) input = TextFieldValue(state.body, TextRange(state.bodyStart, state.bodyEnd))
        if (inserted) { appliedInsertion = state.insertion; focus.requestFocus() }
    }
    TextField(
        value = input,
        onValueChange = { next ->
            if (next.text.length <= 100000) {
                input = next
                model.bodyInput(next.text, next.selection.start, next.selection.end, next.composition != null)
            }
        },
        readOnly = !editable,
        placeholder = { Text("Напишите сообщение…", fontSize = 15.sp, color = Color(0xFF565B6E)) },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent
        ),
        modifier = Modifier
            .focusRequester(focus)
            .onFocusChanged { if (it.isFocused) model.recipients.clear() }
            .fillMaxWidth()
            .heightIn(min = 380.dp)
            .padding(horizontal = 4.dp),
        minLines = 14
    )
}
