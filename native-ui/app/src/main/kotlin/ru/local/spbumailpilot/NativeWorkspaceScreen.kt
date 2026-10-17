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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
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
        containerColor = Color(0xFF0B0C10),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0B0C10),
                    titleContentColor = Color(0xFFF3F4F6)
                ),
                navigationIcon = {
                    IconButton(onClick = model::back, enabled = !state.busy) {
                        Icon(
                            NativeIcons.ArrowBack,
                            contentDescription = "Назад",
                            tint = Color(0xFFF3F4F6)
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
                            fontSize = 18.sp,
                            color = Color(0xFFF3F4F6),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (editor == null && accountEmail.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier.padding(top = 1.dp).clickable(onClick = onSwitchAccount)
                            ) {
                                Text(
                                    accountEmail,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF8E929F),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(
                                    NativeIcons.ChevronDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = Color(0xFF8B5CF6)
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (editor == null) {
                        IconButton(onClick = model::load, enabled = !state.busy) {
                            Icon(
                                NativeIcons.Refresh,
                                contentDescription = "Обновить",
                                modifier = Modifier.size(20.dp),
                                tint = Color(0xFF8E929F)
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (editor == null && !state.eventRoute && state.taskMail == null && state.eventMail == null) {
                GlowingCircleFab(
                    icon = NativeIcons.Plus,
                    contentDescription = if (state.kind == "tasks") "Добавить задачу" else "Добавить заметку",
                    onClick = { model.edit() },
                    enabled = !state.busy
                )
            }
        },
        bottomBar = {
            if (editor != null) {
                Surface(color = Color(0xFF14161D)) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding()) {
                        HorizontalDivider(thickness = 0.5.dp, color = Color(0x0FFFFFFF))
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = model::save,
                                enabled = !state.busy,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                            ) { Text("Сохранить", fontWeight = FontWeight.SemiBold) }
                            if (state.expected != null) {
                                Surface(
                                    onClick = { if (!state.busy) model.requestDelete() },
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF1C1F2A),
                                    modifier = Modifier.heightIn(min = 44.dp)
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            NativeIcons.Trash,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                        Text("Удалить", color = MaterialTheme.colorScheme.error, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (!state.eventRoute && state.taskMail == null && state.eventMail == null) {
                bottomBar()
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.busy) {
                LinearProgressIndicator(
                    Modifier.fillMaxWidth().height(2.dp),
                    color = Color(0xFF7C5CFF),
                    trackColor = Color.Transparent
                )
            }
            state.error?.let {
                Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
            state.notice?.let {
                Text(it, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), fontSize = 12.sp, color = Color(0xFFA78BFA))
            }
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
                            color = Color(0xFF1C1F2A),
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
                                            tint = Color(0xFFA78BFA)
                                        )
                                        Text(
                                            "Исходное письмо: " + source.optString("subject").ifBlank { "Без темы" },
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFFA78BFA)
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
                                            tint = Color(0xFFA78BFA)
                                        )
                                        Text(
                                            "Из события календаря: " + source.optString("calendarTitle", "Календарь"),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFFA78BFA)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    TextField(
                        value = value("title"),
                        onValueChange = { model.field("title", it) },
                        label = { Text("Название", color = Color(0xFF8E929F)) },
                        shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF1C1F2A),
                            unfocusedContainerColor = Color(0xFF1C1F2A),
                            disabledContainerColor = Color(0xFF14161D),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        ),
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
                            Surface(
                                onClick = {
                                    if (!state.busy && SharedStateMerge.same(editor, state.expected)) {
                                        openCalendarDay(value("date"))
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF1C1F2A)
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(NativeIcons.Calendar, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color(0xFFA78BFA))
                                    Text("Показать в календаре", fontSize = 12.sp, color = Color(0xFFA78BFA), fontWeight = FontWeight.Medium)
                                }
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
                        TextField(
                            value = value(body),
                            onValueChange = { model.field(body, it) },
                            label = { Text(if (body == "notes") "Комментарий" else "Текст", color = Color(0xFF8E929F)) },
                            minLines = 6,
                            shape = RoundedCornerShape(12.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF1C1F2A),
                                unfocusedContainerColor = Color(0xFF1C1F2A),
                                disabledContainerColor = Color(0xFF14161D),
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.busy
                        )
                    }
                }
            } else if (!state.eventRoute && state.taskMail == null && state.eventMail == null) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF14161D)
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
                                color = if (active) Color(0xFF1C1F2A) else Color.Transparent
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        label,
                                        fontSize = 13.sp,
                                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                                        color = if (active) Color(0xFFA78BFA) else Color(0xFF8E929F)
                                    )
                                }
                            }
                        }
                    }
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF1C1F2A),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 42.dp).padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            NativeIcons.Search,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp),
                            tint = Color(0xFF8E929F)
                        )
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (state.query.isEmpty()) {
                                Text("Поиск в записях…", fontSize = 13.sp, color = Color(0xFF8E929F).copy(alpha = 0.7f))
                            }
                            BasicTextField(
                                value = state.query,
                                onValueChange = model::query,
                                singleLine = true,
                                textStyle = TextStyle(color = Color(0xFFF3F4F6), fontSize = 13.sp),
                                cursorBrush = SolidColor(Color(0xFF8B5CF6)),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                if (state.kind == "tasks") {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("active" to "В работе", "done" to "Готово", "all" to "Все").forEach { (value, label) ->
                            val selected = state.filter == value
                            Surface(
                                onClick = { model.filter(value) },
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) Color(0xFF7C5CFF).copy(alpha = 0.22f) else Color(0xFF1C1F2A)
                            ) {
                                Text(
                                    label,
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (selected) Color(0xFFA78BFA) else Color(0xFF8E929F),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
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
                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 84.dp)
                ) {
                    if (visible.isEmpty() && !state.busy) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFF14161D),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    "Здесь пока нет записей по выбранным условиям.",
                                    Modifier.padding(16.dp),
                                    fontSize = 13.sp,
                                    color = Color(0xFF8E929F)
                                )
                            }
                        }
                    }
                    items(visible, key = { it.optString("id") }) { row ->
                        val priority = row.optString("priority")
                        val isDone = row.optBoolean("done")
                        Surface(
                            onClick = { if (!state.busy) model.edit(row.optString("id")) },
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF14161D),
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
                                        color = if (isDone) Color(0xFF7C5CFF) else Color.Transparent,
                                        border = BorderStroke(
                                            1.5.dp,
                                            if (isDone) Color(0xFF7C5CFF)
                                            else when (priority) {
                                                "urgent" -> Color(0xFFEF4444)
                                                "important" -> Color(0xFF8B5CF6)
                                                else -> Color(0xFF8E929F).copy(alpha = 0.5f)
                                            }
                                        ),
                                        modifier = Modifier.padding(top = 2.dp).size(20.dp)
                                    ) {
                                        if (isDone) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    "✓",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
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
                                            color = if (isDone) Color(0xFF8E929F) else Color(0xFFF3F4F6),
                                            textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (state.kind == "tasks" && (priority == "urgent" || priority == "important")) {
                                            val isUrgent = priority == "urgent"
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (isUrgent) Color(0xFFEF4444).copy(alpha = 0.18f)
                                                else Color(0xFF7C5CFF).copy(alpha = 0.22f),
                                                modifier = Modifier.padding(start = 8.dp)
                                            ) {
                                                Text(
                                                    if (isUrgent) "Срочно" else "Важно",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (isUrgent) Color(0xFFF87171) else Color(0xFFA78BFA),
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
                                                color = Color(0xFF1C1F2A)
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
                                                        tint = Color(0xFF8E929F)
                                                    )
                                                    Text(
                                                        formatHumanDateTime(row.optString("date"), row.optString("time"))
                                                            .ifBlank { "Без срока" },
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = Color(0xFF8E929F)
                                                    )
                                                }
                                            }
                                            row.optJSONObject("calendarSource")?.let { source ->
                                                Text(
                                                    source.optString("calendarTitle", "Календарь"),
                                                    fontSize = 12.sp,
                                                    color = Color(0xFFA78BFA),
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
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            fontSize = 13.sp,
                                            color = Color(0xFF8E929F)
                                        )
                                    }
                                    if (state.kind == "tasks" && row.optJSONObject("mail") != null) {
                                        Surface(
                                            onClick = { if (!state.busy) openMail(row.optString("id")) },
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFF1C1F2A)
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
                                                    tint = Color(0xFFA78BFA)
                                                )
                                                Text(
                                                    "Открыть исходное письмо",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = Color(0xFFA78BFA)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
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
        Surface(
            onClick = { if (enabled) expanded = true },
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF1C1F2A),
            modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp)
        ) {
            Box(
                Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    "$title: ${options.find { it.first == value }?.second ?: value}",
                    fontSize = 12.sp,
                    color = Color(0xFFF3F4F6),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
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
