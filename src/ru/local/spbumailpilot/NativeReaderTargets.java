package ru.local.spbumailpilot;
import java.net.URI;
import java.util.*;
import java.util.regex.*;
import org.json.JSONArray;

/** Visible plain-text and extracted MIME href targets only. No network, HTML execution or automatic external actions. */
final class NativeReaderTargets {
    static final int MAX_TEXT=200000,MAX_TARGETS=20;
    private static final Pattern PLAIN_URL=Pattern.compile("(?i)https?://[^\\s<>\"']+");
    private static final Pattern HTML_SCRIPT_STYLE=Pattern.compile("(?is)<(script|style)\\b[^>]*>.*?</\\1>");
    private static final Pattern HTML_HREF=Pattern.compile("(?is)<a\\b[^>]*?\\bhref\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>\"']+))[^>]*>");
    private static final Pattern HTML_ENTITY=Pattern.compile("(?i)&(amp|quot|apos|lt|gt|#x[0-9a-f]{1,6}|#[0-9]{1,7});");
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
    static String decodeEntities(String raw){
        if(raw==null||raw.indexOf('&')<0)return raw;
        Matcher m=HTML_ENTITY.matcher(raw);
        StringBuffer sb=new StringBuffer();
        while(m.find()){
            String entity=m.group(1);
            String replacement=null;
            if("amp".equalsIgnoreCase(entity))replacement="&";
            else if("quot".equalsIgnoreCase(entity))replacement="\"";
            else if("apos".equalsIgnoreCase(entity))replacement="'";
            else if("lt".equalsIgnoreCase(entity))replacement="<";
            else if("gt".equalsIgnoreCase(entity))replacement=">";
            else if(entity.length()>1&&entity.charAt(0)=='#'){
                try{
                    int cp=(entity.charAt(1)=='x'||entity.charAt(1)=='X')?Integer.parseInt(entity.substring(2),16):Integer.parseInt(entity.substring(1),10);
                    if(cp>0&&cp<=0x10FFFF&&!Character.isSurrogate((char)cp))replacement=new String(Character.toChars(cp));
                }catch(Exception ignored){}
            }
            m.appendReplacement(sb,Matcher.quoteReplacement(replacement!=null?replacement:m.group(0)));
        }
        m.appendTail(sb);
        return sb.toString();
    }
    static List<String> extractHrefLinks(String html){
        if(html==null||html.isEmpty())return Collections.emptyList();
        String prefix=html.substring(0,Math.min(html.length(),MAX_TEXT));
        String cleaned=HTML_SCRIPT_STYLE.matcher(prefix).replaceAll("");
        LinkedHashSet<String> found=new LinkedHashSet<>();
        Matcher matcher=HTML_HREF.matcher(cleaned);
        while(matcher.find()){
            String raw=matcher.group(1)!=null?matcher.group(1):matcher.group(2)!=null?matcher.group(2):matcher.group(3);
            if(raw==null)continue;
            String decoded=decodeEntities(raw.trim());
            if(decoded==null||decoded.isEmpty())continue;
            String target=web(trimEnd(decoded));
            if(target!=null){
                if(found.size()==MAX_TARGETS&&!found.contains(target))break;
                found.add(target);
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(found));
    }
    private static String trimEnd(String raw){
        if(raw.length()>4096)return raw;
        int end=raw.length(),balance=0;
        for(int i=0;i<end;i++){if(raw.charAt(i)=='(')balance++;else if(raw.charAt(i)==')')balance--;}
        while(end>0){char last=raw.charAt(end-1);if(".,;!?:".indexOf(last)>=0)end--;else if(last==')'&&balance<0){end--;balance++;}else break;}
        return raw.substring(0,end);
    }
    static NativeReaderTargets from(String text){
        return from(text,(JSONArray)null);
    }
    static NativeReaderTargets from(String text,JSONArray extraLinks){
        if(extraLinks==null)return from(text,Collections.emptyList());
        List<String> extra=new ArrayList<>(Math.min(extraLinks.length(),MAX_TARGETS+1));
        for(int i=0;i<extraLinks.length()&&extra.size()<=MAX_TARGETS;i++){
            Object item=extraLinks.opt(i);
            if(item instanceof String)extra.add((String)item);
        }
        return from(text,extra);
    }
    static NativeReaderTargets from(String text,Collection<String> extraLinks){
        if(text==null)text="";boolean limited=text.length()>MAX_TEXT;
        String prefix=text.substring(0,Math.min(text.length(),MAX_TEXT));
        LinkedHashSet<String> links=new LinkedHashSet<>();
        Matcher matcher=PLAIN_URL.matcher(prefix);
        while(matcher.find()){
            String target=web(trimEnd(matcher.group()));
            if(target!=null){if(links.size()==MAX_TARGETS&&!links.contains(target)){limited=true;break;}links.add(target);}
        }
        if(extraLinks!=null){
            for(String raw:extraLinks){
                if(raw==null)continue;
                String decoded=decodeEntities(raw.trim());
                if(decoded==null||decoded.isEmpty())continue;
                String target=web(trimEnd(decoded));
                if(target!=null){
                    if(links.size()==MAX_TARGETS&&!links.contains(target)){limited=true;break;}
                    links.add(target);
                }
            }
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
