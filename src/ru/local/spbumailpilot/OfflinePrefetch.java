package ru.local.spbumailpilot;
import org.json.*;import java.util.*;
/** First50 headers, reversed so newest texts survive the bounded encrypted cache. */
final class OfflinePrefetch {
    static List<JSONObject> refs(String folder,JSONObject page)throws Exception{ArrayList<JSONObject> result=new ArrayList<>();String validity=page.getString("validity");if(!validity.matches("[1-9][0-9]{0,19}")||folder==null||folder.isEmpty()||folder.length()>2048||folder.matches("(?s).*\\p{Cntrl}.*"))throw new IllegalArgumentException();JSONArray rows=page.getJSONArray("messages");Set<String> seen=new HashSet<>();for(int i=0;i<Math.min(50,rows.length());i++){JSONObject row=rows.optJSONObject(i);if(row==null)continue;String uid=row.optString("uid");if(!uid.matches("[1-9][0-9]{0,19}")||!validity.equals(row.optString("validity"))||!folder.equals(row.optString("folder",folder))||!seen.add(uid))continue;result.add(new JSONObject().put("folder",folder).put("uid",uid).put("validity",validity));}Collections.reverse(result);return result;}
}
