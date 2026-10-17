package ru.local.spbumailpilot;
import org.json.*;
public final class NativeWorkspaceTest {
    interface Check{void run()throws Exception;}
    static void check(boolean v){if(!v)throw new AssertionError();}
    static void reject(Check c)throws Exception{try{c.run();}catch(Exception expected){return;}throw new AssertionError("Expected refusal");}
    public static void main(String[] args)throws Exception {
        JSONObject base=new JSONObject("{\"tasks\":[],\"notes\":[],\"settings\":{\"a\":1,\"b\":2}}");
        JSONObject nativeState=new JSONObject(base.toString()).put("tasks",new JSONArray().put(new JSONObject().put("id","t")));
        JSONObject web=new JSONObject(base.toString());web.getJSONObject("settings").put("a",3);
        JSONObject merged=SharedStateMerge.merge(base,web,nativeState);check(merged.getJSONArray("tasks").length()==1&&merged.getJSONObject("settings").getInt("a")==3);
        // Repeated saves from the same stale client must retain native additions.
        JSONObject later=new JSONObject(web.toString());later.getJSONObject("settings").put("b",4);
        check(SharedStateMerge.merge(web,later,merged).getJSONArray("tasks").length()==1);
        reject(()->SharedStateMerge.merge(base,new JSONObject(base.toString()).put("tasks",new JSONArray().put(1)),nativeState));
        check(SharedStateMerge.same(new JSONObject("{\"a\":1,\"b\":2}"),new JSONObject("{\"b\":2,\"a\":1}")));
        JSONObject note=new JSONObject().put("id","n").put("title","Заметка").put("body","Текст 📝");
        JSONObject fresh=NativeWorkspace.change(new JSONObject().put("unknown","keep"),"notes",null,note);
        check(fresh.getString("unknown").equals("keep")&&fresh.has("tasks")&&fresh.has("events"));
        JSONObject next=new JSONObject(note.toString()).put("body","Новый текст");
        JSONObject saved=NativeWorkspace.change(fresh,"notes",note,next);check(saved.getJSONArray("notes").getJSONObject(0).getString("body").equals("Новый текст"));
        reject(()->NativeWorkspace.change(saved,"notes",note,null));reject(()->NativeWorkspace.change(saved,"notes",null,note));
        JSONObject removed=NativeWorkspace.change(saved,"notes",next,null);check(removed.getJSONArray("notes").length()==0&&saved.getJSONArray("notes").length()==1);
        JSONObject task=new JSONObject().put("id","task").put("title","Задача").put("done",false).put("date","2026-10-16").put("time","09:30").put("reminder","auto").put("priority","important").put("mail",new JSONObject().put("uid","42")).put("calendarSource",new JSONObject().put("id","source"));
        JSONObject tasks=NativeWorkspace.change(saved,"tasks",null,task);
        JSONObject complete=new JSONObject(task.toString()).put("done",true);
        JSONObject done=NativeWorkspace.change(tasks,"tasks",task,complete);
        check(done.getJSONArray("tasks").getJSONObject(0).getJSONObject("mail").getString("uid").equals("42"));
        check(done.getJSONArray("tasks").getJSONObject(0).has("calendarSource")&&done.getJSONArray("notes").length()==1);
        reject(()->NativeWorkspace.change(done,"tasks",task,complete));
        reject(()->NativeWorkspace.validate("tasks",new JSONObject(task.toString()).put("date","2026-02-30")));
        reject(()->NativeWorkspace.validate("tasks",new JSONObject(task.toString()).put("date","")));
        reject(()->NativeWorkspace.validate("notes",new JSONObject(note.toString()).put("title"," ")));
        reject(()->NativeWorkspace.validate("tasks",new JSONObject(task.toString()).put("time","25:00")));
        reject(()->NativeWorkspace.change(done,"settings",null,note));
        reject(()->NativeWorkspace.rows(new JSONObject().put("tasks","bad"),"tasks"));
        reject(()->NativeWorkspace.rows(new JSONObject().put("tasks",new JSONArray().put(task).put(task)),"tasks"));
        System.out.println("PASS native workspace: stale WebView merge, conflicts, independent records/collections, task links, exact-delete CAS, invalid dates/times, original snapshots preserved; no Android storage/network");
    }
}
