package ru.local.spbumailpilot;

import javax.mail.*;
import javax.mail.internet.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

public class MailTextTest {
    static void check(boolean value){if(!value)throw new AssertionError();}
    static MimeBodyPart utf8(String value){
        return new MimeBodyPart(){
            public String getContentType(){return "text/plain; charset=UTF-8";}
            public InputStream getInputStream(){return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));}
        };
    }
    public static void main(String[] args)throws Exception{
        final int[] bytes={0};byte[] large=new byte[2000000];Arrays.fill(large,(byte)'x');
        MimeBodyPart streamed=new MimeBodyPart(){
            public String getContentType(){return "text/plain; charset=UTF-8";}
            public Object getContent(){throw new AssertionError("Must not decode the complete text part");}
            public InputStream getInputStream(){return new FilterInputStream(new ByteArrayInputStream(large)){public int read(byte[] b,int off,int len)throws IOException{int n=super.read(b,off,len);if(n>0)bytes[0]+=n;return n;}};}
        };
        String text=MailText.read(streamed,100000);check(text.startsWith("xxx")&&text.contains("Показана часть текста"));check(bytes[0]<120000);
        int before=bytes[0];check(MailText.read(streamed,0).isEmpty()&&bytes[0]==before);
        MimeBodyPart encoded=new MimeBodyPart(new ByteArrayInputStream(("Content-Type: text/plain; charset=UTF-8\r\nContent-Transfer-Encoding: base64\r\n\r\n"+Base64.getEncoder().encodeToString("Привет, мир".getBytes(StandardCharsets.UTF_8))).getBytes(StandardCharsets.US_ASCII)));
        check(MailText.read(encoded,100).equals("Привет, мир"));
        String marker="\n[Показана часть текста]",emoji="\uD83D\uDE00";
        check(MailText.read(utf8("x"+emoji+"y"),2).equals("x"+marker));
        check(MailText.read(utf8(emoji),1).equals(marker));
        check(MailText.read(utf8(emoji),2).equals(emoji));
        check(MailText.read(utf8("x"+emoji+"y"),3).equals("x"+emoji+marker));
        String boundary=new String(new char[4095]).replace('\0','x');
        check(MailText.read(utf8(boundary+emoji),4096).equals(boundary+marker));
        check(MailText.read(utf8(boundary+emoji),4097).equals(boundary+emoji));
        MailReader limiter=new MailReader();
        java.lang.reflect.Method limit=MailReader.class.getDeclaredMethod("limit",String.class);limit.setAccessible(true);
        String prefix=new String(new char[99999]).replace('\0','x');
        check(limit.invoke(limiter,prefix+emoji+"y").equals(prefix+marker));
        check(limit.invoke(limiter,prefix.substring(1)+emoji+"y").equals(prefix.substring(1)+emoji+marker));
        ReadFlagTest.MemoryStore store=new ReadFlagTest.MemoryStore(Session.getInstance(new Properties()));MimeMultipart mixed=new MimeMultipart();
        MimeBodyPart body=new MimeBodyPart();body.setText(new String(new char[150000]).replace('\0','x'),"UTF-8");mixed.addBodyPart(body);
        final int[] downloads={0};MimeBodyPart attachment=new MimeBodyPart(){public int getSize(){return 4;}public InputStream getInputStream(){downloads[0]++;return new ByteArrayInputStream(new byte[]{1,2,3,4});}};
        attachment.setFileName("document.pdf");attachment.setHeader("Content-Type","application/pdf");attachment.setDisposition(Part.ATTACHMENT);mixed.addBodyPart(attachment);store.folder.message.setContent(mixed);store.folder.message.setHeader("Content-Type",mixed.getContentType());
        MailReader reader=new MailReader();java.lang.reflect.Field field=MailReader.class.getDeclaredField("store");field.setAccessible(true);field.set(reader,store);
        JSONObject result=reader.body("INBOX","42","7");check(result.getJSONArray("attachments").length()==1&&downloads[0]==0);check(!store.folder.message.isSet(Flags.Flag.SEEN));
        java.io.File directory=java.nio.file.Files.createTempDirectory("mail-body-fixture-").toFile();
        JSONObject first=reader.attachment("INBOX","42","7","1",directory),second=reader.attachment("INBOX","42","7","1",directory);
        check(first.getString("file").equals(second.getString("file"))&&downloads[0]==1);
        new File(first.getString("file")).delete();reader.attachment("INBOX","42","7","1",directory);check(downloads[0]==2);
        for(File file:directory.listFiles())file.delete();directory.delete();reader.close();
        System.out.println("PASS: bounded streaming text, MIME charset/base64 decoding, metadata after long text, no attachment fetch while reading, reusable file cache and missing-file recovery.");
    }
}

