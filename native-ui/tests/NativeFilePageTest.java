package ru.local.spbumailpilot;
import org.json.*;
import javax.mail.*;
import javax.mail.internet.*;
import java.io.*;
import java.util.*;
final class NativeFilePageTest {
    interface Action{void run()throws Exception;}
    static void check(boolean ok){if(!ok)throw new AssertionError();}
    static void refused(Action action)throws Exception{try{action.run();throw new AssertionError("Invalid file page accepted");}catch(Exception expected){}}
    static JSONObject row(String uid,String path,String name)throws Exception{return new JSONObject().put("name",name).put("path",path).put("size",-1).put("message",new JSONObject().put("uid",uid).put("validity","7").put("subject","fixture").put("bcc","private-hidden"));}
    static JSONObject page(int next,boolean more,JSONArray files)throws Exception{return new JSONObject().put("folder","INBOX").put("validity","7").put("query","pdf").put("snapshot","s1").put("total",100).put("nextOffset",next).put("hasMore",more).put("files",files);}
    public static void main(String[] args)throws Exception{
        NativeFilePage empty=NativeFilePage.first(page(50,true,new JSONArray()),"INBOX","pdf");
        check(empty.next==50&&empty.more&&empty.files.length()==0);
        JSONArray rows=new JSONArray().put(row("9007199254740993","1","Материалы.pdf")).put(row("42","0.1","План.pdf"));
        NativeFilePage full=empty.append(page(100,false,rows));check(full.files.length()==2&&!full.more&&full.next==100);
        check(full.files.getJSONObject(0).getJSONObject("message").getString("uid").equals("9007199254740993")&&!full.files.toString().contains("private-hidden"));
        rows.getJSONObject(0).put("name","changed");check(full.files.getJSONObject(0).getString("name").equals("Материалы.pdf"));
        refused(()->empty.append(page(100,false,new JSONArray()).put("query","docx")));
        refused(()->empty.append(page(100,false,new JSONArray()).put("folder","Other")));
        refused(()->empty.append(page(100,false,new JSONArray()).put("validity","8")));
        refused(()->empty.append(page(100,false,new JSONArray()).put("snapshot","s2")));
        refused(()->empty.append(page(50,true,new JSONArray())));
        refused(()->full.append(page(100,false,new JSONArray())));
        refused(()->NativeFilePage.first(page(51,true,new JSONArray()),"INBOX","pdf"));
        JSONObject numeric=row("42","1","A.pdf");numeric.getJSONObject("message").put("uid",42);refused(()->NativeFilePage.first(page(50,true,new JSONArray().put(numeric)),"INBOX","pdf"));
        refused(()->NativeFilePage.first(page(50,true,new JSONArray().put(row("42","../0","A.pdf"))),"INBOX","pdf"));
        JSONObject wrong=row("42","0","A.pdf");wrong.getJSONObject("message").put("folder","Other");refused(()->NativeFilePage.first(page(50,true,new JSONArray().put(wrong)),"INBOX","pdf"));
        JSONArray duplicate=new JSONArray().put(row("42","1","A.pdf")).put(row("42","1","A.pdf"));check(NativeFilePage.first(page(50,true,duplicate),"INBOX","pdf").files.length()==1);
        // The inventory must not decode a nested EML or an inline image to search names.
        MimeBodyPart nested=new MimeBodyPart(){public Object getContent(){throw new AssertionError("Nested message downloaded");}public InputStream getInputStream(){throw new AssertionError("Binary downloaded");}};
        nested.setHeader("Content-Type","message/rfc822");check(AttachmentIndex.list(nested,"eml").length()==1);
        MimeBodyPart inline=new MimeBodyPart();inline.setHeader("Content-Type","image/png");inline.setFileName("logo.png");inline.setDisposition(Part.INLINE);check(AttachmentIndex.list(inline,"").length()==0);
        refused(()->AttachmentIndex.list(nested,"bad\nquery"));
        ReadFlagTest.MemoryStore store=new ReadFlagTest.MemoryStore(Session.getInstance(new Properties()));
        MimeMultipart multi=new MimeMultipart();MimeBodyPart text=new MimeBodyPart();text.setText("Body");multi.addBodyPart(text);
        MimeBodyPart pdf=new MimeBodyPart(){public InputStream getInputStream(){throw new AssertionError("Downloaded while listing");}};pdf.setFileName("Материалы.pdf");pdf.setContent(new byte[]{1,2},"application/pdf");multi.addBodyPart(pdf);
        store.folder.message.setContent(multi);store.folder.message.saveChanges();
        MailReader reader=new MailReader();java.lang.reflect.Field field=MailReader.class.getDeclaredField("store");field.setAccessible(true);field.set(reader,store);
        reader.body("INBOX","42","7"); // Seed the cursor; this simple UIDFolder fixture has no IMAP command parser.
        long[] uids=new long[51];for(int i=0;i<uids.length;i++)uids[i]=i+1;
        for(Object[] seeded:new Object[][]{{"filePages",new UidPages(uids)},{"fileFolder","INBOX"},{"fileValidity",7L},{"fileGeneration",1L},{"fileQuery","pdf"}}){java.lang.reflect.Field f=MailReader.class.getDeclaredField((String)seeded[0]);f.setAccessible(true);f.set(reader,seeded[1]);}
        JSONObject result=reader.files("INBOX",1,"1","pdf");check(result.getString("folder").equals("INBOX")&&result.getString("validity").equals("7")&&result.getString("query").equals("pdf")&&result.getJSONArray("files").length()==1);
        refused(()->reader.files("INBOX",1,result.getString("snapshot"),"docx"));
        for(int mode:store.folder.modes)check(mode==Folder.READ_ONLY);check(!store.folder.message.isSet(Flags.Flag.SEEN));reader.close();
        System.out.println("PASS file library: empty-page continuation, exact query/folder/UIDVALIDITY/snapshot binding, immutable/deduplicated rows, large string UIDs, malformed refs rejected, no Bcc in list, metadata-only nested EML/inline handling and read-only protocol fixture");
    }
}
