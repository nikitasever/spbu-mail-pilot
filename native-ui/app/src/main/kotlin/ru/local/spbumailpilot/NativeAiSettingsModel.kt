package ru.local.spbumailpilot

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.compose.runtime.*
import org.json.JSONObject
import java.util.concurrent.Executors

internal data class NativeAiState(val owner:String="",val open:Boolean=false,val ready:Boolean=false,val busy:Boolean=false,
    val enabled:Boolean=true,val configured:Boolean=false,val model:String=AiConfiguration.DEFAULT_MODEL,val initialModel:String=AiConfiguration.DEFAULT_MODEL,
    val proxyConfigured:Boolean=false,val proxyPassword:String="",val key:String="",val removeConfirm:Boolean=false,val discard:Boolean=false,val error:String?=null,val notice:String?=null) {
    override fun toString()="NativeAiState(open=$open,ready=$ready,busy=$busy)"
}

/** No secret is loaded for display or persisted in SavedStateHandle. Opening/saving sends no mail. */
internal class NativeAiSettingsModel(app:Application):AndroidViewModel(app) {
    private val context=app.applicationContext
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    @Volatile private var foreground=false
    @Volatile private var closed=false
    private var generation=0L
    private var mutating=false
    private var options=JSONObject()
    private var modelBaseline=JSONObject()
    private var keyVersion=-1L
    private var proxyVersion=-1L
    var state by mutableStateOf(NativeAiState());private set
    private fun allowed(owner:String):SavedAccounts.Login {
        check(foreground&&!closed&&!AppLock.locked(context));val login=CredentialVault(context).load() ?: error("No account")
        check(NoticeCenter.scope(login)==owner);return login
    }
    private fun interactive()=foreground&&!closed&&!AppLock.locked(context)&&state.open&&state.ready&&!state.busy&&!state.discard&&!state.removeConfirm
    fun resume(){foreground=true;if(state.open&&!mutating)load()}
    fun pause(){foreground=false;generation++;state=state.copy(key="",proxyPassword="",removeConfirm=false,discard=false)}
    fun open(owner:String){if(!foreground||closed||state.busy||AppLock.locked(context)||!NativeOpenTarget.account(owner))return
        if(state.owner!=owner){state=NativeAiState(owner=owner);options=JSONObject();modelBaseline=JSONObject();keyVersion=-1}
        state=state.copy(open=true);load()
    }
    fun load(){
        if(!foreground||closed||!state.open||mutating||AppLock.locked(context))return
        val snapshot=state;val ticket=++generation;state=state.copy(busy=true,error=null)
        worker.execute {try {
            val login=allowed(snapshot.owner)
            val data=synchronized(ReplyReminderCenter::class.java){JSONObject(NoticeCenter.state(context,login).getString("state","{}")!!)}
            val proxyStatus=synchronized(GeminiProxyVault::class.java){val proxy=GeminiProxyVault(context);proxy.configured() to proxy.version()}
            val next=NativeGeneralSettings.options(data);val model=AiConfiguration.model(data)
            val status=synchronized(AiVault::class.java){val vault=AiVault(context);vault.configured() to vault.version()};allowed(snapshot.owner)
            main.post {if(!closed&&foreground&&generation==ticket&&!AppLock.locked(context)){
                options=next;keyVersion=status.second;proxyVersion=proxyStatus.second
                val dirtyModel=state.model!=state.initialModel
                if(!dirtyModel)modelBaseline=JSONObject(next.toString())
                state=state.copy(busy=false,ready=true,proxyConfigured=proxyStatus.first,enabled=AiPolicy.enabled(data.toString()),configured=status.first,
                    model=if(dirtyModel)state.model else model,initialModel=if(dirtyModel)state.initialModel else model)
            }}
        }catch(_:Exception){main.post {if(!closed&&foreground&&generation==ticket)state=state.copy(busy=false,ready=false,error="Не удалось прочитать настройки ИИ. Ключ не удалён; повторите загрузку.")}}}
    }
    fun model(value:String){if(interactive()&&value.length<=120)state=state.copy(model=value,error=null,notice=null)}
    fun key(value:String){if(interactive()&&value.length<=512)state=state.copy(key=value,error=null,notice=null)}
    fun toggle(value:Boolean){if(interactive())change("neuralAnalysis",value,JSONObject(options.toString()))}
    fun saveModel(){if(!interactive())return
        val model=try{AiConfiguration.modelId(state.model)}catch(error:Exception){state=state.copy(error=error.message);return}
        change("geminiModel",model,JSONObject(modelBaseline.toString()))
    }
    private fun change(key:String,value:Any,expected:JSONObject){
        val owner=state.owner;mutating=true;generation++;state=state.copy(busy=true,error=null,notice=null)
        worker.execute {try {
            val login=allowed(owner)
            val next=synchronized(ReplyReminderCenter::class.java){
                val prefs=NoticeCenter.state(context,login);val current=JSONObject(prefs.getString("state","{}")!!)
                val next=NativeGeneralSettings.change(current,expected,key,value);allowed(owner)
                val text=next.toString();check(text.length<=2000000);check(prefs.edit().putString("state",text).commit());next
            }
            if(key=="geminiModel"||value==false)GeminiDigest.cancelAll()
            main.post {mutating=false;if(!closed&&state.owner==owner){
                options=NativeGeneralSettings.options(next)
                if(key=="geminiModel")modelBaseline=JSONObject(options.toString())
                state=state.copy(busy=false,enabled=AiPolicy.enabled(next.toString()),model=if(key=="geminiModel")value as String else state.model,
                    initialModel=if(key=="geminiModel")value as String else state.initialModel,notice=if(key=="geminiModel")"Модель сохранена. Доступность проверится при явном запросе сводки." else if(value==false)"Нейроанализ отключён для этого ящика. Текущие запросы отменены." else "Нейроанализ разрешён для этого ящика. Данные отправляются только по вашему действию.")
            }}
        }catch(error:Exception){main.post {mutating=false;if(!closed)state=state.copy(busy=false,error=if(error is SharedStateMerge.Conflict)"Настройка изменилась в другом окне. Скопируйте введённое значение, закройте экран и откройте снова." else "Не удалось сохранить настройку ИИ. Изменение не подтверждено.")}}}
    }
    fun confirmRemove(value:Boolean){if(interactive()||state.removeConfirm&&!state.busy&&foreground&&!AppLock.locked(context))state=state.copy(removeConfirm=value,error=null)}
    fun saveKey(){if(!interactive())return
        val value=try{AiConfiguration.key(state.key)}catch(error:Exception){state=state.copy(error=error.message);return}
        secret(value)
    }
    fun removeKey(){if(state.removeConfirm&&!state.busy&&foreground&&!AppLock.locked(context))secret(null)}
    private fun secret(value:String?){
        val owner=state.owner;val expected=keyVersion;mutating=true;generation++;state=state.copy(busy=true,error=null,notice=null)
        worker.execute {try {
            allowed(owner);val next=synchronized(AiVault::class.java){
                allowed(owner);val vault=AiVault(context)
                if(value==null)vault.remove(expected) else vault.save(value,expected)
                vault.configured() to vault.version()
            }
            GeminiDigest.cancelAll()
            main.post {mutating=false;if(!closed&&state.owner==owner){keyVersion=next.second;state=state.copy(busy=false,key="",removeConfirm=false,configured=next.first,notice=if(value==null)"Общий ключ удалён для всех ящиков." else "Общий ключ зашифрован и сохранён. Сетевой запрос не выполнялся.")}}
        }catch(_:Exception){GeminiDigest.cancelAll();main.post {mutating=false;if(!closed)state=state.copy(busy=false,removeConfirm=false,error="Ключ не сохранён или удаление не подтверждено. Возможно, его изменили в другом окне. Перечитайте статус перед повтором.")}}}
    }
    fun proxyPassword(value:String){if(interactive()&&value.length<=256)state=state.copy(proxyPassword=value,error=null,notice=null)}
    fun saveProxy(){if(interactive()&&GeminiProxyTransport.validPassword(state.proxyPassword))proxySecret(state.proxyPassword)}
    fun disableProxy(){if(interactive()&&state.proxyConfigured)proxySecret(null)}
    private fun proxySecret(value:String?){
        val owner=state.owner;val expected=proxyVersion;mutating=true;generation++;state=state.copy(busy=true,error=null,notice=null)
        worker.execute {try{
            allowed(owner)
            val status=synchronized(GeminiProxyVault::class.java){allowed(owner);val vault=GeminiProxyVault(context);if(value==null)vault.remove(expected) else vault.save(value,expected);vault.configured() to vault.version()}
            GeminiDigest.cancelAll()
            main.post {mutating=false;if(!closed&&state.owner==owner){proxyVersion=status.second;state=state.copy(busy=false,proxyPassword="",proxyConfigured=status.first,notice=if(value==null)"Прокси отключён. Gemini будет подключаться напрямую." else "Прокси сохранён для Gemini. Почта и календарь подключаются напрямую. Соединение ещё не проверено.")}}
        }catch(_:Exception){GeminiDigest.cancelAll();main.post{mutating=false;if(!closed)state=state.copy(busy=false,error="Не удалось сохранить прокси. Перечитайте статус и повторите.")}}}
    }
    private fun dirty()=state.proxyPassword.isNotEmpty()||state.key.isNotEmpty()||state.model!=state.initialModel
    fun back(){if(!foreground||closed||state.busy)return
        if(state.removeConfirm){state=state.copy(removeConfirm=false);return}
        if(dirty())state=state.copy(discard=true) else {generation++;state=state.copy(open=false,key="",proxyPassword="")}
    }
    fun discard(value:Boolean){if(!state.busy){state=if(value)state.copy(open=false,key="",proxyPassword="",model=state.initialModel,discard=false) else state.copy(discard=false);if(value)generation++}}
    override fun onCleared(){closed=true;foreground=false;generation++;state=NativeAiState();worker.shutdown()}
}
