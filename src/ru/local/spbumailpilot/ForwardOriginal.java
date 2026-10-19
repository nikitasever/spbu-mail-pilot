package ru.local.spbumailpilot;
import org.json.*;
/** Readiness follows the attachment entry, so file-token remapping in the queue is safe. */
final class ForwardOriginal {
    static boolean ready(JSONObject mail)throws Exception{
        JSONArray files=mail.optJSONArray("attachments");if(files==null)return false;
        int originals=0;
        for(int i=0;i<files.length();i++){JSONObject file=files.getJSONObject(i);if(file.optBoolean("forwardOriginal")){
            if(!file.getString("name").toLowerCase(java.util.Locale.ROOT).endsWith(".eml")||file.getLong("size")<=0)throw new IllegalArgumentException("Invalid EML attachment");originals++;
        }}
        if(originals>1)throw new IllegalArgumentException("Duplicate original");return originals==1;
    }
    static void requireReady(JSONObject mail)throws Exception{
        if("forward-eml".equals(mail.optString("kind"))&&!ready(mail))throw new IllegalArgumentException("Original EML is not attached");
    }
}
