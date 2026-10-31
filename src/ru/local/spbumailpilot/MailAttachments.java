package ru.local.spbumailpilot;
import javax.mail.*;
import org.json.*;
/** Metadata traversal independent of the text alternative, never opens a binary payload. */
final class MailAttachments {
 static JSONArray list(Part part)throws Exception{JSONArray files=new JSONArray();walk(part,"root",0,new int[]{500},files);return files;}
 static boolean file(Part part)throws Exception{if(Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition()))return true;if(Part.INLINE.equalsIgnoreCase(part.getDisposition())&&part.isMimeType("image/*"))return false;return part.getFileName()!=null||part.isMimeType("message/rfc822")||part.isMimeType("message/global")||part.isMimeType("application/pdf");}
 static String name(Part part)throws Exception{
  String raw=part.getFileName();
  if(raw==null||raw.trim().isEmpty())raw=part.isMimeType("message/rfc822")||part.isMimeType("message/global")?"Пересланное письмо.eml":part.isMimeType("application/pdf")?"Вложение.pdf":"Вложение";
  return AttachmentFiles.safeName(AttachmentNames.decode(raw));
 }
 private static void walk(Part part,String path,int depth,int[] remaining,JSONArray files)throws Exception{if(depth>12||remaining[0]--<=0)throw new MessagingException("Слишком сложная структура вложений.");if(file(part)){files.put(new JSONObject().put("name",name(part)).put("size",part.getSize()).put("path",path));return;}if(part.isMimeType("multipart/*")){Multipart multi=(Multipart)part.getContent();for(int i=0;i<multi.getCount();i++)walk(multi.getBodyPart(i),AttachmentParts.child(path,""+i),depth+1,remaining,files);}}
}
