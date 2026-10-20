package ru.local.spbumailpilot

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.saveable.rememberSaveable
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
    createTask: (JSONObject) -> Unit = {},
    accountEmail: String = "",
    onSwitchAccount: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {}
) {
    val state = model.state
    val date = LocalDate.parse(state.day)
    val month = YearMonth.from(date)
    val ruLocale = Locale.forLanguageTag("ru")
    val label = DateTimeFormatter.ofPattern("d MMMM, EEEE", ruLocale)
    val shortDayLabel = DateTimeFormatter.ofPattern("d MMMM, EEE", ruLocale)
    val monthTitle = month.format(DateTimeFormatter.ofPattern("LLLL yyyy", ruLocale))
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(ruLocale) else it.toString() }
    var searchOpen by remember { mutableStateOf(false) }
    var viewMenuOpen by remember { mutableStateOf(false) }
    var daySheetOpen by rememberSaveable { mutableStateOf(false) }
    val needle = state.searchQuery.trim().lowercase(Locale.ROOT)
    val matchesSearch: (CalendarEntry) -> Boolean = remember(needle) {
        { entry ->
            if (needle.isEmpty()) true
            else listOf("title", "location", "calendarTitle", "description", "notes")
                .any { entry.data.optString(it).lowercase(Locale.ROOT).contains(needle) }
        }
    }
    BackHandler(enabled = daySheetOpen) { daySheetOpen = false }
    BackHandler(enabled = !daySheetOpen, onBack = model::close)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.clickable(enabled = !state.busy) { model.today() }
                        ) {
                            Text(
                                monthTitle,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                NativeIcons.ChevronDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        if (accountEmail.isNotBlank()) {
                            Text(
                                "$accountEmail ▾",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 1.dp).clickable(onClick = onSwitchAccount)
                            )
                        }
                    }
                },
                actions = {
                    GlassIconButton(
                        icon = NativeIcons.Refresh,
                        contentDescription = "Обновить",
                        onClick = { model.load(true) },
                        enabled = !state.busy,
                        size = 36.dp
                    )
                    Spacer(Modifier.width(6.dp))
                    GlassIconButton(
                        icon = NativeIcons.Search,
                        contentDescription = "Поиск",
                        onClick = {
                            if (searchOpen && state.searchQuery.isNotEmpty()) model.clearSearch()
                            searchOpen = !searchOpen
                        },
                        active = searchOpen || state.searchQuery.isNotEmpty(),
                        size = 36.dp
                    )
                    Spacer(Modifier.width(6.dp))
                    GlassIconButton(
                        icon = NativeIcons.Sliders,
                        contentDescription = "Источники",
                        onClick = model::openSources,
                        enabled = !state.busy,
                        size = 36.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
            )
        },
        bottomBar = bottomBar,
        floatingActionButton = {
            GlowingCircleFab(
                icon = NativeIcons.Plus,
                contentDescription = "Новое событие",
                onClick = { if (!state.busy) editEvent(state.day, null) },
                enabled = !state.busy
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            CosmicHeaderGlow()
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                item {
                    if (searchOpen || state.searchQuery.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(NativeIcons.Search, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                TextField(
                                    value = state.searchQuery,
                                    onValueChange = model::search,
                                    placeholder = { Text("Поиск событий…", fontSize = 14.sp, color = MaterialTheme.colorScheme.outline) },
                                    singleLine = true,
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        disabledContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        disabledIndicatorColor = Color.Transparent
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                if (state.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = model::clearSearch, modifier = Modifier.size(28.dp)) {
                                        Icon(NativeIcons.Close, contentDescription = "Сбросить", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 14.dp), color = MaterialTheme.colorScheme.primary)
                    state.error?.let { Text(it, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                    state.actionNotice?.let { Text(it, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary) }

                    // Sleek 3D glass calendar grid card
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp)) {
                            Row(
                                Modifier.fillMaxWidth().padding(bottom = if (state.mode == "week" || state.mode == "month") 6.dp else 0.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    onClick = { model.shift(-1) },
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.size(32.dp).semantics { contentDescription = "Предыдущий период" }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("‹", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                                Row(
                                    Modifier.weight(1f).padding(horizontal = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
                                ) {
                                    listOf("day" to "День", "week" to "Неделя", "month" to "Месяц", "agenda" to "Список").forEach { (mode, text) ->
                                        val active = state.mode == mode
                                        Surface(
                                            onClick = { model.mode(mode) },
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                        ) {
                                            Text(
                                                text,
                                                Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                                fontSize = 11.sp,
                                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                                                color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                                Surface(
                                    onClick = { model.shift(1) },
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.size(32.dp).semantics { contentDescription = "Следующий период" }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("›", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                            if (state.mode == "week" || state.mode == "month") {
                                val start = if (state.mode == "week") NativeAgenda.first(state.day, "week")
                                else month.atDay(1).minusDays(month.atDay(1).dayOfWeek.value - 1L)
                                val cells = if (state.mode == "week") 7
                                else ((month.atDay(1).dayOfWeek.value - 1 + month.lengthOfMonth() + 6) / 7) * 7
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEachIndexed { idx, wd ->
                                        Text(
                                            wd,
                                            Modifier.weight(1f),
                                            textAlign = TextAlign.Center,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (idx >= 5) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                for (week in 0 until cells / 7) Row(
                                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    for (index in 0..6) {
                                        val day = start.plusDays((week * 7 + index).toLong())
                                        val present = state.mode == "week" || YearMonth.from(day) == month
                                        if (!present) Spacer(Modifier.weight(1f).height(44.dp)) else {
                                            val entries = state.entries.filter { NativeAgenda.onDay(it.data, day.toString()) && matchesSearch(it) }
                                            val selected = day == date
                                            val background = when {
                                                selected -> MaterialTheme.colorScheme.primary
                                                index >= 5 -> MaterialTheme.colorScheme.background
                                                else -> Color.Transparent
                                            }
                                            Surface(
                                                onClick = {
                                                    model.select(day.toString())
                                                    if (state.mode == "week" || state.mode == "month") {
                                                        daySheetOpen = true
                                                    }
                                                },
                                                modifier = Modifier.weight(1f).height(44.dp).semantics {
                                                    contentDescription = day.format(label) + ", событий и задач: " + entries.size
                                                },
                                                color = background,
                                                shape = RoundedCornerShape(11.dp)
                                            ) {
                                                Column(
                                                    Modifier.fillMaxSize(),
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.Center
                                                ) {
                                                    Text(
                                                        day.dayOfMonth.toString(),
                                                        fontSize = 13.sp,
                                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
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
                                                                    color = if (selected) Color.White else calendarColor(kind),
                                                                    modifier = Modifier.size(4.5.dp)
                                                                ) {}
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

                    if (state.mode == "day" || state.mode == "agenda" || needle.isNotEmpty()) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (state.mode == "day") date.format(shortDayLabel).replaceFirstChar { if (it.isLowerCase()) it.titlecase(ruLocale) else it.toString() }
                                else if (needle.isNotEmpty()) "Результаты поиска"
                                else "Список событий на $monthTitle",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (state.sources.isEmpty()) {
                                Surface(
                                    onClick = model::connectAccountCalendar,
                                    enabled = !state.busy,
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                                ) {
                                    Text(
                                        "Подключить CalDAV",
                                        Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    } else {
                        if (state.sources.isEmpty()) {
                            Surface(
                                onClick = model::connectAccountCalendar,
                                enabled = !state.busy,
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text("Календарь CalDAV", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                        Text("Подключите СПбГУ, Яндекс или Mail.ru", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text("Подключить", Modifier.padding(horizontal = 10.dp, vertical = 5.dp), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                listOf("remote" to "Сервер", "event" to "События", "task" to "Задачи").forEach { (k, name) ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Surface(shape = CircleShape, color = calendarColor(k), modifier = Modifier.size(6.dp)) {}
                                        Text(name, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            Text("Нажмите на день для событий", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
                val days = if (state.mode == "agenda" || needle.isNotEmpty()) {
                    generateSequence(month.atDay(1)) { it.plusDays(1) }.takeWhile { YearMonth.from(it) == month }.toList()
                } else if (state.mode == "day") {
                    listOf(date)
                } else {
                    emptyList()
                }
                days.forEach { day ->
                    val entries = state.entries.filter { NativeAgenda.onDay(it.data, day.toString()) && matchesSearch(it) }
                    if ((day == date && needle.isEmpty()) || entries.isNotEmpty()) {
                        if (days.size > 1) {
                            item(key = "day:$day") {
                                Text(
                                    day.format(label),
                                    Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        if (entries.isEmpty()) item(key = "empty:$day") {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Column(
                                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        if (state.busy) "Синхронизация…" else "Событий нет",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (state.sources.isEmpty() && !state.busy) {
                                        Text(
                                            "Университетский календарь CalDAV ещё не синхронизирован. Вы можете подключить календарь СПбГУ или создать локальное событие кнопкой «+».",
                                            fontSize = 12.sp,
                                            lineHeight = 17.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Button(
                                            onClick = model::connectAccountCalendar,
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                        ) {
                                            Text("Подключить календарь", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                        }
                                    }
                                }
                            }
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
                item { Spacer(Modifier.height(88.dp)) }
            }
        }
    }

    if (daySheetOpen && (state.mode == "week" || state.mode == "month")) {
        val sheetDate = LocalDate.parse(state.day)
        val dayEntries = state.entries.filter { NativeAgenda.onDay(it.data, state.day) && matchesSearch(it) }
        ModalBottomSheet(
            onDismissRequest = { daySheetOpen = false },
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            dragHandle = {
                Surface(
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                    shape = RoundedCornerShape(2.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                ) {
                    Box(Modifier.size(width = 36.dp, height = 4.dp))
                }
            }
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            sheetDate.format(label).replaceFirstChar { if (it.isLowerCase()) it.titlecase(ruLocale) else it.toString() },
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            if (dayEntries.isEmpty()) "Нет событий" else "Событий и задач: ${dayEntries.size}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        onClick = {
                            daySheetOpen = false
                            editEvent(state.day, null)
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primary,
                        enabled = !state.busy
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(NativeIcons.Plus, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                            Text("Событие", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                    }
                }
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                if (dayEntries.isEmpty()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(NativeIcons.Calendar, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                                }
                            }
                            Text("На этот день событий нет", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                            Text("Вы можете запланировать новое событие или задачу", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(4.dp))
                            Surface(
                                onClick = {
                                    daySheetOpen = false
                                    editEvent(state.day, null)
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(0.8.dp, Color(0x228B5CF6))
                            ) {
                                Text(
                                    "+ Добавить событие",
                                    Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(dayEntries, key = { it.key }) { entry ->
                            NativeCalendarCard(
                                entry = entry,
                                enabled = !state.busy,
                                links = remember(entry.key, state.links) {
                                    val all = JSONArray()
                                    state.links.forEach { all.put(it) }
                                    runCatching { NativeMailLinks.forEvent(entry.data, all) }.getOrDefault(JSONArray())
                                },
                                edit = {
                                    daySheetOpen = false
                                    editEvent(entry.data.optString("date"), entry.data.optString("id"))
                                },
                                openMail = {
                                    daySheetOpen = false
                                    openMail(it)
                                },
                                unlinkMail = { linkId -> model.requestUnlink(linkId) },
                                createTask = {
                                    daySheetOpen = false
                                    createTask(entry.data)
                                }
                            )
                        }
                    }
                }
            }
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
                    if (state.sources.isEmpty()) {
                        OutlinedButton(
                            onClick = { model.closeSources(); model.connectAccountCalendar() },
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Автоподключение по аккаунту") }
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
                        listOf("spbu" to "СПбГУ", "mailru" to "Mail.ru", "other" to "Другой CalDAV", "manual" to "По ссылке").forEach { (mode, text) ->
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
                    if (state.connectMode !in listOf("spbu", "mailru")) {
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
private fun calendarColor(kind: String): Color = when (kind) {
    "remote" -> if(LocalInboxDark.current)Color(0xFF93C5FD) else Color(0xFF30649E)
    "event" -> nativeSuccessColor()
    else -> MaterialTheme.colorScheme.primary
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
    val accentColor = calendarColor(kind)
    val rawRange = NativeAgenda.timeRange(data)
    val rangeParts = rawRange.split("–", "-").map { it.trim() }.filter { it.isNotEmpty() }
    val startTime = data.optString("time").ifBlank {
        val first = rangeParts.firstOrNull().orEmpty()
        if (first.isBlank() || first.equals("Весь день", ignoreCase = true)) "Весь" else first
    }
    val endTime = data.optString("endTime").ifBlank {
        when {
            rangeParts.size > 1 -> rangeParts[1]
            startTime == "Весь" -> "день"
            else -> ""
        }
    }
    val sourceLabel = data.optString("calendarTitle").ifBlank {
        when (kind) {
            "remote" -> "Расписание СПбГУ"
            "task" -> "Задача"
            else -> "Личный календарь"
        }
    }
    val location = data.optString("location")
    var expanded by remember(entry.key) { mutableStateOf(false) }
    Surface(
        onClick = { expanded = !expanded },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Colored vertical indicator bar on the left (type of calendar: server, personal, tasks)
                Surface(
                    shape = RoundedCornerShape(3.dp),
                    color = accentColor,
                    modifier = Modifier.width(4.dp).height(42.dp)
                ) {}
                Spacer(Modifier.width(10.dp))
                Column(
                    Modifier.width(50.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        startTime,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    if (endTime.isNotBlank()) {
                        Text(
                            endTime,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
                Column(
                    Modifier.weight(1f).padding(start = 6.dp, end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        data.optString("title", "Без названия"),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = if (expanded) 4 else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (location.isNotBlank()) "$sourceLabel · $location" else sourceLabel,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (kind == "event" || kind == "remote") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (kind == "event") {
                            GlassIconButton(
                                icon = NativeIcons.ComposePen,
                                contentDescription = "Изменить своё событие",
                                onClick = { if (enabled) edit() },
                                enabled = enabled,
                                size = 32.dp
                            )
                        }
                        GlassIconButton(
                            icon = NativeIcons.Tasks,
                            contentDescription = "Создать задачу",
                            onClick = { if (enabled) createTask() },
                            enabled = enabled,
                            size = 32.dp
                        )
                    }
                }
            }
            if (expanded) {
                listOf("description", "notes").forEach { field ->
                    if (data.optString(field).isNotBlank()) {
                        Text(
                            data.optString(field),
                            Modifier.padding(start = 64.dp, top = 6.dp),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (links.length() > 0) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
                        for (index in 0 until links.length()) {
                            val link = links.getJSONObject(index)
                            val mail = link.optJSONObject("mail")
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    Modifier
                                        .weight(1f)
                                        .clickable(enabled = enabled) { openMail(link.toString()) }
                                        .padding(vertical = 4.dp),
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
                                        mail?.optString("subject")?.ifBlank { "Открыть письмо" } ?: "Письмо",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                IconButton(
                                    onClick = { unlinkMail(link.optString("id")) },
                                    enabled = enabled,
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(NativeIcons.Close, contentDescription = "Отвязать", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
