package ru.local.spbumailpilot;
import org.json.*;
import java.util.*;
final class NativeServerDraftCopyPlanTest {
    static final String OWNER="account-"+"a".repeat(64),ID="11111111-1111-1111-1111-111111111111";
    interface Action{void run()throws Exception;}
    static void check(boolean ok){if(!ok)throw new AssertionError();}
    static void refused(Action action)throws Exception{try{action.run();throw new AssertionError("Invalid draft accepted");}catch(IllegalArgumentException|IllegalStateException expected){}}
    static JSONObject data()throws Exception{return new JSONObject().put("folder","Drafts").put("uid","9007199254740993").put("validity","7").put("html",false).put("key","").put("revision","").put("attachments",new JSONArray().put(new JSONObject().put("path","1").put("name","file.pdf").put("size",-1))).put("draft",NativeDraft.create(OWNER,null,"body Ж🙂").mail);}
    public static void main(String[] args)throws Exception{
        JSONObject source=data();String original=source.toString();NativeDraft copy=NativeServerDraftCopyPlan.create(OWNER,source,ID,"body Ж🙂");
        check(copy.mail.getString("body").equals("body Ж🙂")&&copy.mail.getJSONArray("attachments").length()==0&&copy.status.equals(NativeDraft.EDITING)&&original.equals(source.toString()));
        check(copy.mail.getJSONObject("serverDraftSource").getString("uid").equals("9007199254740993"));
        NativeDraft edited=new NativeDraft(OWNER,NativeDraft.EDITING,new JSONObject(copy.mail.toString()).put("body","local edits"),1);
        check(NativeServerDraftCopyPlan.existing(Arrays.asList(edited),source)==edited);
        check(NativeServerDraftCopyPlan.existing(Arrays.asList(edited),new JSONObject(original).put("validity","8"))==null);
        check(NativeServerDraftCopyPlan.existing(Arrays.asList(edited),new JSONObject(original).put("folder","Other"))==null);
        refused(()->NativeServerDraftCopyPlan.ref(new JSONObject(original).put("uid",42L)));
        refused(()->NativeServerDraftCopyPlan.ref(new JSONObject(original).put("uid","9223372036854775808")));
        refused(()->NativeServerDraftCopyPlan.ref(new JSONObject(original).put("folder","Drafts\nINBOX")));
        JSONObject bad=new JSONObject(original).put("attachments",new JSONArray().put(new JSONObject().put("path","../1").put("name","file.pdf")));refused(()->NativeServerDraftCopyPlan.files(bad));
        JSONObject dup=new JSONObject(original);dup.getJSONArray("attachments").put(dup.getJSONArray("attachments").getJSONObject(0));refused(()->NativeServerDraftCopyPlan.files(dup));
        NativeDraft owned=NativeDraft.create(OWNER,null,"latest local");String key=owned.mail.getString("id"),revision="c".repeat(64);
        owned.mail.put("serverDraft",new JSONObject().put("folder","Drafts").put("uid",source.getString("uid")).put("validity","7").put("key",key).put("revision",revision));
        check(NativeServerDraftCopyPlan.existing(Arrays.asList(owned),new JSONObject(original).put("key",key).put("revision",revision))==owned);
        check(NativeServerDraftCopyPlan.existing(Arrays.asList(owned),new JSONObject(original).put("key",key).put("revision","d".repeat(64)))==null);
        System.out.println("PASS NativeServerDraftCopyPlan: exact UID/UIDVALIDITY/folder identity, native edits preserved, owned backup reconnect, foreign revisions/invalid MIME paths rejected, original server metadata immutable");
    }
}
