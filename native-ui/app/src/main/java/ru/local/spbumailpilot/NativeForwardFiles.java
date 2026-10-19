package ru.local.spbumailpilot;
import org.json.*;
/** Durable, exact original-file selection; never copies recipients or reply headers. */
final class NativeForwardFiles {
    static JSONObject source(JSONObject data)throws Exception{
        JSONObject reference=NativeServerDraftCopyPlan.ref(data);
        JSONArray files=NativeServerDraftCopyPlan.files(data),clean=new JSONArray();
        for(int i=0;i<files.length();i++){JSONObject f=files.getJSONObject(i);clean.put(new JSONObject().put("path",f.getString("path")).put("name",f.getString("name")));}
        return reference.put("attachments",clean);
    }
    static NativeDraft prepare(NativeDraft draft,JSONObject data)throws Exception{
        if(!"forward-text".equals(draft.mail.optString("kind")))throw new IllegalArgumentException("Not a forward");
        return new NativeDraft(draft.owner,draft.status,new JSONObject(draft.mail.toString()).put("forwardSource",source(data)),draft.revision);
    }
    static int pending(NativeDraft draft)throws Exception{
        if(!"forward-text".equals(draft.mail.optString("kind")))return 0;
        JSONObject source=draft.mail.optJSONObject("forwardSource");
        return source==null||draft.mail.optBoolean("forwardFilesAdded")?0:source(source).getJSONArray("attachments").length();
    }
    static void verify(NativeDraft draft,JSONObject current)throws Exception{
        if(pending(draft)==0||!SharedStateMerge.same(source(draft.mail.getJSONObject("forwardSource")),source(current)))throw new IllegalStateException("Original files changed");
        if(draft.mail.getJSONArray("attachments").length()+pending(draft)>10)throw new IllegalArgumentException("Attachment count");
    }
    static NativeDraft complete(NativeDraft draft,JSONArray added)throws Exception{
        if(!NativeDraft.EDITING.equals(draft.status)||pending(draft)==0||added.length()!=pending(draft))throw new IllegalStateException("Incomplete copy");
        NativeAttachments.validate(added);
        JSONArray original=draft.mail.getJSONObject("forwardSource").getJSONArray("attachments");
        for(int i=0;i<added.length();i++)if(!original.getJSONObject(i).getString("name").equals(added.getJSONObject(i).getString("name")))throw new IllegalArgumentException("Attachment name");
        return new NativeDraft(draft.owner,draft.status,new JSONObject(draft.mail.toString()).put("attachments",NativeAttachments.append(draft.mail.getJSONArray("attachments"),added)).put("forwardFilesAdded",true),draft.revision+1);
    }
}
