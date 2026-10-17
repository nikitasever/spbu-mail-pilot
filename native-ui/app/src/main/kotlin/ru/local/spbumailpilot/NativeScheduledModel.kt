package ru.local.spbumailpilot

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.compose.runtime.*
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

internal data class ScheduledQueueState(val open:Boolean=false,val owner:String="",val busy:Boolean=false,
    val items:List<JSONObject> = emptyList(),val confirm:String?=null,val returnId:String?=null,val error:String?=null,val notice:String?=null) {
    override fun toString()="ScheduledQueueState(open=$open,busy=$busy,count=${items.size})"
}
internal class NativeScheduledModel(app:Application):AndroidViewModel(app){
    var state by mutableStateOf(ScheduledQueueState());private set
    private val context=app.applicationContext
    private val worker=Executors.newSingleThreadExecutor()
    private val main=Handler(Looper.getMainLooper())
    private val serial=AtomicLong()
    @Volatile private var foreground=false
    @Volatile private var closed=false
    private fun allowed(owner:String):SavedAccounts.Login {
        check(foreground&&!closed&&!AppLock.locked(context))
        val login=CredentialVault(context).load() ?: error("Account unavailable")
        check(NoticeCenter.scope(login)==owner);return login
    }
    fun resume(){foreground=true;if(state.open)load()}
    fun pause(){foreground=false;serial.incrementAndGet();state=state.copy(busy=false,confirm=null,returnId=null)}
    fun open(owner:String){if(!foreground||state.open||!owner.matches(Regex("account-[0-9a-f]{64}")))return;state=ScheduledQueueState(open=true,owner=owner);load()}
    fun back(){if(!state.busy){serial.incrementAndGet();state=ScheduledQueueState()}}
    fun load(){
        if(!foreground||!state.open||state.busy||AppLock.locked(context))return
        val owner=state.owner;val ticket=serial.incrementAndGet();state=state.copy(busy=true,confirm=null,returnId=null,error=null)
        worker.execute {try{
            val array=ScheduledMail.list(context,allowed(owner)).getJSONArray("items")
            val items=(0 until array.length()).map {JSONObject(array.getJSONObject(it).toString())}
            main.post {if(!closed&&foreground&&serial.get()==ticket&&state.owner==owner&&!AppLock.locked(context))state=state.copy(busy=false,items=items)}
        }catch(_:Exception){main.post {if(!closed&&serial.get()==ticket)state=state.copy(busy=false,error="Не удалось прочитать очередь. Сохранённые письма не изменены.")}}}
    }
    fun requestCancel(id:String){if(foreground&&!AppLock.locked(context)&&!state.busy&&state.items.any {it.optString("id")==id&&ScheduledPolicy.cancellable(it.optString("state"))})state=state.copy(confirm=id)}
    fun dismiss(){if(!state.busy)state=state.copy(confirm=null,returnId=null)}
    fun requestReturn(id:String){if(foreground&&!AppLock.locked(context)&&!state.busy&&state.items.any {it.optString("id")==id&&it.optString("state")=="cancelled"&&it.optBoolean("recoverable")})state=state.copy(returnId=id)}
    fun restore(onRestored:(String,String)->Unit){
        val id=state.returnId ?: return
        if(!foreground||state.busy||AppLock.locked(context))return
        val owner=state.owner;val ticket=serial.incrementAndGet();state=state.copy(busy=true,returnId=null,error=null)
        worker.execute {try{
            val draftId=NativeScheduledRestore.run(context,owner,allowed(owner),id)
            main.post {if(!closed&&foreground&&serial.get()==ticket&&state.owner==owner&&!AppLock.locked(context)){
                try{allowed(owner);state=ScheduledQueueState();onRestored(owner,draftId)}catch(_:Exception){state=state.copy(busy=false,error="Черновик сохранён, но активный ящик изменился. Откройте редактор в прежнем ящике.")}
            }}
        }catch(_:Exception){main.post {if(!closed&&serial.get()==ticket)state=state.copy(busy=false,error="Не удалось вернуть письмо. Проверьте место на телефоне и лимит черновиков, затем повторите. Копия в очереди или уже сохранённый нативный черновик остаются.")}}}
    }
    fun cancel(){
        val id=state.confirm ?: return
        if(!foreground||state.busy||AppLock.locked(context))return
        val owner=state.owner;val ticket=serial.incrementAndGet();state=state.copy(busy=true,confirm=null,error=null)
        worker.execute {
            var notice:String?=null;var failure:String?=null
            try {val result=ScheduledMail.cancel(context,allowed(owner),id)
                notice=result.optString("warning").ifBlank {"Отправка отменена. Копия письма сохранена; её можно вернуть в редактор."}
            }catch(_:Exception){failure="Отмена не подтверждена: отправка могла уже начаться. Обновите очередь и проверьте результат; повторная отправка не выполнялась."}
            main.post {if(!closed&&foreground&&serial.get()==ticket&&state.owner==owner&&!AppLock.locked(context)){
                state=state.copy(busy=false,notice=notice,error=failure)
                if(failure==null)load()
            }}
        }
    }
    override fun onCleared(){foreground=false;closed=true;serial.incrementAndGet();worker.shutdown()}
}
