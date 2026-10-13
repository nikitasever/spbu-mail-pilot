package ru.local.spbumailpilot;
import org.json.*;
import java.time.*;
import java.util.*;
public final class WidgetAgendaTest {
 static void check(boolean ok){if(!ok)throw new AssertionError();}
 static JSONObject event(String id,String date,String time)throws Exception{return new JSONObject().put("id",id).put("date",date).put("time",time).put("title","Встреча "+id);}
 public static void main(String[] args)throws Exception{
  long now=ZonedDateTime.of(2026,10,31,12,0,0,0,WidgetAgenda.ZONE).toInstant().toEpochMilli();
  JSONArray events=new JSONArray().put(event("old","2026-10-31","09:00")).put(event("now","2026-10-31","11:00").put("endTime","13:00")).put(event("future","2026-11-01","10:00")).put(event("far","2026-11-09","10:00")).put(event("bad","broken","10:00"));
  JSONArray tasks=new JSONArray().put(event("done","2026-11-01","09:00").put("done",true)).put(event("task","2026-10-31",""));
  List<WidgetAgenda.Entry> result=WidgetAgenda.entries(events,tasks,now);check(result.size()==3);check(result.get(0).label.contains("Задача"));check(result.get(1).label.equals("Сегодня · 11:00–13:00"));check(result.get(2).label.startsWith("Завтра"));
  JSONObject span=event("span","2026-10-30","").put("allDay",true).put("endDate","2026-11-02").put("calendarId","one");
  result=WidgetAgenda.entries(new JSONArray().put(span).put(span),null,now);check(result.size()==1&&result.get(0).label.startsWith("Продолжается"));
  result=WidgetAgenda.entries(new JSONArray().put(event("ended","2026-10-30","").put("allDay",true).put("endDate","2026-10-31")),null,now);check(result.isEmpty());
  result=WidgetAgenda.entries(new JSONArray().put(span).put(new JSONObject(span.toString()).put("calendarId","two")),null,now);check(result.size()==2);
  result=WidgetAgenda.entries(new JSONArray().put(event("last","2026-11-06","10:00")).put(event("outside","2026-11-07","10:00")),null,now);check(result.size()==1&&result.get(0).title.contains("last"));
  check(WidgetAgenda.entries(null,null,now).isEmpty());check(WidgetAgenda.checked(0,now).contains("Откройте"));check(WidgetAgenda.checked(now+3600000,now).contains("Откройте"));check(WidgetAgenda.checked(now,now).equals("Проверено 12:00 · МСК"));check(WidgetAgenda.checked(now-86400000,now).contains("30 окт"));
  check(!WidgetAgenda.clean("Название\n\r\t",180).contains("\n"));check(WidgetAgenda.clean(String.join("",Collections.nCopies(100,"😀")),5).codePointCount(0,WidgetAgenda.clean(String.join("",Collections.nCopies(100,"😀")),5).length())<=6);
  System.out.println("PASS: widget agenda ignores malformed/completed/expired entries, includes ongoing/all-day and next-month events, deduplicates per calendar, bounds rows and honestly dates the saved unread count.");
 }
}
