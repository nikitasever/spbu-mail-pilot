package ru.local.spbumailpilot;
import org.json.*;
public final class NativeOpenTargetTest {
    static void check(boolean value){if(!value)throw new AssertionError();}
    public static void main(String[] args)throws Exception {
        String owner="account-"+"a".repeat(64),other="account-"+"b".repeat(64);
        String mail=new JSONObject().put("account",owner).put("folder","Проекты/2026").put("uid","42").put("validity","7").toString();
        NativeOpenTarget target=NativeOpenTarget.from(mail,null,null,false,null);
        check(target!=null&&target.kind.equals("mail")&&target.mail.folder.equals("Проекты/2026"));
        check(NativeOpenTarget.parse(target.encode()).mail.uid.equals("42"));
        check(NativeOpenTarget.from(mail,"calendar",owner,false,null)==null);
        check(NativeOpenTarget.from("bad",null,null,false,null)==null);
        check(NativeOpenTarget.from(null,"unknown",owner,false,null)==null);
        check(NativeOpenTarget.from(null,"calendar","invalid",false,null)==null);
        check(NativeOpenTarget.from(null,null,null,true,owner).account.equals(owner));
        check(NativeOpenTarget.from(null,"mail",owner,false,null).kind.equals("mailbox"));
        check(NativeOpenTarget.from(null,"calendar",owner,false,null).kind.equals("calendar"));
        check(NativeOpenTarget.parse(new JSONObject(target.encode()).put("account",other).toString())==null);
        check(NativeOpenTarget.parse(new JSONObject().put("kind","profile").put("account",owner).toString())==null);
        check(NativeOpenTarget.parse("x".repeat(8193))==null);
        check(!target.encode().contains("password")&&!target.encode().contains("subject"));
        System.out.println("PASS native intent targets: bounded internal mail/task/widget routes, exact owner/folder/UID/validity, ambiguous/foreign/corrupt rejection, compact navigation only");
    }
}
