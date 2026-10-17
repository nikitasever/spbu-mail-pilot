package ru.local.spbumailpilot

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NativeCalendarScreen(
    model: NativeCalendarModel,
    fullClient: () -> Unit,
    editEvent: (String, String?) -> Unit = { _, _ -> },
    openMail: (String) -> Unit = {},
    createTask: (JSONObject) -> Unit = {}
) {
    val state = model.state
    val date = LocalDate.parse(state.day)
    val month = YearMonth.from(date)
    val ruLocale = Locale.forLanguageTag("ru")
    val label = DateTimeFormatter.ofPattern("d MMMM, EEEE", ruLocale)
    val monthTitle = month.format(DateTimeFormatter.ofPattern("LLLL yyyy", ruLocale))
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(ruLocale) else it.toString() }
    var searchOpen by remember { mutableStateOf(false) }
    val needle = state.searchQuery.trim().lowercase(Locale.ROOT)
    val matchesSearch: (CalendarEntry) -> Boolean = remember(needle) {
        { entry ->
            if (needle.isEmpty()) true
            else listOf("title", "location", "calendarTitle", "description", "notes")
                .any { entry.data.optString(it).lowercase(Locale.ROOT).contains(needle) }
        }
    }
    BackHandler(onBack = model::close)
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Календарь",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    TextButton(onClick = model::close, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text("Назад")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (searchOpen && state.searchQuery.isNotEmpty()) model.clearSearch()
                            searchOpen = !searchOpen
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) { Text("Поиск") }
                    TextButton(
                        onClick = { model.load(true) },
                        enabled = !state.busy,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) { Text("Обновить") }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 2.dp) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { editEvent(state.day, null) },
                            enabled = !state.busy,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                        ) { Text("Новое событие") }
                        OutlinedButton(
                            onClick = fullClient,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.heightIn(min = 44.dp)
                        ) { Text("Все возможности почты", fontSize = 12.sp) }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                if (searchOpen || state.searchQuery.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = model::search,
                            label = { Text("Поиск событий") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                        if (state.searchQuery.isNotEmpty()) {
                            TextButton(onClick = model::clearSearch) { Text("Сбросить") }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("day" to "День", "week" to "Неделя", "month" to "Месяц", "agenda" to "Повестка").forEach { (mode, text) ->
                        val selected = state.mode == mode
                        Surface(
                            onClick = { model.mode(mode) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.weight(1f).height(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
                                Text(
                                    text,
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.size(34.dp)
                    ) {
                        IconButton(
                            onClick = { model.shift(-1) },
                            modifier = Modifier.fillMaxSize().semantics { contentDescription = "Предыдущий период" }
                        ) {
                            Text("‹", fontSize = 20.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    Text(
                        if (state.mode == "day") date.format(label) else monthTitle,
                        Modifier.weight(1f).padding(horizontal = 4.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.size(34.dp)
                    ) {
                        IconButton(
                            onClick = { model.shift(1) },
                            modifier = Modifier.fillMaxSize().semantics { contentDescription = "Следующий период" }
                        ) {
                            Text("›", fontSize = 20.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    OutlinedButton(
                        onClick = model::today,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.heightIn(min = 34.dp)
                    ) { Text("Сегодня", fontSize = 12.sp) }
                    OutlinedButton(
                        onClick = model::openSources,
                        enabled = !state.busy,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.heightIn(min = 34.dp)
                    ) { Text("Источники", fontSize = 12.sp) }
                }
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
                state.actionNotice?.let { Text(it, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), fontSize = 12.sp) }
                if (state.mode == "week" || state.mode == "month") {
                    val start = if (state.mode == "week") NativeAgenda.first(state.day, "week")
                    else month.atDay(1).minusDays(month.atDay(1).dayOfWeek.value - 1L)
                    val cells = if (state.mode == "week") 7
                    else ((month.atDay(1).dayOfWeek.value - 1 + month.lengthOfMonth() + 6) / 7) * 7
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEach {
                            Text(
                                it,
                                Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    for (week in 0 until cells / 7) Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (index in 0..6) {
                            val day = start.plusDays((week * 7 + index).toLong())
                            val present = state.mode == "week" || YearMonth.from(day) == month
                            if (!present) Spacer(Modifier.weight(1f).height(56.dp)) else {
                                val entries = state.entries.filter { NativeAgenda.onDay(it.data, day.toString()) && matchesSearch(it) }
                                val selected = day == date
                                val background = when {
                                    selected -> MaterialTheme.colorScheme.primaryContainer
                                    index >= 5 -> MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                                    else -> MaterialTheme.colorScheme.background
                                }
                                Surface(
                                    onClick = { model.select(day.toString()) },
                                    modifier = Modifier.weight(1f).height(56.dp).semantics {
                                        contentDescription = day.format(label) + ", событий и задач: " + entries.size
                                    },
                                    color = background,
                                    border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(
                                        Modifier.fillMaxSize(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            day.dayOfMonth.toString(),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (entries.isNotEmpty()) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(top = 2.dp)
                                            ) {
                                                entries.map { it.data.optString("kind") }.distinct().forEach { kind ->
                                                    Surface(
                                                        shape = CircleShape,
                                                        color = calendarColor(kind),
                                                        modifier = Modifier.size(5.dp)
                                                    ) {}
                                                }
                                            }
                                            Text(
                                                entries.size.toString(),
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("remote" to "Сервер", "event" to "События", "task" to "Задачи").forEach { (kind, text) ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = calendarColor(kind),
                                modifier = Modifier.size(6.dp)
                            ) {}
                            Text(text, color = calendarColor(kind), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        state.notice?.let {
                            Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            "Время Москвы · Свои события редактируются на телефоне. Серверные события доступны для чтения.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            val days = if (state.mode == "agenda" || needle.isNotEmpty()) {
                generateSequence(month.atDay(1)) { it.plusDays(1) }.takeWhile { YearMonth.from(it) == month }.toList()
            } else {
                listOf(date)
            }
            days.forEach { day ->
                val entries = state.entries.filter { NativeAgenda.onDay(it.data, day.toString()) && matchesSearch(it) }
                if ((day == date && needle.isEmpty()) || entries.isNotEmpty()) {
                    item(key = "day:$day") {
                        Text(
                            day.format(label),
                            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (entries.isEmpty()) item(key = "empty:$day") {
                        Text(
                            if (state.busy) "Загружаем…" else "Нет событий и задач в доступных данных.",
                            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    entries.forEach { entry ->
                        item(key = "$day:${entry.key}") {
                            NativeCalendarCard(
                                entry = entry,
                                enabled = !state.busy,
                                links = remember(entry.key, state.links) {
                                    val all = JSONArray()
                                    state.links.forEach { all.put(it) }
                                    runCatching { NativeMailLinks.forEvent(entry.data, all) }.getOrDefault(JSONArray())
                                },
                                edit = { editEvent(entry.data.optString("date"), entry.data.optString("id")) },
                                openMail = openMail,
                                unlinkMail = { linkId -> model.requestUnlink(linkId) },
                                createTask = { createTask(entry.data) }
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    if (state.sourcesOpen) {
        AlertDialog(
            onDismissRequest = model::closeSources,
            title = { Text("Источники календарей") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.sources.isEmpty()) {
                        Text("Внешние календари пока не подключены.", fontSize = 13.sp)
                    } else {
                        state.sources.forEach { src ->
                            Surface(tonalElevation = 1.dp, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)) {
                                Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                    Text(src.title, style = MaterialTheme.typography.titleSmall)
                                    if (src.user.isNotBlank()) Text(src.user, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        if (src.error.isNotBlank()) "Ошибка: ${src.error}"
                                        else if (src.cached) "Синхронизирован (${src.syncedAt.ifBlank { "кэш" }})"
                                        else "Нет сохранённых данных",
                                        fontSize = 12.sp,
                                        color = if (src.error.isNotBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    TextButton(
                                        onClick = { model.requestRemoveSource(src.id) },
                                        enabled = !state.busy
                                    ) { Text("Отключить", color = MaterialTheme.colorScheme.error) }
                                }
                            }
                        }
                    }
                    Button(
                        onClick = model::openConnect,
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Подключить календарь") }
                }
            },
            confirmButton = {
                TextButton(onClick = model::closeSources) { Text("Закрыть") }
            }
        )
    }

    if (state.connectOpen) {
        AlertDialog(
            onDismissRequest = model::closeConnect,
            title = { Text("Подключить календарь") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("mailru" to "Mail.ru", "other" to "Другой CalDAV", "manual" to "По ссылке").forEach { (mode, text) ->
                            FilterChip(
                                selected = state.connectMode == mode,
                                onClick = { model.setConnectMode(mode) },
                                label = { Text(text) },
                                enabled = !state.busy
                            )
                        }
                    }
                    OutlinedTextField(
                        value = state.connectTitle,
                        onValueChange = { model.setConnectField("title", it) },
                        label = { Text("Название") },
                        singleLine = true,
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (state.connectMode != "mailru") {
                        OutlinedTextField(
                            value = state.connectUrl,
                            onValueChange = { model.setConnectField("url", it) },
                            label = { Text(if (state.connectMode == "manual") "Ссылка CalDAV или ICS (https://…)" else "Адрес сервера CalDAV (https://…)") },
                            singleLine = true,
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    OutlinedTextField(
                        value = state.connectUser,
                        onValueChange = { model.setConnectField("user", it) },
                        label = { Text(if (state.connectMode == "manual") "Логин (необязательно для ICS)" else "Логин / e-mail") },
                        singleLine = true,
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = state.connectPassword,
                        onValueChange = { model.setConnectField("password", it) },
                        label = { Text("Пароль приложения") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth()
                    )
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                }
            },
            confirmButton = {
                TextButton(onClick = model::submitConnect, enabled = !state.busy) { Text("Подключить") }
            },
            dismissButton = {
                TextButton(onClick = model::closeConnect, enabled = !state.busy) { Text("Отмена") }
            }
        )
    }

    if (state.discoveryPickerOpen) {
        AlertDialog(
            onDismissRequest = model::closeConnect,
            title = { Text("Выберите календарь") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.discoveredCalendars.forEach { (title, url) ->
                        OutlinedButton(
                            onClick = { model.chooseDiscovered(title, url) },
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = model::closeConnect, enabled = !state.busy) { Text("Отмена") }
            }
        )
    }

    if (state.deleteSourceId != null) {
        AlertDialog(
            onDismissRequest = model::cancelRemoveSource,
            title = { Text("Отключить календарь?") },
            text = { Text("Календарь и его сохранённый кэш на телефоне будут удалены. События на сервере не изменятся.") },
            confirmButton = {
                TextButton(onClick = model::confirmRemoveSource, enabled = !state.busy) {
                    Text("Отключить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = model::cancelRemoveSource, enabled = !state.busy) { Text("Оставить") }
            }
        )
    }

    if (state.unlinkLinkId != null) {
        AlertDialog(
            onDismissRequest = model::cancelUnlink,
            title = { Text("Отвязать письмо от события?") },
            text = { Text("Будет удалена только связь между письмом и событием. Само письмо и событие календаря сохранятся.") },
            confirmButton = {
                TextButton(onClick = model::confirmUnlink, enabled = !state.busy) {
                    Text("Отвязать", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = model::cancelUnlink, enabled = !state.busy) { Text("Оставить") }
            }
        )
    }
}

@Composable
internal fun NativeMailEventPickerDialog(model: NativeCalendarModel) {
    val state = model.state
    if (!state.pickerOpen) return
    val needle = state.pickerQuery.trim().lowercase(Locale.ROOT)
    val filtered = remember(state.pickerEntries, needle) {
        state.pickerEntries.filter { entry ->
            if (needle.isEmpty()) true
            else listOf("title", "date", "location", "calendarTitle")
                .any { entry.data.optString(it).lowercase(Locale.ROOT).contains(needle) }
        }
    }
    AlertDialog(
        onDismissRequest = model::closeMailEventPicker,
        title = { Text("Связать письмо с событием") },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.pickerMail?.optString("subject")?.takeIf { it.isNotBlank() }?.let { subject ->
                    Text("Письмо: $subject", fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.primary)
                }
                OutlinedTextField(
                    value = state.pickerQuery,
                    onValueChange = model::pickerSearch,
                    label = { Text("Поиск события") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { model.loadPickerEvents(false) }, enabled = !state.pickerBusy) { Text("Из кэша") }
                    TextButton(onClick = { model.loadPickerEvents(true) }, enabled = !state.pickerBusy) { Text("Обновить с сервера") }
                }
                if (state.pickerBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.pickerError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                if (!state.pickerBusy && filtered.isEmpty()) {
                    Text("Подходящих событий за текущий месяц не найдено.", fontSize = 13.sp)
                } else {
                    LazyColumn(Modifier.heightIn(max = 280.dp)) {
                        items(filtered, key = { it.key }) { entry ->
                            val data = entry.data
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(data.optString("title", "Без названия"), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        listOf(data.optString("date"), NativeAgenda.timeRange(data), data.optString("calendarTitle"))
                                            .filter { it.isNotBlank() }
                                            .joinToString(" · "),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(
                                    onClick = { model.confirmLinkMailToEvent(data) },
                                    enabled = !state.pickerBusy
                                ) { Text("Связать") }
                            }
                            HorizontalDivider(thickness = 0.5.dp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = model::closeMailEventPicker, enabled = !state.pickerBusy) { Text("Закрыть") }
        }
    )
}

@Composable
private fun calendarColor(kind: String): Color {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    return when (kind) {
        "remote" -> if (dark) Color(0xFF99C6F5) else Color(0xFF355E8E)
        "event" -> if (dark) Color(0xFF87D4B5) else Color(0xFF287052)
        else -> MaterialTheme.colorScheme.primary
    }
}

@Composable
private fun NativeCalendarCard(
    entry: CalendarEntry,
    enabled: Boolean,
    links: JSONArray,
    edit: () -> Unit,
    openMail: (String) -> Unit,
    unlinkMail: (String) -> Unit,
    createTask: () -> Unit
) {
    val data = entry.data
    val kind = data.optString("kind")
    var expanded by remember(entry.key) { mutableStateOf(false) }
    Surface(
        onClick = { expanded = !expanded },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp)) {
            Surface(
                shape = RoundedCornerShape(2.dp),
                color = calendarColor(kind),
                modifier = Modifier.width(4.dp).height(36.dp)
            ) {}
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    NativeAgenda.timeRange(data),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = calendarColor(kind)
                )
                Text(
                    data.optString("title", "Без названия"),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (data.optString("calendarTitle").isNotBlank()) {
                    Text(
                        data.optString("calendarTitle"),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (expanded) {
                    listOf("location", "description", "notes").forEach { field ->
                        if (data.optString(field).isNotBlank()) {
                            Text(data.optString(field), Modifier.padding(top = 8.dp), fontSize = 14.sp)
                        }
                    }
                }
                if (kind == "event" || kind == "remote") {
                    Row(
                        Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (kind == "event") {
                            OutlinedButton(
                                onClick = edit,
                                enabled = enabled,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.heightIn(min = 34.dp)
                            ) { Text("Изменить своё событие", fontSize = 12.sp) }
                        }
                        OutlinedButton(
                            onClick = createTask,
                            enabled = enabled,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.heightIn(min = 34.dp)
                        ) { Text("Создать задачу", fontSize = 12.sp) }
                    }
                }
                if (links.length() > 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
                            Text(
                                "Связанные письма · ${links.length()}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            for (index in 0 until links.length()) {
                                val link = links.getJSONObject(index)
                                val mail = link.optJSONObject("mail")
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = { openMail(link.toString()) },
                                        enabled = enabled,
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            mail?.optString("subject")?.ifBlank { "Открыть письмо" } ?: "Повреждённая ссылка",
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            fontSize = 12.sp
                                        )
                                    }
                                    TextButton(
                                        onClick = { unlinkMail(link.optString("id")) },
                                        enabled = enabled,
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("Отвязать", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
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
