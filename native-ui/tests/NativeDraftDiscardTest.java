package ru.local.spbumailpilot;

import org.json.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class NativeDraftDiscardTest {
    interface Check{void run()throws Exception;}
    static void check(boolean value){if(!value)throw new AssertionError();}
    static void reject(Check action)throws Exception{try{action.run();}catch(Exception expected){return;}throw new AssertionError("Expected refusal");}
    static String owner(char c){char[] value=new char[64];Arrays.fill(value,c);return "account-"+new String(value);}
    static final class Store implements NativeDraftDiscard.Store {
        NativeDraft current;boolean fail;int removals;
        Store(NativeDraft draft){current=draft;}
        public void remove(NativeDraft expected)throws Exception {
            NativeDraft.checkRemoval(current,expected,NativeDraft.EDITING);
            if(fail)throw new IOException("Metadata removal failed");
            current=null;removals++;
        }
    }
    public static void main(String[] args)throws Exception {
        NativeDraft draft=NativeDraft.create(owner('a'),null,"");
        Store store=new Store(draft);int[] cleanup={0};
        store.fail=true;
        reject(()->NativeDraftDiscard.run(draft,store,()->{cleanup[0]++;return true;}));
        check(store.current==draft&&cleanup[0]==0);
        store.fail=false;
        store.current=draft.status(NativeDraft.EDITING);
        reject(()->NativeDraftDiscard.run(draft,store,()->{cleanup[0]++;return true;}));
        check(store.current.revision==1&&cleanup[0]==0);
        for(String state:new String[]{NativeDraft.DISPATCHING,NativeDraft.UNCERTAIN,NativeDraft.SENT}) {
            NativeDraft unresolved=draft.status(state);Store guarded=new Store(unresolved);
            reject(()->NativeDraftDiscard.run(unresolved,guarded,()->{cleanup[0]++;return true;}));
            check(guarded.current==unresolved&&guarded.removals==0&&cleanup[0]==0);
            reject(()->NativeDraft.checkRemoval(unresolved,draft,NativeDraft.EDITING));
        }
        NativeDraft other=NativeDraft.create(owner('b'),null,"");
        reject(()->NativeDraft.checkRemoval(other,draft,NativeDraft.EDITING));
        NativeDraft another=NativeDraft.create(owner('a'),null,"");
        reject(()->NativeDraft.checkRemoval(another,draft,NativeDraft.EDITING));
        reject(()->NativeDraft.checkRemoval(null,draft,NativeDraft.EDITING));
        NativeDraft sent=draft.status(NativeDraft.SENT);
        NativeDraft.checkRemoval(sent,sent,NativeDraft.SENT);
        reject(()->NativeDraft.checkRemoval(sent,sent,NativeDraft.EDITING));
        Path root=Files.createTempDirectory("native-discard-");
        try {
            File own=NativeAttachments.directory(root.toFile(),draft),others=NativeAttachments.directory(root.toFile(),other),newer=NativeAttachments.directory(root.toFile(),another);
            own.mkdirs();others.mkdirs();newer.mkdirs();
            Path copy=new File(own,"mail-attachment").toPath(),foreign=new File(others,"mail-other").toPath(),replacement=new File(newer,"mail-new").toPath();
            Files.write(copy,new byte[]{1,2});Files.write(foreign,new byte[]{3});Files.write(replacement,new byte[]{4});
            Path untouched=new File(own,"unowned-file").toPath();Files.write(untouched,new byte[]{5});
            store.current=draft;
            check(NativeDraftDiscard.run(draft,store,()->{check(store.current==null);cleanup[0]++;return NativeAttachments.clearCopies(own);}));
            check(store.current==null&&!Files.exists(copy)&&Files.exists(foreign)&&Files.exists(replacement)&&Files.exists(untouched));
            // A queued save or send from a stale editor cannot recreate a removed draft.
            reject(()->NativeDraft.checkWrite(store.current,draft.status(NativeDraft.EDITING)));
            reject(()->NativeSendTransaction.run(draft,next->NativeDraft.checkWrite(store.current,next),()->{throw new AssertionError("Must not reach delivery");}));
            reject(()->NativeDraftDiscard.run(draft,store,()->{throw new AssertionError("Must not clean again");}));
            // Cleanup failure is a separate outcome after metadata removal, never a restored draft.
            store.current=draft;
            check(!NativeDraftDiscard.run(draft,store,()->{throw new IOException("Cleanup failed");}));
            check(store.current==null);
            Path link=new File(own,"mail-link").toPath();Files.createSymbolicLink(link,foreign);
            check(!NativeAttachments.clearCopies(own)&&Files.exists(link)&&Files.exists(foreign));
            check(NativeAttachments.clearCopies(new File(own,"absent")));
        } finally {
            try(java.util.stream.Stream<Path> paths=Files.walk(root)){paths.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.delete(p);}catch(IOException ignored){}});}
        }
        System.out.println("PASS native draft discard: editing-only/account/id/revision guards, delete failure prevents cleanup, metadata precedes file removal, other drafts/accounts and foreign symlinks preserved, cleanup failure never restores draft, stale save/send/redelete refused; no network.");
    }
}
