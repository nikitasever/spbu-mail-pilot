package ru.local.spbumailpilot;
import org.json.*;
import java.util.*;
import java.text.Normalizer;

/** Same bounded literal rules as the inbox. No body fetches, regexp or external AI. */
final class MailCategories {
    static final String[] IDS={"requests","rector","events","study","personal"};
    static final String[] SUBJECTS={"заявк, ticket, otrs","ректор","мероприятие, приглашение","лекция, семинар, экзамен",""};
    final List<JSONObject> rules=new ArrayList<>();
    MailCategories(JSONArray saved){for(int n=0;n<IDS.length;n++){JSONObject rule=null;for(int i=0;saved!=null&&i<Math.min(50,saved.length());i++){JSONObject item=saved.optJSONObject(i);if(item!=null&&IDS[n].equals(item.optString("id"))){rule=item;break;}}try{if(rule==null)rule=new JSONObject().put("id",IDS[n]).put("subjects",SUBJECTS[n]);if(!rule.has("subjects"))rule=new JSONObject(rule.toString()).put("subjects",SUBJECTS[n]);rules.add(rule);}catch(Exception invalid){}}}
    static String normal(String value){return Normalizer.normalize(value==null?"":value,Normalizer.Form.NFC).toLowerCase(Locale.ROOT);}
    static List<String> parts(String value){ArrayList<String> result=new ArrayList<>();if(value==null||value.length()>1000)return result;for(String word:normal(value).split("[,;\\n]+")){word=word.trim();if(!word.isEmpty())result.add(word);if(result.size()>=50)break;}return result;}
    JSONObject match(String email,String subject){String address=normal(email).trim(),domain=address.substring(address.lastIndexOf('@')+1),title=normal(subject);for(JSONObject rule:rules){if(!rule.optBoolean("enabled",true))continue;boolean matches=parts(rule.optString("senders")).contains(address)||parts(rule.optString("contacts")).contains(address);for(String d:parts(rule.optString("domains")))if((d.startsWith("@")?d.substring(1):d).equals(domain))matches=true;for(String word:parts(rule.optString("subjects")))if(title.contains(word))matches=true;if(matches)return rule;}return null;}
    boolean allowed(String email,String subject){JSONObject category=match(email,subject);return category==null||category.optBoolean("notify",true);}
    boolean hasMuted(){for(JSONObject rule:rules)if(rule.optBoolean("enabled",true)&&!rule.optBoolean("notify",true))return true;return false;}
    JSONObject filter(JSONObject update)throws Exception{JSONArray metadata=update.optJSONArray("noticeMessages");if(!hasMuted()||metadata==null)return update;JSONObject filtered=new JSONObject(update.toString());int count=0;StringBuilder summary=new StringBuilder();ArrayList<JSONObject> matches=new ArrayList<>();for(int i=0;i<Math.min(500,metadata.length());i++){JSONObject m=metadata.optJSONObject(i);if(m!=null&&allowed(m.optString("email"),m.optString("subject")))matches.add(m);}count=matches.size();for(int i=matches.size()-1;i>=Math.max(0,matches.size()-3);i--){JSONObject m=matches.get(i);if(summary.length()>0)summary.append('\n');String sender=MailNotificationText.sender(m.optString("sender"),m.optString("email"));String subject=MailNotificationText.clean(m.optString("subject"),"Без темы",180);summary.append(sender).append(" · ").append(subject);if(i==matches.size()-1)filtered.put("sender",sender).put("email",m.optString("email")).put("subject",subject).put("openUid",m.optString("uid"));}if(count==0)filtered.remove("openUid");return filtered.put("count",count).put("summary",summary.toString());}
}
