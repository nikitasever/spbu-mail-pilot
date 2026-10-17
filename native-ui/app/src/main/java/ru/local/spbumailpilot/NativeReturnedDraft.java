package ru.local.spbumailpilot;
import org.json.*;
final class NativeReturnedDraft {
    static NativeDraft convert(String owner,JSONObject source)throws Exception {
        JSONObject mail=new JSONObject(source.toString());
        if(!mail.has("references"))mail.put("references",new JSONArray());
        if(!mail.has("attachments"))mail.put("attachments",new JSONArray());
        // Queue and server metadata must not initiate another scheduled or server action.
        for(String key:new String[]{"scheduledMessageId","serverDraft","serverDraftAttempted","serverDraftUnconfirmed"})mail.remove(key);
        return new NativeDraft(owner,NativeDraft.EDITING,mail);
    }
}
