package ru.local.spbumailpilot
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream

/** The recoverable queue snapshot is acknowledged only after the native encrypted commit. */
internal object NativeScheduledRestore {
    fun run(context:Context,owner:String,login:SavedAccounts.Login,id:String):String {
        check(NoticeCenter.scope(login)==owner)
        synchronized(NativeDraftStore.gate){
            val store=NativeDraftStore(context,owner)
            val source=ScheduledMail.returned(context,login,id).getJSONObject("draft")
            val returned=NativeReturnedDraft.convert(owner,source)
            val current=store.find(returned.mail.getString("id"))
            if(current!=null){
                check(current.status==NativeDraft.EDITING&&current.mail.getString("id")==returned.mail.getString("id")) {"Another native draft exists"}
                ScheduledFiles.verify(NativeAttachments.directory(File(context.filesDir,"native-attachments"),current),current.mail.getJSONArray("attachments"))
                store.select(store.read(),current)
                ScheduledMail.acknowledgeReturned(context,login,id)
                return current.mail.getString("id")
            }
            val target=NativeAttachments.directory(File(context.filesDir,"native-attachments"),returned)
            val files=JSONArray();val created=mutableListOf<File>();var committed=false
            try{
                val entries=returned.mail.getJSONArray("attachments")
                for(i in 0 until entries.length()){
                    val entry=entries.getJSONObject(i)
                    val original=MailSender.file(File(context.filesDir,"outgoing/$owner"),entry.getString("token"))
                    check(original.length()==entry.getLong("size"))
                    val copy=FileInputStream(original).use {AttachmentFiles.copy(it,target,entry.getString("name"),AttachmentFiles.MAX_BYTES)}
                    created.add(copy)
                    java.io.RandomAccessFile(copy,"rw").use {it.fd.sync()}
                    files.put(JSONObject(entry.toString()).put("token",copy.name).put("size",copy.length()))
                }
                store.create(NativeDraft(owner,NativeDraft.EDITING,JSONObject(returned.mail.toString()).put("attachments",files)))
                committed=true
                ScheduledMail.acknowledgeReturned(context,login,id)
                return returned.mail.getString("id")
            }finally{if(!committed)created.forEach {it.delete()}}
        }
    }
}
