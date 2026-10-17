package ru.local.spbumailpilot
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

internal object NativeLegacyDraftImport {
    private fun current(context:Context,login:SavedAccounts.Login,id:String):JSONObject=synchronized(ReplyReminderCenter::class.java){
        val root=JSONObject(NoticeCenter.state(context,login).getString("state","{}")!!)
        val drafts=root.optJSONArray("drafts") ?: JSONArray()
        val matches=(0 until drafts.length()).mapNotNull {drafts.optJSONObject(it)}.filter {it.optString("id")==id}
        check(matches.size==1);JSONObject(matches.single().toString())
    }
    fun run(context:Context,owner:String,login:SavedAccounts.Login,source:JSONObject,checkAlive:()->Unit,progress:(Long)->Unit):String {
        check(NoticeCenter.scope(login)==owner)
        val id=NativeLegacyDraftPlan.sourceId(source)
        checkAlive();check(CredentialVault(context).load()?.let {NoticeCenter.scope(it)}==owner);NativeLegacyDraftPlan.unchanged(source,current(context,login,id))
        check(!ScheduledMail.protects(context,login,id))
        val native=NativeLegacyDraftPlan.copy(owner,source,UUID.randomUUID().toString())
        val target=NativeAttachments.directory(File(context.filesDir,"native-attachments"),native)
        val created=mutableListOf<File>();var committed=false
        try{
            // Check an earlier committed copy before reading or duplicating files.
            synchronized(NativeDraftStore.gate){
                checkAlive();check(CredentialVault(context).load()?.let {NoticeCenter.scope(it)}==owner);val store=NativeDraftStore(context,owner)
                val stored=store.list()
                NativeLegacyDraftPlan.existing(stored,source)?.let {existing->
                    ScheduledFiles.verify(NativeAttachments.directory(File(context.filesDir,"native-attachments"),existing),existing.mail.getJSONArray("attachments"))
                    synchronized(ReplyReminderCenter::class.java){
                        checkAlive();NativeLegacyDraftPlan.unchanged(source,current(context,login,id));check(!ScheduledMail.protects(context,login,id))
                        store.select(store.read(),existing)
                    }
                    return existing.mail.getString("id")
                }
                check(stored.size<NativeDraftBook.LIMIT)
            }
            val copies=NativeLegacyDraftFiles.copy(File(context.filesDir,"outgoing/$owner"),target,NativeLegacyDraftPlan.files(source),created,{checkAlive()},{bytes->progress(bytes)})
            synchronized(NativeDraftStore.gate){
                checkAlive();check(CredentialVault(context).load()?.let {NoticeCenter.scope(it)}==owner);val store=NativeDraftStore(context,owner)
                synchronized(ReplyReminderCenter::class.java){
                    NativeLegacyDraftPlan.unchanged(source,current(context,login,id));check(!ScheduledMail.protects(context,login,id))
                    NativeLegacyDraftPlan.existing(store.list(),source)?.let {existing->
                        ScheduledFiles.verify(NativeAttachments.directory(File(context.filesDir,"native-attachments"),existing),existing.mail.getJSONArray("attachments"))
                        store.select(store.read(),existing);return existing.mail.getString("id")
                    }
                    store.create(NativeDraft(owner,NativeDraft.EDITING,JSONObject(native.mail.toString()).put("attachments",copies)))
                    committed=true
                }
            }
            return native.mail.getString("id")
        }finally{
            if(!committed)synchronized(NativeDraftStore.gate){
                // An AtomicFile failure can be after commit. Never delete referenced copies.
                runCatching {
                    val stored=NativeDraftStore(context,owner).find(native.mail.getString("id"))
                    val files=stored?.mail?.getJSONArray("attachments") ?: JSONArray()
                    val keep=(0 until files.length()).map {files.getJSONObject(it).getString("token")}.toSet()
                    created.filter {it.name !in keep}.forEach {it.delete()}
                }
            }
        }
    }
}
