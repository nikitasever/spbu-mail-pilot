package ru.local.spbumailpilot;
import java.util.*;

/** Foreground-only UI authentication. Background mail/queued sending do not depend on this gate. */
final class AppLockSession {
    static final int CREDENTIAL_BASE=10000;
    private final Set<Object> started=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());
    private boolean enabled,unlocked;
    private long generation;private Object owner,externalOwner;
    AppLockSession(boolean enabled){this.enabled=enabled;}
    static int credentialCode(long sequence){return sequence>=CREDENTIAL_BASE&&sequence<=Integer.MAX_VALUE?(int)sequence:-1;}
    synchronized boolean enabled(){return enabled;}
    synchronized boolean allowed(){return !enabled||unlocked;}
    synchronized void enabled(boolean value){enabled=value;if(!value){owner=null;externalOwner=null;++generation;}}
    synchronized void start(Object activity){started.add(activity);}
    synchronized void stop(Object activity,boolean changing){started.remove(activity);if(started.isEmpty()&&!changing){unlocked=false;if(externalOwner==null){owner=null;++generation;}}}
    synchronized long begin(Object activity,boolean external){owner=activity;externalOwner=external?activity:null;return ++generation;}
    synchronized boolean current(Object activity,long ticket){return owner==activity&&generation==ticket;}
    synchronized boolean accept(Object activity,long ticket){if(!current(activity,ticket)||!started.contains(activity))return false;unlocked=true;owner=null;externalOwner=null;return true;}
    synchronized void cancel(Object activity){if(owner==activity){owner=null;externalOwner=null;++generation;}}
    synchronized void destroy(Object activity,boolean changing){stop(activity,changing);cancel(activity);}
}
