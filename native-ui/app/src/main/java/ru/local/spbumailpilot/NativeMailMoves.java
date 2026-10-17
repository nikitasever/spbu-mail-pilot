package ru.local.spbumailpilot;
import org.json.*;
/** Moving changes UID scope, so existing local references must be removed first. */
final class NativeMailMoves {
    static void allowed(JSONObject state,String owner,JSONObject mail)throws Exception {
        MailNoticeTarget ref=NativeMailLinks.target(owner,mail);if(ref==null)throw new IllegalArgumentException("Mail reference");
        for(String key:new String[]{"tasks","responseReminders","replyReminders","snoozedMail","eventMailLinks"}) {
            if(!state.has(key))continue;JSONArray records=state.getJSONArray(key);
            if(records.length()>10000)throw new IllegalArgumentException("Too many references");
            for(int i=0;i<records.length();i++) {
                JSONObject row=records.getJSONObject(i);if(!row.has("mail")||row.isNull("mail"))continue;
                JSONObject source=row.getJSONObject("mail");MailNoticeTarget other=NativeMailLinks.target(owner,source);
                if(other==null)throw new IllegalArgumentException("Malformed mail reference");
                if(ref.folder.equals(other.folder)&&ref.uid.equals(other.uid)&&ref.validity.equals(other.validity))throw new IllegalStateException("У письма есть задача, напоминание или связь с событием. Сначала уберите связь в полном клиенте.");
            }
        }
    }
    static String confirmed(JSONObject result)throws Exception {
        if(!Boolean.TRUE.equals(result.opt("moved"))||!(result.opt("destination") instanceof String)||!NativeFolders.valid(result.getString("destination")))throw new IllegalArgumentException("Move not confirmed");
        return result.getString("destination");
    }
}
