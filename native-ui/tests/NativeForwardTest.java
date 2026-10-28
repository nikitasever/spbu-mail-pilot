package ru.local.spbumailpilot;

import org.json.*;
import javax.mail.*;
import javax.mail.internet.*;
import java.util.*;
import java.io.*;

public final class NativeForwardTest {
    interface Check{void run()throws Exception;}
    static void check(boolean value){if(!value)throw new AssertionError();}
    static void reject(Check action)throws Exception{try{action.run();}catch(NativeForward.Invalid expected){return;}throw new AssertionError("Expected forward rejection");}
    static String fill(int size){char[] chars=new char[size];Arrays.fill(chars,'x');return new String(chars);}
    public static void main(String[] args)throws Exception {
        String owner="account-"+fill(64).replace('x','a');
        JSONObject source=new JSONObject().put("subject","Материалы защиты").put("sender","Коллега").put("email","sender@example.invalid")
            .put("date","2026-10-14T15:00:00+03:00").put("replyTo","reply@example.invalid")
            .put("messageId","<private-parent@example.invalid>").put("references",new JSONArray().put("<private-root@example.invalid>"))
            .put("toRecipients",new JSONArray().put("other@example.invalid"))
            .put("bccRecipients",new JSONArray().put("secret@example.invalid"))
            .put("recipients",new JSONArray().put(new JSONObject().put("email","secret@example.invalid")))
            .put("attachments",new JSONArray().put(new JSONObject().put("token","server-token").put("name","private.pdf").put("path","1")))
            .put("folder","PRIVATE-FOLDER").put("uid","123").put("validity","7");
        String before=source.toString(),body="Добрый день!\r\n\r\n<script>буквальный текст</script> \uD83D\uDE00";
        NativeDraft draft=NativeForward.create(owner,source,body,"\n\n-- \nПодпись");
        check(source.toString().equals(before));
        check(draft.mail.getString("subject").equals("Fwd: Материалы защиты"));
        for(String field:new String[]{"to","cc","bcc","inReplyTo"})check(draft.mail.getString(field).isEmpty());
        check(draft.mail.getJSONArray("references").length()==0&&draft.mail.getJSONArray("attachments").length()==0);
        check(draft.mail.getString("body").endsWith(body));
        check(draft.mail.getString("body").startsWith("\n\n-- \nПодпись\n\n---------- Пересланное письмо"));
        check(draft.mail.getString("body").contains("От: Коллега <sender@example.invalid>\n"));
        String record=draft.json().toString();check(!record.contains("secret@example.invalid")&&!record.contains("private-parent")&&!record.contains("PRIVATE-FOLDER")&&!record.contains("private.pdf"));
        NativeDraft restored=NativeDraft.parse(owner,record);check(restored.mail.getString("kind").equals("forward-text"));
        check(restored.mail.getString("body").equals(draft.mail.getString("body")));
        for(String subject:new String[]{"FW: Already","fWd: Already"})check(NativeForward.create(owner,new JSONObject(source.toString()).put("subject",subject),body,"").mail.getString("subject").equals(subject));
        JSONObject same=new JSONObject(source.toString()).put("sender","sender@example.invalid");
        check(NativeForward.create(owner,same,body,"").mail.getString("body").contains("От: sender@example.invalid\n"));
        JSONObject controls=new JSONObject(source.toString()).put("sender","Name\r\nBcc: not-a-header").put("date","date\u2028fake");
        String sanitized=NativeForward.create(owner,controls,body,"").mail.getString("body");
        check(sanitized.contains("От: Name  Bcc: not-a-header <sender@example.invalid>\n")&&!sanitized.contains("\nBcc:"));
        reject(()->NativeForward.create(owner,null,body,""));
        reject(()->NativeForward.create(owner,source," \n", ""));
        reject(()->NativeForward.create(owner,source,"Фрагмент\n[Показана часть текста]", ""));
        reject(()->NativeForward.create(owner,source,fill(100000),""));
        reject(()->NativeForward.create(owner,new JSONObject(source.toString()).put("subject",fill(500)),body,""));
        reject(()->NativeForward.create(owner,new JSONObject(source.toString()).put("subject","bad\r\nBcc: secret@example.invalid"),body,""));
        int overhead=NativeForward.create(owner,source,"x","").mail.getString("body").length()-1;
        check(NativeForward.create(owner,source,fill(100000-overhead),"").mail.getString("body").length()==100000);
        // Sending is impossible until the user explicitly fills in a recipient.
        SavedAccounts.Login login=new SavedAccounts.Login("example.invalid",993,"own@example.invalid","fixture");
        try{MailSender.message(Session.getInstance(new Properties()),login,draft.mail,new File("unused"));throw new AssertionError();}catch(IllegalArgumentException expected){}
        JSONObject addressed=new JSONObject(draft.mail.toString()).put("to","chosen@example.invalid");
        MimeMessage message=MailSender.message(Session.getInstance(new Properties()),login,addressed,new File("unused"));
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();message.writeTo(bytes);
        MimeMessage wire=new MimeMessage(Session.getInstance(new Properties()),new ByteArrayInputStream(bytes.toByteArray()));
        check(wire.isMimeType("text/plain")&&wire.getContent().toString().contains("<script>буквальный текст</script>"));
        check(wire.getAllRecipients().length==1&&((InternetAddress)wire.getAllRecipients()[0]).getAddress().equals("chosen@example.invalid"));
        for(String header:new String[]{"In-Reply-To","References","Bcc","Resent-Bcc"})check(wire.getHeader(header)==null);
        check(!wire.getContent().toString().contains("secret@example.invalid"));
        check(draft.mail.getString("to").isEmpty());
        System.out.println("PASS native text forward: exact original text/emoji, blank editable recipient, signature and subject prefix, no inherited Bcc/thread/attachment/internal metadata, persisted forward kind, limits and partial-body rejection, real plain-text MIME without reply headers; no network.");
    }
}
