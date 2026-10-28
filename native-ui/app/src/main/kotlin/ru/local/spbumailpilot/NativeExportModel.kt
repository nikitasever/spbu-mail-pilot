package ru.local.spbumailpilot
import android.app.Application
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.compose.runtime.*
import org.json.JSONArray
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

internal data class ExportState(val open:Boolean=false,val phase:String="confirm",val original:Boolean=false,val total:Int=0,
    val stage:String="connecting",val done:Int=0,val saved:Int=0,val failures:Int=0,val name:String="",val bytes:Long=0,val size:Long=-1,val notice:String="",val pickerRequested:Boolean=false,
    val failureNames:List<String> = emptyList())

/** Shared bulk engine owns .part/rename/collision handling; lifecycle owns only a transient plan. */
internal class NativeExportModel(app:Application):AndroidViewModel(app) {
    val pickerKey="native-export-"+java.util.UUID.randomUUID().toString()
    private val context=app.applicationContext
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    private val closer=Executors.newSingleThreadExecutor()
    private val gate=NativeReadGate(closer)
    @Volatile private var closed=false
    @Volatile private var foreground=false
    private var plan:NativeExportPlan?=null
    private val picker=NativeExportPicker()
    private var destination:Uri?=null
    private var cancelFlag=AtomicBoolean(false)
    var state by mutableStateOf(ExportState());private set
    private fun owner()=CredentialVault(context).load()?.let {NoticeCenter.scope(it)}
    fun begin(account:String,ref:MailRef,files:List<NativeFile>,original:Boolean){
        if(!foreground||closed||AppLock.locked(context))return
        if(plan!=null||picker.pending()){state=if(plan==null)ExportState(open=true,phase="result",notice="Завершите предыдущий выбор папки и повторите сохранение.") else state.copy(open=true,notice="Завершите предыдущее сохранение.");return}
        try {
            check(owner()==account)
            val rows=JSONArray();files.forEach {rows.put(org.json.JSONObject().put("path",it.path))}
            plan=NativeExportPlan.from(account,ref.folder,ref.uid,ref.validity,rows,original)
            destination=null;cancelFlag=AtomicBoolean(false)
            state=ExportState(open=true,original=original,total=plan!!.refs.size)
        }catch(_:Exception){plan=null;state=ExportState(open=true,phase="result",notice="Не удалось подготовить сохранение. Откройте письмо заново.")}
    }
    fun confirm(){
        val p=plan ?: return
        if(!foreground||AppLock.locked(context)||state.phase!="confirm"||runCatching {owner()!=p.owner}.getOrDefault(true))return
        state=state.copy(phase="picker",pickerRequested=true,notice="Выберите папку в системном окне.")
    }
    fun takePickerRequest():Boolean {
        val p=plan ?: return false
        if(!foreground||AppLock.locked(context)||!state.pickerRequested||picker.pending())return false
        if(runCatching {owner()!=p.owner}.getOrDefault(true)){cancel();return false}
        if(!picker.launch(p))return false;state=state.copy(pickerRequested=false);return true
    }
    fun pickerFailed(){picker.result();plan=null;destination=null;state=state.copy(phase="result",pickerRequested=false,notice="Не удалось открыть выбор папки.")}
    fun picked(uri:Uri?){
        val request=picker.result() ?: return
        // A late result of a cancelled picker cannot attach to any later export.
        if(plan!==request)return
        if(uri==null){plan=null;state=state.copy(phase="result",notice="Выбор папки отменён.");return}
        if(uri.scheme!="content"||!android.provider.DocumentsContract.isTreeUri(uri)){pickerFailed();return}
        destination=uri;state=state.copy(phase="ready",notice="Ожидаем возвращения в приложение и разблокировки.");startIfReady()
    }
    fun resume(){
        foreground=true
        val p=plan
        if(p!=null&&!AppLock.locked(context)&&runCatching {owner()!=p.owner}.getOrDefault(true)){
            cancel();state=state.copy(notice="Аккаунт изменился. Сохранение отменено.")
        }
        startIfReady()
    }
    fun pause(recreating:Boolean){foreground=false;if(!recreating&&state.phase=="transfer")cancel()}
    fun cancel(){
        if(state.phase=="transfer"){cancelFlag.set(true);gate.next();state=state.copy(open=false,notice="Останавливаем сохранение. Остальные действия доступны.");return}
        plan=null;destination=null;state=ExportState() // Keep launched identity until its result is consumed.
    }
    private fun startIfReady(){
        val request=plan ?: return;val tree=destination ?: return
        if(closed||!foreground||AppLock.locked(context)||state.phase!="ready")return
        if(runCatching {owner()!=request.owner}.getOrDefault(true)){cancel();return}
        val ticket=gate.next();val cancelled=cancelFlag
        state=state.copy(phase="transfer",notice="Сохраняем в выбранную папку…")
        worker.execute {
            var reader:MailReader?=null
            try {
                val login=CredentialVault(context).load() ?: error("No account")
                check(NoticeCenter.scope(login)==request.owner)
                val active=MailReader();reader=active;gate.attach(ticket,active)
                check(gate.current(ticket)&&!cancelled.get())
                active.connect(login.host,login.port,login.user,login.password)
                var last=0L
                val result=BulkAttachments.run(request.refs,{ref,stop,progress->
                    check(owner()==request.owner)
                    BulkAttachments.check(stop)
                    val folder=File(context.cacheDir,"attachments")
                    val value=if(request.original)active.exportMessage(ref.folder,ref.uid,ref.validity,folder,false,stop,progress)
                        else active.attachment(ref.folder,ref.uid,ref.validity,ref.path,folder,stop,progress)
                    val file=NativeExportPlan.checkedFile(folder,value.getString("file"))
                    try {
                        check(owner()==request.owner)
                        BulkAttachments.check(stop)
                        BulkAttachments.Download(file,value.getString("name"),value.getString("mime"),true)
                    }catch(error:Exception){file.delete();throw error}
                },SafAttachmentDestination(context.contentResolver,tree),{closed||cancelled.get()||!gate.current(ticket)},
                {done,total,name,stage,bytes,size->
                    val now=android.os.SystemClock.elapsedRealtime()
                    if(stage=="finished"||bytes==size||now-last>=100){last=now;main.post {if(!closed&&plan===request)state=state.copy(stage=if(stage=="download")"downloading" else if(stage=="save")"copying" else "receiving",done=done,total=total,name=name,bytes=bytes,size=size)}}
                })
                main.post {if(!closed&&plan===request){
                    plan=null;destination=null
                    if(runCatching {owner()==request.owner}.getOrDefault(false))state=state.copy(phase="result",done=result.total,saved=result.saved,failures=result.failures.size,
                        notice=if(result.cancelled)"Сохранение остановлено. Уже сохранённые файлы оставлены в папке." else "Сохранение завершено.",
                        failureNames=result.failures.map {it.name+if(it.reason=="unsupported_destination")" — папка не поддерживает завершение временных файлов" else " — не удалось сохранить"})
                    else state=ExportState(open=true,phase="result",notice="Аккаунт изменился. Сохранение остановлено.")
                }}
            }catch(_:Exception){main.post {if(!closed&&plan===request){plan=null;destination=null;state=state.copy(phase="result",notice="Сохранение не завершено. Проверьте сеть и доступ к папке. Повтор выполняется только вручную.")}}}
            finally {reader?.let {gate.detach(ticket,it);runCatching {it.close()}}}
        }
    }
    override fun onCleared(){closed=true;cancelFlag.set(true);gate.next();worker.shutdown();closer.shutdown()}
}
