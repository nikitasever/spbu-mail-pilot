package ru.local.spbumailpilot;
import java.io.*;
/** Interrupted snapshot creation may leave private files before its database row is committed. */
final class ScheduledFiles {
    static void verify(File directory,org.json.JSONArray entries)throws Exception{
        if(entries==null)return;for(int i=0;i<entries.length();i++){org.json.JSONObject entry=entries.getJSONObject(i);if(MailSender.file(directory,entry.getString("token")).length()!=entry.getLong("size"))throw new IllegalArgumentException("Scheduled attachment incomplete");}
    }
    interface Owned {boolean contains(String account,String id)throws Exception;}
    static void cleanup(File root,Owned owned)throws Exception{
        File[] accounts=root.listFiles();if(accounts==null)return;
        for(File account:accounts){if(!account.isDirectory()||!account.getName().matches("account-[0-9a-f]{64}")||!root.getCanonicalFile().equals(account.getCanonicalFile().getParentFile())||!account.getName().equals(account.getCanonicalFile().getName()))continue;
            File[] items=account.listFiles();if(items==null)continue;
            for(File item:items){if(!item.isDirectory()||!item.getName().matches("[0-9a-f-]{36}")||!account.getCanonicalFile().equals(item.getCanonicalFile().getParentFile())||!item.getName().equals(item.getCanonicalFile().getName())||owned.contains(account.getName(),item.getName()))continue;
                File[] files=item.listFiles();if(files!=null)for(File file:files)if(file.isFile()&&item.getCanonicalFile().equals(file.getCanonicalFile().getParentFile()))file.delete();item.delete();
            }
        }
    }
}
