package ru.local.spbumailpilot

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.compose.runtime.*
import org.json.JSONObject
import java.util.concurrent.Executors

internal data class NativeSettingsState(val owner:String="",val open:Boolean=false,val ready:Boolean=false,val busy:Boolean=false,
    val options:JSONObject=JSONObject(),val error:String?=null,val notice:String?=null,val icon:String="spbu",val cacheBytes:Long?=null,val clearConfirm:Boolean=false,val cacheRevision:Long=0)
internal class NativeSettingsModel(app:Application):AndroidViewModel(app) {
    private val context=app.applicationContext
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    @Volatile private var foreground=false
    @Volatile private var closed=false
    private var generation=0L
    private var mutating=false
    var state by mutableStateOf(NativeSettingsState());private set
    private fun allowed(owner:String):SavedAccounts.Login {
        check(foreground&&!closed&&!AppLock.locked(context))
        val login=CredentialVault(context).load() ?: error("No account");check(NoticeCenter.scope(login)==owner);return login
    }
    fun resume(){foreground=true;if(!mutating)load()}
    fun pause(){foreground=false;generation++;state=state.copy(clearConfirm=false)}
    fun open(){if(foreground&&!state.busy&&!AppLock.locked(context)){state=state.copy(open=true);load()}}
    fun back(){if(!state.busy){if(state.clearConfirm){state=state.copy(clearConfirm=false);return};generation++;state=state.copy(open=false)}}
    fun load(){
        if(!foreground||closed||mutating||AppLock.locked(context))return
        val ticket=++generation;state=state.copy(busy=true,error=null)
        worker.execute {try {
            val login=CredentialVault(context).load();val owner=login?.let {NoticeCenter.scope(it)} ?: ""
            val options=if(login==null)JSONObject() else synchronized(ReplyReminderCenter::class.java){allowed(owner);NativeGeneralSettings.options(JSONObject(NoticeCenter.state(context,login).getString("state","{}")!!))}
            // Restore persistent checks on every normal Compose entry/resume, even if settings were never edited.
            val noticesScheduled=runCatching {NoticeCenter.schedule(context)}.isSuccess
            val bytes=login?.let {runCatching {OfflineMailStore.diskBytes(context,it)}.getOrNull()}
            val icon=LauncherIcons.selected(context)
            main.post {if(!closed&&foreground&&generation==ticket&&!AppLock.locked(context))state=state.copy(owner=owner,ready=login!=null,busy=false,options=JSONObject(options.toString()),icon=icon,cacheBytes=bytes,error=null,notice=if(noticesScheduled)null else "Фоновая проверка не настроена. Откройте приложение повторно.",cacheRevision=if(state.owner==owner)state.cacheRevision else 0)}
        }catch(_:Exception){main.post {if(!closed&&foreground&&generation==ticket)state=state.copy(busy=false,ready=false,error="Настройки не прочитаны. Повторите загрузку; данные не изменены.")}}}
    }
    fun change(key:String,value:Any){
        if(!foreground||closed||AppLock.locked(context)||!state.open||!state.ready||state.busy||state.clearConfirm)return
        try{NativeGeneralSettings.validate(key,value)}catch(_:Exception){return}
        val snapshot=state;mutating=true;state=state.copy(busy=true,error=null,notice=null)
        worker.execute {try {
            val login=allowed(snapshot.owner)
            val options=synchronized(ReplyReminderCenter::class.java){
                val prefs=NoticeCenter.state(context,login);val current=JSONObject(prefs.getString("state","{}")!!)
                val next=NativeGeneralSettings.change(current,snapshot.options,key,value);allowed(snapshot.owner)
                val json=next.toString();check(json.length<=2000000);check(prefs.edit().putString("state",json).commit());NativeGeneralSettings.options(next)
            }
            if(key=="theme")context.getSharedPreferences("appearance",0).edit().putString("theme",value as String).apply()
            val scheduled=runCatching {NoticeCenter.schedule(context);MailWidget.request(context)}.isSuccess
            main.post {mutating=false;if(!closed&&state.owner==snapshot.owner)state=state.copy(options=JSONObject(options.toString()),busy=false,notice=if(scheduled)"Сохранено для этого ящика." else "Настройка сохранена. Планировщик не обновился; откройте приложение повторно.")}
        }catch(error:Exception){main.post {mutating=false;if(!closed)state=state.copy(busy=false,error=if(error is SharedStateMerge.Conflict)"Настройка изменилась в другом окне. Обновите экран перед повтором." else "Не удалось сохранить настройку. Повторите загрузку.")}}}
    }
    fun confirmClear(value:Boolean){
        if(foreground&&!closed&&!AppLock.locked(context)&&state.open&&state.ready&&!state.busy)state=state.copy(clearConfirm=value,error=null,notice=null)
    }
    fun clearOffline(){
        if(!foreground||closed||AppLock.locked(context)||!state.open||!state.ready||state.busy||!state.clearConfirm)return
        val owner=state.owner;mutating=true;state=state.copy(busy=true,error=null,notice=null)
        worker.execute {
            var attempted=false
            try {
                val login=allowed(owner);attempted=true;OfflineMailStore.delete(context,login)
                main.post {mutating=false;if(!closed&&state.owner==owner)state=state.copy(busy=false,clearConfirm=false,cacheBytes=0,cacheRevision=state.cacheRevision+1,notice="Офлайн-копии этого ящика удалены. Для загрузки писем нажмите «Обновить».")}
            }catch(_:Exception){main.post {mutating=false;if(!closed&&state.owner==owner)state=state.copy(busy=false,clearConfirm=false,cacheBytes=null,cacheRevision=state.cacheRevision+(if(attempted)1 else 0),error="Очистка не подтверждена: часть копий могла удалиться. Перечитайте размер и повторите попытку.")}}
        }
    }
    fun icon(value:String){
        if(value!="spbu"&&value!="classic"||!foreground||closed||AppLock.locked(context)||!state.open||!state.ready||state.busy||state.clearConfirm)return
        val owner=state.owner;mutating=true;state=state.copy(busy=true,error=null,notice=null)
        worker.execute {try{
            allowed(owner);LauncherIcons.change(context,value)
            main.post {mutating=false;if(!closed&&state.owner==owner)state=state.copy(busy=false,icon=value,notice="Иконка изменена для всего приложения. Рабочий стол может обновиться с задержкой.")}
        }catch(_:Exception){main.post {mutating=false;if(!closed)state=state.copy(busy=false,error="Не удалось изменить иконку. Прежние настройки восстановлены по возможности; проверьте значки рабочего стола.")}}}
    }
    fun action(run:()->Unit){if(!foreground||closed||AppLock.locked(context)||state.busy||!state.open)return
        try {allowed(state.owner);run()}catch(_:Exception){state=state.copy(error="Действие недоступно. Проверьте выбранный аккаунт и настройки Android.")}
    }
    override fun onCleared(){closed=true;foreground=false;generation++;worker.shutdown()}
}
