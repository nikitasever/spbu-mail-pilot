package ru.local.spbumailpilot

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.compose.runtime.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

internal data class ContactPerson(val name: String,val email: String,val number: String="",val local: Boolean=false)
internal data class ContactsState(val open: Boolean=false,val owner: String="",val account: String="",val book: String="local",
    val query: String="",val applied: String="",val letter: String="",val people: List<ContactPerson> = emptyList(),
    val detail: ContactPerson?=null,val editor: JSONObject?=null,val initial: JSONObject?=null,val expected: JSONObject?=null,
    val leaveOpen: Boolean=false,val deleteOpen: Boolean=false,val busy: Boolean=false,val supported: Boolean=false,val more: Boolean=false,val error: String?=null,val notice: String?=null,
    val importOpen: Boolean=false,val importText: String="",val importPreview: NativeContactImport.Preview?=null,val importError: String?=null,
    val importSource: String="text",val importFileName: String?=null,val importSelected:Set<String> = emptySet(),val clearHistoryOpen:Boolean=false,val importPicking:Boolean=false,val importReading:Boolean=false,val importFileSelected:Boolean=false){
    override fun toString()="ContactsState(open=$open,busy=$busy,count=${people.size})"
}

/** Local edits share existing account state; remote directory stays read-only. Form text stays in memory. */

internal class NativeContactsModel(app: Application):AndroidViewModel(app) {
    private val context=app.applicationContext
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    private val directory=UniversityDirectory()
    private val fileCloser=Executors.newSingleThreadExecutor()
    private val fileGate=NativeReadGate(fileCloser)
    private var importSession=0L
    private var filePick:NativeContactFilePick?=null
    private var pendingFile:Pair<NativeContactFilePick,android.net.Uri>?=null
    private var fileUri:android.net.Uri?=null
    private var permissionPick:NativeContactFilePick?=null
    private var pendingSystem:NativeContactFilePick?=null
    private var historyExpected:JSONArray?=null
    private class FileRead:AutoCloseable {
        val signal=android.os.CancellationSignal()
        @Volatile var stream:java.io.InputStream?=null
        @Volatile var cursor:android.database.Cursor?=null
        override fun close(){runCatching {signal.cancel()};runCatching {stream?.close()};runCatching {cursor?.close()}}
    }
    @Volatile private var generation=0L
    @Volatile private var closed=false
    @Volatile private var foreground=false
    private var loaded=JSONObject()
    @Volatile private var mutating=false
    private var cookie=""
    private val seenCookies=mutableSetOf<String>()
    var state by mutableStateOf(ContactsState());private set
    fun resume(){foreground=true;if(AppLock.locked(context))return;if(pendingFile!=null)consumeFile() else if(pendingSystem!=null)consumeSystemPermission() else if(state.open&&!mutating&&!state.importOpen&&!state.importPicking)load(false)}
    fun pause(){historyExpected=null;foreground=false;generation++;fileGate.next();state=state.copy(busy=mutating,clearHistoryOpen=false,importReading=false,importError=if(state.importReading)"Чтение остановлено. Повторите предпросмотр." else state.importError,leaveOpen=false,deleteOpen=false)}
    fun open(owner: String){
        if(!foreground||closed||mutating||AppLock.locked(context)||owner.isBlank())return
        if(state.owner!=owner){clearImportFile();generation++;cookie="";seenCookies.clear();state=ContactsState(owner=owner)}
        state=state.copy(open=true)
        load(false) // Explicit opening reads local contacts only. Remote books have a separate Load button.
    }
    fun canExternalNavigation()=state.editor==null&&!state.importOpen&&!state.busy&&!mutating&&!state.leaveOpen&&!state.deleteOpen&&!state.clearHistoryOpen
    fun closeForNavigation(){if(canExternalNavigation()){generation++;state=state.copy(open=false,detail=null);worker.execute {directory.close()}}}
    fun close(){
        if(state.busy||mutating||state.importPicking)return
        if(state.clearHistoryOpen){cancelHistory();return}
        if(state.importOpen){if(state.importText.isBlank()&&state.importPreview==null&&fileUri==null){clearImportFile();state=state.copy(importOpen=false,importText="",importPreview=null,importSelected=emptySet(),importError=null,importSource="text",importFileName=null,importFileSelected=false,leaveOpen=false)} else state=state.copy(leaveOpen=true)}
        else if(state.editor!=null){if(SharedStateMerge.same(state.editor,state.initial))leave(true) else state=state.copy(leaveOpen=true)}
        else if(state.detail!=null)state=state.copy(detail=null,error=null)
        else closeForNavigation()
    }
    fun leave(confirm:Boolean){
        if(state.busy||state.importPicking)return
        if(confirm){clearImportFile();state=state.copy(editor=null,initial=null,expected=null,importOpen=false,importText="",importPreview=null,importSelected=emptySet(),importError=null,importSource="text",importFileName=null,importFileSelected=false,leaveOpen=false,error=null)}
        else state=state.copy(leaveOpen=false)
    }
    private fun clearImportFile(){importSession++;filePick=null;permissionPick=null;pendingFile=null;pendingSystem=null;fileUri=null;fileGate.next()}

    private fun login(owner: String):SavedAccounts.Login {
        check(!closed&&foreground&&!AppLock.locked(context))
        val account=CredentialVault(context).load() ?: error("No account")
        check(NoticeCenter.scope(account)==owner);return account
    }
    private fun canAct():Boolean = foreground&&!closed&&state.open&&!state.busy&&!state.importPicking&&!mutating&&!AppLock.locked(context)&&runCatching {login(state.owner);true}.getOrDefault(false)
    fun show(email: String){if(canAct()&&state.editor==null)state.people.find {it.email==email}?.let {state=state.copy(detail=it,error=null,notice=null)}}
    fun detail():ContactPerson? = state.detail.takeIf {canAct()&&state.editor==null}
    fun edit(){
        if(!canAct()||state.editor!=null)return
        val person=state.detail
        val expected=person?.let {NativeContactEdits.raw(loaded,it.email)}
        val editor=JSONObject().put("email",expected?.optString("email") ?: person?.email ?: "")
            .put("sender",expected?.optString("sender") ?: person?.name ?: "").put("number",expected?.optString("number") ?: person?.number ?: "")
        state=state.copy(editor=editor,initial=JSONObject(editor.toString()),expected=expected,error=null,notice=null)
    }
    fun create(){if(canAct()&&state.editor==null){state=state.copy(detail=null);edit()}}
    fun startImport(){
        if(!canAct()||state.editor!=null||state.importOpen)return
        clearImportFile()
        state=state.copy(detail=null,importOpen=true,importText="",importPreview=null,importSelected=emptySet(),importError=null,importSource="text",importFileName=null,importFileSelected=false,error=null,notice=null)
    }
    fun importSource(source: String){
        if(!canAct()||!state.importOpen||state.leaveOpen||state.busy||source !in listOf("text","file","system"))return
        if(state.importSource!=source)state=state.copy(importSource=source,importPreview=null,importSelected=emptySet(),importError=null)
    }
    fun importInput(value: String){
        if(!canAct()||!state.importOpen||state.leaveOpen||state.busy)return
        if(value.length>NativeContactImport.MAX_BYTES){state=state.copy(importPreview=null,importSelected=emptySet(),importError="Текст больше 200 КиБ.");return}
        state=state.copy(importText=value,importPreview=null,importSelected=emptySet(),importError=null)
    }
    fun pickImportFile(launch:()->Unit){
        if(!canAct()||!state.importOpen||state.importSource!="file"||state.leaveOpen||filePick!=null)return
        filePick=NativeContactFilePick(state.owner,importSession);state=state.copy(importPicking=true,importError=null)
        try{launch()}catch(_:Exception){filePick=null;state=state.copy(importPicking=false,importError="Не удалось открыть выбор файла. Повторите попытку.")}
    }
    fun importFileFromUri(uri:android.net.Uri?){
        val pick=filePick ?: return;filePick=null;state=state.copy(importPicking=false)
        if(uri==null)return
        if(!state.importOpen||state.importSource!="file"||!pick.matches(state.owner,importSession))return
        state=state.copy(importPreview=null,importSelected=emptySet(),importError=null)
        if(uri.scheme!="content"){state=state.copy(importError="Выберите файл через приложение «Файлы».");return}
        pendingFile=pick to uri
        if(foreground&&!AppLock.locked(context))consumeFile()
    }
    private fun consumeFile(){
        val request=pendingFile ?: return
        if(!foreground||closed||AppLock.locked(context))return
        pendingFile=null
        if(!state.importOpen||state.importSource!="file"||!request.first.matches(state.owner,importSession))return
        if(runCatching {login(state.owner)}.isFailure){state=state.copy(importError="Текущий ящик изменился. Выберите файл заново в нужном ящике.");return}
        fileUri=request.second;state=state.copy(importFileSelected=true,importFileName=null,importPreview=null)
        readImportFile()
    }
    private fun readImportFile(){
        val uri=fileUri ?: return
        if(!canAct()||!state.importOpen||state.importSource!="file"||state.leaveOpen)return
        val snapshot=state;val ticket=++generation;val readTicket=fileGate.next()
        state=state.copy(busy=true,importReading=true,importPreview=null,importSelected=emptySet(),importError=null)
        worker.execute {
            val read=FileRead()
            try{
                val account=login(snapshot.owner);fileGate.attach(readTicket,read);check(fileGate.current(readTicket)&&generation==ticket)
                val name=runCatching {context.contentResolver.query(uri,arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null,read.signal)?.use {c->if(c.moveToFirst())c.getString(0) else null}}.getOrNull()?.let {AttachmentFiles.safeName(it)} ?: "Текстовый файл"
                val descriptor=context.contentResolver.openAssetFileDescriptor(uri,"r",read.signal) ?: error("File unavailable")
                val text=descriptor.use {asset->asset.createInputStream().use {input->read.stream=input
                    NativeContactImport.readStream(input){!foreground||closed||generation!=ticket||!fileGate.current(readTicket)||AppLock.locked(context)}
                }}
                login(snapshot.owner);check(fileGate.current(readTicket)&&generation==ticket)
                val current=synchronized(ReplyReminderCenter::class.java){JSONObject(NoticeCenter.state(context,account).getString("state","{}")!!)}
                val plan=NativeContactImport.preview(text,current)
                main.post {if(valid(ticket,snapshot.owner)&&state.importOpen&&fileGate.current(readTicket)){
                    loaded=current;state=state.copy(busy=false,importReading=false,importFileName=name,importPreview=plan,importSelected=plan.selectable(),importError=null)
                }}
            }catch(error:Exception){main.post {if(valid(ticket,snapshot.owner)&&state.importOpen&&fileGate.current(readTicket)){state=state.copy(busy=false,importReading=false,importPreview=null,importSelected=emptySet(),importError=if(error is IllegalArgumentException)error.message else "Не удалось прочитать файл. Выберите текст UTF-8 до 200 КиБ и повторите предпросмотр.")}}}
            finally{fileGate.detach(readTicket,read);read.close()}
        }
    }
    fun stopImportRead(){
        if(!state.importReading)return
        generation++;fileGate.next();state=state.copy(busy=false,importReading=false,importPreview=null,importSelected=emptySet(),importError="Чтение остановлено. Можно повторить предпросмотр или выбрать другой файл.")
    }
    fun previewImport(){
        if(state.importSource=="file"){readImportFile();return}
        if(state.importSource=="system"){loadSystemContacts();return}
        if(!canAct()||!state.importOpen||state.leaveOpen)return
        val snapshot=state;val ticket=++generation
        state=state.copy(busy=true,importPreview=null,importSelected=emptySet(),importError=null)
        worker.execute {
            try {
                val account=login(snapshot.owner)
                val current=synchronized(ReplyReminderCenter::class.java){
                    JSONObject(NoticeCenter.state(context,account).getString("state","{}")!!)
                }
                val plan=NativeContactImport.preview(snapshot.importText,current)
                main.post {if(valid(ticket,snapshot.owner)&&state.importOpen){
                    loaded=current;state=state.copy(busy=false,importPreview=plan,importSelected=plan.selectable(),importError=null)
                }}
            }catch(error:Exception){main.post {if(valid(ticket,snapshot.owner)&&state.importOpen){
                state=state.copy(busy=false,importPreview=null,importSelected=emptySet(),importError=error.message?.takeIf {it.isNotBlank()} ?: "Не удалось разобрать текст.")
            }}}
        }
    }
    fun toggleImportSelection(email:String){
        val plan=state.importPreview ?: return
        if(!canAct()||!state.importOpen||state.leaveOpen)return
        val key=email.lowercase(java.util.Locale.ROOT);if(key !in plan.selectable())return
        state=state.copy(importSelected=if(key in state.importSelected)state.importSelected-key else state.importSelected+key)
    }
    fun selectAllImport(selected:Boolean){
        val plan=state.importPreview ?: return
        if(canAct()&&state.importOpen&&!state.leaveOpen)state=state.copy(importSelected=if(selected)plan.selectable() else emptySet())
    }
    fun requestSystemContacts(launch:()->Unit){
        if(!canAct()||!state.importOpen||state.importSource!="system"||state.leaveOpen||permissionPick!=null)return
        if(context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS)==android.content.pm.PackageManager.PERMISSION_GRANTED){loadSystemContacts();return}
        permissionPick=NativeContactFilePick(state.owner,importSession);state=state.copy(importPicking=true,importError=null)
        try{launch()}catch(_:Exception){permissionPick=null;state=state.copy(importPicking=false,importError="Не удалось запросить доступ к контактам Android.")}
    }
    fun systemPermissionResult(granted:Boolean){
        val request=permissionPick ?: return;permissionPick=null;state=state.copy(importPicking=false)
        if(!state.importOpen||state.importSource!="system"||!request.matches(state.owner,importSession))return
        if(!granted){state=state.copy(importError="Доступ к контактам не предоставлен. Импорт не выполнялся; можно использовать текст или файл.");return}
        pendingSystem=request;if(foreground&&!AppLock.locked(context))consumeSystemPermission()
    }
    private fun consumeSystemPermission(){
        val request=pendingSystem ?: return
        if(!foreground||closed||AppLock.locked(context))return
        pendingSystem=null
        if(state.importOpen&&state.importSource=="system"&&request.matches(state.owner,importSession))loadSystemContacts()
    }
    private fun loadSystemContacts(){
        if(!canAct()||!state.importOpen||state.importSource!="system"||state.leaveOpen)return
        if(context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS)!=android.content.pm.PackageManager.PERMISSION_GRANTED){state=state.copy(importError="Нет доступа к контактам Android. Нажмите «Прочитать контакты Android», чтобы запросить разрешение.");return}
        val snapshot=state;val ticket=++generation;val readTicket=fileGate.next()
        state=state.copy(busy=true,importReading=true,importPreview=null,importSelected=emptySet(),importError=null)
        worker.execute {
            val read=FileRead()
            try{
                val account=login(snapshot.owner);fileGate.attach(readTicket,read)
                fun alive(){check(foreground&&!closed&&generation==ticket&&fileGate.current(readTicket)&&!AppLock.locked(context))}
                alive();val projection=arrayOf(android.provider.ContactsContract.Data.CONTACT_ID,android.provider.ContactsContract.Data.DISPLAY_NAME_PRIMARY,android.provider.ContactsContract.Data.MIMETYPE,android.provider.ContactsContract.Data.DATA1)
                val kinds=arrayOf(android.provider.ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                val batch=NativeSystemContactsBatch()
                val cursor=context.contentResolver.query(android.provider.ContactsContract.Data.CONTENT_URI,projection,"mimetype IN (?,?)",kinds,"contact_id ASC, _id ASC",read.signal) ?: error("Contacts unavailable")
                read.cursor=cursor
                cursor.use {rows->
                    while(rows.moveToNext()){
                        alive();val mime=rows.getString(2)
                        val kind=if(mime==kinds[0])"email" else if(mime==kinds[1])"phone" else "other"
                        batch.add(rows.getString(0),kind,rows.getString(1),rows.getString(3))
                    }
                }
                read.cursor=null;alive();login(snapshot.owner)
                val current=synchronized(ReplyReminderCenter::class.java){JSONObject(NoticeCenter.state(context,account).getString("state","{}")!!)}
                val plan=NativeContactImport.previewEntries(batch.entries(),current)
                main.post {if(valid(ticket,snapshot.owner)&&state.importSource=="system"&&fileGate.current(readTicket)){
                    loaded=current;state=state.copy(busy=false,importReading=false,importPreview=plan,importSelected=plan.selectable(),importError=null)
                }}
            }catch(error:Exception){main.post {if(valid(ticket,snapshot.owner)&&state.importOpen&&state.importSource=="system"&&fileGate.current(readTicket))state=state.copy(busy=false,importReading=false,importPreview=null,importSelected=emptySet(),importError=if(error is IllegalArgumentException)error.message else "Не удалось прочитать системную книгу. Проверьте разрешение и повторите; контакты не изменены.")}}
            finally{fileGate.detach(readTicket,read);read.close()}
        }
    }
    fun confirmImport(){
        val plan=state.importPreview ?: return
        if(plan.added==0||state.importSelected.isEmpty())return
        if(state.importSelected.size>plan.capacity){state=state.copy(importError="Выберите не больше ${plan.capacity} новых адресов: предел 5000 контактов.");return}
        if(!canAct()||!state.importOpen||state.leaveOpen)return
        val snapshot=state;val ticket=++generation;mutating=true;state=state.copy(busy=true,importError=null)
        worker.execute {
            try {
                val account=login(snapshot.owner)
                val (next,rows)=synchronized(ReplyReminderCenter::class.java){
                    check(generation==ticket)
                    val destination=NoticeCenter.state(context,account)
                    val current=JSONObject(destination.getString("state","{}")!!)
                    val next=plan.apply(current,snapshot.importSelected)
                    val rows=people(NativeContacts.local(next,""),next)
                    val json=next.toString();check(json.length<=2000000)
                    login(snapshot.owner);check(generation==ticket)
                    check(destination.edit().putString("state",json).commit());next to rows
                }
                main.post {mutating=false;if(!closed&&state.owner==snapshot.owner){
                    val active=runCatching {CredentialVault(context).load()?.let {NoticeCenter.scope(it)}}.getOrNull()
                    if(active!=snapshot.owner){loaded=JSONObject();state=ContactsState()}
                    else {clearImportFile();loaded=next;state=state.copy(busy=false,importFileSelected=false,importOpen=false,importText="",importPreview=null,importSelected=emptySet(),importError=null,importSource="text",importFileName=null,leaveOpen=false,detail=null,book="local",query="",applied="",letter="",more=false,
                        people=rows,notice="Импорт завершён: добавлено ${snapshot.importSelected.size}, не добавлено ${plan.skipped+plan.invalidLines.size+plan.added-snapshot.importSelected.size}.")}
                }}
            }catch(error: Exception){main.post {mutating=false;if(!closed&&state.owner==snapshot.owner){
                val active=runCatching {CredentialVault(context).load()?.let {NoticeCenter.scope(it)}}.getOrNull()
                if(active!=snapshot.owner){loaded=JSONObject();state=ContactsState()}
                else {val conflict=error is SharedStateMerge.Conflict
                    state=state.copy(busy=false,importPreview=if(conflict)null else state.importPreview,
                        importError=if(conflict)"Список контактов изменился после предпросмотра. Покажите предпросмотр ещё раз." else error.message?.takeIf {error is IllegalArgumentException&&it.isNotBlank()} ?: "Не удалось импортировать контакты. Форма сохранена; проверьте место и повторите.")}
            }}}
        }
    }

    fun field(key: String,value: String){
        if(!canAct()||state.editor==null||state.leaveOpen||state.deleteOpen||key !in listOf("sender","email","number"))return
        if(value.length>(when(key){"email"->254;"number"->60;else->180}))return
        state=state.copy(editor=JSONObject(state.editor!!.toString()).put(key,value),error=null)
    }
    fun askDelete(){if(canAct()&&state.editor!=null&&state.expected!=null)state=state.copy(deleteOpen=true)}
    fun cancelDelete(){if(!state.busy)state=state.copy(deleteOpen=false)}
    fun save(){change(false)}
    fun delete(){if(state.deleteOpen)change(true)}
    private fun change(remove: Boolean){
        val editor=state.editor ?: return
        if(!canAct()||remove&&state.expected==null)return
        if(!remove)try{NativeContactEdits.fields(editor)}catch(error: Exception){state=state.copy(error=error.message);return}
        val snapshot=state;val ticket=++generation;mutating=true;state=state.copy(busy=true,error=null,deleteOpen=false)
        worker.execute {
            try {
                val account=login(snapshot.owner)
                val (next,rows)=synchronized(ReplyReminderCenter::class.java){
                    check(generation==ticket)
                    val destination=NoticeCenter.state(context,account)
                    val current=JSONObject(destination.getString("state","{}")!!)
                    val next=NativeContactEdits.change(current,snapshot.expected,if(remove)null else editor)
                    val rows=people(NativeContacts.local(next,""),next)
                    val json=next.toString();check(json.length<=2000000)
                    login(snapshot.owner);check(generation==ticket)
                    check(destination.edit().putString("state",json).commit());next to rows
                }
                main.post {mutating=false;if(!closed&&state.owner==snapshot.owner){
                    val active=runCatching {CredentialVault(context).load()?.let {NoticeCenter.scope(it)}}.getOrNull()
                    if(active!=snapshot.owner){loaded=JSONObject();state=ContactsState()}
                    else {loaded=next;state=state.copy(busy=false,editor=null,initial=null,expected=null,detail=null,leaveOpen=false,book="local",query="",applied="",letter="",more=false,
                        people=rows,notice=if(remove)"Контакт удалён с телефона." else "Контакт сохранён на телефоне.")}
                }}
            }catch(error: Exception){main.post {mutating=false;if(!closed&&state.owner==snapshot.owner){
                val active=runCatching {CredentialVault(context).load()?.let {NoticeCenter.scope(it)}}.getOrNull()
                if(active!=snapshot.owner){loaded=JSONObject();state=ContactsState()}
                else state=state.copy(busy=false,error=if(error is SharedStateMerge.Conflict)"Этот контакт изменился в другом окне. Скопируйте текст, закройте форму и обновите список." else error.message?.takeIf {error is IllegalArgumentException} ?: "Не удалось сохранить контакт. Форма осталась открытой.")
            }}}
        }
    }
    fun askClearHistory(){
        if(!canAct()||state.book!="history"||state.editor!=null||state.importOpen)return
        historyExpected=JSONArray((loaded.optJSONArray("recipientBook") ?: JSONArray()).toString());state=state.copy(clearHistoryOpen=true,error=null)
    }
    fun cancelHistory(){if(!state.busy){historyExpected=null;state=state.copy(clearHistoryOpen=false)}}
    fun clearHistory(){
        val expected=historyExpected ?: return
        if(!canAct()||!state.clearHistoryOpen||state.book!="history")return
        val owner=state.owner;val ticket=++generation;mutating=true;state=state.copy(busy=true,error=null)
        worker.execute {try{
            val account=login(owner)
            val next=synchronized(ReplyReminderCenter::class.java){
                check(generation==ticket);val destination=NoticeCenter.state(context,account)
                val current=JSONObject(destination.getString("state","{}")!!);val updated=NativeContactEdits.clearRecipientHistory(current,expected)
                val text=updated.toString();check(text.length<=2000000);login(owner);check(generation==ticket);check(destination.edit().putString("state",text).commit());updated
            }
            main.post {mutating=false;if(!closed&&state.owner==owner){historyExpected=null
                if(runCatching {CredentialVault(context).load()?.let {NoticeCenter.scope(it)}}.getOrNull()!=owner){loaded=JSONObject();state=ContactsState()}
                else {loaded=next;state=state.copy(busy=false,clearHistoryOpen=false,people=emptyList(),query="",applied="",letter="",notice="История получателей этого аккаунта очищена. Сохранённые контакты оставлены.")}}}
        }catch(error:Exception){main.post {mutating=false;if(!closed&&state.owner==owner){historyExpected=null
                if(runCatching {CredentialVault(context).load()?.let {NoticeCenter.scope(it)}}.getOrNull()!=owner){loaded=JSONObject();state=ContactsState()}
                else state=state.copy(busy=false,clearHistoryOpen=false,error=if(error is SharedStateMerge.Conflict)"История изменилась после подтверждения. Обновите список и повторите." else "Не удалось подтвердить очистку истории. Обновите список и проверьте результат.")}}}}
    }
    private fun people(rows: JSONArray,data: JSONObject):List<ContactPerson> {
        val local=NativeContactEdits.rows(data)
        val saved=(0 until local.length()).associate {local.getJSONObject(it).let {p->p.getString("email").lowercase(java.util.Locale.ROOT) to p}}
        val phone=data.optJSONArray("phoneContacts") ?: JSONArray()
        val numbers=mutableMapOf<String,String>()
        for(i in 0 until phone.length())phone.optJSONObject(i)?.let {p->numbers.putIfAbsent(p.optString("email").lowercase(java.util.Locale.ROOT),p.optString("number"))}
        return (0 until rows.length()).map {rows.getJSONObject(it).let {p->
            val email=p.getString("email");val key=email.lowercase(java.util.Locale.ROOT);val stored=saved[key]
            ContactPerson(p.getString("sender"),email,stored?.optString("number")?.takeIf {it.isNotEmpty()} ?: numbers[key] ?: "",stored!=null)
        }}
    }
    fun text(value: String){if(canAct()&&state.editor==null&&value.length<=120)state=state.copy(query=value,error=null)}
    fun book(value: String){
        if(!canAct()||state.editor!=null||value !in listOf("local","history","personal","team","company")||state.book==value)return
        generation++;cookie="";seenCookies.clear()
        state=state.copy(detail=null,book=value,letter="",applied="",people=emptyList(),more=false,busy=false,error=null,notice=null)
        if(value in listOf("local","history"))load(false)
    }
    fun letter(value: String){
        if(!canAct()||state.editor!=null)return
        try{NativeContacts.query("",value)}catch(_: Exception){return}
        state=state.copy(letter=value,query="");load(state.book !in listOf("local","history"))
    }
    fun search(){if(!canAct()||state.editor!=null)return;state=state.copy(letter="");load(state.book !in listOf("local","history"))}
    fun load(online: Boolean,more: Boolean=false){
        if(!foreground||closed||mutating||AppLock.locked(context)||!state.open||state.owner.isBlank()||more&&(!state.more||state.busy))return
        val snapshot=state
        val query=try{if(more)snapshot.applied else NativeContacts.query(snapshot.query,snapshot.letter)}catch(_: Exception){state=state.copy(error="До 120 символов, без управляющих знаков и префикса ^:.");return}
        if(snapshot.book !in listOf("local","history")&&!online){checkOwner(snapshot.owner);return}
        val ticket=++generation
        val previous=if(more)cookie else ""
        val previousCookies=if(more)seenCookies.toSet() else emptySet()
        val oldPeople=if(more)snapshot.people else emptyList()
        if(!more){cookie="";seenCookies.clear()}
        state=state.copy(busy=true,error=null,notice=null,applied=query,people=oldPeople,more=more&&state.more)
        worker.execute {
            try {
                check(!closed&&foreground&&generation==ticket&&!AppLock.locked(context))
                val login=CredentialVault(context).load() ?: error("No account")
                check(NoticeCenter.scope(login)==snapshot.owner)
                val supported=UniversityDirectory.supported(login)
                val data=synchronized(ReplyReminderCenter::class.java){JSONObject(NoticeCenter.state(context,login).getString("state","{}")!!)}
                NativeContactEdits.rows(data)
                val page=if(snapshot.book in listOf("local","history")) {
                    JSONObject().put("people",if(snapshot.book=="history")NativeContacts.history(data,query) else NativeContacts.local(data,query)).put("cookie","").put("hasMore",false)
                }else {
                    check(supported);directory.page(login,snapshot.book,query,previous)
                }
                val next=NativeContacts.next(page,previous,previousCookies)
                val before=JSONArray();oldPeople.forEach {before.put(JSONObject().put("sender",it.name).put("email",it.email))}
                val merged=NativeContacts.merge(before,page.getJSONArray("people"))
                val rows=people(merged,data)
                check(CredentialVault(context).load()?.let {NoticeCenter.scope(it)}==snapshot.owner)
                main.post {if(valid(ticket,snapshot.owner)) {
                    loaded=data;cookie=next;if(next.isNotEmpty())seenCookies.add(next)
                    state=state.copy(busy=false,account=login.user,supported=supported,people=rows,more=next.isNotEmpty(),notice=page.optString("notice").ifBlank {null})
                }}
            }catch(_: Exception){
                val active=runCatching {CredentialVault(context).load()?.let {NoticeCenter.scope(it)}}.getOrNull()
                main.post {if(valid(ticket,snapshot.owner)) {
                    if(active!=snapshot.owner){cookie="";seenCookies.clear();state=ContactsState()}
                    else state=state.copy(busy=false,error=if(snapshot.book in listOf("local","history"))"Не удалось прочитать сохранённые контакты." else "Не удалось загрузить справочник. Проверьте сеть и доступ к адресной книге.")
                }}}
        }
    }
    private fun checkOwner(owner: String){
        val ticket=++generation;state=state.copy(busy=true)
        worker.execute {
            val login=runCatching {CredentialVault(context).load()}.getOrNull()
            main.post {if(valid(ticket,owner)) {
                if(login==null||NoticeCenter.scope(login)!=owner){cookie="";seenCookies.clear();state=ContactsState()}
                else state=state.copy(busy=false,account=login.user,supported=UniversityDirectory.supported(login))
            }}
        }
    }
    private fun valid(ticket: Long,owner: String)=!closed&&foreground&&generation==ticket&&state.open&&state.owner==owner&&!AppLock.locked(context)
    fun person(email: String): ContactPerson? = if(canAct()&&state.editor==null)state.people.find {it.email==email} else null
    override fun onCleared(){closed=true;foreground=false;generation++;clearImportFile();fileCloser.shutdown();worker.execute {directory.close()};worker.shutdown()}
}
