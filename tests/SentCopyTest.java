package ru.local.spbumailpilot;
import javax.mail.*;
import javax.mail.internet.*;
import java.util.*;

public final class SentCopyTest {
    static class Book extends ReadFlagTest.MemoryFolder {
        final String name;final boolean exists;Message[] appended;
        Book(Store store,Session session,String name,boolean exists)throws Exception{super(store,session);this.name=name;this.exists=exists;}
        public String getName(){return name;}public String getFullName(){return name;}public boolean exists(){return exists;}
        public void appendMessages(Message[] messages){appended=messages;}
    }
    static class Account extends ReadFlagTest.MemoryStore {
        final Book sent,outgoing,missing;final Folder root;
        Account(Session session)throws Exception{super(session);sent=new Book(this,session,"Отправленное",true);outgoing=new Book(this,session,"Outgoing",true);missing=new Book(this,session,"missing",false);root=new Book(this,session,"",true){public Folder[] list(String pattern){return new Folder[]{outgoing,sent};}};}
        public Folder getDefaultFolder(){return root;}public Folder getFolder(String name){return name.equals("Отправленное")?sent:name.equals("Outgoing")?outgoing:missing;}
    }
    public static void main(String[] args)throws Exception{
        Session session=Session.getInstance(new Properties());Account account=new Account(session);MailReader reader=new MailReader();java.lang.reflect.Field field=MailReader.class.getDeclaredField("store");field.setAccessible(true);field.set(reader,account);
        MimeMessage message=new MimeMessage(session);message.setFrom("fixture@example.invalid");message.setRecipients(Message.RecipientType.TO,"other@example.invalid");message.setText("Already delivered");message.saveChanges();String id=message.getMessageID();
        reader.saveSent(message);
        if(account.sent.appended==null||account.sent.appended.length!=1||account.sent.appended[0]!=message||account.outgoing.appended!=null||!message.isSet(Flags.Flag.SEEN)||!id.equals(message.getMessageID()))throw new AssertionError();
        System.out.println("PASS: delivered message copied once to Russian singular Sent folder, Outgoing untouched, original Message-ID preserved. No SMTP or real mail used.");
    }
}
