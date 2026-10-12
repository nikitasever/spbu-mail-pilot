package ru.local.spbumailpilot;
import javax.mail.*;
import javax.mail.internet.*;
import javax.mail.search.*;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

final class ServerDrafts {
    static final String KEY="X-Spbu-Pilot-Draft",REV="X-Spbu-Pilot-Revision";
    static String revision(JSONObject draft)throws Exception{
        StringBuilder content=new StringBuilder();for(String name:new String[]{"to","cc","bcc","subject","body","inReplyTo","attachments"}){String value=draft.optString(name);content.append(name).append(':').append(value.length()).append(':').append(value);}
        return AppUpdater.hex(MessageDigest.getInstance("SHA-256").digest(content.toString().getBytes(StandardCharsets.UTF_8)));
    }
    static Folder folder(Store store)throws Exception{
        for(Folder candidate:store.getDefaultFolder().list("*"))if((candidate.getType()&Folder.HOLDS_MESSAGES)!=0&&candidate instanceof com.sun.mail.imap.IMAPFolder)for(String attribute:((com.sun.mail.imap.IMAPFolder)candidate).getAttributes())if(attribute.equalsIgnoreCase("\\Drafts"))return candidate;
        for(String name:new String[]{"Drafts","Черновики"}){Folder candidate=store.getFolder(name);if(candidate.exists()&&(candidate.getType()&Folder.HOLDS_MESSAGES)!=0)return candidate;}
        throw new MessagingException("Drafts folder not found");
    }
    static String header(Message message,String name)throws Exception{String[] values=message.getHeader(name);return values==null||values.length==0?null:values[0];}
    static boolean ours(Message message,String key)throws Exception{return message.isSet(Flags.Flag.DRAFT)&&key.equals(header(message,KEY));}
    static JSONObject save(Store store,TransferMessage message,String key,String revision,TransferMessage.Progress progress)throws Exception{
        if(!key.matches("[a-f0-9-]{36}")||!revision.matches("[a-f0-9]{64}"))throw new IllegalArgumentException();
        Folder folder=folder(store);folder.open(Folder.READ_WRITE);
        try{
            Message current=null;Message[] previous=folder.search(new HeaderTerm(KEY,key));if(previous.length>5000)throw new MessagingException("Too many draft revisions");
            for(Message candidate:previous)if(ours(candidate,key)&&revision.equals(header(candidate,REV))&&!candidate.isSet(Flags.Flag.DELETED))current=candidate;
            if(current==null){message.setHeader(KEY,key);message.setHeader(REV,revision);message.setFlag(Flags.Flag.DRAFT,true);message.setFlag(Flags.Flag.SEEN,true);message.saveChanges();message.setHeader("Message-ID","<"+key+"."+revision+"@spbu-pilot.invalid>");message.prepare(progress,false);folder.appendMessages(new Message[]{message});
                Message[] found=folder.search(new AndTerm(new HeaderTerm(KEY,key),new HeaderTerm(REV,revision)));for(Message candidate:found)if(ours(candidate,key)&&revision.equals(header(candidate,REV))&&!candidate.isSet(Flags.Flag.DELETED))current=candidate;
                if(current==null)throw new MessagingException("Draft confirmation missing");
            }
            long uid=((UIDFolder)folder).getUID(current),validity=((UIDFolder)folder).getUIDValidity();if(uid<=0||validity<=0)throw new MessagingException("Draft UID missing");
            for(Message old:previous)if(old!=current&&((UIDFolder)folder).getUID(old)!=uid&&ours(old,key))old.setFlag(Flags.Flag.DELETED,true);
            return new JSONObject().put("folder",folder.getFullName()).put("uid",String.valueOf(uid)).put("validity",String.valueOf(validity)).put("key",key).put("revision",revision);
        }finally{folder.close(false);}
    }
    static final class DeleteRefused extends MessagingException {
        final String reason;
        DeleteRefused(String reason){super(reason);this.reason=reason;}
    }
    interface ScopedExpunge {
        boolean supported()throws Exception;
        void remove(Message target)throws Exception;
    }
    /** Deletes only the exact, account-owned server reference; never uses broad EXPUNGE. */
    static JSONObject removeKnown(Store store,JSONObject ref,String ownedKey)throws Exception{
        validateRemoval(ref,ownedKey);
        Folder drafts=folder(store);
        if(!drafts.getFullName().equals(ref.getString("folder")))throw new DeleteRefused("folder");
        return removeKnown(drafts,ref,ownedKey,new ScopedExpunge(){
            public boolean supported()throws Exception{
                return drafts instanceof com.sun.mail.imap.IMAPFolder && Boolean.TRUE.equals(((com.sun.mail.imap.IMAPFolder)drafts).doCommand(protocol->protocol.hasCapability("UIDPLUS")));
            }
            public void remove(Message target)throws Exception{
                ((com.sun.mail.imap.IMAPFolder)drafts).expunge(new Message[]{target});
            }
        });
    }
    private static void validateRemoval(JSONObject ref,String ownedKey)throws Exception{
        if(ownedKey==null||!ownedKey.matches("[a-f0-9-]{36}")||!ownedKey.equals(ref.optString("key")))throw new DeleteRefused("ownership");
        String name=ref.getString("folder"),uid=ref.getString("uid"),validity=ref.getString("validity");
        if(name.isEmpty()||name.length()>2048||name.matches("(?s).*\\p{Cntrl}.*")||!uid.matches("[0-9]{1,19}")||!validity.matches("[0-9]{1,19}")||Long.parseLong(uid)<=0||Long.parseLong(validity)<=0)throw new IllegalArgumentException("Invalid draft reference");
        if(ref.has("revision")&&!ref.getString("revision").matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Invalid draft revision");
    }
    /** Testable scoped operation; the production adapter only supplies UIDPLUS UID EXPUNGE. */
    static JSONObject removeKnown(Folder drafts,JSONObject ref,String ownedKey,ScopedExpunge expunge)throws Exception{
        validateRemoval(ref,ownedKey);
        if(!drafts.getFullName().equals(ref.getString("folder"))||!(drafts instanceof UIDFolder))throw new DeleteRefused("folder");
        drafts.open(Folder.READ_WRITE);
        try{
            UIDFolder uids=(UIDFolder)drafts;
            if(uids.getUIDValidity()!=Long.parseLong(ref.getString("validity")))throw new DeleteRefused("validity");
            long uid=Long.parseLong(ref.getString("uid"));
            Message target=uids.getMessageByUID(uid);
            if(target==null||target.isExpunged())return new JSONObject().put("deleted",true).put("alreadyMissing",true);
            if(uids.getUID(target)!=uid)throw new DeleteRefused("ownership");
            if(!ours(target,ownedKey)||(ref.has("revision")&&!ref.getString("revision").equals(header(target,REV))))throw new DeleteRefused("ownership");
            if(!expunge.supported())throw new DeleteRefused("uidplus");
            target.setFlag(Flags.Flag.DELETED,true);
            expunge.remove(target);
            return new JSONObject().put("deleted",true);
        }finally{if(drafts.isOpen())drafts.close(false);}
    }
    static void remove(Store store,String key)throws Exception{
        if(key==null||!key.matches("[a-f0-9-]{36}"))return;Folder folder=folder(store);folder.open(Folder.READ_WRITE);try{Message[] candidates=folder.search(new HeaderTerm(KEY,key));if(candidates.length>5000)return;for(Message candidate:candidates)if(ours(candidate,key))candidate.setFlag(Flags.Flag.DELETED,true);}finally{folder.close(false);}
    }
}

