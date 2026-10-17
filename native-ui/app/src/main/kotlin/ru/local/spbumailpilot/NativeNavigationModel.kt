package ru.local.spbumailpilot
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.compose.runtime.*

private const val PENDING_NAVIGATION_KEY="pendingNativeNavigation"

/** Never trusts the persisted runtime type: only a String is parsed, anything unusable is dropped. */
private fun restorePendingNativeNavigation(saved: SavedStateHandle): NativeOpenTarget? {
    val raw=saved.get<Any?>(PENDING_NAVIGATION_KEY)
    val target=if(raw is String)NativeOpenTarget.parse(raw)else null
    if(target==null&&raw!=null)saved.remove<Any?>(PENDING_NAVIGATION_KEY)
    return target
}

internal class NativeNavigationModel(private val saved: SavedStateHandle):ViewModel() {
    var pending by mutableStateOf(restorePendingNativeNavigation(saved));private set
    fun skipStartup(){saved["nativeStartupHandled"]=true}
    fun initialRoute(owner:String,preferred:String,canOpen:Boolean):String? {
        if(saved.get<Any?>("nativeStartupHandled")==true||!NativeOpenTarget.account(owner))return null
        skipStartup()
        if(pending!=null||!canOpen)return null
        return if(preferred=="calendar")"calendar" else "mail"
    }
    fun receive(target: NativeOpenTarget?){if(target!=null){skipStartup();pending=target;saved[PENDING_NAVIGATION_KEY]=target.encode()}}
    fun consume(target: NativeOpenTarget){if(pending===target){pending=null;saved[PENDING_NAVIGATION_KEY]=null}}
}
