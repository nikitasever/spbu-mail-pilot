package ru.local.spbumailpilot;
import org.json.*;
import java.util.*;
public final class NativeRecipientsTest {
    interface Check{void run()throws Exception;}
    static void check(boolean value){if(!value)throw new AssertionError();}
    static void reject(Check action)throws Exception{try{action.run();}catch(Exception expected){return;}throw new AssertionError("Expected rejection");}
    public static void main(String[] args)throws Exception{
        String prefix="\"Doe, Jane\" <jane@example.invalid>; ";String input=prefix+"иван";
        check(NativeRecipients.query(input,input.length(),false).equals("иван"));
        check(NativeRecipients.query(input,input.length(),true).isEmpty());
        check(NativeRecipients.query(input,3,false).isEmpty());
        check(NativeRecipients.query("и",1,false).isEmpty());
        String comment="Jane (Doe, J.) <jane@example.invalid>, ";
        check(NativeRecipients.boundary(comment+"ив")==comment.length()-1);
        Set<String> used=NativeRecipients.used(input,"COPY@example.invalid","secret@example.invalid","to");
        check(used.contains("jane@example.invalid")&&used.contains("copy@example.invalid")&&used.contains("secret@example.invalid"));
        JSONArray contacts=new JSONArray().put(new JSONObject().put("sender","Иван Иванов").put("email","ivan@example.invalid"))
            .put(new JSONObject().put("sender","Иван Дубликат").put("email","IVAN@example.invalid"))
            .put(new JSONObject().put("sender","Иван копия").put("email","copy@example.invalid"))
            .put(new JSONObject().put("sender","Иван инъекция").put("email","x@example.invalid\r\nBcc: leak@example.invalid"));
        JSONArray matches=NativeRecipients.matches("ИВАН",contacts,used);check(matches.length()==1);
        check(matches.getJSONObject(0).getString("sender").equals("Иван Иванов"));
        String result=NativeRecipients.replace(input,"ivan@example.invalid",used);
        check(result.equals(prefix+"ivan@example.invalid, "));
        javax.mail.internet.InternetAddress[] addresses=OutgoingMail.addresses(result,true);
        check(addresses.length==2&&addresses[0].getAddress().equals("jane@example.invalid")&&addresses[1].getAddress().equals("ivan@example.invalid"));
        reject(()->NativeRecipients.replace(input,"COPY@example.invalid",used));
        reject(()->NativeRecipients.replace(input,"not an address",used));
        reject(()->NativeRecipients.replace(input,"a@example.invalid,b@example.invalid",used));
        check(NativeRecipients.query("\"unfinished, name",17,false).isEmpty());
        check(!NativeRecipients.email("Group:a@example.invalid;"));
        JSONArray many=new JSONArray();for(int i=0;i<50;i++)many.put(new JSONObject().put("sender","Иван "+i).put("email","a"+i+"@example.invalid"));
        check(NativeRecipients.matches("иван",many,Collections.emptySet()).length()==20);
        NativeRecipientEpoch epoch=new NativeRecipientEpoch();
        NativeRecipientEpoch.Ticket old=epoch.begin("accountA","draftA","to","ив");
        NativeRecipientEpoch.Ticket newer=epoch.begin("accountA","draftA","to","иван");
        check(!epoch.current(old)&&epoch.current(newer));
        NativeRecipientEpoch.Ticket cc=epoch.begin("accountA","draftA","cc","иван");check(!epoch.current(newer));
        NativeRecipientEpoch.Ticket account=epoch.begin("accountB","draftA","cc","иван");check(!epoch.current(cc));
        NativeRecipientEpoch.Ticket draft=epoch.begin("accountB","draftB","cc","иван");check(!epoch.current(account));
        epoch.clear();check(!epoch.current(draft)); // pick, background and IME invalidate equally
        NativeRecipientEpoch.Ticket again=epoch.begin("accountB","draftB","cc","иван");check(epoch.current(again)&&!epoch.current(draft));
        System.out.println("PASS native recipients: local Unicode search, duplicates/unsafe addresses excluded, IME and caret guards, quoted/comment prefixes preserved, real MIME address parser, result limit, stale field/account/draft/requery rejected; no network.");
    }
}
