package ru.local.spbumailpilot;

import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Properties;

/** Diagnostic fixtures: a real malformed From is needed to diagnose the reported name. */
public class HeaderUnicodeTest {
    static void check(boolean value){if(!value)throw new AssertionError();}
    static String quotedPrintable(byte[] bytes){
        StringBuilder result=new StringBuilder();
        for(byte value:bytes)result.append(String.format("=%02X",value&255));
        return result.toString();
    }
    static void fixture(String charset,String encoding,String expected)throws Exception{
        byte[] bytes=expected.getBytes(Charset.forName(charset));
        String encoded=encoding.equals("B")?Base64.getEncoder().encodeToString(bytes):quotedPrintable(bytes);
        String raw="From: =?"+charset+"?"+encoding+"?"+encoded+"?= <fixture@example.invalid>\r\nSubject: fixture\r\n\r\nbody";
        MimeMessage message=new MimeMessage(Session.getInstance(new Properties()),new ByteArrayInputStream(raw.getBytes(StandardCharsets.US_ASCII)));
        InternetAddress from=(InternetAddress)message.getFrom()[0];
        check(expected.equals(from.getPersonal()));
        check(from.getPersonal().indexOf('\uFFFD')<0);
    }
    public static void main(String[] args)throws Exception{
        String name="Виктор Олегович";
        fixture("UTF-8","B",name);fixture("UTF-8","Q",name);
        fixture("windows-1251","B",name);fixture("windows-1251","Q",name);
        fixture("UTF-8","B",name+" \uD83D\uDE00");fixture("UTF-8","Q",name+" \uD83D\uDE00");
        System.out.println("PASS: full UTF-8/windows-1251 B/Q From personal names and supplementary Unicode.");
    }
}
