package ru.local.spbumailpilot;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class NativeTransferStateTest {
    static void check(boolean v){if(!v)throw new AssertionError();}
    public static void main(String[] args)throws Exception {
        check(NativeTransferProgress.fraction("downloading",50,100)==.5f);
        check(NativeTransferProgress.fraction("uploading",100,100)<1f);
        for(String stage:new String[]{"connecting","receiving","confirming","sent-copy"})check(NativeTransferProgress.fraction(stage,100,100)<0);
        check(NativeTransferProgress.fraction("downloading",50,-1)<0);
        check(NativeTransferProgress.fraction("copying",20,10)<0);
        check(NativeTransferProgress.fraction("copying",-1,10)<0);
        check(NativeTransferProgress.fraction("uploading",Long.MAX_VALUE,Long.MAX_VALUE)<1);
        check(NativeTransferFailure.describe(new java.net.SocketTimeoutException("secret-api-key")).contains("долго не отвечает"));
        check(!NativeTransferFailure.describe(new java.io.IOException("private/path/secret-api-key")).contains("secret"));
        check(NativeTransferFailure.describe(new java.io.EOFException()).contains("оборвалась"));
        check(NativeTransferFailure.describe(new javax.mail.MessagingException("server",new javax.mail.AuthenticationFailedException("password"))).contains("отклонил вход"));
        ExecutorService other=Executors.newSingleThreadExecutor();
        try{
            try(NativeDraftFlight flight=NativeDraftFlight.claim("sending")){
                NativeDraftFlight.checkWrite("sending");
                check(other.submit(()->{try{NativeDraftFlight.checkWrite("sending");return false;}catch(IllegalStateException expected){return true;}}).get(2,TimeUnit.SECONDS));
                check(other.submit(()->{NativeDraftFlight.checkWrite("different-account");return true;}).get(2,TimeUnit.SECONDS));
                try{NativeDraftFlight.claim("sending");throw new AssertionError();}catch(IllegalStateException expected){}
            }
            check(other.submit(()->{NativeDraftFlight.checkWrite("sending");return true;}).get(2,TimeUnit.SECONDS));
        }finally{other.shutdownNow();}
        ExecutorService closer=Executors.newSingleThreadExecutor();
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        AtomicInteger closed=new AtomicInteger();
        try {
            NativeTransferCancellation cancel=new NativeTransferCancellation(closer);
            cancel.attach(()->{closed.incrementAndGet();entered.countDown();release.await();});
            CountDownLatch returned=new CountDownLatch(1);
            Thread ui=new Thread(()->{cancel.cancel();returned.countDown();});ui.start();
            check(returned.await(2,TimeUnit.SECONDS));check(entered.await(2,TimeUnit.SECONDS));
            check(cancel.cancelled());cancel.cancel();check(closed.get()==1);
            // A provider returning a stream after cancellation cannot start writing it.
            AtomicInteger lateClosed=new AtomicInteger();
            try {cancel.attach(()->lateClosed.incrementAndGet());throw new AssertionError();}catch(java.io.IOException expected){}
            release.countDown();closer.submit(()->{}).get(2,TimeUnit.SECONDS);check(lateClosed.get()==1);
            NativeTransferCancellation clean=new NativeTransferCancellation(closer);
            AutoCloseable finished=()->{throw new AssertionError("detached stream closed again");};clean.attach(finished);clean.detach(finished);clean.cancel();closer.submit(()->{}).get(2,TimeUnit.SECONDS);
            closer.shutdown();
            CountDownLatch shutdownClosed=new CountDownLatch(1);
            NativeTransferCancellation after=new NativeTransferCancellation(closer);after.cancel();
            try {after.attach(shutdownClosed::countDown);throw new AssertionError();}catch(java.io.IOException expected){}
            check(shutdownClosed.await(2,TimeUnit.SECONDS));
            System.out.println("PASS transfers: unknown totals, acknowledgement stages, no premature 100%, non-blocking cancellation, late provider result rejected, detached and shutdown cleanup.");
        }finally{release.countDown();closer.shutdownNow();}
    }
}
