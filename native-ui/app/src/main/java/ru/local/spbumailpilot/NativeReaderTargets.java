package ru.local.spbumailpilot;
import java.net.URI;
import java.util.*;
import java.util.regex.*;

/** Visible plain-text targets only. No network, HTML execution or automatic external actions. */
final class NativeReaderTargets {
    static final int MAX_TEXT=200000,MAX_TARGETS=20;
    final List<String> links,phones;final boolean limited;
    private NativeReaderTargets(List<String> links,List<String> phones,boolean limited){
        this.links=Collections.unmodifiableList(links);this.phones=Collections.unmodifiableList(phones);this.limited=limited;
    }
    static String web(String raw){
        if(raw==null||raw.length()>4096)return null;
        for(int i=0;i<raw.length();i++)if(Character.isISOControl(raw.charAt(i))||Character.getType(raw.charAt(i))==Character.FORMAT)return null;
        try{
            URI uri=new URI(raw);String scheme=uri.getScheme();
            if(!"https".equalsIgnoreCase(scheme)&&!"http".equalsIgnoreCase(scheme))return null;
            if(uri.getHost()==null||uri.getHost().isEmpty()||uri.getRawUserInfo()!=null||uri.getPort()>65535||uri.getPort()==0)return null;
            return uri.toASCIIString();
        }catch(Exception invalid){return null;}
    }
    private static String trimEnd(String raw){
        if(raw.length()>4096)return raw;
        int end=raw.length(),balance=0;
        for(int i=0;i<end;i++){if(raw.charAt(i)=='(')balance++;else if(raw.charAt(i)==')')balance--;}
        while(end>0){char last=raw.charAt(end-1);if(".,;!?:".indexOf(last)>=0)end--;else if(last==')'&&balance<0){end--;balance++;}else break;}
        return raw.substring(0,end);
    }
    static NativeReaderTargets from(String text){
        if(text==null)text="";boolean limited=text.length()>MAX_TEXT;
        String prefix=text.substring(0,Math.min(text.length(),MAX_TEXT));
        LinkedHashSet<String> links=new LinkedHashSet<>();
        Matcher matcher=Pattern.compile("(?i)https?://[^\\s<>\"']+").matcher(prefix);
        while(matcher.find()){
            String target=web(trimEnd(matcher.group()));
            if(target!=null){if(links.size()==MAX_TARGETS&&!links.contains(target)){limited=true;break;}links.add(target);}
        }
        List<String> allPhones=PhoneNumbers.find(prefix);if(allPhones.size()>MAX_TARGETS)limited=true;
        return new NativeReaderTargets(new ArrayList<>(links),new ArrayList<>(allPhones.subList(0,Math.min(allPhones.size(),MAX_TARGETS))),limited);
    }
    static String share(String subject,String sender,String body){
        String text="Тема: "+(subject==null?"":subject)+"\nОт: "+(sender==null?"":sender)+"\n\n"+(body==null?"":body);
        if(text.length()>140000)throw new IllegalArgumentException("Письмо слишком большое для передачи текста. Выделите и скопируйте нужный фрагмент.");
        return text;
    }
}
