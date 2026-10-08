package ru.local.spbumailpilot;

import org.json.*;
import java.util.HashSet;

final class DigestBatch {
    static JSONArray references(String json)throws Exception{
        if(json==null||json.length()>8192)throw new IllegalArgumentException("Invalid batch");
        JSONArray refs=new JSONArray(json);if(refs.length()<1||refs.length()>20)throw new IllegalArgumentException("Batch limit");
        HashSet<String> seen=new HashSet<>();
        for(int i=0;i<refs.length();i++){
            JSONObject ref=refs.getJSONObject(i);String uid=ref.getString("uid"),validity=ref.getString("validity");
            if(!uid.matches("[0-9]{1,20}")||!validity.matches("[0-9]{1,20}")||!seen.add(validity+":"+uid))throw new IllegalArgumentException("Invalid reference");
        }
        return refs;
    }
    static String cut(String text,int max){if(text==null)return "";if(text.length()<=max)return text;int end=max;if(Character.isHighSurrogate(text.charAt(end-1)))end--;return text.substring(0,end);}
    static JSONObject item(String folder,JSONObject ref,JSONObject body)throws Exception{
        JSONObject message=body.getJSONObject("message"),header=new JSONObject().put("folder",folder).put("uid",ref.getString("uid")).put("validity",ref.getString("validity"));
        for(String key:new String[]{"subject","sender","email","date"})header.put(key,cut(message.optString(key),key.equals("subject")?500:320));
        header.put("seen",message.optBoolean("seen"));String raw=body.optString("text");
        return new JSONObject().put("message",header).put("text",cut(raw,4000)).put("truncated",raw.length()>4000);
    }
}
