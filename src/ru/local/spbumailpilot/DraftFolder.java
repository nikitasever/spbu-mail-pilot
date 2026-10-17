package ru.local.spbumailpilot;
import javax.mail.*;
/** Shared protocol-only discovery for both readers and writers. */
final class DraftFolder {
    static Folder find(Store store)throws Exception{
        for(Folder candidate:store.getDefaultFolder().list("*"))if((candidate.getType()&Folder.HOLDS_MESSAGES)!=0&&candidate instanceof com.sun.mail.imap.IMAPFolder)for(String attribute:((com.sun.mail.imap.IMAPFolder)candidate).getAttributes())if(attribute.equalsIgnoreCase("\\Drafts"))return candidate;
        for(String name:new String[]{"Drafts","Черновики"}){Folder candidate=store.getFolder(name);if(candidate.exists()&&(candidate.getType()&Folder.HOLDS_MESSAGES)!=0)return candidate;}
        throw new MessagingException("Drafts folder not found");
    }
}
