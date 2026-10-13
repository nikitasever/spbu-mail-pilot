package ru.local.spbumailpilot;
import org.json.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Exact reply identity, independent of subject text and read flags. */
final class ReplyReminder {
    final MailNoticeTarget target;final long at;final String messageId,sender,subject,key;
    private ReplyReminder(MailNoticeTarget target,long at,String id,String sender,String subject)throws Exception{
        this.target=target;this.at=at;messageId=id;this.sender=sender;this.subject=subject;
        byte[] digest=MessageDigest.getInstance("SHA-256").digest((target.encode()+"\n"+at+"\n"+id).getBytes(StandardCharsets.UTF_8));
        StringBuilder k=new StringBuilder();for(byte b:digest)k.append(String.format(Locale.ROOT,"%02x",b&255));key=k.toString();
    }
    static boolean validId(String id){return MailThreadHeaders.valid(id);}
    static boolean references(String header,String id){if(!validId(id)||header==null||header.length()>32768)return false;java.util.regex.Matcher m=java.util.regex.Pattern.compile("<[^<>\\s]+>").matcher(header);while(m.find())if(m.group().equals(id))return true;return false;}
    static List<ReplyReminder> parse(JSONArray items,String account){
        LinkedHashMap<String,ReplyReminder> result=new LinkedHashMap<>();if(items==null)return new ArrayList<>();
        for(int i=0;i<Math.min(500,items.length());i++)try{
            JSONObject item=items.getJSONObject(i);MailNoticeTarget target=MailNoticeTarget.parse(new JSONObject(item.getJSONObject("mail").toString()).put("account",account).toString());
            long at=item.getLong("at");String id=item.getString("messageId");if(target==null||at<=0||at>253402300799000L||!validId(id))continue;
            ReplyReminder value=new ReplyReminder(target,at,id,MailNotificationText.clean(item.optString("sender"),"Неизвестный отправитель",180),MailNotificationText.clean(item.optString("subject"),"Без темы",300));result.put(target.encode(),value);
        }catch(Exception invalid){}return new ArrayList<>(result.values());
    }
}
