package ru.local.spbumailpilot;

/** Durable marker before SMTP. A crash or unknown delivery never becomes an automatic retry. */
final class NativeSendTransaction {
    interface Store {void write(NativeDraft record)throws Exception;}
    interface Delivery {void send()throws Exception;}
    static NativeDraft run(NativeDraft draft,Store store,Delivery delivery)throws Exception{
        if(!NativeDraft.EDITING.equals(draft.status))throw new IllegalStateException("Delivery must be resolved before retry");
        // Failed persistence must prevent any external effect.
        NativeDraft sending=draft.status(NativeDraft.DISPATCHING);store.write(sending);
        NativeDraft result;
        try{delivery.send();result=sending.status(NativeDraft.SENT);}
        catch(MailSender.Uncertain unknown){result=sending.status(NativeDraft.UNCERTAIN);}
        catch(Exception rejected){store.write(sending.status(NativeDraft.EDITING));throw rejected;}
        // If this write fails, the durable DISPATCHING marker still prevents retry after restart.
        store.write(result);return result;
    }
}
