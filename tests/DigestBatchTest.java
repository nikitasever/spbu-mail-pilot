package ru.local.spbumailpilot;

import org.json.*;

public class DigestBatchTest {
    static void rejected(String json)throws Exception{try{DigestBatch.references(json);throw new AssertionError("Accepted invalid batch");}catch(IllegalArgumentException|JSONException expected){}}
    public static void main(String[] args)throws Exception{
        JSONObject ref=new JSONObject().put("uid","7").put("validity","9");
        if(DigestBatch.references(new JSONArray().put(ref).toString()).length()!=1)throw new AssertionError();
        rejected("[]");rejected(new JSONArray().put(ref).put(ref).toString());rejected("[{\"uid\":\"1\\r\\nBAD\",\"validity\":\"9\"}]");
        JSONArray tooMany=new JSONArray();for(int i=1;i<=21;i++)tooMany.put(new JSONObject().put("uid",Integer.toString(i)).put("validity","9"));rejected(tooMany.toString());
        JSONObject message=new JSONObject().put("subject",new String(new char[1000]).replace('\0','x')).put("seen",false);
        JSONObject raw=new JSONObject().put("message",message).put("text",new String(new char[6000]).replace('\0','y')).put("attachments",new JSONArray().put("private attachment metadata"));
        JSONObject item=DigestBatch.item("INBOX",ref,raw);
        if(item.getString("text").length()!=4000||!item.getBoolean("truncated")||item.getJSONObject("message").getString("subject").length()!=500||item.has("attachments")||item.getJSONObject("message").getBoolean("seen"))throw new AssertionError("Bounded text-only batch");
        if(!DigestBatch.cut("a\uD83D\uDE00b",2).equals("a"))throw new AssertionError("Broken surrogate");
        System.out.println("PASS: digest batches limited to 20, duplicate/control refs refused, bounded text/headers, no attachment data, read status unchanged and Unicode cut preserved.");
    }
}
