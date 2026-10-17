package ru.local.spbumailpilot
import org.json.JSONObject
import org.json.JSONArray
import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.compose.runtime.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

internal data class NativeDraftsState(val open:Boolean=false,val owner:String="",val busy:Boolean=false,
    val active:String="",val items:List<NativeDraft> = emptyList(),val error:String?=null,
    val legacyOpen:Boolean=false,val legacyItems:List<JSONObject> = emptyList(),val legacyOmitted:Int=0,val importId:String?=null,val copyBytes:Long=0,val copying:Boolean=false){
    override fun toString()="NativeDraftsState(open=$open,busy=$busy,count=${items.size})"
}
internal class NativeDraftsModel(app:Application):AndroidViewModel(app){
    var state by mutableStateOf(NativeDraftsState());private set
    private val context=app.applicationContext
    private val worker=Executors.newSingleThreadExecutor()
    private val main=Handler(Looper.getMainLooper())
    private val serial=AtomicLong()
    @Volatile private var foreground=false
    @Volatile private var closed=false
    private fun allowed(owner:String){check(foreground&&!closed&&!AppLock.locked(context));check(CredentialVault(context).load()?.let {NoticeCenter.scope(it)}==owner)}
    fun resume(){foreground=true;if(state.open)load()}
    fun pause(){foreground=false;serial.incrementAndGet();state=state.copy(busy=false,copying=false,importId=null)}
    fun open(owner:String){if(!foreground||state.open||!owner.matches(Regex("account-[0-9a-f]{64}")))return;state=NativeDraftsState(open=true,owner=owner);load()}
    fun back(){if(!state.busy){serial.incrementAndGet();if(state.legacyOpen){state=state.copy(legacyOpen=false,legacyItems=emptyList(),importId=null,error=null);load()}else state=NativeDraftsState()}}
    fun load(){
        if(state.legacyOpen){loadLegacy();return}
        if(!foreground||!state.open||state.busy||AppLock.locked(context))return
        val owner=state.owner;val ticket=serial.incrementAndGet();state=state.copy(busy=true,error=null)
        worker.execute {try{
            allowed(owner)
            val (active,items)=synchronized(NativeDraftStore.gate){val store=NativeDraftStore(context,owner);Pair(store.read()?.mail?.getString("id") ?: "",store.list())}
            main.post {if(!closed&&foreground&&serial.get()==ticket&&!AppLock.locked(context))state=state.copy(busy=false,active=active,items=items)}
        }catch(_:Exception){main.post {if(!closed&&serial.get()==ticket)state=state.copy(busy=false,error="Не удалось прочитать черновики. Сохранённые данные не изменены.")}}}
    }
    fun legacy(){if(foreground&&state.open&&!state.busy&&!AppLock.locked(context)){state=state.copy(legacyOpen=true,error=null);loadLegacy()}}
    private fun loadLegacy(){
        if(!foreground||!state.open||state.busy||AppLock.locked(context))return
        val owner=state.owner;val ticket=serial.incrementAndGet();state=state.copy(busy=true,copying=false,importId=null,error=null,copyBytes=0)
        worker.execute {try{
            allowed(owner);val login=CredentialVault(context).load() ?: error("Account unavailable");check(NoticeCenter.scope(login)==owner)
            val originals=synchronized(ReplyReminderCenter::class.java){JSONObject(NoticeCenter.state(context,login).getString("state","{}")!!).optJSONArray("drafts") ?: JSONArray()}
            val protected=ScheduledMail.list(context,login).getJSONArray("protectedDraftIds")
            val ids=(0 until protected.length()).map {protected.getString(it)}.toSet()
            val candidates=NativeLegacyDraftPlan.candidates(originals,ids)
            val items=(0 until candidates.length()).map {JSONObject(candidates.getJSONObject(it).toString())}
            val omitted=originals.length()-items.size
            main.post {if(!closed&&foreground&&serial.get()==ticket&&!AppLock.locked(context))state=state.copy(busy=false,legacyItems=items,legacyOmitted=omitted)}
        }catch(_:Exception){main.post {if(!closed&&serial.get()==ticket)state=state.copy(busy=false,error="Не удалось прочитать прежние черновики и проверить очередь. Копирование недоступно; исходники не изменены.")}}}
    }
    fun cancelImport(){if(state.copying){serial.incrementAndGet();state=state.copy(busy=false,copying=false,error="Запрошена остановка. Уже сохранённая копия останется в новых черновиках; исходник не изменён. Обновите список перед повтором.")}}
    fun requestImport(id:String){if(foreground&&!state.busy&&state.legacyOpen&&!AppLock.locked(context)&&state.legacyItems.any {it.optString("id")==id})state=state.copy(importId=id)}
    fun dismissImport(){if(!state.busy)state=state.copy(importId=null)}
    fun importLegacy(open:(String,String)->Unit){
        val id=state.importId ?: return
        if(!foreground||state.busy||!state.legacyOpen||AppLock.locked(context))return
        val source=state.legacyItems.find {it.optString("id")==id}?.let {JSONObject(it.toString())} ?: return
        val owner=state.owner;val ticket=serial.incrementAndGet();state=state.copy(busy=true,copying=true,importId=null,error=null,copyBytes=0)
        worker.execute {try{
            allowed(owner);val login=CredentialVault(context).load() ?: error("Account unavailable");check(NoticeCenter.scope(login)==owner)
            var lastProgress=0L
            val nativeId=NativeLegacyDraftImport.run(context,owner,login,source,{
                check(foreground&&!closed&&serial.get()==ticket&&!AppLock.locked(context))
            }){bytes->val now=android.os.SystemClock.elapsedRealtime();if(now-lastProgress>=120){lastProgress=now;main.post {if(!closed&&serial.get()==ticket)state=state.copy(copyBytes=bytes)}}}
            main.post {if(!closed&&foreground&&serial.get()==ticket&&!AppLock.locked(context)){
                runCatching {allowed(owner)}.onSuccess {state=NativeDraftsState();open(owner,nativeId)}.onFailure {state=state.copy(busy=false,copying=false,error="Копия сохранена. Откройте список в прежнем ящике.")}
            }}
        }catch(_:Exception){main.post {if(!closed&&serial.get()==ticket)state=state.copy(busy=false,copying=false,error="Копирование не подтверждено. Черновик мог измениться, попасть в очередь или иметь недоступные файлы. Проверьте лимит 50 черновиков и свободное место. Исходник не изменён; повтор найдёт уже сохранённую копию.")}}}
    }
    fun choose(id:String,open:(String,String)->Unit){
        if(!foreground||!state.open||state.busy||AppLock.locked(context))return
        val snapshot=state;val target=snapshot.items.find {it.mail.optString("id")==id} ?: return
        val active=snapshot.items.find {it.mail.optString("id")==snapshot.active}
        val ticket=serial.incrementAndGet();state=state.copy(busy=true,error=null)
        worker.execute {try{
            allowed(snapshot.owner)
            synchronized(NativeDraftStore.gate){allowed(snapshot.owner);NativeDraftStore(context,snapshot.owner).select(active,target)}
            main.post {if(!closed&&foreground&&serial.get()==ticket&&!AppLock.locked(context)){
                runCatching {allowed(snapshot.owner)}.onSuccess {state=NativeDraftsState();open(snapshot.owner,id)}.onFailure {state=state.copy(busy=false,error="Активный ящик изменился. Откройте список заново.")}
            }}
        }catch(_:Exception){main.post {if(!closed&&serial.get()==ticket)state=state.copy(busy=false,error="Черновик изменился в другом окне или хранилище недоступно. Обновите список; данные не перезаписывались.")}}}
    }
    fun create(open:(String)->Unit){if(!foreground||!state.open||state.busy||AppLock.locked(context)||state.items.size>=NativeDraftBook.LIMIT)return
        val owner=state.owner
        runCatching {allowed(owner)}.onSuccess {state=NativeDraftsState();open(owner)}.onFailure {state=state.copy(error="Активный ящик изменился.")}
    }
    override fun onCleared(){foreground=false;closed=true;serial.incrementAndGet();worker.shutdown()}
}
