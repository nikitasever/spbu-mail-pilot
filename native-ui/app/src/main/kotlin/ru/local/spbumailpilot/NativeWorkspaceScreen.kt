package ru.local.spbumailpilot

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NativeWorkspaceScreen(
    model: NativeWorkspaceModel,
    openCalendarDay: (String) -> Unit = {},
    openMail: (String) -> Unit = {},
    accountEmail: String = "",
    onSwitchAccount: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {}
) {
    val state = model.state
    val editor = state.editor
    BackHandler { model.back() }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = model::back, enabled = !state.busy) {
                        Icon(
                            NativeIcons.ArrowBack,
                            contentDescription = "Назад",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            if (editor != null) when (state.kind) {
                                "tasks" -> "Задача"
                                "events" -> "Своё событие"
                                else -> "Заметка"
                            } else if (state.eventRoute) "Своё событие" else "Задачи и заметки",
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (editor == null && accountEmail.isNotBlank()) {
                            Text(
                                "$accountEmail ▾",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp).clickable(onClick = onSwitchAccount)
                            )
                        }
                    }
                },
                actions = {
                    if (editor == null) {
                        IconButton(onClick = model::load, enabled = !state.busy) {
                            Icon(
                                NativeIcons.Refresh,
                                contentDescription = "Обновить",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth()) {
                Surface(tonalElevation = 2.dp) {
                    Column(Modifier.fillMaxWidth()) {
                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (editor == null && !state.eventRoute && state.taskMail == null && state.eventMail == null) {
                                Button(
                                    onClick = { model.edit() },
                                    enabled = !state.busy,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
                                ) {
                                    Icon(NativeIcons.Plus, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        if (state.kind == "tasks") "Добавить задачу" else "Добавить заметку",
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            } else if (editor != null) {
                                Button(
                                    onClick = model::save,
                                    enabled = !state.busy,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.weight(1f).heightIn(min = 46.dp)
                                ) { Text("Сохранить", fontWeight = FontWeight.SemiBold) }
                                if (state.expected != null) {
                                    OutlinedButton(
                                        onClick = model::requestDelete,
                                        enabled = !state.busy,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.heightIn(min = 46.dp)
                                    ) {
                                        Icon(
                                            NativeIcons.Trash,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text("Удалить", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
                if (editor == null && !state.eventRoute && state.taskMail == null && state.eventMail == null) {
                    bottomBar()
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            state.error?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            state.notice?.let { Text(it, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary) }
            if (editor != null) {
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    fun value(key: String) = editor.optString(key)
                    val mailSource = state.eventMail ?: editor.optJSONObject("mail")
                    val calSource = editor.optJSONObject("calendarSource")
                    if (mailSource != null || calSource != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                mailSource?.let { source ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            NativeIcons.Mail,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            "Исходное письмо: " + source.optString("subject").ifBlank { "Без темы" },
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                calSource?.let { source ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            NativeIcons.Calendar,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            "Из события календаря: " + source.optString("calendarTitle", "Календарь"),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value("title"),
                        { model.field("title", it) },
                        label = { Text("Название") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.busy
                    )
                    if (state.kind != "notes") {
                        WorkspaceDateTime(
                            editor.optString("id"),
                            value("date"),
                            value("time"),
                            !state.busy,
                            state.kind == "tasks",
                            model::field
                        )
                        if (state.kind == "events" && state.expected != null) {
                            OutlinedButton(
                                onClick = { openCalendarDay(value("date")) },
                                enabled = !state.busy && SharedStateMerge.same(editor, state.expected),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(NativeIcons.Calendar, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Показать в календаре")
                            }
                        }
                        if (state.kind == "tasks") {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                WorkspaceChoice(
                                    "Приоритет",
                                    value("priority").ifBlank { "normal" },
                                    listOf("normal" to "Обычный", "important" to "Важно", "urgent" to "Срочно"),
                                    !state.busy,
                                    Modifier.weight(0.9f)
                                ) { model.field("priority", it) }
                                WorkspaceChoice(
                                    "Напоминание",
                                    value("reminder").ifBlank { "auto" },
                                    listOf(
                                        "auto" to "По умолчанию",
                                        "15" to "За 15 минут",
                                        "30" to "За 30 минут",
                                        "60" to "За час",
                                        "1440" to "За день",
                                        "none" to "Не напоминать"
                                    ),
                                    !state.busy,
                                    Modifier.weight(1.1f)
                                ) { model.field("reminder", it) }
                            }
                        }
                    }
                    if (state.kind != "events") {
                        val body = if (state.kind == "tasks") "notes" else "body"
                        OutlinedTextField(
                            value(body),
                            { model.field(body, it) },
                            label = { Text(if (body == "notes") "Комментарий" else "Текст") },
                            minLines = 6,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.busy
                        )
                    }
                }
            } else if (!state.eventRoute && state.taskMail == null && state.eventMail == null) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        listOf("tasks" to "Задачи", "notes" to "Заметки").forEach { (kindKey, label) ->
                            val active = state.kind == kindKey
                            Surface(
                                onClick = { if (!state.busy && (kindKey != "tasks" || state.tasksEnabled)) model.kind(kindKey) },
                                modifier = Modifier.weight(1f).height(34.dp),
                                shape = RoundedCornerShape(9.dp),
                                color = if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        label,
                                        fontSize = 13.sp,
                                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                OutlinedTextField(
                    state.query,
                    model::query,
                    placeholder = { Text("Поиск в записях", fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(
                            NativeIcons.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                )
                if (state.kind == "tasks") {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("active" to "В работе", "done" to "Готово", "all" to "Все").forEach { (value, label) ->
                            FilterChip(
                                selected = state.filter == value,
                                onClick = { model.filter(value) },
                                shape = RoundedCornerShape(10.dp),
                                label = { Text(label, fontSize = 12.sp) }
                            )
                        }
                    }
                }
                val needle = state.query.trim().lowercase(Locale.ROOT)
                val visible = state.rows.filter { row ->
                    (state.kind != "tasks" || state.filter == "all" || row.optBoolean("done") == (state.filter == "done")) &&
                        listOf("title", "body", "notes").joinToString(" ") { row.optString(it) }
                            .lowercase(Locale.ROOT).contains(needle)
                }.sortedWith(
                    compareBy(
                        {
                            if (state.kind == "notes") 0
                            else when (it.optString("priority")) {
                                "urgent" -> 0
                                "important" -> 1
                                else -> 2
                            }
                        },
                        { it.optString("date").ifBlank { "9999" } }
                    )
                )
                LazyColumn(Modifier.weight(1f)) {
                    if (visible.isEmpty() && !state.busy) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    "Здесь пока нет записей по выбранным условиям.",
                                    Modifier.padding(16.dp),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    items(visible, key = { it.optString("id") }) { row ->
                        val priority = row.optString("priority")
                        val isDone = row.optBoolean("done")
                        Surface(
                            onClick = { if (!state.busy) model.edit(row.optString("id")) },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                if (state.kind == "tasks") {
                                    Surface(
                                        onClick = { if (!state.busy) model.done(row.optString("id")) },
                                        shape = CircleShape,
                                        color = if (isDone) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        border = BorderStroke(
                                            1.8.dp,
                                            if (isDone) MaterialTheme.colorScheme.primary
                                            else when (priority) {
                                                "urgent" -> MaterialTheme.colorScheme.error
                                                "important" -> MaterialTheme.colorScheme.primary
                                                else -> MaterialTheme.colorScheme.outline
                                            }
                                        ),
                                        modifier = Modifier.padding(top = 2.dp).size(22.dp)
                                    ) {
                                        if (isDone) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    "✓",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimary
                                                )
                                            }
                                        }
                                    }
                                }
                                Column(
                                    Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text(
                                            row.optString("title"),
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant
                                            else MaterialTheme.colorScheme.onSurface,
                                            textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (state.kind == "tasks" && (priority == "urgent" || priority == "important")) {
                                            val isUrgent = priority == "urgent"
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (isUrgent) MaterialTheme.colorScheme.errorContainer
                                                else MaterialTheme.colorScheme.primaryContainer,
                                                modifier = Modifier.padding(start = 8.dp)
                                            ) {
                                                Text(
                                                    if (isUrgent) "Срочно" else "Важно",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (isUrgent) MaterialTheme.colorScheme.error
                                                    else MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    if (state.kind == "tasks") {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.background
                                            ) {
                                                Row(
                                                    Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        NativeIcons.Clock,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(12.dp),
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Text(
                                                        (row.optString("date") + " " + row.optString("time")).trim()
                                                            .ifBlank { "Без срока" },
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            row.optJSONObject("calendarSource")?.let { source ->
                                                Text(
                                                    source.optString("calendarTitle", "Календарь"),
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                    val text = row.optString(if (state.kind == "tasks") "notes" else "body")
                                    if (text.isNotBlank()) {
                                        Text(
                                            text,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (state.kind == "tasks" && row.optJSONObject("mail") != null) {
                                        Surface(
                                            onClick = { if (!state.busy) openMail(row.optString("id")) },
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                        ) {
                                            Row(
                                                Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                                            ) {
                                                Icon(
                                                    NativeIcons.Mail,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(12.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    "Открыть исходное письмо",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(12.dp)) }
                }
            }
        }
    }
    if (state.deleteOpen) {
        AlertDialog(
            onDismissRequest = model::cancelDelete,
            title = { Text("Удалить запись?") },
            text = { Text("Запись будет удалена с телефона. Письма и общие события календаря останутся.") },
            confirmButton = {
                TextButton(onClick = model::delete) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = model::cancelDelete) { Text("Оставить") }
            }
        )
    }
    if (state.leaveOpen) {
        AlertDialog(
            onDismissRequest = { model.leave(false) },
            title = { Text("Закрыть без сохранения?") },
            text = { Text("Изменения этой формы не будут сохранены.") },
            confirmButton = {
                TextButton(onClick = { model.leave(true) }) { Text("Закрыть") }
            },
            dismissButton = {
                TextButton(onClick = { model.leave(false) }) { Text("Продолжить") }
            }
        )
    }
}

@Composable
private fun WorkspaceChoice(
    title: String,
    value: String,
    options: List<Pair<String, String>>,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    select: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp)
        ) {
            Text(
                "$title: ${options.find { it.first == value }?.second ?: value}",
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (key, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        expanded = false
                        select(key)
                    }
                )
            }
        }
    }
}

