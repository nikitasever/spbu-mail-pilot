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
}
