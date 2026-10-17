package ru.local.spbumailpilot
import org.junit.Assert.*
import org.junit.Test
class NativeWritingAiPrivacyTest {
    @Test fun composerDiagnosticOmitsDraftEnvelopeAndAiText(){
        val state=ComposerState(to="private-to",bcc="private-bcc",body="private-body",aiText="private-prompt",aiInitial="private-initial")
        assertFalse(state.toString().contains("private-"))
    }
    @Test fun handoffBuilderDoesNotReceiveEnvelopeOrFiles(){
        val state=ComposerState(to="private-to",cc="private-cc",bcc="private-bcc",subject="fixture",body="fixture-body")
        val prompt=NativeWritingAi.prompt("reply",state.subject,state.body,"")
        assertFalse(prompt.contains("private-"));assertTrue(prompt.contains("fixture-body"))
        assertEquals("fixture-body",state.body)
    }
}
