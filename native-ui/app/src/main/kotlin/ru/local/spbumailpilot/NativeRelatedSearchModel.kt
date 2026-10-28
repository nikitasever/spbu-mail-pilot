package ru.local.spbumailpilot
import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.compose.runtime.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.Executors

internal data class RelatedSearchState(val enabled:Boolean=false,val owner:String="",val criteria:NativeMailSearch.Criteria?=null,
    val people:List<JSONObject> = emptyList(),val events:List<JSONObject> = emptyList(),val peopleBusy:Boolean=false,val eventsBusy:Boolean=false,
    val peopleMore:Boolean=false,val peopleError:String?=null,val eventsError:String?=null,val online:Boolean=false,val rangeLabel:String="",val peopleNotice:String?=null){
    override fun toString()="RelatedSearchState(enabled=$enabled,people=${people.size},events=${events.size})"
}
/** Mail is searched by NativeInboxModel; these independently failing sections never replace that cursor. */
internal class NativeRelatedSearchModel(app:Application):AndroidViewModel(app){
    private val context=app.applicationContext
    private val main=Handler(Looper.getMainLooper())
    private val peopleWorker=Executors.newSingleThreadExecutor()
    private val calendarWorker=Executors.newSingleThreadExecutor()
    private val directory=UniversityDirectory()
    @Volatile private var foreground=false
    @Volatile private var closed=false
    @Volatile private var generation=0L
    private var remotePeople=JSONArray()
    private var cookie=""
    private var cookies=emptySet<String>()
    var state by mutableStateOf(RelatedSearchState());private set
    fun resume(){foreground=true}
    fun pause(){foreground=false;generation++;state=state.copy(peopleBusy=false,eventsBusy=false,peopleMore=false,
        peopleError=if(state.peopleBusy)"Поиск прерван. Повторите запрос." else state.peopleError,
        eventsError=if(state.eventsBusy)"Поиск прерван. Повторите запрос." else state.eventsError)}
    fun toggle(){clear(!state.enabled)}
    fun clear(enabled:Boolean=state.enabled){generation++;cookie="";cookies=emptySet();remotePeople=JSONArray();state=RelatedSearchState(enabled=enabled)}
    private fun active(ticket:Long,owner:String)=!closed&&foreground&&generation==ticket&&state.enabled&&state.owner==owner&&!AppLock.locked(context)
    private fun login(owner:String):SavedAccounts.Login{
        check(!closed&&foreground&&!AppLock.locked(context));val login=CredentialVault(context).load() ?: error("No account")
        check(NoticeCenter.scope(login)==owner);return login
    }
    private fun data(login:SavedAccounts.Login)=synchronized(ReplyReminderCenter::class.java){JSONObject(NoticeCenter.state(context,login).getString("state","{}")!!)}
    private fun rows(a:JSONArray)=(0 until a.length()).map {JSONObject(a.getJSONObject(it).toString())}
    fun search(owner:String,criteria:NativeMailSearch.Criteria,online:Boolean){
        if(!state.enabled||!foreground||closed||AppLock.locked(context))return
        val range=NativeRelatedSearch.range(criteria,LocalDate.now(ZoneId.of("Europe/Moscow")))
        val ticket=++generation;cookie="";cookies=emptySet();remotePeople=JSONArray()
        state=RelatedSearchState(enabled=true,owner=owner,criteria=criteria,peopleBusy=true,eventsBusy=true,online=online,rangeLabel="${range.start} — ${range.end}")
        contacts(ticket,owner,criteria,online,false)
        calendarWorker.execute {try{
            val account=login(owner);check(active(ticket,owner));val local=data(account);val remote=JSONArray()
            val localRows=rows(NativeRelatedSearch.events(local,remote,criteria.query,range))
            main.post {if(active(ticket,owner))state=state.copy(events=localRows)}
            val warnings=linkedSetOf<String>()
            if(range.tooWide)warnings.add("Для серверных календарей выберите не больше 12 месяцев. Местные события показаны независимо.")
            else for(month in range.months){
                check(active(ticket,owner));val page=CalendarBook(context,account).list(month,online){active(ticket,owner)}
                val events=page.getJSONArray("events");for(i in 0 until events.length())remote.put(events.getJSONObject(i))
                val sources=page.getJSONArray("calendars");for(i in 0 until sources.length()){
                    val s=sources.getJSONObject(i);if(!s.optBoolean("cached")||s.optString("error").isNotEmpty())warnings.add("Часть календарей недоступна; показаны доступные сохранённые события.")
                }
            }
            val events=rows(NativeRelatedSearch.events(local,remote,criteria.query,range));login(owner)
            main.post {if(active(ticket,owner))state=state.copy(events=events,eventsBusy=false,eventsError=warnings.takeIf {it.isNotEmpty()}?.joinToString("\n"))}
        }catch(_:Exception){main.post {if(active(ticket,owner))state=state.copy(eventsBusy=false,eventsError="Не удалось проверить календари. Местные результаты сохранены; повторите поиск.")}}}
    }
    fun moreContacts(){
        val s=state;val c=s.criteria ?: return
        if(s.peopleBusy||!s.peopleMore||!foreground||closed||AppLock.locked(context))return
        state=s.copy(peopleBusy=true,peopleError=null);contacts(generation,s.owner,c,s.online,true)
    }
    private fun contacts(ticket:Long,owner:String,c:NativeMailSearch.Criteria,online:Boolean,more:Boolean){
        val previous=if(more)cookie else "";val seen=if(more)cookies else emptySet()
        val before=if(more)JSONArray(remotePeople.toString()) else JSONArray()
        peopleWorker.execute {try{
            val account=login(owner);check(active(ticket,owner));val local=data(account)
            val localRows=rows(NativeRelatedSearch.people(local,before,c.query))
            main.post {if(active(ticket,owner))state=state.copy(people=localRows)}
            var next="";var notice:String?=if(!online)"Сохранённые контакты на телефоне." else if(!UniversityDirectory.supported(account))"Справочник университета доступен только для ящиков СПбГУ." else if(c.query.length<2)"Справочник проверяется от двух символов." else null;var remote=before
            if(online&&UniversityDirectory.supported(account)&&c.query.length>=2){
                val query=NativeContacts.query(c.query,"")
                check(active(ticket,owner));val page=directory.page(account,"company",query,previous)
                next=NativeContacts.next(page,previous,seen);remote=NativeContacts.merge(before,page.getJSONArray("people"))
                notice=page.optString("notice").takeIf {it.isNotBlank()}
            }
            val found=rows(NativeRelatedSearch.people(local,remote,c.query));login(owner)
            main.post {if(active(ticket,owner)){remotePeople=remote;cookie=next;cookies=seen+listOf(next).filter {it.isNotEmpty()};state=state.copy(people=found,peopleBusy=false,peopleMore=next.isNotEmpty(),peopleNotice=notice)}}
        }catch(_:Exception){main.post {if(active(ticket,owner))state=state.copy(peopleBusy=false,peopleMore=false,peopleError="Справочник недоступен. Сохранённые контакты показаны; повторите запрос. Для справочника — до 120 символов.")}}}
    }
    fun email(value:String):String?=if(foreground&&!closed&&!AppLock.locked(context)&&runCatching {login(state.owner)}.isSuccess)
        state.people.firstOrNull {it.optString("email")==value}?.getString("email") else null
    fun event(key:String):JSONObject?=if(foreground&&!closed&&!AppLock.locked(context)&&runCatching {login(state.owner)}.isSuccess)
        state.events.firstOrNull {eventKey(it)==key}?.let {JSONObject(it.toString())} else null
    override fun onCleared(){closed=true;foreground=false;generation++;peopleWorker.execute {directory.close()};peopleWorker.shutdown();calendarWorker.shutdown()}
    companion object {fun eventKey(e:JSONObject)=JSONArray().put(e.optString("kind")).put(e.optString("calendarId")).put(e.optString("id")).put(e.optString("date")).put(e.optString("time")).toString()}
}
