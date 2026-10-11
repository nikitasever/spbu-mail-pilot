package ru.local.spbumailpilot;

import javax.mail.Part;
import javax.mail.internet.*;
import java.io.*;
import java.nio.charset.*;

/** Decode only the displayed prefix; a large text part must not delay attachment metadata. */
final class MailText {
    static String read(Part part,int maximum)throws Exception{
        if(maximum<=0)return "";
        String name=new ContentType(part.getContentType()).getParameter("charset");
        Charset charset;
        try{charset=Charset.forName(MimeUtility.javaCharset(name==null?"US-ASCII":name));}catch(Exception unsupported){charset=StandardCharsets.UTF_8;}
        try(Reader reader=new InputStreamReader(part.getInputStream(),charset)){
            StringBuilder text=new StringBuilder();char[] buffer=new char[4096];
            while(text.length()<maximum){int count=reader.read(buffer,0,Math.min(buffer.length,maximum-text.length()));if(count<0)return text.toString();text.append(buffer,0,count);}
            int next=reader.read();
            // The bound is in UTF-16 units; never display half of a valid code point.
            if(next>=0&&Character.isLowSurrogate((char)next)&&text.length()>0&&Character.isHighSurrogate(text.charAt(text.length()-1)))text.setLength(text.length()-1);
            return text.toString()+(next<0?"":"\n[Показана часть текста]");
        }
    }
}

