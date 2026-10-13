package ru.local.spbumailpilot;
import javax.mail.*;import javax.mail.internet.*;import java.util.*;import java.nio.charset.*;import java.io.*;
public final class MimeWordsTest {
 static void check(boolean v){if(!v)throw new AssertionError();}
 static String word(String charset,String encoding,byte[] bytes){StringBuilder q=new StringBuilder();for(byte b:bytes)q.append(String.format(Locale.ROOT,"=%02X",b&255));return "=?"+charset+"?"+encoding+"?"+(encoding.equals("B")?Base64.getEncoder().encodeToString(bytes):q.toString())+"?=";}
 public static void main(String[] args)throws Exception{
  String expected="Перминов Виктор Олегович 😀",email="fixture@example.invalid";byte[] bytes=expected.getBytes(StandardCharsets.UTF_8);
  for(int cut=1;cut<bytes.length;cut++)for(String a:new String[]{"B","Q"})for(String b:new String[]{"B","Q"}){String words=word("UTF-8",a,Arrays.copyOfRange(bytes,0,cut))+"\r\n \t"+word("utf8",b,Arrays.copyOfRange(bytes,cut,bytes.length));check(MimeWords.decode(words).equals(expected));MimeMessage message=new MimeMessage(Session.getInstance(new Properties()),new ByteArrayInputStream(("From: "+words+" <"+email+">\r\n\r\nfixture").getBytes(StandardCharsets.US_ASCII)));InternetAddress address=(InternetAddress)message.getFrom()[0];check(MimeWords.personal(address).equals(expected));check(address.getAddress().equals(email));}
  for(String charset:new String[]{"windows-1251","UTF-16","ISO-2022-JP"}){String first=charset.equals("ISO-2022-JP")?"日本語":"Виктор",second=charset.equals("ISO-2022-JP")?" ABC":" Олегович";check(MimeWords.decode(word(charset,"B",first.getBytes(charset))+" "+word(charset,"B",second.getBytes(charset))).equals(first+second));}
  String broken="=?UTF-8?B?0A==?=";check(MimeWords.decode(broken).equals(broken));check(MimeWords.personal(new InternetAddress(broken+" <"+email+">")).equals(email));check(MimeWords.decode("file_"+word("UTF-8","B",expected.getBytes(StandardCharsets.UTF_8))+".pdf").equals("file_"+expected+".pdf"));
  String hostile="Имя, <другой адрес>";InternetAddress address=new InternetAddress(word("UTF-8","B",hostile.getBytes(StandardCharsets.UTF_8))+" <"+email+">");check(MimeWords.personal(address).equals(hostile)&&address.getAddress().equals(email));
  System.out.println("PASS: all UTF-8 split-byte positions across B/Q folded words, supplementary code points, actual JavaMail From, UTF-16 BOM/stateful charset resets, strict malformed fallback and address isolation.");
 }
}
