package ru.local.spbumailpilot;
import javax.mail.*;
import javax.mail.internet.*;
import org.json.*;
import java.util.*;
import java.io.*;

public class ServerDraftsTest {
    static void check(boolean value){if(!value)throw new AssertionError();}
    static class DraftFolder extends ReadFlagTest.MemoryFolder {
        final Session session;final List<MimeMessage> rows=new ArrayList<>();boolean lostAck;
        DraftFolder(Store store,Session session)throws Exception{super(store,session);this.session=session;}
        public String getName(){return "Черновики";}public String getFullName(){return getName();}
        public int getMessageCount(){return rows.size();}public Message getMessage(int number){return rows.get(number-1);}
        public long getUID(Message message){return rows.indexOf(message)+1;}
        public void appendMessages(Message[] messages)throws MessagingException{try{for(Message m:messages){ByteArrayOutputStream out=new ByteArrayOutputStream();m.writeTo(out);MimeMessage copy=new MimeMessage(session,new ByteArrayInputStream(out.toByteArray()));copy.setFlags(m.getFlags(),true);rows.add(copy);}if(lostAck){lostAck=false;throw new MessagingException("ACK lost");}}catch(IOException e){throw new MessagingException("Write failed",e);}}
    }
    static class Account extends ReadFlagTest.MemoryStore {
        final DraftFolder drafts;
        Account(Session session)throws Exception{super(session);drafts=new DraftFolder(this,session);}
        public Folder getDefaultFolder(){return drafts;}public Folder getFolder(String name){return drafts;}
    }
    static TransferMessage message(Session session,String body)throws Exception{TransferMessage m=new TransferMessage(session);m.setFrom("test@example.invalid");m.setText(body,"UTF-8");m.saveChanges();return m;}
    public static void main(String[] args)throws Exception{
        Session session=Session.getInstance(new Properties());Account store=new Account(session);String key=UUID.randomUUID().toString(),other=UUID.randomUUID().toString();
        JSONObject draft=new JSONObject().put("subject","Тест").put("body","Первый текст");String first=ServerDrafts.revision(draft);List<String> stages=new ArrayList<>();
        ServerDrafts.save(store,message(session,"Первый текст"),key,first,(stage,bytes,total)->stages.add(stage));check(store.drafts.rows.size()==1);check(stages.contains("uploading")&&stages.contains("confirming")&&!stages.contains("complete"));check(store.drafts.rows.get(0).isSet(Flags.Flag.DRAFT));
        ServerDrafts.save(store,message(session,"Первый текст"),key,first,null);check(store.drafts.rows.size()==1);
        MimeMessage unrelated=message(session,"Other");unrelated.setHeader(ServerDrafts.KEY,other);unrelated.setFlag(Flags.Flag.DRAFT,true);store.drafts.rows.add(unrelated);
        draft.put("body","Изменённый текст");String second=ServerDrafts.revision(draft);store.drafts.lostAck=true;
        try{ServerDrafts.save(store,message(session,"Изменённый текст"),key,second,null);throw new AssertionError();}catch(MessagingException expected){}
        check(!store.drafts.rows.get(0).isSet(Flags.Flag.DELETED));
        JSONObject result=ServerDrafts.save(store,message(session,"Изменённый текст"),key,second,null);check(result.getString("uid").equals("3"));check(store.drafts.rows.size()==3);check(store.drafts.rows.get(0).isSet(Flags.Flag.DELETED));check(!unrelated.isSet(Flags.Flag.DELETED));
        ServerDrafts.remove(store,key);check(store.drafts.rows.get(2).isSet(Flags.Flag.DELETED));check(!unrelated.isSet(Flags.Flag.DELETED));check(!store.drafts.opened);
        draft.put("serverDraft",result);check(second.equals(ServerDrafts.revision(draft)));
        System.out.println("PASS: immediate server drafts, real streaming, retry after lost ACK creates no duplicate, revisions replace only own draft, unrelated drafts preserved, no global expunge. No real mail used.");
    }
}
