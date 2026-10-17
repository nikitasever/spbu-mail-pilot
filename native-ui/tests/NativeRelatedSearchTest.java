package ru.local.spbumailpilot;
import org.json.*;
import java.time.*;
import java.util.*;
public final class NativeRelatedSearchTest {
 static void check(boolean v){if(!v)throw new AssertionError();}
 static NativeMailSearch.Criteria c(String query,String start,String end){return new NativeMailSearch.Criteria(query,"",start,end,false,false);}
 public static void main(String[] args)throws Exception{
  LocalDate now=LocalDate.of(2026,12,31);
  NativeRelatedSearch.Range range=NativeRelatedSearch.range(c("","",""),now);
  check(range.start.equals("2026-12-01")&&range.end.equals("2027-01-31")&&range.months.equals(Arrays.asList("2026-12","2027-01")));
  range=NativeRelatedSearch.range(c("","","2026-02-28"),now);check(range.start.equals("2026-02-01")&&range.end.equals("2026-02-28")&&range.months.size()==1);
  check(!NativeRelatedSearch.range(c("","2026-01-01","2026-12-31"),now).tooWide);
  NativeRelatedSearch.Range wide=NativeRelatedSearch.range(c("","2026-01-01","2027-01-01"),now);check(wide.tooWide&&wide.months.isEmpty());
  JSONObject local=new JSONObject().put("contacts",new JSONArray().put(new JSONObject().put("sender","Семён Ёлкин").put("email","person@example.invalid").put("number","+7 812 111-22-33").put("private","secret")))
   .put("recipientBook",new JSONArray().put(new JSONObject().put("sender","Other").put("email","history@example.invalid")));
  String before=local.toString();JSONArray remote=new JSONArray().put(new JSONObject().put("sender","Дубликат").put("email","PERSON@example.invalid"))
    .put(new JSONObject().put("sender","Remote").put("email","remote@example.invalid")).put(new JSONObject().put("sender","invalid").put("email","bad"));
  JSONArray people=NativeRelatedSearch.people(local,remote,"семен елкин");check(people.length()==1&&people.getJSONObject(0).getString("email").equals("person@example.invalid")&&!people.getJSONObject(0).has("private"));
  check(NativeRelatedSearch.people(local,remote,"111-22").length()==1);check(NativeRelatedSearch.people(local,remote,"HISTORY@").length()==1);
  check(NativeRelatedSearch.people(local,remote,"").length()==0);check(NativeRelatedSearch.people(local,remote,"@example.invalid").length()==3);check(local.toString().equals(before));
  people.getJSONObject(0).put("sender","modified");check(local.toString().equals(before));
  JSONObject event=new JSONObject().put("id","local").put("title","Защита Семёна").put("date","2026-10-17").put("time","15:00");
  JSONObject done=new JSONObject(event.toString()).put("id","done").put("done",true);
  JSONObject ongoing=new JSONObject().put("id","remote").put("calendarId","work").put("title","Защита проекта").put("date","2026-10-16").put("endDate","2026-10-18").put("allDay",true);
  JSONObject data=new JSONObject().put("events",new JSONArray().put(event)).put("tasks",new JSONArray().put(done));String snapshot=data.toString();
  NativeRelatedSearch.Range day=NativeRelatedSearch.range(c("защита","2026-10-17","2026-10-17"),now);
  JSONArray events=NativeRelatedSearch.events(data,new JSONArray().put(ongoing).put(ongoing),"защита",day);
  check(events.length()==2);check(NativeRelatedSearch.events(data,new JSONArray().put(ongoing),"семен",day).length()==1);
  check(NativeRelatedSearch.events(data,new JSONArray(),"защита",NativeRelatedSearch.range(c("","2026-10-18","2026-10-18"),now)).length()==0);
  events.getJSONObject(1).put("title","changed");check(data.toString().equals(snapshot));
  System.out.println("PASS related search: year boundary/default and inclusive ranges, 12-month server limit, ё/е/case/phone/history matching, safe contact fields/deduplication, multiday events/completed tasks, immutable outputs");
 }
}
