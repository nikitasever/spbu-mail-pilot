package ru.local.spbumailpilot;
import org.json.*;
import java.io.IOException;
import java.time.Instant;
final class NativeScheduleTransactionTest {
    static final String OWNER="account-"+"a".repeat(64);
    static NativeDraft stored;static boolean queued;static int creates;static int failWrite;
    static void write(NativeDraft next)throws Exception{if(failWrite==next.revision)throw new IOException("disk");NativeDraft.checkWrite(stored,next);stored=next;}
    static NativeScheduleTransaction.Queue queue(boolean failAfter){return new NativeScheduleTransaction.Queue(){
        public boolean contains(String source){return queued;}
        public void create(NativeDraft draft)throws Exception{if(!NativeDraft.SCHEDULING.equals(stored.status))throw new AssertionError("No marker");creates++;queued=true;if(failAfter)throw new IOException("after commit");}
    };}
    static void reset()throws Exception{stored=NativeDraft.create(OWNER,null,"text");queued=false;creates=0;failWrite=-1;}
    static void check(boolean ok){if(!ok)throw new AssertionError();}
    public static void main(String[] args)throws Exception{
        reset();NativeDraft done=NativeScheduleTransaction.run(stored,NativeScheduleTransactionTest::write,queue(false));check(done.status.equals(NativeDraft.SCHEDULED)&&queued&&creates==1);
        try{NativeScheduleTransaction.run(done,NativeScheduleTransactionTest::write,queue(false));throw new AssertionError();}catch(IllegalStateException expected){}check(creates==1);
        reset();failWrite=1;try{NativeScheduleTransaction.run(stored,NativeScheduleTransactionTest::write,queue(false));throw new AssertionError();}catch(IOException expected){}check(!queued&&creates==0&&stored.status.equals(NativeDraft.EDITING));
        reset();try{NativeScheduleTransaction.run(stored,NativeScheduleTransactionTest::write,queue(true));throw new AssertionError();}catch(IOException expected){}check(stored.status.equals(NativeDraft.SCHEDULING)&&queued);NativeScheduleTransaction.recover(stored,NativeScheduleTransactionTest::write,queue(false));check(stored.status.equals(NativeDraft.SCHEDULED)&&creates==1);
        reset();failWrite=2;try{NativeScheduleTransaction.run(stored,NativeScheduleTransactionTest::write,queue(false));throw new AssertionError();}catch(IOException expected){}check(stored.status.equals(NativeDraft.SCHEDULING)&&queued);failWrite=-1;NativeScheduleTransaction.recover(stored,NativeScheduleTransactionTest::write,queue(false));check(stored.status.equals(NativeDraft.SCHEDULED)&&creates==1);
        reset();stored=stored.status(NativeDraft.SCHEDULING);NativeScheduleTransaction.recover(stored,NativeScheduleTransactionTest::write,queue(false));check(stored.status.equals(NativeDraft.EDITING)&&creates==0);
        reset();queued=true;NativeScheduleTransaction.run(stored,NativeScheduleTransactionTest::write,queue(false));check(stored.status.equals(NativeDraft.SCHEDULED)&&creates==0);
        reset();stored=stored.status(NativeDraft.SCHEDULING);try{NativeScheduleTransaction.recover(stored,NativeScheduleTransactionTest::write,new NativeScheduleTransaction.Queue(){public boolean contains(String id)throws Exception{throw new IOException("queue unavailable");}public void create(NativeDraft d){throw new AssertionError();}});throw new AssertionError();}catch(IOException expected){}check(stored.status.equals(NativeDraft.SCHEDULING));
        check(!ScheduledPolicy.ready("uncertain",0,100)&&!ScheduledPolicy.cancellable("transmitting")&&ScheduledPolicy.cancellable("pending"));
        check(ScheduledPolicy.recover("transmitting").equals("uncertain"));
        check(ScheduledPolicy.failure("transmitting",new IOException(),1).equals("uncertain"));
        String round=stored.json().toString();check(NativeDraft.parse(OWNER,round).status.equals(NativeDraft.SCHEDULING));
        long now=Instant.parse("2026-10-16T09:00:00Z").toEpochMilli();check(NativeScheduleTime.parse("2026-10-17","09:00",now)==Instant.parse("2026-10-17T06:00:00Z").toEpochMilli());
        for(String[] bad:new String[][]{{"2026-02-30","12:00"},{"2026-10-16","12:00"},{"2026-10-17","24:00"},{"2026-1-1","9:00"}})try{NativeScheduleTime.parse(bad[0],bad[1],now);throw new AssertionError();}catch(java.time.DateTimeException|IllegalArgumentException expected){}
        NativeDraft origin=NativeDraft.create(OWNER,null,"literal <body>");origin.mail.put("scheduledMessageId","<schedule@test.invalid>").put("serverDraft","server-key");
        String original=origin.mail.toString();NativeDraft returned=NativeReturnedDraft.convert(OWNER,origin.mail);
        check(returned.status.equals(NativeDraft.EDITING)&&returned.mail.optString("body").equals("literal <body>")&&!returned.mail.has("scheduledMessageId")&&!returned.mail.has("serverDraft")&&original.equals(origin.mail.toString()));
        try{NativeReturnedDraft.convert("account-b",origin.mail);throw new AssertionError();}catch(IllegalArgumentException expected){}
        System.out.println("PASS NativeScheduleTransaction: durable marker, pre/post-commit disk failures, recovery, existing queue dedupe, fail-closed unreadable queue, status round trip, strict Moscow future date/time");
    }
}
