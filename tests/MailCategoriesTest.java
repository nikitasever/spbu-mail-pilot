package ru.local.spbumailpilot;
import org.json.*;
import java.util.*;
public class MailCategoriesTest{
 static void check(boolean v){if(!v)throw new AssertionError();}
 public static void main(String[] args)throws Exception{
  JSONObject request=new JSONObject().put("id","requests").put("subjects","заявк, Ticket").put("domains","@spbu.ru").put("notify",false),personal=new JSONObject().put("id","personal").put("contacts","Friend@Example.invalid");MailCategories c=new MailCategories(new JSONArray().put(request).put(personal));
  check(!c.allowed("staff@SPBU.RU","Привет"));check(c.allowed("evil@sub.spbu.ru","Привет"));check(!c.allowed("evil@spbu.ru.evil.invalid","Заявка № 1"));check(c.allowed("evil@spbu.ru.evil.invalid","Привет"));check(c.match("friend@example.invalid","Личное").getString("id").equals("personal"));check(c.match("friend@example.invalid","Ticket 3").getString("id").equals("requests"));
  JSONObject update=new JSONObject().put("validity","7").put("uid",100).put("count",3).put("openUid","100").put("noticeMessages",new JSONArray().put(new JSONObject().put("uid","98").put("email","sender@example.invalid").put("sender","Автор").put("subject","Привет")).put(new JSONObject().put("uid","99").put("email","friend@example.invalid").put("sender","Друг").put("subject","Личное")).put(new JSONObject().put("uid","100").put("email","staff@spbu.ru").put("sender","Работа").put("subject","Заявка")));
  JSONObject filtered=c.filter(update);check(filtered.getInt("count")==2&&filtered.getLong("uid")==100&&filtered.getString("openUid").equals("99"));check(!filtered.getString("summary").contains("Заявка"));check(update.getInt("count")==3&&update.getString("openUid").equals("100"));
  JSONObject empty=c.filter(new JSONObject(update.toString()).put("noticeMessages",new JSONArray().put(update.getJSONArray("noticeMessages").getJSONObject(2))));check(empty.getInt("count")==0&&!empty.has("openUid"));request.put("enabled",false);check(new MailCategories(new JSONArray().put(request)).allowed("staff@spbu.ru","Заявка"));
  JSONArray reminders=new JSONArray().put(new JSONObject().put("mail",new JSONObject().put("folder","INBOX").put("uid","42").put("validity","7")).put("at",100).put("sender","Автор").put("subject","Письмо"));String account="account-"+String.join("",Collections.nCopies(64,"a"));MailSnooze normal=MailSnooze.parse(reminders,account).get(0);reminders.getJSONObject(0).put("personal",true);MailSnooze response=MailSnooze.parse(reminders,account).get(0);check(response.personal&&!normal.personal&&!response.key.equals(normal.key));check(MailSnooze.plan(reminders,account,Collections.emptySet(),101).due.size()==1);check(MailSnooze.plan(reminders,account,Collections.singleton(response.key),101).due.isEmpty());
  System.out.println("PASS: literal category priority/domain boundary/contact rules, notification filtering with watermark/target/source isolation, and independent personal-reminder dedupe.");
 }
}
