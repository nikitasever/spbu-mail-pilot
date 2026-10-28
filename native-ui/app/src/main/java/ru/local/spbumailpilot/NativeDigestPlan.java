package ru.local.spbumailpilot;
import org.json.*;
import java.util.*;

/** Immutable source identities; source numbers always address the successfully prepared batch. */
final class NativeDigestPlan {
    static List<MailNoticeTarget> references(String owner,String folder,JSONArray rows)throws Exception {
        if(!NativeOpenTarget.account(owner)||!NativeFolders.canonical(folder).equals(folder)||rows.length()<1||rows.length()>20)throw new IllegalArgumentException("Выберите от 1 до 20 писем одной папки.");
        List<MailNoticeTarget> result=new ArrayList<>();Set<String> seen=new HashSet<>();String validity=null;
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.getJSONObject(i);
            for(String field:new String[]{"account","folder","uid","validity"})if(!(row.opt(field) instanceof String))throw new IllegalArgumentException("Invalid reference type");
            MailNoticeTarget ref=MailNoticeTarget.parse(row.toString());
            if(ref==null||!ref.account.equals(owner)||!ref.folder.equals(folder)||!positive(ref.uid)||!positive(ref.validity)||!seen.add(ref.uid)||validity!=null&&!validity.equals(ref.validity))throw new IllegalArgumentException("Stale or mixed batch");
            validity=ref.validity;result.add(ref);
        }
        return Collections.unmodifiableList(result);
    }
    private static boolean positive(String value){try{return Long.parseLong(value)>0;}catch(NumberFormatException invalid){return false;}}
    static JSONObject item(MailNoticeTarget ref,JSONObject body)throws Exception {
        JSONObject message=body.getJSONObject("message");
        for(String field:new String[]{"uid","validity","folder"})if(!(message.opt(field) instanceof String))throw new IllegalArgumentException("Missing message identity");
        if(!ref.uid.equals(message.getString("uid"))||!ref.validity.equals(message.getString("validity"))||!ref.folder.equals(message.getString("folder"))||!(body.opt("text") instanceof String))throw new IllegalArgumentException("Wrong message source");
        return DigestBatch.item(ref.folder,new JSONObject().put("uid",ref.uid).put("validity",ref.validity),body);
    }
    static JSONObject source(String owner,JSONArray prepared,int index)throws Exception {
        if(index<1||index>prepared.length())throw new IllegalArgumentException("Unknown source");
        JSONObject header=prepared.getJSONObject(index-1).getJSONObject("message");
        JSONObject result=new JSONObject(header.toString()).put("account",owner);
        references(owner,result.getString("folder"),new JSONArray().put(result));return result;
    }
}
