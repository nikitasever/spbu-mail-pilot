package ru.local.spbumailpilot;

import javax.mail.*;
import javax.mail.internet.*;
import org.json.JSONObject;

final class MailSender {
    static final class Uncertain extends Exception {Uncertain(Exception cause){super(cause);}}
    static java.io.File file(java.io.File directory,String token)throws Exception{
        if(token==null||token.indexOf('/')>=0||token.indexOf('\\')>=0)throw new IllegalArgumentException("Invalid file token");
        java.io.File base=directory.getCanonicalFile(),file=new java.io.File(base,token).getCanonicalFile();
        if(!base.equals(file.getParentFile())||!file.isFile()||file.length()>AttachmentFiles.MAX_BYTES)throw new IllegalArgumentException("Attachment unavailable");return file;
    }
    static MimeMessage send(SavedAccounts.Login login,JSONObject draft,java.io.File directory)throws Exception{return send(login,draft,directory,null);}
    static MimeMessage send(SavedAccounts.Login login,JSONObject draft,java.io.File directory,TransferMessage.Progress progress)throws Exception{
        String protocol=login.smtpSsl?"smtps":"smtp";
        Session session=Session.getInstance(OutgoingMail.properties(login.smtpSsl));session.setDebug(false);
        session.setProvider(new Provider(Provider.Type.TRANSPORT,protocol,login.smtpSsl?"com.sun.mail.smtp.SMTPSSLTransport":"com.sun.mail.smtp.SMTPTransport","Eclipse","1.6.7"));
        MimeMessage message=message(session,login,draft,directory);
        TransferMessage transfer=(TransferMessage)message;transfer.prepare(progress,true);
        Transport transport=session.getTransport(protocol);
        try{
            transport.connect(login.smtpHost,login.smtpPort,login.user,login.password);
            try{transport.sendMessage(message,message.getAllRecipients());}catch(SendFailedException rejected){throw rejected;}catch(Exception unknown){throw new Uncertain(unknown);}
            try{transfer.finish();}catch(Exception ignored){transfer.detach();}return message;
        }finally{try{transport.close();}catch(Exception ignored){}}
    }
    static MimeMessage message(Session session,SavedAccounts.Login login,JSONObject draft,java.io.File directory)throws Exception{
        MimeMessage message=OutgoingMail.build(session,login.from,draft.optString("to"),draft.optString("cc"),draft.optString("bcc"),draft.optString("subject"),draft.optString("body"),draft.optString("inReplyTo"));
        attach(message,draft,directory);return message;
    }
    static TransferMessage draft(SavedAccounts.Login login,JSONObject draft,java.io.File directory)throws Exception{
        String subject=draft.optString("subject"),body=draft.optString("body"),reply=draft.optString("inReplyTo");
        if(subject.length()>500||subject.contains("\r")||subject.contains("\n")||body.length()>100000)throw new IllegalArgumentException("Invalid draft");
        if(!reply.isEmpty()&&(reply.length()>1000||!reply.matches("<[^<>\\s]+>")))throw new IllegalArgumentException("Invalid reply header");
        TransferMessage message=new TransferMessage(Session.getInstance(new java.util.Properties()));message.setFrom(new InternetAddress(login.from));message.setSubject(draft.optString("subject"),"UTF-8");message.setText(draft.optString("body"),"UTF-8");
        for(String field:new String[]{"to","cc","bcc"})try{message.setRecipients(field.equals("to")?Message.RecipientType.TO:field.equals("cc")?Message.RecipientType.CC:Message.RecipientType.BCC,OutgoingMail.addresses(draft.optString(field),false));}catch(Exception incomplete){}
        if(!reply.isEmpty()){message.setHeader("In-Reply-To",reply);message.setHeader("References",reply);}attach(message,draft,directory);message.saveChanges();return message;
    }
    static void attach(MimeMessage message,JSONObject draft,java.io.File directory)throws Exception{
        org.json.JSONArray files=draft.optJSONArray("attachments");
        if(files!=null&&files.length()>0){
            if(files.length()>10)throw new IllegalArgumentException("Too many attachments");
            MimeMultipart multipart=new MimeMultipart("mixed");MimeBodyPart body=new MimeBodyPart();body.setText(draft.optString("body"),"UTF-8");multipart.addBodyPart(body);
            for(int i=0;i<files.length();i++){JSONObject entry=files.getJSONObject(i);MimeBodyPart part=new MimeBodyPart();part.attachFile(file(directory,entry.getString("token")));part.setFileName(AttachmentFiles.safeName(entry.optString("name","Вложение")));part.setDisposition(Part.ATTACHMENT);multipart.addBodyPart(part);}
            message.setContent(multipart);message.saveChanges();
        }
    }
}
