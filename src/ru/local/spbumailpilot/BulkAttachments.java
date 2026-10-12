package ru.local.spbumailpilot;

import java.io.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;

/** Sequential coordinator. A completed destination is never removed by cancellation. */
final class BulkAttachments {
    static final int MAX_FILES=500;
    static final class Ref {
        final String folder,uid,validity,path;
        long transferSize=-1;
        Ref(String folder,String uid,String validity,String path){
            if(folder==null||folder.isEmpty()||folder.length()>1024||folder.matches("(?s).*[\\p{Cntrl}].*")
                ||uid==null||!uid.matches("[1-9][0-9]{0,18}")||validity==null||!validity.matches("[1-9][0-9]{0,18}")
                ||path==null||path.length()>160||!path.matches("root|(?:[0-9]{1,5}|m)(?:\\.(?:[0-9]{1,5}|m)){0,12}"))throw new IllegalArgumentException("Invalid attachment reference");
            this.folder=folder;this.uid=uid;this.validity=validity;this.path=path;
        }
        String key(){return folder.length()+":"+folder+":"+uid+":"+validity+":"+path;}
    }
    static final class Download {
        final File file;final String name,mime;final boolean disposable;
        Download(File file,String name,String mime){this(file,name,mime,false);}
        Download(File file,String name,String mime,boolean disposable){this.file=file;this.name=name;this.mime=AttachmentFiles.mime(name==null?"":name,mime);this.disposable=disposable;}
    }
    interface Downloader { Download download(Ref ref,BooleanSupplier cancelled,LongConsumer progress)throws Exception; }
    interface Destination {
        Set<String> names()throws Exception;
        Target create(String name,String mime)throws Exception;
    }
    interface Target {
        OutputStream open()throws Exception;
        default void finish()throws Exception{}
        void delete()throws Exception;
    }
    interface Observer { void progress(int completed,int total,String name,String stage,long bytes,long size); }
    static final class Failure {
        final Ref ref;final String name,reason;
        Failure(Ref ref,String name,String reason){this.ref=ref;this.name=name;this.reason=reason;}
    }
    static final class Result {
        final int total,saved;final boolean cancelled;final List<Failure> failures;final List<String> names;
        Result(int total,int saved,boolean cancelled,List<Failure> failures,List<String> names){this.total=total;this.saved=saved;this.cancelled=cancelled;this.failures=Collections.unmodifiableList(failures);this.names=Collections.unmodifiableList(names);}
    }
    static final class UnsupportedDestination extends IOException { UnsupportedDestination(){super("Choose a folder provider that supports renaming documents");} }
    static final class Cancelled extends IOException { Cancelled(){super("Attachment batch cancelled");} }
    static void check(BooleanSupplier cancelled)throws Cancelled{if(cancelled.getAsBoolean())throw new Cancelled();}
    static Result run(List<Ref> requested,Downloader downloader,Destination destination,BooleanSupplier cancel,Observer observer)throws Exception{
        if(requested==null||requested.isEmpty()||requested.size()>MAX_FILES)throw new IllegalArgumentException("Invalid batch size");
        LinkedHashMap<String,Ref> unique=new LinkedHashMap<>();for(Ref ref:requested){if(ref==null)throw new IllegalArgumentException("Missing attachment");unique.put(ref.key(),ref);}
        List<Ref> refs=new ArrayList<>(unique.values());List<Failure> failures=new ArrayList<>();List<String> saved=new ArrayList<>();
        if(cancel.getAsBoolean())return new Result(refs.size(),0,true,failures,saved);
        Set<String> used=new HashSet<>(destination.names());boolean cancelled=false;int completed=0;
        for(Ref ref:refs){
            if(cancel.getAsBoolean()){cancelled=true;break;}
            Target target=null;Download downloaded=null;String name="Вложение";boolean complete=false;
            try{
                final int index=completed;observer.progress(index,refs.size(),name,"download",0,-1);
                downloaded=downloader.download(ref,cancel,bytes->{if(bytes>AttachmentFiles.MAX_BYTES*2)throw new IllegalArgumentException("Attachment too large");observer.progress(index,refs.size(),"Вложение","download",bytes,ref.transferSize);});
                check(cancel);
                if(downloaded==null||downloaded.file==null||!downloaded.file.isFile()||downloaded.file.length()>AttachmentFiles.MAX_BYTES)throw new IOException("Invalid downloaded attachment");
                name=uniqueName(downloaded.name,used);target=destination.create(name,downloaded.mime);if(target==null)throw new IOException("Cannot create destination");used.add(name);
                long size=downloaded.file.length(),bytes=0;observer.progress(index,refs.size(),name,"save",0,size);
                try(InputStream input=new FileInputStream(downloaded.file);OutputStream output=target.open()){
                    if(output==null)throw new IOException("Cannot open destination");byte[] buffer=new byte[65536];int n;
                    while((n=input.read(buffer))!=-1){check(cancel);bytes+=n;if(bytes>AttachmentFiles.MAX_BYTES*2)throw new IOException("Attachment too large");output.write(buffer,0,n);observer.progress(index,refs.size(),name,"save",bytes,size);}
                    check(cancel);
                }
                check(cancel);target.finish();complete=true;saved.add(name);
            }catch(Exception error){
                if(error instanceof Cancelled||cancel.getAsBoolean())cancelled=true;else failures.add(new Failure(ref,name,error instanceof UnsupportedDestination?"unsupported_destination":"transfer"));
            }finally{
                if(target!=null&&!complete)try{target.delete();}catch(Exception ignored){/* Provider may refuse partial cleanup. */}
                if(downloaded!=null&&downloaded.disposable&&downloaded.file!=null)downloaded.file.delete();
            }
            if(cancelled)break;completed++;observer.progress(completed,refs.size(),name,"finished",0,0);
        }
        return new Result(refs.size(),saved.size(),cancelled,failures,saved);
    }
    static String uniqueName(String value,Set<String> used){
        String name=AttachmentFiles.safeName(value);if(!name.isEmpty()&&Character.isLowSurrogate(name.charAt(0)))name=name.substring(1);
        int dot=name.lastIndexOf('.');String base=dot>0?name.substring(0,dot):name,extension=dot>0?name.substring(dot):"";
        String candidate=name;for(int n=2;used.contains(candidate);n++)candidate=base+" ("+n+")"+extension;return candidate;
    }
}
