package ru.local.spbumailpilot

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

internal data class RecipientChoice(val name: String,val email: String,val origin: String)
internal data class RecipientRequest(val owner: String,val draft: String,val field: String,val value: String,val to: String,val cc: String,val bcc: String)
internal data class RecipientSearchState(val request: RecipientRequest?=null,val choices: List<RecipientChoice> = emptyList(),val loading: Boolean=false,val message: String?=null,val retry: Boolean=false)

/** Account-bound, debounced directory search. Nothing is persisted or sent as mail. */
internal class NativeRecipientSearch(private val context: Context) {
    var state by mutableStateOf(RecipientSearchState());private set
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    private val localWorker=Executors.newSingleThreadExecutor()
    private val epoch=NativeRecipientEpoch()
    private var timer: Runnable?=null
    private val directory=UniversityDirectory()
    @Volatile private var closed=false
    fun clear(session: Boolean=false) {
        epoch.clear();timer?.let(main::removeCallbacks);timer=null;state=RecipientSearchState()
        if(session&&!closed)worker.execute { directory.close() }
    }
    fun search(request: RecipientRequest,caret: Int,composing: Boolean) {
        val query=NativeRecipients.query(request.value,caret,composing)
        if(query.isEmpty()){clear();return}
        if(state.request==request)return
        clear();val ticket=epoch.begin(request.owner,request.draft,request.field,request.value);state=RecipientSearchState(request=request,loading=true)
        timer=Runnable {
            timer=null
            localWorker.execute {
                if(closed||!epoch.current(ticket))return@execute
                var local=JSONArray()
                try {
                    check(!AppLock.locked(context))
                    val login=CredentialVault(context).load() ?: error("Account unavailable")
                    check(NoticeCenter.scope(login)==request.owner)
                    val excluded=NativeRecipients.used(request.to,request.cc,request.bcc,request.field)
                    val data=JSONObject(NoticeCenter.state(context,login).getString("state","{}")!!)
                    for(key in listOf("contacts","phoneContacts","recipientBook")) {
                        val entries=data.optJSONArray(key) ?: continue
                        for(index in 0 until minOf(entries.length(),500))entries.optJSONObject(index)?.let { local.put(JSONObject(it.toString()).put("origin","local")) }
                    }
                    local=NativeRecipients.matches(query,local,excluded)
                    val localRows=choices(local)
                    post(ticket){state=state.copy(choices=localRows)}
                    if(!UniversityDirectory.supported(login)) {
                        post(ticket){state=state.copy(loading=false,message=if(localRows.isEmpty())"Совпадений в контактах нет. Можно ввести полный адрес вручную." else null)}
                        return@execute
                    }
                    worker.execute remote@{
                        if(closed||!epoch.current(ticket)||AppLock.locked(context))return@remote
                        try {
                            val fresh=CredentialVault(context).load() ?: error("Account unavailable")
                            check(NoticeCenter.scope(fresh)==request.owner)
                            val remote=directory.search(fresh,query)
                            check(CredentialVault(context).load()?.let { NoticeCenter.scope(it) }==request.owner)
                            for(index in 0 until remote.length())local.put(JSONObject(remote.getJSONObject(index).toString()).put("origin","directory"))
                            val found=choices(NativeRecipients.matches(query,local,excluded))
                            post(ticket){state=state.copy(loading=false,choices=found,message=if(found.isEmpty())"Совпадений нет. Можно ввести полный адрес вручную." else null)}
                        } catch (_: Exception) {
                            post(ticket){state=state.copy(loading=false,message="Справочник недоступен. Проверьте подключение или введите адрес вручную.",retry=true)}
                        }
                    }
                } catch (_: Exception) {
                    post(ticket){state=state.copy(loading=false,message="Справочник недоступен. Проверьте подключение или введите адрес вручную.",retry=true)}
                }
            }
        }.also { main.postDelayed(it,300) }
    }
    fun retry(){val request=state.request ?: return;clear();search(request,request.value.length,false)}
    private fun choices(array: JSONArray)=(0 until array.length()).map { array.getJSONObject(it).let { p -> RecipientChoice(p.optString("sender"),p.getString("email"),p.optString("origin")) } }
    private fun post(ticket: NativeRecipientEpoch.Ticket,action: ()->Unit){main.post { if(!closed&&epoch.current(ticket))action() } }
    fun close(){clear();closed=true;worker.execute { directory.close() };worker.shutdown();localWorker.shutdown()}
}
