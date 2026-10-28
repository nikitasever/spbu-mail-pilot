package ru.local.spbumailpilot;
import org.json.*;
import java.util.*;
final class NativeServerDraftCopyPlan {
    static JSONObject ref(JSONObject data)throws Exception{
        JSONObject result=new JSONObject();String folder=data.getString("folder");if(folder.isEmpty()||folder.length()>2048||folder.matches("(?s).*\\p{Cntrl}.*"))throw new IllegalArgumentException("Draft folder");result.put("folder",folder);
        for(String key:new String[]{"uid","validity"}){Object value=data.get(key);if(!(value instanceof String)||!((String)value).matches("[1-9][0-9]{0,18}")||Long.parseLong((String)value)<=0)throw new IllegalArgumentException("Draft reference");result.put(key,value);}
        return result;
    }
    static NativeDraft create(String owner,JSONObject data,String id,String text)throws Exception{
        JSONObject source=ref(data).put("htmlConverted",data.getBoolean("html"));
        JSONObject mail=new JSONObject(data.getJSONObject("draft").toString()).put("id",id).put("body",text).put("attachments",new JSONArray()).put("serverDraftSource",source);
        for(String key:new String[]{"serverDraft","serverDraftAttempted","serverDraftUnconfirmed","scheduledMessageId","legacyDraftSource"})mail.remove(key);
        files(data);return new NativeDraft(owner,NativeDraft.EDITING,mail);
    }
    static JSONArray files(JSONObject data)throws Exception{
        JSONArray files=data.getJSONArray("attachments");if(files.length()>10)throw new IllegalArgumentException("Attachment count");Set<String> paths=new HashSet<>();
        for(int i=0;i<files.length();i++){JSONObject file=files.getJSONObject(i);String path=file.getString("path"),name=file.getString("name");if(path.length()>160||!path.matches("root|(?:[0-9]{1,5}|m)(?:\\.(?:[0-9]{1,5}|m)){0,12}")||!paths.add(path)||!name.equals(AttachmentFiles.safeName(name)))throw new IllegalArgumentException("MIME attachment");}
        return new JSONArray(files.toString());
    }
    static NativeDraft existing(List<NativeDraft> drafts,JSONObject data)throws Exception{
        JSONObject reference=ref(data);NativeDraft found=null;
        for(NativeDraft draft:drafts){JSONObject copied=draft.mail.optJSONObject("serverDraftSource"),saved=draft.mail.optJSONObject("serverDraft");
            boolean match=copied!=null&&same(copied,reference);
            if(saved!=null&&draft.mail.optString("id").equals(data.optString("key"))&&same(saved,reference)&&saved.optString("revision").equals(data.optString("revision")))match=true;
            if(match){if(found!=null)throw new IllegalStateException("Ambiguous source");found=draft;}
        }
        return found;
    }
    private static boolean same(JSONObject a,JSONObject b){for(String key:new String[]{"folder","uid","validity"})if(!a.optString(key).equals(b.optString(key)))return false;return true;}
}
