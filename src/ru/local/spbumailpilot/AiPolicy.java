package ru.local.spbumailpilot;
import org.json.JSONObject;
/** Missing preference preserves existing behavior; corrupted state fails closed. */
final class AiPolicy {
 static boolean enabled(String state){try{JSONObject data=new JSONObject(state);if(!data.has("settings"))return true;JSONObject settings=data.getJSONObject("settings");return !settings.has("neuralAnalysis")||Boolean.TRUE.equals(settings.opt("neuralAnalysis"));}catch(Exception invalid){return false;}}
}
