package ru.local.spbumailpilot;
import org.json.*;
import java.util.*;
public final class NativeDigestPlanTest {
    static final String OWNER="account-"+String.join("",Collections.nCopies(64,"a"));
    interface Attempt{void run()throws Exception;}
    static void check(boolean b){if(!b)throw new AssertionError();}
    static void reject(Attempt a)throws Exception{try{a.run();}catch(Exception expected){return;}throw new AssertionError("Expected rejection");}
    static JSONObject ref(String uid)throws Exception{return new JSONObject().put("account",OWNER).put("folder","INBOX").put("uid",uid).put("validity","7");}
    static JSONObject body(String uid)throws Exception{return new JSONObject().put("message",ref(uid).put("sender","fixture").put("subject","Тема "+uid).put("password","must-not-pass")).put("text",String.join("",Collections.nCopies(5000,"x"))).put("attachments",new JSONArray().put("must-not-pass"));}
    public static void main(String[] args)throws Exception {
        JSONArray input=new JSONArray().put(ref("1")).put(ref("2")).put(ref("3"));String before=input.toString();
        List<MailNoticeTarget> plan=NativeDigestPlan.references(OWNER,"INBOX",input);check(input.toString().equals(before)&&plan.size()==3);
        input.getJSONObject(0).put("uid","99");check(plan.get(0).uid.equals("1"));reject(()->plan.add(plan.get(0)));
        reject(()->NativeDigestPlan.references(OWNER,"INBOX",new JSONArray()));
        reject(()->NativeDigestPlan.references(OWNER,"INBOX",new JSONArray().put(ref("1")).put(ref("1"))));
        reject(()->NativeDigestPlan.references(OWNER,"INBOX",new JSONArray().put(ref("1").put("account","account-"+String.join("",Collections.nCopies(64,"b"))))));
        reject(()->NativeDigestPlan.references(OWNER,"INBOX",new JSONArray().put(ref("1").put("folder","Archive"))));
        reject(()->NativeDigestPlan.references(OWNER,"INBOX",new JSONArray().put(ref("1")).put(ref("2").put("validity","8"))));
        for(String invalid:new String[]{"0","-1","9223372036854775808","1\r\n"})reject(()->NativeDigestPlan.references(OWNER,"INBOX",new JSONArray().put(ref(invalid))));
        reject(()->NativeDigestPlan.references(OWNER,"INBOX",new JSONArray().put(ref("1").put("uid",1))));
        JSONArray large=new JSONArray();for(int i=1;i<=21;i++)large.put(ref(""+i));reject(()->NativeDigestPlan.references(OWNER,"INBOX",large));
        JSONObject original=body("1");String originalBefore=original.toString();JSONObject item=NativeDigestPlan.item(plan.get(0),original);
        check(item.getString("text").length()==4000&&item.getBoolean("truncated")&&!item.toString().contains("must-not-pass")&&original.toString().equals(originalBefore));
        reject(()->NativeDigestPlan.item(plan.get(0),body("3")));
        reject(()->NativeDigestPlan.item(plan.get(0),body("1").put("message",ref("1").put("folder","Archive"))));
        reject(()->NativeDigestPlan.item(plan.get(0),body("1").put("message",ref("1").put("validity","8"))));
        // Original selected uid2 failed; source number2 must address successfully prepared uid3.
        JSONArray prepared=new JSONArray().put(item).put(NativeDigestPlan.item(plan.get(2),body("3")));
        check(NativeDigestPlan.source(OWNER,prepared,2).getString("uid").equals("3"));
        reject(()->NativeDigestPlan.source(OWNER,prepared,0));reject(()->NativeDigestPlan.source(OWNER,prepared,3));
        System.out.println("PASS NativeDigestPlan: immutable exact batch/account/folder/UIDVALIDITY, positive IDs and size bounds, body-source validation, bounded payload, partial-batch source mapping");
    }
}
