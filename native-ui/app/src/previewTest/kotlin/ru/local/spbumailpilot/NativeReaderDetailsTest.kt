package ru.local.spbumailpilot
import org.json.*
import org.junit.Assert.*
import org.junit.Test

class NativeReaderDetailsTest {
    @Test fun typedRolesRemainSeparateAndOriginalIsUntouched(){
        val source=JSONObject().put("toRecipients",JSONArray().put("One@example.invalid").put("one@example.invalid")).put("ccRecipients",JSONArray().put("copy@example.invalid")).put("replyToRecipients",JSONArray().put("reply@example.invalid"))
        val before=source.toString();val result=ReaderDetails.from(source)
        assertEquals(listOf("One@example.invalid"),result.to.values)
        assertEquals(listOf("copy@example.invalid"),result.cc.values)
        assertEquals(listOf("reply@example.invalid"),result.reply.values)
        assertEquals(before,source.toString())
    }
    @Test fun oldCombinedCacheDoesNotInventToCcOrBcc(){
        val result=ReaderDetails.from(JSONObject().put("recipients",JSONArray().put(JSONObject().put("email","old@example.invalid"))))
        assertFalse(result.to.available);assertFalse(result.cc.available)
        assertEquals(listOf("old@example.invalid"),result.fallback.values)
    }
    @Test fun authoritativeEmptyRolesDoNotFallBackToCombinedRecipients(){
        val result=ReaderDetails.from(JSONObject().put("toRecipients",JSONArray()).put("ccRecipients",JSONArray()).put("recipients",JSONArray().put(JSONObject().put("email","hidden@example.invalid"))))
        assertTrue(result.to.available);assertTrue(result.cc.available);assertFalse(result.fallback.available)
    }
    @Test fun wrongTypesInjectionAndMultipleAddressesAreNotCoerced(){
        val result=ReaderDetails.from(JSONObject().put("toRecipients",JSONArray().put(42).put("a@example.invalid\r\nBcc: other@example.invalid").put("a@example.invalid,b@example.invalid").put("ok@example.invalid")).put("ccRecipients","not-an-array"))
        assertEquals(listOf("ok@example.invalid"),result.to.values);assertTrue(result.to.incomplete)
        assertFalse(result.cc.available);assertTrue(result.cc.incomplete)
    }
    @Test fun quotedLocalPartRemainsReadableAndReplyFallbackWorks(){
        val result=ReaderDetails.from(JSONObject().put("toRecipients",JSONArray().put("\"quoted local\"@example.invalid")).put("replyTo","reply@example.invalid"))
        assertEquals(listOf("\"quoted local\"@example.invalid"),result.to.values)
        assertEquals(listOf("reply@example.invalid"),result.reply.values)
    }
    @Test fun longRecipientListsAreExplicitlyLimited(){
        val values=JSONArray();repeat(101){values.put("person$it@example.invalid")}
        val result=ReaderDetails.from(JSONObject().put("toRecipients",values))
        assertEquals(100,result.to.values.size);assertTrue(result.to.incomplete);assertTrue(result.incomplete)
    }
    @Test fun incompleteWarningPreservedAlongsideUidRawFromReferencesAndRawHeaders(){
        val source=JSONObject()
            .put("uid","54321")
            .put("rawFrom","\"Sender Name\" <sender@example.invalid>")
            .put("references","<ref-1@example.invalid> <ref-2@example.invalid>")
            .put("rawHeaders","From: \"Sender Name\" <sender@example.invalid>\r\nSubject: Test\u0007\nX-Custom: Value")
            .put("toRecipients",JSONArray().put("valid@example.invalid").put("malformed-address-entry"))
        val result=ReaderDetails.from(source,"fallback-uid")
        assertTrue(result.to.incomplete)
        assertTrue(result.incomplete)
        assertEquals(listOf("valid@example.invalid"),result.to.values)
        assertEquals("54321",result.uid)
        assertEquals("\"Sender Name\" <sender@example.invalid>",result.rawFrom)
        assertEquals("<ref-1@example.invalid> <ref-2@example.invalid>",result.references)
        assertEquals("From: \"Sender Name\" <sender@example.invalid>\nSubject: Test\nX-Custom: Value",result.rawHeaders)
    }
}
