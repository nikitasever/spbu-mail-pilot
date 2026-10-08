package ru.local.spbumailpilot;
import javax.mail.*;
import javax.mail.internet.*;
import java.io.*;
import java.util.Properties;
import java.nio.charset.StandardCharsets;
import org.json.*;
public class SearchAndFilesTest {
    static void check(boolean value){if(!value)throw new AssertionError();}
    public static void main(String[] args)throws Exception{
        for(String query:new String[]{"Документ", "a\"b\\c", "inbox"}){
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            com.sun.mail.iap.Protocol protocol=new com.sun.mail.iap.Protocol(new ByteArrayInputStream("* OK fixture\r\n+ continue\r\n+ continue\r\n+ continue\r\n".getBytes(StandardCharsets.UTF_8)),new PrintStream(out),new Properties(),false);
            protocol.writeCommand("UID SEARCH",SearchQuery.arguments(query));String wire=new String(out.toByteArray(),StandardCharsets.UTF_8);
            check(wire.contains("UID SEARCH CHARSET UTF-8 OR OR SUBJECT")&&wire.contains(" FROM ")&&wire.contains(" BODY "));check(wire.contains("\r\n"));
        }
        for(String query:new String[]{"x\r\nA2 LOGOUT","",new String(new char[201]).replace('\0','x')})try{SearchQuery.arguments(query);throw new AssertionError();}catch(IllegalArgumentException expected){}
        MimeMessage message=new MimeMessage(Session.getInstance(new Properties()));MimeMultipart multi=new MimeMultipart();MimeBodyPart text=new MimeBodyPart();text.setText("Body is not a filename");multi.addBodyPart(text);
        MimeBodyPart file=new MimeBodyPart(){@Override public InputStream getInputStream(){throw new AssertionError("Metadata scan downloaded bytes");}};file.setFileName("План.pdf");file.setDisposition(Part.ATTACHMENT);file.setContent(new byte[]{1,2},"application/pdf");multi.addBodyPart(file);message.setContent(multi);message.saveChanges();
        JSONArray files=AttachmentIndex.list(message,"план");check(files.length()==1&&files.getJSONObject(0).getString("path").equals("1"));check(AttachmentParts.resolve(message,"1")==file);check(AttachmentIndex.list(message,"missing").length()==0);
        ReadFlagTest.MemoryStore store=new ReadFlagTest.MemoryStore(Session.getInstance(new Properties()));MailReader reader=new MailReader();java.lang.reflect.Field field=MailReader.class.getDeclaredField("store");field.setAccessible(true);field.set(reader,store);
        File directory=java.nio.file.Files.createTempDirectory("mail-export-").toFile();JSONObject exported=reader.exportMessage("INBOX","42","7",directory);File saved=new File(exported.getString("file"));try(InputStream input=new FileInputStream(saved)){MimeMessage restored=new MimeMessage(Session.getInstance(new Properties()),input);check(restored.getSubject().equals("Test"));check(restored.getContent().toString().contains("Informational"));}check(!store.folder.message.isSet(Flags.Flag.SEEN));saved.delete();directory.delete();
        System.out.println("PASS: UTF-8 escaped server search, control-character rejection, filename metadata search without payload download, MIME path resolution and EML export preserving read status.");
    }
}
