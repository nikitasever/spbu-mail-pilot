package ru.local.spbumailpilot;
import org.json.*;
public final class AdvancedSearchTest {
 static void check(boolean v){if(!v)throw new AssertionError();}
 static JSONObject mail(String date,Boolean attachment)throws Exception{JSONObject m=new JSONObject().put("date",date);if(attachment!=null)m.put("hasAttachments",attachment);return m;}
 public static void main(String[] args)throws Exception{
  SearchQuery.Filters f=new SearchQuery.Filters("{\"query\":\"Отчёт\",\"from\":\"name@example.invalid\",\"start\":\"2026-10-12\",\"end\":\"2026-10-13\",\"attachments\":true}");
  check(f.matches(mail("2026-10-12T00:00:00+03:00",true)));check(f.matches(mail("2026-10-13T23:59:59+03:00",true)));check(!f.matches(mail("2026-10-14T00:00:00+03:00",true)));check(!f.matches(mail("2026-10-11T23:59:59+03:00",true)));check(!f.matches(mail("2026-10-12",null)));check(!f.matches(mail("",true)));check(!f.matches(mail("2026-10-12",false)));
  check(new SearchQuery.Filters("{}").matches(mail("",null)));f.arguments();new SearchQuery.Filters("{\"query\":\"x\\\" OR ALL (\"}").arguments();
  for(String invalid:new String[]{"{\"query\":\"x\\nUID SEARCH ALL\"}","{\"start\":\"2026-02-30\"}","{\"start\":\"2026-10-14\",\"end\":\"2026-10-12\"}","{\"from\":\"bad\\rheader\"}"})try{new SearchQuery.Filters(invalid);throw new AssertionError();}catch(Exception expected){}
  JSONObject result=new JSONObject().put("messages",new JSONArray().put(mail("2026-10-12",false)).put(mail("2026-10-13",true))).put("nextOffset",50).put("hasMore",true).put("snapshot","fixture").put("total",100);SearchQuery.filtered(result,f);check(result.getJSONArray("messages").length()==1);check(result.getInt("scanned")==50&&result.getBoolean("hasMore")&&result.getString("snapshot").equals("fixture"));
  System.out.println("PASS: advanced-search typed filters, inclusive Moscow received dates, invalid dates/header injection rejection, attachment unknown/false isolation, and honest scanned/snapshot pagination.");
 }
}
