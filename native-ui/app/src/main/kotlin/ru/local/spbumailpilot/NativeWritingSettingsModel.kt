package ru.local.spbumailpilot

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.compose.runtime.*
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.Executors

internal data class WritingSettingsState(val owner: String="",val open: Boolean=false,
    val templates: List<NativeWritingTools.Template> = emptyList(),val editor: String?=null,
    val id: String="",val title: String="",val body: String="",val auto: Boolean=true,
    val initialTitle: String="",val initialBody: String="",val initialAuto: Boolean=true,
    val busy: Boolean=false,val ready: Boolean=false,val error: String?=null,val notice: String?=null,
    val discard: Boolean=false,val deleting: NativeWritingTools.Template?=null)

internal class NativeWritingSettingsModel(app: Application):AndroidViewModel(app) {
    private val context=app.applicationContext
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    @Volatile private var foreground=false
    @Volatile private var closed=false
    @Volatile private var generation=0L
    private var mutating=false
    private var loaded=JSONObject()
    private var expected: JSONObject?=null
    var state by mutableStateOf(WritingSettingsState());private set
    private fun allowed(owner: String): SavedAccounts.Login {
        check(foreground&&!closed&&!AppLock.locked(context))
        val login=CredentialVault(context).load() ?: error("No account")
        check(NoticeCenter.scope(login)==owner);return login
    }
    private fun interactive()=foreground&&!closed&&!AppLock.locked(context)&&state.open&&state.ready&&!state.busy
    fun resume(){foreground=true;if(state.open&&!mutating)load()}
    fun pause(){foreground=false;generation++;state=state.copy(busy=mutating,discard=false)}
    fun open(owner: String){
        if(!foreground||closed||mutating||AppLock.locked(context)||owner.isBlank())return
        if(state.owner!=owner){state=WritingSettingsState(owner=owner);loaded=JSONObject();expected=null}
        state=state.copy(open=true);load()
    }
    fun load(){
        if(!foreground||closed||!state.open||mutating)return
        val owner=state.owner;val ticket=++generation;state=state.copy(busy=true,error=null)
        worker.execute {try {
            val account=allowed(owner)
            val data=synchronized(ReplyReminderCenter::class.java){JSONObject(NoticeCenter.state(context,account).getString("state","{}")!!)}
            val rows=NativeWritingSettings.list(data);NativeWritingSettings.signature(data);allowed(owner)
            main.post {if(!closed&&foreground&&generation==ticket&&!AppLock.locked(context)){
                loaded=data;state=state.copy(templates=rows,busy=false,ready=true)
            }}
        }catch(_: Exception){main.post {if(!closed&&foreground&&generation==ticket){
            val active=runCatching {CredentialVault(context).load()?.let {NoticeCenter.scope(it)}}.getOrNull()
            if(active!=owner){loaded=JSONObject();expected=null;state=WritingSettingsState()}
            else state=state.copy(busy=false,error="Не удалось прочитать настройки. Повторите загрузку; введённый текст сохранён.")
        }}}}
    }
    fun editSignature(){
        if(!interactive()||state.editor!=null)return
        val signature=runCatching {NativeWritingSettings.signature(loaded)}.getOrElse {state=state.copy(error="Не удалось прочитать подпись.");return}
        expected=signature
        val text=if(signature.opt("signatureText") is String)signature.optString("signatureText") else ""
        val auto=signature.opt("signatureAuto")!=false
        state=state.copy(editor="signature",body=text,auto=auto,initialBody=text,initialAuto=auto,error=null,notice=null)
    }
    fun editTemplate(id: String?=null){
        if(!interactive()||state.editor!=null)return
        val row=if(id==null)null else state.templates.find {it.id==id} ?: return
        expected=try {if(row==null)null else NativeWritingSettings.raw(loaded,row.id)}catch(_: Exception){state=state.copy(error="Этот шаблон неоднозначен. Обновите список.");return}
        state=state.copy(editor="template",id=row?.id ?: UUID.randomUUID().toString(),title=row?.title ?: "",body=row?.body ?: "",
            initialTitle=row?.title ?: "",initialBody=row?.body ?: "",error=null,notice=null)
    }
    fun title(value: String){if(interactive()&&!state.discard&&state.editor=="template")state=state.copy(title=value,error=null)}
    fun body(value: String){if(interactive()&&!state.discard&&state.editor!=null)state=state.copy(body=value,error=null)}
    fun automatic(value: Boolean){if(interactive()&&!state.discard&&state.editor=="signature")state=state.copy(auto=value,error=null)}
    fun back(){
        if(!foreground||state.busy)return
        if(state.deleting!=null){cancelDelete();return}
        if(state.discard){discard(false);return}
        if(state.editor==null){generation++;state=state.copy(open=false);return}
        if(state.body!=state.initialBody||(state.editor=="template"&&state.title!=state.initialTitle)||(state.editor=="signature"&&state.auto!=state.initialAuto))state=state.copy(discard=true)
        else discard(true)
    }
    fun discard(confirm: Boolean){if(!state.busy)state=if(confirm)state.copy(editor=null,discard=false,error=null) else state.copy(discard=false)}
    fun requestDelete(id: String){if(interactive()&&state.editor==null)state.templates.find {it.id==id}?.let {state=state.copy(deleting=it,error=null,notice=null)}}
    fun cancelDelete(){if(!state.busy)state=state.copy(deleting=null,error=null)}
    fun canExternalNavigation(): Boolean = !state.open || (!state.busy && state.editor==null && state.deleting==null && !state.discard)
    fun closeForNavigation(){if(canExternalNavigation()){generation++;state=state.copy(open=false)}}
    fun save(){if(interactive()&&state.editor!=null&&!state.discard)mutate(false)}
    fun confirmDelete(){if(interactive()&&state.deleting!=null)mutate(true)}
    private fun mutate(deleting: Boolean){
        val snapshot=state;val baseline=if(deleting)runCatching {NativeWritingSettings.raw(loaded,snapshot.deleting!!.id)}.getOrElse {state=state.copy(error="Обновите список перед удалением.");return} else expected
        val editor=if(deleting||snapshot.editor=="signature")null else JSONObject().put("id",snapshot.id).put("title",snapshot.title.trim()).put("body",snapshot.body)
        try {if(editor!=null)NativeWritingSettings.validate(editor);if(snapshot.editor=="signature"&&snapshot.body.length>4000)error("Сократите подпись до 4000 символов.")}
        catch(error: Exception){state=state.copy(error=error.message);return}
        generation++;mutating=true;state=state.copy(busy=true,error=null)
        worker.execute {try {
            val account=allowed(snapshot.owner)
            val next=synchronized(ReplyReminderCenter::class.java){
                val destination=NoticeCenter.state(context,account);val current=JSONObject(destination.getString("state","{}")!!)
                val next=if(snapshot.editor=="signature")NativeWritingSettings.changeSignature(current,baseline!!,snapshot.body,snapshot.auto)
                    else NativeWritingSettings.changeTemplate(current,if(deleting)snapshot.deleting!!.id else snapshot.id,baseline,editor)
                val json=next.toString();check(json.length<=2000000);allowed(snapshot.owner)
                check(destination.edit().putString("state",json).commit());next
            }
            val rows=NativeWritingSettings.list(next)
            main.post {mutating=false;if(!closed&&state.owner==snapshot.owner){loaded=next;expected=null;state=state.copy(templates=rows,editor=null,deleting=null,busy=false,notice=if(deleting)"Шаблон удалён." else if(snapshot.editor=="signature")"Подпись сохранена для этого ящика." else "Шаблон сохранён.")}}
        }catch(error: Exception){main.post {mutating=false;if(!closed&&state.owner==snapshot.owner)state=state.copy(busy=false,error=when(error){
            is SharedStateMerge.Conflict -> "Данные изменились в другом окне. Скопируйте текст, закройте форму и обновите список."
            is IllegalArgumentException -> error.message
            else -> "Не удалось сохранить. Введённый текст и подтверждение остались открыты; повторите попытку."
        })}}}
    }
    override fun onCleared(){closed=true;foreground=false;generation++;worker.shutdown()}
}
