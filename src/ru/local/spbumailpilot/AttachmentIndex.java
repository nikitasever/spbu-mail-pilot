package ru.local.spbumailpilot;
import javax.mail.*;
import org.json.*;
/** Uses the same bounded metadata-only inventory as the reader; nested EML is a file. */
final class AttachmentIndex {
    static JSONArray list(Part part,String query)throws Exception{
        if(query==null||query.length()>200||query.matches("(?s).*\\p{Cntrl}.*"))throw new IllegalArgumentException("Invalid filename query");
        String needle=query.trim().toLowerCase(java.util.Locale.ROOT);JSONArray all=MailAttachments.list(part),result=new JSONArray();
        for(int i=0;i<all.length();i++){JSONObject file=all.getJSONObject(i);if(file.getString("name").toLowerCase(java.util.Locale.ROOT).contains(needle))result.put(file);}
        return result;
    }
}
