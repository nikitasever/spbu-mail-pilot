package ru.local.spbumailpilot
import org.junit.Assert.*
import org.junit.Test
class NativeRelatedPrivacyTest {
    @Test fun diagnosticDoesNotExposeCriteriaOrResults(){
        val state=RelatedSearchState(enabled=true,owner="private-owner",criteria=NativeMailSearch.Criteria("private-query","private-sender","","",false,false),
            people=listOf(org.json.JSONObject().put("sender","private-contact")),events=listOf(org.json.JSONObject().put("title","private-event")),
            peopleError="private-error",eventsError="private-error")
        assertFalse(state.toString().contains("private-"))
        val export=ExportState(name="private-file",failureNames=listOf("private-failure"),notice="private-notice")
        assertFalse(export.toString().contains("private-"))
        val calls=NativeCallsState(owner="private-owner",number="private-number",rows=listOf(org.json.JSONObject().put("number","private-history")),notice="private-notice")
        assertFalse(calls.toString().contains("private-"))
    }
}
