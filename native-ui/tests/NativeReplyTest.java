package ru.local.spbumailpilot;

import org.json.*;
import javax.mail.*;
import javax.mail.internet.*;
import java.util.*;

public final class NativeReplyTest {
    interface Check{void run()throws Exception;}
    static void check(boolean value){if(!value)throw new AssertionError();}
    static void reject(Check action)throws Exception{try{action.run();}catch(NativeReply.Invalid expected){return;}throw new AssertionError("Expected reply rejection");}
    static JSONArray list(String... values){return new JSONArray(Arrays.asList(values));}
    static JSONObject source()throws Exception{return new JSONObject().put("email","sender@example.invalid").put("replyTo","team@example.invalid")
        .put("replyToRecipients",list("team@example.invalid","second@example.invalid"))
        .put("toRecipients",list("own@example.invalid","other@example.invalid","TEAM@example.invalid"))
        .put("ccRecipients",list("copy@example.invalid","OTHER@example.invalid","login@example.invalid"))
        .put("bccRecipients",list("secret@example.invalid"))
        .put("subject","Тема").put("messageId","<parent@example.invalid>").put("references",list("<root@example.invalid>"));}
    public static void main(String[] args)throws Exception{
        JSONObject source=source();String before=source.toString();
        NativeReply.Targets targets=NativeReply.all(source,"Сотрудник <own@example.invalid>","login@example.invalid");
        check(targets.to.equals("team@example.invalid, second@example.invalid, other@example.invalid"));
        check(targets.cc.equals("copy@example.invalid"));check(source.toString().equals(before));
        String owner="account-"+new String(new char[64]).replace('\0','a');
        NativeDraft draft=NativeDraft.replyAll(owner,source,"Подпись","own@example.invalid","login@example.invalid");
        check(draft.mail.getString("bcc").isEmpty()&&draft.mail.getJSONArray("attachments").length()==0);
        check(draft.mail.getString("subject").equals("Re: Тема")&&draft.mail.getString("body").equals("Подпись"));
        NativeDraft restored=NativeDraft.parse(owner,draft.json().toString());
        check(restored.mail.getString("cc").equals("copy@example.invalid"));
        SavedAccounts.Login login=new SavedAccounts.Login("example.invalid",993,"own@example.invalid","fixture");
        MimeMessage message=MailSender.message(Session.getInstance(new Properties()),login,draft.mail,new java.io.File("unused"));
        check(message.getRecipients(Message.RecipientType.TO).length==3&&message.getRecipients(Message.RecipientType.CC).length==1);
        check(message.getRecipients(Message.RecipientType.BCC)==null&&message.getAllRecipients().length==4);
        check(message.getHeader("In-Reply-To")[0].equals("<parent@example.invalid>"));
        check(message.getHeader("References")[0].contains("<root@example.invalid>")&&message.getHeader("References")[0].contains("<parent@example.invalid>"));
        check(NativeDraft.create(owner,source,"").mail.getString("to").equals("team@example.invalid"));
        JSONObject self=source().put("replyToRecipients",list("OWN@example.invalid")).put("toRecipients",list("own@example.invalid"))
            .put("ccRecipients",list("copy@example.invalid","second@example.invalid"));
        targets=NativeReply.all(self,"own@example.invalid","local-login");
        check(targets.to.equals("copy@example.invalid")&&targets.cc.equals("second@example.invalid"));
        self.put("ccRecipients",list("OWN@example.invalid"));reject(()->NativeReply.all(self,"own@example.invalid","local-login"));
        JSONObject noReply=source().put("replyToRecipients",list());
        check(NativeReply.all(noReply,"own@example.invalid","login@example.invalid").to.startsWith("sender@example.invalid"));
        for(String field:new String[]{"toRecipients","ccRecipients","replyToRecipients"}) {
            JSONObject missing=source();missing.remove(field);reject(()->NativeReply.all(missing,"own@example.invalid","local"));
            JSONObject invalid=source().put(field,list("bad\r\nBcc: secret@example.invalid"));reject(()->NativeReply.all(invalid,"own@example.invalid","local"));
            JSONObject group=source().put(field,list("Team: person@example.invalid;"));reject(()->NativeReply.all(group,"own@example.invalid","local"));
            JSONObject wrong=source().put(field,new JSONArray().put(42));reject(()->NativeReply.all(wrong,"own@example.invalid","local"));
        }
        JSONArray many=new JSONArray();for(int i=0;i<51;i++)many.put("person"+i+"@example.invalid");
        JSONObject huge=source().put("toRecipients",many);reject(()->NativeReply.all(huge,"own@example.invalid","local"));
        // Exercise the actual reader envelope and BODY.PEEK against an in-memory folder.
        ReadFlagTest.MemoryStore store=new ReadFlagTest.MemoryStore(Session.getInstance(new Properties()));
        store.folder.message.setReplyTo(InternetAddress.parse("first@example.invalid, second@example.invalid"));
        store.folder.message.setRecipients(Message.RecipientType.TO,"own@example.invalid, recipient@example.invalid");
        store.folder.message.setRecipients(Message.RecipientType.CC,"copy@example.invalid");
        store.folder.message.setRecipients(Message.RecipientType.BCC,"secret@example.invalid");
        MailReader reader=new MailReader();java.lang.reflect.Field field=MailReader.class.getDeclaredField("store");field.setAccessible(true);field.set(reader,store);
        JSONObject header=reader.body("INBOX","42","7").getJSONObject("message");
        check(header.getJSONArray("replyToRecipients").length()==2&&!store.folder.message.isSet(Flags.Flag.SEEN));
        targets=NativeReply.all(header,"own@example.invalid","local");
        check(targets.to.equals("first@example.invalid, second@example.invalid, recipient@example.invalid")&&targets.cc.equals("copy@example.invalid"));
        reader.close();
        System.out.println("PASS native reply-all: full Reply-To/To/Cc, own-address and case-insensitive duplicate removal, no Bcc or attachment copying, reviewable MIME/thread headers, durable draft round trip, missing/invalid headers and bounds rejected, real reader fixture remains unseen; no network.");
    }
}
