package ru.local.spbumailpilot;
import org.json.*;
import java.time.*;
import java.util.*;
import java.util.regex.*;

/** Local events and links commit together; date suggestions never create anything on their own. */
final class NativeMailEvents {
    private static final ZoneId MOSCOW=ZoneId.of("Europe/Moscow");
    private static final Pattern ABSOLUTE=Pattern.compile("\\b(\\d{2})\\.(\\d{2})\\.(\\d{4})[ ,г.\\s]+(\\d{2}):(\\d{2})\\b");
    private static final Pattern RELATIVE=Pattern.compile("(?:^|[\\s,.!?])(послезавтра|завтра|сегодня)(?=$|[\\s,.!?])",Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
    private static final Pattern TIME=Pattern.compile("\\b([01]?\\d|2[0-3])[:-]([0-5]\\d)\\b");
    static final class Hint {final String date,time;Hint(String date,String time){this.date=date;this.time=time;}}
    static Hint hint(JSONObject mail,String preview,Instant now) {
        try {
            String subject=mail.optString("subject"),day,time;
            Matcher absolute=ABSOLUTE.matcher(subject),words=RELATIVE.matcher(subject);
            boolean hasWord=words.find(),found=absolute.find();
            if(!found&&!hasWord){absolute=ABSOLUTE.matcher(preview==null?"":preview);found=absolute.find();}
            if(found){day=absolute.group(3)+"-"+absolute.group(2)+"-"+absolute.group(1);time=absolute.group(4)+":"+absolute.group(5);}
            else {
                if(!hasWord)return null;String word=words.group(1).toLowerCase(Locale.ROOT);if(words.find())return null;
                Matcher times=TIME.matcher(subject);if(!times.find())return null;
                time=String.format(Locale.ROOT,"%02d:%s",Integer.parseInt(times.group(1)),times.group(2));if(times.find())return null;
                LocalDate sent=OffsetDateTime.parse(mail.getString("date")).atZoneSameInstant(MOSCOW).toLocalDate();
                day=sent.plusDays(word.equals("сегодня")?0:word.equals("завтра")?1:2).toString();
            }
            LocalDate date=NativeAgenda.date(day);LocalTime at=LocalTime.parse(time);
            if(!date.toString().equals(day)||!at.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")).equals(time)||!date.atTime(at).atZone(MOSCOW).toInstant().isAfter(now))return null;
            return new Hint(day,time);
        }catch(Exception invalid){return null;}
    }
    static String key(JSONObject mail)throws Exception{return new JSONArray().put(mail.optString("folder","INBOX")).put(mail.getString("validity")).put(mail.getString("uid")).toString();}
    static JSONObject existing(JSONObject state,JSONObject mail)throws Exception {
        String key=key(mail);JSONArray events=NativeWorkspace.rows(state,"events");JSONObject found=null;
        for(int i=0;i<events.length();i++){JSONObject event=events.getJSONObject(i);if(key.equals(event.optString("createdFromMail"))){if(found!=null)throw new SharedStateMerge.Conflict();found=event;}}
        return found==null?null:new JSONObject(found.toString());
    }
    static JSONObject draft(String owner,JSONObject mail,String id,Instant now)throws Exception {
        NativeMailLinks.task(owner,mail,id);Hint hint=hint(mail,"",now);
        return new JSONObject().put("id",id).put("title",mail.optString("subject").isEmpty()?"Без темы":mail.optString("subject"))
            .put("date",hint==null?now.atZone(MOSCOW).toLocalDate().toString():hint.date).put("time",hint==null?"":hint.time);
    }
    static JSONObject save(JSONObject current,String owner,JSONObject mail,JSONObject expected,JSONObject editor,String linkId)throws Exception {
        JSONObject source=NativeMailLinks.task(owner,mail,"source").getJSONObject("mail");String key=key(source);
        JSONObject present=existing(current,source);
        if(!SharedStateMerge.same(present,expected))throw new SharedStateMerge.Conflict();
        JSONObject event=new JSONObject(editor.toString()).put("createdFromMail",key);
        JSONObject next=NativeWorkspace.change(current,"events",expected,event);
        JSONArray links=next.has("eventMailLinks")?next.getJSONArray("eventMailLinks"):new JSONArray();
        boolean linked=false;String eventKey="local:"+event.getString("id");
        for(int i=0;i<links.length();i++){JSONObject link=links.getJSONObject(i);JSONObject ref=link.optJSONObject("mail");if(eventKey.equals(link.optString("eventKey"))&&ref!=null&&key.equals(key(ref)))linked=true;}
        if(!linked){if(links.length()>=500)throw new IllegalArgumentException("Link limit");links.put(new JSONObject().put("id",linkId).put("eventKey",eventKey).put("eventDate",event.getString("date")).put("eventTitle",event.getString("title")).put("mail",source));}
        return next.put("eventMailLinks",links);
    }
}
