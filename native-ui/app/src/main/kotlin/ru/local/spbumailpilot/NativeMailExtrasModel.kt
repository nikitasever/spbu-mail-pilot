package ru.local.spbumailpilot

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.compose.runtime.*
import org.json.JSONObject
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

internal data class MailExtrasState(val owner: String="",val open: Boolean=false,val labels: JSONObject=JSONObject(),val responses: List<JSONObject> = emptyList(),
    val snoozed: List<JSONObject> = emptyList(),val followups: List<JSONObject> = emptyList(),val replyStatus: Map<String,String> = emptyMap(),val listMode: String="response",val target: JSONObject?=null,val mode: String="",val expectedLabel: Any?=null,val expectedResponse: JSONObject?=null,
    val day: String="",val time: String="",val busy: Boolean=false,val error: String?=null,val notice: String?=null)
internal class NativeMailExtrasModel(app: Application):AndroidViewModel(app) {
    private val context=app.applicationContext
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    @Volatile private var foreground=false
    @Volatile private var closed=false
    @Volatile private var generation=0L
    private var mutating=false
    private var loaded=JSONObject()
    var state by mutableStateOf(MailExtrasState());private set
    private fun login(owner: String?=null):SavedAccounts.Login {
        check(foreground&&!closed&&!AppLock.locked(context));val login=CredentialVault(context).load() ?: error("No account")
        check(owner==null||NoticeCenter.scope(login)==owner);return login
    }
    private data class Snapshot(val responses: List<JSONObject>,val snoozed: List<JSONObject>,val followups: List<JSONObject>,val status: Map<String,String>)
    private fun readReminders(data: JSONObject,owner: String):Snapshot {
        fun rows(mode: String):List<JSONObject>{val rows=NativeMailExtras.records(data,owner,mode);return (0 until rows.length()).map {JSONObject(rows.getJSONObject(it).toString())}.sortedBy {it.optLong("at")}}
        val followups=rows("followup");val array=org.json.JSONArray();followups.forEach {array.put(it)}
        val prefs=ReplyReminderCenter.record(context,owner)
        val status=ReplyReminder.parse(array,owner).associate {it.target.let {ref->NativeMailExtras.key(owner,ref.reference())} to (prefs.getString(it.key,"pending") ?: "pending")}
        return Snapshot(rows("response"),rows("snooze"),followups,status)
    }
    fun resume(){foreground=true;if(!mutating)load()}
    fun pause(){foreground=false;generation++;state=state.copy(busy=mutating)}
    fun clearNotice(){state=state.copy(notice=null)}
    fun load(){
        if(!foreground||closed||mutating)return
        val ticket=++generation;state=state.copy(busy=true,error=null)
        worker.execute {try {
            val account=login();val owner=NoticeCenter.scope(account)
            val data=synchronized(ReplyReminderCenter::class.java){JSONObject(NoticeCenter.state(context,account).getString("state","{}")!!)}
            val labels=JSONObject(NativeMailExtras.labels(data).toString());val reminders=readReminders(data,owner)
            login(owner);main.post {if(!closed&&foreground&&generation==ticket){loaded=data;val prior=if(state.owner==owner)state else MailExtrasState(owner=owner);state=prior.copy(labels=labels,responses=reminders.responses,snoozed=reminders.snoozed,followups=reminders.followups,replyStatus=reminders.status,busy=false)}}
        }catch(_: Exception){val owner=runCatching {CredentialVault(context).load()?.let {NoticeCenter.scope(it)}}.getOrNull();main.post {if(!closed&&foreground&&generation==ticket){if(owner!=state.owner){loaded=JSONObject();state=MailExtrasState()}else state=state.copy(busy=false,error="Не удалось прочитать метки и напоминания. Обновите данные.")}}}}
    }
    fun open(owner: String,mode: String="response"){if(foreground&&!AppLock.locked(context)&&state.owner==owner&&!state.busy&&mode in listOf("response","snooze","followup")){state=state.copy(open=true,listMode=mode,error=null);load()}}
    fun selectList(mode: String){if(!state.busy&&state.target==null&&mode in listOf("response","snooze","followup"))state=state.copy(listMode=mode)}
    fun back(){if(!state.busy)state=state.copy(open=false)}
    fun begin(owner: String,mail: JSONObject,mode: String){
        if(!foreground||AppLock.locked(context)||state.busy||state.owner!=owner||state.target!=null||mode !in listOf("label","response","snooze","followup"))return
        try {
            val key=NativeMailExtras.key(owner,mail)
            val expected=if(mode=="label")null else NativeMailExtras.reminder(loaded,owner,mail,mode)
            if(mode=="followup"&&!ReplyReminder.validId(mail.optString("messageId"))) {state=state.copy(error="У письма нет корректного Message-ID. Проверка ответа недоступна.");return}
            val now=System.currentTimeMillis();val at=expected?.optLong("at")?.takeIf {it>now} ?: now+3600000
            val date=Instant.ofEpochMilli(at).atZone(ZoneId.of("Europe/Moscow"))
            state=state.copy(target=JSONObject(mail.toString()),mode=mode,expectedLabel=state.labels.opt(key),expectedResponse=expected,
                day=date.toLocalDate().toString(),time=date.format(DateTimeFormatter.ofPattern("HH:mm")),error=null)
        }catch(_: Exception){state=state.copy(error="Не удалось открыть точную ссылку на письмо.")}
    }
    fun dismiss(){if(!state.busy)state=state.copy(target=null,error=null)}
    fun field(key: String,value: String){if(!state.busy&&state.target!=null&&value.length<=16)state=when(key){"date"->state.copy(day=value,error=null);"time"->state.copy(time=value,error=null);else->state}}
    fun preset(tomorrow: Boolean){if(state.busy)return;val now=ZonedDateTime.now(ZoneId.of("Europe/Moscow"));val at=if(tomorrow)now.plusDays(1).withHour(9).withMinute(0) else now.plusHours(1);state=state.copy(day=at.toLocalDate().toString(),time=at.format(DateTimeFormatter.ofPattern("HH:mm")),error=null)}
    fun label(value: String){change(value,false)}
    fun saveResponse(){change(null,false)}
    fun removeResponse(){if(state.expectedResponse!=null)change(null,true)}
    fun openResponse(record: JSONObject,edit: Boolean):JSONObject? {
        if(state.busy||!foreground||AppLock.locked(context))return null
        val saved=when(state.listMode){"snooze"->state.snoozed;"followup"->state.followups;else->state.responses}.find {SharedStateMerge.same(it,record)} ?: return null
        val mail=JSONObject(saved.getJSONObject("mail").toString()).put("sender",saved.optString("sender")).put("subject",saved.optString("subject")).put("messageId",saved.optString("messageId"))
        if(edit){begin(state.owner,mail,state.listMode);return null};return mail
    }
    fun linkError(){state=state.copy(error="Не удалось открыть письмо. Проверьте аккаунт и ссылку.")}
    private fun change(label: String?,remove: Boolean){
        val mail=state.target ?: return
        if(!foreground||closed||AppLock.locked(context)||state.busy||mutating)return
        val at=if(label==null&&!remove)try{NativeMailExtras.at(state.day,state.time,System.currentTimeMillis())}catch(error: Exception){state=state.copy(error="Укажите будущую дату ГГГГ-ММ-ДД (1970–2100) и время ЧЧ:ММ по Москве.");return}else null
        val snapshot=state;generation++;mutating=true;state=state.copy(busy=true,error=null)
        worker.execute {try {
            val account=login(snapshot.owner)
            val next=synchronized(ReplyReminderCenter::class.java){
                val destination=NoticeCenter.state(context,account);val current=JSONObject(destination.getString("state","{}")!!)
                NativeMailExtras.labels(current);readReminders(current,snapshot.owner)
                val next=if(label!=null)NativeMailExtras.changeLabel(current,snapshot.owner,mail,snapshot.expectedLabel,label)
                    else NativeMailExtras.changeReminder(current,snapshot.owner,mail,snapshot.expectedResponse,at,System.currentTimeMillis(),snapshot.mode)
                val json=next.toString();check(json.length<=2000000);login(snapshot.owner);check(destination.edit().putString("state",json).commit());next
            }
            val labels=JSONObject(NativeMailExtras.labels(next).toString());val reminders=readReminders(next,snapshot.owner)
            runCatching {NoticeCenter.schedule(context)}
            main.post {mutating=false;if(!closed&&state.owner==snapshot.owner){loaded=next;state=state.copy(labels=labels,responses=reminders.responses,snoozed=reminders.snoozed,followups=reminders.followups,replyStatus=reminders.status,busy=false,target=null,error=null,notice=if(label!=null)"Метка сохранена на телефоне." else if(remove)"Напоминание отменено." else if(snapshot.mode=="snooze")"Письмо отложено до выбранного срока." else if(snapshot.mode=="followup")"Проверка ответа сохранена. В срок понадобится подключение к серверу." else "Напоминание сохранено. Для уведомления нужно разрешение Android.")}}
        }catch(error: Exception){main.post {mutating=false;if(!closed&&state.owner==snapshot.owner)state=state.copy(busy=false,error=if(error is SharedStateMerge.Conflict)"Запись изменилась в другом окне. Закройте форму и обновите данные." else "Не удалось сохранить. Форма осталась открытой.")}}}
    }
    override fun onCleared(){closed=true;foreground=false;generation++;worker.shutdown()}
}
