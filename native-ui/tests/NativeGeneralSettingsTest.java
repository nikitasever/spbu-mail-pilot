package ru.local.spbumailpilot;
import org.json.*;
public final class NativeGeneralSettingsTest {
    interface Attempt {void run()throws Exception;}
    static void check(boolean b){if(!b)throw new AssertionError();}
    static void reject(Attempt a)throws Exception{try{a.run();}catch(Exception good){return;}throw new AssertionError("Expected rejection");}
    public static void main(String[] args)throws Exception {
        JSONObject base=new JSONObject().put("settings",new JSONObject().put("theme","dark").put("mailThreads",true).put("unknown","keep")).put("tasks",new JSONArray().put("original")).put("custom",42);
        JSONObject expected=NativeGeneralSettings.options(base);
        JSONObject concurrent=new JSONObject(base.toString());concurrent.getJSONObject("settings").put("signatureText","other");concurrent.getJSONArray("tasks").put("new");
        JSONObject saved=NativeGeneralSettings.change(concurrent,expected,"theme","light");
        check(saved.getJSONObject("settings").getString("signatureText").equals("other")&&saved.getJSONObject("settings").getString("unknown").equals("keep"));
        check(saved.getJSONArray("tasks").length()==2&&saved.getInt("custom")==42);
        check(base.getJSONObject("settings").getString("theme").equals("dark"));
        JSONObject different=NativeGeneralSettings.change(saved,expected,"mailThreads",false);check(!different.getJSONObject("settings").getBoolean("mailThreads"));
        reject(()->NativeGeneralSettings.change(saved,expected,"theme","system"));
        check(NativeGeneralSettings.change(saved,expected,"theme","light").getJSONObject("settings").getString("theme").equals("light"));
        reject(()->NativeGeneralSettings.change(base,expected,"theme","purple"));
        reject(()->NativeGeneralSettings.change(base,expected,"offlineMail","false"));
        reject(()->NativeGeneralSettings.change(base,expected,"unsupported",true));
        reject(()->NativeGeneralSettings.options(new JSONObject().put("settings",new JSONArray())));
        reject(()->NativeGeneralSettings.change(new JSONObject().put("tasks","corrupt"),new JSONObject(),"large",true));
        JSONObject fresh=NativeGeneralSettings.change(new JSONObject(),new JSONObject(),"offlineMail",false);
        check(!fresh.getJSONObject("settings").getBoolean("offlineMail")&&fresh.getJSONArray("tasks").length()==0);
        JSONObject startup=NativeGeneralSettings.change(base,expected,"startScreen","calendar");
        check(startup.getJSONObject("settings").getString("startScreen").equals("calendar"));
        check(startup.getJSONObject("settings").getString("theme").equals("dark"));
        reject(()->NativeGeneralSettings.change(base,expected,"startScreen","tasks"));
        System.out.println("NativeGeneralSettingsTest PASS");
    }
}
