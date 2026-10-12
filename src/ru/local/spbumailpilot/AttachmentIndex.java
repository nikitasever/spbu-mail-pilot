package ru.local.spbumailpilot;
import javax.mail.*;
import org.json.*;
final class AttachmentIndex {
    static JSONArray list(Part part,String query)throws Exception{JSONArray result=new JSONArray();walk(part,"root",0,query.toLowerCase(java.util.Locale.ROOT),result);return result;}
    private static void walk(Part part,String path,int depth,String query,JSONArray result)throws Exception{
        if(depth>12||result.length()>=500)return;
        String raw=part.getFileName();if(raw!=null||Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())){
            String name=AttachmentFiles.safeName(raw==null?"Вложение":AttachmentNames.decode(raw));
            if(name.toLowerCase(java.util.Locale.ROOT).contains(query))result.put(new JSONObject().put("path",path).put("name",name).put("size",part.getSize()));return;
        }
        if(part.isMimeType("multipart/*")){Multipart multi=(Multipart)part.getContent();for(int i=0;i<multi.getCount();i++)walk(multi.getBodyPart(i),AttachmentParts.child(path,""+i),depth+1,query,result);}
        else if(part.isMimeType("message/rfc822"))walk((Part)part.getContent(),AttachmentParts.child(path,"m"),depth+1,query,result);
    }
}

