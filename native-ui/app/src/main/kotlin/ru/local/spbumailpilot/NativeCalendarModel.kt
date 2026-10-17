package ru.local.spbumailpilot

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.compose.runtime.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.Executors

internal data class CalendarEntry(val key: String, val data: JSONObject)

internal data class CalendarSource(
    val id: String,
    val title: String,
    val url: String,
    val user: String,
    val cached: Boolean,
    val error: String,
    val syncedAt: String
)

internal data class CalendarState(
    val open: Boolean = false,
    val owner: String = "",
    val day: String = LocalDate.now(ZoneId.of("Europe/Moscow")).toString(),
    val mode: String = "week",
    val entries: List<CalendarEntry> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val cached: Boolean = true,
    val actionNotice: String? = null,
    val links: List<JSONObject> = emptyList(),
    val sources: List<CalendarSource> = emptyList(),
    val sourcesOpen: Boolean = false,
    val connectOpen: Boolean = false,
    val connectMode: String = "mailru",
    val connectUrl: String = "",
    val connectTitle: String = "",
    val connectUser: String = "",
    val connectPassword: String = "",
    val discoveredCalendars: List<Pair<String, String>> = emptyList(),
    val discoveryPickerOpen: Boolean = false,
    val deleteSourceId: String? = null,
    val unlinkLinkId: String? = null,
    val unlinkExpected: JSONObject? = null,
    val searchQuery: String = "",
    val returnRoute: String? = null,
    val pickerOpen: Boolean = false,
    val pickerOwner: String = "",
    val pickerMail: JSONObject? = null,
    val pickerEntries: List<CalendarEntry> = emptyList(),
    val pickerBusy: Boolean = false,
    val pickerError: String? = null,
    val pickerQuery: String = ""
) {
    override fun toString(): String = "CalendarState(open=$open,owner=$owner,day=$day,mode=$mode,busy=$busy)"
}

/** Uses the existing account-bound CalendarBook; remote events remain read-only. */
internal class NativeCalendarModel(app: Application) : AndroidViewModel(app) {
    private val context = app.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var generation = 0L
    @Volatile private var foreground = false
    @Volatile private var closed = false
    @Volatile private var mutating = false
    var state by mutableStateOf(CalendarState()); private set

    fun resume() {
        foreground = true
        if (state.open && !mutating) load(false)
        if (state.pickerOpen && !mutating) loadPickerEvents(false)
    }

    fun pause() {
        foreground = false
        generation++
        state = state.copy(
            busy = mutating,
            pickerBusy = false,
            connectPassword = "",
            discoveredCalendars = emptyList(),
            discoveryPickerOpen = false,
            deleteSourceId = null,
            unlinkLinkId = null,
            unlinkExpected = null
        )
    }

    fun open(owner: String, returnRoute: String? = null) {
        if (!foreground || AppLock.locked(context) || owner.isBlank()) return
        if (owner != state.owner) state = CalendarState(owner = owner)
        state = state.copy(
            open = true,
            returnRoute = returnRoute,
            connectPassword = "",
            discoveredCalendars = emptyList(),
            discoveryPickerOpen = false,
            deleteSourceId = null,
            unlinkLinkId = null,
            unlinkExpected = null
        )
        load(false)
    }

    fun openDay(owner: String, day: String, returnRoute: String? = null) {
        if (!foreground || AppLock.locked(context) || owner.isBlank()) return
        try { NativeAgenda.date(day) } catch (_: Exception) { return }
        if (owner != state.owner) state = CalendarState(owner = owner)
        state = state.copy(
            open = true,
            day = day,
            mode = "day",
            returnRoute = returnRoute,
            connectPassword = "",
            discoveredCalendars = emptyList(),
            discoveryPickerOpen = false,
            deleteSourceId = null,
            unlinkLinkId = null,
            unlinkExpected = null
        )
        load(false)
    }

    fun takeReturnRoute(): String? {
        if (state.open) return null
        val route = state.returnRoute ?: return null
        state = state.copy(returnRoute = null)
        return route
    }

    fun returnFromEditor(day: String, notice: String?) {
        if (!state.open) return
        try { NativeAgenda.date(day) } catch (_: Exception) { return }
        state = state.copy(day = day, actionNotice = notice)
        load(false)
    }

    fun linkError() {
        state = state.copy(error = "Не удалось открыть связь. Возможно, ссылка повреждена или принадлежит другому ящику.")
    }

    fun clearActionNotice() {
        state = state.copy(actionNotice = null)
    }

    fun sourceMail(linkJson: String): JSONObject? {
        if (!foreground || AppLock.locked(context) || state.busy) return null
        return runCatching {
            require(linkJson.length <= 64000)
            val links = JSONArray()
            state.links.forEach { links.put(it) }
            NativeMailLinks.savedMail(links, JSONObject(linkJson))
        }.getOrNull()
    }

    fun close() {
        generation++
        state = state.copy(
            open = false,
            busy = false,
            sourcesOpen = false,
            connectOpen = false,
            connectPassword = "",
            discoveredCalendars = emptyList(),
            discoveryPickerOpen = false,
            deleteSourceId = null,
            unlinkLinkId = null,
            unlinkExpected = null
        )
    }

    fun mode(value: String) {
        try { NativeAgenda.first(state.day, value) } catch (_: Exception) { return }
        if (state.mode == value) return
        state = state.copy(mode = value)
        load(false)
    }

    fun select(day: String) {
        try { NativeAgenda.date(day) } catch (_: Exception) { return }
        val same = NativeAgenda.months(day, state.mode) == NativeAgenda.months(state.day, state.mode)
        state = state.copy(day = day)
        if (!same) load(false)
    }

    fun shift(amount: Long) {
        val current = NativeAgenda.date(state.day)
        val next = when (state.mode) {
            "day" -> current.plusDays(amount)
            "week" -> current.plusWeeks(amount)
            else -> current.plusMonths(amount)
        }
        select(next.toString())
    }

    fun today() {
        select(LocalDate.now(ZoneId.of("Europe/Moscow")).toString())
    }

    fun search(query: String) {
        if (query.length <= 200) state = state.copy(searchQuery = query)
    }

    fun clearSearch() {
        state = state.copy(searchQuery = "")
    }

    fun openSources() {
        if (!foreground || closed || AppLock.locked(context) || state.busy || mutating) return
        state = state.copy(sourcesOpen = true, error = null, actionNotice = null)
    }

    fun closeSources() {
        if (state.busy || mutating) return
        state = state.copy(sourcesOpen = false, deleteSourceId = null)
    }

    fun openConnect() {
        if (!foreground || closed || AppLock.locked(context) || state.busy || mutating) return
        state = state.copy(
            connectOpen = true,
            connectMode = "mailru",
            connectUrl = "https://calendar.mail.ru/",
            connectTitle = "Mail.ru",
            connectUser = "",
            connectPassword = "",
            discoveredCalendars = emptyList(),
            discoveryPickerOpen = false,
            error = null,
            actionNotice = null
        )
    }

    fun closeConnect() {
        if (state.busy || mutating) return
        state = state.copy(
            connectOpen = false,
            connectPassword = "",
            discoveredCalendars = emptyList(),
            discoveryPickerOpen = false,
            deleteSourceId = null,
            unlinkLinkId = null,
            unlinkExpected = null,
            error = null
        )
    }

    fun setConnectMode(mode: String) {
        if (state.busy || mutating || mode !in listOf("mailru", "other", "manual")) return
        val url = if (mode == "mailru") "https://calendar.mail.ru/" else if (state.connectMode == "mailru") "" else state.connectUrl
        val title = if (mode == "mailru" && state.connectTitle.isBlank()) "Mail.ru" else state.connectTitle
        state = state.copy(
            connectMode = mode,
            connectUrl = url,
            connectTitle = title,
            discoveredCalendars = emptyList(),
            discoveryPickerOpen = false,
            error = null
        )
    }

    fun setConnectField(name: String, value: String) {
        if (state.busy || mutating || value.length > 1000) return
        state = when (name) {
            "url" -> state.copy(connectUrl = value, error = null)
            "title" -> state.copy(connectTitle = value, error = null)
            "user" -> state.copy(connectUser = value, error = null)
            "password" -> state.copy(connectPassword = value, error = null)
            else -> state
        }
    }

    fun submitConnect() {
        if (!foreground || closed || AppLock.locked(context) || !state.open || state.busy || mutating) return
        val mode = state.connectMode
        val rawUrl = if (mode == "mailru" && state.connectUrl.isBlank()) "https://calendar.mail.ru/" else state.connectUrl.trim()
        val rawTitle = state.connectTitle.trim()
        val user = state.connectUser.trim()
        val password = state.connectPassword
        if (rawUrl.isEmpty()) {
            state = state.copy(error = "Укажите адрес календаря или сервера.")
            return
        }
        if (mode != "manual" && (user.isEmpty() || password.isEmpty())) {
            state = state.copy(error = "Для CalDAV укажите логин и пароль приложения.")
            return
        }
        val snapshot = state
        val month = NativeAgenda.months(snapshot.day, "month").firstOrNull() ?: snapshot.day.substring(0, 7)
        val ticket = ++generation
        mutating = true
        state = state.copy(busy = true, error = null, actionNotice = null)
        worker.execute {
            try {
                verifyCanCommit(ticket, snapshot.owner)
                if (mode == "manual") {
                    val login = verifyCanCommit(ticket, snapshot.owner)
                    CalendarBook(context, login).add(rawTitle.ifEmpty { "Календарь" }, rawUrl, user, password, month)
                    main.post {
                        mutating = false
                        if (active(ticket, snapshot.owner)) {
                            state = state.copy(
                                busy = false,
                                connectOpen = false,
                                connectPassword = "",
                                discoveredCalendars = emptyList(),
                                discoveryPickerOpen = false,
                                actionNotice = "Календарь подключён."
                            )
                            load(false)
                        }
                    }
                } else {
                    val found = CalendarDiscovery.discover(rawUrl, user, password)
                    verifyCanCommit(ticket, snapshot.owner)
                    if (found.size > 1) {
                        val choices = found.map { it.title.ifBlank { "Календарь" } to it.url }
                        main.post {
                            mutating = false
                            if (active(ticket, snapshot.owner)) {
                                state = state.copy(
                                    busy = false,
                                    discoveredCalendars = choices,
                                    discoveryPickerOpen = true,
                                    error = null
                                )
                            }
                        }
                    } else {
                        val chosen = found.firstOrNull() ?: error("Календари не найдены.")
                        val finalTitle = rawTitle.ifEmpty { chosen.title.ifBlank { "Календарь" } }
                        val login = verifyCanCommit(ticket, snapshot.owner)
                        CalendarBook(context, login).add(finalTitle, chosen.url, user, password, month)
                        main.post {
                            mutating = false
                            if (active(ticket, snapshot.owner)) {
                                state = state.copy(
                                    busy = false,
                                    connectOpen = false,
                                    connectPassword = "",
                                    discoveredCalendars = emptyList(),
                                    discoveryPickerOpen = false,
                                    actionNotice = "Календарь подключён."
                                )
                                load(false)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                main.post {
                    mutating = false
                    if (active(ticket, snapshot.owner)) {
                        val msg = e.message?.takeIf { it.isNotBlank() } ?: "Не удалось подключить календарь. Проверьте адрес и пароль приложения."
                        state = state.copy(busy = false, error = msg)
                    }
                }
            }
        }
    }

    fun chooseDiscovered(title: String, url: String) {
        if (!foreground || closed || AppLock.locked(context) || !state.open || state.busy || mutating) return
        if (url.isBlank() || state.discoveredCalendars.none { it.second == url }) return
        val snapshot = state
        val user = snapshot.connectUser.trim()
        val password = snapshot.connectPassword
        val finalTitle = snapshot.connectTitle.trim().ifEmpty { title.ifBlank { "Календарь" } }
        val month = NativeAgenda.months(snapshot.day, "month").firstOrNull() ?: snapshot.day.substring(0, 7)
        val ticket = ++generation
        mutating = true
        state = state.copy(busy = true, discoveryPickerOpen = false, error = null)
        worker.execute {
            try {
                val login = verifyCanCommit(ticket, snapshot.owner)
                CalendarBook(context, login).add(finalTitle, url, user, password, month)
                main.post {
                    mutating = false
                    if (active(ticket, snapshot.owner)) {
                        state = state.copy(
                            busy = false,
                            connectOpen = false,
                            connectPassword = "",
                            discoveredCalendars = emptyList(),
                            discoveryPickerOpen = false,
                            actionNotice = "Календарь подключён."
                        )
                        load(false)
                    }
                }
            } catch (e: Exception) {
                main.post {
                    mutating = false
                    if (active(ticket, snapshot.owner)) {
                        val msg = e.message?.takeIf { it.isNotBlank() } ?: "Не удалось подключить выбранный календарь."
                        state = state.copy(busy = false, error = msg)
                    }
                }
            }
        }
    }

    fun requestRemoveSource(id: String) {
        if (!foreground || closed || AppLock.locked(context) || state.busy || mutating || id.isBlank()) return
        if (state.sources.none { it.id == id }) return
        state = state.copy(deleteSourceId = id, error = null)
    }

    fun cancelRemoveSource() {
        if (state.busy || mutating) return
        state = state.copy(deleteSourceId = null)
    }

    fun confirmRemoveSource() {
        val sourceId = state.deleteSourceId ?: return
        if (!foreground || closed || AppLock.locked(context) || !state.open || state.busy || mutating) return
        val snapshot = state
        val ticket = ++generation
        mutating = true
        state = state.copy(busy = true, deleteSourceId = null, error = null)
        worker.execute {
            try {
                val login = verifyCanCommit(ticket, snapshot.owner)
                CalendarBook(context, login).remove(sourceId)
                main.post {
                    mutating = false
                    if (active(ticket, snapshot.owner)) {
                        state = state.copy(busy = false, actionNotice = "Календарь отключён.")
                        load(false)
                    }
                }
            } catch (_: Exception) {
                main.post {
                    mutating = false
                    if (active(ticket, snapshot.owner)) {
                        state = state.copy(busy = false, error = "Не удалось отключить календарь.")
                    }
                }
            }
        }
    }

    fun requestUnlink(link: JSONObject) {
        if (!foreground || closed || AppLock.locked(context) || state.busy || mutating) return
        val id = link.optString("id")
        if (id.isBlank()) return
        state = state.copy(unlinkLinkId = id, unlinkExpected = JSONObject(link.toString()), error = null)
    }

    fun requestUnlink(linkId: String) {
        if (!foreground || closed || AppLock.locked(context) || state.busy || mutating || linkId.isBlank()) return
        val found = state.links.find { it.optString("id") == linkId } ?: return
        requestUnlink(found)
    }

    fun cancelUnlink() {
        if (state.busy || mutating) return
        state = state.copy(unlinkLinkId = null, unlinkExpected = null)
    }

    fun confirmUnlink() {
        val id = state.unlinkLinkId ?: return
        val expected = state.unlinkExpected ?: return
        if (!foreground || closed || AppLock.locked(context) || !state.open || state.busy || mutating) return
        val snapshot = state
        val ticket = ++generation
        mutating = true
        state = state.copy(busy = true, unlinkLinkId = null, unlinkExpected = null, error = null)
        worker.execute {
            try {
                verifyCanCommit(ticket, snapshot.owner)
                synchronized(ReplyReminderCenter::class.java) {
                    val login = verifyCanCommit(ticket, snapshot.owner)
                    val prefs = NoticeCenter.state(context, login)
                    val current = JSONObject(prefs.getString("state", "{}")!!)
                    val next = NativeMailLinks.unlink(current, id, expected)
                    val json = next.toString()
                    check(json.length <= 2000000)
                    verifyCanCommit(ticket, snapshot.owner)
                    check(prefs.edit().putString("state", json).commit())
                }
                main.post {
                    mutating = false
                    if (active(ticket, snapshot.owner)) {
                        state = state.copy(busy = false, actionNotice = "Связь с письмом удалена.")
                        load(false)
                    }
                }
            } catch (e: Exception) {
                main.post {
                    mutating = false
                    if (active(ticket, snapshot.owner)) {
                        val msg = if (e is SharedStateMerge.Conflict) "Связь уже была изменена или удалена. Обновите календарь."
                        else "Не удалось удалить связь с письмом."
                        state = state.copy(busy = false, error = msg)
                    }
                }
            }
        }
    }

    fun openMailEventPicker(owner: String, mail: JSONObject) {
        if (!foreground || closed || AppLock.locked(context) || mutating || owner.isBlank()) return
        if (NativeMailLinks.target(owner, mail) == null) return
        state = state.copy(
            pickerOpen = true,
            pickerOwner = owner,
            pickerMail = JSONObject(mail.toString()),
            pickerEntries = emptyList(),
            pickerBusy = false,
            pickerError = null,
            pickerQuery = ""
        )
        loadPickerEvents(false)
    }

    fun closeMailEventPicker() {
        if (mutating) return
        generation++
        state = state.copy(
            pickerOpen = false,
            pickerOwner = "",
            pickerMail = null,
            pickerEntries = emptyList(),
            pickerBusy = false,
            pickerError = null,
            pickerQuery = ""
        )
    }

    fun pickerSearch(query: String) {
        if (query.length <= 200) state = state.copy(pickerQuery = query)
    }

    fun loadPickerEvents(online: Boolean) {
        if (!foreground || closed || !state.pickerOpen || state.pickerOwner.isBlank() || mutating || AppLock.locked(context)) return
        val owner = state.pickerOwner
        val day = state.day
        val ticket = ++generation
        state = state.copy(pickerBusy = true, pickerError = null)
        worker.execute {
            try {
                val login = CredentialVault(context).load() ?: error("No account")
                check(NoticeCenter.scope(login) == owner)
                val local = JSONObject(NoticeCenter.state(context, login).getString("state", "{}")!!)
                val book = CalendarBook(context, login)
                val remote = JSONArray()
                for (month in NativeAgenda.months(day, "month")) {
                    check(pickerActive(ticket, owner))
                    val page = book.list(month, online) { pickerActive(ticket, owner) }
                    val events = page.getJSONArray("events")
                    for (i in 0 until events.length()) remote.put(events.getJSONObject(i))
                }
                val merged = NativeAgenda.combine(local, remote)
                val rows = (0 until merged.length()).mapNotNull { idx ->
                    val entry = merged.getJSONObject(idx)
                    val kind = entry.optString("kind")
                    if (kind == "event" || kind == "remote") {
                        CalendarEntry(
                            JSONArray().put(kind).put(entry.optString("calendarId")).put(entry.optString("id")).put(entry.optString("date")).put(entry.optString("time")).toString(),
                            entry
                        )
                    } else null
                }
                check(CredentialVault(context).load()?.let { NoticeCenter.scope(it) } == owner)
                main.post {
                    if (pickerActive(ticket, owner)) {
                        state = state.copy(pickerEntries = rows, pickerBusy = false, pickerError = null)
                    }
                }
            } catch (_: Exception) {
                main.post {
                    if (pickerActive(ticket, owner)) {
                        state = state.copy(pickerBusy = false, pickerError = "Не удалось загрузить события календаря. Проверьте подключение и повторите попытку.")
                    }
                }
            }
        }
    }

    fun confirmLinkMailToEvent(event: JSONObject) {
        if (!foreground || closed || AppLock.locked(context) || !state.pickerOpen || state.pickerOwner.isBlank() || state.pickerBusy || mutating) return
        val owner = state.pickerOwner
        val mail = state.pickerMail ?: return
        val day = state.day
        val eventCopy = JSONObject(event.toString())
        val ticket = ++generation
        mutating = true
        state = state.copy(pickerBusy = true, pickerError = null)
        worker.execute {
            try {
                val targetKey = NativeMailLinks.eventKey(eventCopy)
                check(targetKey.isNotEmpty())
                val initialLogin = verifyPickerCanCommit(ticket, owner)
                if (eventCopy.optString("kind") == "remote") {
                    val book = CalendarBook(context, initialLogin)
                    val checkDay = eventCopy.optString("date").takeIf { it.isNotBlank() } ?: day
                    var foundRemote = false
                    for (month in NativeAgenda.months(checkDay, "month")) {
                        val page = book.list(month, false) { pickerActive(ticket, owner) }
                        val events = page.getJSONArray("events")
                        for (i in 0 until events.length()) {
                            val candidate = events.getJSONObject(i)
                            if (NativeMailLinks.eventKey(candidate) == targetKey) {
                                foundRemote = true
                                break
                            }
                        }
                    }
                    if (!foundRemote) throw IllegalStateException("Выбранное событие больше не найдено в кэше календаря. Обновите список.")
                }
                synchronized(ReplyReminderCenter::class.java) {
                    val verifiedLogin = verifyPickerCanCommit(ticket, owner)
                    val prefs = NoticeCenter.state(context, verifiedLogin)
                    val current = JSONObject(prefs.getString("state", "{}")!!)
                    if (eventCopy.optString("kind") == "event") {
                        val localEvents = current.optJSONArray("events") ?: JSONArray()
                        val eventId = eventCopy.optString("id")
                        val exists = (0 until localEvents.length()).any { localEvents.optJSONObject(it)?.optString("id") == eventId }
                        if (!exists) throw IllegalStateException("Выбранное локальное событие было удалено. Обновите список.")
                    }
                    val next = NativeMailLinks.link(current, owner, mail, eventCopy, UUID.randomUUID().toString())
                    val json = next.toString()
                    check(json.length <= 2000000)
                    verifyPickerCanCommit(ticket, owner)
                    check(prefs.edit().putString("state", json).commit())
                }
                main.post {
                    mutating = false
                    if (pickerActive(ticket, owner)) {
                        state = state.copy(
                            pickerOpen = false,
                            pickerOwner = "",
                            pickerMail = null,
                            pickerEntries = emptyList(),
                            pickerBusy = false,
                            pickerError = null,
                            pickerQuery = "",
                            actionNotice = "Письмо связано с событием."
                        )
                        if (state.open && state.owner == owner) load(false)
                    }
                }
            } catch (e: Exception) {
                main.post {
                    mutating = false
                    if (pickerActive(ticket, owner)) {
                        val msg = e.message?.takeIf { it.isNotBlank() && (e is IllegalArgumentException || e is IllegalStateException) }
                            ?: "Не удалось связать письмо с событием."
                        state = state.copy(pickerBusy = false, pickerError = msg)
                    }
                }
            }
        }
    }

    fun load(online: Boolean) {
        if (!foreground || closed || !state.open || mutating || AppLock.locked(context)) return
        val snapshot = state
        val ticket = ++generation
        // Don't show another month's old rows under a new period while loading.
        state = state.copy(busy = true, error = null, notice = null, entries = emptyList())
        worker.execute {
            try {
                val login = CredentialVault(context).load() ?: error("No account")
                check(NoticeCenter.scope(login) == snapshot.owner)
                val local = JSONObject(NoticeCenter.state(context, login).getString("state", "{}")!!)
                val savedLinks = local.optJSONArray("eventMailLinks") ?: JSONArray()
                check(savedLinks.length() <= 5000)
                val links = (0 until savedLinks.length()).mapNotNull { savedLinks.optJSONObject(it)?.let { link -> JSONObject(link.toString()) } }
                val book = CalendarBook(context, login)
                val remote = JSONArray()
                val warnings = linkedSetOf<String>()
                val sourceMap = linkedMapOf<String, CalendarSource>()
                var calendars = 0
                for (month in NativeAgenda.months(snapshot.day, snapshot.mode)) {
                    check(active(ticket, snapshot.owner))
                    val page = book.list(month, online) { active(ticket, snapshot.owner) }
                    val connections = page.getJSONArray("calendars")
                    calendars = maxOf(calendars, connections.length())
                    for (i in 0 until connections.length()) {
                        val c = connections.getJSONObject(i)
                        val id = c.optString("id")
                        if (id.isNotEmpty()) {
                            sourceMap[id] = CalendarSource(
                                id = id,
                                title = c.optString("title", "Календарь"),
                                url = c.optString("url"),
                                user = c.optString("user"),
                                cached = c.optBoolean("cached"),
                                error = c.optString("error"),
                                syncedAt = c.optString("syncedAt")
                            )
                        }
                        if (c.optString("error").isNotEmpty()) warnings.add(c.optString("title") + ": " + c.optString("error"))
                        if (!c.optBoolean("cached")) warnings.add("Нет сохранённых событий: " + c.optString("title"))
                    }
                    val events = page.getJSONArray("events")
                    for (i in 0 until events.length()) remote.put(events.getJSONObject(i))
                }
                val merged = NativeAgenda.combine(local, remote)
                val rows = (0 until merged.length()).map {
                    merged.getJSONObject(it).let { entry ->
                        CalendarEntry(
                            JSONArray().put(entry.optString("kind")).put(entry.optString("calendarId")).put(entry.optString("id")).put(entry.optString("date")).put(entry.optString("time")).toString(),
                            entry
                        )
                    }
                }
                check(CredentialVault(context).load()?.let { NoticeCenter.scope(it) } == snapshot.owner)
                main.post {
                    if (active(ticket, snapshot.owner)) {
                        state = state.copy(
                            entries = rows,
                            links = links,
                            sources = sourceMap.values.toList(),
                            busy = false,
                            cached = !online,
                            notice = if (warnings.isNotEmpty()) warnings.joinToString("\n")
                            else if (calendars == 0) "Серверный календарь не подключён. Показаны задачи и события на телефоне."
                            else if (online) "Календарь обновлён."
                            else "Сохранённые события календарей. Для актуальных данных нажмите «Обновить»."
                        )
                    }
                }
            } catch (_: Exception) {
                val owner = runCatching { CredentialVault(context).load()?.let { NoticeCenter.scope(it) } }.getOrNull()
                main.post {
                    if (active(ticket, snapshot.owner)) {
                        state = if (owner != snapshot.owner) CalendarState() else state.copy(busy = false, error = "Не удалось загрузить календарь. Проверьте подключение и настройки календарей.")
                    }
                }
            }
        }
    }

    private fun verifyCanCommit(ticket: Long, owner: String): SavedAccounts.Login {
        check(active(ticket, owner))
        val login = CredentialVault(context).load() ?: error("No account")
        check(NoticeCenter.scope(login) == owner)
        return login
    }

    private fun verifyPickerCanCommit(ticket: Long, owner: String): SavedAccounts.Login {
        check(pickerActive(ticket, owner))
        val login = CredentialVault(context).load() ?: error("No account")
        check(NoticeCenter.scope(login) == owner)
        return login
    }

    private fun active(ticket: Long, owner: String) =
        !closed && foreground && generation == ticket && state.open && state.owner == owner && !AppLock.locked(context)

    private fun pickerActive(ticket: Long, owner: String) =
        !closed && foreground && generation == ticket && state.pickerOpen && state.pickerOwner == owner && !AppLock.locked(context)

    override fun onCleared() {
        closed = true
        foreground = false
        generation++
        worker.shutdown()
    }
}
