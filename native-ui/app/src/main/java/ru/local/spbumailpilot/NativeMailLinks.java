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
}
