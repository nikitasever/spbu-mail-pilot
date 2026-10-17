package ru.local.spbumailpilot;
import org.json.*;
/** Pure durable metadata transitions. Server references are accepted only for this draft key. */
final class NativeServerDraftPlan {
    static NativeDraft attempt(NativeDraft draft)throws Exception {
        editable(draft);
        JSONObject mail=new JSONObject(draft.mail.toString()).put("serverDraftAttempted",true).put("serverDraftUnconfirmed",true);
        return new NativeDraft(draft.owner,draft.status,mail,draft.revision+1);
    }
    static NativeDraft confirmed(NativeDraft draft,JSONObject reference,String revision)throws Exception {
        editable(draft);validate(reference,draft.mail.getString("id"));
        if(!revision.equals(reference.getString("revision")))throw new IllegalArgumentException("Wrong content revision");
        JSONObject mail=new JSONObject(draft.mail.toString()).put("serverDraft",new JSONObject(reference.toString()));
        mail.remove("serverDraftUnconfirmed");
        return new NativeDraft(draft.owner,draft.status,mail,draft.revision+1);
    }
    static NativeDraft removed(NativeDraft draft,JSONObject expected)throws Exception {
        editable(draft);validate(expected,draft.mail.getString("id"));
        JSONObject current=draft.mail.optJSONObject("serverDraft");
        if(current==null||!same(current,expected))throw new IllegalStateException("Reference changed");
        JSONObject mail=new JSONObject(draft.mail.toString());mail.remove("serverDraft");
        // A previous unconfirmed upload can have created another revision; do not claim it was deleted.
        if(!mail.optBoolean("serverDraftUnconfirmed"))mail.remove("serverDraftAttempted");
        return new NativeDraft(draft.owner,draft.status,mail,draft.revision+1);
    }
    static void validate(JSONObject ref,String key)throws Exception {
        if(key==null||!key.matches("[a-f0-9-]{36}")||ref==null||!key.equals(ref.getString("key")))throw new IllegalArgumentException("Draft ownership");
        String folder=ref.getString("folder");if(folder.isEmpty()||folder.length()>2048||folder.matches("(?s).*\\p{Cntrl}.*"))throw new IllegalArgumentException("Folder");
        for(String name:new String[]{"uid","validity"}){Object value=ref.get(name);if(!(value instanceof String)||!((String)value).matches("[0-9]{1,19}")||Long.parseLong((String)value)<=0)throw new IllegalArgumentException("UID reference");}
        if(!ref.getString("revision").matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Revision");
    }
    static String state(NativeDraft draft){return draft.mail.optBoolean("serverDraftUnconfirmed")?"uncertain":draft.mail.optJSONObject("serverDraft")!=null?"saved":"none";}
    private static void editable(NativeDraft draft){if(!NativeDraft.EDITING.equals(draft.status))throw new IllegalStateException("Draft not editable");}
    private static boolean same(JSONObject a,JSONObject b)throws Exception {for(String key:new String[]{"folder","uid","validity","key","revision"})if(!a.getString(key).equals(b.getString(key)))return false;return true;}
}
