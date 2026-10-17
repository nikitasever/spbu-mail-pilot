package ru.local.spbumailpilot

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.compose.runtime.*
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

internal data class MailRef(val folder: String, val uid: String, val validity: String)
internal data class NativeMail(val ref: MailRef, val sender: String, val subject: String, val preview: String, val date: String, val unread: Boolean, val hasAttachments: Boolean? = null,val email: String="",val messageId: String="",val references: List<String> = emptyList(),val inReplyTo: String="")
internal data class NativeFile(val name: String, val path: String)
internal data class MailBody(val text: String, val files: List<NativeFile>, val message: JSONObject)
internal data class OpenedFile(val ref: MailRef, val owner: String, val value: JSONObject)
internal data class InboxState(val account: String = "", val owner: String = "", val rows: List<NativeMail> = emptyList(), val selected: NativeMail? = null,
    val body: MailBody? = null, val busy: Boolean = false, val cached: Boolean = true, val error: String? = null,
    val openedFile: OpenedFile? = null, val hasMore: Boolean = false, val total: Int = 0, val pageOffline: Boolean = true, val pageExpired: Boolean = false,
    val moveOpen: Boolean=false,val movePicker: Boolean=false,val moveKind: String="",val moveDestination: String="",val moveNeedsRefresh: Boolean=false,val linked: Boolean=false,val flagNotice: String? = null, val folder: String = "INBOX",
    val foldersOpen: Boolean = false, val foldersBusy: Boolean = false, val foldersOnline: Boolean = false,
    val folders: List<NativeFolders.Entry> = emptyList(), val foldersError: String? = null,
    val query: String = "", val searchText: String = "", val searchOpen: Boolean = false, val searchError: String? = null, val filter: String = "all",
    val threadsEnabled: Boolean = true, val thread: ThreadNav = ThreadNav())

/** Protocol work and in-memory bodies survive recreation; SavedStateHandle contains navigation keys only. */
internal class NativeInboxModel(app: Application, private val saved: SavedStateHandle) : AndroidViewModel(app) {
    private val context=app.applicationContext
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newFixedThreadPool(2)
    private val closer=Executors.newSingleThreadExecutor()
    private val gate=NativeReadGate(closer)
    private val folderGate=NativeReadGate(closer)
    private val seenFlags=NativeSeenFlags()
    @Volatile private var closed=false
    @Volatile private var foreground=false
    private var noticeNeedsList=false
    private var initialized=false
    private var mutating=false
    private val moved=mutableSetOf<Pair<String,MailRef>>()
    private data class Pager(val owner: String, val folder: String, val query: String, val reader: MailReader)
    private val pagerLock=Any()
    @Volatile private var pager: Pager?=null
    private var linkedOrigin: InboxState?=null
    private var linkedOffset=0
    private var pageState: NativeMailPage?=null // main-thread immutable cursor; retained with this ViewModel
    private fun closePager() {
        val old=synchronized(pagerLock){val old=pager;pager=null;old}
        if(old!=null)closer.execute { old.reader.close() }
    }
    private fun firstOnline(ticket: Long,owner: String,folder: String,query: String): JSONObject {
        val login=login(owner);check(gate.current(ticket))
        val connection=Pager(owner,folder,query,MailReader());gate.attach(ticket,connection.reader)
        var retained=false
        try {
            check(gate.current(ticket));connection.reader.connect(login.host,login.port,login.user,login.password)
            check(gate.current(ticket));val result=connection.reader.page(folder,0,"",query)
            synchronized(pagerLock){check(gate.current(ticket));pager=connection;retained=true}
            return result
        } finally {gate.detach(ticket,connection.reader);if(!retained)connection.reader.close()}
    }
    var state by mutableStateOf(InboxState());private set
    /** Reads a saved value defensively so a changed key type can never crash restoration. */
    private inline fun <reified T> savedValue(key: String): T? = runCatching { saved.get<Any?>(key) as? T }.getOrNull()
    init {
        // A process restart must not restore offsets/reader from a search whose private text was not saved.
        // Clearing the thread keys prevents an old filtered conversation from reappearing in an unfiltered list.
        if(savedValue<Boolean>("listFiltered")==true) {
            saved["listIndex"]=0;saved["listOffset"]=0;saved["readerOffset"]=0;saved["mailRoute"]=null;saved["listFiltered"]=false
            clearThreadKeys()
        }
        state=state.copy(threadsEnabled=savedValue<Boolean>("threadsEnabled") ?: true)
    }
    fun toggleSearch(){if(state.selected==null)state=state.copy(searchOpen=!state.searchOpen,searchError=null)}
    fun searchText(value: String){if(value.length<=200)state=state.copy(searchText=value,searchError=null)}
    fun search(online: Boolean){
        if(!foreground||AppLock.locked(context)||state.owner.isBlank()||state.selected!=null)return
        val query=try{NativeMailSearch.query(state.searchText)}catch(_: Exception){state=state.copy(searchError="До 200 символов, без управляющих знаков.");return}
        loadInbox(online,requestedQuery=query)
    }
    fun filter(value: String){
        if(!foreground||AppLock.locked(context)||state.selected!=null)return
        try{NativeMailSearch.matches(value,false,null)}catch(_: Exception){return}
        clearThreadKeys();state=state.copy(filter=value,thread=ThreadNav());saved["listIndex"]=0;saved["listOffset"]=0
        saved["listFiltered"]=state.query.isNotEmpty()||value!="all"
    }
    val listIndex get()=(saved.get<Int>("listIndex") ?: 0).coerceAtLeast(0)
    val listOffset get()=(saved.get<Int>("listOffset") ?: 0).coerceAtLeast(0)
    val readerOffset get()=if(linkedOrigin!=null)linkedOffset else (saved.get<Int>("readerOffset") ?: 0).coerceAtLeast(0)
    private fun readerOffset(value: Int){if(linkedOrigin!=null)linkedOffset=value.coerceAtLeast(0) else saved["readerOffset"]=value.coerceAtLeast(0)}
    private var appliedThreadPreference:Pair<String,Boolean>?=null
    fun threadPreference(owner:String,enabled:Boolean){
        if(owner.isEmpty()||state.owner!=owner||appliedThreadPreference==(owner to enabled))return
        appliedThreadPreference=owner to enabled
        saved["threadsEnabled"]=enabled
        state=state.copy(threadsEnabled=enabled,thread=if(enabled)state.thread else ThreadNavigation.clear(state.thread))
    }
    private fun offlineEnabled(login:SavedAccounts.Login)=NoticeCenter.settings(context,login)?.optBoolean("offlineMail",true)!=false
    private fun currentOwner()=CredentialVault(context).load()?.let { NoticeCenter.scope(it) }
    private fun login(owner: String): SavedAccounts.Login {
        val login=CredentialVault(context).load() ?: error("Account unavailable")
        check(NoticeCenter.scope(login)==owner)
        return login
    }
    private fun publish(ticket: Long, action: ()->Unit) { main.post { if(!closed&&gate.current(ticket))action() } }
    private fun <T> fetchOnline(ticket: Long, owner: String, action: (MailReader)->T): T {
        val login=login(owner);check(gate.current(ticket))
        val reader=MailReader();gate.attach(ticket,reader)
        try {
            check(gate.current(ticket))
            reader.connect(login.host,login.port,login.user,login.password)
            check(gate.current(ticket))
            return action(reader)
        } finally { gate.detach(ticket,reader);reader.close() }
    }
    fun resume() {
        foreground=true
        if(!initialized){initialized=true;loadInbox(false);return}
        if(state.busy&&state.owner.isEmpty())return
        val owner=state.owner
        worker.execute {
            val now=runCatching { currentOwner() }.getOrNull()
            main.post { if(!closed&&foreground&&now!=owner&&state.owner==owner){resetNavigation();state=InboxState();loadInbox(false)} }
        }
    }
    fun pause(recreating: Boolean) {
        foreground=false
        closeFolders()
        if(!recreating) { linkedOrigin=linkedOrigin?.let {it.copy(pageExpired=it.pageExpired||it.hasMore&&!it.pageOffline)};gate.next();closePager();discardFile();state=state.copy(busy=mutating,moveOpen=false,pageExpired=state.pageExpired||state.hasMore&&!state.pageOffline) }
    }
    fun listPosition(owner: String,folder: String,query: String,filter: String,index: Int,offset: Int) {
        if(!state.linked&&owner.isNotEmpty()&&state.owner==owner&&state.folder==folder&&state.query==query&&state.filter==filter) { saved["listIndex"]=index.coerceAtLeast(0);saved["listOffset"]=offset.coerceAtLeast(0) }
    }
    fun readerAction(owner: String,ref: MailRef,action: ()->Unit): Boolean {
        if(closed||!foreground||state.busy||state.owner!=owner||state.selected?.ref!=ref||state.body==null||AppLock.locked(context))return false
        if(!runCatching {currentOwner()==owner}.getOrDefault(false))return false
        action();return true
    }
    fun readerPosition(owner: String,ref: MailRef,offset: Int) {
        if(owner==state.owner&&ref==state.selected?.ref)readerOffset(offset)
    }
    private fun currentScope()=ThreadScope(state.owner,state.folder,state.query,state.filter)
    private fun clearThreadKeys(){saved["threadRoute"]=null;saved["threadOwner"]=null;saved["threadFolder"]=null}
    private fun saveThread(nav: ThreadNav){
        val anchor=nav.anchor ?: run {clearThreadKeys();return}
        saved["threadRoute"]=JSONObject().put("account",state.owner).put("folder",anchor.folder).put("uid",anchor.uid).put("validity",anchor.validity).toString()
        saved["threadOwner"]=state.owner;saved["threadFolder"]=anchor.folder
    }
    private fun restoredThreadAnchor(owner: String,folder: String): ThreadAnchor? {
        if(saved.get<String>("threadOwner")!=owner||saved.get<String>("threadFolder")!=folder)return null
        val raw=saved.get<String>("threadRoute")?:return null
        val target=MailNoticeTarget.parse(raw)?:return null
        if(target.account!=owner||target.folder!=folder)return null
        val anchor=ThreadAnchor(target.folder,target.validity,target.uid)
        return if(anchor.stable())anchor else null
    }
    fun toggleThreads(){
        if(!foreground||AppLock.locked(context))return
        val enabled=!state.threadsEnabled
        saved["threadsEnabled"]=enabled
        if(!enabled)clearThreadKeys()
        state=state.copy(threadsEnabled=enabled,thread=if(enabled)state.thread else ThreadNavigation.clear(state.thread))
    }
    fun openThread(anchor: MailRef,scope: ThreadScope){
        if(!foreground||AppLock.locked(context)||state.busy||state.selected!=null)return
        if(scope!=currentScope())return
        if(anchor.folder!=state.folder)return
        // The clicked row must still exist in the current loaded list, not a stale prior scope.
        if(state.rows.none {sameRef(it.ref,anchor)})return
        val next=ThreadNavigation.open(state.thread,currentScope(),ThreadAnchor(anchor.folder,anchor.validity,anchor.uid))
        if(next.anchor==null)return
        saveThread(next);state=state.copy(thread=next)
    }
    fun openThreadMember(mail: NativeMail,scope: ThreadScope){
        if(!foreground||AppLock.locked(context)||state.busy||state.linked)return
        val anchor=state.thread.anchor?.let {MailRef(it.folder,it.uid,it.validity)} ?: return
        if(!ThreadNavigation.activeMember(state.thread,scope))return
        if(mail.ref.folder!=state.folder)return
        val chain=groupVisibleThreads(state.rows,state.owner,true).find {group->group.members.any {sameRef(it.ref,anchor)}} ?: return
        if(chain.members.none {sameRef(it.ref,mail.ref)})return
        val next=ThreadNavigation.openMember(state.thread)
        saveThread(next);state=state.copy(thread=next)
        openMail(mail,keepPosition=true)
    }
    fun clearThread(){
        if(state.thread.anchor==null&&!state.thread.readerFromThread)return
        clearThreadKeys();state=state.copy(thread=ThreadNavigation.clear(state.thread))
    }
    private fun rememberMail(ref: MailRef?) {
        if(linkedOrigin!=null)return
        saved["mailRoute"]=ref?.let { JSONObject().put("account",state.owner).put("folder",it.folder).put("uid",it.uid).put("validity",it.validity).toString() }
    }
    private fun restoredMail(owner: String): MailRef? {
        if(saved.get<String>("owner")!=owner)return null
        val target=MailNoticeTarget.parse(saved.get<String>("mailRoute")) ?: return null
        return if(target.account==owner)MailRef(target.folder,target.uid,target.validity) else null
    }
    private var cacheRevision=0L
    fun invalidateOffline(owner:String,revision:Long){
        if(closed||mutating||owner!=state.owner||revision<=cacheRevision)return
        cacheRevision=revision;gate.next();closePager();discardFile();resetNavigation()
        state=InboxState(account=state.account,owner=owner,threadsEnabled=state.threadsEnabled)
        if(foreground&&!AppLock.locked(context))loadInbox(false)
    }
    fun backToList() {
        if(mutating)return
        if(linkedOrigin!=null){
            gate.next();discardFile();val origin=linkedOrigin!!;linkedOrigin=null;linkedOffset=0
            val selected=origin.selected?.let {it.copy(unread=!seenFlags.seen(origin.owner,it.ref.folder,it.ref.validity,it.ref.uid,!it.unread))}
            val body=if(selected!=null)origin.body?.let {it.copy(message=JSONObject(it.message.toString()).put("seen",!selected.unread))} else origin.body
            state=origin.copy(selected=selected,body=body,busy=false,openedFile=null,rows=origin.rows.map {row->row.copy(unread=!seenFlags.seen(origin.owner,row.ref.folder,row.ref.validity,row.ref.uid,!row.unread))})
            return
        }
        if(state.thread.readerFromThread&&state.selected!=null&&state.thread.anchor!=null){
            gate.next();discardFile();rememberMail(null);saved["readerOffset"]=0
            val next=ThreadNavigation.back(state.thread);saveThread(next)
            state=state.copy(selected=null,body=null,busy=false,error=null,cached=state.pageOffline,flagNotice=null,thread=next)
            return
        }
        gate.next();discardFile();rememberMail(null);saved["readerOffset"]=0;clearThreadKeys()
        state=state.copy(selected=null,body=null,busy=false,error=null,cached=state.pageOffline,flagNotice=null,thread=ThreadNavigation.clear(state.thread));if(noticeNeedsList){noticeNeedsList=false;loadInbox(false)} }
    fun openLinked(owner: String,mail: JSONObject):Boolean {
        if(!foreground||AppLock.locked(context)||state.busy||owner!=state.owner||linkedOrigin!=null)return false
        val target=NativeMailLinks.target(owner,mail) ?: return false
        val ref=MailRef(target.folder,target.uid,target.validity)
        val row=parseMail(mail,ref,owner) ?: return false
        closeFolders();discardFile();linkedOrigin=state;linkedOffset=0
        clearThreadKeys()
        state=InboxState(account=state.account,owner=owner,folder=ref.folder,linked=true,cached=false,threadsEnabled=state.threadsEnabled)
        openMail(row)
        return true
    }
    fun openNotice(target: MailNoticeTarget):Boolean {
        if(!foreground||closed||AppLock.locked(context)||state.busy||mutating||state.owner!=target.account)return false
        if(linkedOrigin!=null)backToList()
        closeFolders();discardFile();gate.next();closePager();pageState=null;clearThreadKeys()
        saved["folder"]=target.folder;saved["listIndex"]=0;saved["listOffset"]=0;saved["readerOffset"]=0
        val same=state.folder==target.folder
        noticeNeedsList=!same||state.rows.isEmpty()
        state=InboxState(account=state.account,owner=state.owner,folder=target.folder,rows=if(same)state.rows else emptyList(),
            total=if(same)state.total else 0,cached=true,pageOffline=if(same)state.pageOffline else true,pageExpired=true,threadsEnabled=state.threadsEnabled)
        openMail(NativeMail(MailRef(target.folder,target.uid,target.validity),"","Письмо из уведомления","","",false))
        return true
    }
    fun showFolders(){
        if(!foreground||AppLock.locked(context)||state.owner.isBlank()||state.busy||state.selected!=null)return
        state=state.copy(foldersOpen=true,foldersError=null)
        if(state.folders.isEmpty())loadFolders(false)
    }
    fun closeFolders(){folderGate.next();state=state.copy(foldersOpen=false,movePicker=false,foldersBusy=false,foldersError=null)}
    fun loadFolders(online: Boolean){
        if(!foreground||AppLock.locked(context)||!state.foldersOpen||state.owner.isBlank()||state.foldersBusy)return
        val owner=state.owner;val current=state.folder;val ticket=folderGate.next()
        state=state.copy(foldersBusy=true,foldersError=null)
        worker.execute {
            try {
                val login=login(owner);check(folderGate.current(ticket))
                val names=if(online){
                    val reader=MailReader();folderGate.attach(ticket,reader)
                    try {
                        check(folderGate.current(ticket));reader.connect(login.host,login.port,login.user,login.password)
                        check(folderGate.current(ticket));reader.folders()
                    }finally{folderGate.detach(ticket,reader);reader.close()}
                }else {check(offlineEnabled(login)){"Offline access disabled"};OfflineMailStore(context,login).folders().getJSONArray("folders")}
                val entries=NativeFolders.list(names,current)
                check(currentOwner()==owner)
                main.post {if(!closed&&folderGate.current(ticket)&&state.owner==owner&&state.foldersOpen)
                    state=state.copy(foldersBusy=false,folders=entries,foldersOnline=online)}
            }catch(_: Exception){main.post {if(!closed&&folderGate.current(ticket)&&state.owner==owner&&state.foldersOpen)
                state=state.copy(foldersBusy=false,folders=state.folders.ifEmpty {NativeFolders.list(org.json.JSONArray(),current)},
                    foldersError=if(online)"Не удалось получить папки с сервера. Сохранённый список остался доступен; повторите запрос при подключении." else "Не удалось прочитать сохранённые папки. Можно запросить список с сервера.")}}
        }
    }
    fun chooseFolder(name: String){
        if(!foreground||AppLock.locked(context)||!state.foldersOpen||state.busy||state.folders.none {it.name==name})return
        if(state.movePicker){closeFolders();requestMove("folder",name);return}
        val online=state.foldersOnline;val same=name==state.folder
        closeFolders()
        if(!same)loadInbox(online,name)
    }
    fun loadInbox(online: Boolean,requestedFolder: String?=null,requestedQuery: String?=null) {
        if(!foreground||AppLock.locked(context)||mutating)return
        val query=NativeMailSearch.query(requestedQuery ?: state.query)
        val changingQuery=query!=state.query
        val ownerHint=state.owner.ifBlank {saved.get<String>("owner") ?: ""}
        val folderHint=if(state.owner.isNotBlank())state.folder else saved.get<String>("folder")
        val readerHint=MailNoticeTarget.parse(saved.get<String>("mailRoute"))?.takeIf {it.account==ownerHint}?.folder
        if(requestedFolder!=null&&!NativeFolders.valid(requestedFolder))return
        val ticket=gate.next();closePager();discardFile()
        closeFolders()
        if(changingQuery||requestedFolder!=null&&requestedFolder!=state.folder){
            pageState=null;saved["listIndex"]=0;saved["listOffset"]=0;saved["readerOffset"]=0;rememberMail(null);clearThreadKeys()
            if(requestedFolder!=null)saved["folder"]=requestedFolder
            state=state.copy(query=query,folder=requestedFolder ?: state.folder,rows=emptyList(),selected=null,body=null,total=0,hasMore=false,pageOffline=!online,cached=!online,thread=ThreadNav())
        }
        state=state.copy(busy=true,error=null,flagNotice=null,pageExpired=state.pageExpired||state.hasMore&&!state.pageOffline)
        worker.execute {
            try {
                check(gate.current(ticket))
                val login=CredentialVault(context).load()
                if(login==null){publish(ticket){resetNavigation();state=InboxState(error="Выберите «Ещё → Аккаунты и SMTP», чтобы войти в ящик.")};return@execute}
                val owner=NoticeCenter.scope(login)
                if(requestedFolder!=null||requestedQuery!=null)check(owner==ownerHint)
                val target=if(requestedFolder!=null)NativeFolders.canonical(requestedFolder) else NativeFolders.restore(owner,ownerHint,folderHint,readerHint)
                val offlineAllowed=offlineEnabled(login)
                val page=if(online)firstOnline(ticket,owner,target,query) else if(offlineAllowed) OfflineMailStore(context,login).page(target,0,"",query) else null
                val parsedPage=page?.let { NativeMailPage.first(it,target) }
                val rows=parsedPage?.messages
                check(currentOwner()==owner)
                publish(ticket) {
                    val restoring=if(!online&&requestedFolder==null&&!changingQuery)restoredMail(owner)?.takeIf {it.folder==target} else null
                    val knownFolders=if(state.owner==owner)state.folders else emptyList()
                    val foldersOnline=state.owner==owner&&state.foldersOnline
                    if(saved.get<String>("owner")!=owner)resetNavigation()
                    if(online) {
                        if(query.isEmpty())seenFlags.clearFolder(owner,target)
                        for(index in 0 until (rows?.length() ?: 0)) {
                            val header=rows!!.getJSONObject(index)
                            if(header.opt("seen") is Boolean)seenFlags.remember(owner,target,header.getString("validity"),header.getString("uid"),header.getBoolean("seen"))
                        }
                    }
                    val messages=(0 until (rows?.length() ?: 0)).mapNotNull { parseMail(rows!!.getJSONObject(it),owner=owner,folder=target) }
                    saved["owner"]=owner
                    saved["folder"]=target
                    saved["listFiltered"]=query.isNotEmpty()||state.filter!="all"
                    if(online){rememberMail(null);saved["readerOffset"]=0}
                    val nextScope=ThreadScope(owner,target,query,state.filter)
                    val keptThread=if(state.thread.anchor!=null&&state.thread.scope==nextScope)state.thread else ThreadNav()
                    val effectiveThread=if(keptThread.anchor!=null)keptThread
                        else restoredThreadAnchor(owner,target)?.let {ThreadNav(scope=nextScope,anchor=it)} ?: ThreadNav()
                    if(effectiveThread.anchor==null)clearThreadKeys()
                    pageState=parsedPage
                    state=InboxState(account=login.user,owner=owner,folder=target,rows=messages,cached=!online,pageOffline=!online,hasMore=parsedPage?.more ?: false,total=parsedPage?.total ?: 0,
                        query=query,searchText=state.searchText,searchOpen=state.searchOpen,filter=state.filter,folders=knownFolders,foldersOnline=foldersOnline,threadsEnabled=state.threadsEnabled,thread=effectiveThread,error=if(!online&&page==null)if(!offlineAllowed)"Офлайн-доступ отключён в настройках. Нажмите «Обновить» для загрузки с сервера." else "Эта папка не сохранена на телефоне. Нажмите «Обновить» при подключении." else null)
                    if(restoring!=null) {
                        if(effectiveThread.anchor!=null)state=state.copy(thread=ThreadNavigation.openMember(effectiveThread))
                        val selected=messages.find { it.ref==restoring } ?: NativeMail(restoring,"","Письмо","","",false)
                        openMail(selected,cachedOnly=true,keepPosition=true)
                    }
                }
            } catch (_: Exception) { publish(ticket){closePager();state=state.copy(busy=false,error="Не удалось загрузить список. Проверьте подключение и повторите обновление.")} }
        }
    }
    fun loadMore() {
        val snapshot=state;val cursor=pageState ?: return
        if(!foreground||AppLock.locked(context)||snapshot.busy||snapshot.selected!=null||!snapshot.hasMore||snapshot.pageExpired)return
        val ticket=gate.next();state=state.copy(busy=true,error=null)
        worker.execute {
            try {
                check(cursor.folder==snapshot.folder)
                val login=login(snapshot.owner);check(gate.current(ticket))
                val value=if(snapshot.pageOffline) {check(offlineEnabled(login));OfflineMailStore(context,login).page(cursor.folder,cursor.next,cursor.snapshot,snapshot.query) ?: error("Cached snapshot changed")}
                else {
                    val connection=pager ?: error("Paging connection expired")
                    check(NativeMailSearch.sameCursor(connection.owner,connection.folder,connection.query,snapshot.owner,cursor.folder,snapshot.query))
                    gate.attach(ticket,connection.reader)
                    try {check(gate.current(ticket));connection.reader.page(cursor.folder,cursor.next,cursor.snapshot,snapshot.query)}
                    finally {gate.detach(ticket,connection.reader)}
                }
                val merged=cursor.append(value)
                check(currentOwner()==snapshot.owner)
                val rows=(0 until merged.messages.length()).mapNotNull { parseMail(merged.messages.getJSONObject(it),owner=snapshot.owner,folder=snapshot.folder) }
                publish(ticket){pageState=merged;state=state.copy(rows=rows,busy=false,hasMore=merged.more,total=merged.total)}
            } catch (_: Exception) {publish(ticket){closePager();state=state.copy(busy=mutating,moveOpen=false,pageExpired=true,error="Не удалось продолжить список. Загруженные письма сохранены на экране. Обновите список перед следующей страницей.")}}
        }
    }
    private fun resetNavigation(){noticeNeedsList=false;moved.clear();linkedOrigin=null;linkedOffset=0;saved["listFiltered"]=false;folderGate.next();seenFlags.clear();pageState=null; saved["owner"]=null;saved["folder"]=null;saved["listIndex"]=0;saved["listOffset"]=0;saved["readerOffset"]=0;saved["threadRoute"]=null;saved["threadOwner"]=null;saved["threadFolder"]=null;rememberMail(null) }
    fun openMail(mail: NativeMail,cachedOnly: Boolean=false,keepPosition: Boolean=false,forceOnline: Boolean=false) {
        val owner=state.owner;if(owner.isEmpty()||state.busy)return
        if(mail.ref.folder!=state.folder)return
        if(mutating)return
        if(forceOnline&&state.selected?.ref!=mail.ref)return
        val ticket=gate.next();discardFile()
        if(!keepPosition&&state.selected?.ref!=mail.ref)readerOffset(0)
        rememberMail(mail.ref)
        state=state.copy(selected=mail,body=if(forceOnline)state.body else null,busy=true,error=null,flagNotice=null)
        worker.execute {
            try {
                check(gate.current(ticket));val login=login(owner);val ref=mail.ref
                val cached=if(forceOnline||!offlineEnabled(login))null else runCatching { OfflineMailStore(context,login).body(ref.folder,ref.uid,ref.validity) }.getOrNull()
                val value=cached ?: if(cachedOnly) null else fetchOnline(ticket,owner){it.body(ref.folder,ref.uid,ref.validity)}
                check(currentOwner()==owner)
                publish(ticket){
                    val header=value?.optJSONObject("message")
                    if(cached==null&&header?.opt("seen") is Boolean)seenFlags.remember(owner,ref.folder,ref.validity,ref.uid,header.getBoolean("seen"))
                    val fetched=value?.optJSONObject("message")?.let { parseMail(it,ref,owner) }
                    val selected=if(fetched!=null)fetched.copy(messageId=fetched.messageId.ifBlank {mail.messageId},
                        references=if(fetched.references.isEmpty())mail.references else fetched.references,
                        inReplyTo=fetched.inReplyTo.ifBlank {mail.inReplyTo}) else mail
                    val body=value?.let(::body)?.let { it.copy(message=JSONObject(it.message.toString()).put("seen",!selected.unread)) }
                    state=state.copy(selected=selected,body=body,busy=false,cached=cached!=null,moveNeedsRefresh=if(cached==null&&value!=null)false else state.moveNeedsRefresh,
                        rows=state.rows.map {if(it.ref==ref)it.copy(unread=selected.unread,email=selected.email.ifBlank {it.email},hasAttachments=selected.hasAttachments ?: it.hasAttachments) else it},
                        error=if(value==null)"Текст не сохранён на телефоне. Нажмите «Загрузить письмо» при подключении." else null)
                }
            } catch (_: Exception) { publish(ticket){state=state.copy(busy=false,error="Не удалось прочитать письмо. Проверьте подключение и повторите загрузку.")} }
        }
    }
    fun showMoveFolders(){
        if(!canMove())return
        state=state.copy(foldersOpen=true,movePicker=true,foldersError=null)
        loadFolders(true)
    }
    private fun canMove()=foreground&&!closed&&!AppLock.locked(context)&&!state.busy&&!state.linked&&state.selected!=null&&!state.cached&&!state.moveNeedsRefresh
    fun requestMove(kind: String,destination: String=""){
        if(!canMove()||kind !in listOf("archive","trash","spam","folder"))return
        if(kind=="folder"&&(!NativeFolders.valid(destination)||destination==state.selected?.ref?.folder))return
        state=state.copy(moveOpen=true,moveKind=kind,moveDestination=destination,error=null)
    }
    fun cancelMove(){if(!mutating)state=state.copy(moveOpen=false)}
    fun confirmMove(){
        if(!state.moveOpen||!canMove())return
        val snapshot=state;val mail=snapshot.selected ?: return;val owner=snapshot.owner;val ref=mail.ref
        gate.next();closePager();discardFile();mutating=true
        state=state.copy(busy=true,moveOpen=false,error=null,flagNotice=null)
        worker.execute {
            var attempted=false
            try {
                val account=login(owner)
                fun verify(){
                    check(login(owner).password.isNotEmpty())
                    val data=synchronized(ReplyReminderCenter::class.java){JSONObject(NoticeCenter.state(context,account).getString("state","{}")!!)}
                    NativeMailMoves.allowed(data,owner,JSONObject().put("account",owner).put("folder",ref.folder).put("uid",ref.uid).put("validity",ref.validity))
                }
                verify()
                val response=MailReader().use {reader->
                    reader.connect(account.host,account.port,account.user,account.password)
                    reader.moveMail(ref.folder,ref.uid,ref.validity,snapshot.moveKind,snapshot.moveDestination){
                        verify();attempted=true;true
                    }
                }
                val destination=NativeMailMoves.confirmed(response)
                main.post {mutating=false;if(!closed&&state.owner==owner){
                    moved.add(owner to ref);rememberMail(null);saved["readerOffset"]=0;pageState=null;clearThreadKeys()
                    state=state.copy(busy=false,selected=null,body=null,rows=state.rows.filter {it.ref!=ref},pageExpired=true,hasMore=false,thread=ThreadNav(),
                        total=(state.total-1).coerceAtLeast(0),moveNeedsRefresh=false,flagNotice="Перемещено в «${NativeFolders.label(destination)}». Обновите папку для нового списка.")
                }}
            }catch(error: Exception){
                main.post {mutating=false;if(!closed&&state.owner==owner)state=state.copy(busy=false,moveNeedsRefresh=attempted,pageExpired=true,
                    error=if(attempted)"Не удалось подтвердить перемещение. Оно могло выполниться. Обновите письмо или папку с сервера перед повтором. Автоматического повтора нет."
                    else if(error is IllegalStateException&&error.message?.startsWith("У письма есть")==true)error.message
                    else "Перемещение не начато или не подтверждено. Проверьте подключение, папку назначения и поддержку IMAP MOVE.")}
            }
        }
    }
    fun markSeen(requested: Boolean) {
        if(!foreground||AppLock.locked(context)||state.busy)return
        val owner=state.owner;val ref=state.selected?.ref ?: return
        if(owner.isEmpty())return
        val ticket=gate.next();discardFile();state=state.copy(busy=true,error=null,flagNotice=null)
        worker.execute {
            try {
                val response=fetchOnline(ticket,owner) { reader ->
                    check(foreground&&!AppLock.locked(context)&&gate.current(ticket))
                    login(owner) // Recheck the active mailbox immediately before the explicit write.
                    reader.seen(ref.folder,ref.uid,ref.validity,requested)
                }
                val confirmed=NativeSeenFlags.confirmed(response,requested)
                check(currentOwner()==owner)
                publish(ticket){
                    if(state.owner==owner&&state.selected?.ref==ref){
                        seenFlags.remember(owner,ref.folder,ref.validity,ref.uid,confirmed)
                        state=state.copy(busy=false,rows=state.rows.map {if(it.ref==ref)it.copy(unread=!confirmed) else it},
                            selected=state.selected?.copy(unread=!confirmed),
                            body=state.body?.let {it.copy(message=JSONObject(it.message.toString()).put("seen",confirmed))},
                            flagNotice=if(confirmed)"Помечено прочитанным на сервере." else "Помечено непрочитанным на сервере.")
                    }
                }
            } catch(_: Exception){publish(ticket){state=state.copy(busy=false,error="Не удалось подтвердить статус на сервере. Изменение могло примениться; нажмите «Обновить письмо» для проверки.")}}
        }
    }
    fun fileAction(owner: String,ref: MailRef,file: NativeFile,share: Boolean=false) {
        if(state.body?.files?.contains(file)!=true||file.path.isBlank())return
        readerAction(owner,ref){openFile(file,share)}
    }
    private fun openFile(file: NativeFile,share: Boolean) {
        if(!foreground||AppLock.locked(context)||state.busy)return
        val owner=state.owner;val ref=state.selected?.ref ?: return
        val ticket=gate.next();discardFile();state=state.copy(busy=true,error=null)
        worker.execute {
            var result: JSONObject?=null
            try {
                result=fetchOnline(ticket,owner){it.attachment(ref.folder,ref.uid,ref.validity,file.path,File(context.cacheDir,"attachments"),{!gate.current(ticket)},{_ ->})}
                check(currentOwner()==owner)
                val downloaded=result.put("_share",share)
                main.post {
                    if(closed||!gate.current(ticket)){File(downloaded.getString("file")).delete()}
                    else state=state.copy(busy=false,openedFile=OpenedFile(ref,owner,downloaded))
                }
            } catch (_: Exception) {
                result?.let { File(it.getString("file")).delete() }
                publish(ticket){state=state.copy(busy=false,error="Не удалось загрузить вложение. Проверьте подключение и повторите.")}
            }
        }
    }
    fun takeFile(): JSONObject? {
        val pending=state.openedFile ?: return null
        if(!foreground||AppLock.locked(context))return null
        if(pending.owner!=state.owner||pending.ref!=state.selected?.ref||runCatching {currentOwner()!=pending.owner}.getOrDefault(true)){discardFile();return null}
        state=state.copy(openedFile=null);return pending.value
    }
    fun fileError(){state=state.copy(error="Не удалось открыть файл. Возможно, нужен просмотрщик этого формата.")}
    private fun discardFile(){state.openedFile?.let { File(it.value.getString("file")).delete() };state=state.copy(openedFile=null)}
    override fun onCleared(){closed=true;foreground=false;gate.next();folderGate.next();closePager();discardFile();worker.shutdown();closer.shutdown()}
    private fun parseMail(value: JSONObject,ref: MailRef?=null,owner: String=state.owner,folder: String=state.folder): NativeMail? {
        val key=ref ?: MailRef(value.optString("folder",folder),value.optString("uid"),value.optString("validity"))
        if(!key.uid.matches(Regex("[1-9][0-9]{0,19}"))||!key.validity.matches(Regex("[1-9][0-9]{0,19}")))return null
        if(moved.contains(owner to key))return null
        val seen=seenFlags.seen(owner,key.folder,key.validity,key.uid,value.optBoolean("seen"))
        val ownIds=NativeMailThreads.headerIdOccurrences(value.opt("messageId") as? String)
        val messageId=if(ownIds.size==1)ownIds[0] else ""
        val references=NativeMailThreads.headerIds(value.opt("references"))
        val inReplyTo=NativeMailThreads.headerIds(value.opt("inReplyTo") as? String).joinToString(" ")
        return NativeMail(key,value.optString("sender").ifBlank{value.optString("email")},value.optString("subject").ifBlank{"Без темы"},value.optString("preview"),value.optString("date"),!seen,value.opt("hasAttachments") as? Boolean,value.optString("email"),messageId,references,inReplyTo)
    }
    private fun body(value: JSONObject): MailBody {
        val files=value.optJSONArray("attachments")
        return MailBody(value.optString("text"),(0 until (files?.length() ?: 0)).map { files!!.getJSONObject(it).let { f -> NativeFile(f.optString("name","Вложение"),f.optString("path")) } },value.optJSONObject("message") ?: JSONObject())
    }
}
