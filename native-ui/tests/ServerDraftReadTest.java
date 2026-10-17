package ru.local.spbumailpilot;
import javax.mail.*;
import javax.mail.internet.*;
import javax.activation.DataHandler;
import javax.mail.util.ByteArrayDataSource;
import org.json.*;
import java.util.*;
import java.io.*;
final class ServerDraftReadTest {
    interface Action{void run()throws Exception;}
    static void check(boolean ok){if(!ok)throw new AssertionError();}
    static void refused(Action action)throws Exception{try{action.run();throw new AssertionError("Unsupported draft accepted");}catch(Exception expected){}}
    static MimeMessage plain(String body)throws Exception{MimeMessage message=new MimeMessage(Session.getInstance(new Properties()));message.setText(body,"UTF-8");message.setSubject("Тема");message.setFrom("from@example.invalid");message.setRecipients(Message.RecipientType.TO,InternetAddress.parse("\"Иван Петров\" <to@example.invalid>"));message.setRecipients(Message.RecipientType.CC,"cc@example.invalid");message.setRecipients(Message.RecipientType.BCC,"hidden@example.invalid");message.setFlag(Flags.Flag.DRAFT,true);message.saveChanges();return message;}
    static class DraftMemoryFolder extends ReadFlagTest.MemoryFolder {
        DraftMemoryFolder(Store store,Session session)throws Exception{super(store,session);message.setText("complete Ж🙂","UTF-8");message.setFlag(Flags.Flag.DRAFT,true);message.saveChanges();}
        public String getName(){return "Drafts";}public String getFullName(){return "Drafts";}
    }
    static class DraftMemoryStore extends ReadFlagTest.MemoryStore {
        final DraftMemoryFolder drafts;
        DraftMemoryStore(Session session)throws Exception{super(session);drafts=new DraftMemoryFolder(this,session);}
        public Folder getDefaultFolder(){return drafts;}public Folder getFolder(String name){return drafts;}
    }
    public static void main(String[] args)throws Exception{
        String text="Полный текст Ж🙂\n[Показана часть текста]\n-- подпись";MimeMessage message=plain(text);
        message.setHeader("In-Reply-To","<parent@example.invalid>");message.setHeader("References","<older@example.invalid> <parent@example.invalid>");
        JSONObject result=ServerDraftRead.read(message);JSONObject draft=result.getJSONObject("draft");
        check(draft.getString("body").equals(text)&&draft.getString("bcc").equals("hidden@example.invalid")&&draft.getString("to").contains("Иван Петров")&&draft.getJSONArray("references").length()==2&&!result.getBoolean("html"));
        check(!message.isSet(Flags.Flag.SEEN)&&message.isSet(Flags.Flag.DRAFT));
        MimeMessage noDraft=plain("text");noDraft.setFlag(Flags.Flag.DRAFT,false);refused(()->ServerDraftRead.read(noDraft));
        MimeMessage deleted=plain("text");deleted.setFlag(Flags.Flag.DELETED,true);refused(()->ServerDraftRead.read(deleted));
        check(ServerDraftRead.read(plain("x".repeat(100000))).getJSONObject("draft").getString("body").length()==100000);
        refused(()->ServerDraftRead.read(plain("x".repeat(100001))));
        MimeMessage alternative=plain("");MimeMultipart alternatives=new MimeMultipart("alternative");MimeBodyPart html=new MimeBodyPart();html.setText("<p>HTML</p>","UTF-8","html");MimeBodyPart normal=new MimeBodyPart();normal.setText("exact plain","UTF-8");alternatives.addBodyPart(html);alternatives.addBodyPart(normal);alternative.setContent(alternatives);alternative.saveChanges();
        check(ServerDraftRead.read(alternative).getJSONObject("draft").getString("body").equals("exact plain"));
        MimeMessage onlyHtml=plain("");onlyHtml.setText("<p>HTML Ж</p>","UTF-8","html");onlyHtml.saveChanges();check(ServerDraftRead.read(onlyHtml).getBoolean("html"));
        MimeMessage binary=plain("");MimeMultipart mixed=new MimeMultipart();MimeBodyPart textPart=new MimeBodyPart();textPart.setText("body","UTF-8");mixed.addBodyPart(textPart);
        MimeBodyPart pdf=new MimeBodyPart(){@Override public InputStream getInputStream(){throw new AssertionError("Binary payload read before consent");}};pdf.setContent(new byte[]{1,2,3},"application/pdf");pdf.setFileName("Материалы.pdf");pdf.setDisposition(Part.ATTACHMENT);mixed.addBodyPart(pdf);binary.setContent(mixed);binary.saveChanges();
        JSONObject metadata=ServerDraftRead.read(binary);check(metadata.getJSONArray("attachments").length()==1&&metadata.getJSONObject("draft").getString("body").equals("body"));
        MimeMessage malformed=plain("");malformed.setDataHandler(new DataHandler(new ByteArrayDataSource(new byte[]{(byte)0xc3,0x28},"text/plain; charset=UTF-8")));malformed.saveChanges();refused(()->ServerDraftRead.read(malformed));
        MimeMessage parent=plain("text");parent.setHeader("In-Reply-To","<one@example.invalid> <two@example.invalid>");refused(()->ServerDraftRead.read(parent));
        MimeMessage refs=plain("text");refs.setHeader("References","<one@example.invalid>");refused(()->ServerDraftRead.read(refs));
        MimeMessage sections=plain("");MimeMultipart several=new MimeMultipart();several.addBodyPart(textPart);MimeBodyPart second=new MimeBodyPart();second.setText("second","UTF-8");several.addBodyPart(second);sections.setContent(several);sections.saveChanges();refused(()->ServerDraftRead.read(sections));
        DraftMemoryStore store=new DraftMemoryStore(Session.getInstance(new Properties()));MailReader reader=new MailReader();java.lang.reflect.Field field=MailReader.class.getDeclaredField("store");field.setAccessible(true);field.set(reader,store);
        check(reader.serverDraft("Drafts","42","7").getJSONObject("draft").getString("body").equals("complete Ж🙂"));
        refused(()->reader.serverDraft("INBOX","42","7"));refused(()->reader.serverDraft("Drafts","42","8"));refused(()->reader.serverDraft("Drafts","99","7"));
        for(int mode:store.drafts.modes)check(mode==Folder.READ_ONLY);check(!store.drafts.message.isSet(Flags.Flag.SEEN));reader.close();check(!store.drafts.opened);
        System.out.println("PASS ServerDraftRead: exact complete Unicode text/envelope/Bcc, DRAFT/deleted guards, no Seen, plain preferred, HTML disclosed, no binary reads, size/charset/thread/ambiguous-body failures without truncation");
    }
}
