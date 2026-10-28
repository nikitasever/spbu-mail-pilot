package ru.local.spbumailpilot;
import org.json.*;
import java.util.*;
public final class NativeWritingToolsTest {
    interface Check{void run()throws Exception;}
    static void check(boolean value){if(!value)throw new AssertionError();}
    static void reject(Check action)throws Exception{try{action.run();}catch(Exception expected){return;}throw new AssertionError("Expected rejection");}
    static JSONObject template(String id,String title,String body)throws Exception{return new JSONObject().put("id",id).put("title",title).put("body",body);}
    public static void main(String[] args)throws Exception{
        JSONObject config=new JSONObject().put("templates",new JSONArray().put(template("one-1","Ответ","Спасибо!"))
            .put(template("one-1","Дубликат","Не выбирать"))
            .put(template("empty","Пустой"," ")).put(template("oversize","Большой",new String(new char[100001]).replace('\0','x')))
            .put(new JSONObject().put("id","malformed").put("title","bad").put("body",17)))
            .put("settings",new JSONObject().put("signatureAuto",false).put("signatureText","С уважением,\nНикита"));
        List<NativeWritingTools.Template> templates=NativeWritingTools.templates(config);check(templates.size()==1&&templates.get(0).body.equals("Спасибо!"));
        check(NativeWritingTools.templates(new JSONObject()).isEmpty());
        JSONArray many=new JSONArray();for(int i=0;i<110;i++)many.put(template("t-"+i,"Шаблон "+i,"Текст"));
        check(NativeWritingTools.templates(new JSONObject().put("templates",many)).size()==100);
        String body="Добрый день!\n\nХвост";
        NativeWritingTools.Insertion inserted=NativeWritingTools.insert(body,"Спасибо!",14,14);
        check(inserted.body.equals("Добрый день!\n\nСпасибо!\n\nХвост"));
        check(inserted.cursor==22);
        NativeWritingTools.Insertion selected=NativeWritingTools.insert("left OLD right","new",8,5);
        check(selected.body.equals("left \n\nnew\n\n right"));
        check(selected.cursor==10);
        check(NativeWritingTools.insert("before","end",999,999).body.equals("before\n\nend"));
        check(NativeWritingTools.insert("after","start",-20,-20).body.equals("start\n\nafter"));
        String emoji="A\uD83D\uDE00B";
        String atEmoji=NativeWritingTools.insert(emoji,"text",2,2).body;
        check(atEmoji.equals("A\n\ntext\n\n\uD83D\uDE00B"));
        check(NativeWritingTools.insert(emoji,"replacement",2,3).body.equals("A\n\nreplacement\n\nB"));
        String limit=new String(new char[100000]).replace('\0','x');
        reject(()->NativeWritingTools.insert(limit,"1",limit.length(),limit.length()));check(limit.length()==100000);
        check(NativeWritingTools.insert(limit,"a",0,limit.length()).body.equals("a"));
        String signature=NativeWritingTools.signatureBlock(config);check(signature.startsWith("-- \n"));
        NativeWritingTools.Insertion signed=NativeWritingTools.signature("Ответ",config);
        check(signed.body.equals("Ответ\n\n"+signature));
        check(NativeWritingTools.signature(signed.body,config).body.equals(signed.body));
        check(NativeWritingTools.signature(signed.body+"\n\n",config).body.equals(signed.body+"\n\n"));
        check(NativeWritingTools.signature("Ответ",new JSONObject()).body.equals("Ответ"));
        check(NativeWritingTools.signatureBlock(new JSONObject().put("settings",new JSONObject().put("signatureText",42))).isEmpty());
        String owner="account-"+new String(new char[64]).replace('\0','a');
        NativeDraft draft=NativeDraft.create(owner,null,"");JSONObject mail=new JSONObject(draft.mail.toString()).put("to","recipient@example.invalid").put("body",signed.body);
        NativeDraft withText=new NativeDraft(owner,NativeDraft.EDITING,mail,1);
        check(NativeDraft.parse(owner,withText.json().toString()).mail.getString("body").equals(signed.body));
        SavedAccounts.Login login=new SavedAccounts.Login("example.invalid",993,"own@example.invalid","fixture");
        String literal="<script>plain text</script>";
        javax.mail.internet.MimeMessage message=MailSender.message(javax.mail.Session.getInstance(new Properties()),login,new JSONObject(mail.toString()).put("body",literal),new java.io.File("unused"));
        check(message.isMimeType("text/plain")&&message.getContent().equals(literal));
        System.out.println("PASS native writing tools: validated templates, caret/selection replacement and padding, UTF-16 boundaries, length limit with unchanged source, signature duplicate guard incl trailing newlines, draft round trip and literal text/plain MIME; no network.");
    }
}
