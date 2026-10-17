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
        System.out.println("PASS native mail links: owner/folder/UID/UIDVALIDITY guards, old INBOX default only, Unicode folders, exact event keys, duplicate link ids, stale/deleted/tampered link rejection, immutable source references");
    }
}
