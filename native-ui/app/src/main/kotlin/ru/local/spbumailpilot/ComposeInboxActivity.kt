@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package ru.local.spbumailpilot

import android.content.Intent
import android.os.Bundle
import android.net.Uri
import android.content.ClipData
import android.view.WindowManager
import java.io.File
import androidx.activity.enableEdgeToEdge
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

/** Opt-in development entry. Existing app storage/identity and Java read-only mail protocol are reused. */
class ComposeInboxActivity : ComponentActivity(),AppLockAware {
    private val files by lazy { ViewModelProvider(this)[NativeFilesModel::class.java] }
    private val accounts by lazy { ViewModelProvider(this)[NativeAccountsModel::class.java] }
    private val serverDrafts by lazy { ViewModelProvider(this)[NativeServerDraftsModel::class.java] }
    private val drafts by lazy { ViewModelProvider(this)[NativeDraftsModel::class.java] }
    private val scheduled by lazy { ViewModelProvider(this)[NativeScheduledModel::class.java] }
    private val digest by lazy { ViewModelProvider(this)[NativeDigestModel::class.java] }
    private val aiSettings by lazy { ViewModelProvider(this)[NativeAiSettingsModel::class.java] }
    private val settings by lazy { ViewModelProvider(this)[NativeSettingsModel::class.java] }
    private val calls by lazy { ViewModelProvider(this)[NativeCallsModel::class.java] }
    private val relatedSearch by lazy { ViewModelProvider(this)[NativeRelatedSearchModel::class.java] }
    private val exports by lazy { ViewModelProvider(this)[NativeExportModel::class.java] }
    private lateinit var exportPicker:androidx.activity.result.ActivityResultLauncher<Uri?>
    private val navigation by lazy { ViewModelProvider(this)[NativeNavigationModel::class.java] }
    private val writingSettings by lazy { ViewModelProvider(this)[NativeWritingSettingsModel::class.java] }
    private val inbox by lazy { ViewModelProvider(this)[NativeInboxModel::class.java] }
    private val contacts by lazy { ViewModelProvider(this)[NativeContactsModel::class.java] }
    private val calendar by lazy { ViewModelProvider(this)[NativeCalendarModel::class.java] }
    private val extras by lazy { ViewModelProvider(this)[NativeMailExtrasModel::class.java] }
    private val categories by lazy { ViewModelProvider(this)[NativeCategoriesModel::class.java] }
    private val workspace by lazy { ViewModelProvider(this)[NativeWorkspaceModel::class.java] }

    private var nativeAppearanceMode="system"
    private var resumed by mutableStateOf(false)
    private var unlockEpoch by mutableStateOf(0)
    private var restoredComposerOwner: String? = null
    private var restoredComposerId:String?=null
    private val attachmentPicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { composer.receiveFiles(it) }
    private val composer by lazy { ViewModelProvider(this)[NativeComposerModel::class.java] }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        exportPicker=activityResultRegistry.register(exports.pickerKey,this,ActivityResultContracts.OpenDocumentTree()){exports.picked(it)}
        if(savedInstanceState==null)receiveNavigation(intent) else navigation.skipStartup()
        restoredComposerOwner = savedInstanceState?.getString("nativeComposerOwner")
        restoredComposerId=savedInstanceState?.getString("nativeComposerId")
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        setContent { InboxTheme(mode=settings.state.options.optString("theme","system"),large=settings.state.options.optBoolean("large"),compact=settings.state.options.optBoolean("compact")) {
            LaunchedEffect(accounts.state.restart,resumed,unlockEpoch){
                if(accounts.state.restart&&resumed&&!AppLock.locked(this@ComposeInboxActivity)){
                    // New activity record, not recreate(): every old account ViewModel and saved route is discarded.
                    val next=Intent(this@ComposeInboxActivity,ComposeInboxActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    navigation.pending?.let {target->
                        if(target.mail!=null)next.putExtra("openMail",target.mail.encode())
                        else if(target.kind=="tasks")next.putExtra("openTasks",true).putExtra("openTasksAccount",target.account)
                        else next.putExtra("openWidgetRoute",if(target.kind=="calendar")"calendar" else "mail").putExtra("openWidgetAccount",target.account)
                    }
                    startActivity(next);finish()
                }
            }
            LaunchedEffect(settings.state.options,resumed){
                nativeAppearanceMode=settings.state.options.optString("theme","system")
                val dark=AppearancePolicy.dark(nativeAppearanceMode,(resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES)
                val style=if(dark)androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) else androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle=style,navigationBarStyle=style)
                if(resumed&&!AppLock.locked(this@ComposeInboxActivity))BackgroundMailService.reconcile(this@ComposeInboxActivity,true)
            }
            LaunchedEffect(settings.state.owner,settings.state.options,inbox.state.owner){
                if(settings.state.ready&&settings.state.owner==inbox.state.owner)inbox.threadPreference(settings.state.owner,settings.state.options.optBoolean("mailThreads",true))
            }
            val holder = rememberSaveableStateHolder()
            LaunchedEffect(serverDrafts.state.open){if(!serverDrafts.state.open&&drafts.state.open)drafts.load()}
            LaunchedEffect(aiSettings.state.open){if(!aiSettings.state.open){if(settings.state.ready)settings.load();if(digest.state.open)digest.resume()}}

            LaunchedEffect(exports.state.pickerRequested,resumed,unlockEpoch){
                if(resumed&&!AppLock.locked(this@ComposeInboxActivity)&&exports.takePickerRequest()){
                    try{exportPicker.launch(null)}catch(_:Exception){exports.pickerFailed()}
                }
            }
            LaunchedEffect(inbox.state.openedFile,composer.state.open) {
                if (!composer.state.open && inbox.state.openedFile != null) {
                    while (inbox.state.openedFile != null) {
                        if (resumed && !AppLock.locked(this@ComposeInboxActivity)) {
                            inbox.takeFile()?.let { try { showFile(it) } catch (_: Exception) { inbox.fileError() } }
                            break
                        }
                        delay(200)
                    }
                }
            }
            LaunchedEffect(workspace.state.open,workspace.state.eventRoute) {
                workspace.takeEventReturn()?.let {(day,notice)->if(workspace.state.owner==calendar.state.owner)calendar.returnFromEditor(day,notice)}
            }
            LaunchedEffect(calendar.state.open,calendar.state.returnRoute) {
                if(!calendar.state.open){
                    when(calendar.takeReturnRoute()){
                        "workspace"->if(inbox.state.owner.isNotBlank())workspace.open(inbox.state.owner)
                    }
                }
            }
            LaunchedEffect(workspace.state.open,workspace.state.taskMail,workspace.state.eventMail) {
                if(!workspace.state.open&&(workspace.state.taskMail!=null||workspace.state.eventMail!=null)){workspace.clearMailTaskReturn();holder.removeState("mail-task")}
            }
            LaunchedEffect(inbox.state.linked){if(!inbox.state.linked)holder.removeState("linked-reader")}
            LaunchedEffect(files.state.openedFile,resumed,unlockEpoch){
                if(resumed&&!AppLock.locked(this@ComposeInboxActivity))files.takeFile()?.let {try{showFile(it)}catch(_:Exception){files.fileError()}}
            }
            val fullClient = { startActivity(Intent(this, MainActivity::class.java)); Unit }
            val pending=navigation.pending
            val navigationAllowed=!calls.state.open&&!files.state.open&&!serverDrafts.state.open&&!serverDrafts.state.busy&&!drafts.state.open&&!drafts.state.busy&&!scheduled.state.open&&!scheduled.state.busy&&!digest.state.open&&!aiSettings.state.open&&!aiSettings.state.busy&&!accounts.state.open&&!accounts.state.busy&&!settings.state.open&&!settings.state.busy&&!exports.state.open&&!composer.state.open&&!inbox.state.busy&&!inbox.state.foldersOpen&&!inbox.state.moveOpen&&
                !workspace.state.busy&&workspace.state.editor==null&&!workspace.state.leaveOpen&&!workspace.state.deleteOpen&&
                !categories.state.busy&&categories.state.editor==null&&!categories.state.leaveOpen&&
                !extras.state.busy&&extras.state.target==null&&contacts.canExternalNavigation()&&writingSettings.canExternalNavigation()
            val mailRouteVisible=!calls.state.open&&!files.state.open&&!serverDrafts.state.open&&!drafts.state.open&&!scheduled.state.open&&!digest.state.open&&
                !aiSettings.state.open&&!accounts.state.open&&!settings.state.open&&!exports.state.open&&!composer.state.open&&
                !contacts.state.open&&!calendar.state.open&&!workspace.state.open&&!categories.state.open&&!extras.state.open&&!writingSettings.state.open
            LaunchedEffect(mailRouteVisible,resumed,unlockEpoch){
                val visible=mailRouteVisible&&resumed&&!AppLock.locked(this@ComposeInboxActivity)
                inbox.watchVisible(visible)
                if(visible)relatedSearch.resume() else relatedSearch.pause()
            }
            LaunchedEffect(inbox.state.owner){if(relatedSearch.state.owner.isNotEmpty()&&relatedSearch.state.owner!=inbox.state.owner)relatedSearch.clear()}
            LaunchedEffect(settings.state.ready,settings.state.owner,inbox.state.owner,accounts.state.ready,resumed,unlockEpoch){
                if(resumed&&!AppLock.locked(this@ComposeInboxActivity)&&accounts.state.ready&&settings.state.ready&&settings.state.owner==inbox.state.owner){
                    val canOpen=navigationAllowed&&!contacts.state.open&&!calendar.state.open&&!workspace.state.open&&!categories.state.open&&!extras.state.open&&!writingSettings.state.open&&inbox.state.selected==null
                    if(navigation.initialRoute(inbox.state.owner,settings.state.options.optString("startScreen","mail"),canOpen)=="calendar")calendar.open(inbox.state.owner)
                }
            }
            LaunchedEffect(settings.state.cacheRevision,resumed,unlockEpoch){
                if(resumed&&!AppLock.locked(this@ComposeInboxActivity)&&settings.state.cacheRevision>0)inbox.invalidateOffline(settings.state.owner,settings.state.cacheRevision)
            }
            LaunchedEffect(pending,navigationAllowed,resumed,inbox.state.owner,unlockEpoch) {
                if(pending!=null&&navigationAllowed&&resumed&&!AppLock.locked(this@ComposeInboxActivity))openNavigation(pending)
            }
            Column(Modifier.fillMaxSize()) {
                if(pending!=null&&!AppLock.locked(this@ComposeInboxActivity))Surface(tonalElevation=3.dp) {
                    Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
                        Text("Переход из уведомления ждёт завершения текущего действия.",Modifier.weight(1f),fontSize=12.sp)
                        TextButton(onClick={navigation.consume(pending)}){Text("Убрать")}
                    }
                }
                Box(Modifier.weight(1f)) {

            if(accounts.state.restart) Box(Modifier.fillMaxSize(),contentAlignment=androidx.compose.ui.Alignment.Center){CircularProgressIndicator()}
            else if(accounts.state.open) NativeAccountsScreen(accounts)
            else if (composer.state.open) NativeComposer(composer, fullClient) {
                composer.beginPick {
                    if (!isDestroyed && resumed) try { attachmentPicker.launch(arrayOf("*/*")) } catch (_: Exception) { composer.pickerFailed() }
                    else composer.pickerFailed()
                }
            }
            else if(inbox.state.linked) holder.SaveableStateProvider("linked-reader") {
                NativeInbox(inbox,{reply()},{reply(true)},::forwardText,{},fullClient,null,{},categoryRules=categories.state.rules.takeIf {categories.state.owner==inbox.state.owner} ?: emptyList(),
                    labels=extras.state.labels.takeIf {extras.state.owner==inbox.state.owner} ?: JSONObject(),
                    responses=extras.state.responses.takeIf {extras.state.owner==inbox.state.owner} ?: emptyList(),snoozed=extras.state.snoozed.takeIf {extras.state.owner==inbox.state.owner} ?: emptyList(),followups=extras.state.followups.takeIf {extras.state.owner==inbox.state.owner} ?: emptyList(),forwardOriginal=::forwardOriginal,foreground=resumed)
            }
            else if(calls.state.open) NativeCallsScreen(calls)
            else if(files.state.open) NativeFilesScreen(files,{key->
                val source=files.mail(key)
                if(source==null||!inbox.openLinked(files.state.owner,source))files.linkError()
            },{key->files.file(key)?.let {(ref,file)->exports.begin(files.state.owner,ref,listOf(file),false)}},
                saveSelected={files.selectedExport()?.let {exports.beginLibrary(it)}})
            else if(serverDrafts.state.open) NativeServerDraftsScreen(serverDrafts){owner,id->composer.open(owner,restoreOnly=true,expectedId=id)}
            else if(drafts.state.open) NativeDraftsScreen(drafts,{owner,id->composer.open(owner,restoreOnly=true,expectedId=id)},{owner->composer.open(owner)},{serverDrafts.open(drafts.state.owner)})
            else if(scheduled.state.open) NativeScheduledScreen(scheduled){owner,id->composer.open(owner,restoreOnly=true,expectedId=id)}
            else if(aiSettings.state.open) NativeAiSettingsScreen(aiSettings)
            else if(digest.state.open) holder.SaveableStateProvider("mail-digest") {
                NativeDigestScreen(digest,{aiSettings.open(digest.state.owner)}){number->digest.source(number)?.let {if(!inbox.openLinked(digest.state.owner,it))digest.linkError()} ?: digest.linkError()}
            }
            else if(settings.state.open) NativeSettingsScreen(settings,this@ComposeInboxActivity,{accounts.open()},{settings.back();writingSettings.open(inbox.state.owner)},{aiSettings.open(inbox.state.owner)})
            else if(writingSettings.state.open) holder.SaveableStateProvider("writing-settings") {NativeWritingSettingsScreen(writingSettings)}
            else if(extras.state.open) holder.SaveableStateProvider("reply-reminders") {
                NativeMailExtrasScreen(extras){record->extras.openResponse(record,false)?.let {if(!inbox.openLinked(extras.state.owner,it))extras.linkError()}}
            }
            else if(categories.state.open) holder.SaveableStateProvider("categories") {NativeCategoriesScreen(categories)}
            else if(workspace.state.open) holder.SaveableStateProvider(if(workspace.state.taskMail!=null||workspace.state.eventMail!=null)"mail-task" else "workspace") {
                NativeWorkspaceScreen(workspace,openCalendarDay={day->val owner=workspace.state.owner;workspace.leave(true);calendar.openDay(owner,day,"workspace")}){id->workspace.sourceMail(id)?.let {if(!inbox.openLinked(workspace.state.owner,it))workspace.linkError()} ?: workspace.linkError()}
            }
            else if(calendar.state.open) holder.SaveableStateProvider("calendar") {
                NativeCalendarScreen(calendar,fullClient,{day,id->workspace.openEvent(calendar.state.owner,day,id)},{id->
                    calendar.sourceMail(id)?.let {if(!inbox.openLinked(calendar.state.owner,it))calendar.linkError()} ?: calendar.linkError()
                },{event->
                    val owner=calendar.state.owner
                    calendar.close()
                    workspace.openCalendarTask(owner,event)
                })
            }
            else if(contacts.state.open) NativeContactsScreen(contacts,{email->
                contacts.person(email)?.let {composer.open(contacts.state.owner,recipient=it.email)}
            },fullClient)
            else holder.SaveableStateProvider("inbox") {
                key(inbox.state.owner) { NativeInbox(inbox, { reply() }, { reply(true) }, ::forwardText,
                    { if (resumed && !AppLock.locked(this@ComposeInboxActivity)) composer.open(inbox.state.owner) }, fullClient,
                    composer.state.notice ?: extras.state.notice.takeIf {extras.state.owner==inbox.state.owner} ?: calendar.state.actionNotice.takeIf {calendar.state.owner==inbox.state.owner||calendar.state.pickerOwner==inbox.state.owner},{composer.clearClosedNotice();extras.clearNotice();calendar.clearActionNotice()},{contacts.open(inbox.state.owner)},{calendar.open(inbox.state.owner)},{workspace.open(inbox.state.owner)},{
                        val state=inbox.state
                        state.selected?.let {mail->workspace.openMailTask(state.owner,JSONObject().put("account",state.owner)
                            .put("folder",mail.ref.folder).put("uid",mail.ref.uid).put("validity",mail.ref.validity)
                            .put("subject",mail.subject).put("sender",mail.sender).put("date",mail.date))}
                    },{inbox.state.selected?.let {workspace.openMailEvent(inbox.state.owner,mailSource(inbox.state,it))}},
                        linkMailToEvent={inbox.state.selected?.let {calendar.openMailEventPicker(inbox.state.owner,mailSource(inbox.state,it).put("email",it.email))}},
                        categoryRules=categories.state.rules.takeIf {categories.state.owner==inbox.state.owner} ?: emptyList(),
                        categoryFilter=categories.state.filter.takeIf {categories.state.owner==inbox.state.owner} ?: "",
                        selectCategory=categories::filter,openCategories={categories.open(inbox.state.owner)},
                        labels=extras.state.labels.takeIf {extras.state.owner==inbox.state.owner} ?: JSONObject(),
                        responses=extras.state.responses.takeIf {extras.state.owner==inbox.state.owner} ?: emptyList(),
                        setLabel={inbox.state.selected?.let {extras.begin(inbox.state.owner,mailSource(inbox.state,it),"label")}},
                        setResponse={inbox.state.selected?.let {extras.begin(inbox.state.owner,mailSource(inbox.state,it),"response")}},
                        openResponses={extras.open(inbox.state.owner)},
                        snoozed=extras.state.snoozed.takeIf {extras.state.owner==inbox.state.owner} ?: emptyList(),
                        followups=extras.state.followups.takeIf {extras.state.owner==inbox.state.owner} ?: emptyList(),
                        setSnooze={inbox.state.selected?.let {extras.begin(inbox.state.owner,mailSource(inbox.state,it),"snooze")}},
                        setFollowup={inbox.state.selected?.let {extras.begin(inbox.state.owner,mailSource(inbox.state,it).put("messageId",inbox.state.body?.message?.optString("messageId") ?: ""),"followup")}},extrasReady=extras.state.owner==inbox.state.owner&&!extras.state.busy,openWritingSettings={writingSettings.open(inbox.state.owner)},openAccounts={if(navigationAllowed)accounts.open()},openSettings={if(navigationAllowed)settings.open()},
                        digestEnabled=settings.state.ready&&settings.state.owner==inbox.state.owner&&(!settings.state.options.has("neuralAnalysis")||settings.state.options.opt("neuralAnalysis")==true),
                        openDrafts={if(navigationAllowed)drafts.open(inbox.state.owner)},
                        openScheduled={if(navigationAllowed)scheduled.open(inbox.state.owner)},
                        openDigest={if(navigationAllowed&&inbox.state.selected==null)digest.open(inbox.state.owner,inbox.state.folder,inbox.state.rows)},
                        foreground=resumed,openCalls={if(navigationAllowed)calls.open(inbox.state.owner)},related=relatedSearch,onSearchContact={email->composer.open(inbox.state.owner,recipient=email)},
                        onSearchEvent={event->calendar.clearSearch();calendar.openDay(inbox.state.owner,event.getString("date"),"mail")},
                        openFiles={if(navigationAllowed)files.open(inbox.state.owner,inbox.state.folder)},forwardOriginal=::forwardOriginal,saveFiles={owner,ref,files,original->inbox.readerAction(owner,ref){if(original||files.isNotEmpty()&&files.all {inbox.state.body?.files?.contains(it)==true})exports.begin(owner,ref,files,original)}}) }
            }
                }
            }
            NativeMailEventPickerDialog(calendar)
            NativeMailExtrasDialogs(extras)
            NativeExportDialog(exports)
        } }
    }
    override fun getNativeAppearanceMode():String = nativeAppearanceMode
    override fun onResume() {
        super.onResume(); resumed = true; files.resume();serverDrafts.resume();drafts.resume();scheduled.resume();digest.resume();aiSettings.resume();accounts.resume();settings.resume();composer.resume()
        restoreComposer()
        calls.resume();relatedSearch.resume();exports.resume();inbox.resume();contacts.resume();calendar.resume();workspace.resume();categories.resume();extras.resume();writingSettings.resume()
    }

    private fun restoreComposer(){
        if(!resumed||AppLock.locked(this))return
        restoredComposerOwner?.let {owner->
            val id=restoredComposerId;restoredComposerOwner=null;restoredComposerId=null
            composer.open(owner,restoreOnly=true,expectedId=id)
        }
    }
    override fun appLockChanged() {
        unlockEpoch++
        if(resumed&&!AppLock.locked(this)){files.resume();serverDrafts.resume();drafts.resume();scheduled.resume();digest.resume();aiSettings.resume();accounts.resume();settings.resume();composer.resume();restoreComposer();calls.resume();relatedSearch.resume();exports.resume();inbox.resume();contacts.resume();calendar.resume();workspace.resume();categories.resume();extras.resume();writingSettings.resume()}
    }
    override fun onNewIntent(intent: Intent) {super.onNewIntent(intent);setIntent(intent);receiveNavigation(intent)}
    private fun receiveNavigation(intent: Intent){
        runCatching {navigation.receive(NativeOpenTarget.from(intent.getStringExtra("openMail"),intent.getStringExtra("openWidgetRoute"),
            intent.getStringExtra("openWidgetAccount"),intent.getBooleanExtra("openTasks",false),intent.getStringExtra("openTasksAccount")))}
        listOf("openMail","openWidgetRoute","openWidgetAccount","openTasks","openTasksAccount").forEach {intent.removeExtra(it)}
    }
    private fun openNavigation(target: NativeOpenTarget){
        if(navigation.pending!==target||!resumed||AppLock.locked(this))return
        val owner=inbox.state.owner
        if(owner.isEmpty()||target.account.isNotEmpty()&&target.account!=owner){
            android.widget.Toast.makeText(this,"Выберите аккаунт уведомления. Его содержимое откроется только после вашего выбора.",android.widget.Toast.LENGTH_LONG).show()
            accounts.open();return
        }
        contacts.closeForNavigation();writingSettings.closeForNavigation();calendar.close();workspace.back();categories.back();extras.back()
        if(target.mail!=null){if(inbox.openNotice(target.mail))navigation.consume(target);return}
        inbox.backToList()
        when(target.kind){"tasks"->{workspace.kind("tasks");workspace.open(owner)};"calendar"->calendar.open(owner);"mailbox"->inbox.loadInbox(false,"INBOX")}
        navigation.consume(target)
    }
    override fun onSaveInstanceState(outState: Bundle) {
        if (composer.state.open && composer.state.draftId.isNotBlank()) {
            outState.putString("nativeComposerOwner", composer.state.owner)
            outState.putString("nativeComposerId",composer.state.draftId)
        }
        super.onSaveInstanceState(outState)
    }
    override fun onPause() { calls.pause();relatedSearch.pause();files.pause();serverDrafts.pause();drafts.pause();scheduled.pause();digest.pause();aiSettings.pause();accounts.pause();settings.pause();exports.pause(isChangingConfigurations);composer.pause();contacts.pause();calendar.pause();workspace.pause();categories.pause();extras.pause();writingSettings.pause(); resumed = false; inbox.pause(isChangingConfigurations); super.onPause() }
    @Deprecated("System credential result is also used by the existing app lock")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (!AppLock.result(this, requestCode, resultCode)) super.onActivityResult(requestCode, resultCode, data)
    }
    private fun reply(all: Boolean = false) {
        if (!resumed || AppLock.locked(this) || inbox.state.busy) return
        val body = inbox.state.body ?: return
        composer.open(inbox.state.owner, body.message, replyAll=all,replyText=body.text)
    }
    private fun forwardText() {
        if(!resumed||AppLock.locked(this)||inbox.state.busy)return
        val body=inbox.state.body ?: return
        val ref=inbox.state.selected?.ref ?: return
        val files=org.json.JSONArray()
        body.files.forEach {files.put(JSONObject().put("name",it.name).put("path",it.path))}
        val source=JSONObject().put("folder",ref.folder).put("uid",ref.uid).put("validity",ref.validity).put("attachments",files)
        composer.open(inbox.state.owner,body.message,forwardText=body.text,forwardSource=source)
    }
    private fun forwardOriginal(){
        if(!resumed||AppLock.locked(this)||inbox.state.busy)return
        val mail=inbox.state.selected ?: return
        val message=inbox.state.body?.message ?: JSONObject().put("subject",mail.subject)
        val source=JSONObject().put("folder",mail.ref.folder).put("uid",mail.ref.uid).put("validity",mail.ref.validity)
        composer.open(inbox.state.owner,message,forwardSource=source,forwardOriginal=true)
    }
    private fun showFile(value: JSONObject) {
        val file = File(value.getString("file")).canonicalFile
        require(file.parentFile == File(cacheDir, "attachments").canonicalFile && file.isFile)
        val name = value.getString("name"); val mime = value.getString("mime")
        if(value.optBoolean("_share")){ShareActions.file(this,file,mime,name);return}
        val uri = Uri.Builder().scheme("content").authority(AttachmentProvider.AUTHORITY).appendPath(file.name).build()
        val intent = when {
            PdfPreviewPolicy.pdf(name, mime) -> Intent(this, PdfPreviewActivity::class.java).setData(uri)
            DocxReader.docx(name, mime) -> Intent(this, DocxPreviewActivity::class.java).setData(uri)
            else -> Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply { clipData = ClipData.newRawUri(name, uri) }
        }
        startActivity(intent.putExtra("name", name).putExtra("appearance", settings.state.options.optString("theme","system")))
    }

}

internal fun mailSource(state: InboxState,mail: NativeMail):JSONObject=JSONObject().put("account",state.owner)
    .put("folder",mail.ref.folder).put("uid",mail.ref.uid).put("validity",mail.ref.validity)
    .put("subject",mail.subject).put("sender",mail.sender).put("date",mail.date)

internal val LocalCompactInbox=staticCompositionLocalOf {false}
@Composable internal fun InboxTheme(mode:String="system",large:Boolean=false,compact:Boolean=false,content: @Composable () -> Unit) {
    val scheme = if (AppearancePolicy.dark(mode,isSystemInDarkTheme())) darkColorScheme(primary = Color(0xFFCEACF3), onPrimary = Color(0xFF28153A),
        background = Color(0xFF13111A), surface = Color(0xFF1D1A27)) else lightColorScheme(primary = Color(0xFF70508F))
    val density=androidx.compose.ui.platform.LocalDensity.current
    CompositionLocalProvider(LocalCompactInbox provides compact,androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density,density.fontScale*(if(large)1.15f else 1f))) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
@Composable internal fun NativeInbox(model: NativeInboxModel, reply: () -> Unit, replyAll: () -> Unit, forwardText: () -> Unit, compose: () -> Unit, fullClient: () -> Unit, notice: String?, dismissNotice: () -> Unit, openContacts: ()->Unit = {}, openCalendar: ()->Unit = {}, openWorkspace: ()->Unit = {}, createTask: ()->Unit = {}, createEvent: ()->Unit = {}, linkMailToEvent: ()->Unit = {}, categoryRules: List<JSONObject> = emptyList(),categoryFilter: String="",selectCategory: (String)->Unit = {},openCategories: ()->Unit = {},labels: JSONObject=JSONObject(),responses: List<JSONObject> = emptyList(),setLabel: ()->Unit = {},setResponse: ()->Unit = {},openResponses: ()->Unit = {},extrasReady: Boolean=true,snoozed: List<JSONObject> = emptyList(),setSnooze: ()->Unit = {},setFollowup: ()->Unit = {},followups: List<JSONObject> = emptyList(),openWritingSettings: ()->Unit = {},openAccounts: ()->Unit = {},openSettings: ()->Unit = {},digestEnabled:Boolean=false,openDigest: ()->Unit = {},openScheduled: ()->Unit = {},openDrafts: ()->Unit = {},saveFiles: (String,MailRef,List<NativeFile>,Boolean)->Unit = {_,_,_,_->},forwardOriginal:()->Unit = {},openFiles:()->Unit = {},related:NativeRelatedSearchModel?=null,onSearchContact:(String)->Unit = {},onSearchEvent:(JSONObject)->Unit = {},openCalls:()->Unit = {},foreground:Boolean=true) {
    var moreScreens by remember {mutableStateOf(false)}
    val state=model.state
    val scope=ThreadScope(state.owner,state.folder,state.searchKey,state.filter)
    val categoryMatcher=remember(categoryRules){val array=org.json.JSONArray();categoryRules.forEach {array.put(it)};MailCategories(array)}
    val hiddenRows=remember(snoozed){val array=org.json.JSONArray();snoozed.forEach {array.put(it)};array}
    val snoozeClock by produceState(System.currentTimeMillis(),snoozed) {
        while(true){val now=System.currentTimeMillis();value=now;val next=snoozed.map {it.optLong("at")}.filter {it>now}.minOrNull() ?: break;delay((next-now).coerceIn(1L,60000L))}
    }
    val hiddenKeys=remember(state.owner,hiddenRows,snoozeClock){runCatching {NativeMailExtras.activeKeys(state.owner,hiddenRows,snoozeClock)}.getOrDefault(emptySet())}
    fun category(mail: NativeMail)=categoryMatcher.match(mail.email,mail.subject)
    var readerActions by remember(state.owner,state.selected?.ref) {mutableStateOf(false)}
    val position = key(state.owner,state.folder,state.searchKey,state.filter) {rememberLazyListState(initialFirstVisibleItemIndex=model.listIndex,initialFirstVisibleItemScrollOffset=model.listOffset)}
    val threadScroll = key(scope,state.thread.anchor) {rememberLazyListState()}
    var previousCategory by remember(state.owner){mutableStateOf(categoryFilter)}
    LaunchedEffect(categoryFilter){if(previousCategory!=categoryFilter){previousCategory=categoryFilter;position.scrollToItem(0);model.clearThread();model.clearSelection()}}
    LaunchedEffect(position,state.owner,state.folder,state.searchKey,state.filter) {
        snapshotFlow { position.firstVisibleItemIndex to position.firstVisibleItemScrollOffset }.collect { (index,offset) -> model.listPosition(state.owner,state.folder,state.searchKey,state.filter,index,offset) }
    }
    val loadedRows=state.rows.filter {!hiddenKeys.contains(NativeMailEvents.key(mailSource(state,it)))}
    val eligible: (NativeMail)->Boolean={NativeMailSearch.matches(state.filter,it.unread,it.hasAttachments)&&(categoryFilter.isEmpty()||category(it)?.optString("id")==categoryFilter)}
    val displayRows=buildDisplayRows(loadedRows,state.owner,state.threadsEnabled,eligible)
    val visible=loadedRows.filter(eligible)
    val visibleRefs=remember(displayRows){displayRows.map {when(it){is InboxDisplayRow.Single->it.mail.ref;is InboxDisplayRow.Group->it.members.first().ref}}.toSet()}
    val activeSelection=state.activeSelection(visibleRefs)
    BackHandler(state.selected == null && state.thread.anchor == null && activeSelection.isNotEmpty()) { model.clearSelection() }
    BackHandler(state.selected != null || state.thread.anchor != null) { model.backToList() }
    Scaffold(topBar = { TopAppBar(title = {
        if(state.selected==null&&state.thread.anchor==null) TextButton(onClick=model::showFolders,enabled=state.owner.isNotBlank()&&!state.busy,shape=RoundedCornerShape(10.dp),contentPadding=PaddingValues(horizontal=12.dp,vertical=0.dp)) {
            Column {
                Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                    Text(NativeFolders.label(state.folder),Modifier.weight(1f,fill=false),maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.titleLarge,color=MaterialTheme.colorScheme.onSurface)
                    Text("⌄",Modifier.padding(start=6.dp),fontSize=16.sp)
                }
                if(state.account.isNotBlank())Text(state.account,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }else Column {
            Text(if(state.selected!=null)"Письмо" else "Цепочка",maxLines=1)
            if(state.account.isNotBlank())Text(state.account,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
    }, navigationIcon = { if (state.selected != null||state.thread.anchor!=null) TextButton(onClick = model::backToList) { Text(if(state.selected==null)"К списку" else "Назад") } }, actions = {
        if (state.selected == null&&state.thread.anchor==null) {
            TextButton(onClick=model::toggleSearch,enabled=state.owner.isNotBlank()){Text("Поиск")}
            TextButton(onClick = { model.loadInbox(true) }, enabled = !state.busy) { Text("Обновить") }
        }
        else if (state.selected != null) TextButton(onClick={state.selected?.let {model.openMail(it,keepPosition=true,forceOnline=true)}},enabled=!state.busy) {Text("Обновить письмо")}
    }) }, bottomBar = { Surface(tonalElevation = 2.dp) {
        Column {
        if (state.selected != null) Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Button(onClick=reply,enabled=state.body!=null&&!state.busy,modifier=Modifier.weight(1f)) {Text("Ответить")}
            OutlinedButton(onClick=replyAll,enabled=state.body!=null&&!state.busy,modifier=Modifier.weight(1f)) {Text("Ответить всем")}
            Box {
                IconButton(onClick={readerActions=true},enabled=state.body!=null&&!state.busy,
                    modifier=Modifier.semantics {contentDescription="Другие действия письма"}) {Text("⋯",fontSize=24.sp)}
                DropdownMenu(expanded=readerActions,onDismissRequest={readerActions=false}) {
                    DropdownMenuItem(text={Text(if(state.selected?.unread==true)"Пометить прочитанным" else "Пометить непрочитанным")},
                        enabled=!state.busy,onClick={readerActions=false;state.selected?.let {model.markSeen(it.unread)}})
                    if(!state.linked){
                        DropdownMenuItem(text={Text("Отложить письмо")},enabled=!state.busy&&extrasReady,onClick={readerActions=false;setSnooze()})
                        DropdownMenuItem(text={Text("Проверить ответ собеседника")},enabled=!state.busy&&extrasReady&&ReplyReminder.validId(state.body?.message?.optString("messageId")),onClick={readerActions=false;setFollowup()})
                        DropdownMenuItem(text={Text("Метка письма")},enabled=!state.busy&&extrasReady,onClick={readerActions=false;setLabel()})
                        DropdownMenuItem(text={Text("Напомнить ответить")},enabled=!state.busy&&extrasReady,onClick={readerActions=false;setResponse()})
                    }
                    if(!state.linked)DropdownMenuItem(text={Text("Событие из письма")},enabled=!state.busy,
                        onClick={readerActions=false;createEvent()})
                    if(!state.linked)DropdownMenuItem(text={Text("Связать с событием")},enabled=!state.busy,
                        onClick={readerActions=false;linkMailToEvent()})
                    if(!state.linked)DropdownMenuItem(text={Text("Создать задачу")},enabled=!state.busy,
                        onClick={readerActions=false;createTask()})
                    if(!state.linked) {
                        val moveEnabled=!state.busy&&!state.cached&&!state.moveNeedsRefresh
                        if(state.cached||state.moveNeedsRefresh)Text("Для переноса обновите письмо с сервера.",Modifier.widthIn(max=250.dp).padding(12.dp),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        DropdownMenuItem(text={Text("В архив")},enabled=moveEnabled,onClick={readerActions=false;model.requestMove("archive")})
                        DropdownMenuItem(text={Text("В спам")},enabled=moveEnabled,onClick={readerActions=false;model.requestMove("spam")})
                        DropdownMenuItem(text={Text("В корзину")},enabled=moveEnabled,onClick={readerActions=false;model.requestMove("trash")})
                        DropdownMenuItem(text={Text("Переместить в папку…")},enabled=moveEnabled,onClick={readerActions=false;model.showMoveFolders()})
                    }
                    DropdownMenuItem(text={Text("Переслать текст")},enabled=state.body!=null&&!state.busy,
                        onClick={readerActions=false;forwardText()})
                    DropdownMenuItem(text={Text("Переслать оригинал EML")},enabled=state.selected!=null&&!state.busy,onClick={readerActions=false;forwardOriginal()})
                }
            }
        }
        if (state.selected == null && state.owner.isNotBlank()) Button(onClick = compose, enabled = !state.busy, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) { Text("Написать") }
        Row(Modifier.fillMaxWidth().navigationBarsPadding()) {
            if(state.selected==null) {
                TextButton(onClick=openCalendar,enabled=state.owner.isNotBlank(),modifier=Modifier.weight(1f)){Text("Календарь")}
                TextButton(onClick=openContacts,enabled=state.owner.isNotBlank(),modifier=Modifier.weight(1f)){Text("Контакты")}
            }
            Box(Modifier.weight(1f)) {
                TextButton(onClick={moreScreens=true},enabled=!state.busy,modifier=Modifier.fillMaxWidth()){Text("Ещё",maxLines=1)}
                DropdownMenu(expanded=moreScreens,onDismissRequest={moreScreens=false}) {
                    DropdownMenuItem(text={Text("Черновики на телефоне")},enabled=state.selected==null&&!state.busy&&!state.linked,onClick={moreScreens=false;openDrafts()})
                    DropdownMenuItem(text={Text("Файлы писем")},enabled=state.selected==null&&!state.busy&&!state.linked,onClick={moreScreens=false;openFiles()})
                    DropdownMenuItem(text={Text("По расписанию")},enabled=state.selected==null&&!state.busy&&!state.linked,onClick={moreScreens=false;openScheduled()})
                    if(digestEnabled)DropdownMenuItem(text={Text("Сводка почты")},enabled=state.selected==null&&!state.busy&&!state.linked,onClick={moreScreens=false;openDigest()})
                    DropdownMenuItem(text={Text("Письма на потом")},enabled=state.owner.isNotBlank()&&!state.linked&&!state.busy&&extrasReady,onClick={moreScreens=false;openResponses()})
                    DropdownMenuItem(text={Text("Шаблоны и подпись")},enabled=state.owner.isNotBlank()&&!state.linked&&!state.busy,onClick={moreScreens=false;openWritingSettings()})
                    DropdownMenuItem(text={Text("Категории и правила")},enabled=state.owner.isNotBlank()&&!state.linked&&!state.busy,onClick={moreScreens=false;openCategories()})
                    DropdownMenuItem(text={Text("Звонки и история")},enabled=state.owner.isNotBlank()&&!state.linked&&!state.busy,onClick={moreScreens=false;openCalls()})
                    DropdownMenuItem(text={Text("Задачи и заметки")},enabled=state.owner.isNotBlank()&&!state.linked&&!state.busy,onClick={moreScreens=false;openWorkspace()})
                    DropdownMenuItem(text={Text("Аккаунты и SMTP")},enabled=!state.linked&&!state.busy,onClick={moreScreens=false;openAccounts()})
                    DropdownMenuItem(text={Text("Настройки")},enabled=state.owner.isNotBlank()&&!state.linked&&!state.busy,onClick={moreScreens=false;openSettings()})
                    DropdownMenuItem(text={Text("Все возможности почты")},enabled=!state.busy,onClick={moreScreens=false;fullClient()})
                }
            }
        }
        }
    } }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            if(state.serverChanged&&!state.busy)Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
                Text("В папке есть обновления",Modifier.weight(1f),fontSize=13.sp)
                TextButton(onClick={model.loadInbox(true)},enabled=state.selected==null&&state.selectedRefs.isEmpty()&&!state.linked){Text("Обновить")}
            }
            notice?.let { Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                Text(it,Modifier.weight(1f),fontSize=13.sp)
                TextButton(onClick=dismissNotice){Text("Закрыть")}
            } }
            if(state.cached)CopyAgeText(if(state.selected==null)state.pageCachedAt else state.bodyCachedAt,foreground)
            if(state.selected==null&&state.thread.anchor==null&&state.owner.isNotBlank()) {
                if(state.searchOpen||related?.state?.criteria!=null&&related.state.enabled)BoxWithConstraints(Modifier.fillMaxWidth()){
                    val panelHeight=maxHeight*0.55f
                    Column(Modifier.fillMaxWidth().heightIn(max=panelHeight).verticalScroll(rememberScrollState())){
                if(state.searchOpen&&related!=null)Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    FilterChip(selected=!related.state.enabled,onClick={if(related.state.enabled)related.toggle()},label={Text("Письма")})
                    FilterChip(selected=related.state.enabled,onClick={if(!related.state.enabled)related.toggle()},label={Text("Везде")})
                }
                if(state.searchOpen&&related!=null&&related.state.enabled&&related.state.criteria==null)
                    Text("Нажмите «На телефоне» или «На сервере», чтобы искать также контакты и события.",Modifier.padding(horizontal=16.dp),fontSize=12.sp)
                if(state.searchOpen) NativeInboxSearch(
                    state,
                    model::searchText,
                    model::searchFrom,
                    model::searchStart,
                    model::searchEnd,
                    model::searchAttachments,
                    model::searchUnread,
                    model::toggleAdvancedSearch,
                    model::resetSearchFilters,
                    {model.cancelSearch();related?.clear()},
                    {model.search(false)?.let {related?.search(state.owner,it,false)}},
                    {model.search(true)?.let {related?.search(state.owner,it,true)}}
                )
                if(related!=null)NativeRelatedResults(related,onSearchContact,onSearchEvent)
                    }
                }
                if(state.searchKey.isNotEmpty()) {
                    val crit=state.activeCriteria
                    Surface(
                        modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=4.dp),
                        shape=RoundedCornerShape(8.dp),
                        color=if(state.pageOffline) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer.copy(alpha=0.35f)
                    ) {
                        Column(Modifier.padding(horizontal=12.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                                Text(
                                    if(state.pageOffline) "Результаты на телефоне (сохранённые письма)" else "Результаты на сервере: «${NativeFolders.label(state.folder)}»",
                                    fontWeight=FontWeight.SemiBold,
                                    fontSize=13.sp,
                                    color=if(state.pageOffline) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                                    modifier=Modifier.weight(1f)
                                )
                                TextButton(onClick={model.clearSearch();related?.clear()},enabled=!state.busy,contentPadding=PaddingValues(horizontal=8.dp,vertical=0.dp)) {
                                    Text("Сбросить поиск",fontSize=12.sp)
                                }
                            }
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                if(state.query.isNotEmpty()) SuggestionChip(onClick={},label={Text("Текст: «${state.query}»",fontSize=11.sp)})
                                if(crit!=null&&crit.from.isNotEmpty()) SuggestionChip(onClick={},label={Text("От: ${crit.from}",fontSize=11.sp)})
                                if(crit!=null&&(crit.start.isNotEmpty()||crit.end.isNotEmpty())) SuggestionChip(onClick={},label={Text("Даты МСК: ${crit.start.ifEmpty {"…"}} — ${crit.end.ifEmpty {"…"}}",fontSize=11.sp)})
                                if(crit!=null&&crit.attachments) SuggestionChip(onClick={},label={Text("С вложениями",fontSize=11.sp)})
                                if(crit!=null&&crit.unread) SuggestionChip(onClick={},label={Text("Непрочитанные",fontSize=11.sp)})
                            }
                            Text(
                                if(crit?.isAdvanced==true) "Совпадений в загруженном: ${state.rows.size} · проверено кандидатов: ${state.searchScanned} из ${state.searchCandidates}"
                                else "Загружено совпадений: ${state.rows.size} из ${state.total}",
                                fontSize=12.sp,
                                color=MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf("all" to "Все","unread" to "Непрочитанные","attachments" to "С вложениями").forEach {(value,label)->
                        FilterChip(selected=state.filter==value,onClick={model.filter(value)},label={Text(label)})
                    }
                }
                if(categoryRules.isNotEmpty())Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected=categoryFilter.isEmpty(),onClick={selectCategory("")},label={Text("Все категории")})
                    categoryRules.filter {it.optBoolean("enabled",true)}.forEach {rule->FilterChip(selected=categoryFilter==rule.optString("id"),
                        onClick={selectCategory(rule.optString("id"))},label={Text(rule.optString("name"),color=categoryColor(rule.optString("color")))})}
                }
                if(categoryFilter.isNotEmpty())Text("Категория среди загруженных писем. «Показать ещё» расширяет выборку.",Modifier.padding(horizontal=16.dp,vertical=4.dp),fontSize=12.sp)
                if(state.filter!="all")Text("Фильтр по загруженным результатам. «Показать ещё» расширяет выборку.",Modifier.padding(horizontal=16.dp,vertical=4.dp),fontSize=12.sp)
                Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=4.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Группировать в цепочки",Modifier.weight(1f),fontSize=13.sp)
                    Switch(checked=state.threadsEnabled,onCheckedChange={model.toggleThreads()},modifier=Modifier.semantics{contentDescription="Показывать письма сгруппированными в цепочки разговоров"})
                }
                Text("Цепочки собираются по загруженным письмам этой папки; счётчики относятся только к ним.",Modifier.padding(horizontal=16.dp,vertical=4.dp),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(activeSelection.isNotEmpty()) {
                    Surface(
                        modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=4.dp),
                        shape=RoundedCornerShape(8.dp),
                        color=MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=6.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                            Text("Выбрано: ${activeSelection.size}",fontWeight=FontWeight.SemiBold,fontSize=13.sp,modifier=Modifier.weight(1f))
                            TextButton(onClick={model.batchMarkSeen(true,visibleRefs)},enabled=!state.busy,contentPadding=PaddingValues(horizontal=8.dp,vertical=0.dp)) {
                                Text("Прочитано",fontSize=12.sp)
                            }
                            TextButton(onClick={model.batchMarkSeen(false,visibleRefs)},enabled=!state.busy,contentPadding=PaddingValues(horizontal=8.dp,vertical=0.dp)) {
                                Text("Непрочитано",fontSize=12.sp)
                            }
                            TextButton(onClick=model::clearSelection,enabled=!state.busy,contentPadding=PaddingValues(horizontal=8.dp,vertical=0.dp)) {
                                Text("Снять",fontSize=12.sp)
                            }
                        }
                    }
                }
            }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
            state.flagNotice?.let {Text(it,Modifier.padding(horizontal=16.dp,vertical=8.dp),fontSize=13.sp)}
            val mail = state.selected
            if (mail == null && state.thread.anchor != null) {
                Box(Modifier.weight(1f)) {
                    ThreadConversation(state,model,displayRows,threadScroll,scope,state.owner,labels,responses,snoozed,followups,::category)
                }
            } else if (mail == null) LazyColumn(state = position, modifier = Modifier.weight(1f)) {
                if (visible.isEmpty() && !state.busy&&state.error==null) item {
                    Text(
                        if(state.filter!="all"||categoryFilter.isNotEmpty())"В загруженных результатах совпадений нет."
                        else if(state.rows.isNotEmpty()&&state.rows.all {hiddenKeys.contains(NativeMailEvents.key(mailSource(state,it)))})"Письма отложены. Откройте «Письма на потом» в меню «Ещё»."
                        else if(state.searchKey.isNotEmpty()&&state.hasMore)"На проверенном участке совпадений нет. Нажмите «Показать ещё», чтобы проверить следующие письма."
                        else if(state.searchKey.isNotEmpty())"По запросу ничего не найдено."
                        else "В этой папке нет загруженных писем.",
                        Modifier.padding(20.dp)
                    )
                }
                items(displayRows, key = { it.key }) { item ->
                    val row=when(item){is InboxDisplayRow.Single->item.mail;is InboxDisplayRow.Group->item.members.first()}
                    val rule=category(row)
                    val isSelected=row.ref in activeSelection
                    val onOpen={
                        if(activeSelection.isNotEmpty())model.toggleSelection(row.ref)
                        else if(item is InboxDisplayRow.Group)model.openThread(item.anchor,scope)
                        else model.openMail(row)
                    }
                    MailRow(
                        row,!state.busy,onOpen,state.owner,labels,responses,snoozed,followups,
                        rule?.optString("name"),rule?.let{categoryColor(it.optString("color"))},
                        if(item is InboxDisplayRow.Group)summaryOf(item.members) else null,
                        selected=isSelected,
                        onToggleSelect={model.toggleSelection(row.ref)},
                        onToggleSeen=if(item is InboxDisplayRow.Single&&activeSelection.isEmpty())({model.toggleSeenFromList(row)}) else null
                    )
                }
                if (state.rows.isNotEmpty()) item {
                    val summaryText=if(state.activeCriteria?.isAdvanced==true) {
                        if(state.threadsEnabled)"Совпадений: ${state.rows.size}, цепочек: ${displayRows.count {it is InboxDisplayRow.Group}} (проверено ${state.searchScanned} из ${state.searchCandidates})"
                        else "Совпадений в загруженном: ${state.rows.size} (проверено ${state.searchScanned} из ${state.searchCandidates})"
                    } else {
                        if(state.threadsEnabled)"Загружено ${state.rows.size} писем, цепочек: ${displayRows.count {it is InboxDisplayRow.Group}} (в загруженном списке)"
                        else "Загружено ${state.rows.size} из ${state.total}"
                    }
                    Text(summaryText,Modifier.padding(16.dp),fontSize=12.sp)
                }
                if (state.hasMore) item {
                    OutlinedButton(onClick = { if(state.pageExpired)model.loadInbox(!state.pageOffline) else model.loadMore() }, enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp)) {
                        Text(if(state.pageExpired) "Обновить список" else if(state.busy) "Загрузка…" else "Показать ещё")
                    }
                }
            } else key(mail.ref,state.body != null) {
                val readerPosition=rememberScrollState(initial=model.readerOffset)
                LaunchedEffect(readerPosition,state.owner,mail.ref,state.body != null) {
                    if(state.body != null) snapshotFlow { readerPosition.value }.collect { model.readerPosition(state.owner,mail.ref,it) }
                }
                Column(Modifier.weight(1f).verticalScroll(readerPosition).padding(16.dp)) {
                MailLocalBadges(mail,state.owner,labels,responses,snoozed,followups)
                category(mail)?.let {rule->Text(rule.optString("name"),Modifier.padding(bottom=8.dp),color=categoryColor(rule.optString("color")))}
                Text(mail.subject, style = MaterialTheme.typography.headlineSmall)
                NativeReaderHeader(state.owner,mail,state.body?.message)
                if(!state.linked) {
                    val hint=remember(mail.ref,mail.subject,mail.date){NativeMailEvents.hint(mailSource(state,mail),"",java.time.Instant.now())}
                    hint?.let {Card(Modifier.fillMaxWidth().padding(bottom=12.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("${it.date} · ${it.time} МСК",color=MaterialTheme.colorScheme.primary)
                            Text("Дата из темы письма. Проверьте перед сохранением.",fontSize=12.sp)
                            TextButton(onClick=createEvent,enabled=!state.busy){Text("Создать / открыть событие")}
                        }
                    }}
                }
                if (state.body == null && !state.busy) TextButton(onClick = { model.openMail(mail) }) { Text("Загрузить письмо") }
                state.body?.let { body ->
                    if (body.files.isNotEmpty()) {
                        Text("Вложения · ${body.files.size}", color = MaterialTheme.colorScheme.primary)
                        TextButton(onClick={saveFiles(state.owner,mail.ref,body.files,false)},enabled=!state.busy){Text("Сохранить все вложения") }
                        body.files.forEach { file ->
                            OutlinedButton(onClick = { model.fileAction(state.owner,mail.ref,file) }, enabled = !state.busy && file.path.isNotBlank(),
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                Text(file.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Row {
                                TextButton(onClick={saveFiles(state.owner,mail.ref,listOf(file),false)},enabled=!state.busy&&file.path.isNotBlank()){Text("Сохранить")}
                                TextButton(onClick={model.fileAction(state.owner,mail.ref,file,true)},enabled=!state.busy&&file.path.isNotBlank()){Text("Поделиться файлом")}
                            }
                        }
                    }
                    TextButton(onClick={saveFiles(state.owner,mail.ref,emptyList(),true)},enabled=!state.busy){Text("Сохранить оригинал .eml")}
                    NativeReaderActions(model,mail,body.text)
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(body.text.ifBlank {"Текстовая часть отсутствует."}, Modifier.padding(top = 16.dp), fontSize = 16.sp, lineHeight = 25.sp)
                    }
                }
                }
            }
        }
    }
    if(state.moveOpen)AlertDialog(onDismissRequest=model::cancelMove,
        title={Text(when(state.moveKind){"archive"->"Переместить в архив?";"spam"->"Переместить в спам?";"trash"->"Переместить в корзину?";else->"Переместить письмо?"})},
        text={Column {Text(state.selected?.subject ?: "");if(state.moveKind=="folder")Text("Папка: ${state.moveDestination}")
            Text("Действие изменит письмо на сервере. Корзина не удаляет письмо окончательно. Автоматической отмены переноса нет.")}},
        confirmButton={TextButton(onClick=model::confirmMove){Text("Переместить")}},
        dismissButton={TextButton(onClick=model::cancelMove){Text("Отмена")}})
    if(state.foldersOpen) ModalBottomSheet(onDismissRequest=model::closeFolders,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        NativeFolderList(state,model::closeFolders,{model.loadFolders(true)},model::chooseFolder)
    }
}
@Composable internal fun MailLocalBadges(mail: NativeMail,owner: String,labels: JSONObject,responses: List<JSONObject>,snoozed: List<JSONObject> = emptyList(),followups: List<JSONObject> = emptyList()) {
    val ref=JSONObject().put("folder",mail.ref.folder).put("uid",mail.ref.uid).put("validity",mail.ref.validity)
    val key=NativeMailEvents.key(ref)
    val label=NativeMailExtras.labelName(labels.optString(key))
    if(label.isNotEmpty())Text(label,Modifier.padding(top=4.dp),fontSize=11.sp,color=MaterialTheme.colorScheme.primary)
    val response=responses.find {runCatching {NativeMailExtras.key(owner,it.getJSONObject("mail"))==key}.getOrDefault(false)}
    response?.let {Text("Ответить · "+responseTime(it.optLong("at")),Modifier.padding(top=4.dp),fontSize=11.sp,color=MaterialTheme.colorScheme.primary)}
    for((rows,label) in listOf(snoozed to "Отложено до",followups to "Проверить ответ")) {
        rows.find {runCatching {NativeMailExtras.key(owner,it.getJSONObject("mail"))==key}.getOrDefault(false)}?.let {
            Text(label+" · "+responseTime(it.optLong("at")),Modifier.padding(top=4.dp),fontSize=11.sp,color=MaterialTheme.colorScheme.primary)
        }
    }
}

private data class ThreadSummary(val count: Int, val unread: Int, val attachments: Boolean)
private fun summaryOf(members: List<NativeMail>)=ThreadSummary(members.size,members.count {it.unread},members.any {it.hasAttachments==true})

@Composable private fun MailRowBody(mail: NativeMail,rowColor: Color,chain: ThreadSummary?,owner: String,labels: JSONObject,responses: List<JSONObject>,snoozed: List<JSONObject>,followups: List<JSONObject>,categoryName: String?,categoryTint: Color?,selected: Boolean=false,onToggleSelect: (()->Unit)?=null,onToggleSeen: (()->Unit)?=null,enabled: Boolean=true) {
    Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=if(LocalCompactInbox.current)8.dp else 14.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        if(onToggleSelect!=null) {
            Surface(onClick=onToggleSelect,enabled=enabled,shape=CircleShape,color=if(selected)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha=.16f).compositeOver(rowColor),modifier=Modifier.size(40.dp).semantics {contentDescription=if(selected)"Снять выбор письма" else "Выбрать письмо"}) {
                Box(contentAlignment=androidx.compose.ui.Alignment.Center) {Text(if(selected)"✓" else avatarLetters(mail.sender),fontSize=14.sp,fontWeight=FontWeight.SemiBold,color=if(selected)MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)}
            }
        } else {
            Surface(shape=CircleShape,color=MaterialTheme.colorScheme.primary.copy(alpha=.16f).compositeOver(rowColor),modifier=Modifier.size(40.dp).clearAndSetSemantics {}) {
                Box(contentAlignment=androidx.compose.ui.Alignment.Center) {Text(avatarLetters(mail.sender),fontSize=14.sp,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.primary)}
            }
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) { Text(mail.sender, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp,
                fontWeight = if (mail.unread) FontWeight.SemiBold else FontWeight.Normal)
                Row(Modifier.padding(start=8.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(mailTime(mail.date),maxLines=1,fontSize=12.sp,color=if(mail.unread)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    if(onToggleSeen!=null) {
                        TextButton(onClick=onToggleSeen,enabled=enabled,contentPadding=PaddingValues(horizontal=4.dp,vertical=0.dp),modifier=Modifier.height(24.dp).semantics {contentDescription=if(mail.unread)"Пометить прочитанным" else "Пометить непрочитанным"}) {
                            Text(if(mail.unread)"●" else "○",fontSize=11.sp,color=if(mail.unread)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else if(mail.unread)Surface(shape=CircleShape,color=MaterialTheme.colorScheme.primary,modifier=Modifier.size(6.dp)) {}
                }
            }
            Text(mail.subject, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (mail.unread) FontWeight.SemiBold else FontWeight.Normal, fontSize = 15.sp)
            if (mail.preview.isNotBlank()) Text(mail.preview, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            MailLocalBadges(mail,owner,labels,responses,snoozed,followups)
            categoryName?.let {Text(it,Modifier.padding(top=4.dp),fontSize=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis,color=categoryTint ?: MaterialTheme.colorScheme.primary)}
            if(chain!=null&&chain.count>1) {
                Row(Modifier.padding(top=6.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    Surface(shape=RoundedCornerShape(6.dp),color=MaterialTheme.colorScheme.primary.copy(alpha=.10f).compositeOver(rowColor),modifier=Modifier.semantics {contentDescription="Цепочка писем: ${chain.count}"}) {
                        Text("${chain.count} писем",Modifier.padding(horizontal=7.dp,vertical=3.dp),fontSize=11.sp,color=MaterialTheme.colorScheme.primary)
                    }
                    if(chain.unread>0)Text("Непрочитанных: ${chain.unread}",Modifier.semantics {contentDescription="Непрочитанных в цепочке: ${chain.unread}"},fontSize=11.sp,color=MaterialTheme.colorScheme.primary)
                    if(chain.attachments)Text("Есть вложения",Modifier.semantics {contentDescription="В цепочке есть вложения"},fontSize=11.sp,color=MaterialTheme.colorScheme.primary)
                }
            }
            if(chain==null&&mail.hasAttachments==true)Surface(shape=RoundedCornerShape(6.dp),color=MaterialTheme.colorScheme.primary.copy(alpha=.10f).compositeOver(rowColor),modifier=Modifier.padding(top=6.dp)) {
                Text("Есть вложения",Modifier.padding(horizontal=7.dp,vertical=3.dp),fontSize=11.sp,color=MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable private fun MailRow(mail: NativeMail,enabled: Boolean,onClick: ()->Unit,owner: String,labels: JSONObject,responses: List<JSONObject>,snoozed: List<JSONObject>,followups: List<JSONObject>,categoryName: String?,categoryTint: Color?,chain: ThreadSummary?=null,selected: Boolean=false,onToggleSelect: (()->Unit)?=null,onToggleSeen: (()->Unit)?=null) {
    val displayMail=if(chain!=null)mail.copy(unread=chain.unread>0) else mail
    val rowColor=if(selected)MaterialTheme.colorScheme.primary.copy(alpha=.16f).compositeOver(MaterialTheme.colorScheme.background) else if(displayMail.unread)MaterialTheme.colorScheme.primary.copy(alpha=.075f).compositeOver(MaterialTheme.colorScheme.background) else MaterialTheme.colorScheme.background
    Surface(onClick = onClick, enabled = enabled, color = rowColor, modifier = Modifier.heightIn(min=44.dp)) {
        Column {
            MailRowBody(displayMail,rowColor,chain,owner,labels,responses,snoozed,followups,categoryName,categoryTint,selected,onToggleSelect,onToggleSeen,enabled)
            HorizontalDivider(Modifier.padding(start=68.dp,end=16.dp),thickness=.5.dp,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.07f))
        }
    }
}

@Composable private fun ThreadConversation(state: InboxState,model: NativeInboxModel,displayRows: List<InboxDisplayRow>,scroll: LazyListState,scope: ThreadScope,owner: String,labels: JSONObject,responses: List<JSONObject>,snoozed: List<JSONObject>,followups: List<JSONObject>,category: (NativeMail)->JSONObject?) {
    val anchor=state.thread.anchor
    // Resolve the original chain from all loaded rows, then keep only currently eligible display members.
    val displayRefs=displayRows.flatMap {row->when(row){is InboxDisplayRow.Single->listOf(refKey(row.mail.ref));is InboxDisplayRow.Group->row.members.map {refKey(it.ref)}}}.toSet()
    val members=if(anchor!=null)conversationMembers(state.rows,owner,MailRef(anchor.folder,anchor.uid,anchor.validity),displayRefs).asReversed() else emptyList()
    val summary=summaryOf(members)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(members.lastOrNull()?.subject ?: "Цепочка писем",style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
                Text("${summary.count} писем в загруженном списке этой папки. Откройте письмо для чтения и ответа.",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier=Modifier.semantics {contentDescription="В цепочке ${summary.count} писем, непрочитанных ${summary.unread}"})
            }
            TextButton(onClick=model::backToList,modifier=Modifier.heightIn(min=44.dp)){Text("К списку")}
        }
        if(summary.unread>0)Text("Непрочитанных: ${summary.unread}",Modifier.padding(horizontal=16.dp,vertical=4.dp),fontSize=12.sp,color=MaterialTheme.colorScheme.primary)
        if(members.isEmpty())Text("Цепочка отсутствует в текущем загруженном списке. Измените фильтр или загрузите ещё письма папки.",Modifier.padding(20.dp))
        else LazyColumn(state=scroll,modifier=Modifier.weight(1f)) {
            items(members,key={listOf(it.ref.folder,it.ref.validity,it.ref.uid)}) { member ->
                val rule=category(member)
                MailRow(member,!state.busy,{model.openThreadMember(member,scope)},owner,labels,responses,snoozed,followups,rule?.optString("name"),rule?.let{categoryColor(it.optString("color"))})
            }
        }
        if(state.hasMore)OutlinedButton(onClick={if(state.pageExpired)model.loadInbox(!state.pageOffline) else model.loadMore()},enabled=!state.busy,
            modifier=Modifier.fillMaxWidth().heightIn(min=44.dp).padding(horizontal=16.dp,vertical=8.dp)) {
            Text(if(state.pageExpired)"Обновить список" else if(state.busy)"Загрузка…" else "Показать ещё")
        }
    }
}
@Composable internal fun NativeInboxSearch(
    state: InboxState,
    changeText: (String)->Unit,
    changeFrom: (String)->Unit = {},
    changeStart: (String)->Unit = {},
    changeEnd: (String)->Unit = {},
    changeAttachments: (Boolean)->Unit = {},
    changeUnread: (Boolean)->Unit = {},
    toggleAdvanced: ()->Unit = {},
    resetFilters: ()->Unit = {},
    cancel: ()->Unit = {},
    offline: ()->Unit,
    online: ()->Unit
) {
    val focus=androidx.compose.ui.platform.LocalFocusManager.current
    val hasDraftFilters=state.searchText.isNotEmpty()||state.searchFrom.isNotEmpty()||state.searchStart.isNotEmpty()||state.searchEnd.isNotEmpty()||state.searchAttachments||state.searchUnread
    Column(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(value=state.searchText,onValueChange=changeText,singleLine=true,label={Text("Поиск в папке")},modifier=Modifier.fillMaxWidth(),isError=state.searchError!=null,enabled=!state.busy)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
            TextButton(onClick=toggleAdvanced,contentPadding=PaddingValues(horizontal=4.dp,vertical=0.dp)) {
                Text(if(state.advancedSearchOpen)"Скрыть расширенный поиск" else "Расширенный поиск",fontSize=12.sp)
            }
            if(hasDraftFilters) {
                TextButton(onClick=resetFilters,enabled=!state.busy,contentPadding=PaddingValues(horizontal=4.dp,vertical=0.dp)) {
                    Text("Очистить поля",fontSize=12.sp)
                }
            }
        }
        if(state.advancedSearchOpen) {
            OutlinedTextField(value=state.searchFrom,onValueChange=changeFrom,singleLine=true,label={Text("От кого (имя или адрес)")},modifier=Modifier.fillMaxWidth(),enabled=!state.busy)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value=state.searchStart,onValueChange=changeStart,singleLine=true,label={Text("С даты (ГГГГ-ММ-ДД)")},modifier=Modifier.weight(1f),enabled=!state.busy)
                OutlinedTextField(value=state.searchEnd,onValueChange=changeEnd,singleLine=true,label={Text("По дату (ГГГГ-ММ-ДД)")},modifier=Modifier.weight(1f),enabled=!state.busy)
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(selected=state.searchAttachments,onClick={changeAttachments(!state.searchAttachments)},enabled=!state.busy,label={Text("Только с вложениями")})
                FilterChip(selected=state.searchUnread,onClick={changeUnread(!state.searchUnread)},enabled=!state.busy,label={Text("Только непрочитанные")})
            }
        }
        Text("На сервере: отправитель, тема, текст и точные фильтры (даты по времени Москвы). На телефоне: среди сохранённых писем папки.",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        state.searchError?.let {Text(it,color=MaterialTheme.colorScheme.error,fontSize=12.sp)}
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedButton(onClick={focus.clearFocus();offline()},enabled=!state.busy,modifier=Modifier.weight(1f)){Text("На телефоне")}
            Button(onClick={focus.clearFocus();online()},enabled=!state.busy,modifier=Modifier.weight(1f)){Text("На сервере")}
            if(state.busy)TextButton(onClick=cancel){Text("Отменить")}
        }
    }
}
@Composable private fun CopyAgeText(at:Long,active:Boolean){
    val now by produceState(System.currentTimeMillis(),at,active){
        if(active&&at>0)while(true){value=System.currentTimeMillis();delay(60000)}
    }
    Text("Сохранённая почта на телефоне · "+NativeCopyAge.label(at,now),Modifier.padding(horizontal=16.dp,vertical=8.dp),fontSize=12.sp)
}
@Composable internal fun NativeFolderList(state: InboxState,close: ()->Unit,reload: ()->Unit,select: (String)->Unit) {
    NativeFolderChoices(if(state.movePicker)"Куда переместить" else "Почтовые папки",state.folder,state.folders,
        state.foldersBusy,state.foldersOnline,state.foldersError,close,reload,select,state.movePicker)
}
@Composable internal fun NativeFolderChoices(title:String,current:String,folders:List<NativeFolders.Entry>,busy:Boolean,online:Boolean,error:String?,
    close:()->Unit,reload:()->Unit,select:(String)->Unit,excludeCurrent:Boolean=false) {
        Column(Modifier.fillMaxWidth().heightIn(max=560.dp).navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                Text(title,Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                TextButton(onClick=close){Text("Закрыть")}
            }
            Text(if(online)"Список с сервера" else "Папки сохранённых писем",Modifier.padding(horizontal=16.dp,vertical=4.dp),fontSize=12.sp)
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let {Text(it,Modifier.padding(16.dp),color=MaterialTheme.colorScheme.error,fontSize=13.sp)}
            LazyColumn(Modifier.weight(1f,fill=false)) {
                items(folders,key={it.name}) {folder ->
                    TextButton(onClick={select(folder.name)},enabled=!busy&&(!excludeCurrent||folder.name!=current),modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).padding(horizontal=8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(folder.title,Modifier.fillMaxWidth(),maxLines=2,overflow=TextOverflow.Ellipsis)
                            if(folder.title!=folder.name)Text(folder.name,Modifier.fillMaxWidth(),fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if(folder.name==current)Text("Выбрана",fontSize=12.sp)
                    }
                }
            }
            OutlinedButton(onClick=reload,enabled=!busy,modifier=Modifier.fillMaxWidth().padding(16.dp)) {
                Text(if(online)"Обновить папки с сервера" else "Загрузить папки с сервера")
            }
        }
}
internal fun mailTime(raw: String): String = runCatching {
    DateTimeFormatter.ofPattern("d MMM · HH:mm", Locale.forLanguageTag("ru")).withZone(ZoneId.of("Europe/Moscow")).format(Instant.parse(raw))
}.getOrDefault("")
private fun avatarLetters(sender: String): String {
    val words=sender.substringBefore('@').replace(Regex("[._-]+")," ").trim().split(Regex("\\s+")).filter {it.isNotEmpty()}
    return words.take(2).joinToString("") {String(Character.toChars(it.codePointAt(0)))}.uppercase(Locale.forLanguageTag("ru")).ifBlank {"?"}
}
