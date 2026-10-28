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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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
import kotlin.math.abs
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

    private var notificationPermissionEpoch by mutableStateOf(0)
    private var pendingTestNotification=false
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationPermissionEpoch++
        if(!granted)pendingTestNotification=false
        if(!granted)android.widget.Toast.makeText(this,"Уведомления не разрешены. Их можно включить в настройках Android.",android.widget.Toast.LENGTH_LONG).show()
    }
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
        applyFlagSecure()
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
            val darkAppearance=LocalInboxDark.current
            LaunchedEffect(settings.state.options,resumed,darkAppearance){
                nativeAppearanceMode=settings.state.options.optString("theme","system")
                val dark=darkAppearance
                val style=if(dark)androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) else androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle=style,navigationBarStyle=style)
            }
            // Retry after asynchronous account loading, unlock and permission results, not just a theme change.
            LaunchedEffect(settings.state.ready,settings.state.owner,settings.state.options,resumed,unlockEpoch,notificationPermissionEpoch){
                if(resumed&&!AppLock.locked(this@ComposeInboxActivity)&&settings.state.ready&&settings.state.owner.isNotBlank()){
                    BackgroundMailService.reconcile(this@ComposeInboxActivity,true)
                    if(pendingTestNotification&&NoticeCenter.allowed(this@ComposeInboxActivity)){
                        pendingTestNotification=false
                        NoticeCenter.test(this@ComposeInboxActivity)
                    }
                    val options=settings.state.options
                    val prompt=getSharedPreferences("native-notification-permission",MODE_PRIVATE)
                    if(android.os.Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED&&
                        (options.optBoolean("mailNotifications",true)||options.optBoolean("taskNotifications",true))&&!prompt.getBoolean("asked",false)){
                        prompt.edit().putBoolean("asked",true).apply()
                        notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
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
            val mailScreenVisible=!exports.state.open&&extras.state.target==null&&!accounts.state.open&&(!composer.state.open||composer.state.minimized)&&!contacts.state.open&&!calendar.state.open&&!workspace.state.open&&!settings.state.open&&!files.state.open&&!drafts.state.open&&!serverDrafts.state.open&&!scheduled.state.open&&!digest.state.open&&!aiSettings.state.open&&!writingSettings.state.open&&!categories.state.open
            LaunchedEffect(mailScreenVisible){if(!mailScreenVisible)inbox.cancelFileDownload()}
            LaunchedEffect(inbox.state.openedFile,mailScreenVisible) {
                if (mailScreenVisible && inbox.state.openedFile != null) {
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
            var accountSwitcherOpen by remember { mutableStateOf(false) }
            val pending=navigation.pending
            val navigationAllowed=!files.state.open&&!serverDrafts.state.open&&!serverDrafts.state.busy&&!drafts.state.open&&!drafts.state.busy&&!scheduled.state.open&&!scheduled.state.busy&&!digest.state.open&&!aiSettings.state.open&&!aiSettings.state.busy&&!accounts.state.open&&!accounts.state.busy&&!settings.state.open&&!settings.state.busy&&!exports.state.open&&(!composer.state.open||composer.state.minimized)&&!inbox.state.busy&&!inbox.state.foldersOpen&&!inbox.state.moveOpen&&
                !workspace.state.busy&&workspace.state.editor==null&&!workspace.state.leaveOpen&&!workspace.state.deleteOpen&&
                !categories.state.busy&&categories.state.editor==null&&!categories.state.leaveOpen&&
                !extras.state.busy&&extras.state.target==null&&contacts.canExternalNavigation()&&writingSettings.canExternalNavigation()
            LaunchedEffect(inbox.state.owner,resumed,unlockEpoch){
                if(resumed&&!AppLock.locked(this@ComposeInboxActivity)&&inbox.state.owner.isNotBlank()){
                    calendar.ensureLoaded(inbox.state.owner)
                }
            }
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
            val openQuickAccountSwitcher = {
                if (!accounts.state.busy && !inbox.state.linked) {
                    accounts.load()
                    accountSwitcherOpen = true
                }
            }
            val goToMail = {
                contacts.closeForNavigation()
                calendar.close()
                workspace.closeForNavigation()
                settings.back()
                inbox.backToList()
            }
            val goToCalendar = {
                if (inbox.state.owner.isNotBlank()) {
                    contacts.closeForNavigation()
                    workspace.closeForNavigation()
                    settings.back()
                    calendar.open(inbox.state.owner)
                }
            }
            val goToTasks = {
                if (inbox.state.owner.isNotBlank()) {
                    contacts.closeForNavigation()
                    calendar.close()
                    settings.back()
                    workspace.open(inbox.state.owner)
                }
            }
            val goToContacts = {
                if (inbox.state.owner.isNotBlank()) {
                    calendar.close()
                    workspace.closeForNavigation()
                    settings.back()
                    contacts.open(inbox.state.owner)
                }
            }
            val goToSettings = {
                if (inbox.state.owner.isNotBlank()) {
                    contacts.closeForNavigation()
                    calendar.close()
                    workspace.closeForNavigation()
                    settings.open()
                } else {
                    accounts.open()
                }
            }
            val todayIso = remember { java.time.LocalDate.now(ZoneId.of("Europe/Moscow")).toString() }
            val todayEntries = remember(calendar.state.owner, inbox.state.owner, calendar.state.entries, todayIso) {
                if (calendar.state.owner == inbox.state.owner) {
                    calendar.state.entries.filter { NativeAgenda.onDay(it.data, todayIso) }
                } else emptyList()
            }
            val unreadCount = remember(inbox.state.rows) { inbox.state.rows.count { it.unread } }
            val transferBannerVisible=!AppLock.locked(this@ComposeInboxActivity)&&((composer.state.open&&composer.state.minimized)||(!exports.state.open&&exports.state.notice.isNotBlank()))
            Column(Modifier.fillMaxSize().then(if(transferBannerVisible)Modifier.statusBarsPadding().consumeWindowInsets(WindowInsets.statusBars) else Modifier)) {
                if(composer.state.open&&composer.state.minimized&&!AppLock.locked(this@ComposeInboxActivity)){
                    if(composer.state.owner==inbox.state.owner)NativeComposerTransfer(composer)
                    else Text("Операция в другом ящике. Вернитесь в него, чтобы увидеть состояние письма.",Modifier.padding(12.dp),fontSize=12.sp)
                }
                if(!AppLock.locked(this@ComposeInboxActivity)&&!exports.state.open&&exports.state.notice.isNotBlank())Surface(tonalElevation=2.dp){
                    Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically){
                        Text(exports.state.notice+if(exports.state.phase=="result")" Сохранено: ${exports.state.saved} из ${exports.state.total}; ошибок: ${exports.state.failures}." else "",Modifier.weight(1f),fontSize=12.sp)
                        if(exports.state.phase=="result")TextButton(onClick=exports::cancel){Text("Убрать")}
                    }
                }
                if(pending!=null&&!AppLock.locked(this@ComposeInboxActivity))Surface(tonalElevation=3.dp) {
                    Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
                        Text("Переход из уведомления ждёт завершения текущего действия.",Modifier.weight(1f),fontSize=12.sp)
                        TextButton(onClick={navigation.consume(pending)}){Text("Убрать")}
                    }
                }
                Box(Modifier.weight(1f)) {

            if(accounts.state.restart) Box(Modifier.fillMaxSize(),contentAlignment=androidx.compose.ui.Alignment.Center){CircularProgressIndicator()}
            else if(accounts.state.open) NativeAccountsScreen(accounts, onExit = { finish() })
            else if (composer.state.open&&!composer.state.minimized) NativeComposer(composer, fullClient) {
                composer.beginPick {
                    if (!isDestroyed && resumed) try { attachmentPicker.launch(arrayOf("*/*")) } catch (_: Exception) { composer.pickerFailed() }
                    else composer.pickerFailed()
                }
            }
            else if(inbox.state.linked) holder.SaveableStateProvider("linked-reader") {
                NativeInbox(inbox,{reply()},{reply(true)},::forwardText,{},fullClient,null,{},categoryRules=categories.state.rules.takeIf {categories.state.owner==inbox.state.owner} ?: emptyList(),
                    labels=extras.state.labels.takeIf {extras.state.owner==inbox.state.owner} ?: JSONObject(),
                    responses=extras.state.responses.takeIf {extras.state.owner==inbox.state.owner} ?: emptyList(),snoozed=extras.state.snoozed.takeIf {extras.state.owner==inbox.state.owner} ?: emptyList(),followups=extras.state.followups.takeIf {extras.state.owner==inbox.state.owner} ?: emptyList(),forwardOriginal=::forwardOriginal)
            }
            else if(files.state.open) NativeFilesScreen(files,{key->
                val source=files.mail(key)
                if(source==null||!inbox.openLinked(files.state.owner,source))files.linkError()
            },{key->files.file(key)?.let {(ref,file)->exports.begin(files.state.owner,ref,listOf(file),false)}})
            else if(serverDrafts.state.open) NativeServerDraftsScreen(serverDrafts){owner,id->composer.open(owner,restoreOnly=true,expectedId=id)}
            else if(drafts.state.open) NativeDraftsScreen(drafts,{owner,id->composer.open(owner,restoreOnly=true,expectedId=id)},{owner->composer.open(owner)},{serverDrafts.open(drafts.state.owner)})
            else if(scheduled.state.open) NativeScheduledScreen(scheduled){owner,id->composer.open(owner,restoreOnly=true,expectedId=id)}
            else if(aiSettings.state.open) NativeAiSettingsScreen(aiSettings)
            else if(digest.state.open) holder.SaveableStateProvider("mail-digest") {
                NativeDigestScreen(digest,{aiSettings.open(digest.state.owner)}){number->digest.source(number)?.let {if(!inbox.openLinked(digest.state.owner,it))digest.linkError()} ?: digest.linkError()}
            }
            else if(settings.state.open) NativeSettingsScreen(
                model = settings,
                activity = this@ComposeInboxActivity,
                accounts = { accounts.open() },
                writing = { settings.back(); writingSettings.open(inbox.state.owner) },
                ai = { aiSettings.open(inbox.state.owner) },
                bottomBar = {
                    NativeMainBottomBar(
                        activeTab = "settings",
                        enabled = inbox.state.owner.isNotBlank() && !settings.state.busy,
                        unreadCount = unreadCount,
                        todayEventsCount = todayEntries.size,
                        onMail = goToMail,
                        onCalendar = goToCalendar,
                        onTasks = goToTasks,
                        onContacts = goToContacts,
                        onMore = {}
                    )
                }
            )
            else if(writingSettings.state.open) holder.SaveableStateProvider("writing-settings") {NativeWritingSettingsScreen(writingSettings)}
            else if(extras.state.open) holder.SaveableStateProvider("reply-reminders") {
                NativeMailExtrasScreen(extras){record->extras.openResponse(record,false)?.let {if(!inbox.openLinked(extras.state.owner,it))extras.linkError()}}
            }
            else if(categories.state.open) holder.SaveableStateProvider("categories") {NativeCategoriesScreen(categories)}
            else if(workspace.state.open) holder.SaveableStateProvider(if(workspace.state.taskMail!=null||workspace.state.eventMail!=null)"mail-task" else "workspace") {
                NativeWorkspaceScreen(
                    model = workspace,
                    openCalendarDay = { day -> val owner = workspace.state.owner; workspace.leave(true); calendar.openDay(owner, day, "workspace") },
                    openMail = { id -> workspace.sourceMail(id)?.let { if (!inbox.openLinked(workspace.state.owner, it)) workspace.linkError() } ?: workspace.linkError() },
                    accountEmail = inbox.state.account,
                    onSwitchAccount = openQuickAccountSwitcher,
                    bottomBar = {
                        NativeMainBottomBar(
                            activeTab = "tasks",
                            enabled = inbox.state.owner.isNotBlank() && !workspace.state.busy,
                            unreadCount = unreadCount,
                            todayEventsCount = todayEntries.size,
                            onMail = goToMail,
                            onCalendar = goToCalendar,
                            onTasks = {},
                            onContacts = goToContacts,
                            onMore = goToSettings
                        )
                    }
                )
            }
            else if(calendar.state.open) holder.SaveableStateProvider("calendar") {
                NativeCalendarScreen(
                    model = calendar,
                    fullClient = fullClient,
                    editEvent = { day, id -> workspace.openEvent(calendar.state.owner, day, id) },
                    newTask = { day -> workspace.openCalendarNewTask(calendar.state.owner,day) },
                    tasksEnabled = settings.state.ready&&settings.state.owner==calendar.state.owner&&settings.state.options.optBoolean("showTasks",true),
                    openMail = { id ->
                        calendar.sourceMail(id)?.let { if (!inbox.openLinked(calendar.state.owner, it)) calendar.linkError() } ?: calendar.linkError()
                    },
                    createTask = { event ->
                        val owner = calendar.state.owner
                        calendar.close()
                        workspace.openCalendarTask(owner, event)
                    },
                    accountEmail = inbox.state.account,
                    onSwitchAccount = openQuickAccountSwitcher,
                    bottomBar = {
                        NativeMainBottomBar(
                            activeTab = "calendar",
                            enabled = inbox.state.owner.isNotBlank() && !calendar.state.busy,
                            unreadCount = unreadCount,
                            todayEventsCount = todayEntries.size,
                            onMail = goToMail,
                            onCalendar = {},
                            onTasks = goToTasks,
                            onContacts = goToContacts,
                            onMore = goToSettings
                        )
                    }
                )
            }
            else if(contacts.state.open) NativeContactsScreen(
                model = contacts,
                write = { email ->
                    contacts.person(email)?.let { composer.open(contacts.state.owner, recipient = it.email) }
                },
                fullClient = fullClient,
                onSwitchAccount = openQuickAccountSwitcher,
                bottomBar = {
                    NativeMainBottomBar(
                        activeTab = "contacts",
                        enabled = inbox.state.owner.isNotBlank() && !contacts.state.busy,
                        unreadCount = unreadCount,
                        todayEventsCount = todayEntries.size,
                        onMail = goToMail,
                        onCalendar = goToCalendar,
                        onTasks = goToTasks,
                        onContacts = {},
                        onMore = goToSettings
                    )
                }
            )
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
                        openFiles={if(navigationAllowed)files.open(inbox.state.owner,inbox.state.folder)},forwardOriginal=::forwardOriginal,saveFiles={owner,ref,files,original->inbox.readerAction(owner,ref){if(original||files.isNotEmpty()&&files.all {inbox.state.body?.files?.contains(it)==true})exports.begin(owner,ref,files,original)}},
                        onSwitchAccount=openQuickAccountSwitcher,
                        savedAccountsCount=accounts.state.rows.size,
                        todayAgendaEntries=todayEntries,
                        calendarSourcesCount=if(calendar.state.owner==inbox.state.owner&&calendar.state.notice!=null) calendar.state.sources.size else -1,
                        onConnectCalendar={calendar.open(inbox.state.owner);calendar.connectAccountCalendar()}) }
            }
                }
            }
            if (accountSwitcherOpen) {
                NativeAccountSwitcherDialog(
                    accounts = accounts,
                    activeAccountEmail = inbox.state.account,
                    onDismiss = { accountSwitcherOpen = false }
                )
            }
            NativeMailEventPickerDialog(calendar)
            NativeMailExtrasDialogs(extras)
            NativeExportDialog(exports)
        } }
    }
    internal fun testNotifications(){
        if(!resumed||AppLock.locked(this))return
        if(android.os.Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED){
            pendingTestNotification=true
            getSharedPreferences("native-notification-permission",MODE_PRIVATE).edit().putBoolean("asked",true).apply()
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }else if(NoticeCenter.allowed(this))NoticeCenter.test(this)
        else android.widget.Toast.makeText(this,"Разрешите уведомления в настройках Android.",android.widget.Toast.LENGTH_LONG).show()
    }
    override fun getNativeAppearanceMode():String = nativeAppearanceMode
    private fun applyFlagSecure() {
        if (AppLock.enabled(this)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
    override fun onResume() {
        super.onResume(); resumed = true; applyFlagSecure(); files.resume();serverDrafts.resume();drafts.resume();scheduled.resume();digest.resume();aiSettings.resume();accounts.resume();settings.resume();composer.resume()
        restoreComposer()
        exports.resume();inbox.resume();contacts.resume();calendar.resume();workspace.resume();categories.resume();extras.resume();writingSettings.resume()
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
        applyFlagSecure()
        if(resumed&&!AppLock.locked(this)){files.resume();serverDrafts.resume();drafts.resume();scheduled.resume();digest.resume();aiSettings.resume();accounts.resume();settings.resume();composer.resume();restoreComposer();exports.resume();inbox.resume();contacts.resume();calendar.resume();workspace.resume();categories.resume();extras.resume();writingSettings.resume()}
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
        contacts.closeForNavigation();writingSettings.closeForNavigation();calendar.close();workspace.closeForNavigation();categories.back();extras.back()
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
    override fun onPause() { files.pause();serverDrafts.pause();drafts.pause();scheduled.pause();digest.pause();aiSettings.pause();accounts.pause();settings.pause();exports.pause(isChangingConfigurations);composer.pause();contacts.pause();calendar.pause();workspace.pause();categories.pause();extras.pause();writingSettings.pause(); resumed = false; inbox.pause(isChangingConfigurations); super.onPause() }
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
        AttachmentOpen.open(this,file,name,mime,settings.state.options.optString("theme","system"))
    }

}

internal fun mailSource(state: InboxState,mail: NativeMail):JSONObject=JSONObject().put("account",state.owner)
    .put("folder",mail.ref.folder).put("uid",mail.ref.uid).put("validity",mail.ref.validity)
    .put("subject",mail.subject).put("sender",mail.sender).put("date",mail.date)

internal val LocalCompactInbox=staticCompositionLocalOf {false}
@Composable internal fun InboxTheme(mode:String="system",large:Boolean=false,compact:Boolean=false,content: @Composable () -> Unit) {
    val isDark = AppearancePolicy.dark(mode,isSystemInDarkTheme())
    val scheme = if (isDark) darkColorScheme(
        primary = Color(0xFF7C5CFF),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFF231E3D),
        onPrimaryContainer = Color(0xFFE0D7FF),
        secondary = Color(0xFF8B5CF6),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFF1C1F2A),
        onSecondaryContainer = Color(0xFFE2E8F0),
        background = Color(0xFF0B0C10),
        onBackground = Color(0xFFF3F4F8),
        surface = Color(0xFF14161D),
        onSurface = Color(0xFFF3F4F8),
        surfaceVariant = Color(0xFF1C1F2A),
        onSurfaceVariant = Color(0xFF8E929F),
        outline = Color(0xFF282C3E),
        outlineVariant = Color(0x0FFFFFFF),
        error = Color(0xFFF87171),
        onError = Color(0xFF450A0A),
        errorContainer = Color(0xFF2C1519),
        onErrorContainer = Color(0xFFFECACA)
    ) else lightColorScheme(
        primary = Color(0xFF6D4AFF),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFEDE8FF),
        onPrimaryContainer = Color(0xFF1F1147),
        secondary = Color(0xFF5B5670),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFEAE7F5),
        onSecondaryContainer = Color(0xFF1E1B2B),
        background = Color(0xFFF7F8FA),
        onBackground = Color(0xFF12141C),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF12141C),
        surfaceVariant = Color(0xFFEEF0F6),
        onSurfaceVariant = Color(0xFF64697A),
        outline = Color(0xFFD5D8E2),
        outlineVariant = Color(0xFFE6E8F0),
        error = Color(0xFFDC2626),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFEE2E2),
        onErrorContainer = Color(0xFF450A0A)
    )
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(14.dp),
        large = RoundedCornerShape(18.dp),
        extraLarge = RoundedCornerShape(24.dp)
    )
    val density=androidx.compose.ui.platform.LocalDensity.current
    CompositionLocalProvider(LocalInboxDark provides isDark,LocalCompactInbox provides compact,androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density,density.fontScale*(if(large)1.15f else 1f))) {
        MaterialTheme(colorScheme = scheme, shapes = shapes) {
            Surface(Modifier.fillMaxSize(),color=scheme.background,contentColor=scheme.onBackground) { content() }
        }
    }
}
@Composable internal fun NativeInbox(
    model: NativeInboxModel,
    reply: () -> Unit,
    replyAll: () -> Unit,
    forwardText: () -> Unit,
    compose: () -> Unit,
    fullClient: () -> Unit,
    notice: String?,
    dismissNotice: () -> Unit,
    openContacts: ()->Unit = {},
    openCalendar: ()->Unit = {},
    openWorkspace: ()->Unit = {},
    createTask: ()->Unit = {},
    createEvent: ()->Unit = {},
    linkMailToEvent: ()->Unit = {},
    categoryRules: List<JSONObject> = emptyList(),
    categoryFilter: String="",
    selectCategory: (String)->Unit = {},
    openCategories: ()->Unit = {},
    labels: JSONObject=JSONObject(),
    responses: List<JSONObject> = emptyList(),
    setLabel: ()->Unit = {},
    setResponse: ()->Unit = {},
    openResponses: ()->Unit = {},
    extrasReady: Boolean=true,
    snoozed: List<JSONObject> = emptyList(),
    setSnooze: ()->Unit = {},
    setFollowup: ()->Unit = {},
    followups: List<JSONObject> = emptyList(),
    openWritingSettings: ()->Unit = {},
    openAccounts: ()->Unit = {},
    openSettings: ()->Unit = {},
    digestEnabled:Boolean=false,
    openDigest: ()->Unit = {},
    openScheduled: ()->Unit = {},
    openDrafts: ()->Unit = {},
    saveFiles: (String,MailRef,List<NativeFile>,Boolean)->Unit = {_,_,_,_->},
    forwardOriginal:()->Unit = {},
    openFiles:()->Unit = {},
    onSwitchAccount: ()->Unit = openAccounts,
    savedAccountsCount: Int = 1,
    todayAgendaEntries: List<CalendarEntry> = emptyList(),
    calendarSourcesCount: Int = -1,
    onConnectCalendar: ()->Unit = openCalendar
) {
    val context = LocalContext.current
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
    var readerDetailsExpanded by remember(state.owner,state.selected?.ref) {mutableStateOf(false)}
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
    val unreadTotal=remember(loadedRows){loadedRows.count {it.unread}}
    val visibleRefs=remember(displayRows){displayRows.map {when(it){is InboxDisplayRow.Single->it.mail.ref;is InboxDisplayRow.Group->(it.members.firstOrNull { m -> eligible(m) } ?: it.members.first()).ref}}.toSet()}
    val activeSelection=state.activeSelection(visibleRefs)
    BackHandler(state.selected == null && state.thread.anchor == null && activeSelection.isNotEmpty()) { model.clearSelection() }
    BackHandler(state.selected != null || state.thread.anchor != null) { model.backToList() }
    @Composable fun MoreDropdown() {
        DropdownMenu(expanded=moreScreens,onDismissRequest={moreScreens=false}) {
            DropdownMenuItem(text={Text("Настройки")},enabled=state.owner.isNotBlank()&&!state.linked&&!state.busy,onClick={moreScreens=false;openSettings()})
            DropdownMenuItem(text={Text(if(state.threadsEnabled)"Группировать в цепочки: Вкл" else "Группировать в цепочки: Выкл")},enabled=state.owner.isNotBlank()&&!state.busy,onClick={moreScreens=false;model.toggleThreads()})
            DropdownMenuItem(text={Text("Задачи и заметки")},enabled=state.owner.isNotBlank()&&!state.linked&&!state.busy,onClick={moreScreens=false;openWorkspace()})
            DropdownMenuItem(text={Text("Сменить аккаунт")},enabled=!state.linked&&!state.busy,onClick={moreScreens=false;onSwitchAccount()})
            DropdownMenuItem(text={Text("Черновики")},enabled=state.selected==null&&!state.busy&&!state.linked,onClick={moreScreens=false;openDrafts()})
            DropdownMenuItem(text={Text("Файлы писем")},enabled=state.selected==null&&!state.busy&&!state.linked,onClick={moreScreens=false;openFiles()})
            DropdownMenuItem(text={Text("По расписанию")},enabled=state.selected==null&&!state.busy&&!state.linked,onClick={moreScreens=false;openScheduled()})
            if(digestEnabled)DropdownMenuItem(text={Text("Сводка почты")},enabled=state.selected==null&&!state.busy&&!state.linked,onClick={moreScreens=false;openDigest()})
            DropdownMenuItem(text={Text("Письма на потом")},enabled=state.owner.isNotBlank()&&!state.linked&&!state.busy&&extrasReady,onClick={moreScreens=false;openResponses()})
            DropdownMenuItem(text={Text("Шаблоны и подпись")},enabled=state.owner.isNotBlank()&&!state.linked&&!state.busy,onClick={moreScreens=false;openWritingSettings()})
            DropdownMenuItem(text={Text("Категории и правила")},enabled=state.owner.isNotBlank()&&!state.linked&&!state.busy,onClick={moreScreens=false;openCategories()})
            DropdownMenuItem(text={Text("Аккаунты и SMTP")},enabled=!state.linked&&!state.busy,onClick={moreScreens=false;openAccounts()})
        }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    title = {
                        if(state.selected==null&&state.thread.anchor==null) {
                            Column {
                                Row(
                                    verticalAlignment=Alignment.CenterVertically,
                                    horizontalArrangement=Arrangement.spacedBy(6.dp),
                                    modifier=Modifier.clickable(enabled=state.owner.isNotBlank()&&!state.busy,onClick=model::showFolders)
                                ) {
                                    Text(
                                        NativeFolders.label(state.folder),
                                        Modifier.weight(1f,fill=false),
                                        maxLines=1,
                                        overflow=TextOverflow.Ellipsis,
                                        fontSize=22.sp,
                                        fontWeight=FontWeight.Bold,
                                        color=MaterialTheme.colorScheme.onSurface
                                    )
                                    Icon(
                                        NativeIcons.ChevronDown,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Row(
                                    verticalAlignment=Alignment.CenterVertically,
                                    horizontalArrangement=Arrangement.spacedBy(6.dp),
                                    modifier=Modifier.padding(top=1.dp)
                                ) {
                                    Text(
                                        if(state.account.isNotBlank()) "${state.account} ▾" else "Войти в аккаунт ▾",
                                        fontSize=12.sp,
                                        fontWeight=FontWeight.Medium,
                                        maxLines=1,
                                        overflow=TextOverflow.Ellipsis,
                                        color=MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier=Modifier.clickable(enabled=!state.busy&&!state.linked,onClick=onSwitchAccount)
                                    )
                                    if(state.cached && state.searchKey.isEmpty()) {
                                        Icon(
                                            NativeIcons.Cloud,
                                            contentDescription = "Офлайн-кэш",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    if(state.selected!=null) NativeFolders.label(state.folder) else "Цепочка",
                                    maxLines=1,
                                    fontWeight=FontWeight.SemiBold,
                                    fontSize=17.sp,
                                    color=MaterialTheme.colorScheme.onSurface
                                )
                                if(state.cached) {
                                    Icon(
                                        NativeIcons.Cloud,
                                        contentDescription = "Офлайн-кэш",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        if (state.selected != null||state.thread.anchor!=null) {
                            IconButton(onClick = model::backToList) {
                                Icon(NativeIcons.ArrowBack, contentDescription = if(state.selected==null)"К списку" else "Назад", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
                            }
                        }
                    },
                    actions = {
                        if (state.selected == null&&state.thread.anchor==null) {
                            GlassIconButton(
                                icon = NativeIcons.Search,
                                contentDescription = "Поиск",
                                onClick = model::toggleSearch,
                                enabled = state.owner.isNotBlank(),
                                active = state.searchOpen || state.searchKey.isNotEmpty(),
                                size = 36.dp
                            )
                            Spacer(Modifier.width(6.dp))
                            GlassIconButton(
                                icon = NativeIcons.Refresh,
                                contentDescription = "Обновить",
                                onClick = { model.loadInbox(true) },
                                enabled = !state.busy,
                                size = 36.dp
                            )
                            Spacer(Modifier.width(6.dp))
                            Box(Modifier.padding(end = 8.dp).semantics { contentDescription = "Сменить аккаунт" }) {
                                GlassSphereAvatar(
                                    seed = state.account.ifBlank { "SPbU" },
                                    size = 34.dp,
                                    enabled = !state.busy && !state.linked,
                                    onClick = onSwitchAccount
                                )
                            }
                        } else if (state.selected != null) {
                            val mail = state.selected
                            IconButton(
                                onClick = reply,
                                enabled = state.body != null && !state.busy
                            ) {
                                Icon(NativeIcons.Reply, contentDescription = "Ответить", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                            }
                            if(!state.linked) {
                                val moveEnabled=!state.busy&&!state.cached&&!state.moveNeedsRefresh
                                IconButton(
                                    onClick={model.requestMove("trash")},
                                    enabled=moveEnabled
                                ) {
                                    Icon(NativeIcons.Trash, contentDescription = "В корзину", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                }
                            }
                            Box {
                                IconButton(
                                    onClick = { readerActions = true },
                                    enabled = !state.busy,
                                    modifier = Modifier.semantics { contentDescription = "Другие действия письма" }
                                ) {
                                    Icon(NativeIcons.MoreHorizontal, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
                                }
                                DropdownMenu(expanded=readerActions,onDismissRequest={readerActions=false}) {
                                    DropdownMenuItem(
                                        text={Text(if(readerDetailsExpanded) "Скрыть сведения" else "Сведения о письме")},
                                        onClick={readerActions=false;readerDetailsExpanded=!readerDetailsExpanded}
                                    )
                                    DropdownMenuItem(
                                        text={Text("Обновить письмо")},
                                        enabled=!state.busy,
                                        onClick={readerActions=false;mail?.let {model.openMail(it,keepPosition=true,forceOnline=true)}}
                                    )
                                    DropdownMenuItem(
                                        text={Text("Сохранить оригинал .eml")},
                                        enabled=!state.busy&&mail!=null,
                                        onClick={readerActions=false;mail?.let {saveFiles(state.owner,it.ref,emptyList(),true)}}
                                    )
                                    DropdownMenuItem(
                                        text={Text("Поделиться текстом")},
                                        enabled=!state.busy&&state.body!=null,
                                        onClick={
                                            readerActions=false
                                            val text = state.body?.text ?: ""
                                            val subject = mail?.subject ?: ""
                                            try {
                                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                                                    .putExtra(Intent.EXTRA_SUBJECT, subject).putExtra(Intent.EXTRA_TEXT, text.take(100000)), "Поделиться текстом"))
                                            } catch (_: Exception) {}
                                        }
                                    )
                                    DropdownMenuItem(text={Text(if(mail?.unread==true)"Пометить прочитанным" else "Пометить непрочитанным")},
                                        enabled=!state.busy,onClick={readerActions=false;mail?.let {model.markSeen(it.unread)}})
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
                                        DropdownMenuItem(text={Text("В архив")},enabled=moveEnabled,onClick={readerActions=false;model.requestMove("archive")})
                                        DropdownMenuItem(text={Text("В спам")},enabled=moveEnabled,onClick={readerActions=false;model.requestMove("spam")})
                                        DropdownMenuItem(text={Text("В корзину")},enabled=moveEnabled,onClick={readerActions=false;model.requestMove("trash")})
                                        DropdownMenuItem(text={Text("Переместить в папку…")},enabled=moveEnabled,onClick={readerActions=false;model.showMoveFolders()})
                                    }
                                    DropdownMenuItem(text={Text("Переслать текст")},enabled=state.body!=null&&!state.busy,
                                        onClick={readerActions=false;forwardText()})
                                    DropdownMenuItem(text={Text("Переслать оригинал EML")},enabled=mail!=null&&!state.busy,onClick={readerActions=false;forwardOriginal()})
                                }
                            }
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (state.selected == null && state.thread.anchor == null && state.owner.isNotBlank() && !state.searchOpen) {
                GlowingCapsuleFab(
                    label = "Написать",
                    icon = NativeIcons.Quill,
                    onClick = { if (!state.busy) compose() },
                    enabled = !state.busy
                )
            }
        },
        bottomBar = {
            if (state.selected != null) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Column {
                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                onClick = reply,
                                enabled = state.body != null && !state.busy,
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f).height(42.dp)
                            ) {
                                Row(
                                    Modifier.fillMaxSize().padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(NativeIcons.Reply, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Ответить", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1)
                                }
                            }
                            Surface(
                                onClick = replyAll,
                                enabled = state.body != null && !state.busy,
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.weight(1f).height(42.dp)
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Ответить всем", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                                }
                            }
                            Surface(
                                onClick = forwardText,
                                enabled = state.body != null && !state.busy,
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.weight(0.9f).height(42.dp)
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Переслать", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            } else {
                NativeMainBottomBar(
                    activeTab = "mail",
                    enabled = state.owner.isNotBlank() && !state.busy,
                    unreadCount = unreadTotal,
                    todayEventsCount = todayAgendaEntries.size,
                    onMail = {},
                    onCalendar = openCalendar,
                    onTasks = openWorkspace,
                    onContacts = openContacts,
                    onMore = if (!state.busy) ({ moreScreens = true }) else null,
                    moreContent = { MoreDropdown() }
                )
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            CosmicHeaderGlow()
            Column(Modifier.fillMaxSize().padding(padding)) {
                notice?.let {
                    Surface(
                        modifier=Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=4.dp),
                        shape=RoundedCornerShape(12.dp),
                        color=MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
                            Text(it,Modifier.weight(1f),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurface)
                            IconButton(onClick=dismissNotice,modifier=Modifier.size(26.dp)){
                                Icon(NativeIcons.Close, contentDescription="Закрыть", tint=MaterialTheme.colorScheme.onSurfaceVariant, modifier=Modifier.size(14.dp))
                            }
                        }
                    }
                }
                if(state.selected==null&&state.thread.anchor==null&&state.owner.isNotBlank()) {
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
                        model::cancelSearch,
                        {model.search(false)},
                        {model.search(true)}
                    )
                    if(state.searchKey.isNotEmpty() && !state.searchOpen) {
                        Surface(
                            modifier=Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=4.dp),
                            shape=RoundedCornerShape(12.dp),
                            color=MaterialTheme.colorScheme.surface
                        ) {
                            Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                                Icon(NativeIcons.Search, contentDescription=null, modifier=Modifier.size(15.dp), tint=MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if(state.query.isNotEmpty()) "Поиск: «${state.query}»" else "Фильтр поиска",
                                    fontWeight=FontWeight.Medium,
                                    fontSize=13.sp,
                                    color=MaterialTheme.colorScheme.onSurface,
                                    modifier=Modifier.weight(1f)
                                )
                                IconButton(onClick={model.clearSearch()},enabled=!state.busy,modifier=Modifier.size(28.dp)) {
                                    Icon(NativeIcons.Close, contentDescription="Сбросить", tint=MaterialTheme.colorScheme.onSurfaceVariant, modifier=Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                    // Borderless sleek filter chips (LazyRow with toggle deselect & unread badge)
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(listOf("all" to "Все", "unread" to "Непрочитанные", "attachments" to "С вложениями")) { (value, label) ->
                            val active = state.filter == value
                            val chipLabel = if (value == "unread" && unreadTotal > 0) "Непрочитанные ($unreadTotal)" else label
                            Surface(
                                onClick = {
                                    if (value == "all") {
                                        model.filter("all")
                                        selectCategory("")
                                    } else {
                                        model.filter(if (active) "all" else value)
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = if (active) BorderStroke(0.8.dp, Color(0x888B5CF6)) else null
                            ) {
                                Text(
                                    chipLabel,
                                    Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                                    fontSize = 12.sp,
                                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                        if (categoryRules.isNotEmpty()) {
                            val enabledRules = categoryRules.filter { it.optBoolean("enabled", true) }
                            items(enabledRules) { rule ->
                                val ruleId = rule.optString("id")
                                val active = categoryFilter == ruleId
                                val c = categoryColor(rule.optString("color"))
                                Surface(
                                    onClick = { selectCategory(if (active) "" else ruleId) },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (active) c.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    border = if (active) BorderStroke(0.8.dp, c.copy(alpha = 0.6f)) else null
                                ) {
                                    Text(
                                        rule.optString("name"),
                                        Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                        fontSize = 12.sp,
                                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                                        color = if (active) c else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                    if(activeSelection.isNotEmpty()) {
                        Surface(
                            modifier=Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=4.dp),
                            shape=RoundedCornerShape(12.dp),
                            color=MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                Text("Выбрано: ${activeSelection.size}",fontWeight=FontWeight.SemiBold,fontSize=13.sp,color=MaterialTheme.colorScheme.onSurface,modifier=Modifier.weight(1f))
                                TextButton(onClick={model.batchMarkSeen(true,visibleRefs)},enabled=!state.busy,contentPadding=PaddingValues(horizontal=8.dp,vertical=0.dp)) {
                                    Text("Прочитано",fontSize=12.sp,color=MaterialTheme.colorScheme.primary)
                                }
                                IconButton(onClick=model::clearSelection,enabled=!state.busy,modifier=Modifier.size(28.dp)) {
                                    Icon(NativeIcons.Close, contentDescription="Снять выбор", tint=MaterialTheme.colorScheme.onSurfaceVariant, modifier=Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
                if(state.fileTransfer.isNotBlank())NativeTransferCard(state.fileTransfer,state.fileStage,state.fileBytes,state.fileTotal,cancel=model::cancelFileDownload)
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
                state.error?.let {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(it, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 13.sp)
                    }
                }
                state.flagNotice?.let {Text(it,Modifier.padding(horizontal=16.dp,vertical=4.dp),fontSize=12.sp,color=MaterialTheme.colorScheme.primary)}
                val mail = state.selected
                if (mail == null && state.thread.anchor != null) {
                    Box(Modifier.weight(1f)) {
                        ThreadConversation(state,model,displayRows,threadScroll,scope,state.owner,labels,responses,snoozed,followups,::category)
                    }
                } else if (mail == null) LazyColumn(
                    state = position,
                    contentPadding = PaddingValues(top = 4.dp, bottom = 88.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (visible.isEmpty() && !state.busy && state.error == null) item {
                        ZeroInbox3DScene(
                            title = if (state.owner.isBlank()) "Нет подключённого ящика"
                            else if (state.filter != "all" || categoryFilter.isNotEmpty()) "Нет писем по фильтру"
                            else if (state.searchKey.isNotEmpty()) "Ничего не найдено"
                            else "Zero Inbox",
                            subtitle = if (state.owner.isBlank()) "Нажмите «Войти в аккаунт ▾» сверху, чтобы подключить почту"
                            else if (state.filter != "all" || categoryFilter.isNotEmpty()) "Все письма в этой категории обработаны"
                            else "Все входящие разобраны"
                        )
                    }
                    items(displayRows, key = { it.key }) { item ->
                        val row = when(item) {
                            is InboxDisplayRow.Single -> item.mail
                            is InboxDisplayRow.Group -> item.members.firstOrNull { eligible(it) } ?: item.members.first()
                        }
                        val rule = category(row)
                        val isSelected = row.ref in activeSelection
                        val onOpen = {
                            if (activeSelection.isNotEmpty()) model.toggleSelection(row.ref)
                            else if (item is InboxDisplayRow.Group) model.openThread(item.anchor, scope)
                            else model.openMail(row)
                        }
                        MailRow(
                            row, !state.busy, onOpen, state.owner, labels, responses, snoozed, followups,
                            rule?.optString("name")?.takeIf { it.isNotBlank() }, rule?.let { categoryColor(it.optString("color")) },
                            if (item is InboxDisplayRow.Group) summaryOf(item.members) else null,
                            selected = isSelected,
                            onToggleSelect = { model.toggleSelection(row.ref) },
                            onToggleSeen = if (item is InboxDisplayRow.Single && activeSelection.isEmpty()) ({ model.toggleSeenFromList(row) }) else null
                        )
                    }
                    if (state.hasMore) item {
                        LaunchedEffect(displayRows.size, state.hasMore, state.busy, state.pageExpired, state.filter, categoryFilter) {
                            if (!state.busy && !state.pageExpired && (displayRows.isNotEmpty() || state.filter != "all" || categoryFilter.isNotEmpty())) {
                                model.loadMore()
                            }
                        }
                        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                            Surface(
                                onClick = { if(state.pageExpired) model.loadInbox(!state.pageOffline) else model.loadMore() },
                                enabled = !state.busy,
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Text(
                                    if(state.pageExpired) "Обновить" else if(state.busy) "Загрузка…" else "Загрузить ещё",
                                    Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else key(mail.ref,state.body != null) {
                    val readerPosition=rememberScrollState(initial=model.readerOffset)
                    LaunchedEffect(readerPosition,state.owner,mail.ref,state.body != null) {
                        if(state.body != null) snapshotFlow { readerPosition.value }.collect { model.readerPosition(state.owner,mail.ref,it) }
                    }
                    Column(Modifier.weight(1f).verticalScroll(readerPosition).padding(horizontal=16.dp,vertical=8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            category(mail)?.let { rule ->
                                val catName = rule.optString("name")
                                if (catName.isNotBlank()) {
                                    val c = categoryColor(rule.optString("color"))
                                    Surface(shape = RoundedCornerShape(6.dp), color = c.copy(alpha = 0.18f)) {
                                        Text(catName, Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = c)
                                    }
                                }
                            }
                            MailLocalBadges(mail,state.owner,labels,responses,snoozed,followups)
                        }
                        // Subject
                        Text(
                            mail.subject,
                            fontSize = 20.sp,
                            lineHeight = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        // Sender card
                        NativeReaderHeader(state.owner, mail, state.body?.message, externalExpanded = readerDetailsExpanded)
                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 4.dp))

                        if (state.body == null && !state.busy) {
                            Surface(
                                onClick = { model.openMail(mail) },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Text("Загрузить письмо", Modifier.padding(horizontal = 14.dp, vertical = 8.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }

                        // Attachments are available immediately below sender, before a potentially long body.
                        state.body?.let { body ->
                            // Full-width attachment cards so filenames are never truncated
                            if (body.files.isNotEmpty()) {
                                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                                    Text("Вложения · ${body.files.size}",modifier=Modifier.weight(1f),style=MaterialTheme.typography.titleSmall)
                                    TextButton(onClick={saveFiles(state.owner,mail.ref,body.files,false)},enabled=!state.busy){Text("Сохранить все")}
                                }
                                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 6.dp))
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    body.files.forEach { file ->
                                        val ext = file.name.substringAfterLast('.', "").uppercase(Locale.ROOT).take(4).ifBlank { "FILE" }
                                        val badgePal = NativeIcons.fileBadgePalette(ext)
                                        Surface(
                                            onClick = { if (!state.busy && file.path.isNotBlank()) model.fileAction(state.owner, mail.ref, file) },
                                            shape = RoundedCornerShape(14.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.outlineVariant),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = badgePal.container,
                                                    modifier = Modifier.width(42.dp).height(34.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text(
                                                            ext,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = badgePal.content
                                                        )
                                                    }
                                                }
                                                Column(Modifier.weight(1f)) {
                                                    Text(
                                                        file.name,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        ext,
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { saveFiles(state.owner, mail.ref, listOf(file), false) },
                                                    enabled = !state.busy && file.path.isNotBlank(),
                                                    modifier = Modifier.size(32.dp).semantics { contentDescription = "Сохранить ${file.name}" }
                                                ) {
                                                    Icon(
                                                        NativeIcons.Download,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp),
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            androidx.compose.foundation.text.selection.SelectionContainer {
                                Text(
                                    body.text.ifBlank { "Текстовая часть отсутствует." },
                                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                    fontSize = 15.sp,
                                    lineHeight = 23.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            if(!state.linked) {
                                val hint=remember(mail.ref,mail.subject,mail.date){NativeMailEvents.hint(mailSource(state,mail),"",java.time.Instant.now())}
                                hint?.let {
                                    Surface(
                                        onClick = createEvent,
                                        enabled = !state.busy,
                                        modifier = Modifier.padding(top = 6.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                                    ) {
                                        Row(
                                            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(NativeIcons.Calendar, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                                            Text(formatHumanDateTime(it.date, it.time, includeYear = true), fontWeight = FontWeight.Medium, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                            Text("Добавить в календарь", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }

                            NativeReaderActions(model, mail, body.text)
                        }
                    }
                }
            }
        }
    }
    if(state.moveOpen)AlertDialog(onDismissRequest=model::cancelMove,
        title={Text(when(state.moveKind){"archive"->"Переместить в архив?";"spam"->"Переместить в спам?";"trash"->"Переместить в корзину?";else->"Переместить письмо?"})},
        text={Column {Text(state.selected?.subject ?: "");if(state.moveKind=="folder")Text("Папка: ${state.moveDestination}")}},
        confirmButton={TextButton(onClick=model::confirmMove){Text("Переместить")}},
        dismissButton={TextButton(onClick=model::cancelMove){Text("Отмена")}})
    if(state.foldersOpen) ModalBottomSheet(onDismissRequest=model::closeFolders,containerColor=MaterialTheme.colorScheme.surface,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        NativeFolderList(state,model::closeFolders,{model.loadFolders(true)},model::chooseFolder)
    }
}
@Composable internal fun MailLocalBadges(mail: NativeMail,owner: String,labels: JSONObject,responses: List<JSONObject>,snoozed: List<JSONObject> = emptyList(),followups: List<JSONObject> = emptyList()) {
    val ref=JSONObject().put("folder",mail.ref.folder).put("uid",mail.ref.uid).put("validity",mail.ref.validity)
    val key=NativeMailEvents.key(ref)
    val label=NativeMailExtras.labelName(labels.optString(key))
    if(label.isNotEmpty())Text(label,Modifier.padding(top=4.dp),fontSize=11.sp,fontWeight=FontWeight.Medium,color=MaterialTheme.colorScheme.primary)
    val response=responses.find {runCatching {NativeMailExtras.key(owner,it.getJSONObject("mail"))==key}.getOrDefault(false)}
    response?.let {Text("Ответить · "+responseTime(it.optLong("at")),Modifier.padding(top=4.dp),fontSize=11.sp,fontWeight=FontWeight.Medium,color=MaterialTheme.colorScheme.primary)}
    for((rows,badgeLabel) in listOf(snoozed to "Отложено до",followups to "Проверить ответ")) {
        rows.find {runCatching {NativeMailExtras.key(owner,it.getJSONObject("mail"))==key}.getOrDefault(false)}?.let {
            Text(badgeLabel+" · "+responseTime(it.optLong("at")),Modifier.padding(top=4.dp),fontSize=11.sp,fontWeight=FontWeight.Medium,color=MaterialTheme.colorScheme.primary)
        }
    }
}

private val aiTimeRegex = Regex("\\b([01]?\\d|2[0-3]):[0-5]\\d\\b")

internal fun extractAiBadgeLabel(mail: NativeMail): String? {
    if (!mail.unread) return null
    val combined = "${mail.subject} ${mail.preview}"
    val extractedTime = aiTimeRegex.find(mail.subject)?.value
        ?: aiTimeRegex.find(mail.preview)?.value
    val isUrgentCue = mail.subject.contains("срочн", ignoreCase = true) ||
        mail.subject.contains("защит", ignoreCase = true) ||
        mail.subject.contains("дедлайн", ignoreCase = true) ||
        mail.subject.contains("согласован", ignoreCase = true) ||
        combined.contains("срочн", ignoreCase = true)
    if (!isUrgentCue && extractedTime == null) return null
    return if (extractedTime != null) "AI: Срочно • $extractedTime" else "AI: Срочно"
}

private data class ThreadSummary(val count: Int, val unread: Int, val attachments: Boolean)
private fun summaryOf(members: List<NativeMail>)=ThreadSummary(members.size,members.count {it.unread},members.any {it.hasAttachments==true})

@Composable private fun MailRowBody(
    mail: NativeMail,
    rowColor: Color,
    chain: ThreadSummary?,
    owner: String,
    labels: JSONObject,
    responses: List<JSONObject>,
    snoozed: List<JSONObject>,
    followups: List<JSONObject>,
    categoryName: String?,
    categoryTint: Color?,
    selected: Boolean = false,
    onToggleSelect: (() -> Unit)? = null,
    onToggleSeen: (() -> Unit)? = null,
    enabled: Boolean = true
) {
    val hasAttachments = mail.hasAttachments == true || chain?.attachments == true
    val validCategory = categoryName?.takeIf { it.isNotBlank() }
    val hasThreadBadge = chain != null && chain.count > 1

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = if (LocalCompactInbox.current) 10.dp else 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = if (onToggleSelect != null) {
                Modifier.semantics { contentDescription = if (selected) "Снять выбор письма" else "Выбрать письмо" }
            } else {
                Modifier.clearAndSetSemantics {}
            }
        ) {
            GlassSphereAvatar(
                seed = mail.email.ifBlank { mail.sender },
                size = 44.dp,
                selected = selected,
                enabled = enabled,
                onClick = onToggleSelect
            )
        }

        Column(Modifier.weight(1f)) {
            // Top Row: Sender + [Paperclip icon · Date · Unread dot]
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    mail.sender,
                    Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 15.sp,
                    fontWeight = if (mail.unread) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (mail.unread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    Modifier.padding(start = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    if (hasAttachments) {
                        Icon(
                            NativeIcons.Paperclip,
                            contentDescription = "Есть вложения",
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        mailTime(mail.date),
                        maxLines = 1,
                        fontSize = 12.sp,
                        fontWeight = if (mail.unread) FontWeight.Medium else FontWeight.Normal,
                        color = if (mail.unread) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (onToggleSeen != null) {
                        Surface(
                            onClick = onToggleSeen,
                            enabled = enabled,
                            shape = CircleShape,
                            color = Color.Transparent,
                            modifier = Modifier.size(18.dp).semantics {
                                contentDescription = if (mail.unread) "Пометить прочитанным" else "Пометить непрочитанным"
                            }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (mail.unread) {
                                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(7.dp)) {}
                                } else {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.Transparent,
                                        border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.outline),
                                        modifier = Modifier.size(7.dp)
                                    ) {}
                                }
                            }
                        }
                    } else if (mail.unread) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(7.dp)) {}
                    }
                }
            }

            // Second Row: 1-line Subject (Bold for unread)
            Text(
                mail.subject,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (mail.unread) FontWeight.Bold else FontWeight.Medium,
                fontSize = 14.sp,
                color = if (mail.unread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )

            // Third Row: 1-line Muted Gray (#8E929F) Preview
            if (mail.preview.isNotBlank()) {
                Text(
                    mail.preview,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            MailLocalBadges(mail, owner, labels, responses, snoozed, followups)

            val aiBadgeLabel = remember(mail.unread, mail.subject, mail.preview) { extractAiBadgeLabel(mail) }
            // Bottom Row: Rendered ONLY when category, thread badge, or AI badge is present (no duplicate paperclip)
            if (validCategory != null || hasThreadBadge || aiBadgeLabel != null) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    validCategory?.let {
                        val c = categoryTint ?: MaterialTheme.colorScheme.primary
                        Surface(shape = RoundedCornerShape(6.dp), color = c.copy(alpha = 0.18f)) {
                            Text(
                                it,
                                Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                maxLines = 1,
                                color = c,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    if (hasThreadBadge && chain != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.semantics { contentDescription = "Цепочка писем: ${chain.count}" }
                        ) {
                            Text(
                                "${chain.count}",
                                Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (aiBadgeLabel != null) {
                        AiParticleOrb(
                            size = 42.dp,
                            label = aiBadgeLabel
                        )
                    }
                }
            }
        }
    }
}

@Composable private fun MailRow(
    mail: NativeMail,
    enabled: Boolean,
    onClick: () -> Unit,
    owner: String,
    labels: JSONObject,
    responses: List<JSONObject>,
    snoozed: List<JSONObject>,
    followups: List<JSONObject>,
    categoryName: String?,
    categoryTint: Color?,
    chain: ThreadSummary? = null,
    selected: Boolean = false,
    onToggleSelect: (() -> Unit)? = null,
    onToggleSeen: (() -> Unit)? = null
) {
    val displayMail = if (chain != null) mail.copy(unread = chain.unread > 0) else mail
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var dragOffsetX by remember { mutableFloatStateOf(0f) }

    val animatedOffsetX by animateFloatAsState(
        targetValue = dragOffsetX,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "mailCardSwipeX"
    )
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.982f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "mailCardScale"
    )
    val tiltZ = (animatedOffsetX / 45f).coerceIn(-4f, 4f)
    val tiltY = (animatedOffsetX / 25f).coerceIn(-7f, 7f)

    val cardShape = RoundedCornerShape(18.dp)
    val cardBrush = when {
        selected -> Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primaryContainer))
        displayMail.unread -> Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.surface))
        else -> Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.background))
    }
    val borderBrush = when {
        selected -> Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, Color(0xFF6366F1)))
        displayMail.unread -> Brush.linearGradient(listOf(MaterialTheme.colorScheme.outlineVariant, MaterialTheme.colorScheme.outlineVariant, Color(0x388B5CF6)))
        else -> Brush.linearGradient(listOf(MaterialTheme.colorScheme.outlineVariant, MaterialTheme.colorScheme.outlineVariant))
    }

    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 5.dp)
    ) {
        // Kinetic swipe background reveal with glowing gradient
        if (abs(animatedOffsetX) > 6f) {
            Box(
                Modifier
                    .matchParentSize()
                    .clip(cardShape)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0x337C5CFF), Color(0x1438BDF8), Color(0x337C5CFF))
                        )
                    )
                    .padding(horizontal = 20.dp),
                contentAlignment = if (animatedOffsetX > 0f) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                Icon(
                    NativeIcons.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = animatedOffsetX
                    scaleX = pressScale
                    scaleY = pressScale
                    rotationZ = tiltZ
                    rotationY = tiltY
                    cameraDistance = 14f * density
                }
                .shadow(
                    elevation = if (displayMail.unread) 10.dp else 4.dp,
                    shape = cardShape,
                    ambientColor = Color(0xFF000000),
                    spotColor = if (displayMail.unread) MaterialTheme.colorScheme.primary else Color(0xFF000000)
                )
                .clip(cardShape)
                .background(cardBrush)
                .border(0.8.dp, borderBrush, cardShape)
                .pointerInput(onToggleSeen, enabled) {
                    if (onToggleSeen != null && enabled) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (abs(dragOffsetX) > 140f) {
                                    onToggleSeen()
                                }
                                dragOffsetX = 0f
                            },
                            onDragCancel = { dragOffsetX = 0f },
                            onHorizontalDrag = { _, dragAmount ->
                                dragOffsetX = (dragOffsetX + dragAmount * 0.55f).coerceIn(-200f, 200f)
                            }
                        )
                    }
                }
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick
                )
        ) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                // Glowing neon left bar for unread or selected cards
                Box(
                    Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(
                            when {
                                selected -> MaterialTheme.colorScheme.primary
                                displayMail.unread -> MaterialTheme.colorScheme.primary
                                else -> Color.Transparent
                            }
                        )
                )
                Box(Modifier.weight(1f)) {
                    MailRowBody(
                        displayMail,
                        MaterialTheme.colorScheme.surface,
                        chain,
                        owner,
                        labels,
                        responses,
                        snoozed,
                        followups,
                        categoryName,
                        categoryTint,
                        selected,
                        onToggleSelect,
                        onToggleSeen,
                        enabled
                    )
                }
            }
        }
    }
}

@Composable private fun ThreadConversation(state: InboxState,model: NativeInboxModel,displayRows: List<InboxDisplayRow>,scroll: LazyListState,scope: ThreadScope,owner: String,labels: JSONObject,responses: List<JSONObject>,snoozed: List<JSONObject>,followups: List<JSONObject>,category: (NativeMail)->JSONObject?) {
    val anchor=state.thread.anchor
    val displayRefs=displayRows.flatMap {row->when(row){is InboxDisplayRow.Single->listOf(refKey(row.mail.ref));is InboxDisplayRow.Group->row.members.map {refKey(it.ref)}}}.toSet()
    val members=if(anchor!=null)conversationMembers(state.rows,owner,MailRef(anchor.folder,anchor.uid,anchor.validity),displayRefs).asReversed() else emptyList()
    val summary=summaryOf(members)
    Column(Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column {
                Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(members.lastOrNull()?.subject ?: "Цепочка писем",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis)
                        Text("${summary.count} писем",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier=Modifier.semantics {contentDescription="В цепочке ${summary.count} писем, непрочитанных ${summary.unread}"})
                    }
                    IconButton(onClick=model::backToList) {
                        Icon(NativeIcons.Close, contentDescription="К списку", tint=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(thickness=0.5.dp,color=MaterialTheme.colorScheme.outlineVariant)
            }
        }
        if(members.isEmpty()) {
            ZeroInbox3DScene(title = "Цепочка пуста", subtitle = "Нет писем по выбранному фильтру")
        } else LazyColumn(state=scroll, contentPadding = PaddingValues(vertical = 8.dp), modifier=Modifier.weight(1f)) {
            items(members,key={listOf(it.ref.folder,it.ref.validity,it.ref.uid)}) { member ->
                val rule=category(member)
                MailRow(member,!state.busy,{model.openThreadMember(member,scope)},owner,labels,responses,snoozed,followups,rule?.optString("name"),rule?.let{categoryColor(it.optString("color"))})
            }
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
    Surface(
        modifier=Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=6.dp),
        shape=RoundedCornerShape(16.dp),
        color=MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(NativeIcons.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    TextField(
                        value = state.searchText,
                        onValueChange = changeText,
                        singleLine = true,
                        placeholder = { Text("Поиск писем…", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier.weight(1f),
                        isError = state.searchError != null,
                        enabled = !state.busy
                    )
                    IconButton(onClick = toggleAdvanced, modifier = Modifier.size(32.dp)) {
                        Icon(NativeIcons.Sliders, contentDescription = "Фильтры поиска", tint = if (state.advancedSearchOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                    if (hasDraftFilters) {
                        IconButton(onClick = resetFilters, enabled = !state.busy, modifier = Modifier.size(32.dp)) {
                            Icon(NativeIcons.Close, contentDescription = "Очистить поля", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            if(state.advancedSearchOpen) {
                OutlinedTextField(value=state.searchFrom,onValueChange=changeFrom,singleLine=true,shape=RoundedCornerShape(12.dp),label={Text("Отправитель")},modifier=Modifier.fillMaxWidth(),enabled=!state.busy)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value=state.searchStart,onValueChange=changeStart,singleLine=true,shape=RoundedCornerShape(12.dp),label={Text("С (ГГГГ-ММ-ДД)")},modifier=Modifier.weight(1f),enabled=!state.busy)
                    OutlinedTextField(value=state.searchEnd,onValueChange=changeEnd,singleLine=true,shape=RoundedCornerShape(12.dp),label={Text("По (ГГГГ-ММ-ДД)")},modifier=Modifier.weight(1f),enabled=!state.busy)
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected=state.searchAttachments,onClick={changeAttachments(!state.searchAttachments)},enabled=!state.busy,shape=RoundedCornerShape(10.dp),label={Text("С вложениями",fontSize=12.sp)})
                    FilterChip(selected=state.searchUnread,onClick={changeUnread(!state.searchUnread)},enabled=!state.busy,shape=RoundedCornerShape(10.dp),label={Text("Непрочитанные",fontSize=12.sp)})
                }
            }
            state.searchError?.let {Text(it,color=MaterialTheme.colorScheme.error,fontSize=12.sp)}
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                Surface(
                    onClick={focus.clearFocus();offline()},
                    enabled=!state.busy,
                    shape=RoundedCornerShape(10.dp),
                    color=MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier=Modifier.weight(1f).height(38.dp)
                ) {
                    Box(contentAlignment=Alignment.Center) {
                        Text("В кэше", fontSize=13.sp, color=MaterialTheme.colorScheme.onSurface, fontWeight=FontWeight.Medium)
                    }
                }
                Surface(
                    onClick={focus.clearFocus();online()},
                    enabled=!state.busy,
                    shape=RoundedCornerShape(10.dp),
                    color=MaterialTheme.colorScheme.primary,
                    modifier=Modifier.weight(1f).height(38.dp)
                ) {
                    Box(contentAlignment=Alignment.Center) {
                        Text("На сервере", fontSize=13.sp, color=Color.White, fontWeight=FontWeight.SemiBold)
                    }
                }
                if(state.busy)TextButton(onClick=cancel){Text("Стоп")}
            }
        }
    }
}
@Composable internal fun NativeFolderList(state: InboxState,close: ()->Unit,reload: ()->Unit,select: (String)->Unit) {
    Column(Modifier.fillMaxWidth().heightIn(max=560.dp).navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(if(state.movePicker)"Переместить в папку" else "Папки",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
            IconButton(onClick=reload,enabled=!state.foldersBusy) {
                Icon(NativeIcons.Refresh, contentDescription="Обновить папки", tint=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick=close) {
                Icon(NativeIcons.Close, contentDescription="Закрыть", tint=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider(thickness=0.5.dp,color=MaterialTheme.colorScheme.outlineVariant)
        Text(if(state.foldersBusy)"Загружаем полный список с сервера…" else if(state.foldersOnline)"Папки с сервера" else "Сохранённый список. Письма в этих папках могут быть не скачаны.",Modifier.padding(horizontal=16.dp,vertical=4.dp),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(state.foldersBusy)LinearProgressIndicator(Modifier.fillMaxWidth())
        state.foldersError?.let {Text(it,Modifier.padding(16.dp),color=MaterialTheme.colorScheme.error,fontSize=13.sp)}
        LazyColumn(Modifier.weight(1f,fill=false).padding(vertical=6.dp)) {
            items(state.folders,key={it.name}) {folder ->
                val isCurrent = folder.name == state.folder
                Surface(
                    onClick={select(folder.name)},
                    enabled=!state.movePicker||!isCurrent,
                    shape=RoundedCornerShape(12.dp),
                    color=if(isCurrent) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    modifier=Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=2.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=10.dp),
                        verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(NativeIcons.Folder, contentDescription=null, modifier=Modifier.size(18.dp), tint=if(isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(Modifier.weight(1f)) {
                            Text(
                                folder.title,
                                Modifier.fillMaxWidth(),
                                maxLines=1,
                                overflow=TextOverflow.Ellipsis,
                                fontWeight=if(isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                color=if(isCurrent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if(isCurrent) {
                            Icon(NativeIcons.Check, contentDescription="Выбрана", modifier=Modifier.size(16.dp), tint=MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}
internal fun mailTime(raw: String): String = runCatching {
    DateTimeFormatter.ofPattern("d MMM · HH:mm", Locale.forLanguageTag("ru")).withZone(ZoneId.of("Europe/Moscow")).format(Instant.parse(raw))
}.getOrDefault("")
internal fun avatarLetters(sender: String): String {
    val words=sender.substringBefore('@').replace(Regex("[._-]+")," ").trim().split(Regex("\\s+")).filter {it.isNotEmpty()}
    return words.take(2).joinToString("") {String(Character.toChars(it.codePointAt(0)))}.uppercase(Locale.forLanguageTag("ru")).ifBlank {"?"}
}

@Composable internal fun NativeMainBottomBar(
    activeTab: String,
    enabled: Boolean,
    unreadCount: Int = 0,
    todayEventsCount: Int = 0,
    onMail: () -> Unit,
    onCalendar: () -> Unit,
    onTasks: () -> Unit = {},
    onContacts: () -> Unit,
    onMore: (() -> Unit)?,
    moreContent: @Composable () -> Unit = {}
) {
    // 4-tab Flagship Bottom Navigation Bar with top spotlight beam
    Surface(
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                data class NavItem(
                    val id: String,
                    val label: String,
                    val icon: androidx.compose.ui.graphics.vector.ImageVector,
                    val badge: Int,
                    val onClick: (() -> Unit)?
                )
                val effectiveActive = if (activeTab == "tasks" || activeTab == "settings") "more" else activeTab
                val items = listOf(
                    NavItem("mail", "Почта", NativeIcons.Mail, unreadCount, onMail),
                    NavItem("calendar", "Календарь", NativeIcons.Calendar, todayEventsCount, onCalendar),
                    NavItem("contacts", "Контакты", NativeIcons.Contacts, 0, onContacts),
                    NavItem("more", "Ещё", NativeIcons.MoreHorizontal, 0, onMore)
                )
                items.forEach { item ->
                    val isSelected = item.id == effectiveActive
                    val itemEnabled = (enabled || isSelected) && item.onClick != null
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(enabled = itemEnabled) { item.onClick?.invoke() },
                        contentAlignment = Alignment.Center
                    ) {
                        // Top neon spotlight beam for active tab
                        if (isSelected) {
                            Box(
                                Modifier
                                    .align(Alignment.TopCenter)
                                    .width(42.dp)
                                    .height(28.dp)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color(0x408B5CF6), Color.Transparent)
                                        )
                                    )
                            )
                            Box(
                                Modifier
                                    .align(Alignment.TopCenter)
                                    .width(30.dp)
                                    .height(2.5.dp)
                                    .clip(RoundedCornerShape(bottomStart = 2.dp, bottomEnd = 2.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary)
                                        )
                                    )
                            )
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(contentAlignment = Alignment.TopEnd) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = null,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp).size(20.dp),
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (item.badge > 0) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.offset(x = 4.dp, y = (-2).dp)
                                    ) {
                                        Text(
                                            text = if (item.badge > 99) "99+" else item.badge.toString(),
                                            Modifier.padding(horizontal = 4.dp, vertical = 0.5.dp),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                            Text(
                                text = item.label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        if (item.id == "more") {
                            moreContent()
                        }
                    }
                }
            }
        }
    }
}

@Composable internal fun NativeAccountSwitcherDialog(
    accounts: NativeAccountsModel,
    activeAccountEmail: String,
    onDismiss: () -> Unit
) {
    val state = accounts.state
    AlertDialog(
        onDismissRequest = { if (!state.busy) onDismiss() },
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text("Аккаунты", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.error?.let {
                    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                        Text(it, Modifier.padding(10.dp), color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 12.sp)
                    }
                }
                if (state.rows.isEmpty()) {
                    Text(
                        "Нет сохранённых аккаунтов",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    state.rows.forEach { row ->
                        Surface(
                            onClick = {
                                if (!row.active && !state.busy) {
                                    accounts.quickSwitch(row.id)
                                    onDismiss()
                                } else if (row.active) {
                                    onDismiss()
                                }
                            },
                            enabled = !state.busy,
                            shape = RoundedCornerShape(14.dp),
                            color = if (row.active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = if (row.active) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                GlassSphereAvatar(row.user, size = 38.dp, selected = row.active)
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        row.user,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        row.host,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                Button(
                    onClick = {
                        onDismiss()
                        accounts.openAddAccount()
                    },
                    enabled = !state.busy,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp)
                ) {
                    Text("Добавить аккаунт")
                }
                TextButton(
                    onClick = {
                        onDismiss()
                        accounts.open()
                    },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Управление аккаунтами и SMTP", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !state.busy) {
                Text("Закрыть")
            }
        }
    )
}
