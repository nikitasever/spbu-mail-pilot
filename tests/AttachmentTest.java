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
        InputStream broken=new InputStream(){public int read()throws IOException{throw new IOException("Test failure");}};
        try{AttachmentFiles.copy(broken,directory,"broken.bin",10);throw new AssertionError();}catch(IOException expected){}
        check(directory.list().length==before);
        for(File file:directory.listFiles())check(file.delete());check(directory.delete());
        System.out.println("PASS: nested MIME selection, decoded PDF/DOCX bytes, MIME detection, path safety, size limit and partial-download cleanup.");
    }
}

