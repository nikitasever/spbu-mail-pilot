package ru.local.spbumailpilot;

import org.json.*;
import javax.mail.internet.InternetAddress;
import java.util.*;

/** Completion changes only the unfinished final address, never an earlier recipient. */
final class NativeRecipients {
    static String query(String value,int caret,boolean composing){
        if(value==null||value.length()>4000||composing||caret!=value.length()||value.matches("(?s).*\\p{Cntrl}.*"))return "";
        String tail=value.substring(boundary(value)).trim();
        return tail.length()>=2&&tail.length()<=120&&!tail.contains("\"")&&!tail.contains("<")&&!tail.contains(">")&&!tail.contains("(")&&!tail.contains(")")?tail:"";
    }
    static int boundary(String value){
        boolean quoted=false,escaped=false;int angle=0,comment=0,start=0;
        for(int i=0;i<value.length();i++){
            char c=value.charAt(i);
            if(escaped){escaped=false;continue;}
            if((quoted||comment>0)&&c=='\\'){escaped=true;continue;}
            if(!quoted&&c=='('){comment++;continue;}
            if(comment>0){if(c==')')comment--;continue;}
            if(c=='"'){quoted=!quoted;continue;}
            if(!quoted){if(c=='<')angle++;else if(c=='>')angle=Math.max(0,angle-1);else if(angle==0&&(c==','||c==';'))start=i+1;}
        }
        return start;
    }
    static boolean email(String value){
        if(value==null||value.length()>254||value.matches("(?s).*[\\p{Cntrl}\\s<>].*"))return false;
        try{InternetAddress[] parsed=OutgoingMail.addresses(value,true);return parsed.length==1&&value.equals(parsed[0].getAddress());}catch(Exception invalid){return false;}
    }
    static Set<String> used(String to,String cc,String bcc,String active){
        Set<String> used=new HashSet<>();String[] values={to,cc,bcc};String[] fields={"to","cc","bcc"};
        for(int i=0;i<values.length;i++)try{
            String value=fields[i].equals(active)?values[i].substring(0,boundary(values[i])):values[i];
            value=value.replaceFirst("[,;]\\s*$","");
            for(InternetAddress address:OutgoingMail.addresses(value,false))used.add(address.getAddress().toLowerCase(Locale.ROOT));
        }catch(Exception incomplete){}
        return used;
    }
    static JSONArray matches(String query,JSONArray candidates,Set<String> excluded)throws Exception{
        LinkedHashMap<String,JSONObject> result=new LinkedHashMap<>();String needle=query.toLowerCase(Locale.ROOT);
        for(int i=0;i<candidates.length()&&result.size()<20;i++){
            JSONObject item=candidates.optJSONObject(i);if(item==null)continue;
            String address=item.optString("email"),name=item.optString("sender",item.optString("name"));
            String key=address.toLowerCase(Locale.ROOT);
            if(!email(address)||excluded.contains(key)||result.containsKey(key)||!(key.contains(needle)||name.toLowerCase(Locale.ROOT).contains(needle)))continue;
            if(name.length()>180)name=name.substring(0,Character.isHighSurrogate(name.charAt(179))?179:180);
            result.put(key,new JSONObject().put("email",address).put("sender",name).put("origin",item.optString("origin","local")));
        }
        return new JSONArray(result.values());
    }
    static String replace(String value,String chosen,Set<String> excluded){
        if(!email(chosen)||excluded.contains(chosen.toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("Invalid or duplicate recipient");
        int start=boundary(value);String result=value.substring(0,start)+(start>0?" ":"")+chosen+", ";
        if(result.length()>4000)throw new IllegalArgumentException("Recipients too long");
        return result;
    }
}
