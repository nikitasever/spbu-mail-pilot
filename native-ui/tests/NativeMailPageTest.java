package ru.local.spbumailpilot;
import org.json.*;
public final class NativeMailPageTest {
    interface Check{void run()throws Exception;}
    static void check(boolean v){if(!v)throw new AssertionError();}
    static void reject(Check c)throws Exception{try{c.run();}catch(Exception expected){return;}throw new AssertionError("Expected rejection");}
    static JSONObject page(String folder,String validity,String snapshot,int offset,int total,boolean holes)throws Exception{
        JSONArray rows=new JSONArray();int next=Math.min(total,offset+50);
        if(!holes)for(int n=offset;n<next;n++)rows.put(new JSONObject().put("folder",folder).put("validity",validity).put("uid",String.valueOf(total-n)).put("subject","fixture"));
        return new JSONObject().put("folder",folder).put("validity",validity).put("snapshot",snapshot).put("nextOffset",next).put("total",total).put("hasMore",next<total).put("messages",rows);
    }
    public static void main(String[] args)throws Exception{
        NativeMailPage first=NativeMailPage.first(page("INBOX","7","1",0,125,false));
        check(first.messages.length()==50&&first.next==50&&first.more);
        NativeMailPage second=first.append(page("INBOX","7","1",50,125,false));
        NativeMailPage last=second.append(page("INBOX","7","1",100,125,false));
        check(last.messages.length()==125&&!last.more&&last.next==125);
        check(last.messages.getJSONObject(0).getString("uid").equals("125"));
        check(last.messages.getJSONObject(124).getString("uid").equals("1"));
        reject(()->last.append(page("INBOX","7","1",125,125,false)));
        for(JSONObject changed:new JSONObject[]{page("Archive","7","1",50,125,false),page("INBOX","8","1",50,125,false),page("INBOX","7","2",50,125,false),page("INBOX","7","1",50,126,false)})reject(()->first.append(changed));
        reject(()->first.append(page("INBOX","7","1",0,125,false)));
        JSONObject wrong=page("INBOX","7","1",50,125,false);wrong.getJSONArray("messages").getJSONObject(0).put("validity","8");reject(()->first.append(wrong));
        NativeMailPage empty=first.append(page("INBOX","7","1",50,125,true));
        check(empty.messages.length()==50&&empty.next==100&&empty.more);
        check(empty.append(page("INBOX","7","1",100,125,false)).messages.length()==75);
        JSONObject overlap=page("INBOX","7","1",50,125,false);overlap.getJSONArray("messages").getJSONObject(0).put("uid","125");
        check(first.append(overlap).messages.length()==99);
        check(first.messages.length()==50&&first.next==50,"failed append must leave loaded rows/cursor unchanged");
        NativeMailPage offline=NativeMailPage.first(page("INBOX","7","offline:1:7",0,70,false));
        check(offline.append(page("INBOX","7","offline:1:7",50,70,false)).messages.length()==70);
        reject(()->offline.append(page("INBOX","7","offline:2:7",50,70,false)));
        System.out.println("PASS native paging: 125 messages in 50/50/25 pages, stable order, duplicate suppression, empty expunged page, folder/UIDVALIDITY/snapshot/offset isolation, offline revision changes, failure retains cursor; no network.");
    }
    static void check(boolean v,String message){if(!v)throw new AssertionError(message);}
}
