package ru.local.spbumailpilot;
import org.json.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
public final class NativeEventEquivalence {
    public static void main(String[] args)throws Exception {
        JSONArray cases=new JSONArray(new String(Files.readAllBytes(Paths.get(args[0])),StandardCharsets.UTF_8)),output=new JSONArray();
        for(int i=0;i<cases.length();i++){
            JSONObject c=cases.getJSONObject(i),stored=new JSONObject(c.getJSONObject("state").toString());
            if(!c.optBoolean("fail")&&!c.getString("operation").equals("cancel-delete"))try {
                JSONObject expected=stored.getJSONArray("events").getJSONObject(0);
                JSONObject replacement=c.getString("operation").equals("edit")?new JSONObject(c.getJSONObject("replacement").toString()):null;
                if(replacement!=null)replacement.put("title",replacement.getString("title").trim());
                stored=NativeWorkspace.change(stored,"events",expected,replacement);
            }catch(IllegalArgumentException invalid){/* A rejected input leaves the old snapshot intact. */}
            JSONObject projected=new JSONObject();for(String key:new String[]{"events","eventMailLinks","tasks","notes","unknown"})projected.put(key,stored.get(key));
            output.put(new JSONObject().put("name",c.getString("name")).put("state",projected));
        }
        Files.write(Paths.get(args[1]),output.toString(2).getBytes(StandardCharsets.UTF_8));
        System.out.println("Native event cases executed: "+output.length());
    }
}
