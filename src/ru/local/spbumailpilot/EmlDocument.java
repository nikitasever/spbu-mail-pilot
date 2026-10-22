package ru.local.spbumailpilot;

import java.io.*;
import java.util.*;
import javax.mail.*;
import javax.mail.internet.*;
import javax.activation.MailcapCommandMap;
import org.json.*;

/** Bounded local MIME viewer. No connection, external image fetch or implicit nested extraction. */
final class EmlDocument {
 static final long MAX_BYTES=20L*1024*1024;
 static final int MAX_TEXT=100000;
 static boolean eml(String name,String mime){return "message/rfc822".equalsIgnoreCase(mime)||"message/global".equalsIgnoreCase(mime)||name!=null&&name.toLowerCase(Locale.ROOT).endsWith(".eml");}
 static final class Segment {final String text;final boolean html;Segment(String text,boolean html){this.text=text;this.html=html;}}
 final MimeMessage message;final String subject,from,to,date;final JSONArray files;final List<Segment> segments;
 private EmlDocument(MimeMessage message)throws Exception{
  this.message=message;
  MailcapCommandMap handlers=new MailcapCommandMap();
  handlers.addMailcap("multipart/*;; x-java-content-handler=com.sun.mail.handlers.multipart_mixed");
  handlers.addMailcap("message/rfc822;; x-java-content-handler=com.sun.mail.handlers.message_rfc822");
  prepare(message,handlers,0,new int[]{500});
  subject=header(message,"Subject");from=header(message,"From");to=header(message,"To");date=header(message,"Date");
  if(subject.isEmpty()&&from.isEmpty()&&to.isEmpty()&&date.isEmpty()&&message.getHeader("MIME-Version")==null&&message.getHeader("Content-Type")==null)throw new IOException("Not an RFC message");
  files=MailAttachments.list(message);segments=new ArrayList<>();body(message,0,new int[]{500},new int[]{MAX_TEXT},segments);
 }
 private static String header(MimeMessage m,String key)throws Exception{
  String value=m.getHeader(key,null);if(value==null)return "";if(value.length()>3000)value=value.substring(0,3000);
  try{return MimeUtility.decodeText(value).replaceAll("[\\r\\n]+"," ");}catch(Exception malformed){return value.replaceAll("[\\r\\n]+"," ");}
 }
 static EmlDocument read(File file)throws Exception{
  if(!file.isFile()||file.length()==0||file.length()>MAX_BYTES)throw new IOException("EML preview limit is 20 MiB");
  Properties properties=new Properties();properties.setProperty("mail.mime.allowutf8","true");
  try(InputStream input=new BufferedInputStream(new FileInputStream(file))){return new EmlDocument(new MimeMessage(Session.getInstance(properties),input));}
 }
 private static void prepare(Part p,MailcapCommandMap handlers,int depth,int[] remaining)throws Exception{
  if(depth>12||remaining[0]--<=0)throw new IOException("MIME structure too complex");
  p.getDataHandler().setCommandMap(handlers);
  if(!MailAttachments.file(p)&&p.isMimeType("multipart/*")){
   Multipart parts=(Multipart)p.getContent();for(int i=0;i<parts.getCount();i++)prepare(parts.getBodyPart(i),handlers,depth+1,remaining);
  }
 }
 private static void body(Part p,int depth,int[] remaining,int[] budget,List<Segment> out)throws Exception{
  if(depth>12||remaining[0]--<=0)throw new IOException("MIME structure too complex");
  if(budget[0]<=0||MailAttachments.file(p))return;
  if(p.isMimeType("text/plain")||p.isMimeType("text/html")){
   String value=MailText.read(p,budget[0]);budget[0]-=value.length();out.add(new Segment(value,p.isMimeType("text/html")));return;
  }
  if(p.isMimeType("multipart/*")){
   Multipart parts=(Multipart)p.getContent();
   if(p.isMimeType("multipart/alternative")){
    for(int i=0;i<parts.getCount();i++)if(parts.getBodyPart(i).isMimeType("text/plain")&&!MailAttachments.file(parts.getBodyPart(i))){body(parts.getBodyPart(i),depth+1,remaining,budget,out);return;}
    for(int i=parts.getCount()-1;i>=0;i--)if(parts.getBodyPart(i).isMimeType("text/html")&&!MailAttachments.file(parts.getBodyPart(i))){body(parts.getBodyPart(i),depth+1,remaining,budget,out);return;}
   }
   for(int i=0;i<parts.getCount();i++)body(parts.getBodyPart(i),depth+1,remaining,budget,out);
  }
 }
 JSONObject extract(String path,File directory,java.util.function.BooleanSupplier cancelled)throws Exception{
  boolean present=false;for(int i=0;i<files.length();i++)if(path.equals(files.getJSONObject(i).getString("path")))present=true;
  if(!present)throw new IOException("Unknown nested attachment");
  Part part=AttachmentParts.resolve(message,path);String name=MailAttachments.name(part);
  String mime=AttachmentFiles.mime(name,new ContentType(part.getContentType()).getBaseType());
  File output=AttachmentTransfer.copy(part,directory,name,cancelled,(bytes,total)->{});
  return new JSONObject().put("file",output.getAbsolutePath()).put("name",name).put("mime",mime);
 }
}
