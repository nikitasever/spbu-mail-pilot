package ru.local.spbumailpilot;

import java.util.concurrent.Executor;

/** Generation invalidation is immediate; blocking connection close always runs off the UI thread. */
final class NativeReadGate {
    private long generation;
    private AutoCloseable active;
    private final Executor closer;
    NativeReadGate(Executor closer){this.closer=closer;}
    long next(){AutoCloseable old;long ticket;synchronized(this){ticket=++generation;old=active;active=null;}close(old);return ticket;}
    synchronized boolean current(long ticket){return generation==ticket;}
    void attach(long ticket,AutoCloseable connection){boolean stale;synchronized(this){stale=generation!=ticket;if(!stale)active=connection;}if(stale)close(connection);}
    synchronized void detach(long ticket,AutoCloseable connection){if(generation==ticket&&active==connection)active=null;}
    private void close(AutoCloseable connection){if(connection!=null)closer.execute(()->{try{connection.close();}catch(Exception ignored){}});}
}
