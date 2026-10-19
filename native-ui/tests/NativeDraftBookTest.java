package ru.local.spbumailpilot;
import org.json.*;
import java.util.*;
final class NativeDraftBookTest {
    static final String OWNER="account-"+"a".repeat(64);
    interface Action {void run()throws Exception;}
    static void check(boolean ok){if(!ok)throw new AssertionError();}
    static void refused(Action action)throws Exception{try{action.run();throw new AssertionError("Accepted stale/invalid mutation");}catch(IllegalArgumentException|IllegalStateException expected){}}
    static String id(NativeDraft d)throws Exception{return d.mail.getString("id");}
    public static void main(String[] args)throws Exception{
        NativeDraft a=NativeDraft.create(OWNER,null,"first Ж🙂 <text>");a.mail.put("futureMetadata",new JSONObject().put("keep",true));
        NativeDraft b=NativeDraft.create(OWNER,null,"second");
        NativeDraftBook empty=NativeDraftBook.empty(OWNER);check(empty.active()==null&&empty.list().isEmpty());
        NativeDraftBook legacy=NativeDraftBook.parse(OWNER,a.json().toString());check(id(legacy.active()).equals(id(a))&&legacy.active().mail.getJSONObject("futureMetadata").getBoolean("keep"));
        NativeDraftBook two=legacy.create(b);check(two.list().size()==2&&id(two.active()).equals(id(b))&&two.find(id(a)).mail.getString("body").equals(a.mail.getString("body")));
        refused(()->two.update(a.status(NativeDraft.EDITING)));refused(()->two.create(a));
        NativeDraftBook selected=two.select(b,a);NativeDraft selectedA=selected.active();check(selectedA.revision==a.revision+1&&id(selectedA).equals(id(a)));
        refused(()->selected.update(a.status(NativeDraft.EDITING))); // Pre-switch editor cannot reuse the old revision.
        NativeDraft edited=new NativeDraft(OWNER,NativeDraft.EDITING,new JSONObject(selectedA.mail.toString()).put("body","changed"),selectedA.revision+1);
        NativeDraftBook updated=selected.update(edited);check(updated.find(id(b)).mail.getString("body").equals("second"));
        refused(()->updated.select(selectedA,b));refused(()->updated.select(edited,a));
        NativeDraftBook removed=updated.remove(edited,NativeDraft.EDITING);check(removed.list().size()==1&&id(removed.active()).equals(id(b))&&removed.active().revision==b.revision+1);
        refused(()->removed.update(b.status(NativeDraft.EDITING)));refused(()->removed.remove(b,NativeDraft.EDITING));
        check(removed.remove(removed.active(),NativeDraft.EDITING).list().isEmpty());
        NativeDraftBook round=NativeDraftBook.parse(OWNER,updated.json());check(round.list().size()==2&&round.active().mail.getString("body").equals("changed"));
        NativeDraft exposed=round.active();exposed.mail.put("body","tampered");check(round.active().mail.getString("body").equals("changed"));
        round.list().get(0).mail.put("body","tampered-list");check(round.active().mail.getString("body").equals("changed"));
        JSONObject root=new JSONObject(updated.json());JSONArray duplicates=new JSONArray().put(a.json()).put(a.json());refused(()->NativeDraftBook.parse(OWNER,new JSONObject(root.toString()).put("drafts",duplicates).toString()));
        refused(()->NativeDraftBook.parse("account-"+"b".repeat(64),updated.json()));
        refused(()->NativeDraftBook.parse(OWNER,new JSONObject(root.toString()).put("active","missing").toString()));
        NativeDraftBook full=empty;
        for(int i=0;i<NativeDraftBook.LIMIT;i++)full=full.create(NativeDraft.create(OWNER,null,"draft-"+i));
        final NativeDraftBook filled=full;refused(()->filled.create(NativeDraft.create(OWNER,null,"overflow")));check(full.list().size()==50);
        for(String status:new String[]{NativeDraft.DISPATCHING,NativeDraft.UNCERTAIN,NativeDraft.SENT,NativeDraft.SCHEDULING,NativeDraft.SCHEDULED}){
            NativeDraft marked=a.status(status);NativeDraftBook book=NativeDraftBook.parse(OWNER,marked.json().toString()).create(b);NativeDraftBook reopened=book.select(b,marked);
            check(reopened.active().status.equals(status)&&reopened.find(id(b)).status.equals(NativeDraft.EDITING));
        }
        NativeDraftBook[] disk={two};int[] sends={0},queued={0};String beforeSwitch=disk[0].json();
        refused(()->NativeSendTransaction.run(a,next->{disk[0]=disk[0].update(next);},()->{sends[0]++;}));
        refused(()->NativeScheduleTransaction.run(a,next->{disk[0]=disk[0].update(next);},new NativeScheduleTransaction.Queue(){
            public boolean contains(String id){return false;}public void create(NativeDraft draft){queued[0]++;}
        }));
        check(sends[0]==0&&queued[0]==0&&beforeSwitch.equals(disk[0].json()));
        check(NativeDraftRecovery.sameContent(a,a.status(NativeDraft.DISPATCHING)));
        check(!NativeDraftRecovery.sameContent(a,b)&&!NativeDraftRecovery.sameContent(a,null));
        check(!NativeDraftRecovery.sameContent(selectedA,edited));
        NativeDraft other=new NativeDraft("account-"+"b".repeat(64),a.status,a.mail,a.revision);check(!NativeDraftRecovery.sameContent(a,other));
        System.out.println("PASS NativeDraftBook: legacy migration, 50 isolated drafts, immutable snapshots, exact active/revision CAS, stale writes after selection/removal rejected, metadata/status preservation, malformed/foreign/duplicate rejection");
    }
}
