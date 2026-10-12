package ru.local.spbumailpilot;

import java.util.regex.*;
import javax.mail.internet.MimeUtility;

/** Decode complete encoded words even when a sender embeds them in a filename. */
final class AttachmentNames {
    private static final Pattern WORD=Pattern.compile("=\\?[^?\\s]+\\?[bBqQ]\\?[^?]*\\?=");
    static String decode(String raw){
        if(raw==null)return "Вложение";
        if(raw.length()>8192)return raw;
        Matcher matcher=WORD.matcher(raw);StringBuilder text=new StringBuilder();int end=0,words=0;boolean previous=false;
        while(matcher.find()&&words++<64){
            String gap=raw.substring(end,matcher.start()),word=matcher.group(),decoded=word;boolean valid=false;
            try{decoded=MimeUtility.decodeWord(word);valid=!decoded.equals(word);}catch(Exception ignored){}
            if(!(previous&&valid&&gap.matches("[ \\t\\r\\n]+")))text.append(gap);
            text.append(decoded);end=matcher.end();previous=valid;
        }
        return text.append(raw.substring(end)).toString();
    }
}
