package ru.local.spbumailpilot;

/** Delete metadata first. File cleanup failure must never resurrect a discarded draft. */
final class NativeDraftDiscard {
    interface Store {void remove(NativeDraft expected)throws Exception;}
    interface Copies {boolean clear()throws Exception;}
    static boolean run(NativeDraft expected,Store store,Copies copies)throws Exception {
        if(expected==null||!NativeDraft.EDITING.equals(expected.status))
            throw new IllegalStateException("Only an editable draft can be discarded");
        store.remove(expected);
        try{return copies.clear();}catch(Exception failure){return false;}
    }
}
