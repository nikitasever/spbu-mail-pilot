package ru.local.spbumailpilot

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NativeContactsScreen(
    model: NativeContactsModel,
    write: (String) -> Unit,
    fullClient: () -> Unit,
    onSwitchAccount: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {}
) {
    val state = model.state
    if (state.editor != null) {
        NativeContactEditor(model)
        return
    }
    if (state.detail != null) {
        NativeContactDetail(model, write)
        return
    }
    val clipboard = LocalClipboardManager.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    var books by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var copied by remember(state.owner, state.book, state.applied) { mutableStateOf(false) }
    val alphabet = rememberLazyListState(
        initialFirstVisibleItemIndex = (NativeContacts.ALPHABET.indexOf(state.letter)
            .takeIf { state.letter.isNotEmpty() } ?: 0).minus(1).coerceAtLeast(0)
    )
    LaunchedEffect(state.letter) {
        if (state.letter.isNotEmpty()) {
            alphabet.animateScrollToItem((NativeContacts.ALPHABET.indexOf(state.letter) - 1).coerceAtLeast(0))
        }
    }
    val names = mapOf(
        "local" to "На телефоне",
        "history" to "История",
        "personal" to "Личная",
        "team" to "Отдел",
        "company" to "Университет"
    )
    BackHandler(onBack = model::close)
    if (state.importOpen) {
        val hasRegistry = androidx.activity.compose.LocalActivityResultRegistryOwner.current != null
        val fileLauncher = if (hasRegistry) rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            model.importFileFromUri(uri)
        } else null
        val contactsPermission = if (hasRegistry) rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            model.systemPermissionResult(granted)
        } else null
        NativeContactImportDialog(
            model,
            { model.pickImportFile { fileLauncher?.launch(arrayOf("text/*", "application/octet-stream")) } },
            { model.requestSystemContacts { contactsPermission?.launch(android.Manifest.permission.READ_CONTACTS) } }
        )
    }
    if (state.clearHistoryOpen) {
        AlertDialog(
            onDismissRequest = model::cancelHistory,
            title = { Text("Очистить историю получателей?") },
            text = { Text("Будет удалена вся история адресов этого аккаунта, включая скрытую текущим поиском. Сохранённые контакты и системная книга останутся.") },
            confirmButton = {
                TextButton(onClick = model::clearHistory, enabled = !state.busy) {
                    Text("Очистить историю")
                }
            },
            dismissButton = {
                TextButton(onClick = model::cancelHistory, enabled = !state.busy) {
                    Text("Отмена")
                }
            }
        )
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                title = {
                    Column {
                        Text(
                            "Контакты",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (state.account.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier.padding(top = 1.dp).clickable(onClick = onSwitchAccount)
                            ) {
                                Text(
                                    state.account,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(
                                    NativeIcons.ChevronDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            focus.clearFocus()
                            model.load(true)
                        },
                        enabled = !state.busy
                    ) {
                        Icon(
                            NativeIcons.Refresh,
                            contentDescription = "Обновить",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box {
                        IconButton(
                            onClick = { menuOpen = true },
                            enabled = !state.busy && !state.importOpen
                        ) {
                            Icon(
                                NativeIcons.MoreVert,
                                contentDescription = "Ещё",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Импорт контактов") },
                                leadingIcon = {
                                    Icon(
                                        NativeIcons.Download,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    menuOpen = false
                                    model.startImport()
                                }
                            )
                            if (state.book == "history") {
                                DropdownMenuItem(
                                    text = { Text("Очистить историю") },
                                    leadingIcon = {
                                        Icon(
                                            NativeIcons.Trash,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    enabled = state.people.isNotEmpty(),
                                    onClick = {
                                        menuOpen = false
                                        model.askClearHistory()
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            GlowingCircleFab(
                icon = NativeIcons.Plus,
                contentDescription = "Добавить контакт",
                onClick = model::create,
                enabled = !state.busy && !state.importOpen
            )
        },
        bottomBar = {
            bottomBar()
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Combined modern search bar with inline directory filter chip
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 46.dp)
                        .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (state.query.isEmpty()) {
                            Text(
                                "Поиск контактов…",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = state.query,
                            onValueChange = model::text,
                            singleLine = true,
                            textStyle = TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    focus.clearFocus()
                                    model.search()
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (state.query.isNotEmpty()) {
                        Surface(
                            onClick = {
                                if (!state.busy) {
                                    model.text("")
                                    model.search()
                                }
                            },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    NativeIcons.Close,
                                    contentDescription = "Сброс",
                                    modifier = Modifier.size(13.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    // Inline directory filter chip inside search input
                    Box {
                        Surface(
                            onClick = { if (!state.busy) books = true },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    names[state.book] ?: "Университет",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1
                                )
                                Icon(
                                    NativeIcons.ChevronDown,
                                    contentDescription = "Справочник",
                                    modifier = Modifier.size(13.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        DropdownMenu(expanded = books, onDismissRequest = { books = false }) {
                            names.forEach { (book, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        books = false
                                        model.book(book)
                                    },
                                    enabled = book in listOf("local", "history") || state.supported
                                )
                            }
                        }
                    }
                    // Compact inline search trigger button inside input
                    Surface(
                        onClick = {
                            if (!state.busy) {
                                focus.clearFocus()
                                model.search()
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                NativeIcons.Search,
                                contentDescription = "Найти",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
            // Compact borderless alphabet strip
            LazyRow(
                state = alphabet,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(NativeContacts.ALPHABET.toList(), key = { it.toString() }) { letter ->
                    val selected = state.letter == letter.toString()
                    Surface(
                        onClick = {
                            focus.clearFocus()
                            model.letter(letter.toString())
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else Color.Transparent,
                        modifier = Modifier.height(28.dp).widthIn(min = 28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp)) {
                            Text(
                                letter.toString(),
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(top = 4.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
            if (state.busy) {
                LinearProgressIndicator(
                    Modifier.fillMaxWidth().height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Transparent
                )
            }
            state.error?.let {
                Text(
                    it,
                    Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp
                )
            }
            state.notice?.let {
                Text(
                    it,
                    Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (copied) {
                Text(
                    "Адрес скопирован",
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            key(state.owner, state.book, state.applied) {
                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(top = 6.dp, bottom = 84.dp)
                ) {
                    if (state.people.isEmpty() && !state.busy && state.error == null) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    if (state.book !in listOf("local", "history") && state.applied.isEmpty())
                                        "Выберите букву или введите имя для поиска"
                                    else "Совпадений не найдено",
                                    Modifier.padding(16.dp),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    items(state.people, key = { it.email.lowercase(java.util.Locale.ROOT) }) { person ->
                        Surface(
                            onClick = { if (!state.busy) model.show(person.email) },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                GlassSphereAvatar(
                                    initials = contactInitials(person.name, person.email),
                                    seed = person.email.ifBlank { person.name },
                                    size = 42.dp
                                )
                                Column(
                                    Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        person.name,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (person.name != person.email) {
                                        Text(
                                            person.email,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    GlassIconButton(
                                        icon = NativeIcons.Mail,
                                        contentDescription = "Написать",
                                        onClick = {
                                            if (!state.busy) {
                                                model.person(person.email)?.let { write(it.email) }
                                            }
                                        },
                                        size = 34.dp,
                                        iconSize = 16.dp,
                                        accent = true,
                                        enabled = !state.busy
                                    )
                                    GlassIconButton(
                                        icon = NativeIcons.Copy,
                                        contentDescription = "Копировать адрес",
                                        onClick = {
                                            if (!state.busy) {
                                                model.person(person.email)?.let {
                                                    clipboard.setText(AnnotatedString(it.email))
                                                    copied = true
                                                }
                                            }
                                        },
                                        size = 34.dp,
                                        iconSize = 15.dp,
                                        accent = false,
                                        enabled = !state.busy
                                    )
                                }
                            }
                        }
                    }
                    // Auto-pagination on scroll when more items exist
                    if (state.more) {
                        item(key = "load_more_${state.people.size}") {
                            LaunchedEffect(state.people.size, state.more) {
                                if (!state.busy) {
                                    model.load(true, more = true)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun contactInitials(name: String, email: String): String {
    val clean = name.trim().ifEmpty { email.trim() }
    val parts = clean.split(Regex("[\\s._@-]+")).filter { it.isNotEmpty() }
    return when {
        parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}".uppercase(java.util.Locale.ROOT)
        parts.size == 1 -> parts[0].take(2).uppercase(java.util.Locale.ROOT)
        else -> "?"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NativeContactDetail(model: NativeContactsModel, write: (String) -> Unit) {
    val state = model.state
    val person = state.detail ?: return
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var status by remember(state.owner, person.email) { mutableStateOf<String?>(null) }
    BackHandler(onBack = model::close)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                title = { Text("Контакт", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = model::close, enabled = !state.busy) {
                        Icon(NativeIcons.ArrowBack, contentDescription = "Назад", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassSphereAvatar(
                        initials = contactInitials(person.name, person.email),
                        seed = person.email.ifBlank { person.name },
                        size = 54.dp
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(person.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text(person.email, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                        if (person.number.isNotBlank()) {
                            Text(person.number, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { model.detail()?.let { write(it.email) } },
                    enabled = !state.busy,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                ) {
                    Icon(NativeIcons.Mail, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Написать")
                }
                Surface(
                    onClick = {
                        if (!state.busy) {
                            model.detail()?.let {
                                clipboard.setText(AnnotatedString(it.email))
                                status = "Адрес скопирован."
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(NativeIcons.Copy, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.width(6.dp))
                        Text("Копировать", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            if (person.number.isNotBlank()) {
                Surface(
                    onClick = {
                        if (!state.busy) {
                            model.detail()?.let { contact ->
                                if (contact.number.length <= 60 && contact.number.matches(Regex("[+0-9() .-]+"))) {
                                    status = runCatching<String?> {
                                        context.startActivity(
                                            android.content.Intent(
                                                android.content.Intent.ACTION_DIAL,
                                                android.net.Uri.fromParts("tel", contact.number, null)
                                            )
                                        )
                                        null
                                    }.getOrElse { "Не удалось открыть набор номера." }
                                } else status = "Этот номер нельзя передать в набор номера."
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(10.dp)) {
                        Text("Позвонить", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            Surface(
                onClick = { if (!state.busy) model.edit() },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(10.dp)) {
                    Text(
                        if (person.local) "Изменить" else "Сохранить на телефоне",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            status?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NativeContactEditor(model: NativeContactsModel) {
    val state = model.state
    val editor = state.editor ?: return
    BackHandler(onBack = model::close)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                title = {
                    Text(
                        if (state.expected == null) "Новый контакт" else "Изменить контакт",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = model::close, enabled = !state.busy) {
                        Icon(NativeIcons.Close, contentDescription = "Отмена", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                actions = {
                    if (state.expected != null) {
                        IconButton(onClick = model::askDelete, enabled = !state.busy) {
                            Icon(NativeIcons.Trash, contentDescription = "Удалить контакт", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    IconButton(
                        onClick = model::save,
                        enabled = !state.busy && !state.leaveOpen && !state.deleteOpen
                    ) {
                        Icon(NativeIcons.Check, contentDescription = "Сохранить", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            for ((key, label) in listOf("sender" to "Имя", "email" to "Электронная почта", "number" to "Телефон")) {
                TextField(
                    value = editor.optString(key),
                    onValueChange = { model.field(key, it) },
                    label = { Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        disabledContainerColor = MaterialTheme.colorScheme.surface,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    ),
                    enabled = !state.busy && !state.leaveOpen && !state.deleteOpen,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = when (key) {
                            "email" -> KeyboardType.Email
                            "number" -> KeyboardType.Phone
                            else -> KeyboardType.Text
                        }
                    )
                )
            }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        }
    }
    if (state.leaveOpen) {
        AlertDialog(
            onDismissRequest = { model.leave(false) },
            title = { Text("Отменить изменения?") },
            text = { Text("Введённые изменения не будут сохранены.") },
            confirmButton = { TextButton(onClick = { model.leave(true) }) { Text("Не сохранять") } },
            dismissButton = { TextButton(onClick = { model.leave(false) }) { Text("Продолжить редактирование") } }
        )
    }
    if (state.deleteOpen) {
        AlertDialog(
            onDismissRequest = model::cancelDelete,
            title = { Text("Удалить контакт?") },
            text = { Text("${editor.optString("sender")} (${state.expected?.optString("email")}) будет удалён с этого телефона. Запись в справочнике не изменится.") },
            confirmButton = { TextButton(onClick = model::delete) { Text("Удалить", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = model::cancelDelete) { Text("Отмена") } }
        )
    }
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
