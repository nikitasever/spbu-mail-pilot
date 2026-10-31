package ru.local.spbumailpilot;
import java.io.*;import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.util.*;import org.json.*;
public final class EmlDocumentTest {
 static void check(boolean v){if(!v)throw new AssertionError();}
 static File write(File dir,String name,String value)throws Exception{File f=new File(dir,name);Files.write(f.toPath(),value.getBytes(StandardCharsets.UTF_8));return f;}
 static String base64(byte[] bytes){return Base64.getMimeEncoder().encodeToString(bytes);}
 static String encode(String value){return "=?UTF-8?B?"+Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8))+"?=";}
 public static void main(String[] args)throws Exception{
  File dir=Files.createTempDirectory("eml-document-test-").toFile();
  try{
   String nested="From: colleague@example.invalid\r\nTo: reader@example.invalid\r\nSubject: "+encode("Вложенная тема")+"\r\nMIME-Version: 1.0\r\nContent-Type: multipart/mixed; boundary=inner\r\n\r\n--inner\r\nContent-Type: text/plain; charset=UTF-8\r\n\r\nВнутренний текст\r\n--inner\r\nContent-Type: application/pdf\r\nContent-Disposition: attachment; filename=material.pdf\r\nContent-Transfer-Encoding: base64\r\n\r\n"+base64("%PDF-1.7\nfixture".getBytes(StandardCharsets.US_ASCII))+"\r\n--inner--\r\n";
   String root="From: "+encode("Отправитель")+" <sender@example.invalid>\r\nTo: reader@example.invalid\r\nSubject: "+encode("Пересланные материалы")+"\r\nMIME-Version: 1.0\r\nContent-Type: multipart/mixed; boundary=outer\r\n\r\n--outer\r\nContent-Type: multipart/alternative; boundary=alt\r\n\r\n--alt\r\nContent-Type: text/plain; charset=windows-1251\r\nContent-Transfer-Encoding: base64\r\n\r\n"+base64("Проверка русского текста".getBytes("windows-1251"))+"\r\n--alt\r\nContent-Type: text/html; charset=UTF-8\r\n\r\n<p>Alternative not selected<img src=\"https://example.invalid/tracker\"></p>\r\n--alt--\r\n--outer\r\nContent-Type: message/rfc822\r\nContent-Transfer-Encoding: base64\r\n\r\n"+base64(nested.getBytes(StandardCharsets.UTF_8))+"\r\n--outer--\r\n";
   EmlDocument document=EmlDocument.read(write(dir,"outer.eml",root));
   check(document.subject.equals("Пересланные материалы")&&document.from.contains("Отправитель"));
   check(document.segments.size()==1&&!document.segments.get(0).html&&document.segments.get(0).text.contains("Проверка русского текста"));
   check(document.files.length()==1&&document.files.getJSONObject(0).getString("name").equals("Пересланное письмо.eml"));
   String path=document.files.getJSONObject(0).getString("path");
   JSONObject forward=document.extract(path,dir,()->false);check(forward.getString("mime").equals("message/rfc822"));
   check(Arrays.equals(Files.readAllBytes(new File(forward.getString("file")).toPath()),nested.getBytes(StandardCharsets.UTF_8)));
   EmlDocument inner=EmlDocument.read(new File(forward.getString("file")));check(inner.subject.equals("Вложенная тема")&&inner.files.length()==1);
   JSONObject pdf=inner.extract(inner.files.getJSONObject(0).getString("path"),dir,()->false);
   check(Arrays.equals(Files.readAllBytes(new File(pdf.getString("file")).toPath()),"%PDF-1.7\nfixture".getBytes(StandardCharsets.US_ASCII)));
   int before=dir.list().length;try{document.extract(path,dir,()->true);throw new AssertionError();}catch(BulkAttachments.Cancelled expected){}check(dir.list().length==before);
   try{document.extract("../1",dir,()->false);throw new AssertionError();}catch(IOException expected){}
   EmlDocument global=EmlDocument.read(write(dir,"global.eml",root.replace("Content-Type: message/rfc822", "Content-Type: message/global")));
   check(global.files.length()==1&&global.files.getJSONObject(0).getString("name").endsWith(".eml"));
   JSONObject globalFile=global.extract(global.files.getJSONObject(0).getString("path"),dir,()->false);
   check(globalFile.getString("mime").equals("message/rfc822"));
   check(Arrays.equals(Files.readAllBytes(new File(globalFile.getString("file")).toPath()),nested.getBytes(StandardCharsets.UTF_8)));
   EmlDocument html=EmlDocument.read(write(dir,"html.eml","From: sender@example.invalid\r\nContent-Type: text/html; charset=UTF-8\r\n\r\n<p>Текст</p><img src=\"https://example.invalid/image\"><script>doNotRun()</script>"));
   check(html.segments.size()==1&&html.segments.get(0).html&&html.segments.get(0).text.contains("Текст"));
   try{EmlDocument.read(write(dir,"bad.eml","not an RFC message"));throw new AssertionError();}catch(IOException expected){}
   File large=new File(dir,"large.eml");try(RandomAccessFile file=new RandomAccessFile(large,"rw")){file.setLength(EmlDocument.MAX_BYTES+1);}
   try{EmlDocument.read(large);throw new AssertionError();}catch(IOException expected){}
   check(AttachmentFiles.mime("mail.EML",null).equals("message/rfc822"));check(AttachmentFiles.mime("nameless","message/rfc822").equals("message/rfc822"));
   for(String extension:new String[]{"gif","webp","csv","json","xml","msg","odt","7z","ics"})check(!AttachmentFiles.mime("file."+extension,null).equals("application/octet-stream"));
   System.out.println("EmlDocumentTest passed: decoded headers/body, nested RFC822 byte preservation, inner PDF, cancellation cleanup, bounds, safe paths and MIME routing.");
  }finally{for(File f:dir.listFiles())f.delete();dir.delete();}
 }
}
