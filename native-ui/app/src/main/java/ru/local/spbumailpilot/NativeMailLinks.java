package ru.local.spbumailpilot;
import org.json.*;

/** References are account-bound; a missing folder alone retains the legacy INBOX default. */
final class NativeMailLinks {
    static MailNoticeTarget target(String owner,JSONObject mail) {
        try {
            if(mail==null||!(mail.opt("uid") instanceof String)||!(mail.opt("validity") instanceof String)||mail.has("folder")&&!(mail.opt("folder") instanceof String)||mail.has("account")&&(!(mail.opt("account") instanceof String)||!owner.equals(mail.getString("account"))))return null;
            JSONObject ref=new JSONObject().put("account",owner).put("folder",mail.has("folder")?mail.getString("folder"):"INBOX")
                .put("uid",mail.getString("uid")).put("validity",mail.getString("validity"));
            return MailNoticeTarget.parse(ref.toString());
        }catch(Exception invalid){return null;}
    }
    static JSONObject task(String owner,JSONObject mail,String id)throws Exception {
        MailNoticeTarget ref=target(owner,mail);
        if(ref==null||id==null||id.isEmpty())throw new IllegalArgumentException("Mail reference");
        JSONObject source=new JSONObject().put("account",owner).put("folder",mail.optString("folder","INBOX"))
            .put("uid",mail.getString("uid")).put("validity",mail.getString("validity"));
        for(String key:new String[]{"subject","sender","date"})if(mail.opt(key) instanceof String)source.put(key,mail.getString(key));
        return new JSONObject().put("id",id).put("title","").put("date","").put("time","").put("notes","")
            .put("priority","normal").put("done",false).put("reminder","auto").put("mail",source);
    }
    static JSONObject savedMail(JSONArray links,JSONObject selected)throws Exception {
        for(int i=0;i<links.length();i++)if(SharedStateMerge.same(links.optJSONObject(i),selected)) {
            JSONObject mail=selected.optJSONObject("mail");return mail==null?null:new JSONObject(mail.toString());
        }
        return null;
    }
    static String eventKey(JSONObject event)throws Exception {
        String kind=event.optString("kind"),id=event.getString("id");if(id.isEmpty())throw new IllegalArgumentException("Event id");
        if(kind.equals("remote"))return new JSONArray().put(event.getString("calendarId")).put(id).toString();
        if(kind.equals("event"))return "local:"+id;
        return "";
    }
    static JSONArray forEvent(JSONObject event,JSONArray links)throws Exception {
        if(links.length()>5000)throw new IllegalArgumentException("Too many links");
        String key=eventKey(event);JSONArray found=new JSONArray();if(key.isEmpty())return found;
        for(int i=0;i<links.length();i++){
            JSONObject link=links.optJSONObject(i);if(link!=null&&key.equals(link.optString("eventKey")))found.put(new JSONObject(link.toString()));
        }
        return found;
    }
    static JSONObject taskFromEvent(JSONObject event,String id)throws Exception {
        if(event==null||id==null||id.isEmpty())throw new IllegalArgumentException("Event reference");
        String eventId=event.optString("id");
        if(eventId.isEmpty())throw new IllegalArgumentException("Event id");
        String kind=event.optString("kind");
        JSONObject source=new JSONObject();
        if(kind.equals("remote")) {
            source.put("calendarId",event.optString("calendarId")).put("id",eventId).put("calendarTitle",event.optString("calendarTitle","Календарь"));
        } else {
            source.put("calendarId","local").put("id",eventId).put("calendarTitle","Локальный");
        }
        String title=event.optString("title","Без названия");
        if(title.isEmpty())title="Без названия";
        String date=event.optString("date","");
        String time=event.optBoolean("allDay")?"":event.optString("time","");
        String location=event.optString("location","");
        String notes=location.isEmpty()?"":"Место: "+location;
        return new JSONObject().put("id",id).put("title",title).put("date",date).put("time",time).put("notes",notes)
            .put("priority","normal").put("done",false).put("reminder","auto").put("calendarSource",source);
    }
    static JSONObject link(JSONObject state,String owner,JSONObject mail,JSONObject event,String linkId)throws Exception {
        MailNoticeTarget ref=target(owner,mail);
        if(ref==null)throw new IllegalArgumentException("Некорректная ссылка на письмо.");
        if(event==null)throw new IllegalArgumentException("Некорректное событие.");
        String key;
        try {
            key=eventKey(event);
        } catch(Exception e) {
            throw new IllegalArgumentException("Некорректное событие.");
        }
        if(key.isEmpty())throw new IllegalArgumentException("Некорректное событие.");
        if(linkId==null||linkId.isEmpty())throw new IllegalArgumentException("Link id");
        JSONObject next=state==null?new JSONObject():new JSONObject(state.toString());
        JSONArray links=next.optJSONArray("eventMailLinks");
        if(links==null){links=new JSONArray();next.put("eventMailLinks",links);}
        if(links.length()>=500)throw new IllegalArgumentException("Нельзя сохранить больше 500 связей. Удалите ненужные связи.");
        for(int i=0;i<links.length();i++){
            JSONObject existing=links.optJSONObject(i);
            if(existing==null||!key.equals(existing.optString("eventKey")))continue;
            JSONObject existingMail=existing.optJSONObject("mail");
            if(existingMail!=null&&ref.folder.equals(existingMail.optString("folder","INBOX"))
                &&ref.uid.equals(existingMail.optString("uid"))&&ref.validity.equals(existingMail.optString("validity"))) {
                throw new IllegalArgumentException("Письмо уже связано с этим событием.");
            }
        }
        JSONObject safeMail=new JSONObject().put("account",owner).put("folder",ref.folder).put("uid",ref.uid).put("validity",ref.validity);
        for(String field:new String[]{"subject","sender","email","date"})if(mail.opt(field) instanceof String)safeMail.put(field,mail.getString(field));
        JSONObject entry=new JSONObject().put("id",linkId).put("eventKey",key)
            .put("eventDate",event.optString("date","")).put("eventTitle",event.optString("title",""))
            .put("mail",safeMail);
        links.put(entry);
        return next;
    }
    static JSONObject unlink(JSONObject state,JSONObject expectedLink)throws Exception {
        if(expectedLink==null)throw new IllegalArgumentException("Expected link");
        return unlink(state,expectedLink.optString("id"),expectedLink);
    }
    static JSONObject unlink(JSONObject state,String linkId,JSONObject expectedLink)throws Exception {
        if(state==null||linkId==null||linkId.isEmpty()||expectedLink==null)throw new IllegalArgumentException("Link id");
        if(!linkId.equals(expectedLink.optString("id")))throw new SharedStateMerge.Conflict();
        JSONObject next=new JSONObject(state.toString());
        JSONArray links=next.optJSONArray("eventMailLinks");
        if(links==null)throw new SharedStateMerge.Conflict();
        JSONArray updated=new JSONArray();
        boolean removed=false;
        for(int i=0;i<links.length();i++){
            JSONObject link=links.optJSONObject(i);
            if(!removed&&link!=null&&linkId.equals(link.optString("id"))){
                if(!SharedStateMerge.same(link,expectedLink))throw new SharedStateMerge.Conflict();
                removed=true;
                continue;
            }
            if(link!=null)updated.put(link);
        }
        if(!removed)throw new SharedStateMerge.Conflict();
        next.put("eventMailLinks",updated);
        return next;
    }
}
