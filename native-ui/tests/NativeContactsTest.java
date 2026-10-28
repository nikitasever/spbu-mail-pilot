package ru.local.spbumailpilot;
import org.json.*;
import java.util.*;
public final class NativeContactsTest {
    interface Check {void run()throws Exception;}
    static void check(boolean value){if(!value)throw new AssertionError();}
    static void reject(Check action)throws Exception{try{action.run();}catch(Exception expected){return;}throw new AssertionError("Expected refusal");}
    static JSONObject person(String name,String email)throws Exception{return new JSONObject().put("sender",name).put("email",email);}
    public static void main(String[] args)throws Exception {
        check(NativeContacts.ALPHABET.contains("Ё")&&NativeContacts.ALPHABET.contains("П")&&NativeContacts.ALPHABET.contains("Я"));
        check(NativeContacts.query("ignored","П").equals("^:П"));check(NativeContacts.query("  Кузнецов ","").equals("Кузнецов"));
        reject(()->NativeContacts.query("^:П",""));reject(()->NativeContacts.query("","ПС"));reject(()->NativeContacts.query("x\ny",""));
        JSONArray first=new JSONArray().put(person("Сидоров","sid@example.org"));
        JSONArray second=new JSONArray().put(person("Дубль","SID@example.org")).put(person("Петров","pet@example.org")).put(person("Плохой","x\r\n@example.org")).put(person("Тест\n","test@example.org"));
        JSONArray merged=NativeContacts.merge(first,second);check(merged.length()==3&&merged.getJSONObject(0).getString("sender").equals("Сидоров"));
        check(merged.getJSONObject(2).getString("sender").equals("test@example.org"));check(first.length()==1);
        JSONObject data=new JSONObject().put("contacts",first).put("recipientBook",second);
        check(NativeContacts.local(data,"^:П").length()==1);check(NativeContacts.local(data,"SID@").length()==1);
        JSONArray large=new JSONArray();for(int i=0;i<600;i++)large.put(person("Пользователь "+i,"user"+i+"@example.org"));
        check(NativeContacts.local(new JSONObject().put("contacts",large),"^:П").length()==600);
        JSONObject page=new JSONObject().put("cookie","next").put("hasMore",true);
        check(NativeContacts.next(page,"",Collections.emptySet()).equals("next"));
        reject(()->NativeContacts.next(page,"next",Collections.emptySet()));reject(()->NativeContacts.next(page,"previous",Collections.singleton("next")));
        reject(()->NativeContacts.next(new JSONObject().put("cookie","").put("hasMore",true),"",Collections.emptySet()));
        reject(()->NativeContacts.next(new JSONObject().put("cookie","x\n").put("hasMore",true),"",Collections.emptySet()));
        check(NativeContacts.next(new JSONObject().put("cookie","").put("hasMore",false),"previous",Collections.emptySet()).isEmpty());
        String owner="account-"+"a".repeat(64);
        NativeDraft draft=NativeDraft.addressed(owner,"pet@example.org","Signature");
        check(draft.mail.getString("to").equals("pet@example.org")&&draft.mail.getString("body").equals("Signature"));
        check(draft.mail.getString("cc").isEmpty()&&draft.mail.getString("bcc").isEmpty()&&draft.mail.getString("inReplyTo").isEmpty()&&draft.mail.getJSONArray("attachments").length()==0);
        check(NativeDraft.parse(owner,draft.json().toString()).mail.getString("to").equals("pet@example.org"));
        reject(()->NativeDraft.addressed(owner,"pet@example.org,evil@example.org",""));
        reject(()->NativeDraft.addressed(owner,"pet@example.org\r\nBcc: evil@example.org",""));
        JSONObject history=new JSONObject().put("contacts",new JSONArray().put(person("Saved","saved@example.invalid"))).put("phoneContacts",new JSONArray().put(person("Phone","phone@example.invalid"))).put("recipientBook",new JSONArray().put(person("Recent","recent@example.invalid")).put(person("Duplicate","RECENT@example.invalid")));
        check(NativeContacts.history(history,"").length()==1&&NativeContacts.history(history,"Recent").getJSONObject(0).getString("email").equals("recent@example.invalid"));
        check(NativeContacts.history(history,"Saved").length()==0&&NativeContacts.history(history,"^:R").length()==1);
        System.out.println("NativeContactsTest passed");
    }
}
