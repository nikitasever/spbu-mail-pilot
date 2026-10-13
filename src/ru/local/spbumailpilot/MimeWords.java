package ru.local.spbumailpilot;
import java.util.*;
import java.util.regex.*;
import java.nio.*;
import java.nio.charset.*;
import java.io.*;
import javax.mail.internet.*;

/** Decode adjacent words as octets first; never replace malformed code points silently. */
final class MimeWords {
    private static final Pattern WORD=Pattern.compile("=\\?([^?\\s]+)\\?([bBqQ])\\?([^?]*)\\?=");
    private static final class Token {final int start,end;final Charset charset;final byte[] bytes;Token(Matcher m)throws Exception{start=m.start();end=m.end();charset=Charset.forName(MimeUtility.javaCharset(m.group(1)));bytes=octets(m.group(2),m.group(3));}}
    private static byte[] octets(String type,String payload)throws Exception{
        if(type.equalsIgnoreCase("B"))return Base64.getDecoder().decode(payload);
        ByteArrayOutputStream out=new ByteArrayOutputStream();for(int i=0;i<payload.length();i++){char c=payload.charAt(i);if(c=='_')out.write(' ');else if(c=='='){if(i+2>=payload.length())throw new IOException("Bad Q word");int a=Character.digit(payload.charAt(++i),16),b=Character.digit(payload.charAt(++i),16);if(a<0||b<0)throw new IOException("Bad Q word");out.write(a*16+b);}else{if(c>127)throw new IOException("Non ASCII word");out.write(c);}}return out.toByteArray();
    }
    static String decode(String raw){
        if(raw==null)return "";if(raw.length()>8192)return raw;
        ArrayList<Token> tokens=new ArrayList<>();Matcher m=WORD.matcher(raw);try{while(m.find()){if(tokens.size()>=64)return raw;tokens.add(new Token(m));}}catch(Exception invalid){return raw;}
        StringBuilder text=new StringBuilder();int cursor=0;
        for(int i=0;i<tokens.size();){Token first=tokens.get(i),last=first;String gap=raw.substring(cursor,first.start);if(!(i>0&&gap.matches("[ \\t\\r\\n]*")))text.append(gap);ByteArrayOutputStream bytes=new ByteArrayOutputStream();bytes.write(first.bytes,0,first.bytes.length);int next=i+1;
            while(next<tokens.size()){Token t=tokens.get(next);if(!first.charset.equals(t.charset)||!raw.substring(last.end,t.start).matches("[ \\t\\r\\n]*"))break;bytes.write(t.bytes,0,t.bytes.length);last=t;next++;}
            try{StringBuilder separate=new StringBuilder();try{for(int t=i;t<next;t++)separate.append(strict(tokens.get(t).charset,tokens.get(t).bytes));text.append(separate);}catch(Exception splitCharacter){text.append(strict(first.charset,bytes.toByteArray()));}}catch(Exception invalid){return raw;}
            cursor=last.end;i=next;
        }return text.append(raw.substring(cursor)).toString();
    }
    private static String strict(Charset charset,byte[] bytes)throws Exception{return charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}
    private static final class EncodedAddress extends InternetAddress {EncodedAddress(String text)throws AddressException{super(text);}String personalRaw(){return encodedPersonal==null?personal:encodedPersonal;}}
    static String personal(InternetAddress address){try{String raw=new EncodedAddress(address.toString()).personalRaw();if(raw==null||raw.isEmpty())return address.getAddress();String decoded=decode(raw);return decoded.indexOf('\uFFFD')>=0||WORD.matcher(decoded).find()?address.getAddress():decoded;}catch(Exception invalid){return address.getAddress();}}
}
