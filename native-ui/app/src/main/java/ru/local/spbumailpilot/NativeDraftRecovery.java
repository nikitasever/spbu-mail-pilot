package ru.local.spbumailpilot;
/** Adopting a durable delivery marker must never retarget an editor or replace its visible content. */
final class NativeDraftRecovery {
    static boolean sameContent(NativeDraft expected,NativeDraft actual){
        if(expected==null||actual==null||!expected.owner.equals(actual.owner))return false;
        for(String key:new String[]{"id","to","cc","bcc","subject","body","inReplyTo","kind"})if(!expected.mail.optString(key).equals(actual.mail.optString(key)))return false;
        for(String key:new String[]{"references","attachments"})if(!expected.mail.optJSONArray(key).toString().equals(actual.mail.optJSONArray(key).toString()))return false;
        return true;
    }
}
