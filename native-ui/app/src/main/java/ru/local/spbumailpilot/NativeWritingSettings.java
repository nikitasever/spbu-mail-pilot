package ru.local.spbumailpilot;

import org.json.*;
import java.util.*;

/** Account JSON policy. Call mutations inside the shared ReplyReminderCenter lock. */
final class NativeWritingSettings {
    static JSONArray rows(JSONObject state)throws Exception {
        if(!state.has("templates"))return new JSONArray();
        return state.getJSONArray("templates");
    }
    static List<NativeWritingTools.Template> list(JSONObject state)throws Exception {
        List<NativeWritingTools.Template> result=new ArrayList<>();Set<String> ids=new HashSet<>();
        JSONArray rows=rows(state);
        for(int i=0;i<rows.length();i++) {
            JSONObject row=rows.optJSONObject(i);if(row==null)continue;
            try {validate(row);String id=row.getString("id");if(ids.add(id))result.add(new NativeWritingTools.Template(id,row.getString("title"),row.getString("body")));}catch(IllegalArgumentException|JSONException ignored){}
        }
        return result;
    }
    static JSONObject raw(JSONObject state,String id)throws Exception {
        JSONObject found=null;JSONArray rows=rows(state);
        for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row!=null&&id.equals(row.optString("id"))){if(found!=null)throw new SharedStateMerge.Conflict();found=new JSONObject(row.toString());}}
        return found;
    }
    static void validate(JSONObject row)throws Exception {
        if(!(row.opt("id") instanceof String)||!row.getString("id").matches("[A-Za-z0-9-]{1,100}"))throw new IllegalArgumentException("Некорректный идентификатор шаблона.");
        if(!(row.opt("title") instanceof String)||row.getString("title").trim().isEmpty())throw new IllegalArgumentException("Введите название шаблона.");
        if(!(row.opt("body") instanceof String)||row.getString("body").trim().isEmpty())throw new IllegalArgumentException("Введите текст шаблона.");
        if(row.getString("title").length()>180||row.getString("body").length()>100000)throw new IllegalArgumentException("Сократите название до 180, текст до 100 000 символов.");
    }
    static JSONObject signature(JSONObject state)throws Exception {
        JSONObject settings=state.has("settings")?state.getJSONObject("settings"):new JSONObject();JSONObject result=new JSONObject();
        for(String key:new String[]{"signatureText","signatureAuto"})if(settings.has(key))result.put(key,settings.get(key));return result;
    }
    static JSONObject changeSignature(JSONObject current,JSONObject expected,String text,boolean auto)throws Exception {
        if(text.length()>4000)throw new IllegalArgumentException("Сократите подпись до 4000 символов.");
        JSONObject now=signature(current),next=new JSONObject(current.toString());
        JSONObject settings=next.has("settings")?next.getJSONObject("settings"):new JSONObject();
        // Compare only the fields this editor changes; concurrent unrelated settings survive.
        for(String key:new String[]{"signatureText","signatureAuto"}){
            Object desired=key.equals("signatureText")?text:auto;
            Object initial=key.equals("signatureText")?(expected.opt(key) instanceof String?expected.optString(key):""):!Boolean.FALSE.equals(expected.opt(key));
            if(SharedStateMerge.same(initial,desired)){
                if(!now.has(key))settings.put(key,desired);
                continue;
            }
            if(!SharedStateMerge.same(now.opt(key),expected.opt(key))&&!SharedStateMerge.same(now.opt(key),desired))throw new SharedStateMerge.Conflict();
            settings.put(key,desired);
        }
        return NativeSharedState.ready(next.put("settings",settings));
    }
    static JSONObject changeTemplate(JSONObject current,String id,JSONObject expected,JSONObject editor)throws Exception {
        if(editor!=null){validate(editor);if(!id.equals(editor.getString("id")))throw new IllegalArgumentException("Identity changed");}
        if(!SharedStateMerge.same(raw(current,id),expected))throw new SharedStateMerge.Conflict();
        JSONArray source=rows(current),next=new JSONArray();boolean found=false;
        if(expected==null&&editor!=null&&source.length()>=100)throw new IllegalArgumentException("Можно сохранить до 100 шаблонов. Удалите ненужный перед добавлением.");
        for(int i=0;i<source.length();i++){
            Object old=source.get(i);
            if(old instanceof JSONObject&&id.equals(((JSONObject)old).optString("id"))){found=true;if(editor!=null)next.put(new JSONObject(((JSONObject)old).toString()).put("id",id).put("title",editor.getString("title").trim()).put("body",editor.getString("body")));}
            else next.put(old);
        }
        if(!found&&editor!=null)next.put(new JSONObject(editor.toString()).put("title",editor.getString("title").trim()));
        return NativeSharedState.ready(new JSONObject(current.toString()).put("templates",next));
    }
}
