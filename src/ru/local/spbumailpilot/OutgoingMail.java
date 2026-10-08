package ru.local.spbumailpilot;

import javax.mail.*;
import javax.mail.internet.*;
import java.util.*;

final class OutgoingMail {
    static InternetAddress[] addresses(String value,boolean required)throws Exception{
        if(value==null)value="";
        if(value.length()>4000||value.indexOf('\r')>=0||value.indexOf('\n')>=0)throw new IllegalArgumentException("Invalid addresses");
        InternetAddress[] result=InternetAddress.parse(value.replace(';',','),true);
        if(required&&result.length==0)throw new IllegalArgumentException("Recipient required");
        if(result.length>50)throw new IllegalArgumentException("Too many recipients");
        for(InternetAddress address:result){address.validate();if(address.isGroup()||!address.getAddress().contains("@"))throw new IllegalArgumentException("Full email address required");}
        return result;
    }
    static MimeMessage build(Session session,String from,String to,String cc,String bcc,String subject,String body,String inReplyTo)throws Exception{
        InternetAddress[] sender=addresses(from,true);if(sender.length!=1)throw new IllegalArgumentException("One sender required");
        InternetAddress[] recipients=addresses(to,true),copies=addresses(cc,false),hidden=addresses(bcc,false);
        if(recipients.length+copies.length+hidden.length>50)throw new IllegalArgumentException("Too many recipients");
        if(subject==null||subject.length()>500||subject.indexOf('\r')>=0||subject.indexOf('\n')>=0)throw new IllegalArgumentException("Invalid subject");
        if(body==null||body.length()>100000)throw new IllegalArgumentException("Body too large");
        MimeMessage message=new MimeMessage(session);message.setFrom(sender[0]);message.setRecipients(Message.RecipientType.TO,recipients);message.setRecipients(Message.RecipientType.CC,copies);message.setRecipients(Message.RecipientType.BCC,hidden);
        message.setSubject(subject,"UTF-8");message.setText(body,"UTF-8");message.setSentDate(new Date());
        if(inReplyTo!=null&&!inReplyTo.isEmpty()){
            if(inReplyTo.length()>1000||!inReplyTo.matches("<[^<>\\s]+>"))throw new IllegalArgumentException("Invalid reply header");
            message.setHeader("In-Reply-To",inReplyTo);message.setHeader("References",inReplyTo);
        }
        message.saveChanges();return message;
    }
    static Properties properties(boolean ssl){
        Properties props=new Properties();String prefix=ssl?"mail.smtps.":"mail.smtp.";
        props.setProperty(prefix+"auth","true");props.setProperty(prefix+"ssl.checkserveridentity","true");
        props.setProperty(prefix+"connectiontimeout","15000");props.setProperty(prefix+"timeout","30000");props.setProperty(prefix+"writetimeout","30000");
        props.setProperty(prefix+"sendpartial","false");
        if(!ssl){props.setProperty(prefix+"starttls.enable","true");props.setProperty(prefix+"starttls.required","true");}
        return props;
    }
}
