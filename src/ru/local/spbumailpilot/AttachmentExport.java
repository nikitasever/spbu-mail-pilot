package ru.local.spbumailpilot;
import java.io.*;
import java.util.function.BooleanSupplier;

/** Open a destructive output destination only while the explicit export is current. */
final class AttachmentExport {
 interface Destination {OutputStream open()throws IOException;}
 private static void check(BooleanSupplier current)throws InterruptedIOException {
  if(!current.getAsBoolean())throw new InterruptedIOException("Export cancelled");
 }
 static long copy(File source,Destination destination,BooleanSupplier current)throws IOException {
  check(current);
  if(!source.isFile()||source.length()>AttachmentFiles.MAX_BYTES)throw new IOException("Invalid export source");
  try(InputStream in=new FileInputStream(source)){
   check(current);
   try(OutputStream out=destination.open()){
    if(out==null)throw new IOException("No export destination");
    byte[] bytes=new byte[32768];long size=0;
    while(true){
     check(current);int n=in.read(bytes);if(n<0)break;
     size+=n;if(size>AttachmentFiles.MAX_BYTES)throw new IOException("Export too large");
     check(current);out.write(bytes,0,n);
    }
    check(current);out.flush();check(current);return size;
   }
  }
 }
}
