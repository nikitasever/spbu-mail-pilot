package ru.local.spbumailpilot;
import org.json.*;
import java.util.*;
final class NativeLegacyDraftPlanTest {
    static final String OWNER="account-"+"a".repeat(64),ID="11111111-1111-1111-1111-111111111111";
    interface Action{void run()throws Exception;}
    static void check(boolean ok){if(!ok)throw new AssertionError();}
    static void refused(Action action)throws Exception{try{action.run();throw new AssertionError("Invalid import accepted");}catch(IllegalArgumentException|IllegalStateException expected){}}
    static JSONObject source()throws Exception{return new JSONObject().put("id","m4-old-1").put("to","to@example.invalid").put("cc","cc@example.invalid").put("bcc","bcc@example.invalid").put("subject","Тема <raw>").put("body","Точный текст Ж🙂\n\n-- подпись").put("attachments",new JSONArray()).put("serverDraftUnconfirmed",true).put("serverDraft",new JSONObject().put("key","legacy-key"));}
    public static void main(String[] args)throws Exception{
        JSONObject source=source();String original=source.toString();NativeDraft copy=NativeLegacyDraftPlan.copy(OWNER,source,ID);
        check(copy.mail.getString("body").equals(source.getString("body"))&&copy.mail.getString("to").equals(source.getString("to"))&&copy.mail.getString("bcc").equals(source.getString("bcc")));
        check(copy.status.equals(NativeDraft.EDITING)&&copy.mail.getJSONArray("references").length()==0&&copy.mail.getJSONArray("attachments").length()==0);
        check(!copy.mail.has("serverDraft")&&!copy.mail.has("serverDraftUnconfirmed")&&original.equals(source.toString()));
        check(copy.mail.getJSONObject("legacyDraftSource").getString("id").equals("m4-old-1"));
        String fingerprint=NativeLegacyDraftPlan.fingerprint(source);
        JSONObject reordered=new JSONObject();reordered.put("body",source.getString("body"));for(String key:JSONObject.getNames(source))if(!key.equals("body"))reordered.put(key,source.get(key));
        check(fingerprint.equals(NativeLegacyDraftPlan.fingerprint(reordered)));
        JSONObject meta=new JSONObject(original).put("updatedAt",999).put("serverDraftAttempted",true).put("serverDraft",new JSONObject().put("another","value"));check(fingerprint.equals(NativeLegacyDraftPlan.fingerprint(meta)));
        NativeDraft edited=new NativeDraft(OWNER,NativeDraft.EDITING,new JSONObject(copy.mail.toString()).put("body","native edits"),1);
        check(NativeLegacyDraftPlan.existing(Arrays.asList(edited),source)==edited);
        check(NativeLegacyDraftPlan.existing(Arrays.asList(edited),new JSONObject(original).put("body","changed legacy"))==null);
        JSONObject attachment=new JSONObject().put("token","old-file.pdf").put("name","Материалы.pdf").put("size",3);
        JSONObject withFile=new JSONObject(original).put("attachments",new JSONArray().put(attachment));check(NativeLegacyDraftPlan.files(withFile).length()==1);
        for(String token:new String[]{"../secret","folder/file","folder\\file","..","bad\nfile"})refused(()->NativeLegacyDraftPlan.files(new JSONObject(original).put("attachments",new JSONArray().put(new JSONObject(attachment.toString()).put("token",token)))));
        refused(()->NativeLegacyDraftPlan.files(new JSONObject(original).put("attachments",new JSONArray().put(new JSONObject(attachment.toString()).put("size",-1)))));
        refused(()->NativeLegacyDraftPlan.files(new JSONObject(original).put("attachments",new JSONArray().put(attachment).put(attachment))));
        refused(()->NativeLegacyDraftPlan.copy(OWNER,new JSONObject(original).put("subject","header\ninjection"),ID));
        refused(()->NativeLegacyDraftPlan.copy(OWNER,new JSONObject(original).put("body",123),ID));
        refused(()->NativeLegacyDraftPlan.unchanged(source,new JSONObject(original).put("updatedAt",1)));
        NativeLegacyDraftPlan.unchanged(source,new JSONObject(original));
        JSONArray candidates=new JSONArray().put(source).put(new JSONObject(original).put("id","queued")).put(new JSONObject(original).put("id","duplicate")).put(new JSONObject(original).put("id","duplicate")).put(new JSONObject(original).put("id","../bad")).put("invalid");
        JSONArray eligible=NativeLegacyDraftPlan.candidates(candidates,new HashSet<>(Arrays.asList("queued")));check(eligible.length()==1&&eligible.getJSONObject(0).getString("id").equals(source.getString("id")));
        System.out.println("PASS NativeLegacyDraftPlan: exact body/envelope and source preservation, stable snapshot fingerprints, idempotence keeps native edits, changed source rejects stale copy, queue/duplicate filtering, unsafe paths/metadata and headers rejected, old server identities stripped");
    }
}
