package ru.local.spbumailpilot;
import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;

public final class AttachmentTextTest {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception{
  Path directory=Files.createTempDirectory("attachment-text-");
  try{
   Path file=directory.resolve("fixture.txt");
   StringBuilder longText=new StringBuilder("A");for(int i=0;i<262150;i++)longText.append('Я');
   Files.write(file,longText.toString().getBytes(StandardCharsets.UTF_8));
   String viewed=AttachmentText.read(file.toFile());
   check(viewed.startsWith("AЯЯЯ"),"UTF-8 prefix split inside a code point must not become Windows-1251");
   check(viewed.contains("[Показана часть файла]"),"Truncation must be visible");
   Files.write(file,"Привет, коллеги".getBytes(Charset.forName("windows-1251")));
   check(AttachmentText.read(file.toFile()).equals("Привет, коллеги"),"Legacy Windows-1251 must remain readable");
   Files.write(file,"\uFEFFПривет".getBytes(StandardCharsets.UTF_16LE));
   check(AttachmentText.read(file.toFile()).equals("Привет"),"UTF-16 BOM must remain supported");
   Files.write(file,"\uFEFFПривет".getBytes(StandardCharsets.UTF_8));
   check(AttachmentText.read(file.toFile()).equals("Привет"),"UTF-8 BOM must not appear in the text");
   Files.write(file,new byte[0]);check(AttachmentText.read(file.toFile()).isEmpty(),"Empty local text must be safe");
   System.out.println("PASS: bounded UTF-8 prefix, legacy text, UTF-16/UTF-8 BOM and empty text.");
  }finally{try(java.util.stream.Stream<Path> paths=Files.walk(directory)){paths.sorted(java.util.Comparator.reverseOrder()).forEach(p->{try{Files.delete(p);}catch(IOException ignored){}});}}
 }
}
