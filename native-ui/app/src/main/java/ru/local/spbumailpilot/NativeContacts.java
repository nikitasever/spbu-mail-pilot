package ru.local.spbumailpilot;

import java.util.*;
import org.json.*;

/** Presentation policy; no address book is modified and no directory ACL is invented. */
final class NativeContacts {
    static final String ALPHABET="АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯABCDEFGHIJKLMNOPQRSTUVWXYZ#";
    static String query(String value,String letter) {
        if(value==null||value.length()>120||value.matches("(?s).*\\p{Cntrl}.*")||value.startsWith("^:"))throw new IllegalArgumentException("Invalid query");
        if(!letter.isEmpty()) {
            if(letter.length()!=1||!ALPHABET.contains(letter))throw new IllegalArgumentException("Invalid letter");
            return "^:"+letter;
        }
        return value.trim();
    }
    static JSONArray merge(JSONArray previous,JSONArray incoming) throws Exception {
        if(incoming.length()>5000)throw new IllegalArgumentException("Directory page too large");
        LinkedHashMap<String,JSONObject> people=new LinkedHashMap<>();
        for(JSONArray array:new JSONArray[]{previous,incoming})for(int i=0;i<array.length();i++) {
            JSONObject person=array.optJSONObject(i);if(person==null)continue;
            String email=person.optString("email"),name=person.optString("sender",person.optString("name"));
            if(!NativeRecipients.email(email))continue;
            if(name.matches("(?s).*\\p{Cntrl}.*"))name=email;
            if(name.length()>180)name=name.substring(0,Character.isHighSurrogate(name.charAt(179))?179:180);
            people.putIfAbsent(email.toLowerCase(Locale.ROOT),new JSONObject().put("email",email).put("sender",name.isEmpty()?email:name));
        }
        if(people.size()>5000)throw new IllegalArgumentException("Directory session limit");
        return new JSONArray(people.values());
    }
    static JSONArray local(JSONObject data,String query) throws Exception {
        JSONArray clean=new JSONArray();
        for(String key:new String[]{"contacts","phoneContacts","recipientBook"}) {
            JSONArray group=data.optJSONArray(key);if(group==null)continue;
            clean=merge(clean,group);
        }
        JSONArray found=new JSONArray();
        String needle=query.toUpperCase(Locale.ROOT);
        for(int i=0;i<clean.length();i++) {
            JSONObject p=clean.getJSONObject(i);String name=p.getString("sender").toUpperCase(Locale.ROOT),email=p.getString("email").toUpperCase(Locale.ROOT);
            boolean match=needle.startsWith("^:")?(needle.equals("^:#")?!name.matches("^[А-ЯЁA-Z].*"):name.startsWith(needle.substring(2))):name.contains(needle)||email.contains(needle);
            if(match)found.put(p);
        }
        return found;
    }
    static JSONArray history(JSONObject data,String query) throws Exception {
        JSONArray clean=new JSONArray();
        JSONArray group=data.optJSONArray("recipientBook");
        if(group!=null)clean=merge(clean,group);
        JSONArray found=new JSONArray();
        String needle=query.toUpperCase(Locale.ROOT);
        for(int i=0;i<clean.length();i++) {
            JSONObject p=clean.getJSONObject(i);String name=p.getString("sender").toUpperCase(Locale.ROOT),email=p.getString("email").toUpperCase(Locale.ROOT);
            boolean match=needle.startsWith("^:")?(needle.equals("^:#")?!name.matches("^[А-ЯЁA-Z].*"):name.startsWith(needle.substring(2))):name.contains(needle)||email.contains(needle);
            if(match)found.put(p);
        }
        return found;
    }
    static String next(JSONObject page,String previous,Set<String> seen) throws Exception {
        Object more=page.get("hasMore");String cookie=page.getString("cookie");
        if(!(more instanceof Boolean)||cookie.length()>4096||cookie.matches("(?s).*\\p{Cntrl}.*")||((Boolean)more)!=!cookie.isEmpty())throw new IllegalArgumentException("Invalid directory cursor");
        if(!cookie.isEmpty()&&(cookie.equals(previous)||seen.contains(cookie)))throw new IllegalArgumentException("Directory cursor repeated");
        return cookie;
    }
}
