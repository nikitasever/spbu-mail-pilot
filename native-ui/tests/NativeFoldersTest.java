package ru.local.spbumailpilot;

import org.json.*;
import java.util.*;
import javax.mail.*;

public final class NativeFoldersTest {
    interface Check{void run()throws Exception;}
    static void check(boolean value){if(!value)throw new AssertionError();}
    static void reject(Check action)throws Exception{try{action.run();}catch(Exception expected){return;}throw new AssertionError("Expected refusal");}
    static final class NamedFolder extends ReadFlagTest.MemoryFolder {
        final String name;final int type;Folder[] children=new Folder[0];
        NamedFolder(Store store,Session session,String name,int type)throws Exception{super(store,session);this.name=name;this.type=type;}
        public String getName(){return name;}public String getFullName(){return name;}public int getType(){return type;}
        public Folder[] list(String pattern){return children;}
    }
    static final class FolderStore extends ReadFlagTest.MemoryStore {
        final NamedFolder root;
        FolderStore(Session session)throws Exception{
            super(session);root=new NamedFolder(this,session,"",Folder.HOLDS_FOLDERS);
            root.children=new Folder[]{new NamedFolder(this,session,"Sent",Folder.HOLDS_MESSAGES),new NamedFolder(this,session,"Projects",Folder.HOLDS_FOLDERS),
                new NamedFolder(this,session,"INBOX",Folder.HOLDS_MESSAGES),new NamedFolder(this,session,"Проекты/2026",Folder.HOLDS_MESSAGES|Folder.HOLDS_FOLDERS)};
        }
        public Folder getDefaultFolder(){return root;}
    }
    public static void main(String[] args)throws Exception {
        JSONArray names=new JSONArray().put("Sent").put("inbox").put("INBOX").put("Drafts").put("Проекты/2026").put("Projects").put("projects").put("Sent");
        String before=names.toString();List<NativeFolders.Entry> entries=NativeFolders.list(names,"Archive");
        check(entries.get(0).name.equals("INBOX")&&entries.get(0).title.equals("Входящие"));
        check(entries.size()==7&&names.toString().equals(before));
        check(entries.stream().anyMatch(e->e.name.equals("Projects"))&&entries.stream().anyMatch(e->e.name.equals("projects")));
        check(entries.stream().anyMatch(e->e.name.equals("Проекты/2026")&&e.title.equals("Проекты/2026")));
        check(NativeFolders.label("Drafts").equals("Черновики на сервере")&&NativeFolders.label("Черновики").equals("Черновики на сервере"));
        check(NativeFolders.label("Team/Sent").equals("Team/Sent"));
        check(NativeFolders.list(new JSONArray(),"Archive").size()==2);
        check(NativeFolders.restore("a","a","Sent","INBOX").equals("Sent"));
        check(NativeFolders.restore("b","a","Sent","Sent").equals("INBOX"));
        check(NativeFolders.restore("a","a",null,"Проекты/2026").equals("Проекты/2026"));
        check(NativeFolders.restore("a","a","","bad\nfolder").equals("INBOX"));
        for(String value:new String[]{"","bad\nfolder","bad\u0000folder",String.join("",Collections.nCopies(1025,"x"))}) {
            check(!NativeFolders.valid(value));reject(()->NativeFolders.list(new JSONArray().put(value),"INBOX"));
        }
        reject(()->NativeFolders.list(new JSONArray().put(42),"INBOX"));
        JSONArray huge=new JSONArray();for(int i=0;i<2001;i++)huge.put("folder-"+i);reject(()->NativeFolders.list(huge,"INBOX"));
        JSONObject row=new JSONObject().put("uid","42").put("validity","7").put("folder","Archive");
        JSONObject page=new JSONObject().put("folder","Archive").put("validity","7").put("snapshot","one").put("nextOffset",1).put("total",1).put("hasMore",false).put("messages",new JSONArray().put(row));
        check(NativeMailPage.first(page,"Archive").folder.equals("Archive"));
        reject(()->NativeMailPage.first(page,"INBOX"));
        FolderStore store=new FolderStore(Session.getInstance(new Properties()));MailReader reader=new MailReader();
        java.lang.reflect.Field field=MailReader.class.getDeclaredField("store");field.setAccessible(true);field.set(reader,store);
        JSONArray server=reader.folders();check(server.length()==3&&server.getString(0).equals("INBOX"));
        check(!server.toString().contains("\"Projects\""));check(server.toString().contains("Проекты/2026"));
        reader.close();
        System.out.println("PASS native folders: INBOX ordering/canonicalization only, distinct case-sensitive folders and Unicode hierarchy, server draft labels, malformed/oversize lists refused without truncation, account-scoped restoration and initial-page folder guard, real LIST fixture excludes non-selectable containers; no network.");
    }
}
