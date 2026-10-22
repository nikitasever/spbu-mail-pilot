package ru.local.spbumailpilot

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.compose.runtime.*
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.Executors

internal data class WorkspaceState(val open: Boolean=false,val owner: String="",val kind: String="tasks",val rows: List<JSONObject> = emptyList(),
    val editor: JSONObject?=null,val expected: JSONObject?=null,val busy: Boolean=false,val error: String?=null,val notice: String?=null,
    val eventMail: JSONObject?=null,val taskMail: JSONObject?=null,val eventRoute: Boolean=false,val eventDay: String="",val eventId: String?=null,val tasksEnabled: Boolean=true,val query: String="",val filter: String="active",val deleteOpen: Boolean=false,val leaveOpen: Boolean=false)

/** Edits share existing account state and reminders. No plaintext form data is saved in Bundle. */
internal class NativeWorkspaceModel(app: Application):AndroidViewModel(app) {
    private val context=app.applicationContext
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    @Volatile private var foreground=false
    @Volatile private var closed=false
    @Volatile private var generation=0L
    private var mutating=false
    var state by mutableStateOf(WorkspaceState());private set
    private fun login(owner: String):SavedAccounts.Login {
        check(foreground&&!closed&&!AppLock.locked(context))
        val login=CredentialVault(context).load() ?: error("No account")
        check(NoticeCenter.scope(login)==owner);return login
    }
    fun resume(){foreground=true;if(state.open&&!mutating)load()}
    fun pause(){foreground=false;generation++;state=state.copy(deleteOpen=false,leaveOpen=false,busy=mutating)}
    fun open(owner: String){
        if(!foreground||AppLock.locked(context)||owner.isBlank())return
        if(state.owner!=owner)state=WorkspaceState(owner=owner)
        state=state.copy(open=true);load()
    }
    fun openMailTask(owner: String,mail: JSONObject){
        if(!foreground||closed||AppLock.locked(context)||owner.isBlank()||state.open)return
        val task=try{NativeMailLinks.task(owner,mail,UUID.randomUUID().toString())}catch(_: Exception){return}
        state=WorkspaceState(open=true,owner=owner,kind="tasks",taskMail=task)
        load()
    }
    fun openCalendarTask(owner: String,event: JSONObject){
        if(!foreground||closed||AppLock.locked(context)||owner.isBlank()||state.open)return
        val task=try{NativeMailLinks.taskFromEvent(event,UUID.randomUUID().toString())}catch(_: Exception){return}
        state=WorkspaceState(open=true,owner=owner,kind="tasks",taskMail=task)
        load()
    }
    fun openMailEvent(owner: String,mail: JSONObject){
        if(!foreground||closed||AppLock.locked(context)||owner.isBlank()||state.open||NativeMailLinks.target(owner,mail)==null)return
        state=WorkspaceState(open=true,owner=owner,kind="events",eventMail=JSONObject(mail.toString()))
        load()
    }
    fun openCalendarNewTask(owner:String,day:String){
        if(!foreground||closed||AppLock.locked(context)||owner.isBlank()||state.open)return
        try{NativeAgenda.date(day)}catch(_:Exception){return}
        state=WorkspaceState(open=true,owner=owner,kind="tasks",eventRoute=true,eventDay=day)
        load()
    }
    fun openEvent(owner: String,day: String,id: String?=null){
        if(!foreground||AppLock.locked(context)||owner.isBlank()||state.open)return
        try{NativeAgenda.date(day)}catch(_: Exception){return}
        state=WorkspaceState(open=true,owner=owner,kind="events",eventRoute=true,eventDay=day,eventId=id)
        load()
    }
    fun clearMailTaskReturn(){if(!state.open&&(state.taskMail!=null||state.eventMail!=null))state=state.copy(taskMail=null,eventMail=null,notice=null)}
    fun takeEventReturn():Pair<String,String?>? {
        if(state.open||!state.eventRoute)return null
        val result=state.eventDay to state.notice
        state=state.copy(eventRoute=false,notice=null)
        return result
    }
    fun back(){if(state.busy)return;if(state.editor!=null)state=state.copy(leaveOpen=true) else {generation++;state=state.copy(open=false)}}
    fun leave(confirm: Boolean){if(state.busy)return;state=if(confirm)state.copy(open=!(state.eventRoute||(state.taskMail!=null||state.eventMail!=null)),editor=null,expected=null,leaveOpen=false,error=null) else state.copy(leaveOpen=false)}
    fun kind(value: String){if(state.editor!=null||state.busy||(state.taskMail!=null||state.eventMail!=null)||value=="tasks"&&!state.tasksEnabled)return;try{NativeWorkspace.kind(value)}catch(_: Exception){return};state=state.copy(kind=value,query="");load()}
    fun sourceMail(id: String):JSONObject? {
        if(!foreground||AppLock.locked(context)||state.busy||state.editor!=null||state.kind!="tasks")return null
        return state.rows.find {it.optString("id")==id}?.optJSONObject("mail")?.let {JSONObject(it.toString())}
    }
    fun linkError(){state=state.copy(error="Не удалось открыть связь. Проверьте аккаунт и точную ссылку на письмо.")}
    fun query(value: String){if(value.length<=200)state=state.copy(query=value)}
    fun filter(value: String){if(value in listOf("active","done","all"))state=state.copy(filter=value)}
    fun load(){
        if(!foreground||closed||!state.open||mutating)return
        val snapshot=state;val owner=state.owner;val kind=state.kind;val ticket=++generation
        state=state.copy(busy=true,error=null)
        worker.execute {
            try{
                val account=login(owner)
                val data=synchronized(ReplyReminderCenter::class.java){JSONObject(NoticeCenter.state(context,account).getString("state","{}")!!)}
                val tasksEnabled=data.optJSONObject("settings")?.optBoolean("showTasks",true)!=false
                val chosen=if(!tasksEnabled&&kind=="tasks"&&state.editor==null&&snapshot.taskMail==null&&!snapshot.eventRoute)"notes" else kind
                val rows=NativeWorkspace.rows(data,chosen)
                val items=(0 until rows.length()).map {JSONObject(rows.getJSONObject(it).toString())}
                val mailEventExpected=snapshot.eventMail?.let {NativeMailEvents.existing(data,it)}
                val mailEventEditor=snapshot.eventMail?.let {mailEventExpected ?: NativeMailEvents.draft(owner,it,UUID.randomUUID().toString(),java.time.Instant.now())}
                login(owner)
                main.post {if(!closed&&foreground&&generation==ticket&&state.owner==owner) {
                    state=state.copy(kind=chosen,tasksEnabled=tasksEnabled,rows=items,busy=false)
                    if(snapshot.eventMail!=null&&state.editor==null) {
                        state=state.copy(editor=mailEventEditor,expected=mailEventExpected)
                    }
                    if(snapshot.taskMail!=null&&state.editor==null) {
                        state=if(tasksEnabled)state.copy(editor=JSONObject(snapshot.taskMail.toString()),expected=null)
                            else state.copy(error="Задачи отключены в настройках. Включите их в полном клиенте.")
                    }
                    if(snapshot.eventRoute&&state.editor==null) {
                        if(snapshot.kind=="tasks"&&!tasksEnabled)state=state.copy(rows=emptyList(),error="Задачи отключены в настройках. Включите их и вернитесь в календарь.")
                        else if(snapshot.eventId==null)edit(initialDay=snapshot.eventDay)
                        else if(items.any {it.optString("id")==snapshot.eventId})edit(snapshot.eventId)
                        else state=state.copy(error="Событие больше не существует. Вернитесь в календарь и обновите его.")
                    }
                }}
            }catch(_: Exception){
                val active=runCatching {CredentialVault(context).load()?.let {NoticeCenter.scope(it)}}.getOrNull()
                main.post {if(!closed&&foreground&&generation==ticket)state=if(active!=owner)WorkspaceState() else state.copy(busy=false,error="Не удалось прочитать данные. Введённый текст сохранён на экране.")}
            }
        }
    }
    fun edit(id: String?=null,initialDay: String=""){
        if(!foreground||AppLock.locked(context)||state.busy||state.editor!=null)return
        val original=id?.let {key->state.rows.find {it.optString("id")==key}};if(id!=null&&original==null)return
        val entry=original?.let {JSONObject(it.toString())} ?: JSONObject().put("id",UUID.randomUUID().toString()).put("title","").apply {
            if(state.kind=="notes")put("body","") else if(state.kind=="events"){put("date",initialDay);put("time","")} else {put("date",initialDay);put("time","");put("notes","");put("priority","normal");put("reminder","auto");put("done",false)}
        }
        state=state.copy(editor=entry,expected=original?.let {JSONObject(it.toString())},error=null,notice=null)
    }
    fun field(name: String,value: String){
        if(state.busy||state.editor==null||state.deleteOpen||state.leaveOpen)return
        if(name !in listOf("title","body","notes","date","time","priority","reminder"))return
        val limit=if(name=="body"||name=="notes")100000 else if(name=="title")500 else 32
        if(value.length>limit)return
        state=state.copy(editor=JSONObject(state.editor!!.toString()).put(name,value),error=null)
    }
    fun requestDelete(){if(!state.busy&&state.expected!=null)state=state.copy(deleteOpen=true)}
    fun cancelDelete(){state=state.copy(deleteOpen=false)}
    fun save(){val editor=state.editor ?: return;change(state.expected,JSONObject(editor.toString()).put("title",editor.optString("title").trim()))}
    fun delete(){if(state.deleteOpen&&state.expected!=null)change(state.expected,null)}
    fun done(id: String){if(state.kind!="tasks"||state.editor!=null)return;val item=state.rows.find {it.optString("id")==id} ?: return;change(item,JSONObject(item.toString()).put("done",!item.optBoolean("done")))}
    private fun change(expected: JSONObject?,replacement: JSONObject?){
        if(!foreground||closed||AppLock.locked(context)||state.busy||mutating||!state.open||state.kind=="tasks"&&!state.tasksEnabled)return
        try{if(replacement!=null)NativeWorkspace.validate(state.kind,replacement)}catch(_: Exception){state=state.copy(error=if(state.kind=="events")"Укажите название и дату ГГГГ-ММ-ДД (1970–2100). Время ЧЧ:ММ можно оставить пустым." else "Проверьте название, дату ГГГГ-ММ-ДД и время ЧЧ:ММ. Для времени нужна дата.");return}
        val snapshot=state;generation++;mutating=true;state=state.copy(busy=true,error=null,deleteOpen=false)
        worker.execute {
            try {
                val account=login(snapshot.owner)
                val next=synchronized(ReplyReminderCenter::class.java){
                    val destination=NoticeCenter.state(context,account)
                    val current=JSONObject(destination.getString("state","{}")!!)
                    check(snapshot.kind!="tasks"||current.optJSONObject("settings")?.optBoolean("showTasks",true)!=false)
                    val next=if(snapshot.eventMail!=null&&replacement!=null)NativeMailEvents.save(current,snapshot.owner,snapshot.eventMail,expected,replacement,UUID.randomUUID().toString())
                        else NativeWorkspace.change(current,snapshot.kind,expected,replacement)
                    val json=next.toString();check(json.length<=2000000);login(snapshot.owner)
                    check(destination.edit().putString("state",json).commit());next
                }
                val rows=next.getJSONArray(snapshot.kind);val items=(0 until rows.length()).map {JSONObject(rows.getJSONObject(it).toString())}
                runCatching {NoticeCenter.schedule(context)};runCatching {MailWidget.request(context)}
                main.post {mutating=false;if(!closed&&state.owner==snapshot.owner)state=state.copy(open=!(snapshot.eventRoute||snapshot.taskMail!=null||snapshot.eventMail!=null),eventDay=if(snapshot.eventRoute)(replacement ?: expected)?.optString("date") ?: snapshot.eventDay else state.eventDay,rows=items,busy=false,editor=null,expected=null,notice=if(replacement==null)"Удалено с телефона." else "Сохранено на телефоне.")}
            }catch(error: Exception){main.post {mutating=false;if(!closed&&state.owner==snapshot.owner)state=state.copy(busy=false,error=if(error is SharedStateMerge.Conflict)"Запись изменилась в другом окне. Скопируйте введённый текст, закройте редактор и обновите список." else "Не удалось сохранить. Текст остался в редакторе; проверьте аккаунт и свободное место.")}}
        }
    }
    override fun onCleared(){closed=true;foreground=false;generation++;worker.shutdown()}
}
