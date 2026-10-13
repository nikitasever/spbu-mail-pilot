package ru.local.spbumailpilot;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Local reminders: no IMAP moves, flags or SMTP operations. */
final class MailSnooze {
    static final int MAX=500;
    final MailNoticeTarget target;final long at;final String sender,subject,key;
    private MailSnooze(MailNoticeTarget target,long at,String sender,String subject)throws Exception{
        this.target=target;this.at=at;this.sender=sender;this.subject=subject;
        byte[] digest=MessageDigest.getInstance("SHA-256").digest((target.encode()+"\n"+at).getBytes(StandardCharsets.UTF_8));
        StringBuilder k=new StringBuilder();for(byte b:digest)k.append(String.format(Locale.ROOT,"%02x",b&255));key=k.toString();
    }
    static final class Plan {
        final List<MailSnooze> due=new ArrayList<>();final Set<String> current=new HashSet<>();long next=Long.MAX_VALUE;
    }
    static Plan plan(JSONArray items,String account,Set<String> sent,long now){
        Plan result=new Plan();for(MailSnooze value:parse(items,account)){
            result.current.add(value.key);
            if(value.at>now)result.next=Math.min(result.next,value.at);
            else if(!sent.contains(value.key))result.due.add(value);
        }return result;
    }
    static List<MailSnooze> parse(JSONArray items,String account){
        LinkedHashMap<String,MailSnooze> result=new LinkedHashMap<>();if(items==null)return new ArrayList<>();
        for(int i=0;i<Math.min(items.length(),MAX);i++)try{
            JSONObject item=items.getJSONObject(i),ref=item.getJSONObject("mail");
            MailNoticeTarget target=MailNoticeTarget.parse(new JSONObject(ref.toString()).put("account",account).toString());
            long at=item.getLong("at");if(target==null||at<=0||at>253402300799000L)continue;
            MailSnooze value=new MailSnooze(target,at,MailNotificationText.clean(item.optString("sender"),"Неизвестный отправитель",180),MailNotificationText.clean(item.optString("subject"),"Без темы",300));
            result.put(target.encode(),value);
        }catch(Exception invalid){}return new ArrayList<>(result.values());
    }
}
