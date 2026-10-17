package ru.local.spbumailpilot;
import org.json.*;
import java.util.LinkedHashMap;

/** A cursor belongs to one folder/UIDVALIDITY/snapshot. Offset follows consumed UIDs, not surviving rows. */
final class NativeMailPage {
    final String folder,validity,snapshot;
    final int next,total;
    final boolean more;
    final JSONArray messages;
    private NativeMailPage(JSONObject page,NativeMailPage previous)throws Exception{
        folder=page.getString("folder");validity=page.getString("validity");snapshot=page.getString("snapshot");
        next=page.getInt("nextOffset");total=page.getInt("total");more=page.getBoolean("hasMore");
        int offset=previous==null?0:previous.next;
        if(!validity.matches("[1-9][0-9]{0,19}")||folder.isEmpty()||snapshot.isEmpty()||snapshot.length()>200||next<offset||next>total||more!=(next<total)||more&&next==offset)
            throw new IllegalArgumentException("Invalid page cursor");
        if(previous!=null&&(!previous.more||!folder.equals(previous.folder)||!validity.equals(previous.validity)||!snapshot.equals(previous.snapshot)||total!=previous.total))throw new IllegalArgumentException("Page snapshot changed");
        JSONArray incoming=page.getJSONArray("messages");if(incoming.length()>50||incoming.length()>next-offset)throw new IllegalArgumentException("Page size");
        LinkedHashMap<String,JSONObject> unique=new LinkedHashMap<>();
        if(previous!=null)for(int i=0;i<previous.messages.length();i++){JSONObject m=previous.messages.getJSONObject(i);unique.put(m.getString("uid"),m);}
        for(int i=0;i<incoming.length();i++){
            JSONObject m=incoming.getJSONObject(i);String uid=m.getString("uid");
            if(!uid.matches("[1-9][0-9]{0,19}")||!validity.equals(m.getString("validity"))||!folder.equals(m.optString("folder",folder)))throw new IllegalArgumentException("Message identity changed");
            if(!unique.containsKey(uid))unique.put(uid,new JSONObject(m.toString()));
        }
        messages=new JSONArray();for(JSONObject m:unique.values())messages.put(m);
    }
    static NativeMailPage first(JSONObject page)throws Exception{return new NativeMailPage(page,null);}
    static NativeMailPage first(JSONObject page,String expectedFolder)throws Exception{
        NativeMailPage result=first(page);
        if(!expectedFolder.equals(result.folder))throw new IllegalArgumentException("Unexpected folder");
        return result;
    }
    NativeMailPage append(JSONObject page)throws Exception{return new NativeMailPage(page,this);}
}
