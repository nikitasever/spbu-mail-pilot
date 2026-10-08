package ru.local.spbumailpilot;
import org.json.JSONObject;
/** Missing preference preserves existing behavior; corrupted state fails closed. */
final class AiPolicy {
 static boolean enabled(String state){try{JSONObject settings=new JSONObject(state).optJSONObject("settings");return settings==null||settings.optBoolean("neuralAnalysis",true);}catch(Exception invalid){return false;}}
}
