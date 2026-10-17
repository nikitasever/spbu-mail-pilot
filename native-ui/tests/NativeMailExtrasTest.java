package ru.local.spbumailpilot;
import org.json.*;
import java.time.*;
import java.util.*;
public final class NativeMailExtrasTest {
    interface Check{void run()throws Exception;}
    static void check(boolean value){if(!value)throw new AssertionError();}
    static void reject(Check call)throws Exception{try{call.run();}catch(Exception expected){return;}throw new AssertionError("Expected refusal");}
    public static void main(String[] args)throws Exception {
        String owner="account-"+"a".repeat(64);long now=Instant.parse("2026-10-15T08:00:00Z").toEpochMilli();
        JSONObject mail=new JSONObject().put("account",owner).put("folder","Проекты/2026").put("uid","42").put("validity","7").put("subject","Материалы").put("sender","Коллега");
        JSONObject other=new JSONObject(mail.toString()).put("uid","43");
        JSONObject current=new JSONObject().put("unknown",true).put("snoozedMail",new JSONArray()).put("seen",false);
        String key=NativeMailExtras.key(owner,mail);
        JSONObject labelled=NativeMailExtras.changeLabel(current,owner,mail,null,"important");
        check(!current.has("mailLabels")&&labelled.getJSONObject("mailLabels").getString(key).equals("important"));
        JSONObject parallel=NativeMailExtras.changeLabel(labelled,owner,other,null,"urgent");
        JSONObject cleared=NativeMailExtras.changeLabel(parallel,owner,mail,"important","");
        check(cleared.getJSONObject("mailLabels").getString(NativeMailExtras.key(owner,other)).equals("urgent"));
        reject(()->NativeMailExtras.changeLabel(cleared,owner,mail,"important","waiting"));
        reject(()->NativeMailExtras.changeLabel(current,owner,mail,null,"custom"));
        check(NativeMailExtras.at("2026-10-16","09:00",now)==Instant.parse("2026-10-16T06:00:00Z").toEpochMilli());
        reject(()->NativeMailExtras.at("2026-10-15","09:00",now));
        reject(()->NativeMailExtras.at("2026-02-30","12:00",now));
        reject(()->NativeMailExtras.at("2026-10-16","24:00",now));
        JSONObject saved=NativeMailExtras.changeResponse(cleared,owner,mail,null,now+3600000,now);
        check(!saved.getBoolean("seen")&&saved.getBoolean("unknown")&&saved.getJSONArray("snoozedMail").length()==0);
        JSONObject original=NativeMailExtras.response(saved,owner,mail);
        JSONObject withOther=NativeMailExtras.changeResponse(saved,owner,other,null,now+7200000,now);
        JSONObject updated=NativeMailExtras.changeResponse(withOther,owner,mail,original,now+10800000,now);
        check(updated.getJSONArray("responseReminders").length()==2&&NativeMailExtras.response(updated,owner,other).getLong("at")==now+7200000);
        reject(()->NativeMailExtras.changeResponse(updated,owner,mail,original,null,now));
        reject(()->NativeMailExtras.changeResponse(current,"account-"+"b".repeat(64),mail,null,now+3600000,now));
        reject(()->NativeMailExtras.changeResponse(current,owner,mail,null,now,now));
        JSONObject removed=NativeMailExtras.changeResponse(updated,owner,mail,NativeMailExtras.response(updated,owner,mail),null,now);
        check(removed.getJSONArray("responseReminders").length()==1&&NativeMailExtras.response(removed,owner,mail)==null);
        JSONObject full=new JSONObject();JSONArray many=new JSONArray();for(int i=0;i<500;i++)many.put(new JSONObject(original.toString()).put("mail",new JSONObject(mail.toString()).put("uid",Integer.toString(1000+i))));full.put("responseReminders",many);
        reject(()->NativeMailExtras.changeResponse(full,owner,mail,null,now+3600000,now));
        check(NativeMailExtras.changeResponse(full,owner,many.getJSONObject(0).getJSONObject("mail"),many.getJSONObject(0),now+7200000,now).getJSONArray("responseReminders").length()==500);
        JSONArray personal=new JSONArray().put(new JSONObject(original.toString()).put("personal",true));
        MailSnooze.Plan plan=MailSnooze.plan(personal,owner,Collections.emptySet(),now+3600000);
        check(plan.due.size()==1&&plan.due.get(0).personal&&plan.due.get(0).target.folder.equals("Проекты/2026"));
        reject(()->NativeMailExtras.responses(new JSONObject().put("responseReminders",new JSONArray().put(original).put(original)),owner));
        System.out.println("PASS local mail extras: exact labels/reminders and CAS, independent concurrent records, Moscow/future validation, cancellation, notification compatibility, no Seen/snooze mutation");
    }
}
