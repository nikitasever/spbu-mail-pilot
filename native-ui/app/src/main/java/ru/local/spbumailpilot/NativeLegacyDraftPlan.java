package ru.local.spbumailpilot;
import org.json.*;
import java.util.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/** Explicit copy of legacy content. Source snapshots and server identities are never rewritten. */
final class NativeLegacyDraftPlan {
    static String sourceId(JSONObject source)throws Exception{
        Object id=source.get("id");if(!(id instanceof String)||!((String)id).matches("[A-Za-z0-9_-]{1,100}"))throw new IllegalArgumentException("Legacy draft id");return (String)id;
    }
    static JSONArray files(JSONObject source)throws Exception{
        JSONArray result=source.has("attachments")?source.getJSONArray("attachments"):new JSONArray();
        if(result.length()>10)throw new IllegalArgumentException("Too many files");Set<String> tokens=new HashSet<>();
        for(int i=0;i<result.length();i++){
            JSONObject entry=result.getJSONObject(i);String token=entry.getString("token"),name=entry.getString("name");Object size=entry.get("size");
            if(token.isEmpty()||token.length()>240||token.matches("(?s).*[\\p{Cntrl}/\\\\].*")||token.equals(".")||token.equals("..")||!tokens.add(token))throw new IllegalArgumentException("File token");
            if(!name.equals(AttachmentFiles.safeName(name))||!(size instanceof Number)||!size.toString().matches("[0-9]+")||entry.getLong("size")>AttachmentFiles.MAX_BYTES)throw new IllegalArgumentException("File metadata");
        }
        return new JSONArray(result.toString());
    }
    static String fingerprint(JSONObject source)throws Exception{
        JSONObject content=new JSONObject().put("id",sourceId(source));
        for(String name:new String[]{"to","cc","bcc","subject","body","inReplyTo","kind"}){
            if(source.has(name)&&!(source.get(name) instanceof String))throw new IllegalArgumentException("Text field");
            content.put(name,source.optString(name));
        }
        content.put("references",source.has("references")?source.getJSONArray("references"):new JSONArray());
        content.put("attachments",files(source));
        byte[] hash=MessageDigest.getInstance("SHA-256").digest(canonical(content).getBytes(StandardCharsets.UTF_8));
        StringBuilder hex=new StringBuilder();for(byte value:hash)hex.append(String.format(Locale.ROOT,"%02x",value&255));return hex.toString();
    }
    static NativeDraft copy(String owner,JSONObject source,String nativeId)throws Exception{
        String sourceId=sourceId(source),fingerprint=fingerprint(source);
        JSONObject mail=new JSONObject(source.toString()).put("id",nativeId).put("attachments",new JSONArray());
        for(String name:new String[]{"to","cc","bcc","subject","body","inReplyTo"})if(!mail.has(name))mail.put(name,"");
        if(!mail.has("references"))mail.put("references",new JSONArray());
        for(String name:new String[]{"serverDraft","serverDraftAttempted","serverDraftUnconfirmed","scheduledMessageId"})mail.remove(name);
        mail.put("legacyDraftSource",new JSONObject().put("id",sourceId).put("fingerprint",fingerprint));
        return new NativeDraft(owner,NativeDraft.EDITING,mail);
    }
    static NativeDraft existing(List<NativeDraft> drafts,JSONObject source)throws Exception{
        String id=sourceId(source),fingerprint=fingerprint(source);NativeDraft found=null;
        for(NativeDraft draft:drafts){JSONObject ref=draft.mail.optJSONObject("legacyDraftSource");if(ref!=null&&id.equals(ref.optString("id"))&&fingerprint.equals(ref.optString("fingerprint"))){if(found!=null)throw new IllegalStateException("Ambiguous import");found=draft;}}
        return found;
    }
    static JSONArray candidates(JSONArray source,Set<String> protectedIds)throws Exception{
        if(source.length()>5000)throw new IllegalArgumentException("Draft list too large");Map<String,Integer> counts=new HashMap<>();
        for(int i=0;i<source.length();i++)try{String id=sourceId(source.getJSONObject(i));counts.put(id,counts.getOrDefault(id,0)+1);}catch(Exception invalid){}
        JSONArray result=new JSONArray();
        for(int i=0;i<source.length();i++)try{JSONObject item=source.getJSONObject(i);String id=sourceId(item);if(counts.get(id)==1&&!protectedIds.contains(id))result.put(new JSONObject(item.toString()));}catch(Exception invalid){}
        return result;
    }
    static void unchanged(JSONObject expected,JSONObject current)throws Exception{
        if(!SharedStateMerge.same(expected,current))throw new IllegalStateException("Legacy draft changed");
    }
    private static String canonical(Object value)throws Exception{
        if(value instanceof JSONObject){JSONObject object=(JSONObject)value;TreeSet<String> keys=new TreeSet<>();Iterator<String> iterator=object.keys();while(iterator.hasNext())keys.add(iterator.next());StringBuilder result=new StringBuilder("{");for(String key:keys)result.append(JSONObject.quote(key)).append(':').append(canonical(object.get(key))).append(',');return result.append('}').toString();}
        if(value instanceof JSONArray){JSONArray array=(JSONArray)value;StringBuilder result=new StringBuilder("[");for(int i=0;i<array.length();i++)result.append(canonical(array.get(i))).append(',');return result.append(']').toString();}
        return value instanceof String?JSONObject.quote((String)value):String.valueOf(value);
    }
}
