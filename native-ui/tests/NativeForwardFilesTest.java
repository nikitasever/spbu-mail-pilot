package ru.local.spbumailpilot;
import org.json.*;
import javax.mail.*;
import javax.mail.internet.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
final class NativeForwardFilesTest {
    interface Action{void run()throws Exception;}
    static void check(boolean ok){if(!ok)throw new AssertionError();}
    static void refused(Action a)throws Exception{try{a.run();throw new AssertionError("Accepted invalid files");}catch(Exception expected){}}
    public static void main(String[] args)throws Exception{
        String owner="account-"+"a".repeat(64);
        JSONObject metadata=new JSONObject().put("folder","INBOX").put("uid","9007199254740993").put("validity","7")
          .put("attachments",new JSONArray().put(new JSONObject().put("path","1").put("name","Материалы.pdf")));
        NativeDraft draft=NativeForwardFiles.prepare(NativeForward.create(owner,new JSONObject().put("subject","Тема").put("bcc","secret@example.invalid"),"exact text Ж🙂",""),metadata);
        check(NativeForwardFiles.pending(draft)==1&&draft.mail.getJSONObject("forwardSource").getString("uid").equals("9007199254740993"));
        check(!draft.mail.toString().contains("secret@example.invalid"));
        NativeForwardFiles.verify(draft,new JSONObject(metadata.toString()));
        JSONObject other=new JSONObject(metadata.toString()).put("validity","8");refused(()->NativeForwardFiles.verify(draft,other));
        JSONObject moved=new JSONObject(metadata.toString()).put("folder","Other");refused(()->NativeForwardFiles.verify(draft,moved));
        JSONObject numeric=new JSONObject(metadata.toString()).put("uid",42);refused(()->NativeForwardFiles.source(numeric));
        JSONObject duplicate=new JSONObject(metadata.toString());duplicate.getJSONArray("attachments").put(duplicate.getJSONArray("attachments").getJSONObject(0));refused(()->NativeForwardFiles.source(duplicate));
        JSONObject renamed=new JSONObject(metadata.toString());renamed.getJSONArray("attachments").getJSONObject(0).put("name","Other.pdf");refused(()->NativeForwardFiles.verify(draft,renamed));
        refused(()->NativeForwardFiles.complete(draft,new JSONArray()));
        // Exercise the actual attachment reader and MIME sender, not a mock of the copy algorithm.
        ReadFlagTest.MemoryStore store=new ReadFlagTest.MemoryStore(Session.getInstance(new Properties()));
        byte[] payload=new byte[]{0,1,2,3,(byte)255};
        MimeMultipart mixed=new MimeMultipart();MimeBodyPart text=new MimeBodyPart();text.setText("exact text Ж🙂","UTF-8");mixed.addBodyPart(text);
        MimeBodyPart file=new MimeBodyPart();file.setDataHandler(new javax.activation.DataHandler(new javax.mail.util.ByteArrayDataSource(payload,"application/pdf")));file.setFileName("Материалы.pdf");file.setDisposition(Part.ATTACHMENT);mixed.addBodyPart(file);
        store.folder.message.setContent(mixed);store.folder.message.saveChanges();
        MailReader reader=new MailReader();java.lang.reflect.Field field=MailReader.class.getDeclaredField("store");field.setAccessible(true);field.set(reader,store);
        JSONObject manifest=reader.forwardAttachments("INBOX","42","7");
        NativeDraft selected=NativeForwardFiles.prepare(NativeForward.create(owner,new JSONObject().put("subject","Тема"),"exact text Ж🙂",""),manifest);
        Path directory=Files.createTempDirectory("native-forward-files-");
        try{
            JSONObject downloaded=reader.attachment("INBOX","42","7","1",directory.toFile());File local=new File(downloaded.getString("file"));
            check(Arrays.equals(payload,Files.readAllBytes(local.toPath())));
            JSONArray added=new JSONArray().put(new JSONObject().put("token",local.getName()).put("name","Материалы.pdf").put("size",local.length()));
            NativeForwardFiles.verify(selected,reader.forwardAttachments("INBOX","42","7"));
            NativeDraft ready=NativeForwardFiles.complete(selected,added);check(NativeForwardFiles.pending(ready)==0&&ready.revision==selected.revision+1);
            check(NativeForwardFiles.pending(NativeDraft.parse(owner,ready.json().toString()))==0);
            refused(()->NativeForwardFiles.complete(ready,added));check(selected.mail.getJSONArray("attachments").length()==0);
            JSONObject addressed=new JSONObject(ready.mail.toString()).put("to","chosen@example.invalid");
            MimeMessage sent=MailSender.message(Session.getInstance(new Properties()),new SavedAccounts.Login("example.invalid",993,"own@example.invalid","fixture"),addressed,directory.toFile());
            ByteArrayOutputStream wire=new ByteArrayOutputStream();sent.writeTo(wire);
            MimeMessage decoded=new MimeMessage(Session.getInstance(new Properties()),new ByteArrayInputStream(wire.toByteArray()));
            Multipart parts=(Multipart)decoded.getContent();check(parts.getCount()==2&&parts.getBodyPart(0).getContent().toString().contains("exact text Ж🙂"));
            check(Arrays.equals(payload,parts.getBodyPart(1).getInputStream().readAllBytes()));
            check(decoded.getHeader("Bcc")==null&&decoded.getHeader("References")==null&&decoded.getAllRecipients().length==1);
            refused(()->reader.forwardAttachments("INBOX","42","8"));store.folder.message.setFlag(Flags.Flag.DELETED,true);refused(()->reader.forwardAttachments("INBOX","42","7"));
            for(int mode:store.folder.modes)check(mode==Folder.READ_ONLY);check(!store.folder.message.isSet(Flags.Flag.SEEN));
        }finally{reader.close();try(java.util.stream.Stream<Path> paths=Files.walk(directory)){paths.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.delete(p);}catch(IOException e){throw new UncheckedIOException(e);}});}}
        System.out.println("PASS original forward files: exact references/manifest, no inherited Bcc, all-files commit, durable one-time guard, read-only download and real outgoing MIME bytes, stale/deleted rejection; no network");
    }
}
