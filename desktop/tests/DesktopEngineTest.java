package ru.local.spbumailpilot;
import org.json.*;
import java.util.concurrent.*;

public class DesktopEngineTest {
    static void check(boolean value){if(!value)throw new AssertionError();}
    static JSONObject request(String id,String method,JSONObject args){return new JSONObject().put("id",id).put("method",method).put("args",args);}
    static JSONObject login(){return new JSONObject().put("host","fixture.invalid").put("port",993).put("user","fixture").put("password","not-a-real-secret");}
    static final class Mailbox implements DesktopEngine.Gateway {
        final CountDownLatch entered=new CountDownLatch(1),released=new CountDownLatch(1);boolean block;
        public JSONArray folders(){return new JSONArray().put("INBOX").put("Sent").put("Drafts");}
        public JSONObject page(String folder,int offset,String snapshot,String query)throws Exception{
            if(block){entered.countDown();released.await(3,TimeUnit.SECONDS);}
            return new JSONObject().put("folder",folder).put("messages",new JSONArray()).put("hasMore",false);
        }
        public JSONObject body(String folder,String uid,String validity){return new JSONObject().put("uid",uid).put("text","Текст письма");}
        public void close(){released.countDown();}
    }
    static JSONObject result(BlockingQueue<JSONObject> out)throws Exception{JSONObject value=out.poll(3,TimeUnit.SECONDS);check(value!=null);check(!value.toString().contains("not-a-real-secret"));return value;}
    public static void main(String[] args)throws Exception{
        String html=DesktopHtml.fromHtml("<html><head><title>hidden</title><style>bad-css</style></head><body><p>Здравствуйте &amp; привет</p><script>bad-script</script><p>Мир &#128512;<br>ещё</p></body></html>",0);
        check(html.contains("Здравствуйте & привет")&&html.contains("Мир 😀")&&html.contains("\n"));check(!html.contains("bad-")&&!html.contains("hidden"));
        LinkedBlockingQueue<JSONObject> out=new LinkedBlockingQueue<>();Mailbox mailbox=new Mailbox();
        try(DesktopEngine engine=new DesktopEngine((host,port,user,password,register)->{check(host.equals("fixture.invalid")&&port==993);register.attach(mailbox);return mailbox;},out::add)){
            engine.accept(request("a","connect",login()));check(result(out).getJSONObject("result").getBoolean("connected"));
            engine.accept(request("b","folders",new JSONObject()));check(result(out).getJSONArray("result").length()==3);
            engine.accept(request("c","body",new JSONObject().put("folder","INBOX").put("uid","42").put("validity","7")));check(result(out).getJSONObject("result").getString("text").equals("Текст письма"));
            mailbox.block=true;
            engine.accept(request("d","page",new JSONObject().put("folder","INBOX").put("offset",0).put("snapshot","").put("query","")));
            check(mailbox.entered.await(2,TimeUnit.SECONDS));
            engine.accept(request("e","cancel",new JSONObject().put("requestId","d")));
            JSONObject one=result(out),two=result(out);
            check((one.optJSONObject("error")!=null&&one.getJSONObject("error").getString("code").equals("CANCELLED"))||(two.optJSONObject("error")!=null&&two.getJSONObject("error").getString("code").equals("CANCELLED")));
            engine.accept(request("f","folders",new JSONObject()));check(result(out).getJSONArray("result").length()==3);
            engine.accept(request("logout","disconnect",new JSONObject()));result(out);
            engine.accept(request("none","folders",new JSONObject()));check(result(out).getJSONObject("error").getString("code").equals("NO_SESSION"));
            engine.accept(request("g","exec",new JSONObject().put("password","not-a-real-secret")));check(result(out).getJSONObject("error").getString("code").equals("BAD_REQUEST"));
        }
        // Cancellation can reach a connection attempt, before connect() returns.
        Mailbox connecting=new Mailbox();LinkedBlockingQueue<JSONObject> next=new LinkedBlockingQueue<>();
        try(DesktopEngine engine=new DesktopEngine((host,port,user,password,register)->{register.attach(connecting);connecting.entered.countDown();connecting.released.await(3,TimeUnit.SECONDS);return connecting;},next::add)){
            engine.accept(request("connect","connect",login()));check(connecting.entered.await(2,TimeUnit.SECONDS));
            engine.accept(request("stop","cancel",new JSONObject().put("requestId","connect")));
            JSONObject one=result(next),two=result(next);check(one.has("error")||two.has("error"));
        }
        System.out.println("PASS desktop engine: isolated protocol, real folder/UID DTOs, cancellation during connect/read, session invalidation, method allowlist, secret-free failures and HTML text adapter.");
    }
}
