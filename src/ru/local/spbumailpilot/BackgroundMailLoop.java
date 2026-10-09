package ru.local.spbumailpilot;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Independent of Activity lifetime; fixed delay prevents overlapping IMAP checks. */
final class BackgroundMailLoop implements AutoCloseable {
    interface Check { void run(java.util.function.BooleanSupplier stopped)throws Exception; }
    private final ScheduledExecutorService executor=Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean stopped=new AtomicBoolean();
    BackgroundMailLoop(Check check,long delayMs){
        executor.scheduleWithFixedDelay(()->{if(stopped.get())return;try{check.run(stopped::get);}catch(Exception unavailable){/* Retry on the next tick, keeping the saved login. */}},0,delayMs,TimeUnit.MILLISECONDS);
    }
    public void close(){stopped.set(true);executor.shutdownNow();}
}
