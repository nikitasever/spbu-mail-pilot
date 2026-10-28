package ru.local.spbumailpilot;
import org.json.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class NativeAttachmentsTest {
    interface Check {void run()throws Exception;}
    static void check(boolean value){if(!value)throw new AssertionError();}
    static void reject(Check action)throws Exception{try{action.run();}catch(Exception expected){return;}throw new AssertionError("Expected failure");}
    static JSONObject entry(String token,String name,long size)throws Exception{return new JSONObject().put("token",token).put("name",name).put("size",size);}
    public static void main(String[] args)throws Exception{
        String owner="account-"+new String(new char[64]).replace('\0','a');
        NativeDraft draft=NativeDraft.create(owner,null,"");
        File root=Files.createTempDirectory("native-files-test").toFile();
        try{
            File directory=NativeAttachments.directory(root,draft);
            byte[] original="Тестовый документ\nPDF fixture bytes".getBytes("UTF-8");
            File copy=AttachmentFiles.copy(new ByteArrayInputStream(original),directory,"Материалы.pdf",AttachmentFiles.MAX_BYTES);
            JSONArray files=new JSONArray().put(entry(copy.getName(),"Материалы.pdf",copy.length()));
            NativeAttachments.validate(files);
            NativeDraft withFiles=new NativeDraft(owner,NativeDraft.EDITING,new JSONObject(draft.mail.toString()).put("to","recipient@example.invalid").put("body","Текст ответа").put("attachments",files));
            NativeDraft restored=NativeDraft.parse(owner,withFiles.json().toString());
            check(restored.mail.getJSONArray("attachments").getJSONObject(0).getLong("size")==original.length);
            SavedAccounts.Login login=new SavedAccounts.Login("example.invalid",993,"own@example.invalid","fixture");
            javax.mail.internet.MimeMessage message=MailSender.message(javax.mail.Session.getInstance(new Properties()),login,restored.mail,directory);
            ByteArrayOutputStream wire=new ByteArrayOutputStream();message.writeTo(wire);
            javax.mail.internet.MimeMessage parsed=new javax.mail.internet.MimeMessage(javax.mail.Session.getInstance(new Properties()),new ByteArrayInputStream(wire.toByteArray()));
            javax.mail.Multipart mime=(javax.mail.Multipart)parsed.getContent();check(mime.getCount()==2);
            check(mime.getBodyPart(0).getContent().toString().contains("Текст ответа"));
            check(javax.mail.internet.MimeUtility.decodeText(mime.getBodyPart(1).getFileName()).equals("Материалы.pdf"));
            check(Arrays.equals(mime.getBodyPart(1).getInputStream().readAllBytes(),original));
            // Removing the URI/source after selection cannot remove the private draft copy.
            check(copy.isFile());
            NativeDraft[] persisted={withFiles};
            NativeDraft unknown=NativeSendTransaction.run(withFiles,next->{NativeDraft.checkWrite(persisted[0],next);persisted[0]=next;},()->{throw new MailSender.Uncertain(new IOException("SMTP connection lost"));});
            check(unknown.status.equals(NativeDraft.UNCERTAIN)&&copy.isFile());
            reject(()->NativeSendTransaction.run(unknown,next->{},()->{throw new AssertionError("Unexpected resend");}));
            check(MailSender.file(directory,copy.getName()).equals(copy.getCanonicalFile()));
            reject(()->NativeAttachments.validate(new JSONArray().put(entry("../outside","x",1))));
            reject(()->NativeAttachments.validate(new JSONArray().put(entry("mail-x/../../outside","x",1))));
            reject(()->NativeAttachments.validate(new JSONArray().put(entry("mail-x","../x",1))));
            reject(()->NativeAttachments.validate(new JSONArray().put(entry("mail-x","x",-1))));
            reject(()->NativeAttachments.validate(new JSONArray().put(entry("mail-x","x",AttachmentFiles.MAX_BYTES+1))));
            reject(()->NativeAttachments.append(files,files));
            JSONArray ten=new JSONArray();for(int i=0;i<10;i++)ten.put(entry("mail-"+i,"x",0));NativeAttachments.validate(ten);
            reject(()->NativeAttachments.append(ten,new JSONArray().put(entry("mail-extra","x",1))));
            int before=directory.list().length;
            reject(()->AttachmentFiles.copy(new ByteArrayInputStream(new byte[33]),directory,"too-big",32));
            check(directory.list().length==before);
            reject(()->AttachmentFiles.copy(new InputStream(){int read;public int read()throws IOException{if(read++<4)return 1;throw new IOException("Provider disconnected");}},directory,"broken",32));
            check(directory.list().length==before);
            reject(()->AttachmentFiles.copy(new ByteArrayInputStream(original),directory,"cancelled",100,bytes->{throw new IllegalStateException("Cancelled");}));
            check(directory.list().length==before);
            NativeDraft other=NativeDraft.create(owner,null,"");File otherDirectory=NativeAttachments.directory(root,other);
            File otherFile=AttachmentFiles.copy(new ByteArrayInputStream(original),otherDirectory,"other",100);
            reject(()->MailSender.file(otherDirectory,copy.getName()));
            String otherOwner="account-"+new String(new char[64]).replace('\0','b');
            File otherAccount=NativeAttachments.directory(root,NativeDraft.create(otherOwner,null,""));
            check(!otherAccount.getParentFile().equals(directory.getParentFile()));
            reject(()->MailSender.file(otherAccount,copy.getName()));
            JSONArray removed=NativeAttachments.remove(files,copy.getName());check(removed.length()==0);
            check(copy.exists()); // File remains until the metadata removal has committed.
            NativeAttachments.cleanup(directory,files);check(copy.exists());
            NativeAttachments.cleanup(directory,removed);check(!copy.exists()&&otherFile.exists());
            System.out.println("PASS native attachments: real MIME bytes/name metadata, round-trip draft, account/draft paths, traversal/duplicates/count/size limits, cancellation/provider failure cleanup, detach preserves other draft; no network.");
        }finally{
            try(java.util.stream.Stream<Path> paths=Files.walk(root.toPath())){paths.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.delete(p);}catch(IOException ignored){}});}
        }
    }
}
