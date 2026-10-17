package ru.local.spbumailpilot;
import org.json.*;
import java.util.*;
/** Filename-search cursor counts scanned messages, independently of matched file rows. */
final class NativeFilePage {
    static final int LIMIT=5000;
    final String folder,validity,query,snapshot;
    final int next,total;final boolean more;final JSONArray files;
    private NativeFilePage(JSONObject page,NativeFilePage previous)throws Exception{
        folder=page.getString("folder");validity=page.getString("validity");query=NativeMailSearch.query(page.getString("query"));snapshot=page.getString("snapshot");
        next=page.getInt("nextOffset");total=page.getInt("total");more=page.getBoolean("hasMore");int before=previous==null?0:previous.next;
        NativeServerDraftCopyPlan.ref(new JSONObject().put("folder",folder).put("uid","1").put("validity",validity));
        if(total<0||next<before||next>total||next-before>50||more!=(next<total)||more&&next==before||snapshot.isEmpty()||snapshot.length()>200)throw new IllegalArgumentException("File cursor");
        if(previous!=null&&(!previous.more||!folder.equals(previous.folder)||!validity.equals(previous.validity)||!query.equals(previous.query)||!snapshot.equals(previous.snapshot)||total!=previous.total))throw new IllegalArgumentException("File snapshot changed");
        JSONArray incoming=page.getJSONArray("files");if(incoming.length()>LIMIT||next==before&&incoming.length()>0)throw new IllegalArgumentException("File page limit");
        LinkedHashMap<String,JSONObject> unique=new LinkedHashMap<>();
        if(previous!=null)for(int i=0;i<previous.files.length();i++){JSONObject f=previous.files.getJSONObject(i);unique.put(key(f),new JSONObject(f.toString()));}
        Set<String> uids=new HashSet<>();
        for(int i=0;i<incoming.length();i++){
            JSONObject f=incoming.getJSONObject(i),message=f.getJSONObject("message");
            NativeServerDraftCopyPlan.ref(new JSONObject().put("folder",folder).put("uid",message.get("uid")).put("validity",message.get("validity")));
            if(!validity.equals(message.getString("validity"))||!folder.equals(message.optString("folder",folder)))throw new IllegalArgumentException("File message identity");
            NativeServerDraftCopyPlan.files(new JSONObject().put("attachments",new JSONArray().put(f)));
            if(f.getLong("size") < -1)throw new IllegalArgumentException("File size");uids.add(message.getString("uid"));
            JSONObject clean=new JSONObject().put("uid",message.getString("uid")).put("validity",validity).put("folder",folder);
            for(String field:new String[]{"subject","sender","email","date"})clean.put(field,message.optString(field));
            JSONObject row=new JSONObject().put("name",f.getString("name")).put("path",f.getString("path")).put("size",f.getLong("size")).put("message",clean);
            String key=key(row);if(!unique.containsKey(key))unique.put(key,row);
            if(unique.size()>LIMIT)throw new IllegalArgumentException("File list limit: refine search");
        }
        if(uids.size()>next-before)throw new IllegalArgumentException("File UID count");files=new JSONArray();for(JSONObject f:unique.values())files.put(f);
    }
    static String key(JSONObject f)throws Exception{return new JSONArray().put(f.getJSONObject("message").getString("uid")).put(f.getString("path")).toString();}
    static NativeFilePage first(JSONObject page,String folder,String query)throws Exception{NativeFilePage result=new NativeFilePage(page,null);if(!folder.equals(result.folder)||!query.equals(result.query))throw new IllegalArgumentException("Unexpected file scope");return result;}
    NativeFilePage append(JSONObject page)throws Exception{return new NativeFilePage(page,this);}
}
