package ru.local.spbumailpilot;
import org.json.*;
public class MailFolderCatalogTest {
    static void check(boolean v){if(!v)throw new AssertionError();}
    static JSONObject page(String name)throws Exception{return new JSONObject().put("folder",name).put("validity","7").put("messages",new JSONArray());}
    public static void main(String[] args)throws Exception{
        OfflineMailbox old=new OfflineMailbox();old.page("INBOX",0,page("INBOX"));check(old.folders().length()==1);
        JSONArray names=new JSONArray().put("inbox").put("Sent").put("Drafts").put("Архив/2026").put("Projects").put("projects");
        old.rememberFolders(names);names.put("later");OfflineMailbox restored=new OfflineMailbox(old.data);
        check(restored.folders().length()==6);check(restored.folders().getString(0).equals("INBOX"));
        check(restored.page("Sent",0,"","")==null); // A known folder does not invent downloaded mail.
        restored.page("Sent",0,page("Sent"));restored.rememberFolders(new JSONArray().put("INBOX").put("Drafts"));
        check(restored.folders().length()==2);check(!restored.folders().toString().contains("Sent"));
        JSONArray detached=restored.folders();detached.put("mutated");check(restored.folders().length()==2);
        for(JSONArray bad:new JSONArray[]{new JSONArray().put(42),new JSONArray().put("bad\nname"),new JSONArray().put("")}){
            try{restored.rememberFolders(bad);throw new AssertionError();}catch(IllegalArgumentException expected){}
            check(restored.folders().length()==2);
        }
        JSONArray huge=new JSONArray();for(int i=0;i<2001;i++)huge.put("folder"+i);
        try{MailFolderCatalog.copy(huge);throw new AssertionError();}catch(IllegalArgumentException expected){}
        check(!new OfflineMailbox().data.has("folderCatalog"));
        System.out.println("PASS folder catalog: full LIST survives restart without cached Sent/Drafts mail; names/case/hierarchy preserved, replacement removes stale folders, bounded defensive snapshots, legacy cache supported.");
    }
}
