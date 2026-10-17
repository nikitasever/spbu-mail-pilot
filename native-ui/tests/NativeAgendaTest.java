package ru.local.spbumailpilot;
import java.time.*;
import java.util.*;
import org.json.*;
public final class NativeAgendaTest {
    static void check(boolean value){if(!value)throw new AssertionError();}
    static JSONObject event(String id,String start,String end,String endTime,boolean allDay)throws Exception{return new JSONObject().put("id",id).put("calendarId","calendar").put("date",start).put("endDate",end).put("endTime",endTime).put("time",allDay?"":"10:00").put("allDay",allDay).put("kind","remote");}
    public static void main(String[] args)throws Exception {
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"));
        check(NativeAgenda.first("2026-11-01","week").toString().equals("2026-10-26"));
        check(NativeAgenda.months("2026-11-01","week").equals(Arrays.asList("2026-10","2026-11")));
        check(NativeAgenda.end("2028-02-20","month").toString().equals("2028-03-01"));
        JSONObject allDay=event("one","2026-10-14","2026-10-16","",true);
        check(NativeAgenda.onDay(allDay,"2026-10-14")&&NativeAgenda.onDay(allDay,"2026-10-15")&&!NativeAgenda.onDay(allDay,"2026-10-16"));
        check(NativeAgenda.timeRange(allDay).contains("14")&&NativeAgenda.timeRange(allDay).contains("15")&&!NativeAgenda.timeRange(allDay).contains("16"));
        JSONObject midnight=event("two","2026-10-14","2026-10-15","00:00",false);
        check(!NativeAgenda.onDay(midnight,"2026-10-15"));
        midnight.put("endTime","00:01");check(NativeAgenda.timeRange(midnight).contains("14")&&NativeAgenda.timeRange(midnight).contains("15"));check(NativeAgenda.onDay(midnight,"2026-10-15"));
        check(!NativeAgenda.onDay(midnight,"2026-10-13"));
        JSONObject task=new JSONObject().put("id","task").put("date","2026-10-15").put("kind","task").put("done",true);
        check(!NativeAgenda.onDay(task,"2026-10-15"));task.put("done",false);check(NativeAgenda.onDay(task,"2026-10-15"));check(!NativeAgenda.onDay(task,"2026-10-16"));
        JSONObject local=new JSONObject().put("tasks",new JSONArray().put(task).put(new JSONObject(task.toString()).put("id","done").put("done",true))).put("events",new JSONArray().put(new JSONObject().put("id","bad").put("date","2026-02-31")));
        JSONArray remote=new JSONArray().put(allDay).put(new JSONObject(allDay.toString())).put(new JSONObject(allDay.toString()).put("calendarId","other"));
        JSONArray merged=NativeAgenda.combine(local,remote);
        check(merged.length()==3);check(remote.length()==3&&task.getString("kind").equals("task"));
        check(NativeAgenda.onDay(merged.getJSONObject(0),"2026-10-15"));
        System.out.println("NativeAgendaTest passed: cross-month weeks, leap year, timezone independence, exclusive all-day/midnight ends, completed task exclusion, calendar-scoped deduplication");
    }
}
