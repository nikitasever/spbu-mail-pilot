package ru.local.spbumailpilot;
import java.util.*;
import org.json.*;
/** Bounded provider metadata; joins every email with the first nonblank phone of its contact. */
final class NativeSystemContactsBatch {
    static final int MAX_ROWS=25000;
    private int rows;
    private final List<JSONObject> emails=new ArrayList<>();
    private final Map<String,String> phones=new HashMap<>();
    void add(String id,String kind,String name,String value)throws Exception{
        if(++rows>MAX_ROWS)throw new IllegalArgumentException("Системная книга слишком велика. Подготовьте отдельный текстовый список для импорта.");
        if(id==null||id.isEmpty()||id.length()>100)throw new IllegalArgumentException("Не удалось прочитать идентификатор системного контакта.");
        String text=value==null?"":value.trim();
        if("email".equals(kind)){
            if(emails.size()>=NativeContactImport.MAX_CONTACTS)throw new IllegalArgumentException("В системной книге больше 5000 адресов. Импортируйте выбранный список файлом.");
            emails.add(new JSONObject().put("contact",id).put("email",text).put("sender",name==null?"":name));
        }else if("phone".equals(kind)&&!text.isEmpty())phones.putIfAbsent(id,text);
    }
    List<JSONObject> entries()throws Exception{
        List<JSONObject> result=new ArrayList<>();
        for(JSONObject entry:emails)result.add(new JSONObject().put("email",entry.getString("email")).put("sender",entry.getString("sender")).put("number",phones.getOrDefault(entry.getString("contact"),"")));
        return result;
    }
}
