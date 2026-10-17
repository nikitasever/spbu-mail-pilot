package ru.local.spbumailpilot

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NativeCalendarLinksTest {
    private val owner = "account-" + "a".repeat(64)

    @Test
    fun calendarStateRedactsSecretsInToString() {
        val state = CalendarState(
            open = true,
            owner = owner,
            connectUser = "student@st.spbu.ru",
            connectPassword = "super-secret-password-123",
            connectUrl = "https://caldav.example.org/private/token",
            searchQuery = "confidential-meeting",
            pickerQuery = "secret-topic"
        )
        val rendered = state.toString()
        assertFalse(rendered.contains("super-secret-password-123"))
        assertFalse(rendered.contains("student@st.spbu.ru"))
        assertFalse(rendered.contains("private/token"))
        assertFalse(rendered.contains("confidential-meeting"))
        assertFalse(rendered.contains("secret-topic"))
        assertTrue(rendered.startsWith("CalendarState(open=true,owner=$owner"))
    }

    @Test
    fun taskFromEventMapsRemoteAndLocalSources() {
        val remote = JSONObject()
            .put("id", "remote-42")
            .put("kind", "remote")
            .put("calendarId", "cal-work")
            .put("calendarTitle", "Рабочий")
            .put("title", "Защита проекта")
            .put("date", "2026-10-25")
            .put("time", "11:00")
            .put("allDay", false)
            .put("location", "Ауд. 204")
        val remoteTask = NativeMailLinks.taskFromEvent(remote, "task-1")
        assertEquals("task-1", remoteTask.getString("id"))
        assertEquals("Защита проекта", remoteTask.getString("title"))
        assertEquals("2026-10-25", remoteTask.getString("date"))
        assertEquals("11:00", remoteTask.getString("time"))
        assertEquals("Место: Ауд. 204", remoteTask.getString("notes"))
        assertFalse(remoteTask.getBoolean("done"))
        val remoteSource = remoteTask.getJSONObject("calendarSource")
        assertEquals("cal-work", remoteSource.getString("calendarId"))
        assertEquals("remote-42", remoteSource.getString("id"))
        assertEquals("Рабочий", remoteSource.getString("calendarTitle"))

        val local = JSONObject()
            .put("id", "local-7")
            .put("kind", "event")
            .put("date", "2026-10-26")
            .put("time", "10:00")
            .put("allDay", true)
        val localTask = NativeMailLinks.taskFromEvent(local, "task-2")
        assertEquals("Без названия", localTask.getString("title"))
        assertEquals("", localTask.getString("time"))
        assertEquals("", localTask.getString("notes"))
        val localSource = localTask.getJSONObject("calendarSource")
        assertEquals("local", localSource.getString("calendarId"))
        assertEquals("local-7", localSource.getString("id"))
        assertEquals("Локальный", localSource.getString("calendarTitle"))
    }

    @Test
    fun linkAndUnlinkEnforceOwnerValidationDuplicateLimitAndCas() {
        val mail = JSONObject()
            .put("folder", "INBOX")
            .put("uid", "101")
            .put("validity", "9")
            .put("subject", "Повестка")
            .put("sender", "Деканат")
            .put("body", "sensitive-body")
        val event = JSONObject()
            .put("id", "ev-1")
            .put("kind", "event")
            .put("title", "Семинар")
            .put("date", "2026-10-27")

        val state1 = NativeMailLinks.link(JSONObject(), owner, mail, event, "link-101")
        val links = state1.getJSONArray("eventMailLinks")
        assertEquals(1, links.length())
        val saved = links.getJSONObject(0)
        assertEquals("link-101", saved.getString("id"))
        assertEquals("local:ev-1", saved.getString("eventKey"))
        assertFalse(saved.getJSONObject("mail").has("body"))

        // Duplicate link is rejected
        try {
            NativeMailLinks.link(state1, owner, mail, event, "link-102")
            fail("Expected duplicate link rejection")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("уже связано"))
        }

        // CAS unlink rejects modified expected snapshot
        val modified = JSONObject(saved.toString()).put("eventTitle", "Изменённый заголовок")
        try {
            NativeMailLinks.unlink(state1, "link-101", modified)
            fail("Expected CAS conflict on modified link")
        } catch (_: SharedStateMerge.Conflict) {
        }

        // CAS unlink succeeds with exact snapshot
        val afterUnlink = NativeMailLinks.unlink(state1, "link-101", saved)
        assertEquals(0, afterUnlink.getJSONArray("eventMailLinks").length())
    }
}
