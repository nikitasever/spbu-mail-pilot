package ru.local.spbumailpilot;

import javax.mail.*;
import javax.mail.internet.*;
import org.json.*;
import java.util.*;
import java.util.regex.*;

/** Conservative RFC message identifiers; subjects never determine a conversation. */
final class MailThreadHeaders {
    private static final Pattern ID=Pattern.compile("<[^<>\\s@]+@[^<>\\s@]+>");
    static boolean valid(String value){return value!=null&&value.length()<=998&&ID.matcher(value).matches()&&value.matches("[\\x21-\\x7e]+");}
    static List<String> ids(String[] headers){
        LinkedHashSet<String> result=new LinkedHashSet<>();
        if(headers!=null)for(String header:headers){if(header==null||header.length()>32768)continue;Matcher matcher=ID.matcher(header);while(matcher.find()&&result.size()<100){String id=matcher.group();if(valid(id))result.add(id);}if(result.size()>=100)break;}
        return new ArrayList<>(result);
    }
    static void fetch(FetchProfile profile){for(String name:new String[]{"Message-ID","References","In-Reply-To"})profile.add(name);}
    static void add(Message message,JSONObject result)throws Exception {
        try {List<String> own=ids(message.getHeader("Message-ID"));if(own.size()==1)result.put("messageId",own.get(0));
        List<String> references=ids(message.getHeader("References")),parents=ids(message.getHeader("In-Reply-To"));
        if(!references.isEmpty())result.put("references",new JSONArray(references));
        if(!parents.isEmpty())result.put("inReplyTo",String.join(" ",parents));
        }catch(MessagingException unavailable){ /* Missing optional headers must not hide the mail list. */ }
    }
    static void reply(MimeMessage message,String parent,JSONArray references)throws Exception {
        if(references==null)return; // Older drafts retain their existing single-parent References.
        if(references.length()>100)throw new IllegalArgumentException("Too many references");
        LinkedHashSet<String> ids=new LinkedHashSet<>();for(int i=0;i<references.length();i++){String id=references.getString(i);if(!valid(id))throw new IllegalArgumentException("Invalid reference");ids.add(id);}
        if(parent==null||parent.isEmpty()){if(!ids.isEmpty())throw new IllegalArgumentException("References without parent");return;}
        if(!valid(parent))throw new IllegalArgumentException("Invalid reply ID");
        // Keep the root plus the newest ancestors and always append the immediate parent.
        ids.remove(parent);List<String> values=new ArrayList<>(ids);while(values.size()>=100)values.remove(1);values.add(parent);
        message.setHeader("References",MimeUtility.fold(12,String.join(" ",values)));message.saveChanges();
    }
}
