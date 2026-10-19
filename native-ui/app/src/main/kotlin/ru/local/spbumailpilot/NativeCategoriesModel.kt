package ru.local.spbumailpilot

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.compose.runtime.*
import org.json.JSONObject
import java.util.concurrent.Executors

internal data class CategoriesState(val owner: String="",val open: Boolean=false,val rules: List<JSONObject> = emptyList(),
    val editor: JSONObject?=null,val initial: JSONObject?=null,val expected: JSONObject?=null,val busy: Boolean=false,
    val error: String?=null,val notice: String?=null,val leaveOpen: Boolean=false,val filter: String="")

/** Rules are local; notification services read the same mailCategories array. */
internal class NativeCategoriesModel(app: Application):AndroidViewModel(app) {
    private val context=app.applicationContext
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    @Volatile private var foreground=false
    @Volatile private var closed=false
    @Volatile private var generation=0L
    private var mutating=false
    private var loaded=JSONObject()
    var state by mutableStateOf(CategoriesState());private set
    private fun login(owner: String?=null):SavedAccounts.Login {
        check(foreground&&!closed&&!AppLock.locked(context))
        val login=CredentialVault(context).load() ?: error("No account")
        check(owner==null||NoticeCenter.scope(login)==owner);return login
    }
    fun resume(){foreground=true;if(!mutating)load()}
    fun pause(){foreground=false;generation++;state=state.copy(busy=mutating,leaveOpen=false)}
    fun open(owner: String){
        if(!foreground||state.busy||AppLock.locked(context)||owner.isBlank())return
        if(state.owner!=owner)state=CategoriesState(owner=owner)
        state=state.copy(open=true,error=null);load(owner)
    }
    fun filter(id: String){if(state.editor==null&&(id.isEmpty()||state.rules.any {it.optString("id")==id&&it.optBoolean("enabled",true)}))state=state.copy(filter=id)}
    fun load(expectedOwner: String?=null){
        if(!foreground||closed||mutating)return
        val ticket=++generation;state=state.copy(busy=true,error=null)
        worker.execute {
            try {
                val account=login(expectedOwner);val owner=NoticeCenter.scope(account)
                val data=synchronized(ReplyReminderCenter::class.java){JSONObject(NoticeCenter.state(context,account).getString("state","{}")!!)}
                val rows=NativeCategoryRules.list(data);val rules=(0 until rows.length()).map {JSONObject(rows.getJSONObject(it).toString())}
                login(owner)
                main.post {if(!closed&&foreground&&generation==ticket){
                    loaded=data
                    val prior=if(state.owner==owner)state else CategoriesState(owner=owner)
                    state=prior.copy(rules=rules,busy=false,error=null,filter=prior.filter.takeIf {id->rules.any {it.optString("id")==id&&it.optBoolean("enabled",true)}} ?: "")
                }}
            }catch(_: Exception){
                val active=runCatching {CredentialVault(context).load()?.let {NoticeCenter.scope(it)}}.getOrNull()
                main.post {if(!closed&&foreground&&generation==ticket){if(active!=state.owner){loaded=JSONObject();state=CategoriesState()}else state=state.copy(busy=false,error="Не удалось прочитать правила. Проверьте аккаунт; введённый текст остался в форме.")}}
            }
        }
    }
    fun edit(id: String){
        if(!foreground||state.busy||state.editor!=null||AppLock.locked(context))return
        val row=state.rules.find {it.optString("id")==id} ?: return
        val copy=JSONObject(row.toString())
        state=state.copy(editor=copy,initial=JSONObject(copy.toString()),expected=NativeCategoryRules.raw(loaded,id),error=null,notice=null)
    }
    fun field(key: String,value: String){
        if(state.busy||state.editor==null||state.leaveOpen||key !in listOf("name","senders","domains","subjects","contacts"))return
        if(value.length>(if(key=="name")40 else 1000))return
        state=state.copy(editor=JSONObject(state.editor!!.toString()).put(key,value),error=null)
    }
    fun option(key: String,value: Boolean){if(!state.busy&&state.editor!=null&&!state.leaveOpen&&key in listOf("enabled","notify"))state=state.copy(editor=JSONObject(state.editor!!.toString()).put(key,value),error=null)}
    fun back(){
        if(state.busy)return
        if(state.editor==null){generation++;state=state.copy(open=false)}
        else if(SharedStateMerge.same(state.editor,state.initial))leave(true) else state=state.copy(leaveOpen=true)
    }
    fun leave(confirm: Boolean){if(!state.busy)state=if(confirm)state.copy(editor=null,initial=null,expected=null,leaveOpen=false,error=null) else state.copy(leaveOpen=false)}
    fun save(){
        val editor=state.editor ?: return
        if(!foreground||closed||state.busy||mutating||AppLock.locked(context))return
        try{NativeCategoryRules.validate(editor)}catch(error: Exception){state=state.copy(error=error.message ?: "Проверьте условия.");return}
        val snapshot=state;generation++;mutating=true;state=state.copy(busy=true,error=null)
        worker.execute {
            try {
                val account=login(snapshot.owner)
                val next=synchronized(ReplyReminderCenter::class.java){
                    val destination=NoticeCenter.state(context,account);val current=JSONObject(destination.getString("state","{}")!!)
                    val next=NativeCategoryRules.change(current,editor.getString("id"),snapshot.expected,editor)
                    val json=next.toString();check(json.length<=2000000);login(snapshot.owner)
                    check(destination.edit().putString("state",json).commit());next
                }
                val rows=NativeCategoryRules.list(next);val rules=(0 until rows.length()).map {JSONObject(rows.getJSONObject(it).toString())}
                runCatching {NoticeCenter.schedule(context)}
                main.post {mutating=false;if(!closed&&state.owner==snapshot.owner){loaded=next;state=state.copy(rules=rules,editor=null,initial=null,expected=null,busy=false,
                    filter=state.filter.takeIf {id->rules.any {it.optString("id")==id&&it.optBoolean("enabled",true)}} ?: "",notice="Правила сохранены на телефоне.")}}
            }catch(error: Exception){main.post {mutating=false;if(!closed&&state.owner==snapshot.owner)state=state.copy(busy=false,error=if(error is SharedStateMerge.Conflict)"Эта категория изменилась в другом окне. Скопируйте текст, закройте форму и обновите список." else "Не удалось сохранить правила. Форма осталась открытой.")}}
        }
    }
    override fun onCleared(){closed=true;foreground=false;generation++;worker.shutdown()}
}
