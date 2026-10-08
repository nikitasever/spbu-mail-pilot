package ru.local.spbumailpilot;

import javax.mail.*;
import javax.mail.internet.*;
import java.util.*;

public class ReadFlagTest {
    static void check(boolean value){if(!value)throw new AssertionError();}
    static class MemoryStore extends Store {
        final MemoryFolder folder;
        MemoryStore(Session session)throws Exception{super(session,new URLName("test",null,-1,null,null,null));folder=new MemoryFolder(this,session);}
        public Folder getDefaultFolder(){return folder;}public Folder getFolder(String name){return folder;}public Folder getFolder(URLName url){return folder;}
    }
    static class MemoryFolder extends Folder implements UIDFolder {
        final MimeMessage message;boolean opened;final ArrayList<Integer> modes=new ArrayList<>();
        MemoryFolder(Store store,Session session)throws Exception{super(store);message=new MimeMessage(session);message.setFrom("from@example.invalid");message.setSubject("Test");message.setText("Informational message");message.saveChanges();}
        public String getName(){return "INBOX";}public String getFullName(){return "INBOX";}public Folder getParent(){return null;}public boolean exists(){return true;}public Folder[] list(String p){return new Folder[]{this};}public char getSeparator(){return '/';}public int getType(){return HOLDS_MESSAGES;}public boolean create(int t){return false;}public boolean hasNewMessages(){return false;}public Folder getFolder(String name){return this;}public boolean delete(boolean recurse){return false;}public boolean renameTo(Folder other){return false;}
        public void open(int value){mode=value;opened=true;modes.add(value);}public void close(boolean expunge){check(!expunge);opened=false;}public boolean isOpen(){return opened;}public Flags getPermanentFlags(){return new Flags(Flags.Flag.SEEN);}public int getMessageCount(){return 1;}public Message getMessage(int index){return message;}public void appendMessages(Message[] m){}public Message[] expunge(){return new Message[0];}
        public long getUIDValidity(){return 7;}public long getUIDNext(){return 43;}public Message getMessageByUID(long uid){return uid==42?message:null;}public Message[] getMessagesByUID(long start,long end){return new Message[]{message};}public Message[] getMessagesByUID(long[] uids){return new Message[]{message};}public long getUID(Message m){return 42;}
    }
    public static void main(String[] args)throws Exception{
        MemoryStore store=new MemoryStore(Session.getInstance(new Properties()));MailReader reader=new MailReader();java.lang.reflect.Field field=MailReader.class.getDeclaredField("store");field.setAccessible(true);field.set(reader,store);
        reader.body("INBOX","42","7");check(!store.folder.message.isSet(Flags.Flag.SEEN));check(store.folder.modes.get(0)==Folder.READ_ONLY);
        check(reader.seen("INBOX","42","7",true).getBoolean("seen"));check(store.folder.modes.get(1)==Folder.READ_WRITE);check(!store.folder.opened);
        check(!reader.seen("INBOX","42","7",false).getBoolean("seen"));
        try{reader.seen("INBOX","42","999",true);throw new AssertionError();}catch(MessagingException expected){}check(!store.folder.message.isSet(Flags.Flag.SEEN));check(!store.folder.opened);
        try{reader.seen("INBOX","999","7",true);throw new AssertionError();}catch(MessagingException expected){}check(!store.folder.message.isSet(Flags.Flag.SEEN));
        reader.body("INBOX","42","7");check(store.folder.modes.get(store.folder.modes.size()-1)==Folder.READ_ONLY);
        System.out.println("PASS: body remains read-only, explicit read/unread write, UIDVALIDITY/missing-message protection, no expunge and return to read-only mode.");
    }
}
