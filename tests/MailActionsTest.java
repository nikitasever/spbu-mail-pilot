package ru.local.spbumailpilot;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import javax.mail.*;
import javax.mail.internet.*;

public class MailActionsTest {
    static void check(boolean value){if(!value)throw new AssertionError();}
    public static void main(String[] args)throws Exception{
        Properties tls=OutgoingMail.properties(false);check("true".equals(tls.getProperty("mail.smtp.starttls.required")));check("true".equals(tls.getProperty("mail.smtp.ssl.checkserveridentity")));
        check("true".equals(OutgoingMail.properties(true).getProperty("mail.smtps.ssl.checkserveridentity")));
        Session session=Session.getInstance(new Properties());
        MimeMessage message=OutgoingMail.build(session,"sender@example.invalid","first@example.invalid; second@example.invalid","copy@example.invalid","hidden@example.invalid","Проверка","Тестовый текст","<original@example.invalid>");
        check(message.getAllRecipients().length==4&&message.getSubject().equals("Проверка"));
        check(message.getHeader("In-Reply-To")[0].equals("<original@example.invalid>"));
        for(String address:new String[]{"","not-an-email","one@example.invalid\r\nBcc: injected@example.invalid","Group: a@example.invalid;"}){
            try{OutgoingMail.addresses(address,true);throw new AssertionError();}catch(IllegalArgumentException|AddressException expected){}
        }
        try{OutgoingMail.build(session,"sender@example.invalid","to@example.invalid","","","Subject\r\nBcc: injected@example.invalid","Body","");throw new AssertionError();}catch(IllegalArgumentException expected){}
        try(ServerSocket server=new ServerSocket(0,1,InetAddress.getLoopbackAddress())){
            final ArrayList<String> commands=new ArrayList<>();final StringBuilder wire=new StringBuilder();final Throwable[] failure={null};
            Thread mock=new Thread(()->{try(Socket socket=server.accept()){
                socket.setSoTimeout(10000);BufferedReader input=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.US_ASCII));PrintWriter output=new PrintWriter(new OutputStreamWriter(socket.getOutputStream(),StandardCharsets.US_ASCII),true);
                output.print("220 localhost test SMTP\r\n");output.flush();boolean data=false;String line;
                while((line=input.readLine())!=null){if(data){if(line.equals(".")){data=false;output.print("250 accepted for test\r\n");output.flush();}else wire.append(line).append("\r\n");continue;}commands.add(line);if(line.startsWith("EHLO"))output.print("250-localhost\r\n250 8BITMIME\r\n");else if(line.equals("DATA")){data=true;output.print("354 send test data\r\n");}else if(line.equals("QUIT")){output.print("221 bye\r\n");output.flush();break;}else output.print("250 OK\r\n");output.flush();}
            }catch(Throwable error){failure[0]=error;}},"local-smtp-test");mock.start();
            Session plain=Session.getInstance(new Properties());plain.setProvider(new Provider(Provider.Type.TRANSPORT,"smtp","com.sun.mail.smtp.SMTPTransport","Test","1"));
            try(Transport transport=plain.getTransport("smtp")){transport.connect("localhost",server.getLocalPort(),null,null);transport.sendMessage(message,message.getAllRecipients());}
            mock.join(10000);check(!mock.isAlive()&&failure[0]==null);
            check(commands.stream().filter(s->s.startsWith("RCPT TO:")).count()==4);check(!wire.toString().toLowerCase(Locale.ROOT).contains("\r\nbcc:"));
            MimeMessage received=new MimeMessage(session,new ByteArrayInputStream(wire.toString().getBytes(StandardCharsets.US_ASCII)));check(received.getSubject().equals("Проверка")&&received.getContent().toString().contains("Тестовый текст"));
        }
        check(PhoneNumbers.normalize("+7 (812) 326-49-56").equals("+78123264956"));
        for(String number:new String[]{"*123#","tel:+1234567","+7812;9999","+12\n3456","abc"})try{PhoneNumbers.normalize(number);throw new AssertionError();}catch(IllegalArgumentException expected){}
        check(PhoneNumbers.find("Связаться: +7 (812) 326-49-56. Приказ 25.08.2026").size()==1);
        File directory=Files.createTempDirectory("outgoing-test-").toFile();File attachment=new File(directory,"safe.pdf");Files.write(attachment.toPath(),new byte[]{1,2,3});check(MailSender.file(directory,"safe.pdf").equals(attachment.getCanonicalFile()));
        org.json.JSONObject draft=new org.json.JSONObject().put("to","to@example.invalid").put("subject","Вложение").put("body","Текст").put("attachments",new org.json.JSONArray().put(new org.json.JSONObject().put("token","safe.pdf").put("name","Документ.pdf")));
        MimeMessage attached=MailSender.message(session,new SavedAccounts.Login("mail.example.invalid",993,"sender@example.invalid","test-only-password"),draft,directory);
        ByteArrayOutputStream serialized=new ByteArrayOutputStream();attached.writeTo(serialized);
        MimeMessage parsed=new MimeMessage(session,new ByteArrayInputStream(serialized.toByteArray()));Multipart parts=(Multipart)parsed.getContent();check(parts.getCount()==2&&parts.getBodyPart(1).getFileName().equals("Документ.pdf"));
        check(Arrays.equals(parts.getBodyPart(1).getInputStream().readAllBytes(),new byte[]{1,2,3}));
        try{MailSender.file(directory,"../outside.pdf");throw new AssertionError();}catch(IllegalArgumentException expected){}
        check(attachment.delete()&&directory.delete());
        System.out.println("PASS: TLS requirements, address/header validation, local SMTP with UTF-8 and hidden Bcc, reply headers, phone validation and attachment isolation. No real messages or calls sent.");
    }
}
