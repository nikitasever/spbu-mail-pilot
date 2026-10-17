package ru.local.spbumailpilot;
import org.json.*;
import java.io.*;
import java.util.*;

/** Immutable exact-source plan. No credentials, guessed MIME paths, or folder/UID fallback. */
final class NativeExportPlan {
    final String owner;final boolean original;final List<BulkAttachments.Ref> refs;
    private NativeExportPlan(String owner,boolean original,List<BulkAttachments.Ref> refs){this.owner=owner;this.original=original;this.refs=Collections.unmodifiableList(refs);}
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
    static File checkedFile(File directory,String path)throws Exception{
        if(path==null)throw new IOException("Missing downloaded file");File file=new File(path).getCanonicalFile();
        if(!directory.getCanonicalFile().equals(file.getParentFile())||!file.isFile()||file.length()>AttachmentFiles.MAX_BYTES)throw new IOException("Invalid downloaded file");
        return file;
    }
}
