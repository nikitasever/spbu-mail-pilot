package ru.local.spbumailpilot
import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.compose.runtime.*
import org.json.JSONObject
import java.util.concurrent.Executors
internal data class NativeCallsState(val open:Boolean=false,val owner:String="",val number:String="",val rows:List<JSONObject> = emptyList(),
    val busy:Boolean=false,val error:String?=null,val notice:String?=null){
    override fun toString()="NativeCallsState(open=$open,busy=$busy,count=${rows.size})"
}
internal class NativeCallsModel(app:Application):AndroidViewModel(app){
    private val context=app.applicationContext
    private val worker=Executors.newSingleThreadExecutor()
    private val main=Handler(Looper.getMainLooper())
    @Volatile private var foreground=false
    @Volatile private var closed=false
    @Volatile private var generation=0L
    var state by mutableStateOf(NativeCallsState());private set
    fun resume(){foreground=true;if(state.open)load()}
    fun pause(){foreground=false;generation++;state=state.copy(busy=false)}
    fun open(owner:String){if(!foreground||closed||AppLock.locked(context))return;state=if(state.owner==owner)state.copy(open=true) else NativeCallsState(open=true,owner=owner);load()}
    fun back(){generation++;state=state.copy(open=false,busy=false)}
    fun number(value:String){if(value.length<=60)state=state.copy(number=value,error=null)}
    private fun allowed(owner:String):SavedAccounts.Login{check(foreground&&!closed&&!AppLock.locked(context));val login=CredentialVault(context).load() ?: error("No account");check(NoticeCenter.scope(login)==owner);return login}
    private fun valid(ticket:Long,owner:String)=!closed&&foreground&&generation==ticket&&state.open&&state.owner==owner&&!AppLock.locked(context)
    fun load(){
        if(!foreground||closed||!state.open||AppLock.locked(context))return
        val owner=state.owner;val ticket=++generation;state=state.copy(busy=true,error=null)
        worker.execute {try{
            val login=allowed(owner)
            val rows=synchronized(ReplyReminderCenter::class.java){val all=CallHistory.rows(JSONObject(NoticeCenter.state(context,login).getString("state","{}")!!));(0 until all.length()).map {JSONObject(all.getJSONObject(it).toString())}}
            allowed(owner);main.post {if(valid(ticket,owner))state=state.copy(rows=rows,busy=false)}
        }catch(_:Exception){main.post {if(valid(ticket,owner))state=state.copy(busy=false,error="Не удалось прочитать историю приложения. Повторите загрузку.")}}}
    }
    fun dial(ui:Context,number:String){
        if(state.busy||!foreground||closed||!state.open||AppLock.locked(context))return
        val owner=state.owner
        try{
            allowed(owner);val clean=PhoneNumbers.normalize(number)
            state=state.copy(busy=true,error=null)
            NativeDialer.dial(ui,owner,clean){notice->if(!closed&&state.owner==owner){state=state.copy(busy=false,notice=notice);if(foreground&&state.open&&!AppLock.locked(context))load()}}
        }catch(_:Exception){state=state.copy(busy=false,error="Не удалось открыть «Телефон». Укажите полный номер: от 5 до 15 цифр, знак + в начале необязателен.")}
    }
    override fun onCleared(){closed=true;foreground=false;generation++;worker.shutdown()}
}
