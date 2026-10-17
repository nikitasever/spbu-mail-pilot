package ru.local.spbumailpilot
import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.*
import org.junit.Test

class NativeNavigationTest {
    private val key="pendingNativeNavigation"
    private val owner="account-"+"a".repeat(64)
    @Test fun pendingSurvivesAndConsumesExactlyOnce(){
        val saved=SavedStateHandle()
        val model=NativeNavigationModel(saved)
        val first=NativeOpenTarget.from(null,"calendar",owner,false,null)!!
        val second=NativeOpenTarget.from(null,null,null,true,owner)!!
        model.receive(first)
        model.receive(null)
        assertSame(first,model.pending)
        model.receive(second)
        model.consume(first)
        assertSame(second,model.pending)
        val restored=NativeNavigationModel(saved)
        assertEquals("tasks",restored.pending?.kind)
        restored.consume(restored.pending!!)
        assertNull(restored.pending)
        assertNull(saved.get<String>("pendingNativeNavigation"))
    }

    @Test fun nonStringSavedValueIsIgnoredAndCleared(){
        val saved=SavedStateHandle()
        saved[key]=7
        val model=NativeNavigationModel(saved)
        assertNull(model.pending)
        assertFalse("non-string value must be dropped",saved.contains(key))
        assertNull(NativeNavigationModel(saved).pending)
    }

    @Test fun malformedSavedValueIsIgnoredAndCleared(){
        val saved=SavedStateHandle()
        saved[key]="{not valid json"
        val model=NativeNavigationModel(saved)
        assertNull(model.pending)
        assertFalse("malformed value must be dropped",saved.contains(key))
    }

    @Test fun invalidRouteSavedValueIsIgnoredAndCleared(){
        val saved=SavedStateHandle()
        saved[key]="{\"kind\":\"bogus\",\"account\":\"\"}"
        val model=NativeNavigationModel(saved)
        assertNull(model.pending)
        assertFalse("invalid route must be dropped",saved.contains(key))
    }

    @Test fun invalidAccountSavedValueIsIgnoredAndCleared(){
        val saved=SavedStateHandle()
        saved[key]="{\"kind\":\"calendar\",\"account\":\"account-xyz\"}"
        val model=NativeNavigationModel(saved)
        assertNull(model.pending)
        assertFalse("invalid account must be dropped",saved.contains(key))
    }

    @Test fun oversizedSavedValueIsIgnoredAndCleared(){
        val saved=SavedStateHandle()
        saved[key]="{\"kind\":\"calendar\",\"account\":\""+"a".repeat(9000)+"\"}"
        val model=NativeNavigationModel(saved)
        assertNull(model.pending)
        assertFalse("oversized value must be dropped",saved.contains(key))
    }

    @Test fun validSavedValueIsRestoredAndKept(){
        val saved=SavedStateHandle()
        saved[key]=NativeOpenTarget.from(null,null,null,true,owner)!!.encode()
        val model=NativeNavigationModel(saved)
        assertEquals("tasks",model.pending?.kind)
        assertEquals(owner,model.pending?.account)
        assertTrue("valid value must survive for later recreation",saved.contains(key))
    }

    @Test fun equalValuedNewerRequestSurvivesOlderConsume(){
        val saved=SavedStateHandle()
        val model=NativeNavigationModel(saved)
        val older=NativeOpenTarget.from(null,"calendar",owner,false,null)!!
        val newer=NativeOpenTarget.from(null,"calendar",owner,false,null)!!
        assertNotSame(older,newer)
        assertEquals(older.kind,newer.kind)
        assertEquals(older.account,newer.account)
        model.receive(older)
        model.receive(newer)
        model.consume(older)
        assertSame("older equal-valued request must not clear newer",newer,model.pending)
        model.consume(newer)
        assertNull(model.pending)
        assertNull(saved.get<Any?>(key))
    }
}
