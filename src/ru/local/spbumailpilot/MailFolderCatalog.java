package ru.local.spbumailpilot;
import org.json.*;
import java.util.*;

/** Server names only; a catalog does not assert that messages are cached. */
final class MailFolderCatalog {
    static JSONArray copy(JSONArray input)throws Exception {
        if(input==null||input.length()>2000)throw new IllegalArgumentException("Folder catalog size");
        LinkedHashSet<String> names=new LinkedHashSet<>();
        for(int i=0;i<input.length();i++){
            Object raw=input.opt(i);
            if(!(raw instanceof String))throw new IllegalArgumentException("Folder catalog entry");
            String name=(String)raw;
            if(name.isEmpty()||name.length()>1024||name.matches("(?s).*[\\p{Cntrl}].*"))throw new IllegalArgumentException("Folder catalog name");
            names.add(name.equalsIgnoreCase("INBOX")?"INBOX":name);
        }
        JSONArray result=new JSONArray();for(String name:names)result.put(name);
        if(result.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>256*1024)throw new IllegalArgumentException("Folder catalog bytes");
        return result;
    }
}
