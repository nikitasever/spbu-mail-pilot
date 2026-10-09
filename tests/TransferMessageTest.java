package ru.local.spbumailpilot;
import javax.mail.*;
import java.io.*;
import java.util.*;

public class TransferMessageTest {
    static void check(boolean value){if(!value)throw new AssertionError();}
    public static void main(String[] args)throws Exception{
        TransferMessage message=new TransferMessage(Session.getInstance(new Properties()));message.setFrom("from@example.invalid");message.setRecipients(Message.RecipientType.TO,"to@example.invalid");message.setRecipients(Message.RecipientType.BCC,"hidden@example.invalid");message.setText("Большой текст "+String.join("",Collections.nCopies(10000,"abcdef")),"UTF-8");message.saveChanges();
        List<String> stages=new ArrayList<>();long[] last={0},total={0};message.prepare((stage,bytes,size)->{check(size>0);total[0]=size;if(stage.equals("uploading")){check(bytes>=last[0]&&bytes<=size);last[0]=bytes;}stages.add(stage);},true);
        check(stages.equals(Collections.singletonList("connecting")));
        ByteArrayOutputStream out=new ByteArrayOutputStream();message.writeTo(out,new String[]{"Bcc","Content-Length"});check(out.size()==total[0]);check(!out.toString("UTF-8").contains("Bcc:"));check(stages.get(stages.size()-1).equals("confirming"));check(!stages.contains("complete"));message.finish();check(stages.get(stages.size()-1).equals("complete"));
        Class<?> type=Class.forName("com.sun.mail.imap.LengthCounter");java.lang.reflect.Constructor<?> ctor=type.getDeclaredConstructor(int.class);ctor.setAccessible(true);OutputStream counter=(OutputStream)ctor.newInstance(0);stages.clear();message.prepare((stage,bytes,size)->stages.add(stage),false);message.writeTo(counter);check(stages.equals(Collections.singletonList("connecting")));message.detach();
        message.prepare((stage,bytes,size)->{if(stage.equals("uploading"))throw new IOException("Cancelled");},false);try{message.writeTo(new ByteArrayOutputStream());throw new AssertionError();}catch(IOException expected){}
        System.out.println("PASS: measured MIME bytes match transfer total, Bcc omitted from SMTP, sizing pass emits no upload, 100% requires acknowledgement, cancellation stops transfer.");
    }
}
