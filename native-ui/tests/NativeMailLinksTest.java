package ru.local.spbumailpilot;
import org.json.*;
public final class NativeMailLinksTest {
    static void check(boolean value){if(!value)throw new AssertionError();}
    static JSONObject mail(String folder,String uid,String validity)throws Exception{return new JSONObject().put("folder",folder).put("uid",uid).put("validity",validity).put("subject","Материалы");}
    public static void main(String[] args)throws Exception {
        String owner="account-"+"a".repeat(64),other="account-"+"b".repeat(64);
        JSONObject m=mail("Проекты/2026","42","7");MailNoticeTarget target=NativeMailLinks.target(owner,m);
        check(target!=null&&target.folder.equals("Проекты/2026")&&target.uid.equals("42")&&target.validity.equals("7")&&target.account.equals(owner));
        check(NativeMailLinks.target(owner,new JSONObject(m.toString()).put("account",other))==null);
        check(NativeMailLinks.target(owner,new JSONObject(m.toString()).put("uid","0"))==null);
        check(NativeMailLinks.target(owner,new JSONObject(m.toString()).put("uid",42))==null);
        check(NativeMailLinks.target(owner,new JSONObject(m.toString()).put("validity",7))==null);
        check(NativeMailLinks.target(owner,new JSONObject(m.toString()).put("validity",""))==null);
        check(NativeMailLinks.target(owner,new JSONObject(m.toString()).put("folder",""))==null);
        check(NativeMailLinks.target(owner,new JSONObject(m.toString()).put("folder","INBOX\nTrash"))==null);
        JSONObject old=new JSONObject(m.toString());old.remove("folder");check(NativeMailLinks.target(owner,old).folder.equals("INBOX"));
        // Large display metadata must not invalidate an otherwise exact compact reference.
        check(NativeMailLinks.target(owner,new JSONObject(m.toString()).put("preview","x".repeat(5000)))!=null);
        JSONObject task=NativeMailLinks.task(owner,new JSONObject(m.toString()).put("body","private-body").put("bcc","secret@example.org"),"new-task");
        check(task.getString("title").isEmpty()&&task.getString("date").isEmpty()&&task.getString("time").isEmpty());
        check(!task.getBoolean("done")&&task.getString("priority").equals("normal")&&task.getString("reminder").equals("auto"));
        JSONObject source=task.getJSONObject("mail");check(source.getString("account").equals(owner)&&source.getString("folder").equals("Проекты/2026"));
        check(!source.has("body")&&!source.has("bcc")&&NativeMailLinks.target(owner,source)!=null);
        source.put("subject","changed");check(m.getString("subject").equals("Материалы"));
        boolean refused=false;try{NativeMailLinks.task(other,new JSONObject(m.toString()).put("account",owner),"new-task");}catch(IllegalArgumentException e){refused=true;}check(refused);
        JSONObject local=new JSONObject().put("kind","event").put("id","same"),remote=new JSONObject().put("kind","remote").put("id","same").put("calendarId","work");
        check(NativeMailLinks.eventKey(local).equals("local:same"));check(NativeMailLinks.eventKey(remote).equals("[\"work\",\"same\"]"));
        JSONObject first=new JSONObject().put("id","duplicate").put("eventKey","local:same").put("mail",m);
        JSONObject second=new JSONObject(first.toString()).put("mail",mail("Archive","43","8"));
        JSONArray links=new JSONArray().put(first).put(second).put(new JSONObject().put("eventKey","[\"work\",\"same\"]").put("mail",mail("INBOX","44","9")));
        check(NativeMailLinks.forEvent(local,links).length()==2&&NativeMailLinks.forEvent(remote,links).length()==1);
        check(NativeMailLinks.forEvent(new JSONObject().put("id","task").put("kind","task"),links).length()==0);
        JSONObject chosen=NativeMailLinks.savedMail(links,second);check(chosen.getString("uid").equals("43")&&chosen.getString("folder").equals("Archive"));
        chosen.put("uid","99");check(second.getJSONObject("mail").getString("uid").equals("43"));
        check(NativeMailLinks.savedMail(links,new JSONObject(second.toString()).put("mail",mail("Archive","99","8")))==null);
        check(NativeMailLinks.savedMail(new JSONArray(),second)==null);

        // taskFromEvent tests
        JSONObject remoteEvent=new JSONObject().put("id","evt-1").put("kind","remote").put("calendarId","cal-main")
            .put("calendarTitle","Работа").put("title","Совещание").put("date","2026-10-20").put("time","14:30")
            .put("allDay",false).put("location","Каб. 301");
        JSONObject fromRemote=NativeMailLinks.taskFromEvent(remoteEvent,"task-remote-1");
        check(fromRemote.getString("id").equals("task-remote-1")&&fromRemote.getString("title").equals("Совещание"));
        check(fromRemote.getString("date").equals("2026-10-20")&&fromRemote.getString("time").equals("14:30"));
        check(fromRemote.getString("notes").equals("Место: Каб. 301")&&!fromRemote.getBoolean("done"));
        JSONObject remoteSrc=fromRemote.getJSONObject("calendarSource");
        check(remoteSrc.getString("calendarId").equals("cal-main")&&remoteSrc.getString("id").equals("evt-1")&&remoteSrc.getString("calendarTitle").equals("Работа"));

        JSONObject localEvent=new JSONObject().put("id","evt-2").put("kind","event").put("date","2026-10-21").put("time","09:00").put("allDay",true);
        JSONObject fromLocal=NativeMailLinks.taskFromEvent(localEvent,"task-local-2");
        check(fromLocal.getString("title").equals("Без названия")&&fromLocal.getString("time").isEmpty()&&fromLocal.getString("notes").isEmpty());
        JSONObject localSrc=fromLocal.getJSONObject("calendarSource");
        check(localSrc.getString("calendarId").equals("local")&&localSrc.getString("id").equals("evt-2")&&localSrc.getString("calendarTitle").equals("Локальный"));

        // link tests
        JSONObject state=new JSONObject();
        JSONObject mailWithExtras=new JSONObject(m.toString()).put("sender","Иван").put("email","ivan@spbu.ru").put("date","2026-10-17").put("body","secret");
        JSONObject state1=NativeMailLinks.link(state,owner,mailWithExtras,remoteEvent,"link-1");
        JSONArray savedLinks=state1.getJSONArray("eventMailLinks");
        check(savedLinks.length()==1);
        JSONObject savedLink=savedLinks.getJSONObject(0);
        check(savedLink.getString("id").equals("link-1")&&savedLink.getString("eventKey").equals("[\"cal-main\",\"evt-1\"]"));
        check(savedLink.getString("eventDate").equals("2026-10-20")&&savedLink.getString("eventTitle").equals("Совещание"));
        check(!savedLink.getJSONObject("mail").has("body")&&savedLink.getJSONObject("mail").getString("sender").equals("Иван"));

        // Duplicate link rejection
        boolean dupRejected=false;
        try{NativeMailLinks.link(state1,owner,mailWithExtras,remoteEvent,"link-2");}catch(IllegalArgumentException e){dupRejected=e.getMessage().contains("уже связано");}
        check(dupRejected);

        // 500 link limit
        JSONObject fullState=new JSONObject();
        JSONArray fullArr=new JSONArray();
        for(int i=0;i<500;i++)fullArr.put(new JSONObject().put("id","l-"+i).put("eventKey","local:e-"+i));
        fullState.put("eventMailLinks",fullArr);
        boolean limitRejected=false;
        try{NativeMailLinks.link(fullState,owner,mailWithExtras,localEvent,"link-501");}catch(IllegalArgumentException e){limitRejected=e.getMessage().contains("500");}
        check(limitRejected);

        // unlink CAS conflict & success
        boolean conflict=false;
        try{NativeMailLinks.unlink(state1,"link-1",new JSONObject(savedLink.toString()).put("eventTitle","Другое"));}catch(SharedStateMerge.Conflict e){conflict=true;}
        check(conflict);
        boolean missingConflict=false;
        try{NativeMailLinks.unlink(state1,"link-missing",savedLink);}catch(SharedStateMerge.Conflict e){missingConflict=true;}
        check(missingConflict);
        JSONObject stateAfterUnlink=NativeMailLinks.unlink(state1,"link-1",savedLink);
        check(stateAfterUnlink.getJSONArray("eventMailLinks").length()==0);
        JSONObject stateAfterUnlink2=NativeMailLinks.unlink(state1,savedLink);
        check(stateAfterUnlink2.getJSONArray("eventMailLinks").length()==0);

        System.out.println("PASS native mail links: owner/folder/UID/UIDVALIDITY guards, old INBOX default only, Unicode folders, exact event keys, duplicate link ids, stale/deleted/tampered link rejection, immutable source references, taskFromEvent, link/500-limit/duplicate, CAS unlink");
    }
}
