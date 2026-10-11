package ru.local.spbumailpilot;

import android.text.Html;
import javax.mail.*;

/** Read one small text prefix, never attachments or forwarded messages. */
final class MailPreview {
    static String read(Part part)throws Exception { return clean(text(part,0,new int[]{80})); }
    static String clean(String value) {
        value=value.replace("\n[Показана часть текста]", "").replaceAll("[\\s\\u00a0]+", " ").trim();
        int end=Math.min(180,value.length());
        if(end>0&&end<value.length()&&Character.isHighSurrogate(value.charAt(end-1))&&Character.isLowSurrogate(value.charAt(end)))end--;
        return value.substring(0,end);
    }
    private static String text(Part part,int depth,int[] remaining)throws Exception {
        if(depth>12||remaining[0]--<=0||part.getFileName()!=null||Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition()))return "";
        if(part.isMimeType("text/plain"))return MailText.read(part,512);
        if(part.isMimeType("text/html")) {
            String html=MailText.read(part,4096).replaceAll("(?is)<(script|style)\\b[^>]*>.*?(</\\1>|$)", "");
            return Html.fromHtml(html,Html.FROM_HTML_MODE_COMPACT).toString();
        }
        if(!part.isMimeType("multipart/*"))return "";
        Multipart multi=(Multipart)part.getContent();
        if(part.isMimeType("multipart/alternative")) {
            for(int i=0;i<multi.getCount()&&i<80;i++)if(multi.getBodyPart(i).isMimeType("text/plain"))return text(multi.getBodyPart(i),depth+1,remaining);
        }
        for(int i=0;i<multi.getCount()&&remaining[0]>0;i++) {
            String value=text(multi.getBodyPart(i),depth+1,remaining);
            if(!value.trim().isEmpty())return value;
        }
        return "";
    }
}
