package ru.local.spbumailpilot;

import org.json.*;
import javax.mail.*;
import java.util.*;

public final class NativeSeenFlagsTest {
    interface Check{void run()throws Exception;}
    static void check(boolean value){if(!value)throw new AssertionError();}
    static void reject(Check action)throws Exception{try{action.run();}catch(Exception expected){return;}throw new AssertionError("Expected rejection");}
    static String owner(char value){char[] chars=new char[64];Arrays.fill(chars,value);return "account-"+new String(chars);}
    public static void main(String[] args)throws Exception {
        String a=owner('a'),b=owner('b');NativeSeenFlags flags=new NativeSeenFlags();
        check(!flags.seen(a,"INBOX","7","42",false));
        JSONObject cache=new JSONObject().put("uid","42").put("folder","INBOX").put("validity","7").put("seen",false);
        JSONObject ack=new JSONObject().put("seen",true);String raw=ack.toString();
        boolean confirmed=NativeSeenFlags.confirmed(ack,true);
        flags.remember(a,"INBOX","7","42",confirmed);
        check(flags.seen(a,"INBOX","7","42",false)&&!cache.getBoolean("seen")&&ack.toString().equals(raw));
        check(!flags.seen(b,"INBOX","7","42",false));
        check(!flags.seen(a,"Archive","7","42",false));
        check(!flags.seen(a,"INBOX","8","42",false));
        check(!flags.seen(a,"INBOX","7","43",false));
        for(JSONObject invalid:new JSONObject[]{null,new JSONObject(),new JSONObject().put("seen","true"),new JSONObject().put("seen",1),new JSONObject().put("seen",false)})
            reject(()->NativeSeenFlags.confirmed(invalid,true));
        check(flags.seen(a,"INBOX","7","42",false));
        reject(()->flags.remember("bad","INBOX","7","42",true));
        reject(()->flags.remember(a,"INBOX\nInjected","7","42",true));
        reject(()->flags.remember(a,"INBOX","0","42",true));
        reject(()->flags.remember(a,"INBOX","7","../42",true));
        // A cursor's original headers stay immutable; adding a page cannot reverse a confirmed flag.
        NativeMailPage first=NativeMailPage.first(new JSONObject().put("folder","INBOX").put("validity","7").put("snapshot","one")
            .put("total",2).put("nextOffset",1).put("hasMore",true).put("messages",new JSONArray().put(cache)));
        NativeMailPage second=first.append(new JSONObject().put("folder","INBOX").put("validity","7").put("snapshot","one")
            .put("total",2).put("nextOffset",2).put("hasMore",false).put("messages",new JSONArray().put(new JSONObject(cache.toString()).put("uid","43"))));
        JSONObject stale=second.messages.getJSONObject(0);
        check(!stale.getBoolean("seen")&&flags.seen(a,"INBOX","7",stale.getString("uid"),stale.getBoolean("seen")));
        // A fresh online observation can supersede the acknowledgement; cached headers cannot.
        flags.remember(a,"INBOX","7","42",false);check(!flags.seen(a,"INBOX","7","42",true));
        flags.remember(a,"Archive","7","42",true);flags.clearFolder(a,"INBOX");
        check(flags.seen(a,"INBOX","7","42",true)&&flags.seen(a,"Archive","7","42",false));
        flags.remember(b,"INBOX","7","42",true);flags.clearOwner(a);
        check(flags.seen(a,"INBOX","7","42",true)&&!flags.seen(a,"INBOX","7","42",false));
        check(flags.seen(b,"INBOX","7","42",false));flags.clear();check(!flags.seen(b,"INBOX","7","42",false));
        check(!new NativeSeenFlags().seen(a,"INBOX","7","42",false)); // no hidden disk writes
        // Actual JavaMail read/write paths against an in-memory folder, with no transport.
        ReadFlagTest.MemoryStore store=new ReadFlagTest.MemoryStore(Session.getInstance(new Properties()));MailReader reader=new MailReader();
        java.lang.reflect.Field field=MailReader.class.getDeclaredField("store");field.setAccessible(true);field.set(reader,store);
        reader.body("INBOX","42","7");check(!store.folder.message.isSet(Flags.Flag.SEEN));
        confirmed=NativeSeenFlags.confirmed(reader.seen("INBOX","42","7",true),true);
        flags.remember(a,"INBOX","7","42",confirmed);check(flags.seen(a,"INBOX","7","42",false));
        reject(()->reader.seen("INBOX","42","999",false));check(store.folder.message.isSet(Flags.Flag.SEEN)&&flags.seen(a,"INBOX","7","42",false));
        confirmed=NativeSeenFlags.confirmed(reader.seen("INBOX","42","7",false),false);
        flags.remember(a,"INBOX","7","42",confirmed);check(!flags.seen(a,"INBOX","7","42",true));
        reader.body("INBOX","42","7");check(!store.folder.message.isSet(Flags.Flag.SEEN));reader.close();
        check(NativeSeenFlags.batchSummary(3,3,0,true).equals("Помечено прочитанными на сервере: 3."));
        check(NativeSeenFlags.batchSummary(3,3,0,false).equals("Помечено непрочитанными на сервере: 3."));
        check(NativeSeenFlags.batchSummary(3,0,3,true).contains("Подтверждено: 0 из 3. Ошибок: 3, не начато: 0."));
        check(NativeSeenFlags.batchSummary(3,2,1,true).contains("Подтверждено: 2 из 3. Ошибок: 1, не начато: 0."));
        check(NativeSeenFlags.batchSummary(4,2,1,true).contains("Подтверждено: 2 из 4. Ошибок: 1, не начато: 1."));
        System.out.println("PASS native read status: strict server acknowledgement, account/folder/UIDVALIDITY/UID isolation, pagination/cache override without mutation, fresh observation and reset, rejected identity/acknowledgement and unchanged state on write failure; real JavaMail folder fixture, no network.");
    }
}
