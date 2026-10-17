package ru.local.spbumailpilot;
import org.json.*;
public final class NativeMailSearchTest {
    interface Check {void run()throws Exception;}
    static void check(boolean value){if(!value)throw new AssertionError();}
    static void reject(Check action)throws Exception{try{action.run();}catch(Exception expected){return;}throw new AssertionError("Expected refusal");}
    public static void main(String[] args)throws Exception{
        check(NativeMailSearch.query("  защита  ").equals("защита"));check(NativeMailSearch.query("   ").isEmpty());
        reject(()->NativeMailSearch.query("x\nFROM other"));reject(()->NativeMailSearch.query("x".repeat(201)));
        check(NativeMailSearch.matches("all",false,null));check(!NativeMailSearch.matches("attachments",true,null));
        check(!NativeMailSearch.matches("attachments",true,false));check(NativeMailSearch.matches("attachments",false,true));
        check(NativeMailSearch.matches("unread",true,null));reject(()->NativeMailSearch.matches("unknown",false,true));
        check(!NativeMailSearch.sameCursor("a","INBOX","first","a","INBOX","second"));
        check(!NativeMailSearch.sameCursor("a","INBOX","same","b","INBOX","same"));
        check(!NativeMailSearch.sameCursor("a","INBOX","same","a","Sent","same"));
        // Reuse the actual offline mailbox search: only cached sender/email/subject are searchable.
        OfflineMailbox cache=new OfflineMailbox();JSONArray rows=new JSONArray();
        for(int i=1;i<=65;i++)rows.put(new JSONObject().put("uid",""+i).put("validity","7").put("folder","INBOX").put("subject",i%2==0?"Защита":"Другое").put("sender","Коллега").put("email","colleague@example.org"));
        cache.page("INBOX",0,new JSONObject().put("messages",rows).put("validity","7"));
        JSONObject found=cache.page("INBOX",0,"",NativeMailSearch.query("ЗАЩИТА"));
        check(found.getInt("total")==32&&found.getJSONArray("messages").length()==32&&!found.getBoolean("hasMore"));
        JSONObject first=cache.page("INBOX",0,"","");NativeMailPage page=NativeMailPage.first(first,"INBOX");
        page=page.append(cache.page("INBOX",page.next,page.snapshot,""));check(page.messages.length()==65&&!page.more);
        check(cache.page("Sent",0,"","защита")==null);
        // A changed snapshot cannot silently extend a previously displayed query result.
        cache.page("INBOX",0,new JSONObject().put("messages",rows).put("validity","7"));
        check(cache.page("INBOX",50,first.getString("snapshot"),"")==null);
        System.out.println("NativeMailSearchTest passed");
    }
}
