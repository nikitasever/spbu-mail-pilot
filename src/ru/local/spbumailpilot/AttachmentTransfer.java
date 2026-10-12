package ru.local.spbumailpilot;

import java.io.*;
import java.util.function.BooleanSupplier;
import javax.mail.*;
import javax.mail.internet.*;

/** Count the transfer-encoded stream against the MIME body size, before decoding. */
final class AttachmentTransfer {
    interface Progress { void update(long bytes,long total); }
    static File copy(Part part,File directory,String name,BooleanSupplier cancelled,Progress progress)throws Exception{
        InputStream raw;String encoding=null;long total=-1;
        if(part instanceof MimeBodyPart){MimeBodyPart body=(MimeBodyPart)part;try{encoding=body.getEncoding();total=part.getSize();raw=body.getRawInputStream();}catch(MessagingException unavailable){encoding=null;total=-1;raw=part.getInputStream();}}
        else if(part instanceof MimeMessage){MimeMessage body=(MimeMessage)part;try{encoding=body.getEncoding();total=part.getSize();raw=body.getRawInputStream();}catch(MessagingException unavailable){encoding=null;total=-1;raw=part.getInputStream();}}
        else raw=part.getInputStream();
        final long expected=total;
        try(InputStream counted=new FilterInputStream(raw){
            long bytes;
            private void report(int n)throws IOException{if(n>0){bytes+=n;if(bytes>AttachmentFiles.MAX_BYTES*2)throw new IOException("Attachment transfer too large");progress.update(bytes,expected>=bytes?expected:-1);}}
            public int read()throws IOException{BulkAttachments.check(cancelled);int n=in.read();report(n<0?0:1);return n;}
            public int read(byte[] buffer,int offset,int length)throws IOException{BulkAttachments.check(cancelled);int n=in.read(buffer,offset,length);report(n);return n;}
        }){
            progress.update(0,total);
            InputStream decoded=encoding==null?counted:MimeUtility.decode(counted,encoding);
            return AttachmentFiles.copy(decoded,directory,name,AttachmentFiles.MAX_BYTES);
        }
    }
}
