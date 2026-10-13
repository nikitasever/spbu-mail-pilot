package ru.local.spbumailpilot;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
public final class ScheduledFilesTest {
 static void check(boolean ok){if(!ok)throw new AssertionError();}
 public static void main(String[] args)throws Exception{
  File root=Files.createTempDirectory("schedule-orphans-").toFile(),owner=new File(root,"account-"+String.join("",Collections.nCopies(64,"a")));check(owner.mkdir());
  File owned=new File(owner,"12345678-1234-1234-1234-123456789abc"),orphan=new File(owner,"abcdefab-1234-1234-1234-123456789abc");check(owned.mkdir()&&orphan.mkdir());File good=new File(owned,"snapshot.pdf"),partial=new File(orphan,"snapshot.pdf");Files.write(good.toPath(),new byte[]{1});Files.write(partial.toPath(),new byte[]{1,2});
  org.json.JSONArray entries=new org.json.JSONArray().put(new org.json.JSONObject().put("token","snapshot.pdf").put("size",1));ScheduledFiles.verify(owned,entries);Files.write(good.toPath(),new byte[0]);try{ScheduledFiles.verify(owned,entries);throw new AssertionError("Truncated snapshot accepted");}catch(IllegalArgumentException expected){}Files.write(good.toPath(),new byte[]{1});
  ScheduledFiles.cleanup(root,(a,id)->id.equals(owned.getName()));check(good.isFile()&&!orphan.exists());
  File link=new File(owner,"aaaaaaaa-1234-1234-1234-123456789abc");boolean linked=false;try{Files.createSymbolicLink(link.toPath(),owned.toPath());linked=true;}catch(IOException|UnsupportedOperationException unsupported){}if(linked){ScheduledFiles.cleanup(root,(a,id)->id.equals(owned.getName()));check(good.isFile()&&Files.isSymbolicLink(link.toPath()));check(link.delete());}
  File unrelated=new File(owner,"unrelated");check(unrelated.mkdir());File evidence=new File(unrelated,"keep.txt");Files.write(evidence.toPath(),new byte[]{3});
  try{ScheduledFiles.cleanup(root,(a,id)->{throw new IOException("Database unavailable");});throw new AssertionError();}catch(IOException expected){}check(good.isFile()&&evidence.isFile());
  check(good.delete()&&owned.delete()&&evidence.delete()&&unrelated.delete()&&owner.delete()&&root.delete());
  System.out.println("PASS: interrupted scheduled snapshot cleanup, owned/failed snapshots preserved, database failures fail closed and unrelated paths untouched.");
 }
}
