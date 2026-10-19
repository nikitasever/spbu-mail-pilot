package ru.local.spbumailpilot;
import java.io.*;
import java.nio.file.*;
public final class OfflineCacheFilesTest {
    static void check(boolean value){if(!value)throw new AssertionError();}
    public static void main(String[] args)throws Exception {
        Path directory=Files.createTempDirectory("cache-policy-");
        try{
            File base=directory.resolve("offline-one.bin").toFile();check(OfflineCacheFiles.bytes(base)==0);OfflineCacheFiles.requireDeleted(base);
            Files.write(base.toPath(),new byte[3]);Files.write(directory.resolve("offline-one.bin.bak"),new byte[5]);Files.write(directory.resolve("offline-one.bin.new"),new byte[7]);
            Files.write(directory.resolve("offline-two.bin"),new byte[11]);Files.write(directory.resolve("native-draft"),new byte[13]);
            check(OfflineCacheFiles.bytes(base)==15);
            try{OfflineCacheFiles.requireDeleted(base);throw new AssertionError();}catch(IOException expected){}
            Files.delete(base.toPath());try{OfflineCacheFiles.requireDeleted(base);throw new AssertionError();}catch(IOException expected){}
            Files.delete(directory.resolve("offline-one.bin.bak"));Files.delete(directory.resolve("offline-one.bin.new"));OfflineCacheFiles.requireDeleted(base);
            check(Files.size(directory.resolve("offline-two.bin"))==11&&Files.size(directory.resolve("native-draft"))==13);
            Files.createSymbolicLink(base.toPath(),directory.resolve("offline-two.bin"));try{OfflineCacheFiles.bytes(base);throw new AssertionError();}catch(IOException expected){}Files.delete(base.toPath());
            Files.createDirectory(base.toPath());try{OfflineCacheFiles.bytes(base);throw new AssertionError();}catch(IOException expected){}
            System.out.println("PASS OfflineCacheFiles: exact sidecars, backup leftovers, account/draft isolation, missing/zero cache, symlink/directory rejection");
        }finally{try(java.util.stream.Stream<Path> paths=Files.walk(directory)){paths.sorted(java.util.Comparator.reverseOrder()).forEach(p->{try{Files.delete(p);}catch(IOException error){throw new RuntimeException(error);}});}}
    }
}
