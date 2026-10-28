package ru.local.spbumailpilot;
import javax.mail.*;
import javax.mail.internet.MimeMessage;
import java.io.*;
import java.util.function.*;
import org.json.*;
/** Streaming EML export with a bound, cancellation, and no partial files. */
final class MessageExport {
    static JSONObject write(Message message,File directory,boolean forwarding,BooleanSupplier cancelled,LongConsumer progress)throws Exception{
        if(message==null||message.isExpunged()||message.isSet(Flags.Flag.DELETED))throw new MessagingException("Message unavailable");
        if(forwarding&&!(message instanceof MimeMessage))throw new MessagingException("Unsupported original message");
        String name=AttachmentFiles.safeName(AttachmentFiles.safeName(message.getSubject())+".eml");
        if(cancelled.getAsBoolean())throw new IOException("Cancelled");
        if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Cannot create export directory");
        File file=File.createTempFile(forwarding?"mail-":"message-",".eml",directory);boolean complete=false;
        try{
            try(OutputStream raw=new FileOutputStream(file);OutputStream bounded=new FilterOutputStream(raw){
                long size;
                void advance(int length)throws IOException{if(cancelled.getAsBoolean())throw new IOException("Cancelled");if(length<0||size>AttachmentFiles.MAX_BYTES-length)throw new IOException("Message too large");size+=length;}
                public void write(int b)throws IOException{advance(1);out.write(b);progress.accept(size);}
                public void write(byte[] b,int off,int len)throws IOException{advance(len);out.write(b,off,len);progress.accept(size);}
            }){
                if(forwarding)((MimeMessage)message).writeTo(bounded,new String[]{"Bcc","Resent-Bcc"});else message.writeTo(bounded);
            }
            if(cancelled.getAsBoolean())throw new IOException("Cancelled");
            complete=true;return new JSONObject().put("file",file.getAbsolutePath()).put("name",name).put("mime","message/rfc822");
        }finally{if(!complete)file.delete();}
    }
}
