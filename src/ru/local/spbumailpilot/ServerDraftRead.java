package ru.local.spbumailpilot;
import javax.mail.*;
import javax.mail.internet.*;
import org.json.*;
import java.util.*;
import java.util.regex.*;

/** Full editable text/envelope; binary payloads are read separately after explicit consent. */
final class ServerDraftRead {
    static JSONObject read(Message message)throws Exception{
        if(message.isExpunged()||!message.isSet(Flags.Flag.DRAFT)||message.isSet(Flags.Flag.DELETED))throw new MessagingException("Not an available draft");
        Part body=body(message,0,new int[]{500});boolean html=body!=null&&body.isMimeType("text/html");
        String text=body==null?"":MailText.readComplete(body,100000);
        String subject=message.getSubject()==null?"":message.getSubject();
        if(subject.length()>500||subject.contains("\r")||subject.contains("\n"))throw new MessagingException("Draft subject limit");
        JSONArray parents=ids(message,"In-Reply-To",1),references=ids(message,"References",100);
        if(parents.length()==0&&references.length()>0)throw new MessagingException("References without parent not supported by editor");
        JSONArray files=MailAttachments.list(message);if(files.length()>10)throw new MessagingException("Editor attachment count limit");
        JSONObject draft=new JSONObject().put("to",addresses(message.getRecipients(Message.RecipientType.TO)))
            .put("cc",addresses(message.getRecipients(Message.RecipientType.CC))).put("bcc",addresses(message.getRecipients(Message.RecipientType.BCC)))
            .put("subject",subject).put("body",text).put("inReplyTo",parents.length()==0?"":parents.getString(0)).put("references",references).put("attachments",new JSONArray());
        return new JSONObject().put("draft",draft).put("html",html).put("attachments",files).put("from",addresses(message.getFrom()))
            .put("key",header(message,"X-Spbu-Pilot-Draft")).put("revision",header(message,"X-Spbu-Pilot-Revision"));
    }
    private static String header(Message message,String key)throws Exception{String[] values=message.getHeader(key);return values==null||values.length!=1||values[0].length()>200?"":values[0];}
    private static String addresses(Address[] entries)throws Exception{
        List<String> values=new ArrayList<>();if(entries!=null)for(Address entry:entries){if(!(entry instanceof InternetAddress))throw new MessagingException("Unsupported address");values.add(((InternetAddress)entry).toUnicodeString());}
        String value=String.join(", ",values);if(value.length()>4000||value.contains("\r")||value.contains("\n"))throw new MessagingException("Address field limit");return value;
    }
    private static JSONArray ids(Message message,String name,int limit)throws Exception{
        String[] headers=message.getHeader(name);LinkedHashSet<String> result=new LinkedHashSet<>();
        if(headers!=null)for(String value:headers){
            if(value.length()>32768)throw new MessagingException("Thread header limit");
            Matcher matches=Pattern.compile("<[^<>]*>").matcher(value);StringBuffer rest=new StringBuffer();
            while(matches.find()){String id=matches.group();if(!MailThreadHeaders.valid(id))throw new MessagingException("Invalid thread header");result.add(id);if(result.size()>limit)throw new MessagingException("Thread header count");matches.appendReplacement(rest,"");}
            matches.appendTail(rest);if(!rest.toString().trim().isEmpty())throw new MessagingException("Unsupported thread header");
        }
        return new JSONArray(result);
    }
    private static Part body(Part part,int depth,int[] remaining)throws Exception{
        if(depth>12||--remaining[0]<0)throw new MessagingException("Draft MIME structure limit");
        if(MailAttachments.file(part))return null;
        if(part.isMimeType("text/plain")||part.isMimeType("text/html"))return part;
        if(part.isMimeType("multipart/signed")||part.isMimeType("multipart/encrypted"))throw new MessagingException("Signed/encrypted draft requires original client");
        if(part.isMimeType("multipart/*")){
            Multipart multipart=(Multipart)part.getContent();Part selected=null;
            boolean alternative=part.isMimeType("multipart/alternative");
            for(int i=0;i<multipart.getCount();i++){
                Part candidate=body(multipart.getBodyPart(i),depth+1,remaining);if(candidate==null)continue;
                if(selected==null)selected=candidate;
                else if(!alternative)throw new MessagingException("Multiple body sections require original client");
                else if(!selected.isMimeType("text/plain")&&candidate.isMimeType("text/plain"))selected=candidate;
            }
            return selected;
        }
        if(part.isMimeType("image/*")&&Part.INLINE.equalsIgnoreCase(part.getDisposition()))return null;
        throw new MessagingException("Unsupported draft body");
    }
}
