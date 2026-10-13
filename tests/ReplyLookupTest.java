package ru.local.spbumailpilot;
import javax.mail.*;import javax.mail.internet.*;import java.util.*;
public final class ReplyLookupTest {
 static void check(boolean v){if(!v)throw new AssertionError();}
 static final class FolderFixture extends ReadFlagTest.MemoryFolder {
  Message[] values=new Message[0];boolean fail;
  FolderFixture(Store store,Session session)throws Exception{super(store,session);}
  public Message[] search(javax.mail.search.SearchTerm term)throws MessagingException{if(fail)throw new MessagingException("Fixture network failure");ArrayList<Message> result=new ArrayList<>();for(Message m:values)if(term.match(m))result.add(m);return result.toArray(new Message[0]);}
  public void fetch(Message[] values,FetchProfile profile){}
 }
 static MimeMessage message(Session session,String from,String name,String refs)throws Exception{MimeMessage m=new MimeMessage(session);m.setFrom(from);m.setHeader(name,refs);m.setText("Not downloaded by lookup");m.saveChanges();return m;}
 public static void main(String[] args)throws Exception{
  Session session=Session.getInstance(new Properties());ReadFlagTest.MemoryStore store=new ReadFlagTest.MemoryStore(session);FolderFixture folder=new FolderFixture(store,session);MailReader reader=new MailReader();for(String name:new String[]{"store","inbox"}){java.lang.reflect.Field f=MailReader.class.getDeclaredField(name);f.setAccessible(true);f.set(reader,name.equals("store")?store:folder);}folder.open(Folder.READ_ONLY);
  String id="<source@example.invalid>",own="fixture@example.invalid";
  folder.values=new Message[]{message(session,own,"In-Reply-To",id)};check(!reader.hasReply(id,own));
  folder.values=new Message[]{message(session,"other@example.invalid","References","<parent@example.invalid>\r\n "+id)};check(reader.hasReply(id,own));check(!folder.values[0].isSet(Flags.Flag.SEEN));
  folder.values=new Message[]{message(session,"other@example.invalid","In-Reply-To","<SOURCE@example.invalid>")};check(!reader.hasReply(id,own));
  folder.values=new Message[]{message(session,"other@example.invalid","In-Reply-To","<source@example.invalid.extra>")};check(!reader.hasReply(id,own));folder.values=new Message[0];check(!reader.hasReply(id,own));
  folder.fail=true;try{reader.hasReply(id,own);throw new AssertionError();}catch(MessagingException expected){}check(folder.getMode()==Folder.READ_ONLY);
  System.out.println("PASS: actual MailReader reply lookup with JavaMail search terms, other-sender/exact-header checks, self-reply exclusion, empty results and network failure propagation without Seen writes; fixture only.");
 }
}
