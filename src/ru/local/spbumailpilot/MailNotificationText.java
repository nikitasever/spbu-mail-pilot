package ru.local.spbumailpilot;
import org.json.JSONObject;

final class MailNotificationText {
    static String clean(String value,String fallback,int limit){
        String text=value==null?"":value.replaceAll("[\\p{Cntrl}\\u202a-\\u202e\\u2066-\\u2069]"," ").replaceAll("\\s+"," ").trim();
        if(text.isEmpty())text=fallback;int count=text.codePointCount(0,text.length());return count<=limit?text:text.substring(0,text.offsetByCodePoints(0,limit))+"…";
    }
    static String sender(String name,String email){return clean(name,clean(email,"Неизвестный отправитель",180),180);}
    static String title(JSONObject update){int count=update.optInt("count");return count==1?sender(update.optString("sender"),update.optString("email")):"Новые письма · "+count;}
    static String text(JSONObject update,String account){String content=update.optInt("count")==1?clean(update.optString("subject"),"Без темы",180):update.optString("summary");return content+"\n"+clean(account,"",254);}
}
