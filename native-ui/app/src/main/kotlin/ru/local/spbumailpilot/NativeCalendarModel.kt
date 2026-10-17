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

internal data class CalendarEntry(val key: String,val data: JSONObject)
internal data class CalendarState(val open: Boolean=false,val owner: String="",val day: String=LocalDate.now(ZoneId.of("Europe/Moscow")).toString(),
    val mode: String="week",val entries: List<CalendarEntry> = emptyList(),val busy: Boolean=false,val error: String?=null,val notice: String?=null,val cached: Boolean=true,val actionNotice: String?=null,val links: List<JSONObject> = emptyList())

/** Uses the existing account-bound CalendarBook; remote events remain read-only. */
internal class NativeCalendarModel(app: Application):AndroidViewModel(app) {
    private val context=app.applicationContext
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    @Volatile private var generation=0L
    @Volatile private var foreground=false
    @Volatile private var closed=false
    var state by mutableStateOf(CalendarState());private set
    fun resume(){foreground=true;if(state.open)load(false)}
    fun pause(){foreground=false;generation++;state=state.copy(busy=false)}
    fun open(owner: String){
        if(!foreground||AppLock.locked(context)||owner.isBlank())return
        if(owner!=state.owner)state=CalendarState(owner=owner)
        state=state.copy(open=true);load(false)
    }
    fun openDay(owner: String,day: String){
        if(!foreground||AppLock.locked(context)||owner.isBlank())return
        try{NativeAgenda.date(day)}catch(_: Exception){return}
        if(owner!=state.owner)state=CalendarState(owner=owner)
        state=state.copy(open=true,day=day,mode="day");load(false)
    }
    fun returnFromEditor(day: String,notice: String?){
        if(!state.open)return
        try{NativeAgenda.date(day)}catch(_: Exception){return}
        state=state.copy(day=day,actionNotice=notice);load(false)
    }
    fun linkError(){state=state.copy(error="Не удалось открыть связь. Возможно, ссылка повреждена или принадлежит другому ящику.")}
    fun sourceMail(linkJson: String):JSONObject? {
        if(!foreground||AppLock.locked(context)||state.busy)return null
        return runCatching {
            require(linkJson.length<=64000)
            val links=JSONArray();state.links.forEach {links.put(it)}
            NativeMailLinks.savedMail(links,JSONObject(linkJson))
        }.getOrNull()
    }
    fun close(){generation++;state=state.copy(open=false,busy=false)}
    fun mode(value: String){
        try{NativeAgenda.first(state.day,value)}catch(_: Exception){return}
        if(state.mode==value)return
        state=state.copy(mode=value);load(false)
    }
    fun select(day: String){
        try{NativeAgenda.date(day)}catch(_: Exception){return}
        val same=NativeAgenda.months(day,state.mode)==NativeAgenda.months(state.day,state.mode)
        state=state.copy(day=day)
        if(!same)load(false)
    }
    fun shift(amount: Long){
        val current=NativeAgenda.date(state.day)
        val next=when(state.mode){"day"->current.plusDays(amount);"week"->current.plusWeeks(amount);else->current.plusMonths(amount)}
        select(next.toString())
    }
    fun today(){select(LocalDate.now(ZoneId.of("Europe/Moscow")).toString())}
    fun load(online: Boolean){
        if(!foreground||closed||!state.open||AppLock.locked(context))return
        val snapshot=state;val ticket=++generation
        // Don't show another month's old rows under a new period while loading.
        state=state.copy(busy=true,error=null,notice=null,entries=emptyList())
        worker.execute {
            try {
                val login=CredentialVault(context).load() ?: error("No account")
                check(NoticeCenter.scope(login)==snapshot.owner)
                val local=JSONObject(NoticeCenter.state(context,login).getString("state","{}")!!)
                val savedLinks=local.optJSONArray("eventMailLinks") ?: JSONArray()
                check(savedLinks.length()<=5000)
                val links=(0 until savedLinks.length()).mapNotNull {savedLinks.optJSONObject(it)?.let {link->JSONObject(link.toString())}}
                val book=CalendarBook(context,login);val remote=JSONArray();val warnings=linkedSetOf<String>()
                var calendars=0
                for(month in NativeAgenda.months(snapshot.day,snapshot.mode)) {
                    check(active(ticket,snapshot.owner))
                    val page=book.list(month,online){active(ticket,snapshot.owner)}
                    val connections=page.getJSONArray("calendars");calendars=maxOf(calendars,connections.length())
                    for(i in 0 until connections.length()) {
                        val c=connections.getJSONObject(i)
                        if(c.optString("error").isNotEmpty())warnings.add(c.optString("title")+": "+c.optString("error"))
                        if(!c.optBoolean("cached"))warnings.add("Нет сохранённых событий: "+c.optString("title"))
                    }
                    val events=page.getJSONArray("events");for(i in 0 until events.length())remote.put(events.getJSONObject(i))
                }
                val merged=NativeAgenda.combine(local,remote)
                val rows=(0 until merged.length()).map { merged.getJSONObject(it).let {entry->
                    CalendarEntry(JSONArray().put(entry.optString("kind")).put(entry.optString("calendarId")).put(entry.optString("id")).put(entry.optString("date")).put(entry.optString("time")).toString(),entry)
                }}
                check(CredentialVault(context).load()?.let {NoticeCenter.scope(it)}==snapshot.owner)
                main.post {if(active(ticket,snapshot.owner))state=state.copy(entries=rows,links=links,busy=false,cached=!online,
                    notice=if(warnings.isNotEmpty())warnings.joinToString("\n") else if(calendars==0)"Серверный календарь не подключён. Показаны задачи и события на телефоне." else if(online)"Календарь обновлён." else "Сохранённые события календарей. Для актуальных данных нажмите «Обновить».")}
            }catch(_: Exception){
                val owner=runCatching {CredentialVault(context).load()?.let {NoticeCenter.scope(it)}}.getOrNull()
                main.post {if(active(ticket,snapshot.owner))state=if(owner!=snapshot.owner)CalendarState() else state.copy(busy=false,error="Не удалось загрузить календарь. Проверьте подключение и настройки календарей.")}
            }
        }
    }
    private fun active(ticket: Long,owner: String)=!closed&&foreground&&generation==ticket&&state.open&&state.owner==owner&&!AppLock.locked(context)
    override fun onCleared(){closed=true;foreground=false;generation++;worker.shutdown()}
}
