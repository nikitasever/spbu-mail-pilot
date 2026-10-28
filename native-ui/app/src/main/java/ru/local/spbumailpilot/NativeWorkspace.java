package ru.local.spbumailpilot;
import org.json.*;
import java.time.*;
import java.util.*;

/** Record-level optimistic edits over fresh shared state. Unknown fields/other collections survive. */
final class NativeWorkspace {
    static void kind(String kind){if(!kind.equals("tasks")&&!kind.equals("notes")&&!kind.equals("events"))throw new IllegalArgumentException("Collection");}
    static JSONArray rows(JSONObject data,String kind)throws Exception {
        kind(kind);if(data.has(kind)&&!(data.opt(kind) instanceof JSONArray))throw new IllegalArgumentException("Malformed collection");
        JSONArray rows=data.optJSONArray(kind);if(rows==null)return new JSONArray();
        if(rows.length()>5000)throw new IllegalArgumentException("Слишком много записей.");
        Set<String> ids=new HashSet<>();for(int i=0;i<rows.length();i++){
            String id=rows.getJSONObject(i).getString("id");
            if(id.isEmpty()||id.length()>200||id.matches("(?s).*\\p{Cntrl}.*")||!ids.add(id))throw new IllegalArgumentException("Invalid or duplicate ID");
        }
        return rows;
    }
    static void validate(String kind,JSONObject item)throws Exception {
        kind(kind);String id=item.getString("id"),title=item.getString("title");
        if(id.isEmpty()||id.length()>200||id.matches("(?s).*\\p{Cntrl}.*"))throw new IllegalArgumentException("ID");
        if(title.trim().isEmpty()||title.length()>500||title.matches("(?s).*\\p{Cntrl}.*"))throw new IllegalArgumentException("Укажите название до 500 символов.");
        if(kind.equals("notes")){if(item.optString("body").length()>100000)throw new IllegalArgumentException("Текст слишком длинный.");return;}
        String day=item.optString("date"),time=item.optString("time");
        if(kind.equals("events")&&(day.isEmpty()||item.optString("kind").equals("remote")||!item.optString("calendarId").isEmpty()))throw new IllegalArgumentException("Укажите дату локального события.");
        if(!day.isEmpty()&&(!day.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")||!LocalDate.parse(day).toString().equals(day)))throw new IllegalArgumentException("Дата: ГГГГ-ММ-ДД.");
        if(!time.isEmpty()&&(day.isEmpty()||!time.matches("[0-9]{2}:[0-9]{2}")||!LocalTime.parse(time).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")).equals(time)))throw new IllegalArgumentException("Время: ЧЧ:ММ; сначала укажите дату.");
        if(kind.equals("events")){NativeAgenda.date(day);return;}
        if(!Arrays.asList("normal","important","urgent").contains(item.optString("priority","normal"))||!Arrays.asList("auto","15","30","60","1440","none").contains(item.optString("reminder","auto")))throw new IllegalArgumentException("Настройки задачи.");
        if(item.optString("notes").length()>100000||!(item.opt("done") instanceof Boolean))throw new IllegalArgumentException("Некорректная задача.");
    }
    static JSONObject change(JSONObject current,String kind,JSONObject expected,JSONObject replacement)throws Exception {
        kind(kind);JSONObject identity=replacement!=null?replacement:expected;if(identity==null)throw new IllegalArgumentException();
        String id=identity.getString("id");if(expected!=null&&!id.equals(expected.getString("id")))throw new IllegalArgumentException("Identity changed");
        if(replacement!=null)validate(kind,replacement);
        JSONArray rows=rows(current,kind);
        JSONArray next=new JSONArray();JSONObject found=null;Set<String> ids=new HashSet<>();
        for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);String key=row.getString("id");if(!ids.add(key))throw new IllegalArgumentException("Duplicate ID");if(key.equals(id)){found=row;if(replacement!=null)next.put(replacement);}else next.put(row);}
        if(!SharedStateMerge.same(expected,found))throw new SharedStateMerge.Conflict();
        if(found==null&&replacement!=null){if(rows.length()>=5000)throw new IllegalArgumentException("Слишком много записей.");next.put(replacement);}
        JSONObject result=new JSONObject(current.toString()).put(kind,next);
        for(String key:new String[]{"tasks","notes","events"}){if(!result.has(key))result.put(key,new JSONArray());else if(!(result.opt(key) instanceof JSONArray))throw new IllegalArgumentException("Malformed collection");}
        if(kind.equals("events")&&current.has("eventMailLinks")) {
            JSONArray links=current.getJSONArray("eventMailLinks"),updated=new JSONArray();
            for(int i=0;i<links.length();i++) {
                JSONObject link=links.getJSONObject(i);
                if(link.optString("eventKey").equals("local:"+id)) {
                    if(replacement!=null)updated.put(new JSONObject(link.toString()).put("eventTitle",replacement.getString("title")).put("eventDate",replacement.getString("date")));
                }else updated.put(new JSONObject(link.toString()));
            }
            result.put("eventMailLinks",updated);
        }
        return result;
    }
}
