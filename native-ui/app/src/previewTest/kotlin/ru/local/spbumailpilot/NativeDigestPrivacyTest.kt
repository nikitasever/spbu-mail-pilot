package ru.local.spbumailpilot
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
class NativeDigestPrivacyTest {
    @Test fun diagnosticDoesNotExposeCandidateOrPreparedText(){
        val mail=NativeMail(MailRef("INBOX","1","7"),"private-sender","private-subject","private-preview","",false)
        val state=NativeDigestState(owner="private-owner",candidates=listOf(mail),selected=setOf(mail.ref),items=listOf(JSONObject().put("text","private-body")),overview="private-overview")
        assertFalse(state.toString().contains("private-"))
        assertFalse(state.toString().contains("uid="))
    }
    @Test fun preparedHeaderKeepsExactlyTheSourceReaderNeeds(){
        val owner="account-"+"a".repeat(64)
        val ref=JSONObject().put("account",owner).put("folder","INBOX").put("uid","2").put("validity","9")
        val target=NativeDigestPlan.references(owner,"INBOX",org.json.JSONArray().put(ref))[0]
        val body=JSONObject().put("message",JSONObject(ref.toString()).put("subject","fixture").put("sender","fixture")).put("text","fixture")
        val source=NativeDigestPlan.source(owner,org.json.JSONArray().put(NativeDigestPlan.item(target,body)),1)
        assertNotNull(NativeMailLinks.target(owner,source))
        assertNull(NativeMailLinks.target("account-"+"b".repeat(64),source))
        assertEquals("2",source.getString("uid"))
    }
}
