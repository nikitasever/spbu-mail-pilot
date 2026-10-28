package ru.local.spbumailpilot;
import org.json.*;
/** EML forwarding has no dependency on the displayed/possibly truncated reader text. */
final class NativeForwardOriginal {
    static NativeDraft create(String owner,JSONObject message,JSONObject reference,String signature)throws Exception{
        if(message==null)throw new IllegalArgumentException("Missing source");
        NativeDraft empty=NativeDraft.create(owner,null,signature);
        return new NativeDraft(owner,NativeDraft.EDITING,new JSONObject(empty.mail.toString()).put("kind","forward-eml")
            .put("subject",NativeForward.subject(message.optString("subject"))).put("forwardSource",NativeServerDraftCopyPlan.ref(reference)));
    }
    static NativeDraft complete(NativeDraft draft,JSONObject file)throws Exception{
        if(!NativeDraft.EDITING.equals(draft.status)||!"forward-eml".equals(draft.mail.optString("kind"))||ForwardOriginal.ready(draft.mail))throw new IllegalStateException("Original already attached");
        JSONObject entry=new JSONObject(file.toString()).put("forwardOriginal",true);
        JSONObject mail=new JSONObject(draft.mail.toString()).put("attachments",NativeAttachments.append(draft.mail.getJSONArray("attachments"),new JSONArray().put(entry)));
        ForwardOriginal.requireReady(mail);return new NativeDraft(draft.owner,draft.status,mail,draft.revision+1);
    }
}
