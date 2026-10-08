package ru.local.spbumailpilot;
import java.io.*;
import java.net.URL;
import java.nio.file.Files;
public final class UpdatePolicyTest {
    static void expect(boolean value){if(!value)throw new AssertionError();}
    public static void main(String[] args)throws Exception{
        expect(UpdatePolicy.accept(new URL("https://api.github.com/repos/test")).equals("application/vnd.github+json"));
        expect(UpdatePolicy.accept(new URL("https://github.com/test.apk")).equals("application/octet-stream"));
        expect(UpdatePolicy.allowed(new URL("https://github.com/nikitasever/spbu-mail-pilot-updates")));
        for(String url:new String[]{"http://github.com/x","https://github.com.evil.invalid/x","https://password@github.com/x","https://github.com:8443/x","https://evil.invalid/x"})expect(!UpdatePolicy.allowed(new URL(url)));
        File dir=Files.createTempDirectory("update-policy").toFile(),target=new File(dir,"update.apk");byte[] content="abc".getBytes("UTF-8");String sha="ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
        UpdatePolicy.copy(new ByteArrayInputStream(content),sha,target,3);expect(java.util.Arrays.equals(content,Files.readAllBytes(target.toPath())));
        for(int attempt=0;attempt<3;attempt++){try{UpdatePolicy.copy(new ByteArrayInputStream(content),attempt==0?sha.replace('a','b'):attempt==1?sha:"invalid",target,attempt==1?2:3);throw new AssertionError();}catch(IOException expected){}expect(!new File(dir,"update.part").exists());expect(java.util.Arrays.equals(content,Files.readAllBytes(target.toPath())));}
        target.delete();dir.delete();System.out.println("PASS: update HTTPS host policy, checksum, size limit, failed download cleanup and existing APK preservation.");
    }
}
