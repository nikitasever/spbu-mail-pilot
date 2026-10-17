package ru.local.spbumailpilot;
import org.json.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Local labels/personal reply reminders; never changes Seen, hides mail or sends SMTP. */
final class NativeMailExtras {
    static String labelName(String value){switch(value){case "urgent":return "Срочно";case "important":return "Важно";case "waiting":return "Ответить";default:return "";}}
    static String key(String owner,JSONObject mail)throws Exception{if(NativeMailLinks.target(owner,mail)==null)throw new IllegalArgumentException("Mail reference");return NativeMailEvents.key(mail);}
    static JSONObject labels(JSONObject state)throws Exception{return state.has("mailLabels")?state.getJSONObject("mailLabels"):new JSONObject();}
    static String bucket(String mode){switch(mode){case "response":return "responseReminders";case "snooze":return "snoozedMail";case "followup":return "replyReminders";default:throw new IllegalArgumentException("Reminder mode");}}
    static JSONArray records(JSONObject state,String owner,String mode)throws Exception {
        String bucket=bucket(mode);JSONArray rows=state.has(bucket)?state.getJSONArray(bucket):new JSONArray();
        if(rows.length()>500)throw new IllegalArgumentException("Сохранено больше 500 напоминаний.");Set<String> keys=new HashSet<>();
        for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);String key=key(owner,row.getJSONObject("mail"));if(!keys.add(key)||!(row.opt("at") instanceof Number)||row.getLong("at")<=0||row.getLong("at")>253402300799000L||mode.equals("followup")&&!ReplyReminder.validId(row.optString("messageId")))throw new IllegalArgumentException("Invalid reminder");}
        return rows;
    }
    static JSONArray responses(JSONObject state,String owner)throws Exception{return records(state,owner,"response");}
    static JSONObject reminder(JSONObject state,String owner,JSONObject mail,String mode)throws Exception {
        String key=key(owner,mail);JSONArray rows=records(state,owner,mode);
        for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);if(key.equals(key(owner,row.getJSONObject("mail"))))return new JSONObject(row.toString());}return null;
    }
    static JSONObject response(JSONObject state,String owner,JSONObject mail)throws Exception{return reminder(state,owner,mail,"response");}
    static boolean hidden(String owner,JSONObject mail,JSONArray snoozed,long now)throws Exception {
        return activeKeys(owner,snoozed,now).contains(key(owner,mail));
    }
    static Set<String> activeKeys(String owner,JSONArray snoozed,long now)throws Exception {
        Set<String> keys=new HashSet<>();for(int i=0;i<snoozed.length();i++){JSONObject row=snoozed.getJSONObject(i);if(row.getLong("at")>now)keys.add(key(owner,row.getJSONObject("mail")));}return keys;
    }
    static long at(String day,String time,long now)throws Exception {
        LocalDate date=NativeAgenda.date(day);LocalTime clock=LocalTime.parse(time);
        if(!date.toString().equals(day)||!clock.format(DateTimeFormatter.ofPattern("HH:mm")).equals(time))throw new IllegalArgumentException("Дата и время: ГГГГ-ММ-ДД, ЧЧ:ММ.");
        long at=date.atTime(clock).atZone(ZoneId.of("Europe/Moscow")).toInstant().toEpochMilli();if(at<=now)throw new IllegalArgumentException("Выберите дату и время в будущем.");return at;
    }
    static JSONObject changeLabel(JSONObject current,String owner,JSONObject mail,Object expected,String value)throws Exception {
        if(!value.isEmpty()&&labelName(value).isEmpty())throw new IllegalArgumentException("Label");String key=key(owner,mail);
        JSONObject old=labels(current);if(!SharedStateMerge.same(old.opt(key),expected))throw new SharedStateMerge.Conflict();
        JSONObject next=new JSONObject(old.toString()).put(key,value);return seeded(new JSONObject(current.toString()).put("mailLabels",next));
    }
    static JSONObject changeResponse(JSONObject current,String owner,JSONObject mail,JSONObject expected,Long at,long now)throws Exception {
        return changeReminder(current,owner,mail,expected,at,now,"response");
    }
    static JSONObject changeReminder(JSONObject current,String owner,JSONObject mail,JSONObject expected,Long at,long now,String mode)throws Exception {
        String bucket=bucket(mode),key=key(owner,mail);if(!SharedStateMerge.same(reminder(current,owner,mail,mode),expected))throw new SharedStateMerge.Conflict();
        JSONArray next=new JSONArray(),rows=records(current,owner,mode);
        for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);if(!key.equals(key(owner,row.getJSONObject("mail"))))next.put(new JSONObject(row.toString()));}
        if(at!=null){if(at<=now||at>253402300799000L)throw new IllegalArgumentException("Выберите будущее время.");if(next.length()>=500)throw new IllegalArgumentException("Уже сохранено 500 напоминаний.");
            JSONObject source=NativeMailLinks.task(owner,mail,"source").getJSONObject("mail");
            JSONObject record=expected==null?new JSONObject():new JSONObject(expected.toString());
            if(mode.equals("followup")){String id=mail.optString("messageId",expected==null?"":expected.optString("messageId"));if(!ReplyReminder.validId(id))throw new IllegalArgumentException("У письма нет корректного Message-ID.");record.put("messageId",id);}
            next.put(record.put("mail",source).put("sender",mail.optString("sender",expected==null?"":expected.optString("sender"))).put("subject",mail.optString("subject",expected==null?"":expected.optString("subject"))).put("at",at));}
        return seeded(new JSONObject(current.toString()).put(bucket,next));
    }
    private static JSONObject seeded(JSONObject result)throws Exception {for(String key:new String[]{"tasks","notes","events"}){if(!result.has(key))result.put(key,new JSONArray());else if(!(result.opt(key) instanceof JSONArray))throw new IllegalArgumentException("Malformed collection");}return result;}
}
