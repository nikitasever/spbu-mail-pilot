package ru.local.spbumailpilot;
import java.io.*;
import java.nio.file.*;
import java.util.Arrays;
import java.util.concurrent.atomic.*;

public final class AttachmentExportTest {
 static void check(boolean value){if(!value)throw new AssertionError();}
 public static void main(String[] args)throws Exception {
  Path source=Files.createTempFile("export-fixture-",".bin");
  byte[] content=new byte[70000];for(int i=0;i<content.length;i++)content[i]=(byte)i;Files.write(source,content);
  try{
   AtomicBoolean opened=new AtomicBoolean();
   try{AttachmentExport.copy(source.toFile(),()->{opened.set(true);throw new AssertionError("Cancelled export opened a destructive output");},()->false);throw new AssertionError();}catch(InterruptedIOException expected){}
   check(!opened.get());
   AtomicInteger checks=new AtomicInteger();
   try{AttachmentExport.copy(source.toFile(),()->{opened.set(true);throw new AssertionError("Cancellation before output was ignored");},()->checks.incrementAndGet()<2);throw new AssertionError();}catch(InterruptedIOException expected){}
   check(!opened.get());
   ByteArrayOutputStream out=new ByteArrayOutputStream();
   check(AttachmentExport.copy(source.toFile(),()->out,()->true)==content.length);check(Arrays.equals(out.toByteArray(),content));
   AtomicBoolean current=new AtomicBoolean(true),closed=new AtomicBoolean();
   OutputStream cancelling=new OutputStream(){public void write(int b){}public void write(byte[] b,int offset,int n){current.set(false);}public void close(){closed.set(true);}};
   try{AttachmentExport.copy(source.toFile(),()->cancelling,current::get);throw new AssertionError();}catch(InterruptedIOException expected){}
   check(closed.get());check(Arrays.equals(Files.readAllBytes(source),content));
   try{AttachmentExport.copy(source.toFile(),()->null,()->true);throw new AssertionError();}catch(IOException expected){}
   try(RandomAccessFile sparse=new RandomAccessFile(source.toFile(),"rw")){sparse.setLength(AttachmentFiles.MAX_BYTES+1);}
   try{AttachmentExport.copy(source.toFile(),()->{opened.set(true);throw new AssertionError("Oversized source opened output");},()->true);throw new AssertionError();}catch(IOException expected){}
   check(!opened.get());
   System.out.println("PASS: cancelled/oversized exports never open output; byte-preserving copy and cancellation close streams without changing source.");
  }finally{Files.deleteIfExists(source);}
 }
}
