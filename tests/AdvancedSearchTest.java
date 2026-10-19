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
  SearchQuery.Filters unreadFilter=new SearchQuery.Filters("{\"unread\":true}");
  check(unreadFilter.matches(new JSONObject().put("unread",true)));
  check(unreadFilter.matches(new JSONObject().put("seen",false)));
  check(!unreadFilter.matches(new JSONObject().put("seen",true)));
  unreadFilter.arguments();
  ReadFlagTest.MemoryStore store=new ReadFlagTest.MemoryStore(javax.mail.Session.getInstance(new java.util.Properties()));
  MailReader reader=new MailReader();java.lang.reflect.Field storeField=MailReader.class.getDeclaredField("store");storeField.setAccessible(true);storeField.set(reader,store);
  reader.body("INBOX","42","7");
  for(Object[] entry:new Object[][]{{"smartPages",new UidPages(new long[]{42})},{"smartFolder","INBOX"},{"smartValidity",7L},{"smartKey",new SearchQuery.Filters("{}").key()},{"smartGeneration",1L}}){java.lang.reflect.Field field=MailReader.class.getDeclaredField((String)entry[0]);field.setAccessible(true);field.set(reader,entry[1]);}
  try{reader.smartPage("INBOX",1,"1",unreadFilter);throw new AssertionError("Cursor reused with another unread filter");}catch(javax.mail.MessagingException expected){}
  check(reader.smartPage("INBOX",1,"1",new SearchQuery.Filters("{\"unread\":false}")).getInt("nextOffset")==1);reader.close();
  System.out.println("PASS: advanced-search typed filters, inclusive Moscow received dates, invalid dates/header injection rejection, attachment unknown/false isolation, unread filtering, and honest scanned/snapshot pagination.");
 }
}
