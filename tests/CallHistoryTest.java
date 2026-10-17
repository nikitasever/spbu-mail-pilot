package ru.local.spbumailpilot;
import java.time.Instant;
import org.json.*;
public class CallHistoryTest {
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args)throws Exception{
  JSONObject source=new JSONObject().put("settings",new JSONObject().put("theme","dark")).put("recipientBook",new JSONArray().put("unchanged"));String before=source.toString();
  Instant at=Instant.parse("2026-10-17T12:00:00Z");JSONObject next=CallHistory.append(source,"+7 (812) 326-49-56",at);
  check(source.toString().equals(before));check(next.getJSONObject("settings").getString("theme").equals("dark")&&next.getJSONArray("recipientBook").getString(0).equals("unchanged"));
  check(next.getJSONArray("callHistory").getJSONObject(0).getString("number").equals("+78123264956"));
  check(next.getJSONArray("callHistory").getJSONObject(0).getString("at").equals(at.toString()));
  for(int i=0;i<60;i++)next=CallHistory.append(next,"12345",at.plusSeconds(i+1));
  JSONArray rows=CallHistory.rows(next);check(rows.length()==50&&rows.getJSONObject(0).getString("at").equals(at.plusSeconds(60).toString()));
  rows.getJSONObject(0).put("number","modified");check(!next.getJSONArray("callHistory").getJSONObject(0).getString("number").equals("modified"));
  JSONObject malformed=new JSONObject().put("callHistory",new JSONArray().put(new JSONObject().put("number","tel:javascript").put("at",at.toString())).put(new JSONObject().put("number",12345).put("at",at.toString())).put(new JSONObject().put("number","12345").put("at","invalid")));
  check(CallHistory.rows(malformed).length()==0);
  try{CallHistory.append(source,"12345;call",at);throw new AssertionError();}catch(IllegalArgumentException expected){}
  try{CallHistory.rows(new JSONObject().put("callHistory","wrong"));throw new AssertionError();}catch(IllegalArgumentException expected){}
  check(CallHistory.rows(new JSONObject()).length()==0);
  System.out.println("PASS app dial history: exact normalized numbers and times, newest-first 50 limit, nonmutation/unrelated fields, malformed rows/type/injection rejection; no system calls");
 }
}
