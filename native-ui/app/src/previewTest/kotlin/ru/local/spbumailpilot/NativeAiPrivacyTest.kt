package ru.local.spbumailpilot
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
class NativeAiPrivacyTest {
    @Test fun stateDiagnosticOmitsKeyAndAccount(){
        val state=NativeAiState(owner="private-owner",key="private-key",model="private-model",error="private-error")
        assertFalse(state.toString().contains("private-"))
        assertTrue(state.copy(busy=true).toString().contains("busy=true"))
    }
    @Test fun secretSnapshotDiagnosticOmitsValue(){
        val snapshot=AiVault.Snapshot("fixture-secret",Long.MAX_VALUE)
        assertFalse(snapshot.toString().contains("fixture-secret"));assertFalse(snapshot.current())
    }
    @Test fun modelIsNotASecretOrCustomEndpoint(){
        val state=JSONObject().put("settings",JSONObject().put("geminiModel","fixture-model"))
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/fixture-model:generateContent",AiConfiguration.endpoint(AiConfiguration.model(state)))
        assertThrows(Exception::class.java){AiConfiguration.model(JSONObject().put("settings",JSONObject().put("geminiModel",true)))}
    }
}
