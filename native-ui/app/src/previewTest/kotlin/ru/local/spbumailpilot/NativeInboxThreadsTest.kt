package ru.local.spbumailpilot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for the display bridge: grouping all nonsnoozed loaded rows first and retaining a whole
 * group when any member matches, plus the conversation-member resolution that supports singleton
 * survivors and never renders snoozed members.
 */
class NativeInboxThreadsTest {
    private val account = "fixture@example.invalid"

    private fun mail(uid: String, messageId: String, references: List<String> = emptyList(), unread: Boolean = false, attachments: Boolean? = null) =
        NativeMail(ref = MailRef("INBOX", uid, "7"), sender = "s$uid", subject = "t$uid", preview = "", date = "",
            unread = unread, hasAttachments = attachments, messageId = messageId, references = references)

    // Oldest chain: uid2 (root) <- uid4 <- uid6 (newest).
    private val newest = mail("6", "<c@example>", listOf("<b@example>"))
    private val middle = mail("4", "<b@example>", listOf("<a@example>"), unread = true, attachments = true)
    private val oldest = mail("2", "<a@example>")

    @Test fun groupRetainedWhenNonRepresentativeMemberMatches() {
        val rows = listOf(newest, middle, oldest)
        val display = buildDisplayRows(rows, account, true) { it.unread }
        assertEquals(1, display.size)
        val group = display[0] as InboxDisplayRow.Group
        assertEquals(3, group.members.size)
        assertEquals("2", group.anchor.uid) // whole loaded group is kept, not just the matching row
        assertEquals(1, group.members.count { it.unread })
        assertTrue(group.members.any { it.hasAttachments == true })
    }

    @Test fun disabledGroupingFiltersSingles() {
        val rows = listOf(newest, middle, oldest)
        val display = buildDisplayRows(rows, account, false) { it.unread }
        assertEquals(1, display.size)
        assertTrue(display[0] is InboxDisplayRow.Single)
        assertEquals("4", (display[0] as InboxDisplayRow.Single).mail.ref.uid)
    }

    @Test fun conversationMembersKeepSingletonSurvivorAndDropSnoozed() {
        val all = listOf(newest, middle, oldest)
        val anchor = oldest.ref // original anchor, now snoozed away
        // Caller removed the snoozed oldest member before display grouping.
        val displayRefs = buildDisplayRows(listOf(newest, middle), account, true)
            .flatMap { row -> when (row) {
                is InboxDisplayRow.Single -> listOf(refKey(row.mail.ref))
                is InboxDisplayRow.Group -> row.members.map { refKey(it.ref) }
            } }.toSet()
        val members = conversationMembers(all, account, anchor, displayRefs)
        assertEquals(listOf("6", "4"), members.map { it.ref.uid })
        assertTrue(members.none { it.ref.uid == "2" })
    }

    @Test fun conversationMembersSupportSingleEligibleSurvivor() {
        val all = listOf(newest, middle, oldest)
        val anchor = oldest.ref
        val displayRefs = setOf(refKey(middle.ref)) // only one member survives filter/snooze
        val members = conversationMembers(all, account, anchor, displayRefs)
        assertEquals(1, members.size)
        assertEquals("4", members[0].ref.uid)
    }

    @Test fun conversationMembersReturnEmptyWhenAnchorNotInChain() {
        val members = conversationMembers(listOf(newest), account, MailRef("INBOX", "999", "7"), setOf(refKey(newest.ref)))
        assertTrue(members.isEmpty())
        assertFalse(sameRef(newest.ref, oldest.ref))
    }
}
