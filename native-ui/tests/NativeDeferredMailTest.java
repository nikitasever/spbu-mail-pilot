package ru.local.spbumailpilot;
import org.json.*;
import java.util.*;
public final class NativeDeferredMailTest {
    interface Check{void run()throws Exception;}
    static void check(boolean value){if(!value)throw new AssertionError();}
    static void reject(Check call)throws Exception{try{call.run();}catch(Exception expected){return;}throw new AssertionError("Expected refusal");}
    public static void main(String[] args)throws Exception {
        String owner="account-"+"a".repeat(64);long now=1792051200000L;
        JSONObject mail=new JSONObject().put("account",owner).put("folder","INBOX").put("uid","42").put("validity","7").put("messageId","<original@example.org>").put("subject","Материалы");
        JSONObject original=new JSONObject().put("seen",false).put("unknown",42);
        JSONObject snoozed=NativeMailExtras.changeReminder(original,owner,mail,null,now+3600000,now,"snooze");
        JSONArray rows=NativeMailExtras.records(snoozed,owner,"snooze");
        check(NativeMailExtras.hidden(owner,mail,rows,now));check(!NativeMailExtras.hidden(owner,mail,rows,now+3600000));
        check(!NativeMailExtras.hidden(owner,new JSONObject(mail.toString()).put("validity","8"),rows,now));
        check(!snoozed.has("responseReminders")&&!snoozed.has("replyReminders")&&!snoozed.getBoolean("seen")&&snoozed.getInt("unknown")==42);
        JSONObject reply=NativeMailExtras.changeReminder(snoozed,owner,mail,null,now+7200000,now,"followup");
        List<ReplyReminder> planned=ReplyReminder.parse(reply.getJSONArray("replyReminders"),owner);
        check(planned.size()==1&&planned.get(0).messageId.equals("<original@example.org>")&&planned.get(0).at==now+7200000);
        JSONObject personal=NativeMailExtras.changeResponse(reply,owner,mail,null,now+10800000,now);
        check(personal.getJSONArray("responseReminders").length()==1&&personal.getJSONArray("replyReminders").length()==1&&personal.getJSONArray("snoozedMail").length()==1);
        JSONObject expected=NativeMailExtras.reminder(personal,owner,mail,"snooze");
        JSONObject cancelled=NativeMailExtras.changeReminder(personal,owner,mail,expected,null,now,"snooze");
        check(!NativeMailExtras.hidden(owner,mail,cancelled.getJSONArray("snoozedMail"),now)&&cancelled.getJSONArray("replyReminders").length()==1);
        reject(()->NativeMailExtras.changeReminder(cancelled,owner,mail,expected,null,now,"snooze"));
        reject(()->NativeMailExtras.changeReminder(original,owner,new JSONObject(mail.toString()).put("messageId","same subject"),null,now+3600000,now,"followup"));
        reject(()->NativeMailExtras.changeReminder(original,owner,new JSONObject(mail.toString()).put("messageId","<one@x> <two@x>"),null,now+3600000,now,"followup"));
        reject(()->NativeMailExtras.changeReminder(original,owner,new JSONObject(mail.toString()).put("messageId","<a@x>\r\nInjected"),null,now+3600000,now,"followup"));
        reject(()->NativeMailExtras.changeReminder(original,owner,mail,null,now,now,"snooze"));
        reject(()->NativeMailExtras.records(new JSONObject().put("replyReminders",new JSONArray().put(new JSONObject().put("mail",mail).put("at",now+1).put("messageId","bad"))),owner,"followup"));
        System.out.println("PASS deferred mail: independent snooze/personal/followup buckets, expiry/cancel visibility, exact UID scope, strict Message-ID, ReplyReminder compatibility, stale CAS and no Seen changes");
    }
}
