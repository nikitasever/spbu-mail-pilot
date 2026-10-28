package ru.local.spbumailpilot;
import org.json.*;
import java.util.*;

/** Five ordered local categories share the exact notification matcher and existing account JSON. */
final class NativeCategoryRules {
    static final String[] NAMES={"Заявки","Ректорат","События","Учёба","Личное"};
    static final String[] COLORS={"coral","blue","violet","green","pink"};
    private static int index(String id){for(int i=0;i<MailCategories.IDS.length;i++)if(MailCategories.IDS[i].equals(id))return i;throw new IllegalArgumentException("Category ID");}
    static JSONArray saved(JSONObject state)throws Exception {
        if(!state.has("mailCategories"))return new JSONArray();
        JSONArray rows=state.getJSONArray("mailCategories");if(rows.length()>50)throw new IllegalArgumentException("Too many categories");
        Set<String> ids=new HashSet<>();for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);String id=row.getString("id");if(!ids.add(id))throw new IllegalArgumentException("Duplicate category");}
        return rows;
    }
    static JSONObject raw(JSONObject state,String id)throws Exception {
        index(id);JSONArray rows=saved(state);for(int i=0;i<rows.length();i++)if(id.equals(rows.getJSONObject(i).getString("id")))return new JSONObject(rows.getJSONObject(i).toString());return null;
    }
    static JSONArray list(JSONObject state)throws Exception {
        saved(state);JSONArray result=new JSONArray();
        for(int i=0;i<MailCategories.IDS.length;i++){
            String id=MailCategories.IDS[i];JSONObject row=new JSONObject().put("id",id).put("name",NAMES[i]).put("color",COLORS[i])
                .put("subjects",MailCategories.SUBJECTS[i]).put("senders","").put("domains","").put("contacts","").put("enabled",true).put("notify",true);
            JSONObject stored=raw(state,id);if(stored!=null){Iterator<String> keys=stored.keys();while(keys.hasNext()){String key=keys.next();row.put(key,stored.get(key));}}
            row.put("id",id).put("color",COLORS[i]);validate(row);result.put(row);
        }
        return result;
    }
    static void validate(JSONObject row)throws Exception {
        index(row.getString("id"));
        if(!(row.opt("name") instanceof String)||row.getString("name").trim().isEmpty()||row.getString("name").length()>40||row.getString("name").matches("(?s).*\\p{Cntrl}.*"))throw new IllegalArgumentException("Название: 1–40 символов.");
        for(String key:new String[]{"senders","domains","subjects","contacts"}){
            if(!(row.opt(key) instanceof String)||row.getString(key).length()>1000)throw new IllegalArgumentException("Условие: до 1000 символов.");
            for(String part:MailCategories.parts(row.getString(key))) {
                if((key.equals("senders")||key.equals("contacts"))&&!part.matches("\\S+@[^@\\s]+\\.[^@\\s]+"))throw new IllegalArgumentException("Укажите полные адреса через запятую.");
                if(key.equals("domains")&&!part.matches("@?[a-z0-9.-]+\\.[a-z0-9-]+"))throw new IllegalArgumentException("Укажите домены без ссылок, например spbu.ru.");
            }
        }
        if(!(row.opt("enabled") instanceof Boolean)||!(row.opt("notify") instanceof Boolean))throw new IllegalArgumentException("Invalid flags");
    }
    static JSONObject change(JSONObject current,String id,JSONObject expected,JSONObject editor)throws Exception {
        int position=index(id);if(!id.equals(editor.getString("id")))throw new IllegalArgumentException("Identity changed");
        JSONObject row=new JSONObject(editor.toString());for(String key:new String[]{"name","senders","domains","subjects","contacts"})row.put(key,row.getString(key).trim());
        row.put("color",COLORS[position]);validate(row);
        if(!SharedStateMerge.same(raw(current,id),expected))throw new SharedStateMerge.Conflict();
        JSONArray next=new JSONArray();boolean replaced=false;JSONArray original=saved(current);
        for(int i=0;i<original.length();i++){JSONObject old=original.getJSONObject(i);if(id.equals(old.getString("id"))){next.put(row);replaced=true;}else next.put(new JSONObject(old.toString()));}
        if(!replaced){if(next.length()>=50)throw new IllegalArgumentException("Too many categories");next.put(row);}
        JSONObject result=new JSONObject(current.toString()).put("mailCategories",next);
        for(String key:new String[]{"tasks","notes","events"}){if(!result.has(key))result.put(key,new JSONArray());else if(!(result.opt(key) instanceof JSONArray))throw new IllegalArgumentException("Malformed collection");}
        return result;
    }
    static String summary(JSONObject row){
        StringBuilder result=new StringBuilder();String[] keys={"subjects","senders","domains","contacts"},labels={"Тема","От","Домен","Контакты"};
        for(int i=0;i<keys.length;i++){List<String> parts=MailCategories.parts(row.optString(keys[i]));if(parts.isEmpty())continue;if(result.length()>0)result.append(" · ");result.append(labels[i]).append(": ").append(String.join(", ",parts.subList(0,Math.min(3,parts.size()))));if(parts.size()>3)result.append(" …");}
        return result.length()==0?"Правила пока не заданы":result.toString();
    }
}
