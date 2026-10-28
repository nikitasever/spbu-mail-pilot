package ru.local.spbumailpilot
import org.junit.Assert.*
import org.junit.Test
class NativeWatchRefreshTest {
    @Test fun refreshOnlyIdleOnlineList(){
        val base=InboxState(owner="account-"+"a".repeat(64),pageOffline=false)
        assertTrue(base.refreshableFromWatch())
        assertFalse(base.copy(owner="").refreshableFromWatch())
        assertFalse(base.copy(busy=true).refreshableFromWatch())
        assertFalse(base.copy(pageOffline=true).refreshableFromWatch())
        assertFalse(base.copy(linked=true).refreshableFromWatch())
        assertFalse(base.copy(searchOpen=true).refreshableFromWatch())
        assertFalse(base.copy(foldersOpen=true).refreshableFromWatch())
        assertFalse(base.copy(moveOpen=true).refreshableFromWatch())
        val ref=MailRef("INBOX","1","7")
        assertFalse(base.copy(selectedRefs=setOf(ref)).refreshableFromWatch())
        assertFalse(base.copy(selected=NativeMail(ref,"","","","",false)).refreshableFromWatch())
        assertFalse(base.copy(thread=ThreadNav(anchor=ThreadAnchor("INBOX","7","1"))).refreshableFromWatch())
    }
}
