package ru.local.spbumailpilot;
import org.json.JSONObject;
public final class MailNotificationTextTest {
 static void check(boolean value){if(!value)throw new AssertionError();}
 public static void main(String[] args)throws Exception{
  JSONObject one=new JSONObject().put("count",1).put("sender","Иван Иванов").put("subject","Документы");check(MailNotificationText.title(one).equals("Иван Иванов"));check(MailNotificationText.text(one,"me@spbu.ru").equals("Документы\nme@spbu.ru"));
  one.put("sender","").put("email","from@spbu.ru");check(MailNotificationText.title(one).equals("from@spbu.ru"));one.remove("email");check(MailNotificationText.title(one).equals("Неизвестный отправитель"));
  check(MailNotificationText.clean("А\nБ\u202eВ","",180).equals("А Б В"));String emoji=new String(new char[180]).replace('\0','А')+"😀";check(!MailNotificationText.clean(emoji,"",181).contains("\ufffd"));
  JSONObject many=new JSONObject().put("count",3).put("summary","Иван · Встреча\nАнна · Документы");check(MailNotificationText.title(many).equals("Новые письма · 3"));check(MailNotificationText.text(many,"me").startsWith("Иван · Встреча"));
  System.out.println("PASS: notification sender/title/address fallback, subject/account, multi-sender summary, control cleanup and Unicode-safe limits.");
 }
}
