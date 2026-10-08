package ru.local.spbumailpilot;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.mail.AuthenticationFailedException;

public class MailWatchTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        AtomicInteger changes=new AtomicInteger(),closes=new AtomicInteger();
        BlockingQueue<Boolean> events=new LinkedBlockingQueue<>();CountDownLatch ready=new CountDownLatch(1);
        MailWatch watcher=new MailWatch(()->new MailWatch.Connection(){
            public boolean awaitChange()throws Exception{ready.countDown();return events.take();}
            public void close(){closes.incrementAndGet();}
        },changes::incrementAndGet);
        watcher.start();check(ready.await(3,TimeUnit.SECONDS),"connected");check(changes.get()==1,"resume refresh");
        events.put(false);Thread.sleep(1100);check(changes.get()==1,"heartbeat must not refresh unchanged inbox");
        events.put(true);long limit=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(changes.get()<2&&System.nanoTime()<limit)Thread.sleep(10);
        check(changes.get()==2,"new mail refresh");watcher.close();Thread.sleep(100);check(closes.get()>0,"cancel closes socket");check(changes.get()==2,"no callback after stop");
        CountDownLatch connecting=new CountDownLatch(1),allowConnect=new CountDownLatch(1),released=new CountDownLatch(1);AtomicInteger late=new AtomicInteger();
        MailWatch pending=new MailWatch(()->{connecting.countDown();for(;;)try{allowConnect.await();break;}catch(InterruptedException ignored){}return new MailWatch.Connection(){public boolean awaitChange(){throw new AssertionError("cancelled connection was used");}public void close(){released.countDown();}};},late::incrementAndGet);
        pending.start();check(connecting.await(3,TimeUnit.SECONDS),"connection started");pending.close();allowConnect.countDown();check(released.await(3,TimeUnit.SECONDS),"connection completing after cancellation is closed");check(late.get()==0,"old account cannot deliver events");
        AtomicInteger authAttempts=new AtomicInteger();CountDownLatch rejected=new CountDownLatch(1);
        MailWatch badAuth=new MailWatch(()->{authAttempts.incrementAndGet();rejected.countDown();throw new AuthenticationFailedException("fixture");},()->{throw new AssertionError("rejected auth emitted");});
        badAuth.start();check(rejected.await(3,TimeUnit.SECONDS),"auth attempted");Thread.sleep(2100);check(authAttempts.get()==1,"auth rejection must not loop");badAuth.close();
        System.out.println("PASS: mail watcher changes, unchanged heartbeat, cancellation, late connect/account isolation and auth rejection without repeated login.");
    }
}
