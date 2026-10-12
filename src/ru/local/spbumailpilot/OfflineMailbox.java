package ru.local.spbumailpilot;
import java.util.*;
import java.math.BigInteger;
import org.json.*;

/** Bounded copies of explicitly loaded mail. No network calls or server mutations. */
final class OfflineMailbox {
    final JSONObject data;
    OfflineMailbox()throws Exception{data=new JSONObject().put("schema",1).put("folders",new JSONObject()).put("bodies",new JSONObject());}
    OfflineMailbox(JSONObject saved)throws Exception{if(saved.getInt("schema")!=1)throw new IllegalArgumentException("Cache schema");data=new JSONObject(saved.toString());data.getJSONObject("folders");data.getJSONObject("bodies");ArrayList<String> keys=new ArrayList<>();Iterator<String> names=data.getJSONObject("bodies").keys();while(names.hasNext())keys.add(names.next());for(String key:keys)try{JSONObject body=data.getJSONObject("bodies").optJSONObject(key),message=body==null?null:body.optJSONObject("message");JSONArray ref=new JSONArray(key);if(message!=null&&ref.getString(0).equals(message.optString("folder"))&&ref.getString(1).equals(message.optString("validity"))&&ref.getString(2).equals(message.optString("uid")))indexBody(message,body.optLong("cachedAt"));}catch(Exception malformed){} }
    static JSONObject copy(JSONObject value)throws Exception{return new JSONObject(value.toString());}
    static String key(String folder,String validity,String uid){return new JSONArray().put(folder).put(validity).put(uid).toString();}
    boolean available(){return data.optJSONObject("folders").length()>0;}
    JSONArray folders(){JSONArray result=new JSONArray();Iterator<String> names=data.optJSONObject("folders").keys();while(names.hasNext())result.put(names.next());return result;}
    void page(String folder,int offset,JSONObject result)throws Exception{page(folder,offset,result,System.currentTimeMillis());}
    private void page(String folder,int offset,JSONObject result,long cachedAt)throws Exception{
        JSONArray incoming=result.getJSONArray("messages");String validity=result.optString("validity");if(validity.isEmpty()&&incoming.length()>0)validity=incoming.getJSONObject(0).getString("validity");if(!validity.matches("[1-9][0-9]{0,19}"))return;
        JSONObject all=data.getJSONObject("folders"),previous=all.optJSONObject(folder),bodies=data.getJSONObject("bodies");boolean changed=previous!=null&&!previous.optString("validity").equals(validity);
        if(changed){ArrayList<String> stale=new ArrayList<>();Iterator<String> keys=bodies.keys();while(keys.hasNext()){String key=keys.next();if(new JSONArray(key).getString(0).equals(folder))stale.add(key);}for(String key:stale)bodies.remove(key);}
        JSONObject entries=new JSONObject();if(offset>0&&previous!=null&&!changed)entries=copy(previous.getJSONObject("messages"));
        for(int i=0;i<incoming.length();i++){JSONObject item=incoming.getJSONObject(i);String uid=item.getString("uid");if(validity.equals(item.optString("validity"))&&uid.matches("[1-9][0-9]{0,19}")&&item.toString().length()<=32768)entries.put(uid,copy(item));}
        ArrayList<String> ids=ids(entries);for(int i=200;i<ids.size();i++)entries.remove(ids.get(i));
        all.put(folder,new JSONObject().put("validity",validity).put("messages",entries).put("at",cachedAt).put("revision",previous==null?1:previous.optLong("revision")+1));
        while(all.length()>10){String oldest=null;long at=Long.MAX_VALUE;Iterator<String> keys=all.keys();while(keys.hasNext()){String name=keys.next();if(all.getJSONObject(name).getLong("at")<at){oldest=name;at=all.getJSONObject(name).getLong("at");}}if(oldest==null)break;all.remove(oldest);}
        trim();
    }
    static ArrayList<String> ids(JSONObject items){ArrayList<String> keys=new ArrayList<>();Iterator<String> iterator=items.keys();while(iterator.hasNext())keys.add(iterator.next());Collections.sort(keys,(a,b)->new BigInteger(b).compareTo(new BigInteger(a)));return keys;}
    JSONObject page(String folder,int offset,String snapshot,String query)throws Exception{
        JSONObject record=data.getJSONObject("folders").optJSONObject(folder);if(record==null)return null;String token="offline:"+record.getLong("revision")+":"+record.getString("validity");if(offset>0&&!token.equals(snapshot))return null;
        JSONObject items=record.getJSONObject("messages");ArrayList<String> matching=new ArrayList<>();String needle=query.toLowerCase(Locale.ROOT);for(String uid:ids(items)){JSONObject item=items.getJSONObject(uid);if((item.optString("sender")+" "+item.optString("email")+" "+item.optString("subject")).toLowerCase(Locale.ROOT).contains(needle))matching.add(uid);}
        int end=Math.min(matching.size(),offset+50);JSONArray messages=new JSONArray();for(int i=offset;i<end;i++)messages.put(copy(items.getJSONObject(matching.get(i))));
        return new JSONObject().put("folder",folder).put("validity",record.getString("validity")).put("messages",messages).put("total",matching.size()).put("nextOffset",end).put("hasMore",end<matching.size()).put("snapshot",token).put("offline",true).put("cachedAt",record.getLong("at"));
    }
    private void indexBody(JSONObject message,long cachedAt)throws Exception{
        String folder=message.optString("folder"),uid=message.optString("uid"),validity=message.optString("validity");
        if(folder.isEmpty()||!uid.matches("[1-9][0-9]{0,19}")||!validity.matches("[1-9][0-9]{0,19}"))return;
        JSONObject record=data.getJSONObject("folders").optJSONObject(folder);
        if(record!=null&&(!validity.equals(record.optString("validity"))||record.getJSONObject("messages").has(uid)))return;
        long at=Math.max(cachedAt,record==null?0:record.optLong("at"));
        page(folder,record==null?0:1,new JSONObject().put("validity",validity).put("messages",new JSONArray().put(message)),at);
    }
    void body(String folder,String uid,String validity,JSONObject result)throws Exception{
        JSONObject message=result.optJSONObject("message");if(message==null||!uid.equals(message.optString("uid"))||!validity.equals(message.optString("validity"))||!folder.equals(message.optString("folder")))return;
        JSONObject record=data.getJSONObject("folders").optJSONObject(folder);if(record!=null&&!validity.equals(record.optString("validity")))return;
        JSONObject copy=copy(result);copy.put("cachedAt",System.currentTimeMillis());JSONObject bodies=data.getJSONObject("bodies");bodies.put(key(folder,validity,uid),copy);indexBody(message,copy.getLong("cachedAt"));trim();
    }
    JSONObject body(String folder,String uid,String validity)throws Exception{JSONObject record=data.getJSONObject("folders").optJSONObject(folder);if(record!=null&&!validity.equals(record.optString("validity")))return null;JSONObject body=data.getJSONObject("bodies").optJSONObject(key(folder,validity,uid));return body==null?null:copy(body).put("offline",true);}
    void seen(String folder,String uid,String validity,boolean seen)throws Exception{JSONObject record=data.getJSONObject("folders").optJSONObject(folder);if(record!=null&&validity.equals(record.optString("validity"))){JSONObject message=record.getJSONObject("messages").optJSONObject(uid);if(message!=null)message.put("seen",seen);}JSONObject body=data.getJSONObject("bodies").optJSONObject(key(folder,validity,uid));if(body!=null&&body.optJSONObject("message")!=null)body.getJSONObject("message").put("seen",seen);}
    void trim()throws Exception{
        JSONObject bodies=data.getJSONObject("bodies");while(bodies.length()>50||data.toString().getBytes("UTF-8").length>4*1024*1024){String oldest=null;long at=Long.MAX_VALUE;Iterator<String> keys=bodies.keys();while(keys.hasNext()){String key=keys.next();long time=bodies.getJSONObject(key).optLong("cachedAt");if(time<at){at=time;oldest=key;}}if(oldest==null)break;bodies.remove(oldest);}
        JSONObject folders=data.getJSONObject("folders");while(data.toString().getBytes("UTF-8").length>4*1024*1024&&folders.length()>0){String oldest=null;long at=Long.MAX_VALUE;Iterator<String> names=folders.keys();while(names.hasNext()){String name=names.next();long time=folders.getJSONObject(name).optLong("at");if(time<at){at=time;oldest=name;}}if(oldest==null)break;folders.remove(oldest);}

    }
}
