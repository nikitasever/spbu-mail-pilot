package ru.local.spbumailpilot;

import org.json.*;
import javax.mail.internet.InternetAddress;
import java.util.*;

/** Builds a reviewable reply-all envelope from explicit To/Cc headers only. */
final class NativeReply {
    static final class Invalid extends Exception {
        Invalid(String message){super(message);}
    }
    static final class Targets {
        final String to,cc;
        Targets(String to,String cc){this.to=to;this.cc=cc;}
    }
    static Targets all(JSONObject source,String from,String user)throws Invalid {
        if(source==null||source.optJSONArray("toRecipients")==null||source.optJSONArray("ccRecipients")==null||source.optJSONArray("replyToRecipients")==null)
            throw new Invalid("В сохранённой копии нет полного списка адресатов. Загрузите письмо заново или ответьте отправителю.");
        try {
            Set<String> used=new HashSet<>();
            for(InternetAddress address:OutgoingMail.addresses(from,true))used.add(key(address.getAddress()));
            if(user!=null&&user.contains("@"))used.add(key(single(user)));
            List<String> to=new ArrayList<>(),cc=new ArrayList<>();
            JSONArray replies=source.getJSONArray("replyToRecipients");
            if(replies.length()>0)add(replies,to,used);
            else add(source.optString("email"),to,used);
            add(source.getJSONArray("toRecipients"),to,used);
            add(source.getJSONArray("ccRecipients"),cc,used);
            if(to.isEmpty()&&!cc.isEmpty())to.add(cc.remove(0));
            if(to.isEmpty())throw new Invalid("В письме нет других адресатов для ответа. Создайте письмо и укажите получателя вручную.");
            String primary=String.join(", ",to),copies=String.join(", ",cc);
            if(to.size()+cc.size()>50||primary.length()>4000||copies.length()>4000)
                throw new Invalid("Слишком много адресатов для ответа всем. Ответьте отправителю и добавьте нужные адреса вручную.");
            return new Targets(primary,copies);
        }catch(Invalid failure){throw failure;}
        catch(Exception failure){throw new Invalid("Не удалось проверить всех адресатов. Загрузите письмо заново или ответьте отправителю; список не был сокращён автоматически.");}
    }
    private static String key(String address){return address.toLowerCase(Locale.ROOT);}
    private static String single(String value)throws Exception {
        InternetAddress[] parsed=OutgoingMail.addresses(value,true);
        if(parsed.length!=1)throw new IllegalArgumentException("One address expected");
        return parsed[0].getAddress();
    }
    private static void add(String value,List<String> result,Set<String> used)throws Exception {
        String email=single(value);
        if(used.add(key(email)))result.add(email);
    }
    private static void add(JSONArray values,List<String> result,Set<String> used)throws Exception {
        if(values.length()>1000)throw new IllegalArgumentException("Header too large");
        for(int i=0;i<values.length();i++){
            if(!(values.opt(i) instanceof String))throw new IllegalArgumentException("Invalid address entry");
            add(values.getString(i),result,used);
        }
    }
}
