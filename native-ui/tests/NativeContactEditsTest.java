package ru.local.spbumailpilot;
import java.nio.file.*;
import org.json.*;

public final class NativeContactEditsTest {
    interface Check {void run()throws Exception;}
    static void check(boolean value){if(!value)throw new AssertionError();}
    static void reject(Check action)throws Exception{try{action.run();}catch(Exception expected){return;}throw new AssertionError("Expected refusal");}
    static JSONObject person(String email,String name,String number)throws Exception{return new JSONObject().put("email",email).put("sender",name).put("number",number);}
    public static void main(String[] args)throws Exception {
        if(args.length>0){
            JSONArray fixtures=new JSONArray(Files.readString(Path.of(args[0]))),results=new JSONArray();
            for(int i=0;i<fixtures.length();i++) {
                JSONObject fixture=fixtures.getJSONObject(i),current=fixture.getJSONObject("current"),result=current;
                try{result=NativeContactEdits.change(current,fixture.optJSONObject("expected"),fixture.optJSONObject("replacement"));}catch(Exception rejected){}
                results.put(result.optJSONArray("contacts"));
            }
            check(NativeContactEdits.change(new JSONObject(),null,person("first@example.invalid","First","")).getJSONArray("events").length()==0);
        JSONObject historyState=new JSONObject().put("contacts",new JSONArray().put(person("saved@example.invalid","Saved",""))).put("recipientBook",new JSONArray().put(person("recent@example.invalid","Recent",""))).put("phoneContacts",new JSONArray()).put("settings",new JSONObject().put("theme","dark"));
        String historyBefore=historyState.toString();JSONArray expectedHistory=new JSONArray(historyState.getJSONArray("recipientBook").toString());
        JSONObject cleared=NativeContactEdits.clearRecipientHistory(historyState,expectedHistory);
        check(cleared.getJSONArray("recipientBook").length()==0&&cleared.getJSONArray("contacts").length()==1&&cleared.getJSONObject("settings").getString("theme").equals("dark"));
        check(historyState.toString().equals(historyBefore));
        JSONObject advancedHistory=new JSONObject(historyBefore);advancedHistory.getJSONArray("recipientBook").put(person("new@example.invalid","New",""));
        try{NativeContactEdits.clearRecipientHistory(advancedHistory,expectedHistory);throw new AssertionError("New history cleared");}catch(SharedStateMerge.Conflict expected){}
        System.out.println(results);return;
        }
        JSONObject a=person("a@example.invalid","Alice","+7 123").put("custom",new JSONObject().put("kept",true));
        JSONObject b=person("b@example.invalid","Bob","");
        JSONObject current=new JSONObject().put("contacts",new JSONArray().put(a).put(b)).put("recipientBook",new JSONArray().put(a))
            .put("phoneContacts",new JSONArray().put(person("phone@example.invalid","Phone","123"))).put("settings",new JSONObject().put("theme","dark"));
        JSONObject edited=NativeContactEdits.change(current,a,person("A2@example.invalid"," Renamed "," 456 "));
        check(edited.getJSONArray("contacts").length()==2&&edited.getJSONArray("contacts").getJSONObject(0).getString("sender").equals("Renamed"));
        check(edited.getJSONArray("contacts").getJSONObject(0).getJSONObject("custom").getBoolean("kept"));
        check(SharedStateMerge.same(edited.get("settings"),current.get("settings"))&&SharedStateMerge.same(edited.get("recipientBook"),current.get("recipientBook")));
        check(current.getJSONArray("contacts").getJSONObject(0).getString("sender").equals("Alice"));
        check(NativeContactEdits.raw(edited,"a2@EXAMPLE.INVALID").getString("number").equals("456"));
        check(NativeContactEdits.number(current,"PHONE@example.invalid").equals("123"));
        JSONObject unrelated=NativeContactEdits.change(current,b,person("b@example.invalid","Concurrent Bob",""));
        check(NativeContactEdits.change(unrelated,a,person("a@example.invalid","New Alice","")).getJSONArray("contacts").getJSONObject(1).getString("sender").equals("Concurrent Bob"));
        reject(()->NativeContactEdits.change(edited,a,person("a@example.invalid","Stale", "")));
        reject(()->NativeContactEdits.change(edited,a,null));
        reject(()->NativeContactEdits.change(current,null,person("A@example.invalid","Duplicate", "")));
        reject(()->NativeContactEdits.change(current,a,person("B@example.invalid","Duplicate", "")));
        JSONObject removed=NativeContactEdits.change(current,a,null);
        check(removed.getJSONArray("contacts").length()==1&&removed.getJSONArray("recipientBook").length()==1);
        reject(()->NativeContactEdits.change(new JSONObject().put("contacts",new JSONArray().put(a).put(a)),a,null));
        reject(()->NativeContactEdits.change(new JSONObject().put("contacts","bad"),null,b));
        for(String email:new String[]{"", "broken", "a@b@c", "a\nb@example.invalid", "a@example.invalid,b@example.invalid", "<a@example.invalid>"})reject(()->NativeContactEdits.fields(person(email,"Name", "")));
        reject(()->NativeContactEdits.fields(person("a@example.invalid","Bad\nName", "")));
        reject(()->NativeContactEdits.fields(person("a@example.invalid","Name", "0".repeat(61))));
        check(NativeContactEdits.fields(person(" a@example.invalid "," "," ")).getString("sender").equals("a@example.invalid"));
        check(NativeContactEdits.change(new JSONObject(),null,person("first@example.invalid","First","")).getJSONArray("events").length()==0);
        System.out.println("NativeContactEditsTest passed: CRUD, record CAS, independent edits, duplicates, validation, unknown fields and source isolation");
    }
}
