package ru.local.spbumailpilot;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import javax.crypto.*;
import javax.mail.*;
import javax.mail.internet.*;
import org.json.*;
public final class ScheduledPolicyTest {
 static void check(boolean ok){if(!ok)throw new AssertionError();}
 static final class FakeTransport extends Transport {
  int calls;boolean fail;
  FakeTransport(Session session){super(session,new URLName("smtp",null,-1,null,null,null));}
  public void sendMessage(Message m,Address[] recipients)throws MessagingException{calls++;if(fail)throw new MessagingException("Lost SMTP ACK");}
 }
 public static void main(String[] args)throws Exception{
  check(!ScheduledPolicy.ready("pending",200,199)&&ScheduledPolicy.ready("pending",200,200));
  for(String phase:new String[]{"preparing","transmitting","sent","uncertain","failed","cancelled"})check(!ScheduledPolicy.ready(phase,0,200));
  check(ScheduledPolicy.recover("preparing").equals("pending"));check(ScheduledPolicy.recover("transmitting").equals("uncertain"));
  check(ScheduledPolicy.failure("preparing",new IOException(),1).equals("pending"));check(ScheduledPolicy.failure("preparing",new IOException(),3).equals("failed"));
  check(ScheduledPolicy.failure("preparing",new AuthenticationFailedException(),1).equals("failed"));check(ScheduledPolicy.failure("preparing",new IllegalArgumentException(),1).equals("failed"));
  check(ScheduledPolicy.failure("transmitting",new IOException(),1).equals("uncertain"));check(!ScheduledPolicy.cancellable("transmitting")&&ScheduledPolicy.cancellable("preparing")&&ScheduledPolicy.cancellable("uncertain"));
  check(ScheduledPolicy.retryAt(100,1)==300100&&ScheduledPolicy.retryAt(100,2)==600100);
  File dir=Files.createTempDirectory("scheduled-test-").toFile();Session session=Session.getInstance(new Properties());
  JSONObject draft=new JSONObject().put("id","draft-1").put("to","to@example.invalid").put("subject","По расписанию").put("body","Тест").put("scheduledMessageId","<schedule-12345678-1234-1234-1234-123456789abc@mail-pilot.local>");
  SavedAccounts.Login login=new SavedAccounts.Login("mail.example.invalid",993,"from@example.invalid","fixture-password");
  MimeMessage message=MailSender.message(session,login,draft,dir);check(message.getMessageID().equals(draft.getString("scheduledMessageId")));
  ((TransferMessage)message).prepare(null,true);ByteArrayOutputStream wire=new ByteArrayOutputStream();message.writeTo(wire);check(wire.toString("UTF-8").contains("Message-ID: "+draft.getString("scheduledMessageId")));
  FakeTransport transport=new FakeTransport(session);String[] durable={"preparing"};
  try{MailSender.transmit(transport,message,()->{throw new IOException("Disk full before durable DATA claim");});throw new AssertionError();}catch(IOException expected){}check(transport.calls==0);
  durable[0]="cancelled";try{MailSender.transmit(transport,message,()->{if(!durable[0].equals("preparing"))throw new IOException("Cancelled");});throw new AssertionError();}catch(IOException expected){}check(transport.calls==0);
  durable[0]="preparing";MailSender.transmit(transport,message,()->{durable[0]="transmitting";});durable[0]="sent";check(transport.calls==1&&!ScheduledPolicy.ready(durable[0],0,100));
  transport.fail=true;durable[0]="preparing";try{MailSender.transmit(transport,message,()->{durable[0]="transmitting";});throw new AssertionError();}catch(MailSender.Uncertain expected){durable[0]=ScheduledPolicy.failure(durable[0],expected,1);}check(durable[0].equals("uncertain")&&transport.calls==2);check(!ScheduledPolicy.ready(ScheduledPolicy.recover(durable[0]),0,100));
  KeyGenerator generator=KeyGenerator.getInstance("AES");generator.init(256);SecretKey key=generator.generateKey();byte[] plain=draft.toString().getBytes("UTF-8"),encoded=OfflineMailStore.crypt(true,key,"scheduled/account-a/id-a",plain);
  check(Arrays.equals(plain,OfflineMailStore.crypt(false,key,"scheduled/account-a/id-a",encoded)));for(String wrong:new String[]{"scheduled/account-b/id-a","scheduled/account-a/id-b"})try{OfflineMailStore.crypt(false,key,wrong,encoded);throw new AssertionError();}catch(javax.crypto.AEADBadTagException expected){}
  check(dir.delete());System.out.println("PASS: scheduled durable pre-DATA gate/cancel, ACK and lost-ACK policy, process recovery, bounded connect retries, stable Message-ID and encrypted account/item isolation. No real messages sent.");
 }
}
