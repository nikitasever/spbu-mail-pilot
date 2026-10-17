package ru.local.spbumailpilot;
import org.json.*;

/** Only bounded internal navigation keys survive process recreation. No message/body/credentials. */
final class NativeOpenTarget {
    final String kind,account;final MailNoticeTarget mail;
    private NativeOpenTarget(String kind,String account,MailNoticeTarget mail){this.kind=kind;this.account=account;this.mail=mail;}
    static boolean account(String value){return value!=null&&value.matches("account-[0-9a-f]{64}");}
    static NativeOpenTarget from(String mail,String widget,String widgetAccount,boolean tasks,String tasksAccount){
        int routes=(mail!=null?1:0)+(widget!=null?1:0)+(tasks?1:0);if(routes!=1)return null;
        if(mail!=null){MailNoticeTarget target=MailNoticeTarget.parse(mail);return target==null?null:new NativeOpenTarget("mail",target.account,target);}
        String owner=tasks?tasksAccount:widgetAccount;if(owner==null)owner="";if(!owner.isEmpty()&&!account(owner))return null;
        if(tasks)return new NativeOpenTarget("tasks",owner,null);
        if(!widget.equals("mail")&&!widget.equals("calendar"))return null;
        return new NativeOpenTarget(widget.equals("mail")?"mailbox":"calendar",owner,null);
    }
    String encode()throws Exception {
        JSONObject value=new JSONObject().put("kind",kind).put("account",account);if(mail!=null)value.put("mail",new JSONObject(mail.encode()));return value.toString();
    }
    static NativeOpenTarget parse(String encoded){try{
        if(encoded==null||encoded.length()>8192)return null;JSONObject value=new JSONObject(encoded);String kind=value.getString("kind"),owner=value.getString("account");
        if(kind.equals("mail")){MailNoticeTarget mail=MailNoticeTarget.parse(value.getJSONObject("mail").toString());return mail!=null&&mail.account.equals(owner)?new NativeOpenTarget(kind,owner,mail):null;}
        if(!owner.isEmpty()&&!account(owner)||!kind.equals("mailbox")&&!kind.equals("calendar")&&!kind.equals("tasks"))return null;
        return new NativeOpenTarget(kind,owner,null);
    }catch(Exception invalid){return null;}}
}
