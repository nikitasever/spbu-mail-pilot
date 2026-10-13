package ru.local.spbumailpilot;
import org.json.JSONObject;
final class MailNoticeTarget {
    final String account,uid,validity,folder;
    private MailNoticeTarget(String account,String uid,String validity,String folder){this.account=account;this.uid=uid;this.validity=validity;this.folder=folder;}
    static MailNoticeTarget parse(String json){try{if(json==null||json.length()>4096)return null;JSONObject o=new JSONObject(json);String account=o.getString("account"),uid=o.getString("uid"),validity=o.getString("validity"),folder=o.optString("folder","INBOX");if(!account.matches("account-[0-9a-f]{64}")||!uid.matches("[1-9][0-9]{0,19}")||!validity.matches("[1-9][0-9]{0,19}"))return null;if(folder.isEmpty()||folder.length()>1024||folder.matches("(?s).*[\\p{Cntrl}].*"))return null;return new MailNoticeTarget(account,uid,validity,folder);}catch(Exception error){return null;}}
    JSONObject reference()throws Exception{return new JSONObject().put("folder",folder).put("uid",uid).put("validity",validity);}
    String encode()throws Exception{return reference().put("account",account).toString();}
}

