package ru.local.spbumailpilot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM unit tests for the pure conversation navigation reducer (no Android dependencies). */
class NativeThreadNavigationTest {
    private val scope = ThreadScope("mail@example.invalid", "INBOX", "", "all")
    private val anchor = ThreadAnchor("INBOX", "7", "4")

    @Test fun openKeepsScopeAndAnchor() {
        val nav = ThreadNavigation.open(ThreadNav(), scope, anchor)
        assertEquals(scope, nav.scope)
        assertEquals(anchor, nav.anchor)
        assertFalse(nav.readerFromThread)
    }

    @Test fun openRefusesInvalidScopeOrAnchor() {
        assertNull(ThreadNavigation.open(ThreadNav(), ThreadScope("mail@example.invalid", "", "", "all"), anchor).anchor)
        assertNull(ThreadNavigation.open(ThreadNav(), scope, ThreadAnchor("INBOX", "bad", "4")).anchor)
        assertNull(ThreadNavigation.open(ThreadNav(), scope, ThreadAnchor("", "7", "4")).anchor)
    }

    @Test fun openMemberAndBackReturnToConversationThenList() {
        val opened = ThreadNavigation.open(ThreadNav(), scope, anchor)
        val reading = ThreadNavigation.openMember(opened)
        assertTrue(reading.readerFromThread)
        val backToConversation = ThreadNavigation.back(reading)
        assertFalse(backToConversation.readerFromThread)
        assertEquals(anchor, backToConversation.anchor)
        val backToList = ThreadNavigation.back(backToConversation)
        assertNull(backToList.anchor)
        assertFalse(backToList.readerFromThread)
    }

    @Test fun backFromListOpenedReaderDoesNotInventConversation() {
        val reading = ThreadNavigation.openMember(ThreadNav())
        assertNull(reading.anchor)
        val cleared = ThreadNavigation.back(reading)
        assertNull(cleared.anchor)
    }

    @Test fun reScopeKeepsSameScopeAndResetsOnChange() {
        val opened = ThreadNavigation.open(ThreadNav(), scope, anchor)
        assertTrue(ThreadNavigation.reScope(opened, scope).anchor != null)
        assertNull(ThreadNavigation.reScope(opened, scope.copy(folder = "Archive")).anchor)
        assertNull(ThreadNavigation.reScope(opened, scope.copy(query = "term")).anchor)
        assertNull(ThreadNavigation.reScope(opened, scope.copy(filter = "unread")).anchor)
    }

    @Test fun activeMemberRequiresMatchingScopeAndAnchor() {
        val opened = ThreadNavigation.open(ThreadNav(), scope, anchor)
        assertTrue(ThreadNavigation.activeMember(opened, scope))
        assertFalse(ThreadNavigation.activeMember(opened, scope.copy(query = "term")))
        assertFalse(ThreadNavigation.activeMember(opened, scope.copy(folder = "Archive")))
        assertFalse(ThreadNavigation.activeMember(ThreadNav(scope = scope), scope))
    }

    @Test fun clearKeepsOnlyScope() {
        val reading = ThreadNavigation.openMember(ThreadNavigation.open(ThreadNav(), scope, anchor))
        val cleared = ThreadNavigation.clear(reading)
        assertEquals(scope, cleared.scope)
        assertNull(cleared.anchor)
        assertFalse(cleared.readerFromThread)
    }

    @Test fun advancedSearchKeyIsolatesThreadScopeAndRedactsToString() {
        val critA = NativeMailSearch.Criteria("отчёт", "dean@spbu.ru", "2026-10-01", "2026-10-16", true, false)
        val critB = NativeMailSearch.Criteria("отчёт", "office@spbu.ru", "2026-10-01", "2026-10-16", true, false)
        val stateA = InboxState(owner = "account-1", folder = "INBOX", query = "отчёт", activeCriteria = critA, searchText = "секрет", searchFrom = "dean@spbu.ru")
        val stateB = stateA.copy(activeCriteria = critB)
        assertFalse(stateA.searchKey == stateB.searchKey)
        val scopeA = ThreadScope(stateA.owner, stateA.folder, stateA.searchKey, stateA.filter)
        val scopeB = ThreadScope(stateB.owner, stateB.folder, stateB.searchKey, stateB.filter)
        val opened = ThreadNavigation.open(ThreadNav(), scopeA, anchor)
        assertNull(ThreadNavigation.reScope(opened, scopeB).anchor)
        assertFalse(stateA.toString().contains("секрет"))
        assertFalse(stateA.toString().contains("dean@spbu.ru"))
    }

    @Test fun activeSelectionEnforcesScopeAndDisplayedRows() {
        val ref1 = MailRef("INBOX", "10", "7")
        val ref2 = MailRef("INBOX", "11", "7")
        val mail1 = NativeMail(ref1, "Alice", "Subj 1", "", "", true)
        val mail2 = NativeMail(ref2, "Bob", "Subj 2", "", "", false)
        val selScope = SelectionScope("account-1", "INBOX", "", "all")
        val state = InboxState(
            owner = "account-1",
            folder = "INBOX",
            rows = listOf(mail1, mail2),
            selectedRefs = setOf(ref1, ref2),
            selectionScope = selScope
        )
        assertEquals(setOf(ref1, ref2), state.activeSelection())
        // Changing filter to unread invalidates selection scope and hides read rows
        assertEquals(emptySet<MailRef>(), state.copy(filter = "unread").activeSelection())
        // Even if selectionScope matched unread, only displayedRows (unread mail1) remain selectable
        val unreadScope = selScope.copy(filter = "unread")
        assertEquals(setOf(ref1), state.copy(filter = "unread", selectionScope = unreadScope).activeSelection())
        // Changing owner, folder, or query invalidates activeSelection
        assertEquals(emptySet<MailRef>(), state.copy(owner = "account-2").activeSelection())
        assertEquals(emptySet<MailRef>(), state.copy(folder = "Sent").activeSelection())
        assertEquals(emptySet<MailRef>(), state.copy(query = "search").activeSelection())
    }
}

