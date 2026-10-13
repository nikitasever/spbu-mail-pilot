package ru.local.spbumailpilot;
import javax.mail.*;
import javax.mail.internet.*;
import com.sun.mail.imap.*;
import java.util.*;
public class MailMoveTest{
 static void check(boolean v){if(!v)throw new AssertionError();}
 static final class Memory extends IMAPStore{
  boolean capability=true,fail=false,changed=false;int moves=0;final Box inbox,archive,trash;
  Memory(Session s)throws Exception{super(s,new URLName("imap",null,-1,null,null,null));inbox=new Box(this,"INBOX",null);archive=new Box(this,"Archive","\\Archive");trash=new Box(this,"Trash","\\Trash");}
  public boolean hasCapability(String name){check(name.equals("MOVE"));return capability;}
  public Folder getFolder(String name){return name.equals("INBOX")?inbox:name.equals("Trash")?trash:archive;}
  public Folder getDefaultFolder(){return inbox;}
 }
 static final class Box extends IMAPFolder{
  final Memory memory;final Message mail;boolean opened;final String attr;
  Box(Memory store,String name,String attr)throws Exception{super(name,'/',store,Boolean.FALSE);memory=store;this.attr=attr;mail=new MimeMessage(Session.getInstance(new Properties()));}
  public boolean exists(){return true;}public int getType(){return HOLDS_MESSAGES;}public String[] getAttributes(){return attr==null?new String[0]:new String[]{attr};}public Folder[] list(String pattern){return new Folder[]{memory.inbox,memory.archive,memory.trash};}
  public boolean isOpen(){return opened;}public void open(int m){mode=m;opened=true;}public void close(boolean expunge){check(!expunge);opened=false;}public long getUIDValidity(){return memory.changed&&mode==READ_WRITE?8:7;}
  public Message getMessageByUID(long uid){return uid==42?mail:null;}
  public void moveMessages(Message[] messages,Folder destination)throws MessagingException{check(mode==READ_WRITE&&messages.length==1&&messages[0]==mail&&(destination==memory.archive||destination==memory.trash));memory.moves++;if(memory.fail)throw new MessagingException("lost ACK");}
 }
 public static void main(String[] args)throws Exception{Memory memory=new Memory(Session.getInstance(new Properties()));MailReader reader=new MailReader();java.lang.reflect.Field field=MailReader.class.getDeclaredField("store");field.setAccessible(true);field.set(reader,memory);check(reader.moveMail("INBOX","42","7","archive").getBoolean("moved"));check(memory.moves==1&&memory.inbox.getMode()==Folder.READ_ONLY);
  for(String[] invalid:new String[][]{{"999","7","trash"},{"42","8","trash"},{"42","7","delete"}}){try{reader.moveMail("INBOX",invalid[0],invalid[1],invalid[2]);throw new AssertionError();}catch(Exception refused){check(!(refused instanceof RuntimeException));}check(memory.moves==1);}
  memory.capability=false;try{reader.moveMail("INBOX","42","7","trash");throw new AssertionError();}catch(java.io.IOException expected){}check(memory.moves==1);memory.capability=true;memory.changed=true;try{reader.moveMail("INBOX","42","7","trash");throw new AssertionError();}catch(java.io.IOException expected){}check(memory.moves==1&&memory.inbox.getMode()==Folder.READ_ONLY);memory.changed=false;memory.fail=true;try{reader.moveMail("INBOX","42","7","trash");throw new AssertionError();}catch(MessagingException expected){}check(memory.moves==2&&memory.inbox.getMode()==Folder.READ_ONLY);
  System.out.println("PASS: atomic MOVE only, exact UID/UIDVALIDITY/target/capability guards, read-only restoration and no retry after lost acknowledgement; no real mailbox.");}
}
