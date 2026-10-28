package ru.local.spbumailpilot;

import java.util.*;
import org.json.*;

/** Local contacts only. The caller commits fresh shared account JSON under its existing lock. */
final class NativeContactEdits {
    static JSONArray rows(JSONObject state)throws Exception {
        if(!state.has("contacts"))return new JSONArray();
        JSONArray rows=state.getJSONArray("contacts");
        if(rows.length()>5000)throw new IllegalArgumentException("Слишком много контактов.");
        Set<String> seen=new HashSet<>();
        for(int i=0;i<rows.length();i++) {
            String email=rows.getJSONObject(i).getString("email");
            if(email.isEmpty()||!seen.add(email.toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("Повторяющиеся адреса в сохранённых контактах.");
        }
        return rows;
    }
    static JSONObject raw(JSONObject state,String email)throws Exception {
        JSONArray rows=rows(state);
        for(int i=0;i<rows.length();i++)if(rows.getJSONObject(i).getString("email").equalsIgnoreCase(email))return new JSONObject(rows.getJSONObject(i).toString());
        return null;
    }
    static JSONObject fields(JSONObject value)throws Exception {
        String email=value.getString("email").trim(),sender=value.getString("sender").trim(),number=value.getString("number").trim();
        if(email.length()>254||!email.matches("[^\\s<>@\\p{Cntrl}]+@[^\\s<>@\\p{Cntrl}]+"))throw new IllegalArgumentException("Укажите полный адрес электронной почты.");
        if(sender.length()>180||sender.matches("(?s).*\\p{Cntrl}.*"))throw new IllegalArgumentException("Имя: до 180 символов, без управляющих знаков.");
        if(number.length()>60||number.matches("(?s).*\\p{Cntrl}.*"))throw new IllegalArgumentException("Телефон: до 60 символов, без управляющих знаков.");
        return new JSONObject().put("email",email).put("sender",sender.isEmpty()?email:sender).put("number",number);
    }
    static JSONObject change(JSONObject current,JSONObject expected,JSONObject replacement)throws Exception {
        if(expected==null&&replacement==null)throw new IllegalArgumentException("Contact required");
        JSONObject clean=replacement==null?null:fields(replacement);
        String original=expected!=null?expected.getString("email"):clean.getString("email");
        JSONObject found=raw(current,original);
        if(!SharedStateMerge.same(found,expected))throw new SharedStateMerge.Conflict();
        if(clean!=null) {
            JSONObject duplicate=raw(current,clean.getString("email"));
            if(duplicate!=null&&(expected==null||!duplicate.getString("email").equalsIgnoreCase(original)))throw new IllegalArgumentException("Контакт с таким адресом уже сохранён. Откройте его для редактирования.");
        }
        JSONObject stored=null;
        if(clean!=null) {
            stored=found==null?new JSONObject():new JSONObject(found.toString());
            for(String key:new String[]{"email","sender","number"})stored.put(key,clean.getString(key));
        }
        JSONArray next=new JSONArray(),rows=rows(current);
        for(int i=0;i<rows.length();i++) {
            JSONObject row=rows.getJSONObject(i);
            if(row.getString("email").equalsIgnoreCase(original)){if(stored!=null)next.put(stored);}else next.put(row);
        }
        if(found==null&&stored!=null){if(rows.length()>=5000)throw new IllegalArgumentException("Слишком много контактов.");next.put(stored);}
        return NativeSharedState.ready(new JSONObject(current.toString()).put("contacts",next));
    }
    static String number(JSONObject state,String email)throws Exception {
        JSONObject stored=raw(state,email);if(stored!=null&&!stored.optString("number").isEmpty())return stored.optString("number");
        JSONArray phone=state.optJSONArray("phoneContacts");
        if(phone!=null)for(int i=0;i<phone.length();i++){JSONObject row=phone.optJSONObject(i);if(row!=null&&email.equalsIgnoreCase(row.optString("email")))return row.optString("number");}
        return "";
    }
    static JSONObject clearRecipientHistory(JSONObject current,JSONArray expected)throws Exception{
        JSONObject base=current==null?new JSONObject():current;
        JSONArray actual=base.has("recipientBook")?base.getJSONArray("recipientBook"):new JSONArray();
        if(expected==null||!SharedStateMerge.same(expected,actual))throw new SharedStateMerge.Conflict();
        return NativeSharedState.ready(new JSONObject(base.toString()).put("recipientBook",new JSONArray()));
    }
}
