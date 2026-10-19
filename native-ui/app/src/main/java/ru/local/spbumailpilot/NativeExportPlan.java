package ru.local.spbumailpilot;
import org.json.*;
import java.io.*;
import java.util.*;

/** Immutable exact-source plan. No credentials, guessed MIME paths, or folder/UID fallback. */
final class NativeExportPlan {
    final String owner;final boolean original;final List<BulkAttachments.Ref> refs;
    private NativeExportPlan(String owner,boolean original,List<BulkAttachments.Ref> refs){this.owner=owner;this.original=original;this.refs=Collections.unmodifiableList(refs);}
    NativeExportPlan failed(List<BulkAttachments.Failure> failures){
        if(failures==null||failures.size()>refs.size())throw new IllegalArgumentException("Invalid failures");
        Set<String> failed=new HashSet<>();Set<String> planned=new HashSet<>();for(BulkAttachments.Ref ref:refs)planned.add(ref.key());
        for(BulkAttachments.Failure f:failures){
            if(f==null||f.ref==null||!planned.contains(f.ref.key())||!failed.add(f.ref.key()))throw new IllegalArgumentException("Unknown failed attachment");
        }
        if(failed.isEmpty())return null;
        List<BulkAttachments.Ref> retry=new ArrayList<>();for(BulkAttachments.Ref ref:refs)if(failed.contains(ref.key()))retry.add(ref);
        return new NativeExportPlan(owner,original,retry);
    }
    static NativeExportPlan from(String owner,String folder,String uid,String validity,JSONArray files,boolean original)throws Exception{
        if(!NativeOpenTarget.account(owner))throw new IllegalArgumentException("Invalid account");
        List<BulkAttachments.Ref> refs=new ArrayList<>();
        if(original)refs.add(new BulkAttachments.Ref(folder,uid,validity,"root"));
        else {
            if(files==null||files.length()==0||files.length()>BulkAttachments.MAX_FILES)throw new IllegalArgumentException("Выберите от 1 до 500 вложений.");
            Set<String> seen=new HashSet<>();
            for(int i=0;i<files.length();i++){
                Object raw=files.getJSONObject(i).opt("path");if(!(raw instanceof String)||!seen.add((String)raw))throw new IllegalArgumentException("Неверные или повторяющиеся ссылки на вложения.");
                refs.add(new BulkAttachments.Ref(folder,uid,validity,(String)raw));
            }
        }
        return new NativeExportPlan(owner,original,refs);
    }
    /** Only selected rows in the current filename-search scope may become a batch. */
    static NativeExportPlan library(String owner,String folder,JSONArray rows,Set<String> selected)throws Exception{
        if(!NativeOpenTarget.account(owner)||rows==null||rows.length()>NativeFilePage.LIMIT
            ||selected==null||selected.isEmpty()||selected.size()>BulkAttachments.MAX_FILES||selected.contains(null))
            throw new IllegalArgumentException("Выберите от 1 до 500 вложений текущего списка.");
        new BulkAttachments.Ref(folder,"1","1","root");
        Set<String> missing=new HashSet<>(selected);
        List<BulkAttachments.Ref> refs=new ArrayList<>();String validity=null;
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.getJSONObject(i);String key=NativeFilePage.key(row);
            if(!selected.contains(key))continue;
            if(!missing.remove(key))throw new IllegalArgumentException("Повторяющаяся ссылка на вложение.");
            JSONObject message=row.getJSONObject("message");
            if(!(message.opt("folder") instanceof String)||!folder.equals(message.getString("folder"))
                ||message.has("account")&&!owner.equals(message.opt("account"))
                ||!(message.opt("uid") instanceof String)||!(message.opt("validity") instanceof String)
                ||!(row.opt("path") instanceof String))throw new IllegalArgumentException("Область списка изменилась.");
            BulkAttachments.Ref ref=new BulkAttachments.Ref(folder,message.getString("uid"),message.getString("validity"),row.getString("path"));
            if(validity!=null&&!validity.equals(ref.validity))throw new IllegalArgumentException("Папка изменилась.");
            validity=ref.validity;refs.add(ref);
        }
        if(!missing.isEmpty())throw new IllegalArgumentException("Выбранные вложения больше не находятся в списке.");
        return new NativeExportPlan(owner,false,refs);
    }
    static File checkedFile(File directory,String path)throws Exception{
        if(path==null)throw new IOException("Missing downloaded file");File file=new File(path).getCanonicalFile();
        if(!directory.getCanonicalFile().equals(file.getParentFile())||!file.isFile()||file.length()>AttachmentFiles.MAX_BYTES)throw new IOException("Invalid downloaded file");
        return file;
    }
}
