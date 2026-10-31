package ru.local.spbumailpilot;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
/** Bounded local text decoding, independent of Activity lifecycle. */
final class AttachmentText {
 static String read(File file)throws Exception{
  byte[] bytes;boolean truncated;try(InputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){
   byte[] buffer=new byte[4096];int n;while(out.size()<524288&&(n=in.read(buffer,0,Math.min(buffer.length,524288-out.size())))!=-1)out.write(buffer,0,n);truncated=in.read()!=-1;bytes=out.toByteArray();
  }
  String result;int skip=0;Charset charset=null;
  if(bytes.length>=2&&bytes[0]==(byte)0xff&&bytes[1]==(byte)0xfe){charset=StandardCharsets.UTF_16LE;skip=2;}
  else if(bytes.length>=2&&bytes[0]==(byte)0xfe&&bytes[1]==(byte)0xff){charset=StandardCharsets.UTF_16BE;skip=2;}
  else if(bytes.length>=3&&bytes[0]==(byte)0xef&&bytes[1]==(byte)0xbb&&bytes[2]==(byte)0xbf){charset=StandardCharsets.UTF_8;skip=3;}
  if(charset!=null)result=new String(bytes,skip,bytes.length-skip,charset);else try{
   // A bounded prefix may end inside a valid UTF-8 code point. Only complete
   // input should report that trailing sequence as malformed.
   CharsetDecoder decoder=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT);
   CharBuffer text=CharBuffer.allocate(bytes.length);
   CoderResult decoded=decoder.decode(ByteBuffer.wrap(bytes),text,!truncated);
   if(decoded.isError())decoded.throwException();
   ((java.nio.Buffer)text).flip();result=text.toString();
  }catch(CharacterCodingException legacy){result=new String(bytes,Charset.forName("windows-1251"));}
  if(result.length()>100000){int end=100000;if(Character.isHighSurrogate(result.charAt(end-1)))end--;result=result.substring(0,end);truncated=true;}
  return result+(truncated?"\n[Показана часть файла]":"");
 }
}
