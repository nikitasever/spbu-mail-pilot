package ru.local.spbumailpilot;
import org.json.*;

/** Per-field compare-and-set; retains unknown keys and other account data. */
final class NativeGeneralSettings {
    static JSONObject options(JSONObject state)throws Exception {return state.has("settings")?state.getJSONObject("settings"):new JSONObject();}
    static void validate(String key,Object value) {
        if(key.equals("geminiModel")){if(!(value instanceof String)||!AiConfiguration.modelId((String)value).equals(value))throw new IllegalArgumentException("Invalid model");return;}
        if(key.equals("startScreen")){if(!"mail".equals(value)&&!"calendar".equals(value))throw new IllegalArgumentException("Unknown start screen");return;}
        if(key.equals("theme")){if(!(value instanceof String)||!java.util.Arrays.asList("system","light","dark").contains(value))throw new IllegalArgumentException("Unknown theme");return;}
        if(!java.util.Arrays.asList("compact","large","mailThreads","showTasks","offlineMail","mailNotifications","backgroundMail","taskNotifications","neuralAnalysis").contains(key)||!(value instanceof Boolean))throw new IllegalArgumentException("Unsupported setting");
    }
    static JSONObject change(JSONObject current,JSONObject expected,String key,Object value)throws Exception {
        validate(key,value);JSONObject now=options(current);
        if(!SharedStateMerge.same(now.opt(key),expected.opt(key))&&!SharedStateMerge.same(now.opt(key),value))throw new SharedStateMerge.Conflict();
        JSONObject next=new JSONObject(current.toString()),settings=new JSONObject(now.toString());
        return NativeSharedState.ready(next.put("settings",settings.put(key,value)));
    }
}
