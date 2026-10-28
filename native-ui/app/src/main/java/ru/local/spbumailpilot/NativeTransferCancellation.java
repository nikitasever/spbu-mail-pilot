package ru.local.spbumailpilot;

import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.concurrent.Executor;

/** Cancellation never waits for a provider's blocking close on the UI thread. */
final class NativeTransferCancellation {
    private final Executor closer;
    private final IdentityHashMap<AutoCloseable,Boolean> resources=new IdentityHashMap<>();
    private boolean cancelled;
    NativeTransferCancellation(Executor closer){this.closer=closer;}
    synchronized boolean cancelled(){return cancelled;}
    void attach(AutoCloseable resource)throws IOException{
        synchronized(this){if(!cancelled){resources.put(resource,Boolean.TRUE);return;}}
        close(resource);throw new IOException("Transfer cancelled");
    }
    synchronized void detach(AutoCloseable resource){resources.remove(resource);}
    void cancel(){
        AutoCloseable[] old;
        synchronized(this){if(cancelled)return;cancelled=true;old=resources.keySet().toArray(new AutoCloseable[0]);resources.clear();}
        for(AutoCloseable resource:old)close(resource);
    }
    private void close(AutoCloseable resource){
        Runnable cleanup=()->{try{resource.close();}catch(Exception ignored){}};
        try{closer.execute(cleanup);}catch(java.util.concurrent.RejectedExecutionException stopped){Thread late=new Thread(cleanup,"late-transfer-close");late.setDaemon(true);late.start();}
    }
}
