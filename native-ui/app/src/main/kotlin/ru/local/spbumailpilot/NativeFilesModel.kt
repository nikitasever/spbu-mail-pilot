package ru.local.spbumailpilot
import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.compose.runtime.*
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

internal data class FileLibraryState(val open:Boolean=false,val owner:String="",val folder:String="",val query:String="",
    val rows:List<JSONObject> = emptyList(),val next:Int=0,val total:Int=0,val more:Boolean=false,val expired:Boolean=true,
    val downloadName:String="",val downloadStage:String="connecting",val downloadTotal:Long=-1,val notice:String?=null,
    val busy:Boolean=false,val downloading:Boolean=false,val bytes:Long=0,val openedFile:JSONObject?=null,val error:String?=null,
    val selected:Set<String> = emptySet(),val foldersOpen:Boolean=false,val foldersBusy:Boolean=false,
    val foldersOnline:Boolean=false,val folders:List<NativeFolders.Entry> = emptyList(),val foldersError:String?=null){
    override fun toString()="FileLibraryState(open=$open,busy=$busy,count=${rows.size})"
}
internal class NativeFilesModel(app:Application):AndroidViewModel(app){
    var state by mutableStateOf(FileLibraryState());private set
    private val context=app.applicationContext
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    private val closer=Executors.newCachedThreadPool()
    private val downloadWorker=Executors.newFixedThreadPool(2)
    private val pages=NativeReadGate(closer)
    private val transfers=NativeReadGate(closer)
    private val folderGate=NativeReadGate(closer)
    private val serial=AtomicLong()
    private var pageTicket=0L
    private var reader:MailReader?=null // worker only
    private var cursor:NativeFilePage?=null // worker only
    @Volatile private var foreground=false
    @Volatile private var closed=false
    private fun allowed(owner:String):SavedAccounts.Login{check(foreground&&!closed&&!AppLock.locked(context));val login=CredentialVault(context).load() ?: error("No account");check(NoticeCenter.scope(login)==owner);return login}
    fun resume(){foreground=true}
    fun pause(){foreground=false;serial.incrementAndGet();pages.next();transfers.next();closeFolders();discardFile();if(state.open)state=state.copy(busy=false,downloading=false,expired=true,selected=emptySet())}
    fun open(owner:String,folder:String){
        if(!foreground||state.open||AppLock.locked(context))return
        if(!owner.matches(Regex("account-[0-9a-f]{64}")))return
        if(runCatching {NativeServerDraftCopyPlan.ref(JSONObject().put("folder",folder).put("uid","1").put("validity","1"))}.isFailure)return
        state=FileLibraryState(open=true,owner=owner,folder=folder);load()
    }
    fun back(){closeFolders();cancel();state=FileLibraryState()}
    fun cancelDownload(){
        if(!state.downloading)return
        serial.incrementAndGet();transfers.next()
        state=state.copy(downloading=false,notice="Загрузка отменена.")
    }
    fun cancel(){serial.incrementAndGet();pages.next();transfers.next();discardFile();state=state.copy(busy=false,downloading=false,expired=true)}
    fun clearSelection(){state=state.copy(selected=emptySet())}
    fun showFolders(){
        if(!foreground||!state.open||state.busy||AppLock.locked(context))return
        state=state.copy(foldersOpen=true,foldersError=null)
        if(state.folders.isEmpty())loadFolders(false)
    }
    fun closeFolders(){folderGate.next();state=state.copy(foldersOpen=false,foldersBusy=false,foldersError=null)}
    fun loadFolders(online:Boolean){
        if(!foreground||!state.open||!state.foldersOpen||state.foldersBusy||AppLock.locked(context))return
        val owner=state.owner;val folder=state.folder;val ticket=folderGate.next()
        state=state.copy(foldersBusy=true,foldersError=null)
        worker.execute {try{
            val login=allowed(owner);check(folderGate.current(ticket))
            val names=if(online){
                val active=MailReader();folderGate.attach(ticket,active)
                try{check(folderGate.current(ticket));active.connect(login.host,login.port,login.user,login.password);active.folders()}
                finally{folderGate.detach(ticket,active);active.close()}
            }else{
                check(NoticeCenter.settings(context,login)?.optBoolean("offlineMail",true)!=false)
                OfflineMailStore(context,login).folders().getJSONArray("folders")
            }
            val entries=NativeFolders.list(names,folder);allowed(owner)
            main.post {if(!closed&&foreground&&folderGate.current(ticket)&&state.foldersOpen&&state.owner==owner&&!AppLock.locked(context))
                state=state.copy(folders=entries,foldersBusy=false,foldersOnline=online)}
        }catch(_:Exception){main.post {if(!closed&&foreground&&folderGate.current(ticket)&&state.foldersOpen&&state.owner==owner)
            state=state.copy(foldersBusy=false,folders=state.folders.ifEmpty {NativeFolders.list(org.json.JSONArray(),folder)},
                foldersError=if(online)"Не удалось получить папки. Проверьте сеть и повторите." else "Сохранённого списка нет. Загрузите папки с сервера.")}}}
    }
    fun chooseFolder(name:String){
        if(!foreground||!state.open||!state.foldersOpen||state.foldersBusy||state.busy||AppLock.locked(context)
            ||state.folders.none {it.name==name}||runCatching {allowed(state.owner)}.isFailure)return
        closeFolders();if(name==state.folder)return
        cancel();state=state.copy(folder=NativeFolders.canonical(name),rows=emptyList(),selected=emptySet(),next=0,total=0,more=false,error=null)
        load()
    }
    fun toggleSelection(key:String){
        if(state.busy||!foreground||!state.open||AppLock.locked(context)||row(key)==null)return
        val selected=state.selected
        if(key !in selected&&selected.size>=BulkAttachments.MAX_FILES){state=state.copy(error="Можно выбрать не больше 500 файлов.");return}
        state=state.copy(selected=if(key in selected)selected-key else selected+key,error=null)
    }
    fun selectLoaded(){
        if(state.busy||!foreground||!state.open||AppLock.locked(context))return
        state=state.copy(selected=state.rows.take(BulkAttachments.MAX_FILES).map {NativeFilePage.key(it)}.toSet(),
            error=if(state.rows.size>BulkAttachments.MAX_FILES)"Выбраны первые 500 загруженных файлов." else null)
    }
    fun selectedExport():NativeExportPlan?{
        if(state.busy||!foreground||!state.open||AppLock.locked(context))return null
        return try{
            allowed(state.owner)
            val rows=org.json.JSONArray();state.rows.forEach {rows.put(it)}
            NativeExportPlan.library(state.owner,state.folder,rows,state.selected)
        }catch(_:Exception){state=state.copy(selected=emptySet(),error="Список изменился. Выберите файлы заново.");null}
    }
    fun search(value:String){
        if(!foreground||!state.open||AppLock.locked(context))return
        val query=try{NativeMailSearch.query(value)}catch(_:Exception){state=state.copy(error="Имя файла: до 200 символов без переносов строк.");return}
        cancel();state=state.copy(query=query,rows=emptyList(),next=0,total=0,more=false,error=null,selected=emptySet());load()
    }
    fun load(more:Boolean=false){
        if(!foreground||!state.open||state.busy||AppLock.locked(context)||more&&(!state.more||state.expired))return
        discardFile();val owner=state.owner;val folder=state.folder;val query=state.query;val operation=serial.incrementAndGet()
        val ticket=if(more)pageTicket else pages.next().also {pageTicket=it}
        discardFile();transfers.next();state=state.copy(busy=true,downloading=false,error=null,notice=null,selected=if(more)state.selected else emptySet())
        worker.execute {try{
            val login=allowed(owner);check(pages.current(ticket))
            if(!more){reader=null;cursor=null;val connection=MailReader();reader=connection;pages.attach(ticket,connection);check(pages.current(ticket));connection.connect(login.host,login.port,login.user,login.password)}
            val active=reader ?: error("Expired cursor");val prior=cursor
            val result=active.files(folder,if(more)prior!!.next else 0,if(more)prior!!.snapshot else "",query)
            allowed(owner);check(pages.current(ticket))
            val next=if(more)prior!!.append(result) else NativeFilePage.first(result,folder,query);cursor=next
            val rows=(0 until next.files.length()).map {JSONObject(next.files.getJSONObject(it).toString())}
            main.post {if(valid(owner,operation)&&pages.current(ticket))state=state.copy(busy=false,rows=rows,next=next.next,total=next.total,more=next.more,expired=false)}
        }catch(_:Exception){if(pages.current(ticket))pages.next();main.post {if(valid(owner,operation))state=state.copy(busy=false,expired=true,error="Не удалось прочитать страницу файлов. Проверьте сеть и вход; обновите список. Если найдено слишком много файлов, уточните имя (лимит 5000). Уже показанные данные сохранены.")}}}
    }
    private fun valid(owner:String,operation:Long)=!closed&&foreground&&state.open&&state.owner==owner&&serial.get()==operation&&!AppLock.locked(context)
    private fun row(key:String):JSONObject?=state.rows.find {NativeFilePage.key(it)==key}
    fun mail(key:String):JSONObject?{
        if(state.busy||!foreground||AppLock.locked(context)||runCatching {allowed(state.owner)}.isFailure)return null
        val result=row(key)?.getJSONObject("message")?.let {JSONObject(it.toString()).put("account",state.owner)}
        discardFile();return result
    }
    fun file(key:String):Pair<MailRef,NativeFile>?{
        val message=mail(key) ?: return null;val entry=row(key) ?: return null
        return MailRef(state.folder,message.getString("uid"),message.getString("validity")) to NativeFile(entry.getString("name"),entry.getString("path"))
    }
    fun download(key:String,share:Boolean=false){
        val entry=file(key) ?: return
        discardFile();val owner=state.owner;val operation=serial.incrementAndGet();val ticket=transfers.next()
        state=state.copy(downloading=true,downloadName=entry.second.name,downloadStage="connecting",downloadTotal=-1,bytes=0,error=null,notice=null)
        downloadWorker.execute {
            var connection:MailReader?=null;var saved:File?=null
            try{
                val login=allowed(owner);check(transfers.current(ticket))
                val active=MailReader();connection=active;transfers.attach(ticket,active);check(transfers.current(ticket));active.connect(login.host,login.port,login.user,login.password)
                main.post {if(valid(owner,operation)&&transfers.current(ticket))state=state.copy(downloadStage="receiving")}
                var last=0L;val ref=entry.first
                val result=active.attachmentMeasured(ref.folder,ref.uid,ref.validity,entry.second.path,File(context.cacheDir,"attachments"),
                    {!foreground||closed||!transfers.current(ticket)||AppLock.locked(context)}){bytes,total->
                    val now=android.os.SystemClock.elapsedRealtime();if(now-last>=120){last=now;main.post {if(valid(owner,operation)&&transfers.current(ticket))state=state.copy(bytes=bytes,downloadTotal=total,downloadStage=if(bytes>0)"downloading" else "receiving")}}
                }
                val local=File(result.getString("file"));saved=local
                check(local.canonicalFile.parentFile==File(context.cacheDir,"attachments").canonicalFile&&local.isFile&&local.length()<=AttachmentFiles.MAX_BYTES)
                allowed(owner);check(transfers.current(ticket));val ready=result.put("_share",share)
                main.post {if(valid(owner,operation)&&transfers.current(ticket))state=state.copy(downloading=false,openedFile=ready) else local.delete()}
            }catch(failure:Exception){saved?.delete();main.post {if(valid(owner,operation))state=state.copy(downloading=false,error=NativeTransferFailure.describe(failure))}}
            finally{connection?.let {transfers.detach(ticket,it);runCatching {it.close()}}}
        }
    }
    fun takeFile():JSONObject?{
        val result=state.openedFile ?: return null
        if(!foreground||AppLock.locked(context)||runCatching {allowed(state.owner)}.isFailure){discardFile();return null}
        state=state.copy(openedFile=null);return result
    }
    fun fileError(){state=state.copy(error="Не удалось открыть файл. Установите просмотрщик этого формата или сохраните файл.")}
    fun linkError(){state=state.copy(error="Не удалось перейти к письму. Вернитесь в почту и повторите.")}
    private fun discardFile(){cancelDownload();state.openedFile?.let {runCatching {File(it.getString("file")).delete()}};state=state.copy(openedFile=null)}
    override fun onCleared(){foreground=false;closed=true;serial.incrementAndGet();pages.next();transfers.next();folderGate.next();discardFile();worker.shutdown();downloadWorker.shutdown();closer.shutdown()}
}
