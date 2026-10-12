package ru.local.spbumailpilot;

import javax.mail.*;
import javax.mail.internet.*;
import java.io.*;
import java.nio.file.Files;
import java.util.*;

public class AttachmentTest {
    static void check(boolean value){if(!value)throw new AssertionError();}
    public static void main(String[] args)throws Exception{
        String wire="MIME-Version: 1.0\r\nContent-Type: multipart/mixed; boundary=outer\r\n\r\n--outer\r\nContent-Type: text/plain; charset=UTF-8\r\n\r\nBody\r\n--outer\r\nContent-Type: application/pdf\r\nContent-Disposition: attachment; filename=sample.pdf\r\nContent-Transfer-Encoding: base64\r\n\r\nJVBERi0xLjcK\r\n--outer\r\nContent-Type: multipart/mixed; boundary=inner\r\n\r\n--inner\r\nContent-Type: application/octet-stream\r\nContent-Disposition: attachment; filename=sample.docx\r\nContent-Transfer-Encoding: base64\r\n\r\nUEsDBAAh/w==\r\n--inner--\r\n--outer--\r\n";
        MimeMessage message=new MimeMessage(Session.getInstance(new Properties()),new ByteArrayInputStream(wire.getBytes("US-ASCII")));
        check(AttachmentNames.decode("prezentacia_=?UTF-8?B?0KLQtdGB0YI=?=.pdf").equals("prezentacia_Тест.pdf"));
        check(AttachmentNames.decode("=?UTF-8?B?0KLQtdGB0YI=?= =?UTF-8?B?0KLQtdGB0YI=?=").equals("ТестТест"));
        check(AttachmentNames.decode("normal_имя.pdf").equals("normal_имя.pdf"));
        check(AttachmentNames.decode("=?unknown-charset?B?abcd?=").equals("=?unknown-charset?B?abcd?="));
        File directory=Files.createTempDirectory("mail-attachment-test-").toFile();
        File pdf;try(InputStream input=AttachmentParts.resolve(message,"1").getInputStream()){pdf=AttachmentFiles.copy(input,directory,"../../sample.pdf",100);}
        check(Arrays.equals(Files.readAllBytes(pdf.toPath()),"%PDF-1.7\n".getBytes("US-ASCII")));
        check(pdf.getCanonicalFile().getParentFile().equals(directory.getCanonicalFile()));
        long[] transferred={0},transferTotal={-1};Part encoded=AttachmentParts.resolve(message,"1");
        File measured=AttachmentTransfer.copy(encoded,directory,"measured.pdf",()->false,(bytes,total)->{check(bytes>=transferred[0]);transferred[0]=bytes;transferTotal[0]=total;});
        check(Arrays.equals(Files.readAllBytes(measured.toPath()),"%PDF-1.7\n".getBytes("US-ASCII")));
        check(transferred[0]==encoded.getSize()&&transferTotal[0]==encoded.getSize());measured.delete();
        File doc;try(InputStream input=AttachmentParts.resolve(message,"2.0").getInputStream()){doc=AttachmentFiles.copy(input,directory,"sample.docx",100);}
        check(Arrays.equals(Files.readAllBytes(doc.toPath()),new byte[]{80,75,3,4,0,33,(byte)255}));
        check(AttachmentFiles.mime("sample.docx","application/octet-stream").equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        check(AttachmentFiles.mime("REPORT.PDF",null).equals("application/pdf"));
        check(AttachmentFiles.safeName("C:\\folder\\name.pdf").equals("name.pdf"));
        for(String path:new String[]{"../1","0","9","2.9","1.0","root.bad",""}){
            try{AttachmentParts.resolve(message,path);throw new AssertionError("Invalid MIME path accepted: "+path);}catch(MessagingException|IllegalArgumentException expected){}
        }
        int before=directory.list().length;
        try{AttachmentFiles.copy(new ByteArrayInputStream(new byte[11]),directory,"large.bin",10);throw new AssertionError();}catch(IOException expected){}
        check(directory.list().length==before);
        // Transfer encoding can exceed 100 MiB while the saved file remains below the limit.
        final int largeEncodedSize=(int)AttachmentFiles.MAX_BYTES+4;
        MimeBodyPart largeEncoded=new MimeBodyPart(){
            public String getEncoding(){return "base64";}
            public int getSize(){return largeEncodedSize;}
            public InputStream getRawInputStream(){return new InputStream(){
                int remaining=largeEncodedSize;
                public int read(){if(remaining==0)return -1;remaining--;return 'A';}
                public int read(byte[] b,int off,int len){if(len==0)return 0;if(remaining==0)return -1;int n=Math.min(len,remaining);Arrays.fill(b,off,off+n,(byte)'A');remaining-=n;return n;}
            };}
        };
        File largeDecoded=AttachmentTransfer.copy(largeEncoded,directory,"large.bin",()->false,(bytes,total)->{});
        check(largeDecoded.length()==(long)largeEncodedSize/4*3);check(largeDecoded.delete());
        // An IMAP stream may end early without throwing: never cache it as a complete attachment.
        byte[] encodedBytes="JVBERi0xLjcK".getBytes("US-ASCII");
        MimeBodyPart truncated=new MimeBodyPart(){
            public String getEncoding(){return "base64";}
            public int getSize(){return encodedBytes.length+4;}
            public InputStream getRawInputStream(){return new ByteArrayInputStream(encodedBytes);}
        };
        try{AttachmentTransfer.copy(truncated,directory,"truncated.pdf",()->false,(bytes,total)->{});throw new AssertionError("Truncated attachment accepted");}catch(IOException expected){}
        check(directory.list().length==before);
        MimeBodyPart closeFailure=new MimeBodyPart(){
            public String getEncoding(){return "base64";}
            public int getSize(){return encodedBytes.length;}
            public InputStream getRawInputStream(){return new ByteArrayInputStream(encodedBytes){public void close()throws IOException{throw new IOException("Network close failed");}};}
        };
        try{AttachmentTransfer.copy(closeFailure,directory,"close.pdf",()->false,(bytes,total)->{});throw new AssertionError("Stream close failure accepted");}catch(IOException expected){}
        check(directory.list().length==before);
        // A retry starts from scratch, with exact decoded bytes and no leftover partial file.
        File retry=AttachmentTransfer.copy(encoded,directory,"retry.pdf",()->false,(bytes,total)->{});
        check(Arrays.equals(Files.readAllBytes(retry.toPath()),"%PDF-1.7\n".getBytes("US-ASCII")));check(retry.delete());
        java.util.concurrent.atomic.AtomicBoolean cancel=new java.util.concurrent.atomic.AtomicBoolean();
        try{AttachmentTransfer.copy(encoded,directory,"cancel.pdf",cancel::get,(bytes,total)->{if(bytes>0)cancel.set(true);});throw new AssertionError("Cancelled attachment accepted");}catch(BulkAttachments.Cancelled expected){}
        check(directory.list().length==before);
        InputStream broken=new InputStream(){public int read()throws IOException{throw new IOException("Test failure");}};
        try{AttachmentFiles.copy(broken,directory,"broken.bin",10);throw new AssertionError();}catch(IOException expected){}
        check(directory.list().length==before);
        for(File file:directory.listFiles())check(file.delete());check(directory.delete());
        System.out.println("PASS: nested MIME selection, decoded PDF/DOCX bytes, MIME detection, path safety, size limit and partial-download cleanup.");
    }
}

