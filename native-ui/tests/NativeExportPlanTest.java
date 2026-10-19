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
  JSONObject a=new JSONObject().put("path","1.2").put("name","same.pdf").put("message",new JSONObject().put("folder","INBOX").put("uid","42").put("validity","7"));
  JSONObject b=new JSONObject(a.toString());b.getJSONObject("message").put("uid","43");
  JSONArray library=new JSONArray().put(a).put(b);Set<String> selected=new HashSet<>(Arrays.asList(NativeFilePage.key(a),NativeFilePage.key(b)));
  NativeExportPlan batch=NativeExportPlan.library(owner,"INBOX",library,selected);
  check(batch.refs.size()==2&&!batch.original&&batch.refs.get(0).uid.equals("42")&&batch.refs.get(1).uid.equals("43"));
  check(batch.refs.get(0).path.equals(batch.refs.get(1).path)); // The same MIME path in distinct messages is not a duplicate.
  NativeExportPlan retry=batch.failed(Collections.singletonList(new BulkAttachments.Failure(batch.refs.get(1),"same.pdf","download")));
  check(retry.owner.equals(owner)&&!retry.original&&retry.refs.size()==1&&retry.refs.get(0).uid.equals("43"));
  check(batch.failed(Collections.emptyList())==null);
  reject(()->batch.failed(Collections.singletonList(new BulkAttachments.Failure(new BulkAttachments.Ref("INBOX","99","7","1.2"),"x","download"))));
  reject(()->batch.failed(Arrays.asList(new BulkAttachments.Failure(batch.refs.get(1),"x","download"),new BulkAttachments.Failure(batch.refs.get(1),"x","download"))));
  a.getJSONObject("message").put("uid","999");selected.clear();check(batch.refs.get(0).uid.equals("42")&&batch.refs.size()==2);
  try{batch.refs.clear();throw new AssertionError();}catch(UnsupportedOperationException expected){}
  Set<String> choose=Collections.singleton(NativeFilePage.key(b));
  reject(()->NativeExportPlan.library(owner,"Archive",library,choose));
  reject(()->NativeExportPlan.library(owner,"INBOX",library,Collections.singleton("stale-key")));
  reject(()->NativeExportPlan.library(owner,"INBOX",library,Collections.emptySet()));
  reject(()->NativeExportPlan.library("foreign","INBOX",library,choose));
  reject(()->NativeExportPlan.library(owner,"INBOX",new JSONArray().put(b).put(b),choose));
  JSONObject foreign=new JSONObject(b.toString());foreign.getJSONObject("message").put("account","account-"+"b".repeat(64));
  reject(()->NativeExportPlan.library(owner,"INBOX",new JSONArray().put(foreign),choose));
  JSONObject changed=new JSONObject(b.toString());changed.getJSONObject("message").put("validity","8").put("uid","44");
  reject(()->NativeExportPlan.library(owner,"INBOX",new JSONArray().put(b).put(changed),new HashSet<>(Arrays.asList(NativeFilePage.key(b),NativeFilePage.key(changed)))));
  JSONArray full=new JSONArray();Set<String> keys=new HashSet<>();
  for(int i=1;i<=500;i++){JSONObject f=new JSONObject(b.toString());f.getJSONObject("message").put("uid",String.valueOf(i));full.put(f);keys.add(NativeFilePage.key(f));}
  check(NativeExportPlan.library(owner,"INBOX",full,keys).refs.size()==500);
  keys.add("extra");reject(()->NativeExportPlan.library(owner,"INBOX",full,keys));
  Path root=Files.createTempDirectory("native-export-test"),cache=Files.createDirectory(root.resolve("cache"));
  try{
   Path empty=Files.createFile(cache.resolve("empty"));check(NativeExportPlan.checkedFile(cache.toFile(),empty.toString()).length()==0);
   Path outside=Files.createFile(root.resolve("outside"));reject(()->NativeExportPlan.checkedFile(cache.toFile(),outside.toString()));
   Files.createSymbolicLink(cache.resolve("link"),outside);reject(()->NativeExportPlan.checkedFile(cache.toFile(),cache.resolve("link").toString()));
   File large=cache.resolve("large").toFile();try(RandomAccessFile file=new RandomAccessFile(large,"rw")){file.setLength(AttachmentFiles.MAX_BYTES+1);}reject(()->NativeExportPlan.checkedFile(cache.toFile(),large.getPath()));
  }finally{try(java.util.stream.Stream<Path> paths=Files.walk(root)){paths.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.delete(p);}catch(IOException e){throw new RuntimeException(e);}});}}
  System.out.println("PASS NativeExportPlan: exact scope, multi-message selection and 500 limit, failed-only retry, immutable paths, stale/mixed/duplicate rejection, picker isolation and downloaded-file bounds");
 }
}
