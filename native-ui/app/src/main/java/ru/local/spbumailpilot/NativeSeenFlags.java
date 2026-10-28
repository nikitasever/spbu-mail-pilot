package ru.local.spbumailpilot;

import org.json.JSONObject;
import java.util.*;

/** Session-only confirmed flag overrides. Shared offline storage stays owned by the legacy client. */
final class NativeSeenFlags {
    private final Map<List<String>,Boolean> values=new HashMap<>();
    private static List<String> key(String owner,String folder,String validity,String uid){
        if(owner==null||!owner.matches("account-[0-9a-f]{64}")||folder==null||folder.isEmpty()||folder.length()>1024||folder.matches("(?s).*[\\p{Cntrl}].*")||
            validity==null||!validity.matches("[1-9][0-9]{0,19}")||uid==null||!uid.matches("[1-9][0-9]{0,19}"))
            throw new IllegalArgumentException("Invalid mail identity");
        return Arrays.asList(owner,folder,validity,uid);
    }
    static boolean confirmed(JSONObject response,boolean requested){
        if(response==null||!(response.opt("seen") instanceof Boolean)||response.optBoolean("seen")!=requested)
            throw new IllegalArgumentException("Read status not confirmed");
        return requested;
    }
    static String batchSummary(int total,int confirmed,int failed,boolean requested){
        if(total<=0||confirmed<0||failed<0||confirmed+failed>total)throw new IllegalArgumentException("Invalid batch counts");
        int unstarted=total-confirmed-failed;
        if(confirmed==total){
            return requested
                ? "Помечено прочитанными на сервере: "+confirmed+"."
                : "Помечено непрочитанными на сервере: "+confirmed+".";
        }
        return "Подтверждено: "+confirmed+" из "+total+". Ошибок: "+failed+", не начато: "+unstarted+". Обновите папку для проверки.";
    }
    synchronized void remember(String owner,String folder,String validity,String uid,boolean confirmed){values.put(key(owner,folder,validity,uid),confirmed);}
    synchronized boolean seen(String owner,String folder,String validity,String uid,boolean fallback){return values.getOrDefault(key(owner,folder,validity,uid),fallback);}
    synchronized void clearOwner(String owner){values.keySet().removeIf(k->k.get(0).equals(owner));}
    synchronized void clearFolder(String owner,String folder){values.keySet().removeIf(k->k.get(0).equals(owner)&&k.get(1).equals(folder));}
    synchronized void clear(){values.clear();}
}
