package ru.local.spbumailpilot

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.compose.runtime.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

internal data class NativeDigestPoint(val kind:String,val title:String,val detail:String,val deadline:String,val sources:List<Int>)
internal data class NativeDigestState(val owner:String="",val folder:String="INBOX",val open:Boolean=false,val candidates:List<NativeMail> = emptyList(),
    val selected:Set<MailRef> = emptySet(),val phase:String="choose",val done:Int=0,val total:Int=0,
    val items:List<JSONObject> = emptyList(),val failures:List<NativeMail> = emptyList(),val overview:String="",val points:List<NativeDigestPoint> = emptyList(),
    val model:String=AiConfiguration.DEFAULT_MODEL,val error:String?=null,val notice:String?=null) {
    val busy get()=phase=="loading"||phase=="analyzing"
    override fun toString()="NativeDigestState(open=$open,phase=$phase,selected=${selected.size})"
}

/** Text remains transient; BODY.PEEK and Gemini requests only follow explicit separate actions. */
internal class NativeDigestModel(app:Application):AndroidViewModel(app) {
    private val context=app.applicationContext
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    private val closer=Executors.newSingleThreadExecutor()
    private val gate=NativeReadGate(closer)
    private val gemini=GeminiDigest()
    @Volatile private var foreground=false
    @Volatile private var closed=false
    var state by mutableStateOf(NativeDigestState());private set
    private fun login(owner:String):SavedAccounts.Login {
        check(foreground&&!closed&&!AppLock.locked(context));val login=CredentialVault(context).load() ?: error("No account")
        check(NoticeCenter.scope(login)==owner);return login
    }
    private fun options(login:SavedAccounts.Login)=NoticeCenter.state(context,login).getString("state","{}")!!
    private fun permitted(owner:String):SavedAccounts.Login {val login=login(owner);check(AiPolicy.enabled(options(login))){"Нейроанализ отключён в настройках этого ящика."};return login}
    private fun active(ticket:Long,owner:String)=!closed&&foreground&&gate.current(ticket)&&state.owner==owner&&!AppLock.locked(context)
    private fun post(ticket:Long,owner:String,run:()->Unit){main.post {if(active(ticket,owner))run()}}
    fun resume(){foreground=true;if(!state.open)return
        val owner=state.owner;val ticket=gate.next()
        worker.execute {try {val login=login(owner);val raw=options(login);val enabled=AiPolicy.enabled(raw);val model=AiConfiguration.model(JSONObject(raw))
            post(ticket,owner){if(!enabled)state=state.copy(phase="choose",items=emptyList(),overview="",points=emptyList(),error="Нейроанализ отключён. Включите его в настройках ИИ.") else {
                    val changed=state.model!=model
                    state=state.copy(model=model,phase=if(changed&&state.items.isNotEmpty())"preview" else state.phase,
                        overview=if(changed)"" else state.overview,points=if(changed)emptyList() else state.points,
                        notice=if(changed)"Модель изменилась. Проверьте подготовленные тексты перед новым запросом." else state.notice)
                }}
        }catch(_:Exception){val same=runCatching {CredentialVault(context).load()?.let {NoticeCenter.scope(it)}==owner}.getOrDefault(false);post(ticket,owner){state=if(same)state.copy(error="Не удалось прочитать настройки ИИ. Проверьте их перед запросом.") else NativeDigestState()}}}
    }
    fun pause(){foreground=false;gate.next();closer.execute(gemini.cancellation());if(state.busy)state=state.copy(phase=if(state.items.isEmpty())"choose" else "preview",notice="Операция отменена при уходе с экрана. Повторите её явно.")}
    fun open(owner:String,folder:String,rows:List<NativeMail>){
        if(!foreground||closed||AppLock.locked(context)||state.busy||!NativeOpenTarget.account(owner))return
        try {NativeFolders.canonical(folder);check(rows.size<=5000);check(rows.all {it.ref.folder==folder})}
        catch(_:Exception){return}
        gate.next();state=NativeDigestState(owner=owner,folder=folder,open=true,candidates=rows.distinctBy {it.ref}.toList())
    }
    fun select(ref:MailRef){if(!foreground||closed||AppLock.locked(context)||!state.open||state.busy||state.candidates.none {it.ref==ref})return
        val next=state.selected.toMutableSet();if(!next.remove(ref)){if(next.size==20){state=state.copy(error="Можно выбрать до 20 писем.");return};next.add(ref)}
        gate.next();state=state.copy(selected=next.toSet(),phase="choose",items=emptyList(),failures=emptyList(),overview="",points=emptyList(),error=null,notice=null)
    }
    fun editSelection(){if(!foreground||closed||AppLock.locked(context)||!state.open||state.busy)return
        gate.next();state=state.copy(phase="choose",items=emptyList(),failures=emptyList(),overview="",points=emptyList(),error=null,notice=null)
    }
    fun firstTwenty(){if(!foreground||closed||AppLock.locked(context)||!state.open||state.busy)return
        gate.next();state=state.copy(selected=state.candidates.take(20).map {it.ref}.toSet(),phase="choose",items=emptyList(),failures=emptyList(),overview="",points=emptyList(),error=null,notice=null)
    }
    fun prepare(){
        if(!foreground||closed||AppLock.locked(context)||!state.open||state.busy||state.selected.isEmpty())return
        val snapshot=state;val selected=snapshot.candidates.filter {it.ref in snapshot.selected}
        val refs=try{val values=JSONArray();selected.forEach {values.put(sourceHeader(snapshot.owner,it))};NativeDigestPlan.references(snapshot.owner,snapshot.folder,values)}
        catch(_:Exception){state=state.copy(error="Выберите до 20 писем одной версии папки. Обновите список, если он устарел.");return}
        val ticket=gate.next();state=state.copy(phase="loading",done=0,total=refs.size,items=emptyList(),failures=emptyList(),overview="",points=emptyList(),error=null,notice=null)
        worker.execute {val reader=MailReader();var attached=false
            try {
                val login=permitted(snapshot.owner);check(AiVault(context).configured()) {"Добавьте API-ключ в настройках ИИ."}
                val model=AiConfiguration.model(JSONObject(options(login)));val prepared=mutableListOf<JSONObject>();val failures=mutableListOf<NativeMail>();val started=System.nanoTime()
                gate.attach(ticket,reader);attached=true;check(active(ticket,snapshot.owner));reader.connect(login.host,login.port,login.user,login.password)
                for((index,ref) in refs.withIndex()){
                    check(active(ticket,snapshot.owner));permitted(snapshot.owner)
                    if(System.nanoTime()-started>java.util.concurrent.TimeUnit.SECONDS.toNanos(60)){failures.addAll(selected.drop(index));break}
                    try {val body=reader.body(ref.folder,ref.uid,ref.validity);check(active(ticket,snapshot.owner));prepared.add(NativeDigestPlan.item(ref,body))}
                    catch(rejected:javax.mail.AuthenticationFailedException){throw rejected}
                    catch(_:Exception){check(active(ticket,snapshot.owner));failures.add(selected[index])}
                    post(ticket,snapshot.owner){state=state.copy(done=index+1)}
                }
                permitted(snapshot.owner)
                post(ticket,snapshot.owner){state=state.copy(phase=if(prepared.isEmpty())"choose" else "preview",items=prepared.toList(),failures=failures.toList(),model=model,done=refs.size,
                    error=if(prepared.isEmpty())"Ни одно письмо не загрузилось. Обновите список или проверьте подключение." else null,
                    notice=if(prepared.isNotEmpty())"Тексты подготовлены на телефоне. Передача Gemini ещё не выполнялась." else null)}
            }catch(_:Exception){post(ticket,snapshot.owner){state=state.copy(phase="choose",error="Не удалось подготовить письма. Проверьте сеть, выбранный ящик, разрешение анализа и ключ в настройках ИИ.")}}
            finally {if(attached)gate.detach(ticket,reader);reader.close()}
        }
    }
    fun analyze(){
        if(!foreground||closed||AppLock.locked(context)||!state.open||state.busy||state.items.isEmpty())return
        val snapshot=state;val items=JSONArray();snapshot.items.forEach {items.put(JSONObject(it.toString()))}
        val ticket=gate.next();state=state.copy(phase="analyzing",overview="",points=emptyList(),error=null,notice=null)
        worker.execute {try {
            val login=permitted(snapshot.owner);val prefs=NoticeCenter.state(context,login);val model=AiConfiguration.model(JSONObject(options(login)))
            check(model==snapshot.model){"Model changed"}
            val key=AiVault(context).snapshot()
            val current=java.util.function.BooleanSupplier {active(ticket,snapshot.owner)&&key.current()&&runCatching {val raw=prefs.getString("state","{}")!!;AiPolicy.enabled(raw)&&AiConfiguration.model(JSONObject(raw))==model}.getOrDefault(false)}
            val result=gemini.analyze(key.value,items,login.user,current,model);check(current.asBoolean);this.login(snapshot.owner)
            val points=result.getJSONArray("points");val typed=(0 until points.length()).map {index->val p=points.getJSONObject(index);val sources=p.getJSONArray("sources")
                NativeDigestPoint(p.getString("kind"),p.getString("title"),p.getString("detail"),p.getString("deadline"),(0 until sources.length()).map {sources.getInt(it)})}
            post(ticket,snapshot.owner){state=if(current.asBoolean)state.copy(phase="result",overview=result.getString("overview"),points=typed) else state.copy(phase="preview",error="Настройки ИИ изменились. Запрос отменён; проверьте их перед повтором.")}
        }catch(error:Exception){val message=listOf(400,403,404,429,500).map {GeminiDigest.status(it)}.firstOrNull {it==error.message} ?: "Сводка не получена. Проверьте ключ, модель, доступность Gemini и разрешение анализа. Автоматического повтора нет."
            post(ticket,snapshot.owner){state=state.copy(phase="preview",error=message)}}}
    }
    fun cancel(){if(closed)return;gate.next();closer.execute(gemini.cancellation());state=state.copy(phase=if(state.items.isEmpty())"choose" else "preview",notice="Запрос отменён. Уже отправленные данные нельзя отозвать.",error=null)}
    fun back(){if(state.busy){cancel();return};gate.next();state=NativeDigestState()}
    fun source(index:Int):JSONObject? {
        if(!foreground||closed||AppLock.locked(context)||!state.open||state.busy||state.phase!="result")return null
        return runCatching {permitted(state.owner);val rows=JSONArray();state.items.forEach {rows.put(it)};NativeDigestPlan.source(state.owner,rows,index)}.getOrNull()
    }
    fun linkError(){state=state.copy(error="Исходное письмо недоступно в этом ящике. Оно могло переместиться или измениться.")}
    override fun onCleared(){closed=true;foreground=false;gate.next();closer.execute(gemini.cancellation());state=NativeDigestState();worker.shutdown();closer.shutdown()}
}
private fun sourceHeader(owner:String,mail:NativeMail)=JSONObject().put("account",owner).put("folder",mail.ref.folder).put("uid",mail.ref.uid).put("validity",mail.ref.validity)
