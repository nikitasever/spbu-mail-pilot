package ru.local.spbumailpilot;

/** A live SMTP operation reserves its account's active draft without holding the disk lock. */
final class NativeDraftFlight implements AutoCloseable {
    private static final java.util.Map<String,NativeDraftFlight> active=new java.util.HashMap<>();
    private final String account;
    private final Thread thread;
    private NativeDraftFlight(String account){this.account=account;this.thread=Thread.currentThread();}
    static synchronized NativeDraftFlight claim(String account){
        if(active.containsKey(account))throw new IllegalStateException("Draft send in progress");
        NativeDraftFlight flight=new NativeDraftFlight(account);active.put(account,flight);return flight;
    }
    static synchronized void checkWrite(String account){
        NativeDraftFlight flight=active.get(account);
        if(flight!=null&&flight.thread!=Thread.currentThread())throw new IllegalStateException("Draft send in progress");
    }
    @Override public void close(){synchronized(NativeDraftFlight.class){if(active.get(account)==this)active.remove(account);}}
}
