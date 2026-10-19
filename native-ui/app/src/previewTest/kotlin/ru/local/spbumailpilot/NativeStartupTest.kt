package ru.local.spbumailpilot
import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.*
import org.junit.Test
class NativeStartupTest {
    private val owner="account-"+"a".repeat(64)
    @Test fun preferredCalendarOnlyOnce(){val model=NativeNavigationModel(SavedStateHandle());assertEquals("calendar",model.initialRoute(owner,"calendar",true));assertNull(model.initialRoute(owner,"calendar",true))}
    @Test fun notificationWinsEvenAfterConsumption(){val model=NativeNavigationModel(SavedStateHandle());val target=NativeOpenTarget.from(null,"mail",owner,false,null)!!;model.receive(target);model.consume(target);assertNull(model.initialRoute(owner,"calendar",true))}
    @Test fun restoredScreenWins(){val saved=SavedStateHandle();val model=NativeNavigationModel(saved);model.skipStartup();assertNull(NativeNavigationModel(saved).initialRoute(owner,"calendar",true))}
    @Test fun userEditorWinsAndIsNotOverriddenLater(){val model=NativeNavigationModel(SavedStateHandle());assertNull(model.initialRoute(owner,"calendar",false));assertNull(model.initialRoute(owner,"calendar",true))}
    @Test fun noAccountDoesNotConsumePreference(){val model=NativeNavigationModel(SavedStateHandle());assertNull(model.initialRoute("","calendar",true));assertEquals("calendar",model.initialRoute(owner,"calendar",true))}
    @Test fun unknownPreferenceFallsBackToMail(){assertEquals("mail",NativeNavigationModel(SavedStateHandle()).initialRoute(owner,"unknown",true))}
}
