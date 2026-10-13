package ru.local.spbumailpilot;
import org.json.*;import java.util.*;
public final class ReplyReminderTest {
 static void check(boolean value){if(!value)throw new AssertionError();}
 static JSONObject item(String folder,long at,String id)throws Exception{return new JSONObject().put("mail",new JSONObject().put("folder",folder).put("uid","42").put("validity","7")).put("at",at).put("messageId",id).put("sender","Автор\n<имя>").put("subject","Тема");}
 public static void main(String[] args)throws Exception{
  String account="account-"+String.join("",Collections.nCopies(64,"a")),other="account-"+String.join("",Collections.nCopies(64,"b"));long at=2000000000000L;String id="<first@example.invalid>";
  check(ReplyReminder.validId(id));for(String bad:new String[]{"", "x"+id,"<x\r\n@example.invalid>","<x y@example.invalid>","<x>",id+id})check(!ReplyReminder.validId(bad));
  check(ReplyReminder.references("<previous@example.invalid>\r\n\t"+id,id));check(!ReplyReminder.references("<first@example.invalid.extra>",id));check(!ReplyReminder.references("<First@example.invalid>",id));check(!ReplyReminder.references(String.join("",Collections.nCopies(40000,"x"))+id,id));
  JSONArray items=new JSONArray().put(item("INBOX",at,id)).put(item("Отправленные",at,id)).put(item("INBOX",at+1,id));List<ReplyReminder> parsed=ReplyReminder.parse(items,account);check(parsed.size()==2);check(parsed.get(0).at==at+1);check(!parsed.get(0).key.equals(parsed.get(1).key));check(!parsed.get(0).sender.contains("\n"));check(!ReplyReminder.parse(items,other).get(0).key.equals(parsed.get(0).key));
  check(!ReplyReminder.parse(new JSONArray().put(item("INBOX",at+2,id)),account).get(0).key.equals(parsed.get(0).key));check(ReplyReminder.parse(new JSONArray().put(item("INBOX",0,id)).put(item("bad\nfolder",at,id)).put(item("INBOX",at,"bad")),account).isEmpty());
  JSONArray many=new JSONArray();for(int i=0;i<600;i++)many.put(item("F"+i,at,id));check(ReplyReminder.parse(many,account).size()==500);
  System.out.println("PASS: exact reply identifiers, folded references, prefix/case mismatches, invalid records, replace/account/folder scoping and bounded storage.");
 }
}
