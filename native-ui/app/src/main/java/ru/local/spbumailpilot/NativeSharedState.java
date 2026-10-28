package ru.local.spbumailpilot;
import org.json.*;
/** Preserve the minimum legacy loadState shape when saving the first native-only setting. */
final class NativeSharedState {
    static JSONObject ready(JSONObject next)throws Exception {
        for(String key:new String[]{"tasks","notes","events"}){
            if(!next.has(key))next.put(key,new JSONArray());else if(!(next.opt(key) instanceof JSONArray))throw new IllegalArgumentException("Malformed collection");
        }
        return next;
    }
}
