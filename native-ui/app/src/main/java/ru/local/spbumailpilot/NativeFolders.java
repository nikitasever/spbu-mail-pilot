package ru.local.spbumailpilot;

import org.json.JSONArray;
import java.util.*;

/** Actual IMAP names remain the identity; only INBOX is case-insensitive. */
final class NativeFolders {
    static final class Entry {
        final String name,title;
        Entry(String name){this.name=name;this.title=label(name);}
    }
    static boolean valid(String name){return name!=null&&!name.isEmpty()&&name.length()<=1024&&!name.matches("(?s).*[\\p{Cntrl}].*");}
    static String canonical(String name){if(!valid(name))throw new IllegalArgumentException("Invalid folder");return name.equalsIgnoreCase("INBOX")?"INBOX":name;}
    static String restore(String owner,String savedOwner,String savedFolder,String readerFolder){
        if(owner==null||!owner.equals(savedOwner))return "INBOX";
        return valid(savedFolder)?canonical(savedFolder):valid(readerFolder)?canonical(readerFolder):"INBOX";
    }
    static List<Entry> list(JSONArray names,String current)throws Exception {
        if(names==null||names.length()>2000)throw new IllegalArgumentException("Invalid folder list");
        LinkedHashSet<String> unique=new LinkedHashSet<>();unique.add("INBOX");unique.add(canonical(current));
        for(int i=0;i<names.length();i++){
            if(!(names.opt(i) instanceof String))throw new IllegalArgumentException("Invalid folder entry");
            unique.add(canonical(names.getString(i)));
        }
        List<String> sorted=new ArrayList<>(unique);
        sorted.sort((a,b)->a.equals(b)?0:a.equals("INBOX")?-1:b.equals("INBOX")?1:a.compareToIgnoreCase(b)!=0?a.compareToIgnoreCase(b):a.compareTo(b));
        List<Entry> result=new ArrayList<>();for(String name:sorted)result.add(new Entry(name));return result;
    }
    static String label(String name){
        switch(name.toLowerCase(Locale.ROOT)){
            case "inbox":return "Входящие";
            case "sent":case "sent items":case "sent messages":return "Отправленные";
            case "drafts":case "черновики":return "Черновики на сервере";
            case "spam":case "junk":case "junk e-mail":return "Спам";
            case "trash":case "deleted items":return "Корзина";
            case "archive":case "archives":return "Архив";
            default:return name;
        }
    }
}
