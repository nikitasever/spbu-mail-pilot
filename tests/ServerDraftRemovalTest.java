package ru.local.spbumailpilot;

import javax.mail.*;
import javax.mail.internet.*;
import org.json.*;
import java.util.*;

public class ServerDraftRemovalTest {
    static void check(boolean value){if(!value)throw new AssertionError();}
    static final String KEY="11111111-1111-1111-1111-111111111111";
    static final String REV=String.join("",Collections.nCopies(64,"a"));
    static class DraftFolder extends ReadFlagTest.MemoryFolder {
        final Map<Long,Message> rows=new LinkedHashMap<>();
        DraftFolder(Store store,Session session)throws Exception{super(store,session);}
        public String getName(){return "Drafts";}public String getFullName(){return "Drafts";}
        public Message getMessageByUID(long uid){return rows.get(uid);}
        public long getUID(Message message){for(Map.Entry<Long,Message> row:rows.entrySet())if(row.getValue()==message)return row.getKey();return -1;}
        public Message[] expunge(){throw new AssertionError("Broad expunge forbidden");}
    }
    static class Account extends ReadFlagTest.MemoryStore {
        final DraftFolder drafts;
        Account(Session session)throws Exception{super(session);drafts=new DraftFolder(this,session);}
        public Folder getDefaultFolder(){return drafts;}public Folder getFolder(String name){return drafts;}
    }
    static class Scoped implements ServerDrafts.ScopedExpunge {
        final DraftFolder folder; boolean enabled=true,lostAck; final List<Long> deleted=new ArrayList<>();
        Scoped(DraftFolder folder){this.folder=folder;}
        public boolean supported(){return enabled;}
        public void remove(Message message)throws Exception{
            long uid=folder.getUID(message);check(uid==42);check(message.isSet(Flags.Flag.DELETED));
            deleted.add(uid);folder.rows.remove(uid);
            if(lostAck){lostAck=false;throw new MessagingException("ACK lost");}
        }
    }
    static JSONObject ref()throws Exception{return new JSONObject().put("folder","Drafts").put("uid","42").put("validity","7").put("key",KEY).put("revision",REV);}
    static Message draft(Session session,String key)throws Exception{
        MimeMessage message=new MimeMessage(session);message.setText("local test");message.setHeader(ServerDrafts.KEY,key);message.setHeader(ServerDrafts.REV,REV);message.setFlag(Flags.Flag.DRAFT,true);return message;
    }
    static void refused(String reason,DraftFolder folder,JSONObject ref,Scoped scoped)throws Exception{
        try{ServerDrafts.removeKnown(folder,ref,KEY,scoped);throw new AssertionError("Expected refusal");}
        catch(ServerDrafts.DeleteRefused expected){check(reason.equals(expected.reason));}
        check(!folder.opened);
    }
    public static void main(String[] args)throws Exception{
        Session session=Session.getInstance(new Properties());Account account=new Account(session);DraftFolder folder=account.drafts;Scoped scoped=new Scoped(folder);
        Message target=draft(session,KEY),unrelated=draft(session,"22222222-2222-2222-2222-222222222222");
        unrelated.setFlag(Flags.Flag.DELETED,true);folder.rows.put(42L,target);folder.rows.put(99L,unrelated);
        scoped.enabled=false;refused("uidplus",folder,ref(),scoped);check(!target.isSet(Flags.Flag.DELETED));
        // Real adapter refuses providers without scoped UIDPLUS expunge as well.
        try{ServerDrafts.removeKnown(account,ref(),KEY);throw new AssertionError();}catch(ServerDrafts.DeleteRefused expected){check(expected.reason.equals("uidplus"));}
        check(!target.isSet(Flags.Flag.DELETED));check(!folder.opened);
        scoped.enabled=true;
        refused("validity",folder,ref().put("validity","8"),scoped);check(!target.isSet(Flags.Flag.DELETED));
        refused("folder",folder,ref().put("folder","INBOX"),scoped);
        refused("ownership",folder,ref().put("key","22222222-2222-2222-2222-222222222222"),scoped);
        refused("ownership",folder,ref().put("revision",String.join("",Collections.nCopies(64,"b"))),scoped);
        target.setFlag(Flags.Flag.DRAFT,false);refused("ownership",folder,ref(),scoped);target.setFlag(Flags.Flag.DRAFT,true);
        JSONObject result=ServerDrafts.removeKnown(folder,ref(),KEY,scoped);
        check(result.getBoolean("deleted"));check(scoped.deleted.equals(Arrays.asList(42L)));check(folder.rows.get(99L)==unrelated);check(!folder.opened);
        result=ServerDrafts.removeKnown(folder,ref(),KEY,scoped);check(result.getBoolean("alreadyMissing"));check(scoped.deleted.size()==1);
        target=draft(session,KEY);target.setFlag(Flags.Flag.DELETED,true);folder.rows.put(42L,target);scoped.lostAck=true;
        try{ServerDrafts.removeKnown(folder,ref(),KEY,scoped);throw new AssertionError();}catch(MessagingException expected){}
        check(!folder.opened);check(folder.rows.get(99L)==unrelated);
        result=ServerDrafts.removeKnown(folder,ref(),KEY,scoped);check(result.getBoolean("alreadyMissing"));check(scoped.deleted.size()==2);
        check(!unrelated.isExpunged());
        System.out.println("PASS: exact owned UID removal, UIDVALIDITY/key/revision/DRAFT checks, no UIDPLUS no mutation, no broad expunge, missing/lost-ACK retry and unrelated deleted message preserved.");
    }
}
