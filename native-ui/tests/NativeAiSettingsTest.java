package ru.local.spbumailpilot;
import org.json.*;
public final class NativeAiSettingsTest {
    interface Attempt{void run()throws Exception;}
    static void check(boolean v){if(!v)throw new AssertionError();}
    static void reject(Attempt a)throws Exception{try{a.run();}catch(Exception expected){return;}throw new AssertionError("Expected rejection");}
    public static void main(String[] args)throws Exception {
        check(AiConfiguration.model(new JSONObject()).equals(AiConfiguration.DEFAULT_MODEL));
        check(AiConfiguration.modelId(" gemini-fixture-1 ").equals("gemini-fixture-1"));
        check(new java.net.URI(AiConfiguration.endpoint("fixture-model")).getHost().equals("generativelanguage.googleapis.com"));
        for(String invalid:new String[]{"","models/fixture","https://evil.invalid/","fixture?key=x","../fixture","x\r\nHost: evil","fixture:generateContent","a".repeat(121)})reject(()->AiConfiguration.modelId(invalid));
        for(String invalid:new String[]{"","fixture key","x\r\ny","a".repeat(513)})reject(()->AiConfiguration.key(invalid));
        check(AiConfiguration.key(" fixture_key-1.2 ").equals("fixture_key-1.2"));
        for(String invalid:new String[]{"broken","{\"settings\":[]}","{\"settings\":null}","{\"settings\":{\"neuralAnalysis\":\"true\"}}","{\"settings\":{\"neuralAnalysis\":1}}"})check(!AiPolicy.enabled(invalid));
        check(AiPolicy.enabled("{}")&&AiPolicy.enabled("{\"settings\":{}}"));
        JSONObject base=new JSONObject().put("settings",new JSONObject().put("theme","dark").put("neuralAnalysis",true)).put("custom",42);
        JSONObject expected=NativeGeneralSettings.options(base);
        JSONObject changed=NativeGeneralSettings.change(base,expected,"geminiModel","fixture-model");
        JSONObject disabled=NativeGeneralSettings.change(changed,expected,"neuralAnalysis",false);
        check(!AiPolicy.enabled(disabled.toString())&&AiConfiguration.model(disabled).equals("fixture-model")&&disabled.getInt("custom")==42);
        check(disabled.getJSONObject("settings").getString("theme").equals("dark")&&!base.getJSONObject("settings").has("geminiModel"));
        reject(()->NativeGeneralSettings.change(changed,expected,"geminiModel","another-model"));
        reject(()->NativeGeneralSettings.change(base,expected,"geminiModel","https://evil.invalid"));
        reject(()->NativeGeneralSettings.change(base,expected,"neuralAnalysis","false"));
        System.out.println("PASS NativeAiSettings: fixed Google origin, bounded identifiers/key, fail-closed analysis, per-field CAS and nonmutation");
    }
}
