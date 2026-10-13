package ru.local.spbumailpilot;
import org.json.*;
import java.util.*;
public final class MailSnoozeTest {
 static void check(boolean value){if(!value)throw new AssertionError();}
 static JSONObject item(String folder,long at)throws Exception{return new JSONObject().put("mail",new JSONObject().put("folder",folder).put("uid","42").put("validity","7")).put("at",at).put("sender","Автор\n<имя>").put("subject","Тема");}
 public static void main(String[] args)throws Exception{
  String account="account-"+String.join("",Collections.nCopies(64,"a")),other="account-"+String.join("",Collections.nCopies(64,"b"));long at=2000000000000L;
  JSONArray values=new JSONArray().put(item("INBOX",at)).put(item("Архив/Работа",at)).put(item("INBOX",at+1));
  List<MailSnooze> parsed=MailSnooze.parse(values,account);check(parsed.size()==2);
  MailSnooze inbox=parsed.get(0),archive=parsed.get(1);check(inbox.at==at+1&&archive.target.folder.equals("Архив/Работа"));check(!inbox.key.equals(archive.key));check(!inbox.sender.contains("\n"));
  check(MailNoticeTarget.parse(archive.target.encode()).reference().getString("folder").equals("Архив/Работа"));
  check(!MailSnooze.parse(new JSONArray().put(item("INBOX",at+1)),other).get(0).key.equals(inbox.key));
  for(String folder:new String[]{"","bad\nfolder","bad\u0000folder"})check(MailSnooze.parse(new JSONArray().put(item(folder,at)),account).isEmpty());
  check(MailSnooze.parse(new JSONArray().put(item("INBOX",0)).put(item("INBOX",-1)).put(new JSONObject()).put(item("INBOX",at).put("mail",new JSONObject().put("uid","0"))),account).isEmpty());
  check(!MailSnooze.parse(new JSONArray().put(item("INBOX",at)),account).get(0).key.equals(inbox.key));
  MailSnooze.Plan future=MailSnooze.plan(values,account,Collections.emptySet(),at-1);check(future.due.isEmpty()&&future.next==at&&future.current.size()==2);
  MailSnooze.Plan due=MailSnooze.plan(values,account,Collections.emptySet(),at);check(due.due.size()==1&&due.due.get(0).target.folder.equals("Архив/Работа")&&due.next==at+1);
  Set<String> sent=new HashSet<>();for(MailSnooze v:MailSnooze.plan(values,account,sent,at+1).due)sent.add(v.key);
  check(MailSnooze.plan(values,account,sent,at+100).due.isEmpty());
  check(MailSnooze.plan(new JSONArray().put(item("INBOX",at+200)),account,sent,at+100).next==at+200);
  check(MailSnooze.plan(new JSONArray(),account,sent,at+100).current.isEmpty());
  JSONArray many=new JSONArray();for(int i=0;i<600;i++)many.put(item("F"+i,at));check(MailSnooze.parse(many,account).size()==500);
  System.out.println("PASS: mail snooze exact account/folder/UID/UIDVALIDITY, replacement keys, invalid records, bounded storage and safe notification copy.");
 }
}
