package ru.local.spbumailpilot;
import org.json.*;
import java.time.*;
public final class NativeMailEventsTest {
    interface Check {void run()throws Exception;}
    static void check(boolean value){if(!value)throw new AssertionError();}
    static void reject(Check call)throws Exception {try{call.run();}catch(Exception expected){return;}throw new AssertionError("Expected refusal");}
    public static void main(String[] args)throws Exception {
        String owner="account-"+"a".repeat(64);Instant clock=Instant.parse("2026-10-13T08:00:00Z");
        JSONObject mail=new JSONObject().put("account",owner).put("folder","Проекты/2026").put("uid","42").put("validity","7")
            .put("subject","к защите Шахматовой завтра в 15-00").put("date","2026-10-13T23:30:00Z");
        NativeMailEvents.Hint hint=NativeMailEvents.hint(mail,"",clock);
        check(hint.date.equals("2026-10-15")&&hint.time.equals("15:00")); // Moscow sent day, not host day.
        check(NativeMailEvents.hint(new JSONObject(mail.toString()).put("subject","сегодня и завтра в 15:00"),"",clock)==null);
        check(NativeMailEvents.hint(new JSONObject(mail.toString()).put("subject","завтра в 15:00 или 16:00"),"",clock)==null);
        check(NativeMailEvents.hint(new JSONObject(mail.toString()).put("subject","31.02.2027 15:00"),"",clock)==null);
        check(NativeMailEvents.hint(new JSONObject(mail.toString()).put("subject","14.10.2026 24:00"),"",clock)==null);
        check(NativeMailEvents.hint(new JSONObject(mail.toString()).put("subject","13.10.2026 09:00"),"",clock)==null);
        check(NativeMailEvents.hint(new JSONObject(mail.toString()).put("date","bad"),"",clock)==null);
        JSONObject event=NativeMailEvents.draft(owner,mail,"event",clock);
        check(event.getString("date").equals("2026-10-15")&&event.getString("time").equals("15:00"));
        JSONObject initial=new JSONObject().put("unknown",true).put("tasks",new JSONArray().put(new JSONObject().put("id","task")));
        JSONObject saved=NativeMailEvents.save(initial,owner,mail,null,event,"link");
        check(!initial.has("events")&&!initial.has("eventMailLinks"));
        check(saved.getBoolean("unknown")&&saved.getJSONArray("tasks").length()==1);
        check(saved.getJSONArray("events").length()==1&&saved.getJSONArray("eventMailLinks").length()==1);
        JSONObject original=NativeMailEvents.existing(saved,mail),changed=new JSONObject(original.toString()).put("title","Новое название").put("date","2026-11-01");
        JSONObject updated=NativeMailEvents.save(saved,owner,mail,original,changed,"not-used");
        check(updated.getJSONArray("events").length()==1&&updated.getJSONArray("eventMailLinks").length()==1);
        JSONObject link=updated.getJSONArray("eventMailLinks").getJSONObject(0);
        check(link.getString("eventTitle").equals("Новое название")&&link.getString("eventDate").equals("2026-11-01"));
        check(NativeMailLinks.target(owner,link.getJSONObject("mail"))!=null);
        reject(()->NativeMailEvents.save(updated,owner,mail,original,changed,"link2")); // stale editor
        reject(()->NativeMailEvents.save(saved,owner,mail,null,event,"link2")); // concurrent create
        reject(()->NativeMailEvents.save(initial,"account-"+"b".repeat(64),mail,null,event,"link2"));
        JSONObject duplicate=new JSONObject(saved.toString());duplicate.getJSONArray("events").put(new JSONObject(original.toString()).put("id","other"));
        reject(()->NativeMailEvents.existing(duplicate,mail));
        JSONObject full=new JSONObject(initial.toString());JSONArray links=new JSONArray();for(int i=0;i<500;i++)links.put(new JSONObject().put("eventKey","local:other"));full.put("eventMailLinks",links);
        reject(()->NativeMailEvents.save(full,owner,mail,null,event,"overflow"));check(!full.has("events"));
        check(NativeWorkspace.change(updated,"events",NativeMailEvents.existing(updated,mail),null).getJSONArray("eventMailLinks").length()==0);
        System.out.println("PASS native mail events: Moscow hints, invalid/ambiguous/past dates, atomic event/link, dedupe, exact references, stale/foreign/limit refusal, metadata and deletion");
    }
}
