package ru.local.spbumailpilot;
import org.json.*;
public final class NativeCategoryRulesTest {
    interface Check{void run()throws Exception;}
    static void check(boolean value){if(!value)throw new AssertionError();}
    static void reject(Check call)throws Exception{try{call.run();}catch(Exception expected){return;}throw new AssertionError("Expected refusal");}
    public static void main(String[] args)throws Exception {
        JSONObject empty=new JSONObject().put("unknown",17);JSONArray defaults=NativeCategoryRules.list(empty);
        check(defaults.length()==5&&defaults.getJSONObject(0).getString("id").equals("requests"));
        JSONObject requests=new JSONObject(defaults.getJSONObject(0).toString()).put("senders","HELP@SPBU.RU").put("domains","@example.org").put("subjects","Приглашение, ticket").put("notify",false);
        JSONObject state=NativeCategoryRules.change(empty,"requests",null,requests);
        check(!empty.has("mailCategories")&&state.getInt("unknown")==17&&state.getJSONArray("tasks").length()==0);
        MailCategories matcher=new MailCategories(NativeCategoryRules.list(state));
        check(matcher.match("help@spbu.ru","").getString("id").equals("requests"));
        check(matcher.match("user@example.org","").getString("id").equals("requests"));
        check(matcher.match("x@unrelated.org","ПРИГЛАШЕНИЕ").getString("id").equals("requests")); // first matching category
        check(matcher.match("x@sub.example.org","")==null);
        check(!matcher.allowed("help@spbu.ru","")&&matcher.allowed("x@unrelated.org","обычное письмо"));
        JSONObject partial=new JSONObject().put("id","requests").put("notify",false);
        check(!new MailCategories(new JSONArray().put(partial)).allowed("nobody@example.org","ticket 5"));
        check(!partial.has("subjects"));
        JSONObject old=NativeCategoryRules.raw(state,"requests");JSONObject events=new JSONObject(defaults.getJSONObject(2).toString()).put("name","Мероприятия");
        JSONObject parallel=NativeCategoryRules.change(state,"events",null,events);
        JSONObject updated=NativeCategoryRules.change(parallel,"requests",old,new JSONObject(requests.toString()).put("enabled",false));
        check(NativeCategoryRules.list(updated).getJSONObject(2).getString("name").equals("Мероприятия"));
        check(new MailCategories(NativeCategoryRules.list(updated)).allowed("help@spbu.ru",""));
        reject(()->NativeCategoryRules.change(updated,"requests",old,requests));
        reject(()->NativeCategoryRules.change(state,"rector",null,requests));
        reject(()->NativeCategoryRules.validate(new JSONObject(requests.toString()).put("domains","https://spbu.ru")));
        reject(()->NativeCategoryRules.validate(new JSONObject(requests.toString()).put("senders","broken-address")));
        reject(()->NativeCategoryRules.validate(new JSONObject(requests.toString()).put("subjects","x".repeat(1001))));
        reject(()->NativeCategoryRules.validate(new JSONObject(requests.toString()).put("enabled","false")));
        reject(()->NativeCategoryRules.list(new JSONObject().put("mailCategories",new JSONArray().put(requests).put(requests))));
        JSONObject unknown=new JSONObject(state.toString());unknown.getJSONArray("mailCategories").put(new JSONObject().put("id","future-category").put("custom",true));
        JSONObject kept=NativeCategoryRules.change(unknown,"requests",old,requests);
        check(kept.getJSONArray("mailCategories").getJSONObject(1).getBoolean("custom"));
        check(NativeCategoryRules.summary(requests).contains("Тема: приглашение, ticket")&&NativeCategoryRules.summary(requests).contains("От: help@spbu.ru"));
        System.out.println("PASS native categories: defaults, ordered OR rules, exact domains/addresses, shared notification matcher, record CAS/concurrent edits, validation, unknown data and summary");
    }
}
