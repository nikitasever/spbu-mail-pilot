package ru.local.spbumailpilot

import android.app.Application
import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.lifecycle.SavedStateHandle
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

class NativePreviewTest {
    @get:Rule val paparazzi=Paparazzi(deviceConfig=DeviceConfig.PIXEL_5.copy(nightMode=NightMode.NIGHT),showSystemUi=true)
    private class PreviewApplication(private val preview: Context):Application(){override fun getApplicationContext():Context=preview}
    private val owner="account-"+"a".repeat(64)
    private fun mail(uid:String,sender:String,subject:String,preview:String,time:String,unread:Boolean)=NativeMail(MailRef("INBOX",uid,"7"),sender,subject,preview,"2026-10-15T$time:00+03:00",unread,uid=="42"||uid=="41")
    private fun inbox(state:InboxState):NativeInboxModel {
        val model=NativeInboxModel(PreviewApplication(paparazzi.context),SavedStateHandle())
        val field=model.javaClass.getDeclaredField("state\$delegate").apply {isAccessible=true}
        @Suppress("UNCHECKED_CAST") (field.get(model) as MutableState<InboxState>).value=state
        return model
    }
    @Test fun inbox() {
        val rows=listOf(
            mail("42","Учебный отдел","Материалы к защите завтра в 15:00","Коллеги, направляем материалы к заседанию.","10:24",true),
            mail("41","Перминов Виктор Олегович","По 191 аудитории — согласование","Направляю обновлённую спецификацию.","09:52",true),
            mail("40","OTRS Notifications","[Ticket#202610159588] Заявка принята","Ваша заявка принята в работу.","09:17",false),
            mail("39","Ректорат","Материалы ректорского совещания","Повестка и презентации к совещанию.","08:45",false),
            mail("38","Рабочая группа","RE: Обсуждение расписания","Спасибо, изменения согласованы.","08:12",false),
            mail("37","Библиотека СПбГУ","Доступ к электронным ресурсам","Обновление списка доступных изданий.","08:02",false))
        val model=inbox(InboxState(account="demo@spbu.ru",owner=owner,rows=rows,total=1330,hasMore=true,cached=true,pageOffline=true))
        paparazzi.snapshot(name="inbox"){InboxTheme{NativeInbox(model,{},{},{},{},{},null,{})}}
    }
    @Test fun search() {
        val rows=listOf(mail("42","Учебный отдел","Материалы к защите завтра в 15:00","Коллеги, направляем материалы.","10:24",true))
        val model=inbox(InboxState(account="demo@spbu.ru",owner=owner,rows=rows,total=1,query="защита",searchText="защита",searchOpen=true,cached=false,pageOffline=false))
        paparazzi.snapshot(name="search"){InboxTheme{NativeInbox(model,{},{},{},{},{},null,{})}}
    }
    @Test fun contacts() {
        val model=NativeContactsModel(PreviewApplication(paparazzi.context))
        val field=model.javaClass.getDeclaredField("state\$delegate").apply {isAccessible=true}
        @Suppress("UNCHECKED_CAST") (field.get(model) as MutableState<ContactsState>).value=ContactsState(open=true,owner=owner,account="demo@spbu.ru",book="company",supported=true,letter="П",applied="^:П",people=listOf(ContactPerson("Перминов Виктор Олегович","demo-perminov@spbu.ru"),ContactPerson("Петрова Елена Александровна","demo-petrova@spbu.ru")),more=true)
        paparazzi.snapshot(name="contacts"){InboxTheme{NativeContactsScreen(model,{},{})}}
    }
    @Test fun calendar() {
        val model=NativeCalendarModel(PreviewApplication(paparazzi.context))
        val field=model.javaClass.getDeclaredField("state\$delegate").apply {isAccessible=true}
        val entries=listOf(
            JSONObject().put("id","meeting").put("title","Материалы к защите Шахматовой").put("date","2026-10-15").put("time","15:00").put("endDate","2026-10-15").put("endTime","16:00").put("kind","remote").put("calendarTitle","Календарь подразделения"),
            JSONObject().put("id","task").put("title","Проверить спецификацию по 191 аудитории").put("date","2026-10-15").put("time","17:00").put("kind","task"))
        @Suppress("UNCHECKED_CAST") (field.get(model) as MutableState<CalendarState>).value=CalendarState(open=true,owner=owner,day="2026-10-15",entries=entries.map {CalendarEntry(it.getString("id"),it)},notice="Сохранённые события календарей.")
        paparazzi.snapshot(name="calendar"){InboxTheme{NativeCalendarScreen(model,{})}}
    }
    @Test fun workspaceTasks() {
        val model=NativeWorkspaceModel(PreviewApplication(paparazzi.context))
        val task=JSONObject().put("id","demo-task").put("title","Проверить материалы к защите").put("date","2026-10-16").put("time","15:00").put("priority","important").put("done",false).put("notes","Уточнить подключение удалённого участника")
        val field=model.javaClass.getDeclaredField("state\$delegate").apply {isAccessible=true}
        @Suppress("UNCHECKED_CAST") (field.get(model) as MutableState<WorkspaceState>).value=WorkspaceState(open=true,owner=owner,rows=listOf(task))
        paparazzi.snapshot(name="tasks"){InboxTheme{NativeWorkspaceScreen(model)}}
    }
    @Test fun workspaceNote() {
        val model=NativeWorkspaceModel(PreviewApplication(paparazzi.context))
        val note=JSONObject().put("id","demo-note").put("title","К заседанию комиссии").put("body","Проверить материалы и список участников.\n\nЧетыре участника очно, один — удалённо.")
        val field=model.javaClass.getDeclaredField("state\$delegate").apply {isAccessible=true}
        @Suppress("UNCHECKED_CAST") (field.get(model) as MutableState<WorkspaceState>).value=WorkspaceState(open=true,owner=owner,kind="notes",editor=note,expected=note)
        paparazzi.snapshot(name="note-editor"){InboxTheme{NativeWorkspaceScreen(model)}}
    }
    @Test fun eventEditor() {
        val model=NativeWorkspaceModel(PreviewApplication(paparazzi.context))
        val event=JSONObject().put("id","demo-event").put("title","Защита Шахматовой").put("date","2026-10-16").put("time","15:00")
        val field=model.javaClass.getDeclaredField("state\$delegate").apply {isAccessible=true}
        @Suppress("UNCHECKED_CAST") (field.get(model) as MutableState<WorkspaceState>).value=WorkspaceState(open=true,owner=owner,kind="events",eventRoute=true,eventDay="2026-10-16",editor=event,expected=event)
        paparazzi.snapshot(name="event-editor"){InboxTheme{NativeWorkspaceScreen(model)}}
    }
    @Test fun localCalendar() {
        val model=NativeCalendarModel(PreviewApplication(paparazzi.context))
        val event=JSONObject().put("id","demo-event").put("title","Защита Шахматовой").put("date","2026-10-16").put("time","15:00").put("kind","event")
        val field=model.javaClass.getDeclaredField("state\$delegate").apply {isAccessible=true}
        @Suppress("UNCHECKED_CAST") (field.get(model) as MutableState<CalendarState>).value=CalendarState(open=true,owner=owner,day="2026-10-16",entries=listOf(CalendarEntry("local:demo-event",event)),actionNotice="Сохранено на телефоне.")
        paparazzi.snapshot(name="local-calendar"){InboxTheme{NativeCalendarScreen(model,{})}}
    }
    @Test fun linkedReturn() {
        val saved=SavedStateHandle(mapOf("readerOffset" to 777,"listIndex" to 12,"mailRoute" to "original-route"))
        val model=NativeInboxModel(PreviewApplication(paparazzi.context),saved)
        val original=InboxState(owner=owner,folder="INBOX",query="защита",filter="unread",hasMore=true,pageOffline=false,rows=listOf(mail("42","Учебный отдел","Материалы","","10:24",true)))
        val ref=MailRef("Archive","43","8")
        val field=model.javaClass.getDeclaredField("state\$delegate").apply {isAccessible=true}
        @Suppress("UNCHECKED_CAST") (field.get(model) as MutableState<InboxState>).value=InboxState(owner=owner,folder="Archive",linked=true,selected=NativeMail(ref,"Коллега","Связанное письмо","","",true))
        model.javaClass.getDeclaredField("linkedOrigin").apply {isAccessible=true}.set(model,original)
        model.readerPosition(owner,ref,500)
        org.junit.Assert.assertEquals(500,model.readerOffset)
        org.junit.Assert.assertEquals(777,saved.get<Int>("readerOffset"))
        model.pause(false) // The origin pager must expire even when another folder's reader is visible.
        model.backToList()
        org.junit.Assert.assertFalse(model.state.linked)
        org.junit.Assert.assertEquals("INBOX",model.state.folder)
        org.junit.Assert.assertEquals("защита",model.state.query)
        org.junit.Assert.assertEquals("unread",model.state.filter)
        org.junit.Assert.assertEquals(original.rows,model.state.rows)
        org.junit.Assert.assertTrue(model.state.pageExpired)
        org.junit.Assert.assertEquals(777,model.readerOffset)
        org.junit.Assert.assertEquals("original-route",saved.get<String>("mailRoute"))
        org.junit.Assert.assertEquals(12,saved.get<Int>("listIndex"))
    }
    @Test fun deferredFormState() {
        val model=NativeMailExtrasModel(PreviewApplication(paparazzi.context))
        val source=JSONObject().put("account",owner).put("folder","INBOX").put("uid","42").put("validity","7")
        val field=model.javaClass.getDeclaredField("state\$delegate").apply {isAccessible=true}
        @Suppress("UNCHECKED_CAST") (field.get(model) as MutableState<MailExtrasState>).value=MailExtrasState(owner=owner,open=true,target=source,mode="snooze",day="2026-10-16",time="09:00")
        model.selectList("followup")
        org.junit.Assert.assertEquals("response",model.state.listMode)
        model.field("time","12:30")
        model.pause()
        org.junit.Assert.assertEquals("snooze",model.state.mode)
        org.junit.Assert.assertEquals("12:30",model.state.time)
        model.dismiss()
        model.selectList("followup")
        org.junit.Assert.assertEquals("followup",model.state.listMode)
        model.selectList("invalid")
        org.junit.Assert.assertEquals("followup",model.state.listMode)
        org.junit.Assert.assertTrue(model.state.snoozed.isEmpty())
    }
    @Test fun mailExtrasState() {
        val model=NativeMailExtrasModel(PreviewApplication(paparazzi.context))
        val source=JSONObject().put("account",owner).put("folder","INBOX").put("uid","42").put("validity","7").put("subject","Письмо")
        val field=model.javaClass.getDeclaredField("state\$delegate").apply {isAccessible=true}
        @Suppress("UNCHECKED_CAST") (field.get(model) as MutableState<MailExtrasState>).value=MailExtrasState(owner=owner,open=true,target=source,mode="response",day="2026-10-16",time="09:00")
        model.field("time","10:30")
        model.pause()
        org.junit.Assert.assertEquals("10:30",model.state.time)
        org.junit.Assert.assertNotNull(model.state.target)
        model.dismiss()
        org.junit.Assert.assertNull(model.state.target)
        org.junit.Assert.assertTrue(model.state.responses.isEmpty())
        model.back()
        org.junit.Assert.assertFalse(model.state.open)
    }
    @Test fun categoryFormState() {
        val model=NativeCategoriesModel(PreviewApplication(paparazzi.context))
        val rows=NativeCategoryRules.list(JSONObject());val rule=rows.getJSONObject(0)
        val field=model.javaClass.getDeclaredField("state\$delegate").apply {isAccessible=true}
        @Suppress("UNCHECKED_CAST") (field.get(model) as MutableState<CategoriesState>).value=CategoriesState(owner=owner,open=true,
            rules=(0 until rows.length()).map {rows.getJSONObject(it)},editor=JSONObject(rule.toString()),initial=JSONObject(rule.toString()))
        model.field("name","Новое название")
        model.back()
        org.junit.Assert.assertTrue(model.state.leaveOpen)
        model.leave(false)
        org.junit.Assert.assertEquals("Новое название",model.state.editor?.optString("name"))
        model.pause()
        org.junit.Assert.assertEquals("Новое название",model.state.editor?.optString("name"))
        model.leave(true)
        org.junit.Assert.assertNull(model.state.editor)
        model.filter("events")
        org.junit.Assert.assertEquals("events",model.state.filter)
        model.filter("invalid")
        org.junit.Assert.assertEquals("events",model.state.filter)
    }
    @Test fun moveLifecycle() {
        val mail=mail("42","Коллега","Письмо","","10:24",true)
        val model=inbox(InboxState(owner=owner,rows=listOf(mail),selected=mail,cached=false,busy=true,moveOpen=true))
        model.javaClass.getDeclaredField("mutating").apply {isAccessible=true}.setBoolean(model,true)
        model.pause(false)
        org.junit.Assert.assertTrue(model.state.busy)
        org.junit.Assert.assertFalse(model.state.moveOpen)
        model.backToList()
        org.junit.Assert.assertEquals(mail.ref,model.state.selected?.ref)
        model.requestMove("trash")
        org.junit.Assert.assertFalse(model.state.moveOpen)
        model.javaClass.getDeclaredField("mutating").apply {isAccessible=true}.setBoolean(model,false)
        model.backToList()
        org.junit.Assert.assertNull(model.state.selected)
    }
    @Test fun mailEventEditor() {
        val model=NativeWorkspaceModel(PreviewApplication(paparazzi.context))
        val source=JSONObject().put("account",owner).put("folder","INBOX").put("uid","42").put("validity","7")
            .put("subject","Защита Шахматовой завтра в 15-00").put("date","2026-10-15T10:00:00+03:00")
        val event=NativeMailEvents.draft(owner,source,"from-mail",java.time.Instant.parse("2026-10-15T09:00:00Z"))
        val field=model.javaClass.getDeclaredField("state\$delegate").apply {isAccessible=true}
        @Suppress("UNCHECKED_CAST") (field.get(model) as MutableState<WorkspaceState>).value=WorkspaceState(open=true,owner=owner,kind="events",eventMail=source,editor=event)
        paparazzi.snapshot(name="mail-event"){InboxTheme{NativeWorkspaceScreen(model)}}
        model.leave(false)
        org.junit.Assert.assertTrue(model.state.open)
        org.junit.Assert.assertEquals("2026-10-16",model.state.editor?.optString("date"))
        model.leave(true)
        org.junit.Assert.assertFalse(model.state.open)
        model.clearMailTaskReturn()
        org.junit.Assert.assertNull(model.state.eventMail)
    }
    @Test fun mailTaskEditor() {
        val model=NativeWorkspaceModel(PreviewApplication(paparazzi.context))
        val task=NativeMailLinks.task(owner,JSONObject().put("folder","INBOX").put("uid","42").put("validity","7").put("subject","Материалы к защите завтра в 15:00"),"task-from-mail")
        val field=model.javaClass.getDeclaredField("state\$delegate").apply {isAccessible=true}
        @Suppress("UNCHECKED_CAST") (field.get(model) as MutableState<WorkspaceState>).value=WorkspaceState(open=true,owner=owner,taskMail=task,editor=task)
        paparazzi.snapshot(name="mail-task"){InboxTheme{NativeWorkspaceScreen(model)}}
        model.leave(true)
        org.junit.Assert.assertFalse(model.state.open)
        org.junit.Assert.assertNull(model.state.editor)
        model.clearMailTaskReturn()
        org.junit.Assert.assertNull(model.state.taskMail)
    }
    @Test fun linkedTask() {
        val model=NativeWorkspaceModel(PreviewApplication(paparazzi.context))
        val task=JSONObject().put("id","demo-task").put("title","Подготовить материалы к защите").put("date","2026-10-16").put("time","15:00").put("done",false).put("mail",JSONObject().put("folder","Archive").put("uid","42").put("validity","7"))
        val field=model.javaClass.getDeclaredField("state\$delegate").apply {isAccessible=true}
        @Suppress("UNCHECKED_CAST") (field.get(model) as MutableState<WorkspaceState>).value=WorkspaceState(open=true,owner=owner,rows=listOf(task))
        paparazzi.snapshot(name="task-source"){InboxTheme{NativeWorkspaceScreen(model)}}
    }
    @Test fun linkedCalendar() {
        val model=NativeCalendarModel(PreviewApplication(paparazzi.context))
        val event=JSONObject().put("id","demo-event").put("title","Защита Шахматовой").put("date","2026-10-16").put("time","15:00").put("kind","event")
        val link=JSONObject().put("id","demo-link").put("eventKey","local:demo-event").put("mail",JSONObject().put("folder","Archive").put("uid","42").put("validity","7").put("subject","Материалы к защите Шахматовой"))
        val field=model.javaClass.getDeclaredField("state\$delegate").apply {isAccessible=true}
        @Suppress("UNCHECKED_CAST") (field.get(model) as MutableState<CalendarState>).value=CalendarState(open=true,owner=owner,day="2026-10-16",entries=listOf(CalendarEntry("local:demo-event",event)),links=listOf(link))
        paparazzi.snapshot(name="event-mails"){InboxTheme{NativeCalendarScreen(model,{})}}
    }
    @Test fun reader() {
        val mail=mail("42","Учебный отдел","Материалы к защите завтра в 15:00","","10:24",true)
        val header=JSONObject().put("sender",mail.sender).put("email","demo-office@spbu.ru").put("subject",mail.subject).put("seen",false)
        val body=MailBody("Коллеги, материалы на завтра. Будут 4 человека лично и 1 — удалённо.\n\nНачало в 15:00, аудитория 191.",listOf(NativeFile("Заключение комиссии.docx","1"),NativeFile("Диссертация — презентация.pptx","2")),header)
        val model=inbox(InboxState(account="demo@spbu.ru",owner=owner,rows=listOf(mail),selected=mail,body=body,cached=true,pageOffline=true))
        paparazzi.snapshot(name="reader"){InboxTheme{NativeInbox(model,{},{},{},{},{},null,{})}}
    }
    @Test fun folders() {
        val entries=NativeFolders.list(org.json.JSONArray().put("INBOX").put("Sent").put("Drafts").put("Archive").put("Junk").put("Trash").put("Проекты/2026"),"INBOX")
        val state=InboxState(account="demo@spbu.ru",owner=owner,folders=entries,foldersOnline=true)
        // Layoutlib does not capture the separate Android Dialog window. Render its actual content.
        paparazzi.snapshot(name="folders-content"){InboxTheme{
            androidx.compose.material3.Surface(androidx.compose.ui.Modifier.fillMaxSize()) {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize(),contentAlignment=androidx.compose.ui.Alignment.BottomCenter) {
                    androidx.compose.material3.Surface {NativeFolderList(state,{},{},{})}
                }
            }
        }}
    }
    @Test fun composer() {
        val model=NativeComposerModel(PreviewApplication(paparazzi.context))
        val field=model.javaClass.getDeclaredField("state\$delegate").apply {isAccessible=true}
        @Suppress("UNCHECKED_CAST") (field.get(model) as MutableState<ComposerState>).value=ComposerState(open=true,owner=owner,from="demo@spbu.ru",to="demo-office@spbu.ru",subject="Re: Материалы к защите завтра в 15:00",body="Добрый день!\n\nМатериалы получил, спасибо. Буду к 15:00.\n\nС уважением,\nДемонстрационный пользователь",draftId="00000000-0000-4000-8000-000000000001",accountValid=true,saved=true)
        paparazzi.snapshot(name="composer"){InboxTheme{NativeComposer(model,{},{})}}
    }
}
