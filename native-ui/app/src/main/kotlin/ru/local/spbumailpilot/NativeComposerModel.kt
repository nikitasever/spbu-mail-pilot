package ru.local.spbumailpilot

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.compose.runtime.*
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

internal data class ComposeAttachment(val token: String, val name: String, val size: Long)
internal data class ComposerState(val open: Boolean = false, val owner: String = "", val from: String = "",
    val to: String = "", val cc: String = "", val bcc: String = "", val subject: String = "", val body: String = "",
    val accountValid: Boolean = false, val picking: Boolean = false, val importing: Boolean = false, val transferName: String = "", val transferBytes: Long = 0,
    val files: List<ComposeAttachment> = emptyList(), val draftId: String = "", val busy: Boolean = false, val saved: Boolean = false, val status: String = NativeDraft.EDITING,
    val forwardEml:Boolean=false,val originalPending:Boolean=false, val forwardTextOnly: Boolean = false, val originalFiles: Int = 0, val forwardFilesAdded: Boolean = false,
    val discardOpen: Boolean = false,
    val bodyStart: Int = 0, val bodyEnd: Int = 0, val bodyComposing: Boolean = false, val insertion: Long = 0,
    val writingOpen: Boolean = false, val writingLoading: Boolean = false, val templates: List<NativeWritingTools.Template> = emptyList(), val writingError: String? = null,
    val conflicted:Boolean=false,val serverBackup:String="none",val serverReference:Boolean=false,val serverWorking:Boolean=false,val serverDelete:Boolean=false,val serverBytes:Long=0,val serverTotal:Long=0,
    val scheduleOpen:Boolean=false,val scheduleDay:String="",val scheduleTime:String="",
    val aiEnabled:Boolean=false,val aiOpen:Boolean=false,val aiText:String="",val aiInitial:String="",val aiDiscard:Boolean=false,val aiBusy:Boolean=false,
    val remaining: Int = 0, val error: String? = null, val notice: String? = null) {
    override fun toString()="ComposerState(open=$open,busy=$busy,status=$status)"
}

/** Retained across Activity recreation. Durable records are encrypted and account-bound. */
internal class NativeComposerModel(app: Application) : AndroidViewModel(app) {
    var state by mutableStateOf(ComposerState()); private set
    private val context = app.applicationContext
    val recipients = NativeRecipientSearch(context)
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val serverCloser=Executors.newSingleThreadExecutor()
    private val serverGate=NativeReadGate(serverCloser)
    private data class Pick(val owner: String, val id: String)
    private var pick: Pick? = null
    private val importCancelled = AtomicBoolean()
    private var aiReplyContext=""
    private val aiSerial=AtomicLong()
    private val resumeSerial=AtomicLong()
    private var record: NativeDraft? = null // worker only
    private var editVersion = 0L
    private val writingSerial=AtomicLong()
    private var saveTask: Runnable? = null
    private var delayTask: Runnable? = null
    private var deadline = 0L
    @Volatile private var foreground = false
    @Volatile private var closed = false

    fun resume() {
        foreground = true
        val ticket=resumeSerial.incrementAndGet()
        if(AppLock.locked(context))return
        recipients.clear(session=true)
        if (state.open && state.from.isNotBlank()) {
            val owner=state.owner;val draftId=state.draftId
            state=state.copy(accountValid=false)
            worker.execute {
                val valid=runCatching { CredentialVault(context).load()?.let { NoticeCenter.scope(it) } == owner }.getOrDefault(false)
                val enabled=runCatching {val login=allowed(owner);AiPolicy.enabled(NoticeCenter.state(context,login).getString("state","{}")!!)}.getOrDefault(false)
                publish { if (resumeSerial.get()==ticket && foreground && !AppLock.locked(context) && state.open && state.owner==owner && state.draftId==draftId) state=state.copy(accountValid=valid,aiEnabled=enabled,aiOpen=state.aiOpen&&enabled&&valid,aiText=if(enabled&&valid)state.aiText else "",aiInitial=if(enabled&&valid)state.aiInitial else "",
                    error=if(valid) state.error else "Текущий ящик изменился. Черновик сохранён для прежнего отправителя; вернитесь в нужный ящик.") }
            }
        }
    }
    fun pause() { serverGate.next();resumeSerial.incrementAndGet();aiSerial.incrementAndGet();state=state.copy(serverDelete=false,scheduleOpen=false,aiBusy=false,aiDiscard=false,bodyComposing=false,discardOpen=false);closeWritingTools(); recipients.clear(session=true); if (state.importing) importCancelled.set(true); foreground = false; cancelSend(); flush() }
    private fun allowed(owner: String): SavedAccounts.Login {
        check(foreground && !AppLock.locked(context))
        val login = CredentialVault(context).load() ?: error("Account unavailable")
        check(NoticeCenter.scope(login) == owner)
        return login
    }
    private fun publish(action: () -> Unit) { main.post { if (!closed) action() } }
    fun open(owner: String, source: JSONObject? = null, replyAll: Boolean = false, forwardText: String? = null, restoreOnly: Boolean = false, recipient: String? = null,replyText:String?=null,expectedId:String?=null,forwardSource:JSONObject?=null,forwardOriginal:Boolean=false) {
        if (state.open || owner.isEmpty() || expectedId!=null&&!restoreOnly) return
        if(replyAll&&forwardText!=null||forwardOriginal&&(source==null||forwardSource==null||forwardText!=null||replyAll||restoreOnly||recipient!=null))return
        if(recipient!=null&&(!NativeRecipients.email(recipient)||source!=null||forwardText!=null||replyAll||restoreOnly))return
        recipients.clear()
        state = ComposerState(open = true, owner = owner, busy = true)
        worker.execute {
            try {
                val login = allowed(owner)
                var existing = false
                val draft = synchronized(NativeDraftStore.gate) {
                    val store = NativeDraftStore(context, owner)
                    val chosen=if(restoreOnly)store.read()?.also { existing = true } else run {
                        val config=JSONObject(NoticeCenter.state(context, login).getString("state", "{}")!!)
                        val signature=NativeWritingTools.signatureBlock(config)
                        val text=if(config.optJSONObject("settings")?.optBoolean("signatureAuto",true)!=false&&signature.isNotEmpty()) "\n\n$signature" else ""
                        (if(forwardOriginal) NativeForwardOriginal.create(owner,source,forwardSource,text)
                        else if(forwardText!=null) NativeForward.create(owner,source,forwardText,text).let {if(forwardSource==null)it else NativeForwardFiles.prepare(it,forwardSource)}
                        else if(replyAll) NativeDraft.replyAll(owner,source,text,login.from,login.user)
                        else if(recipient!=null) NativeDraft.addressed(owner,recipient,text)
                        else NativeDraft.create(owner, source, text)).also { store.create(it) }
                    }
                    if(expectedId!=null)check(chosen?.mail?.optString("id")==expectedId)
                    if(chosen==null)null else NativeScheduleTransaction.recover(chosen,{store.write(it)},scheduleQueue(owner,0))
                }
                if(draft==null){record=null;publish {state=ComposerState(notice="Сохранённый черновик больше недоступен. Новое письмо не создано.")};return@execute}
                record = draft
                aiReplyContext=if(!existing&&source!=null&&forwardText==null&&!forwardOriginal)replyText ?: "" else ""
                val aiEnabled=AiPolicy.enabled(NoticeCenter.state(context,login).getString("state","{}")!!)
                publish { state = fields(draft, login.from).copy(aiEnabled=aiEnabled,notice = if(draft.mail.has("serverDraftSource"))"Открыта редактируемая копия серверного черновика. Исходник на сервере не изменён. Оформление и встроенные картинки не переносятся. Отправитель — текущий ящик; проверьте адресатов и не отправляйте обе копии." else if(draft.mail.has("legacyDraftSource"))"Открыта копия прежнего черновика. Исходник и его серверная копия оставлены без изменений. Перед отправкой проверьте, что исходник уже не отправлялся; не отправляйте обе копии." else if ((source != null||recipient!=null) && existing) "Открыт существующий черновик. Новое письмо не создано." else if (existing) "Открыт сохранённый черновик" else if(replyAll) "Ответ всем подготовлен. Проверьте поля «Кому» и «Копия» перед отправкой." else if(forwardOriginal) "Добавьте оригинал EML, проверьте его содержимое и укажите получателя. В файле сохраняются исходные заголовки и вложения, кроме верхних Bcc/Resent-Bcc." else if(forwardText!=null) "Укажите получателя пересылки." else null) }
            } catch (failure: Exception) { publish { state = state.copy(busy = false, error = if(failure is NativeReply.Invalid||failure is NativeForward.Invalid) failure.message else "Не удалось открыть черновик. Проверьте выбранный ящик, свободное место и лимит 50 черновиков. Существующие данные сохранены.") } }
        }
    }
    private fun fields(draft: NativeDraft, from: String) = ComposerState(open = true, owner = draft.owner, from = from,
        serverBackup=NativeServerDraftPlan.state(draft),serverReference=draft.mail.optJSONObject("serverDraft")!=null,draftId = draft.mail.getString("id"), files = attachments(draft.mail.getJSONArray("attachments")), forwardTextOnly=draft.mail.optString("kind")=="forward-text",forwardEml=draft.mail.optString("kind")=="forward-eml",originalPending=draft.mail.optString("kind")=="forward-eml"&&!ForwardOriginal.ready(draft.mail),originalFiles=if(draft.mail.optString("kind")=="forward-eml"&&!ForwardOriginal.ready(draft.mail))1 else NativeForwardFiles.pending(draft),forwardFilesAdded=draft.mail.optBoolean("forwardFilesAdded"),
        to = draft.mail.optString("to"), cc = draft.mail.optString("cc"), bcc = draft.mail.optString("bcc"),
        subject = draft.mail.optString("subject"), body = draft.mail.optString("body"), bodyStart=if(draft.revision>0)draft.mail.optString("body").length else 0, bodyEnd=if(draft.revision>0)draft.mail.optString("body").length else 0, saved = true, accountValid = true, status = draft.status)

    fun edit(field: String, value: String) {
        if (!state.open || state.conflicted || state.busy || state.serverDelete || state.scheduleOpen || state.aiOpen || state.writingOpen || state.discardOpen || state.picking || state.remaining > 0 || state.status != NativeDraft.EDITING || !state.accountValid) return
        val limit = when(field) { "body" -> 100000; "subject" -> 500; else -> 4000 }
        if (value.length > limit || field != "body" && (value.contains('\r') || value.contains('\n'))) return
        recipients.clear()
        state = when(field) { "to" -> state.copy(to=value); "cc" -> state.copy(cc=value); "bcc" -> state.copy(bcc=value)
            "subject" -> state.copy(subject=value); "body" -> state.copy(body=value); else -> return }.copy(saved=false, error=null, notice=null)
        editVersion++
        saveTask?.let(main::removeCallbacks)
        saveTask = Runnable { flush() }.also { main.postDelayed(it, 400) }
    }
    private fun canWrite(allowDiscard: Boolean=false)=foreground&&!AppLock.locked(context)&&state.open&&!state.conflicted&&!state.aiOpen&&!state.scheduleOpen&&!state.serverDelete&&!state.busy&&!state.picking&&state.remaining==0&&state.status==NativeDraft.EDITING&&state.accountValid&&(!state.discardOpen||allowDiscard)
    fun clearClosedNotice(){if(!state.open)state=state.copy(notice=null)}
    fun requestDiscard(){
        if(!canWrite()||state.writingOpen||state.draftId.isBlank())return
        recipients.clear();state=state.copy(discardOpen=true)
    }
    fun cancelDiscard(){state=state.copy(discardOpen=false)}
    fun discard(){
        if(!state.discardOpen||!canWrite(allowDiscard=true))return
        val snapshot=state
        state=state.copy(discardOpen=false,busy=true,error=null)
        saveTask?.let(main::removeCallbacks);saveTask=null
        worker.execute {
            try {
                val cleaned=synchronized(NativeDraftStore.gate) {
                    allowed(snapshot.owner)
                    val expected=record ?: error("Draft unavailable")
                    check(expected.owner==snapshot.owner&&expected.mail.getString("id")==snapshot.draftId)
                    val directory=attachmentDirectory(expected)
                    NativeDraftDiscard.run(expected,{NativeDraftStore(context,snapshot.owner).discard(it)},
                        {NativeAttachments.clearCopies(directory)})
                }
                record=null
                publish {state=ComposerState(notice=if(cleaned)"Черновик удалён с этого телефона." else "Черновик удалён. Часть локальных копий вложений не удалось очистить.")}
            }catch(_: Exception){publish {state=state.copy(busy=false,saved=false,error="Не удалось подтвердить удаление: черновик мог измениться в другом окне или хранилище недоступно. Текст остался в редакторе; локальные вложения не очищались.")}}
        }
    }
    fun bodyInput(value: String,start: Int,end: Int,composing: Boolean) {
        if(!canWrite()||state.writingOpen||value.length>100000)return
        if(value!=state.body)edit("body",value)
        state=state.copy(bodyStart=start.coerceIn(0,value.length),bodyEnd=end.coerceIn(0,value.length),bodyComposing=composing)
    }
    fun openAi(mode:String){
        if(!canWrite()||state.writingOpen||state.bodyComposing||!state.aiEnabled)return
        val snapshot=state;val version=editVersion;val ticket=aiSerial.incrementAndGet()
        val prompt=try{NativeWritingAi.prompt(mode,snapshot.subject,snapshot.body,aiReplyContext)}catch(error:Exception){state=state.copy(error=error.message);return}
        recipients.clear();saveTask?.let(main::removeCallbacks);saveTask=null;state=state.copy(busy=true,error=null)
        worker.execute {try{
            val login=allowed(snapshot.owner);check(AiPolicy.enabled(NoticeCenter.state(context,login).getString("state","{}")!!))
            if(!snapshot.saved)save(snapshot)
            publish {if(aiSerial.get()==ticket&&version==editVersion&&state.owner==snapshot.owner&&state.draftId==snapshot.draftId&&foreground&&!AppLock.locked(context))
                state=state.copy(busy=false,saved=true,aiOpen=true,aiText=prompt,aiInitial=prompt,aiDiscard=false)
                else state=state.copy(busy=false)}
        }catch(_:Exception){publish {state=state.copy(busy=false,error="Не удалось сохранить черновик или ИИ отключён. Передача текста не выполнялась.")}}}
    }
    fun aiText(value:String){if(foreground&&!closed&&!AppLock.locked(context)&&state.aiOpen&&!state.aiBusy&&!state.aiDiscard&&value.length<=120000)state=state.copy(aiText=value,error=null)}
    fun closeAi(){if(state.aiBusy)return
        if(state.aiText!=state.aiInitial)state=state.copy(aiDiscard=true) else discardAi(true)
    }
    fun discardAi(confirm:Boolean){if(!state.aiBusy){state=if(confirm)state.copy(aiOpen=false,aiText="",aiInitial="",aiDiscard=false) else state.copy(aiDiscard=false);if(confirm)aiSerial.incrementAndGet()}}
    fun handoffAi(copy:Boolean,action:(String,Boolean)->Unit){
        if(!foreground||closed||AppLock.locked(context)||!state.open||!state.aiOpen||state.aiBusy||state.aiDiscard||!state.accountValid)return
        val text=try{NativeWritingAi.validate(state.aiText)}catch(error:Exception){state=state.copy(error=error.message);return}
        val snapshot=state;val ticket=aiSerial.incrementAndGet();state=state.copy(aiBusy=true,error=null)
        worker.execute {try{
            val login=allowed(snapshot.owner);check(AiPolicy.enabled(NoticeCenter.state(context,login).getString("state","{}")!!))
            publish {
                if(aiSerial.get()!=ticket||!foreground||AppLock.locked(context)||!state.aiOpen||state.owner!=snapshot.owner||state.draftId!=snapshot.draftId||state.aiText!=text)return@publish
                try {val active=allowed(snapshot.owner);check(AiPolicy.enabled(NoticeCenter.state(context,active).getString("state","{}")!!));action(text,copy)
                    state=state.copy(aiBusy=false,notice=if(copy)"Запрос скопирован. Вставьте его в выбранный чат ИИ." else "Открыт выбор приложения. Передачу подтвердите там.")
                }catch(_:Exception){state=state.copy(aiBusy=false,error="Не удалось передать текст. Проверьте разрешение ИИ и активный аккаунт.")}
            }
        }catch(_:Exception){publish {if(aiSerial.get()==ticket)state=state.copy(aiBusy=false,error="ИИ отключён или активный аккаунт изменился. Текст не передан.")}}}
    }
    fun closeWritingTools(){writingSerial.incrementAndGet();state=state.copy(writingOpen=false,writingLoading=false,templates=emptyList(),writingError=null)}
    fun showTemplates() {
        if(!canWrite())return
        if(state.bodyComposing){state=state.copy(notice="Завершите ввод слова перед вставкой шаблона.");return}
        recipients.clear();val ticket=writingSerial.incrementAndGet();val owner=state.owner;val id=state.draftId
        state=state.copy(writingOpen=true,writingLoading=true,writingError=null)
        worker.execute {
            try {
                val login=allowed(owner)
                val config=JSONObject(NoticeCenter.state(context,login).getString("state","{}")!!)
                val items=NativeWritingTools.templates(config)
                publish {if(writingSerial.get()==ticket&&state.owner==owner&&state.draftId==id)state=state.copy(writingLoading=false,templates=items)}
            }catch(_: Exception){publish {if(writingSerial.get()==ticket)state=state.copy(writingLoading=false,writingError="Не удалось прочитать шаблоны. Проверьте выбранный ящик и повторите.")}}
        }
    }
    fun insertTemplate(id: String) {
        if(!canWrite()||!state.writingOpen||state.writingLoading||state.bodyComposing)return
        val template=state.templates.find {it.id==id} ?: return
        val snapshot=state
        val inserted=try {NativeWritingTools.insert(snapshot.body,template.body,snapshot.bodyStart,snapshot.bodyEnd)}catch(_: Exception){state=state.copy(writingError="Текст письма превышает 100 000 символов. Сократите его перед вставкой.");return}
        closeWritingTools();commitInsertion(snapshot,inserted,"Шаблон вставлен.")
    }
    private fun commitInsertion(snapshot: ComposerState,inserted: NativeWritingTools.Insertion,notice: String) {
        recipients.clear();state=state.copy(busy=true,error=null)
        saveTask?.let(main::removeCallbacks);saveTask=null
        worker.execute {
            try {
                allowed(snapshot.owner)
                save(snapshot.copy(body=inserted.body))
                publish {editVersion++;state=state.copy(body=inserted.body,bodyStart=inserted.cursor,bodyEnd=inserted.cursor,bodyComposing=false,insertion=state.insertion+1,busy=false,saved=true,notice=notice)}
            }catch(_: Exception){publish {state=state.copy(busy=false,error="Вставка не сохранена. Исходный текст остался в редакторе. Проверьте сохранение черновика и повторите.")}}
        }
    }
    fun insertSignature() {
        if(!canWrite()||state.writingOpen)return
        if(state.bodyComposing){state=state.copy(notice="Завершите ввод слова перед вставкой подписи.");return}
        recipients.clear();val snapshot=state
        state=state.copy(busy=true,error=null)
        saveTask?.let(main::removeCallbacks);saveTask=null
        worker.execute {
            try {
                val login=allowed(snapshot.owner)
                if(!snapshot.saved)save(snapshot)
                val config=JSONObject(NoticeCenter.state(context,login).getString("state","{}")!!)
                val inserted=NativeWritingTools.signature(snapshot.body,config)
                if(inserted.body==snapshot.body) {
                    val message=if(NativeWritingTools.signatureBlock(config).isEmpty())"Подпись для этого ящика не настроена. Добавьте её в настройках почтового клиента." else "Подпись уже добавлена в конце письма."
                    publish {state=state.copy(busy=false,saved=true,notice=message)}
                }else {
                    save(snapshot.copy(body=inserted.body))
                    publish {editVersion++;state=state.copy(body=inserted.body,bodyStart=inserted.cursor,bodyEnd=inserted.cursor,bodyComposing=false,insertion=state.insertion+1,busy=false,saved=true,notice="Подпись добавлена.")}
                }
            }catch(_: Exception){publish {state=state.copy(busy=false,error="Не удалось вставить подпись. Исходный текст сохранён в редакторе; проверьте лимит 100 000 символов и доступ к черновику.")}}
        }
    }
    fun recipientInput(field: String,value: String,caret: Int,composing: Boolean) {
        if(!foreground||AppLock.locked(context)||!state.open||state.busy||state.aiOpen||state.writingOpen||state.discardOpen||state.picking||state.remaining>0||state.status!=NativeDraft.EDITING||!state.accountValid||field !in listOf("to","cc","bcc")) { recipients.clear();return }
        val current=when(field){"to"->state.to;"cc"->state.cc;else->state.bcc}
        if(value!=current){recipients.clear();return}
        recipients.search(RecipientRequest(state.owner,state.draftId,field,value,state.to,state.cc,state.bcc),caret,composing)
    }
    fun chooseRecipient(email: String) {
        val request=recipients.state.request ?: return
        if(!foreground||AppLock.locked(context)||state.busy||state.picking||state.remaining>0||state.status!=NativeDraft.EDITING||!state.accountValid||
            request.owner!=state.owner||request.draft!=state.draftId||request.to!=state.to||request.cc!=state.cc||request.bcc!=state.bcc)return
        if(recipients.state.choices.none { it.email==email })return
        try {
            val updated=NativeRecipients.replace(request.value,email,NativeRecipients.used(state.to,state.cc,state.bcc,request.field))
            recipients.clear();edit(request.field,updated)
        } catch (_: Exception) { recipients.clear();state=state.copy(error="Не удалось добавить адрес: он уже выбран или список получателей слишком длинный.") }
    }
    private fun save(snapshot: ComposerState) {
        val prior = record ?: error("Draft unavailable")
        check(prior.owner == snapshot.owner && prior.status == NativeDraft.EDITING)
        val mail = JSONObject(prior.mail.toString()).put("to",snapshot.to).put("cc",snapshot.cc).put("bcc",snapshot.bcc)
            .put("subject",snapshot.subject).put("body",snapshot.body)
        val next = NativeDraft(prior.owner, NativeDraft.EDITING, mail, prior.revision + 1)
        synchronized(NativeDraftStore.gate) { NativeDraftStore(context, prior.owner).write(next) }
        record = next
    }
    fun flush(close: Boolean = false) {
        saveTask?.let(main::removeCallbacks); saveTask = null
        if(state.conflicted){if(close)state=ComposerState();return}
        if (!state.open || state.busy || state.remaining > 0) return
        val snapshot = state; val version = editVersion
        if (!snapshot.saved || close) {
            if (close) state = state.copy(busy=true)
            worker.execute {
                try {
                    if (!snapshot.saved) save(snapshot)
                    if (close && snapshot.status == NativeDraft.SENT) synchronized(NativeDraftStore.gate) { NativeDraftStore(context,snapshot.owner).clearSent(record ?: error("Draft unavailable"))
                        runCatching { NativeAttachments.cleanup(attachmentDirectory(record!!), JSONArray()) } }
                    if(close && snapshot.status==NativeDraft.SCHEDULED)synchronized(NativeDraftStore.gate){
                        val scheduled=record ?: error("Draft unavailable")
                        NativeDraftStore(context,snapshot.owner).clearScheduled(scheduled)
                        runCatching {NativeAttachments.cleanup(attachmentDirectory(scheduled),JSONArray())}
                    }
                    publish { if (version == editVersion) state = if(close) ComposerState() else state.copy(saved=true) }
                } catch (_: Exception) { publish { state = state.copy(busy=false, saved=snapshot.saved, error="Не удалось сохранить черновик. Оставьте редактор открытым и повторите сохранение.") } }
            }
        }
    }
    fun close() { if(state.serverDelete){cancelServerDelete();return};if(state.scheduleOpen){closeSchedule();return};if(state.aiOpen){closeAi();return};if(state.discardOpen){cancelDiscard();return};if(state.writingOpen){closeWritingTools();return}; recipients.clear(); if (state.picking) return; if (state.from.isBlank() && !state.busy) { state=ComposerState(); return }; if (state.remaining > 0) cancelSend(); if (!state.busy) flush(true) }
    fun requestServerDelete(){if(canWrite()&&!state.writingOpen&&state.serverReference)state=state.copy(serverDelete=true)}
    fun cancelServerDelete(){if(!state.busy)state=state.copy(serverDelete=false)}
    fun cancelServerTransfer(){if(state.serverWorking){serverGate.next();state=state.copy(notice="Останавливаем соединение. Уже загруженная копия может остаться на сервере.")}}
    fun serverCopy(remove:Boolean=false){
        if(!remove&&state.originalPending){state=state.copy(error="Сначала добавьте оригинал EML, затем сохраняйте серверную копию.");return}
        if(remove){if(!state.serverDelete||state.busy||!foreground||AppLock.locked(context)||!state.accountValid)return}
        else if(!canWrite()||state.writingOpen||state.bodyComposing)return
        val snapshot=state;val ticket=serverGate.next()
        recipients.clear();saveTask?.let(main::removeCallbacks);saveTask=null
        state=state.copy(busy=true,serverWorking=true,serverDelete=false,serverBytes=0,serverTotal=0,error=null,notice=null)
        worker.execute {
            var localSaved=snapshot.saved
            var connection:MailReader?=null
            try{
                val login=allowed(snapshot.owner)
                if(!snapshot.saved){save(snapshot);localSaved=true}
                val before=record ?: error("Draft unavailable")
                check(before.owner==snapshot.owner&&before.mail.getString("id")==snapshot.draftId&&before.status==NativeDraft.EDITING)
                check(serverGate.current(ticket))
                val reference=if(remove)before.mail.optJSONObject("serverDraft") ?: error("No confirmed server reference") else null
                if(remove)NativeServerDraftPlan.validate(reference,before.mail.getString("id"))
                val revision=if(remove)"" else ServerDrafts.revision(before.mail)
                val message=if(remove)null else MailSender.draft(login,before.mail,attachmentDirectory(before))
                val prepared=synchronized(NativeDraftStore.gate){
                    val store=NativeDraftStore(context,snapshot.owner)
                    val current=store.read() ?: error("Draft unavailable")
                    check(current.revision==before.revision&&current.mail.getString("id")==snapshot.draftId)
                    if(remove)current else NativeServerDraftPlan.attempt(current).also {store.write(it)}
                }
                record=prepared
                check(serverGate.current(ticket));allowed(snapshot.owner)
                connection=MailReader();serverGate.attach(ticket,connection)
                check(serverGate.current(ticket))
                connection.connect(login.host,login.port,login.user,login.password)
                allowed(snapshot.owner);check(serverGate.current(ticket))
                val result=if(remove){
                    ServerDrafts.removeKnown(connection.draftStore(),reference,prepared.mail.getString("id"))
                    NativeServerDraftPlan.removed(prepared,reference!!)
                }else {
                    val saved=ServerDrafts.save(connection.draftStore(),message!!,prepared.mail.getString("id"),revision){_,bytes,total->
                        if(!serverGate.current(ticket))throw java.io.IOException("Cancelled")
                        try{allowed(snapshot.owner)}catch(_:Exception){throw java.io.IOException("Account unavailable")}
                        publish {if(serverGate.current(ticket))state=state.copy(serverBytes=bytes,serverTotal=total)}
                    }
                    NativeServerDraftPlan.confirmed(prepared,saved,revision)
                }
                // Persist confirmed metadata even if pause cancelled the UI: server effects cannot be rolled back.
                synchronized(NativeDraftStore.gate){NativeDraftStore(context,snapshot.owner).write(result)}
                record=result
                publish {state=state.copy(busy=false,saved=true,serverWorking=false,serverBackup=NativeServerDraftPlan.state(result),serverReference=result.mail.optJSONObject("serverDraft")!=null,notice=if(remove)"Подтверждённая серверная копия удалена. Черновик на телефоне сохранён." else "Копия сохранена в серверных «Черновиках». Последующие правки пока сохраняются на телефоне; загрузите их отдельно.")}
            }catch(_:Exception){
                val recovered=runCatching {synchronized(NativeDraftStore.gate){NativeDraftStore(context,snapshot.owner).read()}}.getOrNull()
                val conflict=recovered==null||record==null||recovered.revision!=record?.revision||recovered.mail.optString("id")!=snapshot.draftId
                if(!conflict)record=recovered
                publish {state=state.copy(busy=false,saved=localSaved,conflicted=conflict,serverWorking=false,serverBackup=if(recovered==null)"uncertain" else NativeServerDraftPlan.state(recovered),serverReference=recovered?.mail?.optJSONObject("serverDraft")!=null,error=if(conflict)"Состояние черновика изменилось или не читается. Текст в этом окне оставлен для копирования; закройте и заново откройте черновик для сверки. Отправка заблокирована." else if(!localSaved)"Сохранение правок на телефоне не подтверждено. Текст остался в редакторе; серверная загрузка не началась." else if(remove)"Удаление не подтверждено. Серверная копия могла сохраниться; локальный текст и файлы не удалены." else "Сервер не подтвердил сохранение. Копия могла загрузиться; локальный черновик сохранён. Повторная явная загрузка сверит ту же ревизию.")}
            }finally{
                connection?.let {serverGate.detach(ticket,it);runCatching {it.close()}}
            }
        }
    }
    private fun scheduleQueue(owner:String,at:Long,warning:(String)->Unit={})=object:NativeScheduleTransaction.Queue {
        override fun contains(source:String)=ScheduledMail.protects(context,allowed(owner),source)
        override fun create(draft:NativeDraft){
            val login=allowed(owner)
            warning(ScheduledMail.createFrom(context,login,draft.mail,at,attachmentDirectory(draft),false).optString("warning"))
        }
    }
    fun openSchedule(){
        if(!canWrite()||state.writingOpen||state.bodyComposing)return
        if(state.originalPending){state=state.copy(error="Сначала добавьте оригинал EML.");return}
        recipients.clear()
        val next=java.time.ZonedDateTime.now(java.time.ZoneId.of("Europe/Moscow")).plusHours(1)
        state=state.copy(scheduleOpen=true,scheduleDay=next.toLocalDate().toString(),scheduleTime=next.toLocalTime().withSecond(0).withNano(0).toString(),error=null)
    }
    fun closeSchedule(){if(!state.busy)state=state.copy(scheduleOpen=false)}
    fun scheduleDate(day:String,time:String){if(state.scheduleOpen&&!state.busy&&day.length<=10&&time.length<=5)state=state.copy(scheduleDay=day,scheduleTime=time,error=null)}
    fun schedule(){
        if(!foreground||AppLock.locked(context)||!state.scheduleOpen||state.busy||!state.accountValid||state.status!=NativeDraft.EDITING)return
        val snapshot=state
        val at=try{NativeScheduleTime.parse(snapshot.scheduleDay,snapshot.scheduleTime,System.currentTimeMillis())}catch(_:Exception){state=state.copy(error="Выберите будущую дату и время по Москве.");return}
        saveTask?.let(main::removeCallbacks);saveTask=null;state=state.copy(busy=true,error=null)
        worker.execute {
            var draftSaved=snapshot.saved
            var scheduleWarning=""
            try{
            allowed(snapshot.owner)
            if(!snapshot.saved){save(snapshot);draftSaved=true}
            val result=synchronized(NativeDraftStore.gate){
                val store=NativeDraftStore(context,snapshot.owner)
                val current=store.read() ?: error("Draft unavailable")
                check(current.owner==snapshot.owner&&current.mail.getString("id")==snapshot.draftId&&current.revision==record?.revision)
                NativeScheduleTransaction.run(current,{store.write(it)},scheduleQueue(snapshot.owner,at){scheduleWarning=it})
            }
            record=result
            publish {state=state.copy(busy=false,saved=true,status=result.status,scheduleOpen=false,notice=scheduleWarning.ifBlank {"Письмо сохранено в очереди. Отправка зависит от сети и фоновых ограничений Android; проверьте статус в «По расписанию»."})}
        }catch(_:Exception){
            val recovered=runCatching {synchronized(NativeDraftStore.gate){NativeDraftStore(context,snapshot.owner).read()}}.getOrNull()
            val conflict=!NativeDraftRecovery.sameContent(record,recovered)
            if(!conflict)record=recovered
            publish {state=state.copy(busy=false,saved=draftSaved,conflicted=conflict,status=if(conflict)NativeDraft.SCHEDULING else recovered!!.status,scheduleOpen=false,error=if(conflict)"Активный черновик изменился или не читается. Закройте и откройте его через список черновиков; отправка из этого окна заблокирована." else if(recovered?.status==NativeDraft.EDITING)"Планирование не выполнено. Проверьте получателей, вложения, место на телефоне и сохранение черновика." else "Планирование не подтверждено. Проверьте очередь и заново откройте черновик для сверки; повторная отправка заблокирована до сверки.")}
        }}
    }
    fun send() {
        if(state.originalPending){state=state.copy(error="Сначала добавьте оригинал EML.");return}
        if (!state.open || state.conflicted || state.busy || state.serverDelete || state.scheduleOpen || state.aiOpen || state.discardOpen || state.picking || state.remaining > 0 || state.status != NativeDraft.EDITING || !state.accountValid) return
        closeWritingTools()
        recipients.clear()
        val snapshot = state; val version = editVersion
        state = state.copy(busy=true, error=null)
        saveTask?.let(main::removeCallbacks); saveTask = null
        worker.execute {
            try {
                val login = allowed(snapshot.owner)
                if (!snapshot.saved) save(snapshot)
                // Validate before starting the cancellation interval. No network or SMTP here.
                val draft = record ?: error("Draft unavailable")
                MailSender.message(javax.mail.Session.getInstance(java.util.Properties()), login, draft.mail, attachmentDirectory(draft))
                publish {
                    if (version == editVersion && foreground && !AppLock.locked(context)) {
                        state = state.copy(busy=false, saved=true, remaining=10)
                        deadline = SystemClock.elapsedRealtime() + 10000
                        tick()
                    } else state = state.copy(busy=false, saved=true, notice="Отправка отменена. Черновик сохранён.")
                }
            } catch (_: Exception) { publish { state = state.copy(busy=false, error="Проверьте адреса получателей и сохранение черновика. Отправка не началась.") } }
        }
    }
    private fun tick() {
        if (!foreground || AppLock.locked(context)) { cancelSend(); return }
        val left = ((deadline-SystemClock.elapsedRealtime()+999)/1000).toInt().coerceAtLeast(0)
        if (left == 0) { state = state.copy(remaining=0,busy=true); deliver(state.owner); return }
        state = state.copy(remaining=left)
        delayTask = Runnable { tick() }.also { main.postDelayed(it, 200) }
    }
    fun cancelSend() {
        delayTask?.let(main::removeCallbacks); delayTask=null
        if (state.remaining > 0) state=state.copy(remaining=0,notice="Отправка отменена. Черновик сохранён.")
    }
    private fun deliver(owner: String) {
        worker.execute {
            var warning: String? = null
            try {
                val login = allowed(owner)
                var message: javax.mail.internet.MimeMessage? = null
                val result = synchronized(NativeDraftStore.gate) {
                    val store = NativeDraftStore(context, owner)
                    NativeSendTransaction.run(record ?: error("Draft unavailable"), { store.write(it) }, {
                        message = MailSender.send(login, record!!.mail, attachmentDirectory(record!!), null) { allowed(owner) }
                    })
                }
                record = result
                // A failed Sent copy must never turn SMTP success into a retryable failure.
                if (result.status == NativeDraft.SENT) try {
                    MailReader().use { reader -> reader.connect(login.host,login.port,login.user,login.password); reader.saveSent(message!!)
                        result.mail.optJSONObject("serverDraft")?.let {ref->
                            NativeServerDraftPlan.validate(ref,result.mail.getString("id"))
                            ServerDrafts.removeKnown(reader.draftStore(),ref,result.mail.getString("id"))
                        }
                        check(!result.mail.optBoolean("serverDraftUnconfirmed"))
                    }
                } catch (_: Exception) { warning="Письмо отправлено, но сохранение копии или удаление серверного черновика не подтверждено. Не отправляйте повторно." }
                publish { state=state.copy(busy=false,saved=true,status=result.status,notice=warning) }
            } catch (_: Exception) {
                // Reload the durable marker: even failure to persist SMTP success must block repeats.
                val recovered = runCatching { synchronized(NativeDraftStore.gate) { NativeDraftStore(context,owner).read() } }.getOrNull()
                val conflict=!NativeDraftRecovery.sameContent(record,recovered)
                if(!conflict)record = recovered
                publish { state=state.copy(busy=false,conflicted=conflict,status=if(conflict)NativeDraft.UNCERTAIN else recovered!!.status,
                    error=if(conflict)"Активный черновик изменился или не читается. Не повторяйте отправку из этого окна; откройте нужный черновик через список и проверьте результат." else if(recovered?.status == NativeDraft.EDITING) "Письмо не отправлено. Проверьте подключение и повторите." else "Результат отправки не подтверждён. Проверьте «Отправленные» перед любыми повторными действиями.") }
            }
        }
    }
    private fun attachmentDirectory(draft: NativeDraft) = NativeAttachments.directory(File(context.filesDir,"native-attachments"),draft)
    private fun attachments(files: JSONArray) = (0 until files.length()).map {
        files.getJSONObject(it).let { f -> ComposeAttachment(f.getString("token"),f.getString("name"),f.getLong("size")) }
    }
    fun beginPick(launch: () -> Unit) {
        if (!canWrite()||state.writingOpen) return
        if (state.files.size >= 10) { state=state.copy(error="Можно прикрепить до 10 файлов."); return }
        recipients.clear()
        saveTask?.let(main::removeCallbacks);saveTask=null
        val snapshot=state
        state=state.copy(busy=true,picking=true,error=null)
        worker.execute {
            try {
                allowed(snapshot.owner)
                if (!snapshot.saved) save(snapshot)
                publish {
                    state=state.copy(busy=false,saved=true)
                    if (foreground && !AppLock.locked(context)) {
                        pick=Pick(snapshot.owner,snapshot.draftId)
                        launch()
                    } else state=state.copy(picking=false,notice="Выбор файлов отменён. Черновик сохранён.")
                }
            } catch (_: Exception) { publish { state=state.copy(busy=false,picking=false,error="Не удалось сохранить черновик. Повторите добавление файла после сохранения.") } }
        }
    }
    fun pickerFailed() { pick=null; state=state.copy(picking=false,error="Не удалось открыть выбор файлов. Повторите попытку.") }
    fun receiveFiles(uris: List<Uri>) {
        val request=pick; pick=null
        state=state.copy(picking=false)
        if (uris.isEmpty()) return
        if (request==null || !state.open || request.owner!=state.owner || request.id!=state.draftId) {
            state=state.copy(error="Черновик изменился. Выберите файлы заново.");return
        }
        if (uris.size+state.files.size>10 || uris.any { it.scheme!="content" }) {
            state=state.copy(error="Выберите до 10 файлов через приложение «Файлы».");return
        }
        val snapshot=state
        importCancelled.set(false)
        state=state.copy(busy=true,importing=true,transferBytes=0,transferName="",error=null)
        worker.execute {
            val created=mutableListOf<File>()
            var directory: File? = null
            try {
                // The picker callback may arrive before onResume; import is allowed only for its original account/draft.
                check(CredentialVault(context).load()?.let { NoticeCenter.scope(it) }==request.owner)
                if (!snapshot.saved) save(snapshot)
                val current=record ?: error("Draft unavailable")
                check(current.owner==request.owner && current.mail.getString("id")==request.id && current.status==NativeDraft.EDITING)
                val target=attachmentDirectory(current);directory=target
                val added=JSONArray()
                for (uri in uris) {
                    check(!importCancelled.get())
                    var name="Вложение";var size=-1L
                    runCatching { context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE),null,null,null)?.use { cursor ->
                        if(cursor.moveToFirst()) { name=cursor.getString(0) ?: "Вложение";if(!cursor.isNull(1))size=cursor.getLong(1) }
                    } }
                    check(size<=AttachmentFiles.MAX_BYTES)
                    val display=AttachmentFiles.safeName(name)
                    var last=0L
                    val file=context.contentResolver.openInputStream(uri)?.use { input ->
                        AttachmentFiles.copy(input,target,display,AttachmentFiles.MAX_BYTES) { bytes ->
                            check(!importCancelled.get())
                            val now=SystemClock.elapsedRealtime()
                            if(now-last>=100 || bytes==size) { last=now;publish { state=state.copy(transferName=display,transferBytes=bytes) } }
                        }
                    } ?: error("File unavailable")
                    created.add(file)
                    added.put(JSONObject().put("token",file.name).put("name",display).put("size",file.length()))
                }
                check(!importCancelled.get())
                check(CredentialVault(context).load()?.let { NoticeCenter.scope(it) }==request.owner)
                val files=NativeAttachments.append(current.mail.getJSONArray("attachments"),added)
                val next=NativeDraft(current.owner,NativeDraft.EDITING,JSONObject(current.mail.toString()).put("attachments",files),current.revision+1)
                synchronized(NativeDraftStore.gate) { NativeDraftStore(context,current.owner).write(next) }
                record=next
                publish { state=state.copy(busy=false,importing=false,saved=true,files=attachments(files),notice="Файлы добавлены в черновик.") }
            } catch (_: Exception) {
                // Never delete a file that an AtomicFile commit might already reference.
                runCatching { synchronized(NativeDraftStore.gate) {
                    val committed=NativeDraftStore(context,request.owner).find(request.id)
                    val keep=committed?.mail?.getJSONArray("attachments") ?: JSONArray()
                    if(directory!=null) {
                        val tokens=(0 until keep.length()).map { keep.getJSONObject(it).getString("token") }.toSet()
                        created.filter { it.name !in tokens }.forEach { it.delete() }
                    }
                } }
                publish { state=state.copy(busy=false,importing=false,error=if(importCancelled.get()) "Добавление отменено. Черновик сохранён." else "Не удалось добавить файлы. Максимум — 10 файлов по 100 МБ; проверьте свободное место и повторите выбор.") }
            }
        }
    }
    /** Copies all selected original files, then atomically adds them to the same draft. */
    fun originalFiles() {
        if(!canWrite()||state.writingOpen||state.originalFiles==0)return
        val snapshot=state;val eml=snapshot.forwardEml
        if(state.files.size+state.originalFiles>10){state=state.copy(error="В черновике может быть не больше 10 файлов. Уберите лишние файлы перед добавлением исходных.");return}
        saveTask?.let(main::removeCallbacks);saveTask=null;recipients.clear()
        importCancelled.set(false);val ticket=serverGate.next()
        state=state.copy(busy=true,importing=true,transferName="",transferBytes=0,error=null,notice=null)
        worker.execute {
            val created=mutableListOf<File>();var directory:File?=null;var connection:MailReader?=null
            fun alive(){check(!closed&&foreground&&!importCancelled.get()&&serverGate.current(ticket));allowed(snapshot.owner)}
            try {
                alive();if(!snapshot.saved)save(snapshot)
                val current=record ?: error("Draft unavailable")
                check(current.owner==snapshot.owner&&current.mail.getString("id")==snapshot.draftId&&current.status==NativeDraft.EDITING)
                val source=if(eml)NativeServerDraftCopyPlan.ref(current.mail.getJSONObject("forwardSource")) else NativeForwardFiles.source(current.mail.getJSONObject("forwardSource"))
                val folder=source.getString("folder");val uid=source.getString("uid");val validity=source.getString("validity")
                val login=allowed(snapshot.owner);val reader=MailReader();connection=reader;serverGate.attach(ticket,reader);alive()
                reader.connect(login.host,login.port,login.user,login.password)
                if(!eml)NativeForwardFiles.verify(current,reader.forwardAttachments(folder,uid,validity))
                val target=attachmentDirectory(current);directory=target
                val files=if(eml)JSONArray() else source.getJSONArray("attachments");val added=JSONArray();var completed=0L;var last=0L
                if(eml){
                    val result=reader.exportMessage(folder,uid,validity,target,true,
                        {!foreground||closed||importCancelled.get()||!serverGate.current(ticket)||AppLock.locked(context)}){bytes->
                        val now=SystemClock.elapsedRealtime();if(now-last>=120){last=now;publish {if(state.draftId==snapshot.draftId&&state.importing&&serverGate.current(ticket))state=state.copy(transferName="Оригинал письма.eml",transferBytes=bytes)}}
                    }
                    val file=File(result.getString("file"));created.add(file)
                    check(file.canonicalFile.parentFile==target.canonicalFile&&file.isFile&&file.length()>0&&file.length()<=AttachmentFiles.MAX_BYTES)
                    java.io.RandomAccessFile(file,"rw").use {it.fd.sync()}
                    added.put(JSONObject().put("token",file.name).put("name",result.getString("name")).put("size",file.length()))
                }
                for(i in 0 until files.length()){
                    alive();val entry=files.getJSONObject(i);val name=entry.getString("name");var bytesSeen=0L
                    val result=reader.attachment(folder,uid,validity,entry.getString("path"),target,
                        {!foreground||closed||importCancelled.get()||!serverGate.current(ticket)||AppLock.locked(context)}) {bytes->
                        bytesSeen=maxOf(bytesSeen,bytes);val now=SystemClock.elapsedRealtime()
                        if(now-last>=120){last=now;val display=completed+bytesSeen;publish {if(state.draftId==snapshot.draftId&&state.importing&&serverGate.current(ticket))state=state.copy(transferName=name,transferBytes=display)}}
                    }
                    val file=File(result.getString("file"));created.add(file)
                    check(file.canonicalFile.parentFile==target.canonicalFile&&file.length()<=AttachmentFiles.MAX_BYTES)
                    java.io.RandomAccessFile(file,"rw").use {it.fd.sync()}
                    added.put(JSONObject().put("token",file.name).put("name",name).put("size",file.length()));completed+=bytesSeen
                }
                alive();if(!eml)NativeForwardFiles.verify(current,reader.forwardAttachments(folder,uid,validity))
                val next=if(eml)NativeForwardOriginal.complete(current,added.getJSONObject(0)) else NativeForwardFiles.complete(current,added)
                synchronized(NativeDraftStore.gate){alive();NativeDraftStore(context,current.owner).write(next)}
                record=next
                publish {if(state.draftId==snapshot.draftId)state=state.copy(busy=false,importing=false,saved=true,originalFiles=0,originalPending=false,forwardFilesAdded=!eml,files=attachments(next.mail.getJSONArray("attachments")),notice=if(eml)"Оригинал EML добавлен. Проверьте его содержимое и укажите получателя." else "Вложения исходного письма добавлены. Проверьте файлы и укажите получателя.")}
            }catch(_:Exception){
                runCatching {synchronized(NativeDraftStore.gate){
                    val stored=NativeDraftStore(context,snapshot.owner).find(snapshot.draftId)
                    if(stored!=null&&(if(eml)runCatching {ForwardOriginal.ready(stored.mail)}.getOrDefault(false) else stored.mail.optBoolean("forwardFilesAdded")))record=stored
                    val keep=stored?.mail?.getJSONArray("attachments") ?: JSONArray()
                    val tokens=(0 until keep.length()).map {keep.getJSONObject(it).getString("token")}.toSet()
                    if(directory!=null)created.filter {it.name !in tokens}.forEach {it.delete()}
                }}
                val saved=record?.takeIf {it.mail.optString("id")==snapshot.draftId}
                val done=saved!=null&&(if(eml)runCatching {ForwardOriginal.ready(saved.mail)}.getOrDefault(false) else saved.mail.optBoolean("forwardFilesAdded"))
                publish {if(state.draftId==snapshot.draftId)state=state.copy(busy=false,importing=false,saved=state.saved||snapshot.saved||saved!=null,
                    files=if(done)attachments(saved!!.mail.getJSONArray("attachments")) else state.files,forwardFilesAdded=done&&!eml,originalPending=eml&&!done,originalFiles=if(done)0 else state.originalFiles,
                    error=if(done)null else "Вложения не добавлены: операция остановлена, исходник изменился или файл недоступен. Проверьте сеть, место и исходное письмо; текст черновика сохранён.",notice=if(done)"Файлы уже сохранены в черновике." else null)}
            }finally{connection?.let {serverGate.detach(ticket,it);runCatching {it.close()}}}
        }
    }
    fun cancelImport() { importCancelled.set(true); if(state.importing)serverGate.next() }
    fun removeFile(token: String) {
        if (!canWrite()||state.writingOpen) return
        recipients.clear()
        val snapshot=state;state=state.copy(busy=true,error=null)
        worker.execute {
            try {
                allowed(snapshot.owner)
                if(!snapshot.saved)save(snapshot)
                val current=record ?: error("Draft unavailable")
                val files=NativeAttachments.remove(current.mail.getJSONArray("attachments"),token)
                val next=NativeDraft(current.owner,NativeDraft.EDITING,JSONObject(current.mail.toString()).put("attachments",files),current.revision+1)
                synchronized(NativeDraftStore.gate) {
                    NativeDraftStore(context,current.owner).write(next)
                    record=next
                    runCatching { MailSender.file(attachmentDirectory(current),token).delete() }
                }
                publish { state=state.copy(busy=false,saved=true,files=attachments(files),originalPending=state.forwardEml&&!ForwardOriginal.ready(next.mail),originalFiles=if(state.forwardEml&&!ForwardOriginal.ready(next.mail))1 else state.originalFiles,notice="Файл убран из письма.") }
            } catch (_: Exception) { publish { state=state.copy(busy=false,error="Не удалось убрать файл. Черновик сохранён.") } }
        }
    }
    fun allowRetryAfterCheck() {
        if (state.conflicted || state.busy || state.status !in listOf(NativeDraft.DISPATCHING, NativeDraft.UNCERTAIN)) return
        val expectedId=state.draftId
        val owner=state.owner; state=state.copy(busy=true)
        worker.execute {
            try {
                allowed(owner)
                val draft = synchronized(NativeDraftStore.gate) {
                    val store=NativeDraftStore(context,owner)
                    val current=store.read() ?: error("Draft unavailable")
                    check(current.mail.getString("id")==expectedId&&current.revision==record?.revision)
                    check(current.status in listOf(NativeDraft.DISPATCHING,NativeDraft.UNCERTAIN))
                    current.status(NativeDraft.EDITING).also { store.write(it) }
                }
                record=draft
                publish { state=state.copy(busy=false,status=NativeDraft.EDITING,error=null,notice="Повтор разрешён после вашей проверки. Для отправки нажмите «Отправить».") }
            } catch (_: Exception) { publish { state=state.copy(busy=false,error="Не удалось разрешить повтор. Черновик сохранён.") } }
        }
    }
    override fun onCleared() {
        serverGate.next();serverCloser.shutdown()
        aiSerial.incrementAndGet();aiReplyContext=""
        writingSerial.incrementAndGet()
        recipients.close()
        importCancelled.set(true)
        foreground=false; closed=true
        saveTask?.let(main::removeCallbacks); delayTask?.let(main::removeCallbacks)
        worker.shutdown() // Do not interrupt a started SMTP operation; it must persist its final result.
    }
}
