package ru.local.spbumailpilot;
import org.json.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
final class NativeLegacyDraftFilesTest {
    static void check(boolean ok){if(!ok)throw new AssertionError();}
    interface Action{void run()throws Exception;}
    static void refused(Action action)throws Exception{try{action.run();throw new AssertionError("Invalid copy accepted");}catch(IOException|IllegalArgumentException|IllegalStateException expected){}}
    static JSONObject entry(String token,String name,long size)throws Exception{return new JSONObject().put("token",token).put("name",name).put("size",size);}
    static void remove(File root)throws Exception{if(Files.isSymbolicLink(root.toPath())){Files.delete(root.toPath());return;}File[] children=root.listFiles();if(children!=null)for(File child:children)remove(child);Files.deleteIfExists(root.toPath());}
    public static void main(String[] args)throws Exception{
        File root=Files.createTempDirectory("legacy-draft-files-").toFile();
        try{
            File source=new File(root,"source"),target=new File(root,"target");check(source.mkdir());File a=new File(source,"old.pdf"),b=new File(source,"old.docx");
            byte[] body=new byte[100000];new Random(1).nextBytes(body);Files.write(a.toPath(),body);Files.write(b.toPath(),new byte[]{4,5,6});
            JSONArray entries=new JSONArray().put(entry("old.pdf","Материалы.pdf",body.length)).put(entry("old.docx","Протокол.docx",3));List<File> created=new ArrayList<>();long[] bytes={0};
            JSONArray copies=NativeLegacyDraftFiles.copy(source,target,entries,created,()->{},value->{check(value>=bytes[0]);bytes[0]=value;});
            check(copies.length()==2&&created.size()==2&&bytes[0]==100003&&Arrays.equals(Files.readAllBytes(a.toPath()),body));
            check(Arrays.equals(Files.readAllBytes(created.get(0).toPath()),body)&&copies.getJSONObject(0).getString("token").startsWith("mail-"));
            for(File file:created)check(file.delete());created.clear();
            refused(()->NativeLegacyDraftFiles.copy(source,target,new JSONArray().put(entry("missing.pdf","missing.pdf",1)),created,()->{},value->{}));check(created.isEmpty());
            refused(()->NativeLegacyDraftFiles.copy(source,target,new JSONArray().put(entry("old.pdf","wrong.pdf",1)),created,()->{},value->{}));check(created.isEmpty());
            int[] checks={0};refused(()->NativeLegacyDraftFiles.copy(source,target,entries,created,()->{if(++checks[0]>1)throw new IllegalStateException("cancel");},value->{}));check(created.isEmpty()&&target.list().length==0);
            File outside=new File(root,"outside.pdf");Files.write(outside.toPath(),new byte[]{7});File link=new File(source,"link.pdf");
            try{Files.createSymbolicLink(link.toPath(),outside.toPath());refused(()->NativeLegacyDraftFiles.copy(source,target,new JSONArray().put(entry("link.pdf","link.pdf",1)),created,()->{},value->{}));}catch(UnsupportedOperationException noSymlinks){}
            check(Files.readAllBytes(outside.toPath())[0]==7&&Arrays.equals(Files.readAllBytes(a.toPath()),body));
            System.out.println("PASS NativeLegacyDraftFiles: exact file copies and monotonic bytes, missing/truncated source rejection, cancelled partial-file cleanup, symlink escape blocked, originals unchanged");
        }finally{remove(root);}
    }
}
