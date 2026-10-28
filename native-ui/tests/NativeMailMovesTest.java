package ru.local.spbumailpilot;
import org.json.*;
public final class NativeMailMovesTest {
    interface Check{void run()throws Exception;}
    static void check(boolean value){if(!value)throw new AssertionError();}
    static void reject(Check c)throws Exception{try{c.run();}catch(Exception expected){return;}throw new AssertionError("Expected refusal");}
    public static void main(String[] args)throws Exception {
        String owner="account-"+"a".repeat(64);
        JSONObject mail=new JSONObject().put("folder","Проекты").put("uid","42").put("validity","7");
        NativeMailMoves.allowed(new JSONObject(),owner,mail);
        for(String name:new String[]{"tasks","responseReminders","replyReminders","snoozedMail","eventMailLinks"}) {
            JSONObject state=new JSONObject().put(name,new JSONArray().put(new JSONObject().put("mail",mail)));
            reject(()->NativeMailMoves.allowed(state,owner,mail));
            JSONObject different=new JSONObject(mail.toString()).put("validity","8");NativeMailMoves.allowed(state,owner,different);
            reject(()->NativeMailMoves.allowed(new JSONObject().put(name,"bad"),owner,mail));
        }
        check(NativeMailMoves.confirmed(new JSONObject().put("moved",true).put("destination","Проекты/2026")).equals("Проекты/2026"));
        reject(()->NativeMailMoves.confirmed(new JSONObject().put("moved","true").put("destination","Archive")));
        reject(()->NativeMailMoves.confirmed(new JSONObject().put("moved",true).put("destination","bad\nfolder")));
        System.out.println("PASS move policy: exact mail reference, task/reminder/snooze/event protection, malformed-state refusal, strict acknowledgement");
    }
}
