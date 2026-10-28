package ru.local.spbumailpilot;
import javax.mail.*;
import javax.mail.internet.*;
import javax.activation.*;
import javax.mail.util.*;
import org.json.*;
import java.util.*;
import java.io.*;
import java.nio.file.*;
import java.util.concurrent.atomic.*;
final class NativeForwardOriginalTest {
    interface Action{void run()throws Exception;}
    static void check(boolean value){if(!value)throw new AssertionError();}
    static void refused(Action action)throws Exception{try{action.run();throw new AssertionError("Invalid original accepted");}catch(Exception expected){}}
    static void empty(Path path)throws Exception{try(java.util.stream.Stream<Path> files=Files.list(path)){check(files.count()==0);}}
    public static void main(String[] args)throws Exception{
        String owner="account-"+"a".repeat(64);Session session=Session.getInstance(new Properties());
        JSONObject ref=new JSONObject().put("folder","INBOX").put("uid","42").put("validity","7");
        JSONObject header=new JSONObject().put("subject","Защита").put("bcc","private@example.invalid").put("messageId","<parent@example.invalid>");
        NativeDraft draft=NativeForwardOriginal.create(owner,header,ref,"\n\n-- Подпись");
        check(draft.mail.getString("body").equals("\n\n-- Подпись")&&draft.mail.getString("subject").equals("Fwd: Защита"));
        check(NativeForwardFiles.pending(draft)==0);
        check(draft.mail.getString("to").isEmpty()&&draft.mail.getString("bcc").isEmpty()&&draft.mail.getString("inReplyTo").isEmpty());
        check(!draft.mail.toString().contains("private@example.invalid")&&!ForwardOriginal.ready(draft.mail));
        SavedAccounts.Login login=new SavedAccounts.Login("example.invalid",993,"own@example.invalid","fixture");
        JSONObject addressed=new JSONObject(draft.mail.toString()).put("to","chosen@example.invalid");
        refused(()->MailSender.message(session,login,addressed,new File("unused")));
        Path directory=Files.createTempDirectory("native-eml-");MailReader reader=new MailReader();
        try{
            ReadFlagTest.MemoryStore store=new ReadFlagTest.MemoryStore(session);
            MimeMessage original=store.folder.message;
            original.setRecipients(Message.RecipientType.BCC,"private@example.invalid");original.setHeader("Resent-Bcc","other-private@example.invalid");
            original.setHeader("X-Original-Header","kept");original.setSubject("Ж".repeat(200),"UTF-8");
            MimeMultipart related=new MimeMultipart("related");MimeBodyPart html=new MimeBodyPart();html.setText("<p>Ж🙂<img src=\"cid:logo\"></p>","UTF-8","html");related.addBodyPart(html);
            byte[] bytes=new byte[]{0,1,2,(byte)255};MimeBodyPart image=new MimeBodyPart();image.setDataHandler(new DataHandler(new ByteArrayDataSource(bytes,"image/png")));image.setHeader("Content-ID","<logo>");image.setDisposition(Part.INLINE);related.addBodyPart(image);
            original.setContent(related);original.saveChanges();
            java.lang.reflect.Field field=MailReader.class.getDeclaredField("store");field.setAccessible(true);field.set(reader,store);
            AtomicLong progress=new AtomicLong();JSONObject exported=reader.exportMessage("INBOX","42","7",directory.toFile(),true,()->false,progress::set);
            File file=new File(exported.getString("file"));check(file.getName().startsWith("mail-")&&progress.get()==file.length()&&exported.getString("name").length()<=160&&exported.getString("name").endsWith(".eml"));
            MimeMessage eml;try(InputStream input=new FileInputStream(file)){eml=new MimeMessage(session,input);}
            check(eml.getHeader("Bcc")==null&&eml.getHeader("Resent-Bcc")==null&&eml.getHeader("X-Original-Header")[0].equals("kept"));
            Multipart inner=(Multipart)eml.getContent();check(inner.getBodyPart(0).getContent().toString().contains("cid:logo")&&Arrays.equals(bytes,inner.getBodyPart(1).getInputStream().readAllBytes()));
            check(original.getRecipients(Message.RecipientType.BCC).length==1&&original.getHeader("Resent-Bcc")!=null&&!original.isSet(Flags.Flag.SEEN));
            for(int mode:store.folder.modes)check(mode==Folder.READ_ONLY);
            JSONObject entry=new JSONObject().put("token",file.getName()).put("name",exported.getString("name")).put("size",file.length());
            NativeDraft ready=NativeForwardOriginal.complete(draft,entry);check(ForwardOriginal.ready(ready.mail));
            check(ForwardOriginal.ready(NativeDraft.parse(owner,ready.json().toString()).mail));refused(()->NativeForwardOriginal.complete(ready,entry));
            JSONObject outgoing=new JSONObject(ready.mail.toString()).put("to","chosen@example.invalid");
            MimeMessage sent=MailSender.message(session,login,outgoing,directory.toFile());ByteArrayOutputStream wire=new ByteArrayOutputStream();sent.writeTo(wire);
            MimeMessage received=new MimeMessage(session,new ByteArrayInputStream(wire.toByteArray()));
            check(received.getAllRecipients().length==1&&received.getHeader("Bcc")==null&&received.getHeader("References")==null);
            Multipart outer=(Multipart)received.getContent();check(Arrays.equals(Files.readAllBytes(file.toPath()),outer.getBodyPart(1).getInputStream().readAllBytes()));
            JSONObject removed=new JSONObject(outgoing.toString()).put("attachments",new JSONArray());refused(()->MailSender.message(session,login,removed,directory.toFile()));
            JSONObject remapped=new JSONObject(ready.mail.toString());remapped.getJSONArray("attachments").getJSONObject(0).put("token","mail-queue-copy.eml");check(ForwardOriginal.ready(remapped));
            file.delete();empty(directory);
            AtomicBoolean cancelled=new AtomicBoolean();refused(()->reader.exportMessage("INBOX","42","7",directory.toFile(),true,cancelled::get,n->cancelled.set(true)));empty(directory);
            refused(()->reader.exportMessage("INBOX","42","8",directory.toFile(),true));empty(directory);
            MimeMessage broken=new MimeMessage(session){public void writeTo(OutputStream out,String[] ignore)throws IOException{out.write(1);throw new IOException("connection lost");}};
            refused(()->MessageExport.write(broken,directory.toFile(),true,()->false,n->{}));empty(directory);
            MimeMessage huge=new MimeMessage(session){public void writeTo(OutputStream out,String[] ignore)throws IOException{out.write(new byte[1],0,(int)AttachmentFiles.MAX_BYTES+1);}};
            refused(()->MessageExport.write(huge,directory.toFile(),true,()->false,n->{}));empty(directory);
            original.setFlag(Flags.Flag.DELETED,true);refused(()->reader.exportMessage("INBOX","42","7",directory.toFile(),true));empty(directory);
        }finally{reader.close();try(java.util.stream.Stream<Path> paths=Files.walk(directory)){paths.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.delete(p);}catch(IOException e){throw new UncheckedIOException(e);}});}}
        System.out.println("PASS EML forward: full HTML/inline MIME roundtrip and exact attached bytes, top Bcc stripped without mutating source, blank recipients/thread context, readiness across restore/remap/removal, cancellation/oversize/failure cleanup, read-only UIDVALIDITY checks; no network");
    }
}
