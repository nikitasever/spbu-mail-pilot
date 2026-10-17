package ru.local.spbumailpilot;

/** Caller holds NativeDraftStore.gate. A partial commit never makes a draft sendable. */
final class NativeScheduleTransaction {
    interface Store {void write(NativeDraft draft)throws Exception;}
    interface Queue {boolean contains(String source)throws Exception;void create(NativeDraft draft)throws Exception;}
    static NativeDraft recover(NativeDraft draft,Store store,Queue queue)throws Exception {
        if(!NativeDraft.SCHEDULING.equals(draft.status))return draft;
        NativeDraft next=draft.status(queue.contains(draft.mail.getString("id"))?NativeDraft.SCHEDULED:NativeDraft.EDITING);
        store.write(next);return next;
    }
    static NativeDraft run(NativeDraft draft,Store store,Queue queue)throws Exception {
        if(!NativeDraft.EDITING.equals(draft.status))throw new IllegalStateException("Draft not editable");
        if(queue.contains(draft.mail.getString("id"))){NativeDraft next=draft.status(NativeDraft.SCHEDULED);store.write(next);return next;}
        NativeDraft pending=draft.status(NativeDraft.SCHEDULING);store.write(pending);
        // A failure may be after the durable queue insert; leave SCHEDULING for explicit reconciliation.
        queue.create(pending);
        NativeDraft scheduled=pending.status(NativeDraft.SCHEDULED);store.write(scheduled);return scheduled;
    }
}
