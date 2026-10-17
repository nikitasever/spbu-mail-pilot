package ru.local.spbumailpilot;
import org.json.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
public final class NativeExportPlanTest {
 interface Step{void run()throws Exception;}
 static void check(boolean value){if(!value)throw new AssertionError();}
 static void reject(Step step)throws Exception{try{step.run();throw new AssertionError("Expected rejection");}catch(IllegalArgumentException|IOException|JSONException expected){}}
 public static void main(String[] args)throws Exception{
  String owner="account-"+"a".repeat(64);JSONArray files=new JSONArray().put(new JSONObject().put("path","1.2"));String initial=files.toString();
  NativeExportPlan plan=NativeExportPlan.from(owner,"INBOX","42","7",files,false);
  check(plan.owner.equals(owner)&&!plan.original&&plan.refs.size()==1&&plan.refs.get(0).path.equals("1.2"));check(files.toString().equals(initial));
  files.getJSONObject(0).put("path","other");check(plan.refs.get(0).path.equals("1.2"));
  try{plan.refs.clear();throw new AssertionError();}catch(UnsupportedOperationException expected){}
  NativeExportPlan eml=NativeExportPlan.from(owner,"Archive","42","7",null,true);check(eml.original&&eml.refs.get(0).path.equals("root"));
  reject(()->NativeExportPlan.from("wrong","INBOX","42","7",null,true));reject(()->NativeExportPlan.from(owner,null,"42","7",null,true));
  reject(()->NativeExportPlan.from(owner,"INBOX","42","",null,true));reject(()->NativeExportPlan.from(owner,"INBOX","42","7",new JSONArray(),false));
  reject(()->NativeExportPlan.from(owner,"INBOX","42","7",new JSONArray().put(new JSONObject().put("path",1)),false));
  reject(()->NativeExportPlan.from(owner,"INBOX","42","7",new JSONArray().put(new JSONObject().put("path","../file")),false));
  reject(()->NativeExportPlan.from(owner,"INBOX","42","7",new JSONArray().put(new JSONObject().put("path","1")).put(new JSONObject().put("path","1")),false));
  JSONArray many=new JSONArray();for(int i=0;i<501;i++)many.put(new JSONObject().put("path",String.valueOf(i)));reject(()->NativeExportPlan.from(owner,"INBOX","42","7",many,false));
  NativeExportPicker picker=new NativeExportPicker();check(picker.launch(plan));check(!picker.launch(eml));check(picker.pending());
  // Caller cancelled plan; the outstanding result still belongs to it, never to the replacement.
  check(picker.result()==plan);check(picker.result()==null);check(picker.launch(eml));check(picker.result()==eml);
  Path root=Files.createTempDirectory("native-export-test"),cache=Files.createDirectory(root.resolve("cache"));
  try{
   Path empty=Files.createFile(cache.resolve("empty"));check(NativeExportPlan.checkedFile(cache.toFile(),empty.toString()).length()==0);
   Path outside=Files.createFile(root.resolve("outside"));reject(()->NativeExportPlan.checkedFile(cache.toFile(),outside.toString()));
   Files.createSymbolicLink(cache.resolve("link"),outside);reject(()->NativeExportPlan.checkedFile(cache.toFile(),cache.resolve("link").toString()));
   File large=cache.resolve("large").toFile();try(RandomAccessFile file=new RandomAccessFile(large,"rw")){file.setLength(AttachmentFiles.MAX_BYTES+1);}reject(()->NativeExportPlan.checkedFile(cache.toFile(),large.getPath()));
  }finally{try(java.util.stream.Stream<Path> paths=Files.walk(root)){paths.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.delete(p);}catch(IOException e){throw new RuntimeException(e);}});}}
  System.out.println("PASS NativeExportPlan: exact scope, immutable paths, malformed/duplicate/oversized rejection, picker result isolation, empty files, outside/symlink/size bounds");
 }
}
