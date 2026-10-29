package ru.local.spbumailpilot;

import org.json.*;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import javax.mail.*;
import javax.mail.internet.*;

/** SMTP and a content-free durable attempt receipt. An ambiguous delivery is never retried. */
final class DesktopOutgoing {
    interface Delivery {
        MimeMessage send(SavedAccounts.Login login,JSONObject draft,File files,TransferMessage.Progress progress,MailSender.BeforeSend before)throws Exception;
        void copy(SavedAccounts.Login login,MimeMessage message)throws Exception;
    }
    static final class Fault extends Exception {final String code;Fault(String code){this.code=code;}}
    private final Delivery delivery;
    DesktopOutgoing(){this(new Delivery(){
        public MimeMessage send(SavedAccounts.Login login,JSONObject draft,File files,TransferMessage.Progress progress,MailSender.BeforeSend before)throws Exception{return MailSender.send(login,draft,files,progress,before);}
        public void copy(SavedAccounts.Login login,MimeMessage message)throws Exception{try(MailReader reader=new MailReader()){reader.connect(login.host,login.port,login.user,login.password);reader.saveSent(message);}}
    });}
    DesktopOutgoing(Delivery delivery){this.delivery=delivery;}
    static JSONObject draft(JSONObject args,File directory)throws Exception{
        JSONObject draft=new JSONObject();
        for(String key:new String[]{"to","cc","bcc","subject","body","inReplyTo"}){
            Object raw=args.opt(key);if(!(raw instanceof String))throw new Fault("BAD_DRAFT");draft.put(key,raw);
        }
        JSONArray references=args.optJSONArray("references");if(references==null||references.length()>100)throw new Fault("BAD_DRAFT");draft.put("references",references);
        JSONArray attachments=args.optJSONArray("attachments");if(attachments==null||attachments.length()>10)throw new Fault("BAD_DRAFT");
        long total=0;for(int i=0;i<attachments.length();i++){JSONObject file=attachments.getJSONObject(i);total+=MailSender.file(directory,file.getString("token")).length();if(total>AttachmentFiles.MAX_BYTES)throw new Fault("TOO_LARGE");}
        draft.put("attachments",attachments);return draft;
    }
    JSONObject send(SavedAccounts.Login auth,JSONObject args,TransferMessage.Progress progress,MailSender.BeforeSend boundary)throws Exception{
        String attempt=args.optString("attemptId");if(!attempt.matches("[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}"))throw new Fault("BAD_DRAFT");
        Path receipts=Paths.get(args.getString("receipts"));Files.createDirectories(receipts);Path receipt=receipts.resolve(attempt+".state");
        if(Files.exists(receipt)){
            if("sent".equals(Files.readString(receipt)))return new JSONObject().put("accepted",true).put("replayed",true).put("copySaved",JSONObject.NULL);
            throw new Fault("SEND_UNKNOWN");
        }
        String host=args.optString("smtpHost"),from=args.optString("from");int port=args.optInt("smtpPort",-1);Object tls=args.opt("smtpSsl");
        if(host.isEmpty()||host.length()>253||host.matches("(?s).*[\\p{Cntrl}\\s/:].*")||port<1||port>65535||!(tls instanceof Boolean))throw new Fault("BAD_DRAFT");
        File directory=new File(args.getString("directory"));if(!directory.isDirectory())throw new Fault("BAD_DRAFT");
        int recipients=0;
        for(String field:new String[]{"to","cc","bcc","from"}){
            try{InternetAddress[] parsed=OutgoingMail.addresses(args.optString(field),field.equals("to")||field.equals("from"));if(field.equals("from")){if(parsed.length!=1)throw new IllegalArgumentException();}else recipients+=parsed.length;}
            catch(Exception invalid){throw new Fault("BAD_"+field.toUpperCase(java.util.Locale.ROOT));}
        }
        if(recipients>50)throw new Fault("RECIPIENTS_LIMIT");
        JSONObject draft;
        SavedAccounts.Login sender=new SavedAccounts.Login(auth.host,auth.port,auth.user,auth.password,host,port,(Boolean)tls,from);
        try{draft=draft(args,directory);MailSender.message(Session.getInstance(new java.util.Properties()),sender,draft,directory);}
        catch(Fault known){throw known;}catch(Exception invalid){throw new Fault("BAD_DRAFT");}
        // Reuse the existing stable Message-ID hook; this is immediate SMTP, not a scheduler.
        draft.put("scheduledMessageId","<schedule-"+attempt+"@mail-pilot.local>");
        final boolean[] entered={false};MimeMessage message;
        try{
            message=delivery.send(sender,draft,directory,progress,()->{
                boundary.run();
                try(FileChannel file=FileChannel.open(receipt,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){
                    java.nio.ByteBuffer bytes=StandardCharsets.UTF_8.encode("sending");while(bytes.hasRemaining())file.write(bytes);file.force(true);
                }
                entered[0]=true;
            });
        }catch(Exception failure){
            if(entered[0]||Files.exists(receipt))throw new Fault("SEND_UNKNOWN");throw failure;
        }
        // SMTP acceptance is recorded BEFORE attempting the independent IMAP Sent copy.
        try{
            Path temporary=Files.createTempFile(receipts,"receipt-",".tmp");
            try{
                try(FileChannel file=FileChannel.open(temporary,StandardOpenOption.WRITE)){java.nio.ByteBuffer bytes=StandardCharsets.UTF_8.encode("sent");while(bytes.hasRemaining())file.write(bytes);file.force(true);}
                try{Files.move(temporary,receipt,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException unavailable){Files.move(temporary,receipt,StandardCopyOption.REPLACE_EXISTING);}
            }finally{Files.deleteIfExists(temporary);}
        }catch(IOException failed){throw new Fault("SEND_UNKNOWN");}
        progress.update("accepted",0,-1);
        boolean copied=true;try{delivery.copy(sender,message);}catch(Exception copy){copied=false;}
        return new JSONObject().put("accepted",true).put("copySaved",copied);
    }
}
