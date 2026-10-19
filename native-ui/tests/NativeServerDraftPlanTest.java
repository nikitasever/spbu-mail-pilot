package ru.local.spbumailpilot;
import org.json.*;
final class NativeServerDraftPlanTest {
    static final String OWNER="account-"+"b".repeat(64),REV="c".repeat(64);
    static void check(boolean ok){if(!ok)throw new AssertionError();}
    static JSONObject ref(NativeDraft draft)throws Exception{return new JSONObject().put("key",draft.mail.getString("id")).put("folder","Черновики").put("uid","9007199254740993").put("validity","7").put("revision",REV);}
    public static void main(String[] args)throws Exception{
        NativeDraft source=NativeDraft.create(OWNER,null,"literal <body> and unicode Ж🙂");String original=source.mail.toString();
        NativeDraft pending=NativeServerDraftPlan.attempt(source);NativeDraft.checkWrite(source,pending);
        check(NativeServerDraftPlan.state(pending).equals("uncertain")&&pending.mail.optBoolean("serverDraftAttempted")&&original.equals(source.mail.toString()));
        JSONObject ref=ref(source);NativeDraft saved=NativeServerDraftPlan.confirmed(pending,ref,REV);NativeDraft.checkWrite(pending,saved);
        check(NativeServerDraftPlan.state(saved).equals("saved")&&saved.mail.optString("body").equals(source.mail.optString("body"))&&!saved.mail.has("serverDraftUnconfirmed"));
        ref.put("folder","mutated");check(saved.mail.getJSONObject("serverDraft").getString("folder").equals("Черновики"));
        NativeDraft retry=NativeServerDraftPlan.attempt(saved);check(retry.mail.has("serverDraft")&&NativeServerDraftPlan.state(retry).equals("uncertain"));
        NativeDraft removed=NativeServerDraftPlan.removed(saved,ref(source));NativeDraft.checkWrite(saved,removed);check(NativeServerDraftPlan.state(removed).equals("none")&&!removed.mail.has("serverDraftAttempted"));
        NativeDraft partial=NativeServerDraftPlan.removed(retry,ref(source));check(NativeServerDraftPlan.state(partial).equals("uncertain")&&partial.mail.optBoolean("serverDraftAttempted"));
        for(String field:new String[]{"uid","validity","key","revision","folder"}){
            JSONObject bad=ref(source);bad.put(field,field.equals("folder")?"INBOX\n":field.equals("key")?"11111111-1111-1111-1111-111111111111":field.equals("revision")?"bad":"0");
            try{NativeServerDraftPlan.confirmed(pending,bad,REV);throw new AssertionError(field);}catch(IllegalArgumentException expected){}
        }
        JSONObject numeric=ref(source).put("uid",42L);try{NativeServerDraftPlan.confirmed(pending,numeric,REV);throw new AssertionError();}catch(IllegalArgumentException expected){}
        JSONObject stale=ref(source).put("uid","42");try{NativeServerDraftPlan.removed(saved,stale);throw new AssertionError();}catch(IllegalStateException expected){}
        try{NativeServerDraftPlan.confirmed(pending,ref(source),"d".repeat(64));throw new AssertionError();}catch(IllegalArgumentException expected){}
        for(String phase:new String[]{NativeDraft.DISPATCHING,NativeDraft.SENT,NativeDraft.SCHEDULING,NativeDraft.SCHEDULED,NativeDraft.UNCERTAIN})try{NativeServerDraftPlan.attempt(source.status(phase));throw new AssertionError();}catch(IllegalStateException expected){}
        check(NativeServerDraftPlan.state(NativeDraft.parse(OWNER,pending.json().toString())).equals("uncertain"));
        System.out.println("PASS NativeServerDraftPlan: durable attempt, exact owned references/revision, unsafe/numeric UID rejection, immutable text, metadata CAS, unconfirmed uploads retained after known-copy removal, non-editable states blocked");
    }
}
