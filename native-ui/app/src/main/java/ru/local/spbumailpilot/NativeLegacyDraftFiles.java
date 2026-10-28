package ru.local.spbumailpilot;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.function.LongConsumer;

/** Copies all files or throws; caller keeps committed copies and cleans only unreferenced files. */
final class NativeLegacyDraftFiles {
    static JSONArray copy(File source,File target,JSONArray entries,List<File> created,Runnable alive,LongConsumer progress)throws Exception{
        entries=NativeLegacyDraftPlan.files(new JSONObject().put("attachments",entries));
        JSONArray copies=new JSONArray();long completed=0;
        for(int i=0;i<entries.length();i++){
            alive.run();JSONObject entry=entries.getJSONObject(i);
            File original=MailSender.file(source,entry.getString("token"));if(original.length()!=entry.getLong("size"))throw new IOException("Source file size changed");
            final long prior=completed;File copy;
            try(InputStream input=new FileInputStream(original)){copy=AttachmentFiles.copy(input,target,entry.getString("name"),AttachmentFiles.MAX_BYTES,bytes->{alive.run();progress.accept(prior+bytes);});}
            created.add(copy);if(copy.length()!=entry.getLong("size"))throw new IOException("Copied file size changed");
            try(RandomAccessFile durable=new RandomAccessFile(copy,"rw")){durable.getFD().sync();}
            copies.put(new JSONObject(entry.toString()).put("token",copy.getName()).put("size",copy.length()));completed+=copy.length();
        }
        return copies;
    }
}
