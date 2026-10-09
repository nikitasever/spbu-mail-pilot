package ru.local.spbumailpilot;
import org.json.JSONObject;
final class MailNoticeTarget {
    final String account,uid,validity;
    private MailNoticeTarget(String account,String uid,String validity){this.account=account;this.uid=uid;this.validity=validity;}
    static MailNoticeTarget parse(String json){try{if(json==null||json.length()>1024)return null;JSONObject o=new JSONObject(json);String account=o.getString("account"),uid=o.getString("uid"),validity=o.getString("validity");if(!account.matches("account-[0-9a-f]{64}")||!uid.matches("[1-9][0-9]{0,19}")||!validity.matches("[1-9][0-9]{0,19}"))return null;return new MailNoticeTarget(account,uid,validity);}catch(Exception error){return null;}}
    JSONObject reference()throws Exception{return new JSONObject().put("folder","INBOX").put("uid",uid).put("validity",validity);}
    String encode()throws Exception{return reference().put("account",account).toString();}
}
