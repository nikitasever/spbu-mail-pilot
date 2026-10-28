package ru.local.spbumailpilot;
import org.json.*;
public final class NativeLocalEventsTest {
    interface Check{void run()throws Exception;}
    static void check(boolean v){if(!v)throw new AssertionError();}
    static void reject(Check c)throws Exception{try{c.run();}catch(Exception expected){return;}throw new AssertionError("Expected refusal");}
    public static void main(String[] args)throws Exception {
        JSONObject event=new JSONObject().put("id","event").put("title","Защита 🟣").put("date","2026-10-15").put("time","15:00");
        JSONObject created=NativeWorkspace.change(new JSONObject(),"events",null,event);
        check(created.getJSONArray("events").length()==1&&created.getJSONArray("tasks").length()==0&&!created.getJSONArray("events").getJSONObject(0).has("done"));
        JSONObject input=new JSONObject().put("events",new JSONArray().put(event)).put("tasks",new JSONArray()).put("eventMailLinks",new JSONArray().put(new JSONObject().put("eventKey","local:event").put("eventDate","2026-10-15").put("eventTitle","Защита 🟣").put("mail",new JSONObject().put("uid","42"))).put(new JSONObject().put("eventKey","remote:event").put("eventTitle","Server")));
        JSONObject edit=new JSONObject(event.toString()).put("date","2026-11-01").put("title","Перенесено");
        JSONObject updated=NativeWorkspace.change(input,"events",event,edit);
        check(updated.getJSONArray("tasks").length()==0&&!updated.getJSONArray("events").getJSONObject(0).has("reminder"));
        check(updated.getJSONArray("eventMailLinks").getJSONObject(0).getString("eventDate").equals("2026-11-01"));
        check(updated.getJSONArray("eventMailLinks").getJSONObject(0).getJSONObject("mail").getString("uid").equals("42"));
        check(input.getJSONArray("eventMailLinks").getJSONObject(0).getString("eventTitle").equals("Защита 🟣"));
        reject(()->NativeWorkspace.change(updated,"events",event,null));
        check(NativeWorkspace.change(updated,"events",edit,null).getJSONArray("eventMailLinks").length()==1);
        reject(()->NativeWorkspace.validate("events",new JSONObject(event.toString()).put("date","")));
        reject(()->NativeWorkspace.validate("events",new JSONObject(event.toString()).put("date","2026-02-30")));
        reject(()->NativeWorkspace.validate("events",new JSONObject(event.toString()).put("time","24:00")));
        reject(()->NativeWorkspace.validate("events",new JSONObject(event.toString()).put("kind","remote")));
        reject(()->NativeWorkspace.validate("events",new JSONObject(event.toString()).put("calendarId","server")));
        reject(()->NativeWorkspace.validate("events",new JSONObject(event.toString()).put("date","1969-12-31")));
        NativeWorkspace.validate("events",new JSONObject(event.toString()).put("time",""));
        NativeWorkspace.validate("events",new JSONObject(event.toString()).put("date","2028-02-29"));
        JSONObject malformed=new JSONObject(input.toString()).put("eventMailLinks","bad");
        reject(()->NativeWorkspace.change(malformed,"events",event,null));
        check(malformed.getJSONArray("events").length()==1);
        System.out.println("PASS local events: exact-record CAS, linked metadata update/delete, other links and mail refs preserved, no implicit tasks/reminders, strict dates/time, remote mutation refused");
    }
}
