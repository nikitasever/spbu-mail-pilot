package ru.local.spbumailpilot
import org.junit.Assert.*
import org.junit.Test
class NativeDraftCollectionPrivacyTest {
    @Test fun collectionStateDiagnosticOmitsMessagesAndOwner(){
        val owner="account-"+"a".repeat(64)
        val draft=NativeDraft.create(owner,null,"private-body")
        draft.mail.put("to","private-to").put("subject","private-subject").put("bcc","private-bcc")
        val text=NativeDraftsState(open=true,owner=owner,items=listOf(draft)).toString()
        assertFalse(text.contains("private-"));assertFalse(text.contains(owner));assertTrue(text.contains("count=1"))
    }
    @Test fun legacySnapshotsAreExcludedFromDiagnostic(){
        val source=org.json.JSONObject().put("id","private-id").put("body","private-body").put("bcc","private-bcc")
        val text=NativeDraftsState(legacyOpen=true,legacyItems=listOf(source),importId="private-id").toString()
        assertFalse(text.contains("private-"))
    }
    @Test fun serverDraftListDiagnosticOmitsEnvelopeAndOwner(){
        val text=ServerDraftsState(open=true,owner="private-owner",rows=listOf(org.json.JSONObject().put("subject","private-subject").put("bcc","private-bcc")),confirm="private-uid").toString()
        assertFalse(text.contains("private-"));assertTrue(text.contains("count=1"))
    }
}
