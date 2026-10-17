package ru.local.spbumailpilot;

/** Opaque request identity prevents late results from a prior field, draft or account being accepted. */
final class NativeRecipientEpoch {
    static final class Ticket {
        final String owner,draft,field,value;
        Ticket(String owner,String draft,String field,String value){this.owner=owner;this.draft=draft;this.field=field;this.value=value;}
    }
    private Ticket active;
    synchronized Ticket begin(String owner,String draft,String field,String value){return active=new Ticket(owner,draft,field,value);}
    synchronized void clear(){active=null;}
    synchronized boolean current(Ticket ticket){return ticket!=null&&active==ticket;}
}
