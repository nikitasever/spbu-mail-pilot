package ru.local.spbumailpilot

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
    val label = DateTimeFormatter.ofPattern("d MMMM, EEEE", Locale.forLanguageTag("ru"))
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
                title = { Text("Календарь") },
                navigationIcon = { TextButton(onClick = model::close) { Text("Назад") } },
                actions = {
                    TextButton(onClick = model::openSources, enabled = !state.busy) { Text("Источники") }
                    TextButton(onClick = {
                        if (searchOpen && state.searchQuery.isNotEmpty()) model.clearSearch()
                        searchOpen = !searchOpen
                    }) { Text("Поиск") }
                    TextButton(onClick = { model.load(true) }, enabled = !state.busy) { Text("Обновить") }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 2.dp) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
                    Button(
                        onClick = { editEvent(state.day, null) },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                    ) { Text("Новое событие") }
                    TextButton(onClick = fullClient, modifier = Modifier.fillMaxWidth()) { Text("Все возможности почты") }
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
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = model::search,
                            label = { Text("Поиск событий") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        if (state.searchQuery.isNotEmpty()) {
                            TextButton(onClick = model::clearSearch) { Text("Сбросить") }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("day" to "День", "week" to "Неделя", "month" to "Месяц", "agenda" to "Повестка").forEach { (mode, text) ->
                        FilterChip(selected = state.mode == mode, onClick = { model.mode(mode) }, label = { Text(text) })
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    IconButton(onClick = { model.shift(-1) }, modifier = Modifier.semantics { contentDescription = "Предыдущий период" }) {
                        Text("‹", fontSize = 24.sp)
                    }
                    Text(
                        if (state.mode == "day") date.format(label)
                        else month.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.forLanguageTag("ru"))),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall
                    )
                    IconButton(onClick = { model.shift(1) }, modifier = Modifier.semantics { contentDescription = "Следующий период" }) {
                        Text("›", fontSize = 24.sp)
                    }
                    TextButton(onClick = model::today) { Text("Сегодня") }
                }
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
                state.actionNotice?.let { Text(it, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), fontSize = 12.sp) }
                if (state.mode == "week" || state.mode == "month") {
                    val start = if (state.mode == "week") NativeAgenda.first(state.day, "week")
                    else month.atDay(1).minusDays(month.atDay(1).dayOfWeek.value - 1L)
                    val cells = if (state.mode == "week") 7
                    else ((month.atDay(1).dayOfWeek.value - 1 + month.lengthOfMonth() + 6) / 7) * 7
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                        listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEach {
                            Text(it, Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 11.sp)
                        }
                    }
                    for (week in 0 until cells / 7) Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                        for (index in 0..6) {
                            val day = start.plusDays((week * 7 + index).toLong())
                            val present = state.mode == "week" || YearMonth.from(day) == month
                            if (!present) Spacer(Modifier.weight(1f).height(64.dp)) else {
                                val entries = state.entries.filter { NativeAgenda.onDay(it.data, day.toString()) && matchesSearch(it) }
                                val selected = day == date
                                val background = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background
                                Surface(
                                    onClick = { model.select(day.toString()) },
                                    modifier = Modifier.weight(1f).height(64.dp).semantics {
                                        contentDescription = day.format(label) + ", событий и задач: " + entries.size
                                    },
                                    color = background,
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                                ) {
                                    Column(
                                        Modifier.fillMaxSize(),
                                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(day.dayOfMonth.toString(), style = MaterialTheme.typography.titleSmall)
                                        if (entries.isNotEmpty()) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                entries.map { it.data.optString("kind") }.distinct().forEach { kind ->
                                                    Text("•", color = calendarColor(kind), fontSize = 12.sp)
                                                }
                                            }
                                            Text(entries.size.toString(), fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    listOf("remote" to "Сервер", "event" to "События", "task" to "Задачи").forEach { (kind, text) ->
                        Text("• $text", color = calendarColor(kind), fontSize = 11.sp)
                    }
                }
                state.notice?.let { Text(it, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text(
                    "Время Москвы · Свои события редактируются на телефоне. Серверные события доступны для чтения.",
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (entries.isEmpty()) item(key = "empty:$day") {
                        Text(
                            if (state.busy) "Загружаем…" else "Нет событий и задач в доступных данных.",
                            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            fontSize = 13.sp
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
    val dark = LocalInboxDark.current
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
    Card(
        onClick = { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(NativeAgenda.timeRange(data), fontSize = 12.sp, color = calendarColor(kind))
            Text(data.optString("title", "Без названия"), style = MaterialTheme.typography.titleSmall)
            if (data.optString("calendarTitle").isNotBlank()) {
                Text(data.optString("calendarTitle"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (expanded) {
                listOf("location", "description", "notes").forEach { field ->
                    if (data.optString(field).isNotBlank()) Text(data.optString(field), Modifier.padding(top = 8.dp), fontSize = 14.sp)
                }
            }
            if (kind == "event" || kind == "remote") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (kind == "event") {
                        TextButton(onClick = edit, enabled = enabled) { Text("Изменить своё событие") }
                    }
                    TextButton(onClick = createTask, enabled = enabled) { Text("Создать задачу") }
                }
            }
            if (links.length() > 0) {
                Text("Связанные письма · ${links.length()}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                for (index in 0 until links.length()) {
                    val link = links.getJSONObject(index)
                    val mail = link.optJSONObject("mail")
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { openMail(link.toString()) }, enabled = enabled, modifier = Modifier.weight(1f)) {
                            Text(
                                mail?.optString("subject")?.ifBlank { "Открыть письмо" } ?: "Повреждённая ссылка",
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        TextButton(onClick = { unlinkMail(link.optString("id")) }, enabled = enabled) {
                            Text("Отвязать", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}
