package ru.local.spbumailpilot;
import org.json.*;
import java.util.*;

/** Plain-text writing helpers shared by the native editor and host-side tests. */
final class NativeWritingTools {
    static final class Template {
        final String id,title,body;
        Template(String id,String title,String body){this.id=id;this.title=title;this.body=body;}
    }
    static final class Insertion {
        final String body;final int cursor;
        Insertion(String body,int cursor){this.body=body;this.cursor=cursor;}
    }
    static List<Template> templates(JSONObject state){
        List<Template> result=new ArrayList<>();Set<String> ids=new HashSet<>();JSONArray items=state.optJSONArray("templates");
        if(items==null)return result;
        for(int i=0;i<items.length()&&result.size()<100;i++){
            JSONObject item=items.optJSONObject(i);if(item==null)continue;
            if(!(item.opt("id") instanceof String)||!(item.opt("title") instanceof String)||!(item.opt("body") instanceof String))continue;
            String id=item.optString("id"),title=item.optString("title"),body=item.optString("body");
            if(!id.matches("[A-Za-z0-9-]{1,100}")||ids.contains(id)||title.trim().isEmpty()||title.length()>180||body.trim().isEmpty()||body.length()>100000)continue;
            ids.add(id);result.add(new Template(id,title,body));
        }
        return result;
    }
    static String signatureBlock(JSONObject state){
        JSONObject settings=state.optJSONObject("settings");String text=settings==null||!(settings.opt("signatureText") instanceof String)?"":settings.optString("signatureText");
        return text.trim().isEmpty()||text.length()>4000?"":"-- \n"+text;
    }
    static boolean splitPair(String text,int point){return point>0&&point<text.length()&&Character.isHighSurrogate(text.charAt(point-1))&&Character.isLowSurrogate(text.charAt(point));}
    static Insertion insert(String body,String text,int first,int last){
        if(body==null||text==null||text.isEmpty()||body.length()>100000)throw new IllegalArgumentException("Invalid text");
        int a=Math.max(0,Math.min(body.length(),first)),b=Math.max(0,Math.min(body.length(),last));
        int start=Math.min(a,b),end=Math.max(a,b);
        if(start==end){if(splitPair(body,start)){start--;end=start;}}
        else{if(splitPair(body,start))start--;if(splitPair(body,end))end++;}
        String left=body.substring(0,start),right=body.substring(end),before=!left.isEmpty()&&!left.endsWith("\n")?"\n\n":"",after=!right.isEmpty()&&!right.startsWith("\n")?"\n\n":"";
        if((long)left.length()+before.length()+text.length()+after.length()+right.length()>100000)throw new IllegalArgumentException("Body limit");
        return new Insertion(left+before+text+after+right,left.length()+before.length()+text.length());
    }
    private static String withoutFinalNewlines(String text){int end=text.length();while(end>0&&(text.charAt(end-1)=='\n'||text.charAt(end-1)=='\r'))end--;return text.substring(0,end);}
    static Insertion signature(String body,JSONObject state){
        String block=signatureBlock(state);
        String tail=withoutFinalNewlines(body),signature=withoutFinalNewlines(block);
        if(block.isEmpty()||tail.equals(signature)||tail.endsWith("\n"+signature))return new Insertion(body,body.length());
        return insert(body,block,body.length(),body.length());
    }
}
