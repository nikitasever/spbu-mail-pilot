package ru.local.spbumailpilot;
import java.time.Instant;
import org.json.*;
/** Application dial intents only; never reads Android call logs or claims a completed call. */
final class CallHistory {
    static final int LIMIT=50;
    static JSONArray rows(JSONObject state)throws Exception{
        Object raw=state.opt("callHistory");if(raw==null)return new JSONArray();
        if(!(raw instanceof JSONArray)||((JSONArray)raw).length()>5000)throw new IllegalArgumentException("Invalid call history");
        JSONArray result=new JSONArray(),all=(JSONArray)raw;
        for(int i=0;i<all.length()&&result.length()<LIMIT;i++){
            JSONObject row=all.optJSONObject(i);if(row==null||!(row.opt("number") instanceof String)||!(row.opt("at") instanceof String))continue;
            try{result.put(new JSONObject().put("number",PhoneNumbers.normalize(row.getString("number"))).put("at",Instant.parse(row.getString("at")).toString()));}
            catch(IllegalArgumentException|java.time.format.DateTimeParseException ignored){}
        }
        return result;
    }
    static JSONObject append(JSONObject current,String number,Instant at)throws Exception{
        String normalized=PhoneNumbers.normalize(number);if(at==null)throw new IllegalArgumentException("Missing time");
        JSONArray old=rows(current),next=new JSONArray().put(new JSONObject().put("number",normalized).put("at",at.toString()));
        for(int i=0;i<old.length()&&next.length()<LIMIT;i++)next.put(old.getJSONObject(i));
        return new JSONObject(current.toString()).put("callHistory",next);
    }
}
