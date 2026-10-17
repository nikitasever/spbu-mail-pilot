package ru.local.spbumailpilot
import org.junit.Assert.*
import org.junit.Test
class NativeContactSafPrivacyTest {
    @Test fun contactsDiagnosticOmitsFileAndContactContents(){
        val value=ContactsState(owner="private-owner",account="private-account",query="private-query",people=listOf(ContactPerson("private-name","private-email")),importText="private-text",importFileName="private-file",importSelected=setOf("private-selected"),clearHistoryOpen=true).toString()
        assertFalse(value.contains("private-"));assertTrue(value.contains("count=1"))
    }
}
