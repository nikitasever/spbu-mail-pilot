package ru.local.spbumailpilot;
import java.io.*;

/** Exactly one encrypted snapshot and AtomicFile sidecars; no directory-wide deletion. */
final class OfflineCacheFiles {
    static File[] files(File base)throws IOException {
        File[] result={base,new File(base.getPath()+".bak"),new File(base.getPath()+".new")};
        for(File file:result)if(!new File(file.getAbsoluteFile().getParentFile().getCanonicalFile(),file.getName()).equals(file.getCanonicalFile()))throw new IOException("Unexpected cache path");
        return result;
    }
    static long bytes(File base)throws IOException {
        long result=0;for(File file:files(base)){if(file.exists()&&!file.isFile())throw new IOException("Unexpected cache entry");long size=file.length();if(Long.MAX_VALUE-result<size)throw new IOException("Size overflow");result+=size;}return result;
    }
    static void requireDeleted(File base)throws IOException {for(File file:files(base))if(file.exists())throw new IOException("Cannot clear cache");}
}
