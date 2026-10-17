package ru.local.spbumailpilot
import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.compose.runtime.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

internal data class ServerDraftsState(val open:Boolean=false,val owner:String="",val busy:Boolean=false,
    val folder:String="",val rows:List<JSONObject> = emptyList(),val total:Int=0,val more:Boolean=false,val expired:Boolean=true,
    val confirm:String?=null,val copying:Boolean=false,val bytes:Long=0,val error:String?=null,val notice:String?=null){
    override fun toString()="ServerDraftsState(open=$open,busy=$busy,count=${rows.size})"
}
internal class NativeServerDraftsModel(app:Application):AndroidViewModel(app){
    var state by mutableStateOf(ServerDraftsState());private set
    private val context=app.applicationContext
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    private val closer=Executors.newSingleThreadExecutor()
    private val gate=NativeReadGate(closer)
    private val serial=AtomicLong()
    private var epoch=0L
    private var reader:MailReader?=null // worker only
    private var page:NativeMailPage?=null // worker only
    @Volatile private var foreground=false
    @Volatile private var closed=false
    private fun allowed(owner:String):SavedAccounts.Login{check(foreground&&!closed&&!AppLock.locked(context));val login=CredentialVault(context).load() ?: error("Account unavailable");check(NoticeCenter.scope(login)==owner);return login}
    fun resume(){foreground=true}
    fun pause(){foreground=false;serial.incrementAndGet();gate.next();if(state.open)state=state.copy(busy=false,copying=false,confirm=null,expired=true,notice="Соединение закрыто. Обновите список перед продолжением.")}
    fun open(owner:String){if(!foreground||state.open||AppLock.locked(context)||!owner.matches(Regex("account-[0-9a-f]{64}")))return;state=ServerDraftsState(open=true,owner=owner);load()}
    fun back(){if(state.busy){cancel();return};serial.incrementAndGet();gate.next();state=ServerDraftsState()}
    fun cancel(){serial.incrementAndGet();gate.next();state=state.copy(busy=false,copying=false,confirm=null,expired=true,notice="Операция остановлена. Уже сохранённая копия останется в черновиках на телефоне. Исходник не изменён.")}
    fun load(more:Boolean=false){
        if(!foreground||!state.open||state.busy||AppLock.locked(context)||more&&(!state.more||state.expired))return
        val owner=state.owner;val operation=serial.incrementAndGet()
        val ticket=if(more)epoch else gate.next().also {epoch=it}
        state=state.copy(busy=true,error=null,notice=null,confirm=null)
        worker.execute {try{
            val login=allowed(owner);check(gate.current(ticket))
            if(!more){reader=null;page=null;val next=MailReader();reader=next;gate.attach(ticket,next);check(gate.current(ticket));next.connect(login.host,login.port,login.user,login.password)}
            val connection=reader ?: error("Page expired")
            val folder=if(more)page?.folder ?: error("Page expired") else ServerDrafts.folder(connection.draftStore()).fullName
            check(folder.isNotEmpty()&&folder.length<=2048&&folder.none {it.isISOControl()})
            allowed(owner);check(gate.current(ticket))
            val result=connection.page(folder,if(more)page!!.next else 0,if(more)page!!.snapshot else "")
            val next=if(more)page!!.append(result) else NativeMailPage.first(result,folder)
            page=next
            val rows=(0 until next.messages.length()).map {JSONObject(next.messages.getJSONObject(it).toString())}.filter {it.optBoolean("draft")&&!it.optBoolean("deleted")}
            main.post {if(!closed&&foreground&&serial.get()==operation&&gate.current(ticket)&&!AppLock.locked(context))state=state.copy(busy=false,folder=folder,rows=rows,total=next.total,more=next.more,expired=false)}
        }catch(_:Exception){if(gate.current(ticket))gate.next();main.post {if(!closed&&serial.get()==operation)state=state.copy(busy=false,expired=true,error="Не удалось получить серверные черновики. Проверьте сеть, вход и папку «Черновики»; обновите список. Сохранённые письма не изменены.")}}}
    }
    fun request(uid:String){if(foreground&&!state.busy&&!state.expired&&!AppLock.locked(context)&&state.rows.any {it.optString("uid")==uid})state=state.copy(confirm=uid)}
    fun dismiss(){if(!state.busy)state=state.copy(confirm=null)}
    fun copy(open:(String,String)->Unit){
        val uid=state.confirm ?: return
        if(!foreground||state.busy||state.expired||AppLock.locked(context))return
        val row=state.rows.find {it.optString("uid")==uid} ?: return
        val owner=state.owner;val folder=state.folder;val validity=row.getString("validity")
        val ticket=gate.next();epoch=ticket;val operation=serial.incrementAndGet()
        state=state.copy(busy=true,copying=true,confirm=null,bytes=0,error=null,notice=null,expired=true)
        worker.execute {
            var connection:MailReader?=null
            try{
                val login=allowed(owner);check(gate.current(ticket));reader=null;page=null
                connection=MailReader();gate.attach(ticket,connection);check(gate.current(ticket));connection.connect(login.host,login.port,login.user,login.password)
                val source=connection.serverDraft(folder,uid,validity)
                val nativeId=importCopy(owner,source,connection,operation,ticket)
                main.post {if(!closed&&foreground&&serial.get()==operation&&gate.current(ticket)&&!AppLock.locked(context)){
                    runCatching {allowed(owner)}.onSuccess {state=ServerDraftsState();open(owner,nativeId)}.onFailure {state=state.copy(busy=false,copying=false,error="Копия сохранена. Откройте черновики в нужном ящике.")}
                }}
            }catch(_:Exception){main.post {if(!closed&&serial.get()==operation)state=state.copy(busy=false,copying=false,error="Открытие не подтверждено. Черновик мог измениться, содержать неподдерживаемую структуру/кодировку, слишком большой текст или недоступные вложения. Исходник не изменён. Проверьте сеть, место и лимит черновиков; обновите список.")}}
            finally{connection?.let {gate.detach(ticket,it);runCatching {it.close()}}}
        }
    }
    private fun importCopy(owner:String,source:JSONObject,connection:MailReader,operation:Long,ticket:Long):String{
        fun alive(){check(foreground&&!closed&&serial.get()==operation&&gate.current(ticket)&&!AppLock.locked(context))}
        val reference=NativeServerDraftCopyPlan.ref(source)
        synchronized(NativeDraftStore.gate){
            alive();allowed(owner);val store=NativeDraftStore(context,owner);val stored=store.list()
            NativeServerDraftCopyPlan.existing(stored,source)?.let {existing->
                ScheduledFiles.verify(NativeAttachments.directory(File(context.filesDir,"native-attachments"),existing),existing.mail.getJSONArray("attachments"))
                store.select(store.read(),existing);return existing.mail.getString("id")
            }
            check(stored.size<NativeDraftBook.LIMIT)
        }
        val raw=source.getJSONObject("draft").getString("body")
        val text=if(source.getBoolean("html"))android.text.Html.fromHtml(raw.replace(Regex("(?is)<(script|style)\\b[^>]*>.*?(</\\1>|$)"),""),android.text.Html.FROM_HTML_MODE_COMPACT).toString() else raw
        val draft=NativeServerDraftCopyPlan.create(owner,source,UUID.randomUUID().toString(),text)
        val directory=NativeAttachments.directory(File(context.filesDir,"native-attachments"),draft)
        val created=mutableListOf<File>();var committed=false
        try{
            val entries=NativeServerDraftCopyPlan.files(source);val files=JSONArray();var completed=0L;var lastProgress=0L
            for(i in 0 until entries.length()){
                alive();allowed(owner);val entry=entries.getJSONObject(i)
                var fileBytes=0L
                val downloaded=connection.attachment(reference.getString("folder"),reference.getString("uid"),reference.getString("validity"),entry.getString("path"),directory,{!foreground||closed||serial.get()!=operation||!gate.current(ticket)||AppLock.locked(context)}){bytes->
                    fileBytes=maxOf(fileBytes,bytes);val now=android.os.SystemClock.elapsedRealtime();if(now-lastProgress>=120){lastProgress=now;val display=completed+fileBytes;main.post {if(!closed&&serial.get()==operation)state=state.copy(bytes=display)}}
                }
                val file=File(downloaded.getString("file"));created.add(file)
                check(file.canonicalFile.parentFile==directory.canonicalFile&&file.length()<=AttachmentFiles.MAX_BYTES)
                java.io.RandomAccessFile(file,"rw").use {it.fd.sync()}
                files.put(JSONObject().put("token",file.name).put("name",entry.getString("name")).put("size",file.length()));completed+=fileBytes
            }
            alive();allowed(owner)
            val current=connection.serverDraft(reference.getString("folder"),reference.getString("uid"),reference.getString("validity"))
            check(SharedStateMerge.same(source,current))
            synchronized(NativeDraftStore.gate){
                alive();allowed(owner);val store=NativeDraftStore(context,owner)
                NativeServerDraftCopyPlan.existing(store.list(),source)?.let {existing->ScheduledFiles.verify(NativeAttachments.directory(File(context.filesDir,"native-attachments"),existing),existing.mail.getJSONArray("attachments"));store.select(store.read(),existing);return existing.mail.getString("id")}
                store.create(NativeDraft(owner,NativeDraft.EDITING,JSONObject(draft.mail.toString()).put("attachments",files)));committed=true
            }
            return draft.mail.getString("id")
        }finally{
            if(!committed)synchronized(NativeDraftStore.gate){runCatching{
                val stored=NativeDraftStore(context,owner).find(draft.mail.getString("id"));val kept=stored?.mail?.getJSONArray("attachments") ?: JSONArray()
                val tokens=(0 until kept.length()).map {kept.getJSONObject(it).getString("token")}.toSet()
                created.filter {it.name !in tokens}.forEach {it.delete()}
            }}
        }
    }
    override fun onCleared(){foreground=false;closed=true;serial.incrementAndGet();gate.next();worker.shutdown();closer.shutdown()}
}
