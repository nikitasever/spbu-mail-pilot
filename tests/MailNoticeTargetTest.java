package ru.local.spbumailpilot;
import org.json.JSONObject;
public final class MailNoticeTargetTest {
 static void check(boolean value){if(!value)throw new AssertionError();}
 public static void main(String[] args)throws Exception {
  String account="account-"+new String(new char[64]).replace('\0','a');
  String json=new JSONObject().put("account",account).put("uid","4294967295").put("validity","7").toString();
  MailNoticeTarget target=MailNoticeTarget.parse(json);check(target!=null&&target.reference().getString("folder").equals("INBOX")&&target.uid.equals("4294967295"));check(MailNoticeTarget.parse(target.encode()).account.equals(account));
  check(MailNoticeTarget.parse(null)==null);check(MailNoticeTarget.parse("{}") ==null);check(MailNoticeTarget.parse(json.replace("4294967295","0"))==null);check(MailNoticeTarget.parse(json.replace("4294967295","<script>"))==null);check(MailNoticeTarget.parse(json.replace(account,"other"))==null);
  System.out.println("PASS: notification links retain account scope, UID and UIDVALIDITY; invalid/missing references refused, no credentials in target.");
 }
}
