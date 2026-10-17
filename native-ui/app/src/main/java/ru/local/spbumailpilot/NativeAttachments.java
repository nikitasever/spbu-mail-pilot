package ru.local.spbumailpilot;

import org.json.*;
import java.io.File;
import java.util.HashSet;

/** Opaque, account/draft-scoped files, compatible with the existing MIME sender. */
final class NativeAttachments {
    static void validate(JSONArray files)throws Exception{
        if(files==null||files.length()>10)throw new IllegalArgumentException("Attachment count");
        HashSet<String> tokens=new HashSet<>();
        for(int i=0;i<files.length();i++){
            JSONObject file=files.getJSONObject(i);String token=file.getString("token"),name=file.getString("name");long size=file.getLong("size");
            if(!token.startsWith("mail-")||token.length()>240||token.matches("(?s).*[\\p{Cntrl}/\\\\].*")||!tokens.add(token))throw new IllegalArgumentException("Attachment token");
            if(!name.equals(AttachmentFiles.safeName(name))||size<0||size>AttachmentFiles.MAX_BYTES)throw new IllegalArgumentException("Attachment metadata");
        }
    }
    static JSONArray append(JSONArray current,JSONArray added)throws Exception{
        JSONArray result=new JSONArray(current.toString());for(int i=0;i<added.length();i++)result.put(added.getJSONObject(i));validate(result);return result;
    }
    static JSONArray remove(JSONArray current,String token)throws Exception{
        JSONArray result=new JSONArray();for(int i=0;i<current.length();i++){JSONObject entry=current.getJSONObject(i);if(!token.equals(entry.getString("token")))result.put(entry);}validate(result);return result;
    }
    static File directory(File base,NativeDraft draft)throws Exception{
        // NativeDraft has already validated account hash and UUID; canonical checks prevent symlink escapes.
        File root=base.getCanonicalFile(),owner=new File(root,draft.owner).getCanonicalFile(),result=new File(owner,draft.mail.getString("id")).getCanonicalFile();
        if(!root.equals(owner.getParentFile())||!owner.equals(result.getParentFile())||!owner.getName().equals(draft.owner)||!result.getName().equals(draft.mail.getString("id")))throw new IllegalArgumentException("Attachment location");
        return result;
    }
    static void cleanup(File directory,JSONArray keep)throws Exception{
        validate(keep);HashSet<String> tokens=new HashSet<>();for(int i=0;i<keep.length();i++)tokens.add(keep.getJSONObject(i).getString("token"));
        File base=directory.getCanonicalFile();File[] files=base.listFiles();if(files==null)return;
        for(File file:files)if(file.getName().startsWith("mail-")&&!tokens.contains(file.getName())&&base.equals(file.getCanonicalFile().getParentFile()))file.delete();
    }
    static boolean clearCopies(File directory)throws Exception{
        cleanup(directory,new JSONArray());
        File[] remaining=directory.listFiles();
        if(remaining==null)return !directory.exists();
        for(File file:remaining)if(file.getName().startsWith("mail-"))return false;
        // Non-attachment files, if any, are deliberately left alone.
        return true;
    }
}
