package ru.local.spbumailpilot;
import org.json.JSONObject;

/** The endpoint stays Google-owned; only a bounded model identifier is configurable. */
final class AiConfiguration {
    static final String DEFAULT_MODEL="gemini-3.5-flash-lite";
    static String modelId(String value){
        String id=value.trim();if(!id.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,119}"))throw new IllegalArgumentException("Введите идентификатор модели без адреса сервера, пробелов и models/.");return id;
    }
    static String model(JSONObject state)throws Exception {
        if(!state.has("settings"))return DEFAULT_MODEL;
        JSONObject options=state.getJSONObject("settings");
        if(!options.has("geminiModel"))return DEFAULT_MODEL;
        if(!(options.opt("geminiModel") instanceof String))throw new IllegalArgumentException("Invalid model identifier");
        return modelId(options.getString("geminiModel"));
    }
    static String key(String value){String key=value.trim();if(key.isEmpty()||key.length()>512||!key.matches("[A-Za-z0-9_.-]+"))throw new IllegalArgumentException("Проверьте ключ: от 1 до 512 символов, без пробелов.");return key;}
    static String endpoint(String model){return "https://generativelanguage.googleapis.com/v1beta/models/"+modelId(model)+":generateContent";}
}
