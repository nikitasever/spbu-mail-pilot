package ru.local.spbumailpilot;
import org.json.*;
public final class NativeWritingSettingsTest {
    interface Attempt {void run()throws Exception;}
    static void check(boolean value){if(!value)throw new AssertionError();}
    static void reject(Attempt a)throws Exception{try{a.run();}catch(Exception expected){return;}throw new AssertionError("Expected rejection");}
    static JSONObject row(String id,String text)throws Exception{return new JSONObject().put("id",id).put("title","Название").put("body",text);}
    public static void main(String[] args)throws Exception{
        JSONObject base=new JSONObject().put("templates",new JSONArray().put(row("one","Исходный")).put(row("two","Другой")))
            .put("settings",new JSONObject().put("signatureText","Привет").put("signatureAuto",false).put("theme","dark"))
            .put("tasks",new JSONArray().put("concurrent task"));
        JSONObject expected=NativeWritingSettings.raw(base,"one");
        JSONObject current=NativeWritingSettings.changeTemplate(base,"two",NativeWritingSettings.raw(base,"two"),row("two","Обновлён"));
        JSONObject saved=NativeWritingSettings.changeTemplate(current,"one",expected,row("one","Новый\n\nтекст"));
        check(saved.getJSONArray("templates").getJSONObject(0).getString("body").equals("Новый\n\nтекст"));
        check(saved.getJSONArray("templates").getJSONObject(1).getString("body").equals("Обновлён"));
        check(saved.getJSONArray("tasks").getString(0).equals("concurrent task"));
        reject(()->NativeWritingSettings.changeTemplate(saved,"one",expected,row("one","lost update")));
        check(NativeWritingSettings.raw(base,"one").getString("body").equals("Исходный"));
        JSONObject deleted=NativeWritingSettings.changeTemplate(saved,"one",NativeWritingSettings.raw(saved,"one"),null);
        check(deleted.getJSONArray("templates").length()==1&&deleted.getJSONArray("templates").getJSONObject(0).getString("id").equals("two"));
        JSONObject signature=NativeWritingSettings.signature(base);
        JSONObject changed=NativeWritingSettings.changeSignature(saved,signature,"--\nПодпись",true);
        check(changed.getJSONObject("settings").getString("theme").equals("dark"));
        check(changed.getJSONArray("templates").length()==2);
        reject(()->NativeWritingSettings.changeSignature(changed,signature,"conflict",false));
        JSONObject toggled=NativeWritingSettings.changeSignature(base,signature,"Привет",true);
        JSONObject merged=NativeWritingSettings.changeSignature(toggled,signature,"Новый текст",false);
        check(merged.getJSONObject("settings").getBoolean("signatureAuto"));
        check(merged.getJSONObject("settings").getString("signatureText").equals("Новый текст"));
        JSONObject empty=new JSONObject();check(NativeWritingSettings.list(empty).isEmpty());
        check(NativeWritingSettings.changeSignature(empty,new JSONObject(),"",true).getJSONObject("settings").getString("signatureText").isEmpty());
        reject(()->NativeWritingSettings.changeSignature(base,signature,"x".repeat(4001),true));
        reject(()->NativeWritingSettings.changeTemplate(base,"new",null,row("new"," ")));
        JSONObject longTitle=row("new","valid").put("title","x".repeat(181));reject(()->NativeWritingSettings.changeTemplate(base,"new",null,longTitle));
        JSONArray many=new JSONArray();for(int i=0;i<110;i++)many.put(row("t-"+i,"Текст"));
        JSONObject old=new JSONObject().put("templates",many);check(NativeWritingSettings.list(old).size()==110);
        reject(()->NativeWritingSettings.changeTemplate(old,"new",null,row("new","Текст")));
        check(NativeWritingSettings.changeTemplate(old,"t-0",NativeWritingSettings.raw(old,"t-0"),row("t-0","Изменён")).getJSONArray("templates").length()==110);
        JSONObject unknown=row("one","Исходный").put("extension",17);
        JSONObject extra=new JSONObject().put("templates",new JSONArray().put(unknown).put("unknown legacy item"));
        check(NativeWritingSettings.changeTemplate(extra,"one",unknown,row("one","Новый")).getJSONArray("templates").getString(1).equals("unknown legacy item"));
        check(NativeWritingSettings.changeTemplate(extra,"one",unknown,row("one","Новый")).getJSONArray("templates").getJSONObject(0).getInt("extension")==17);
        check(NativeWritingSettings.changeSignature(new JSONObject(),new JSONObject(),"Подпись",true).getJSONArray("tasks").length()==0);
        JSONObject duplicates=new JSONObject().put("templates",new JSONArray().put(row("one","a")).put(row("one","b")));reject(()->NativeWritingSettings.raw(duplicates,"one"));
        reject(()->NativeWritingSettings.changeTemplate(new JSONObject().put("templates",12),"new",null,row("new","a")));
        System.out.println("NativeWritingSettingsTest passed");
    }
}
