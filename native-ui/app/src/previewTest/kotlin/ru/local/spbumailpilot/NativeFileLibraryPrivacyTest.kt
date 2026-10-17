package ru.local.spbumailpilot
import org.junit.Assert.*
import org.junit.Test
class NativeFileLibraryPrivacyTest {
    @Test fun diagnosticOmitsOwnerQueryNamesAndSource(){
        val state=FileLibraryState(open=true,owner="private-owner",folder="private-folder",query="private-query",rows=listOf(org.json.JSONObject().put("name","private-file").put("message",org.json.JSONObject().put("subject","private-subject"))),openedFile=org.json.JSONObject().put("file","private-path"))
        assertFalse(state.toString().contains("private-"));assertTrue(state.toString().contains("count=1"))
    }
}
