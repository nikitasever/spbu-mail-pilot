package ru.local.spbumailpilot;
import java.util.*;
import org.json.*;

/** Three-way merge for a UI snapshot. Unchanged client fields never undo another writer. */
final class SharedStateMerge {
    static final class Conflict extends Exception {Conflict(){super("State changed in another editor");}}
    static boolean same(Object a,Object b)throws Exception {
        if(a==b)return true;if(a==null||b==null)return false;
        if(a instanceof JSONObject&&b instanceof JSONObject){JSONObject x=(JSONObject)a,y=(JSONObject)b;if(x.length()!=y.length())return false;Iterator<String> keys=x.keys();while(keys.hasNext()){String k=keys.next();if(!y.has(k)||!same(x.get(k),y.get(k)))return false;}return true;}
        if(a instanceof JSONArray&&b instanceof JSONArray){JSONArray x=(JSONArray)a,y=(JSONArray)b;if(x.length()!=y.length())return false;for(int i=0;i<x.length();i++)if(!same(x.get(i),y.get(i)))return false;return true;}
        return a.equals(b);
    }
    static JSONObject merge(JSONObject base,JSONObject incoming,JSONObject current)throws Exception {
        JSONObject result=new JSONObject(current.toString());Set<String> keys=new HashSet<>();for(JSONObject o:new JSONObject[]{base,incoming}){Iterator<String> it=o.keys();while(it.hasNext())keys.add(it.next());}
        for(String k:keys){Object old=base.opt(k),next=incoming.opt(k),now=current.opt(k);if(same(old,next))continue;
            if(same(now,old)||same(now,next)){if(incoming.has(k))result.put(k,next);else result.remove(k);}
            else if(old instanceof JSONObject&&next instanceof JSONObject&&now instanceof JSONObject)result.put(k,merge((JSONObject)old,(JSONObject)next,(JSONObject)now));
            else throw new Conflict();
        }
        return result;
    }
}
