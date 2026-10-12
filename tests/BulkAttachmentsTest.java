package ru.local.spbumailpilot;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BulkAttachmentsTest {
    static void check(boolean value){if(!value)throw new AssertionError();}
    static BulkAttachments.Ref ref(int n){return new BulkAttachments.Ref("INBOX",Integer.toString(n),"9","1.0");}
    static final class MemoryDestination implements BulkAttachments.Destination {
        final Map<String,ByteArrayOutputStream> files=new LinkedHashMap<>();int deleted,finished;boolean fail,finishFailure,closeFailure;int failAfter=-1;
        public Set<String> names(){return new HashSet<>(files.keySet());}
        public BulkAttachments.Target create(String name,String mime){check(!files.containsKey(name));ByteArrayOutputStream stream=new ByteArrayOutputStream();files.put(name,stream);
            return new BulkAttachments.Target(){
                public OutputStream open(){return new OutputStream(){
                    public void write(int b)throws IOException{if(fail||(failAfter>=0&&stream.size()>=failAfter))throw new IOException("Destination full");stream.write(b);}
                    public void close()throws IOException{if(closeFailure)throw new IOException("Destination close failed");}
                };}
                public void finish()throws IOException{if(finishFailure)throw new BulkAttachments.UnsupportedDestination();finished++;}
                public void delete(){files.remove(name);deleted++;}
            };
        }
    }
    public static void main(String[] args)throws Exception{
        File source=File.createTempFile("batch-source-",".txt");try{
            try(OutputStream out=new FileOutputStream(source)){out.write(new byte[200000]);}
            MemoryDestination dest=new MemoryDestination();dest.files.put("same.txt",new ByteArrayOutputStream());
            List<BulkAttachments.Ref> refs=new ArrayList<>();for(int n=1;n<=50;n++)refs.add(ref(n));refs.add(ref(1));
            int[] active={0},max={0},progress={0};
            BulkAttachments.Result result=BulkAttachments.run(refs,(r,c,p)->{active[0]++;max[0]=Math.max(max[0],active[0]);p.accept(source.length());active[0]--;return new BulkAttachments.Download(source,"../same.txt","text/plain");},dest,()->false,(n,t,name,stage,b,size)->{progress[0]++;check(n<=t);});
            check(result.saved==50&&result.total==50&&!result.cancelled&&result.failures.isEmpty()&&max[0]==1&&progress[0]>50);
            check(dest.finished==50);
            check(dest.files.size()==51&&dest.files.containsKey("same (51).txt")&&dest.files.get("same.txt").size()==0);
            check(BulkAttachments.uniqueName("../a\\b.pdf",new HashSet<>()).equals("b.pdf"));
            check(BulkAttachments.uniqueName("..",new HashSet<>()).equals("Вложение"));
            check(!BulkAttachments.uniqueName("a\n.pdf",new HashSet<>()).contains("\n"));
            for(String path:new String[]{"../../1","1..0","1/0","","root.0"}){
                boolean invalid=false;try{new BulkAttachments.Ref("INBOX","1","9",path);}catch(IllegalArgumentException expected){invalid=true;}check(invalid);
            }
            MemoryDestination failures=new MemoryDestination();
            result=BulkAttachments.run(Arrays.asList(ref(1),ref(2),ref(3)),(r,c,p)->{if(r.uid.equals("2"))throw new IOException("No network");return new BulkAttachments.Download(source,"file.txt","text/plain");},failures,()->false,(a,b,c,d,e,f)->{});
            check(result.saved==2&&result.failures.size()==1&&result.failures.get(0).ref.uid.equals("2"));
            BulkAttachments.Result retried=BulkAttachments.run(Arrays.asList(result.failures.get(0).ref),(r,c,p)->new BulkAttachments.Download(source,"file.txt","text/plain"),failures,()->false,(a,b,c,d,e,f)->{});
            check(retried.saved==1&&failures.files.size()==3&&failures.files.get("file.txt").size()==source.length());
            AtomicBoolean cancel=new AtomicBoolean(false);MemoryDestination cancelled=new MemoryDestination();
            result=BulkAttachments.run(Arrays.asList(ref(1),ref(2),ref(3)),(r,c,p)->new BulkAttachments.Download(source,"file.txt","text/plain"),cancelled,cancel::get,(n,t,name,stage,bytes,size)->{if(n==1&&stage.equals("save")&&bytes>0)cancel.set(true);});
            check(result.saved==1&&result.cancelled&&result.failures.isEmpty()&&cancelled.deleted==1&&cancelled.files.size()==1&&cancelled.files.get("file.txt").size()==source.length());
            result=BulkAttachments.run(Arrays.asList(ref(1)),(r,c,p)->{throw new AssertionError("Cancelled download started");},new MemoryDestination(),()->true,(a,b,c,d,e,f)->{});check(result.cancelled&&result.saved==0);
            cancel.set(false);MemoryDestination downloadCancelled=new MemoryDestination();
            result=BulkAttachments.run(Arrays.asList(ref(1)),(r,c,p)->{p.accept(10);BulkAttachments.check(c);throw new AssertionError("Cancelled download continued");},downloadCancelled,cancel::get,(a,b,c,stage,e,f)->{if(stage.equals("download")&&e>0)cancel.set(true);});
            check(result.cancelled&&result.saved==0&&downloadCancelled.files.isEmpty());
            MemoryDestination broken=new MemoryDestination();broken.fail=true;
            result=BulkAttachments.run(Arrays.asList(ref(1)),(r,c,p)->new BulkAttachments.Download(source,"file.txt","text/plain"),broken,()->false,(a,b,c,d,e,f)->{});check(result.saved==0&&result.failures.size()==1&&broken.deleted==1&&broken.files.isEmpty());
            for(boolean atClose:new boolean[]{false,true}){
                MemoryDestination full=new MemoryDestination();full.closeFailure=atClose;full.failAfter=atClose?-1:70000;
                result=BulkAttachments.run(Arrays.asList(ref(1)),(r,c,p)->new BulkAttachments.Download(source,"file.txt","text/plain"),full,()->false,(a,b,c,d,e,f)->{});
                check(result.saved==0&&result.failures.size()==1&&full.deleted==1&&full.finished==0&&full.files.isEmpty());
            }
            try{BulkAttachments.run(Arrays.asList(ref(1)),(r,c,p)->{throw new AssertionError("Download started without folder access");},new BulkAttachments.Destination(){
                public Set<String> names()throws IOException{throw new IOException("Folder permission revoked");}
                public BulkAttachments.Target create(String name,String mime){throw new AssertionError();}
            },()->false,(a,b,c,d,e,f)->{});throw new AssertionError("Folder access failure ignored");}catch(IOException expected){}
            MemoryDestination cannotFinish=new MemoryDestination();cannotFinish.finishFailure=true;
            result=BulkAttachments.run(Arrays.asList(ref(1)),(r,c,p)->new BulkAttachments.Download(source,"file.txt","text/plain"),cannotFinish,()->false,(a,b,c,d,e,f)->{});
            check(result.saved==0&&result.failures.size()==1&&cannotFinish.deleted==1&&cannotFinish.finished==0&&cannotFinish.files.isEmpty()&&result.failures.get(0).reason.equals("unsupported_destination"));
            File tooLarge=File.createTempFile("batch-large-",".txt");try{try(RandomAccessFile file=new RandomAccessFile(tooLarge,"rw")){file.setLength(AttachmentFiles.MAX_BYTES+1);}result=BulkAttachments.run(Arrays.asList(ref(1)),(r,c,p)->new BulkAttachments.Download(tooLarge,"large.txt","text/plain",true),new MemoryDestination(),()->false,(a,b,c,d,e,f)->{});check(result.saved==0&&result.failures.size()==1&&!tooLarge.exists());}finally{tooLarge.delete();}
            System.out.println("PASS bulk attachment batch: 50 sequential files, duplicate names, invalid paths, partial failure, cancellation, size bound");
        }finally{source.delete();}
    }
}
