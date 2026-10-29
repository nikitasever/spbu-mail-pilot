package ru.local.spbumailpilot;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.json.*;
import javax.mail.*;
import javax.mail.internet.*;

public final class DesktopOutgoingTest {
    static void check(boolean value){if(!value)throw new AssertionError();}
    static class Delivery implements DesktopOutgoing.Delivery {
        int calls,copies,transmitted;String mode="ok";
        public MimeMessage send(SavedAccounts.Login login,JSONObject draft,File files,TransferMessage.Progress progress,MailSender.BeforeSend before)throws Exception{
            calls++;if(mode.equals("auth"))throw new AuthenticationFailedException("fixture-password");
            TransferMessage message=(TransferMessage)MailSender.message(Session.getInstance(new Properties()),login,draft,files);message.prepare(progress,true);before.run();
            transmitted++;if(mode.equals("unknown"))throw new IOException("fixture-private-body");
            ByteArrayOutputStream wire=new ByteArrayOutputStream();message.writeTo(wire,new String[]{"Bcc","Content-Length"});
            String mime=wire.toString("UTF-8");check(!mime.contains("Bcc:"));check(mime.contains("Message-ID: <schedule-"));message.finish();return message;
        }
        public void copy(SavedAccounts.Login login,MimeMessage message)throws Exception{copies++;if(mode.equals("copy-failure"))throw new IOException("Sent unavailable");}
    }
    static JSONObject args(Path directory,String attempt){return new JSONObject().put("attemptId",attempt).put("receipts",directory.resolve("receipts").toString()).put("directory",directory.toString()).put("smtpHost","smtp.fixture.invalid").put("smtpPort",465).put("smtpSsl",true).put("from","sender@fixture.invalid").put("to","to@fixture.invalid").put("cc","").put("bcc","hidden@fixture.invalid").put("subject","Тема").put("body","fixture-private-body").put("inReplyTo","<parent@fixture.invalid>").put("references",new JSONArray().put("<root@fixture.invalid>")).put("attachments",new JSONArray());}
    public static void main(String[] unused)throws Exception{
        Path directory=Files.createTempDirectory("outgoing-fixture-");SavedAccounts.Login auth=new SavedAccounts.Login("fixture.invalid",993,"sender@fixture.invalid","fixture-password");
        try{
            Delivery delivery=new Delivery();DesktopOutgoing sender=new DesktopOutgoing(delivery);String first=UUID.randomUUID().toString();JSONObject args=args(directory,first);
            Files.writeString(directory.resolve("snapshot.bin"),"attachment snapshot");args.getJSONArray("attachments").put(new JSONObject().put("token","snapshot.bin").put("name","Вложение.txt"));
            JSONObject sent=sender.send(auth,args,(s,b,t)->{},()->{});check(sent.getBoolean("accepted")&&sent.getBoolean("copySaved"));
            check(Files.readString(directory.resolve("receipts/"+first+".state")).equals("sent"));
            check(sender.send(auth,args,(s,b,t)->{},()->{}).getBoolean("replayed"));check(delivery.calls==1&&delivery.copies==1&&delivery.transmitted==1);
            delivery.mode="copy-failure";JSONObject copy=sender.send(auth,args(directory,UUID.randomUUID().toString()),(s,b,t)->{},()->{});check(copy.getBoolean("accepted")&&!copy.getBoolean("copySaved"));
            delivery.mode="unknown";String ambiguous=UUID.randomUUID().toString();JSONObject unknown=args(directory,ambiguous);
            try{sender.send(auth,unknown,(s,b,t)->{},()->{});throw new AssertionError();}catch(DesktopOutgoing.Fault fault){check(fault.code.equals("SEND_UNKNOWN"));}
            int calls=delivery.calls;try{sender.send(auth,unknown,(s,b,t)->{},()->{});throw new AssertionError();}catch(DesktopOutgoing.Fault fault){check(fault.code.equals("SEND_UNKNOWN"));}check(delivery.calls==calls);
            // A fresh engine also blocks the persisted ambiguous receipt without touching SMTP.
            Delivery restarted=new Delivery();try{new DesktopOutgoing(restarted).send(auth,unknown,(s,b,t)->{},()->{});throw new AssertionError();}catch(DesktopOutgoing.Fault fault){check(fault.code.equals("SEND_UNKNOWN"));}check(restarted.calls==0);
            delivery.mode="auth";String safe=UUID.randomUUID().toString();JSONObject retry=args(directory,safe);
            try{sender.send(auth,retry,(s,b,t)->{},()->{});throw new AssertionError();}catch(AuthenticationFailedException expected){}
            check(!Files.exists(directory.resolve("receipts/"+safe+".state")));delivery.mode="ok";check(sender.send(auth,retry,(s,b,t)->{},()->{}).getBoolean("accepted"));
            calls=delivery.calls;try{sender.send(auth,args(directory,UUID.randomUUID().toString()).put("to","bad\r\nBcc: attack@fixture.invalid"),(s,b,t)->{},()->{});throw new AssertionError();}catch(DesktopOutgoing.Fault expected){check(expected.code.equals("BAD_TO"));}check(delivery.calls==calls);
            check(!Files.readString(directory.resolve("receipts/"+ambiguous+".state")).contains("fixture"));
        }finally{try(java.util.stream.Stream<Path> files=Files.walk(directory)){for(Path path:files.sorted(Comparator.reverseOrder()).toArray(Path[]::new))Files.delete(path);}}
        System.out.println("PASS outgoing: MIME attachments/reply/Bcc, durable no-repeat receipts, ambiguity across restart, auth retry before transmission and Sent-copy failure isolation (fixture transport).");
    }
}
