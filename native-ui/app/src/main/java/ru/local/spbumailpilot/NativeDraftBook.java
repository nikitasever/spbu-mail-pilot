package ru.local.spbumailpilot;
import org.json.*;
import java.util.*;

/** Immutable account collection. Active identity and per-record revisions gate every mutation. */
final class NativeDraftBook {
    static final int LIMIT=50;
    final String owner,activeId;
    private final LinkedHashMap<String,NativeDraft> records;
    private NativeDraftBook(String owner,String activeId,Map<String,NativeDraft> records)throws Exception{
        if(!owner.matches("account-[0-9a-f]{64}")||records.size()>LIMIT)throw new IllegalArgumentException("Draft collection");
        this.owner=owner;this.activeId=activeId;this.records=new LinkedHashMap<>();
        for(Map.Entry<String,NativeDraft> entry:records.entrySet()){
            NativeDraft value=copy(entry.getValue());
            if(!owner.equals(value.owner)||!entry.getKey().equals(id(value)))throw new IllegalArgumentException("Draft owner/id");
            this.records.put(entry.getKey(),value);
        }
        if(records.isEmpty()?!activeId.isEmpty():!records.containsKey(activeId))throw new IllegalArgumentException("Active draft");
    }
    static NativeDraftBook empty(String owner)throws Exception{return new NativeDraftBook(owner,"",Collections.emptyMap());}
    static NativeDraftBook parse(String owner,String text)throws Exception{
        JSONObject root=new JSONObject(text);
        if(root.getInt("version")==1){NativeDraft legacy=NativeDraft.parse(owner,text);LinkedHashMap<String,NativeDraft> one=new LinkedHashMap<>();one.put(id(legacy),legacy);return new NativeDraftBook(owner,id(legacy),one);}
        if(root.getInt("version")!=2||!owner.equals(root.getString("owner")))throw new IllegalArgumentException("Draft collection version/owner");
        JSONArray drafts=root.getJSONArray("drafts");if(drafts.length()>LIMIT)throw new IllegalArgumentException("Draft limit");
        LinkedHashMap<String,NativeDraft> entries=new LinkedHashMap<>();
        for(int i=0;i<drafts.length();i++){NativeDraft draft=NativeDraft.parse(owner,drafts.getJSONObject(i).toString());if(entries.put(id(draft),draft)!=null)throw new IllegalArgumentException("Duplicate draft");}
        return new NativeDraftBook(owner,root.getString("active"),entries);
    }
    NativeDraft active()throws Exception{return find(activeId);}
    NativeDraft find(String id)throws Exception {NativeDraft draft=records.get(id);return draft==null?null:copy(draft);}
    List<NativeDraft> list()throws Exception{List<NativeDraft> result=new ArrayList<>();for(NativeDraft draft:records.values())result.add(copy(draft));return Collections.unmodifiableList(result);}
    NativeDraftBook create(NativeDraft draft)throws Exception{
        if(!owner.equals(draft.owner)||draft.revision!=0||records.containsKey(id(draft))||records.size()>=LIMIT)throw new IllegalStateException("Draft already exists or collection full");
        LinkedHashMap<String,NativeDraft> next=new LinkedHashMap<>(records);next.put(id(draft),draft);return new NativeDraftBook(owner,id(draft),next);
    }
    NativeDraftBook update(NativeDraft next)throws Exception{
        if(!activeId.equals(id(next)))throw new IllegalStateException("Active draft changed");
        NativeDraft.checkWrite(active(),next);
        LinkedHashMap<String,NativeDraft> entries=new LinkedHashMap<>(records);entries.put(id(next),next);return new NativeDraftBook(owner,activeId,entries);
    }
    NativeDraftBook select(NativeDraft expectedActive,NativeDraft target)throws Exception{
        exact(active(),expectedActive);exact(find(id(target)),target);
        NativeDraft selected=new NativeDraft(owner,target.status,target.mail,target.revision+1);
        LinkedHashMap<String,NativeDraft> entries=new LinkedHashMap<>(records);entries.put(id(selected),selected);return new NativeDraftBook(owner,id(selected),entries);
    }
    NativeDraftBook remove(NativeDraft expected,String status)throws Exception{
        NativeDraft.checkRemoval(active(),expected,status);
        LinkedHashMap<String,NativeDraft> entries=new LinkedHashMap<>(records);entries.remove(id(expected));
        if(entries.isEmpty())return empty(owner);
        String selectedId=entries.keySet().iterator().next();NativeDraft selected=entries.get(selectedId);
        entries.put(selectedId,new NativeDraft(owner,selected.status,selected.mail,selected.revision+1));
        return new NativeDraftBook(owner,selectedId,entries);
    }
    String json()throws Exception{JSONArray entries=new JSONArray();for(NativeDraft draft:records.values())entries.put(draft.json());return new JSONObject().put("version",2).put("owner",owner).put("active",activeId).put("drafts",entries).toString();}
    private static void exact(NativeDraft current,NativeDraft expected){
        if(current==null||expected==null){if(current!=expected)throw new IllegalStateException("Draft changed");return;}
        NativeDraft.checkRemoval(current,expected,expected.status);
    }
    private static NativeDraft copy(NativeDraft draft)throws Exception{return new NativeDraft(draft.owner,draft.status,draft.mail,draft.revision);}
    private static String id(NativeDraft draft)throws Exception{return draft.mail.getString("id");}
}
