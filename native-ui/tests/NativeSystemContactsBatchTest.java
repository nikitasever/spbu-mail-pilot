package ru.local.spbumailpilot;
import org.json.*;
import java.util.*;
final class NativeSystemContactsBatchTest {
    interface Action{void run()throws Exception;}
    static void check(boolean value){if(!value)throw new AssertionError();}
    static void reject(Action action)throws Exception{try{action.run();throw new AssertionError("Unbounded batch accepted");}catch(Exception expected){}}
    public static void main(String[] args)throws Exception{
        NativeSystemContactsBatch batch=new NativeSystemContactsBatch();
        batch.add("1","email","Иван","work@example.invalid");batch.add("1","email","Иван","home@example.invalid");
        batch.add("1","phone","Иван"," ");batch.add("1","phone","Иван","+7 111");batch.add("1","phone","Иван","+7 222");
        batch.add("2","phone","Phone only","+7 333");batch.add("3","email","Bad","invalid");
        List<JSONObject> entries=batch.entries();check(entries.size()==3&&entries.get(0).getString("number").equals("+7 111")&&entries.get(1).getString("number").equals("+7 111"));
        check(!entries.get(0).has("contact"));entries.get(0).put("email","tampered");check(batch.entries().get(0).getString("email").equals("work@example.invalid"));
        NativeContactImport.Preview preview=NativeContactImport.previewEntries(batch.entries(),new JSONObject());check(preview.added==2&&preview.invalidLines.size()==1);
        NativeSystemContactsBatch many=new NativeSystemContactsBatch();
        for(int i=0;i<NativeContactImport.MAX_CONTACTS;i++)many.add("1","email","A","user"+i+"@example.invalid");
        reject(()->many.add("1","email","A","overflow@example.invalid"));
        NativeSystemContactsBatch rows=new NativeSystemContactsBatch();for(int i=0;i<NativeSystemContactsBatch.MAX_ROWS;i++)rows.add("1","phone","A","+7 111");
        reject(()->rows.add("1","phone","A","+7 111"));reject(()->new NativeSystemContactsBatch().add(null,"email","A","a@example.invalid"));
        System.out.println("PASS system contact batch: all emails joined with first phone, phone-only exclusion, immutable metadata, invalid record disclosure and explicit row/email bounds without silent truncation");
    }
}
