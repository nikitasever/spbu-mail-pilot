package ru.local.spbumailpilot;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class NativeReadGateTest {
    static void check(boolean value){if(!value)throw new AssertionError();}
    static final class Connection implements AutoCloseable {
        final CountDownLatch closing=new CountDownLatch(1),release=new CountDownLatch(1);
        final AtomicInteger count=new AtomicInteger();volatile Thread thread;
        public void close(){thread=Thread.currentThread();count.incrementAndGet();closing.countDown();try{release.await(3,TimeUnit.SECONDS);}catch(InterruptedException ignored){Thread.currentThread().interrupt();}}
        void awaitClose()throws Exception{check(closing.await(3,TimeUnit.SECONDS));}
    }
    public static void main(String[] args)throws Exception{
        ExecutorService closer=Executors.newSingleThreadExecutor();
        try{
            NativeReadGate gate=new NativeReadGate(closer);
            Connection first=new Connection();long a=gate.next();gate.attach(a,first);check(gate.current(a));
            long b=gate.next();check(!gate.current(a)&&gate.current(b));
            first.awaitClose();check(first.thread!=Thread.currentThread());
            // Closing blocks: generation change and new request still do not wait for the socket.
            Connection second=new Connection();gate.attach(b,second);check(gate.current(b));
            gate.detach(a,first); // A late finally block must not detach the new connection.
            long c=gate.next();check(!gate.current(b)&&gate.current(c));first.release.countDown();second.awaitClose();second.release.countDown();
            Connection late=new Connection();late.release.countDown();gate.attach(a,late);late.awaitClose();check(late.count.get()==1);
            Connection finished=new Connection();finished.release.countDown();gate.attach(c,finished);gate.detach(c,finished);gate.next();
            closer.submit(()->{}).get(3,TimeUnit.SECONDS);check(finished.count.get()==0);
            check(first.count.get()==1&&second.count.get()==1);
            System.out.println("PASS native read gate: stale list/body/account/file results rejected, delayed connection attachment closed, matching detach only, blocking close off caller thread; no network.");
        }finally{closer.shutdownNow();}
    }
}
