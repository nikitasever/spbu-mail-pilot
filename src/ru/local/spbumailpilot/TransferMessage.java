package ru.local.spbumailpilot;
import javax.mail.*;
import javax.mail.internet.*;
import java.io.*;

final class TransferMessage extends MimeMessage {
    interface Progress {void update(String stage,long bytes,long total)throws IOException;}
    private Progress progress;private long total;
    TransferMessage(Session session){super(session);}
    void prepare(Progress listener,boolean smtp)throws Exception{
        progress=null;final long[] count={0};super.writeTo(new OutputStream(){public void write(int b){count[0]++;}public void write(byte[] b,int o,int n){count[0]+=n;}},smtp?new String[]{"Bcc","Content-Length"}:null);
        total=count[0];progress=listener;if(progress!=null)progress.update("connecting",0,total);
    }
    void finish()throws IOException{if(progress!=null)progress.update("complete",total,total);progress=null;}
    void detach(){progress=null;}
    @Override public void writeTo(OutputStream out)throws IOException,MessagingException{writeTo(out,null);}
    @Override public void writeTo(OutputStream out,String[] ignored)throws IOException,MessagingException{
        if(progress==null||out.getClass().getName().equals("com.sun.mail.imap.LengthCounter")){super.writeTo(out,ignored);return;}
        final Progress listener=progress;listener.update("uploading",0,total);
        OutputStream counted=new FilterOutputStream(out){long bytes,last;public void write(int b)throws IOException{out.write(b);bytes++;report(false);}public void write(byte[] b,int offset,int length)throws IOException{out.write(b,offset,length);bytes+=length;report(false);}void report(boolean force)throws IOException{long now=System.currentTimeMillis();if(force||now-last>=120){last=now;listener.update("uploading",bytes,total);}}};
        super.writeTo(counted,ignored);counted.flush();listener.update("confirming",total,total);
    }
}
