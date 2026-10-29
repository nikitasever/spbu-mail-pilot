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
        // Attachments use a private connection/worker; cancellation must not close the inbox session.
        java.nio.file.Path directory=java.nio.file.Files.createTempDirectory("desktop-attachment-test-");
        CountDownLatch downloading=new CountDownLatch(1),release=new CountDownLatch(1);
        LinkedBlockingQueue<JSONObject> downloads=new LinkedBlockingQueue<>();
        DesktopEngine.Gateway transfer=new DesktopEngine.Gateway(){
            public JSONArray folders(){throw new AssertionError("Transfer connection used for inbox");}
            public JSONObject page(String f,int o,String s,String q){throw new AssertionError();}
            public JSONObject body(String f,String u,String v){throw new AssertionError();}
            public JSONObject attachment(String f,String u,String v,String path,java.io.File dir,java.util.function.BooleanSupplier cancelled,AttachmentTransfer.Progress progress)throws Exception{
                check(f.equals("INBOX")&&u.equals("42")&&v.equals("7")&&path.equals("1"));
                downloading.countDown();progress.update(25,100);release.await(3,TimeUnit.SECONDS);
                check(cancelled.getAsBoolean());throw new java.io.IOException("not-a-real-secret");
            }
            public void close(){release.countDown();}
        };
        java.util.concurrent.atomic.AtomicInteger connections=new java.util.concurrent.atomic.AtomicInteger();
        try(DesktopEngine engine=new DesktopEngine((h,p,u,pw,register)->{DesktopEngine.Gateway gateway=connections.getAndIncrement()==0?new Mailbox():transfer;register.attach(gateway);return gateway;},message->{if(!message.has("event"))downloads.add(message);})){
            engine.accept(request("login","connect",login()));result(downloads);
            JSONObject fileArgs=new JSONObject().put("folder","INBOX").put("uid","42").put("validity","7").put("path","1").put("directory",directory.toString());
            engine.accept(request("file","attachment",fileArgs));check(downloading.await(2,TimeUnit.SECONDS));
            engine.accept(request("inbox","folders",new JSONObject()));check(result(downloads).getJSONArray("result").length()==3);
            engine.accept(request("cancel-file","cancel",new JSONObject().put("requestId","file")));
            JSONObject a=result(downloads),b=result(downloads);check(a.has("error")||b.has("error"));
            engine.accept(request("inbox-after","folders",new JSONObject()));check(result(downloads).getJSONArray("result").length()==3);
            engine.accept(request("bad-file","attachment",new JSONObject(fileArgs.toString()).put("path","../../secret")));
            check(result(downloads).getJSONObject("error").getString("code").equals("BAD_REQUEST"));
        }finally{java.nio.file.Files.delete(directory);}
        System.out.println("PASS desktop engine: isolated protocol, real folder/UID DTOs, cancellation during connect/read, session invalidation, method allowlist, secret-free failures and HTML text adapter.");
    }
}
