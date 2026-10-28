package ru.local.spbumailpilot;

import org.json.*;
import java.util.UUID;

/** Native editor record. Never stored in or merged over the legacy account JSON. */
final class NativeDraft {
    static final String EDITING="editing", DISPATCHING="dispatching", UNCERTAIN="uncertain", SENT="sent", SCHEDULING="scheduling", SCHEDULED="scheduled";
    final String owner,status;
    final long revision;
    final JSONObject mail;
    NativeDraft(String owner,String status,JSONObject mail)throws Exception{this(owner,status,mail,0);}
    NativeDraft(String owner,String status,JSONObject mail,long revision)throws Exception{
        if(revision<0)throw new IllegalArgumentException("Revision");this.revision=revision;
        if(!owner.matches("account-[0-9a-f]{64}")||!(EDITING.equals(status)||DISPATCHING.equals(status)||UNCERTAIN.equals(status)||SENT.equals(status)||SCHEDULING.equals(status)||SCHEDULED.equals(status)))throw new IllegalArgumentException("Draft identity");
        this.owner=owner;this.status=status;this.mail=new JSONObject(mail.toString());
        if(this.mail.has("kind")&&!"forward-text".equals(this.mail.optString("kind"))&&!"forward-eml".equals(this.mail.optString("kind")))throw new IllegalArgumentException("Draft kind");
        UUID.fromString(this.mail.getString("id"));
        for(String field:new String[]{"to","cc","bcc"})if(this.mail.optString(field).length()>4000||this.mail.optString(field).matches("(?s).*[\\r\\n].*"))throw new IllegalArgumentException("Addresses");
        String subject=this.mail.optString("subject"),parent=this.mail.optString("inReplyTo");
        if(subject.length()>500||subject.matches("(?s).*[\\r\\n].*")||this.mail.optString("body").length()>100000)throw new IllegalArgumentException("Content limit");
        if(!parent.isEmpty()&&!MailThreadHeaders.valid(parent))throw new IllegalArgumentException("Parent ID");
        JSONArray refs=this.mail.optJSONArray("references");
        if(refs==null||refs.length()>100||parent.isEmpty()&&refs.length()>0)throw new IllegalArgumentException("References");
        for(int i=0;i<refs.length();i++)if(!MailThreadHeaders.valid(refs.getString(i)))throw new IllegalArgumentException("Reference ID");
        JSONArray files=this.mail.optJSONArray("attachments");
        NativeAttachments.validate(files);
    }
    static void checkWrite(NativeDraft current,NativeDraft next){
        if(current==null){if(next.revision!=0)throw new IllegalStateException("Missing prior revision");}
        else if(!current.owner.equals(next.owner)||!current.mail.optString("id").equals(next.mail.optString("id"))||next.revision!=current.revision+1)
            throw new IllegalStateException("Draft changed in another editor");
    }
    static void checkRemoval(NativeDraft current,NativeDraft expected,String status){
        if(current==null||expected==null||!status.equals(current.status)||!status.equals(expected.status)||
            !current.owner.equals(expected.owner)||current.revision!=expected.revision||
            !current.mail.optString("id").equals(expected.mail.optString("id")))
            throw new IllegalStateException("Draft changed or delivery unresolved");
    }
    NativeDraft status(String status)throws Exception{return new NativeDraft(owner,status,mail,revision+1);}
    JSONObject json()throws Exception{return new JSONObject().put("revision",revision).put("version",1).put("owner",owner).put("status",status).put("mail",mail);}
    static NativeDraft parse(String owner,String value)throws Exception{
        JSONObject o=new JSONObject(value);if(o.getInt("version")!=1||!owner.equals(o.getString("owner")))throw new IllegalArgumentException("Draft owner");
        return new NativeDraft(owner,o.getString("status"),o.getJSONObject("mail"),o.getLong("revision"));
    }
    static NativeDraft create(String owner,JSONObject source,String signature)throws Exception{
        String to=source==null?"":source.optString("replyTo");if(source!=null&&to.isEmpty())to=source.optString("email");
        String subject=source==null?"":source.optString("subject");
        if(source!=null&&!subject.regionMatches(true,0,"Re:",0,3))subject="Re: "+subject;
        String parent=source==null?"":source.optString("messageId");if(!MailThreadHeaders.valid(parent))parent="";
        JSONArray refs=parent.isEmpty()||source.optJSONArray("references")==null?new JSONArray():source.getJSONArray("references");
        JSONObject mail=new JSONObject().put("id",UUID.randomUUID().toString()).put("to",to).put("cc","").put("bcc","")
            .put("subject",subject).put("body",signature).put("inReplyTo",parent).put("references",refs).put("attachments",new JSONArray());
        return new NativeDraft(owner,EDITING,mail);
    }
    static NativeDraft replyAll(String owner,JSONObject source,String signature,String from,String user)throws Exception{
        NativeReply.Targets targets=NativeReply.all(source,from,user);
        NativeDraft draft=create(owner,source,signature);
        return new NativeDraft(owner,EDITING,draft.mail.put("to",targets.to).put("cc",targets.cc));
    }
    static NativeDraft addressed(String owner,String email,String signature)throws Exception{
        if(!NativeRecipients.email(email))throw new IllegalArgumentException("Invalid contact address");
        NativeDraft draft=create(owner,null,signature);
        return new NativeDraft(owner,EDITING,draft.mail.put("to",email));
    }
}
