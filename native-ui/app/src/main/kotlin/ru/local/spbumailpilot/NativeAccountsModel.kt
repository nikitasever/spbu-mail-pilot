package ru.local.spbumailpilot

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.compose.runtime.*
import java.util.concurrent.Executors

internal data class NativeAccountSummary(val id:String,val user:String,val host:String,val active:Boolean)
internal data class AccountsState(val open:Boolean=false,val ready:Boolean=false,val owner:String="",val rows:List<NativeAccountSummary> = emptyList(),
    val busy:Boolean=false,val editor:String?=null,val host:String="mail.spbu.ru",val port:String="993",val user:String="",val password:String="",
    val from:String="",val ssl:Boolean=true,val dirty:Boolean=false,val discard:Boolean=false,val confirmation:String?=null,
    val error:String?=null,val notice:String?=null,val restart:Boolean=false) {
    override fun toString()="AccountsState(open=$open,busy=$busy,editor=$editor)"
}

/** Credentials live only in the vault and transient worker requests, never SavedStateHandle. */
internal class NativeAccountsModel(app:Application):AndroidViewModel(app) {
    private val context=app.applicationContext
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    private val closer=Executors.newSingleThreadExecutor()
    private val gate=NativeReadGate(closer)
    @Volatile private var foreground=false
    @Volatile private var closed=false
    private var mutating=false
    private var expectedSmtp:SavedAccounts.Login?=null
    private var expectedAccount:SavedAccounts.Login?=null
    var state by mutableStateOf(AccountsState());private set
    private fun allowed(){check(foreground&&!closed&&!AppLock.locked(context))}
    private fun owner(login:SavedAccounts.Login?)=login?.let {NoticeCenter.scope(it)} ?: ""
    private fun vault():CredentialVault {allowed();return CredentialVault(context)}
    private fun current(expected:String):CredentialVault {val v=vault();check(owner(v.load())==expected);return v}
    fun resume(){foreground=true;if(!state.busy&&!state.restart)load()}
    fun pause(){foreground=false;gate.next();expectedAccount=null;state=state.copy(password="",discard=false,busy=mutating,editor=if(state.editor=="password")null else state.editor,dirty=if(state.editor=="password")false else state.dirty)}
    fun open(){if(!foreground||closed||state.busy||state.restart||AppLock.locked(context))return;state=state.copy(open=true);load()}
    fun load(){
        if(!foreground||closed||state.busy||state.restart||AppLock.locked(context))return
        val ticket=gate.next();expectedAccount=null;state=state.copy(busy=true,error=null)
        worker.execute {try {
            val result=synchronized(CredentialVault::class.java){val v=vault();val active=v.load();owner(active) to v.list().map {NativeAccountSummary(NoticeCenter.scope(it),it.user,it.host,NativeAccountPolicy.same(it,active))}}
            main.post {if(!closed&&gate.current(ticket)&&foreground&&!AppLock.locked(context)){
                val changed=state.ready&&result.first!=state.owner
                if(changed){expectedSmtp=null;expectedAccount=null}
                state=state.copy(owner=result.first,rows=result.second,ready=true,busy=false,open=state.open||result.first.isEmpty(),editor=if(changed||state.editor=="password")null else state.editor,password=if(changed||state.editor=="password")"" else state.password,dirty=if(changed||state.editor=="password")false else state.dirty)
            }}
        }catch(_:Exception){main.post {if(!closed&&gate.current(ticket)&&foreground){state=state.copy(busy=false,error="Не удалось прочитать сохранённые входы. Данные не удалены; повторите загрузку.",open=true)}}}}
    }
    fun add(){if(!interactive()||state.editor!=null)return;expectedSmtp=null;expectedAccount=null;state=state.copy(editor="login",host="mail.spbu.ru",port="993",user="",password="",dirty=false,error=null,notice=null)}
    fun openAddAccount(){
        if(!foreground||closed||state.busy||state.restart||AppLock.locked(context))return
        expectedSmtp=null;expectedAccount=null
        state=state.copy(open=true,editor="login",host="mail.spbu.ru",port="993",user="",password="",dirty=false,discard=false,error=null,notice=null,confirmation=null)
        if(!state.ready)load()
    }
    fun smtp(){
        if(!interactive()||state.editor!=null||state.owner.isEmpty())return
        try {val login=current(state.owner).load()!!;expectedSmtp=login;expectedAccount=null;state=state.copy(editor="smtp",host=login.smtpHost,port=login.smtpPort.toString(),from=login.from,ssl=login.smtpSsl,dirty=false,error=null,notice=null)}
        catch(_:Exception){state=state.copy(error="Не удалось прочитать настройки отправки. Обновите аккаунты.")}
    }
    fun changePassword(id:String){
        if(!foreground||closed||state.busy||state.restart||AppLock.locked(context)||state.editor!=null)return
        val account=runCatching {vault().list().find {it.identity()==id||NoticeCenter.scope(it)==id}}.getOrNull() ?: return
        expectedSmtp=null;expectedAccount=account
        state=state.copy(editor="password",host=account.host,port=account.port.toString(),user=account.user,password="",from=account.from,ssl=account.smtpSsl,dirty=false,discard=false,error=null,notice=null)
    }
    private fun interactive()=foreground&&!closed&&!AppLock.locked(context)&&state.open&&state.ready&&!state.busy&&!state.restart
    fun field(key:String,value:String){if(!interactive()||state.editor==null||state.discard)return
        if(state.editor=="password"){if(key=="password"&&value.length<=4096)state=state.copy(password=value,dirty=value.isNotEmpty(),error=null);return}
        state=when(key){"host"->state.copy(host=value);"port"->state.copy(port=value);"user"->state.copy(user=value);"password"->state.copy(password=value);"from"->state.copy(from=value);else->return}.copy(dirty=true,error=null)
    }
    fun ssl(value:Boolean){if(interactive()&&state.editor=="smtp"&&!state.discard)state=state.copy(ssl=value,dirty=true,error=null)}
    fun back(){if(!interactive())return
        if(state.confirmation!=null){state=state.copy(confirmation=null);return}
        if(state.editor!=null){if(state.dirty)state=state.copy(discard=true) else discard(true);return}
        if(state.owner.isNotEmpty())state=state.copy(open=false)
    }
    fun discard(yes:Boolean){if(interactive()){state=if(yes)state.copy(editor=null,password="",dirty=false,discard=false,error=null) else state.copy(discard=false);if(yes){expectedSmtp=null;expectedAccount=null}}}
    fun confirm(action:String?){if(interactive()&&state.editor==null&&(action==null||action=="logout"||state.rows.any {it.id==action}))state=state.copy(confirmation=action,error=null)}
    fun switch(id:String){if(interactive()&&state.editor==null&&state.rows.any {it.id==id&&!it.active})change("switch",id)}
    fun quickSwitch(id:String){
        if(!foreground||closed||AppLock.locked(context)||!state.ready||state.busy||state.restart||state.editor!=null)return
        if(state.rows.any {it.id==id&&!it.active})change("switch",id)
    }
    fun confirmed(){if(interactive())state.confirmation?.let {change(if(it=="logout")"logout" else "remove",it)}}
    private fun change(kind:String,id:String){
        val snapshot=state;mutating=true;state=state.copy(busy=true,error=null)
        worker.execute {try {
            var restart=false
            if(kind=="remove"){
                val target=synchronized(CredentialVault::class.java){current(snapshot.owner).list().single {NoticeCenter.scope(it)==id}}
                allowed();newCalendarClear(target);current(snapshot.owner).remove(target);restart=id==snapshot.owner
            } else synchronized(CredentialVault::class.java){
                val v=current(snapshot.owner)
                when(kind){
                    "logout"->{v.deactivate();restart=true}
                    "switch"->{val target=v.list().single {NoticeCenter.scope(it)==id};allowed();v.save(target);restart=true}
                }
            }
            runCatching {NoticeCenter.schedule(context)}
            main.post {mutating=false;if(!closed){state=state.copy(busy=false,confirmation=null,restart=restart,notice=if(restart)null else "Сохранённый вход удалён.");if(!restart&&foreground)load()}}
        }catch(_:Exception){main.post {mutating=false;if(!closed)state=state.copy(busy=false,error="Операция не завершена. Обновите список перед повтором; удаление могло остановить очередь и очистить часть локальных данных.")}}}
    }
    private fun newCalendarClear(login:SavedAccounts.Login){CalendarBook(context,login).clear()}
    fun save(){
        if(!interactive()||state.editor==null||state.discard)return
        val snapshot=state
        val oldPasswordAccount=if(snapshot.editor=="password")(expectedAccount ?: return) else null
        val target=try {when(snapshot.editor){
            "login"->NativeAccountPolicy.login(snapshot.host,snapshot.port,snapshot.user,snapshot.password)
            "password"->NativeAccountPolicy.password(oldPasswordAccount,snapshot.password)
            else->NativeAccountPolicy.smtp(expectedSmtp ?: error("No baseline"),snapshot.host,snapshot.port,snapshot.ssl,snapshot.from)
        }}catch(error:Exception){state=state.copy(error=error.message ?: "Проверьте поля.");return}
        val ticket=gate.next();mutating=true;state=state.copy(busy=true,error=null)
        worker.execute {
            var committed=false
            var updatedRows:List<NativeAccountSummary>?=null
            var initialInboxPage:org.json.JSONObject?=null
            try {
                if(snapshot.editor=="login"){
                    synchronized(CredentialVault::class.java){require(current(snapshot.owner).list().none {it.identity()==target.identity()}) {"Этот ящик уже сохранён. Выберите его в списке."}}
                    val reader=MailReader();gate.attach(ticket,reader)
                    try {
                        check(gate.current(ticket));reader.connect(target.host,target.port,target.user,target.password);check(gate.current(ticket))
                        initialInboxPage=runCatching {if(gate.current(ticket))reader.page("INBOX",0,"","") else null}.getOrNull()
                    }
                    finally {gate.detach(ticket,reader);reader.close()}
                } else if(snapshot.editor=="password"){
                    val old=oldPasswordAccount!!
                    synchronized(CredentialVault::class.java){
                        val v=current(snapshot.owner)
                        val currentTarget=v.list().find {it.identity()==old.identity()}
                        check(NativeAccountPolicy.same(currentTarget,old))
                    }
                    val reader=MailReader().also {gate.attach(ticket,it)}
                    try {check(gate.current(ticket));reader.connect(target.host,target.port,target.user,target.password);check(gate.current(ticket))}
                    finally {gate.detach(ticket,reader);reader.close()}
                }
                synchronized(CredentialVault::class.java){
                    allowed();check(gate.current(ticket))
                    val v=current(snapshot.owner)
                    when(snapshot.editor){
                        "login"->{check(v.list().none {it.identity()==target.identity()});v.save(target)}
                        "password"->{
                            val old=oldPasswordAccount!!
                            val currentTarget=v.list().find {it.identity()==old.identity()}
                            check(NativeAccountPolicy.same(currentTarget,old))
                            v.updatePassword(old,target.password)
                            val active=v.load()
                            updatedRows=v.list().map {NativeAccountSummary(NoticeCenter.scope(it),it.user,it.host,NativeAccountPolicy.same(it,active))}
                        }
                        else->{check(NativeAccountPolicy.same(v.load(),expectedSmtp));v.save(target)}
                    }
                    committed=true
                }
                if(snapshot.editor=="login"&&initialInboxPage!=null){
                    runCatching {if(NoticeCenter.settings(context,target)?.optBoolean("offlineMail",true)!=false)OfflineMailStore(context,target).savePage("INBOX",0,initialInboxPage)}
                }
                runCatching {NoticeCenter.schedule(context)}
                main.post {mutating=false;if(!closed){
                    expectedSmtp=null;expectedAccount=null
                    if(snapshot.editor=="password")state=state.copy(rows=updatedRows ?: state.rows,password="",busy=false,editor=null,dirty=false,discard=false,error=null,notice="Сохранённый пароль обновлён.")
                    else state=state.copy(password="",busy=false,editor=null,dirty=false,restart=true)
                }}
            }catch(error:Exception){main.post {mutating=false;if(!closed)state=state.copy(password="",busy=false,error=if(committed)"Вход сохранён. Закройте и откройте приложение." else if(!gate.current(ticket))"Подключение отменено. Введите пароль снова." else if((snapshot.editor=="login"||snapshot.editor=="password")&&error is javax.mail.MessagingException)LoginFailure.describe(error,target.host,target.user,target.password) else if(error is IllegalArgumentException)error.message else "Не удалось сохранить вход. Проверьте соединение и обновите аккаунты.")}}
        }
    }
    override fun onCleared(){closed=true;foreground=false;expectedSmtp=null;expectedAccount=null;state=AccountsState();gate.next();worker.shutdown();closer.shutdown()}
}
