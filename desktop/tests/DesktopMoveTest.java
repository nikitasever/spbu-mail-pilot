package ru.local.spbumailpilot;
import org.json.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** No provider credentials/network: protocol boundary plus shared MailMoveTest transport fixture. */
public class DesktopMoveTest {
    static void check(boolean v){if(!v)throw new AssertionError();}
    static JSONObject request(String id,String method,JSONObject args){return new JSONObject().put("id",id).put("method",method).put("args",args);}
    static JSONObject next(BlockingQueue<JSONObject> out)throws Exception{JSONObject value=out.poll(3,TimeUnit.SECONDS);check(value!=null&&!value.toString().contains("fixture-secret"));return value;}
    public static void main(String[] ignored)throws Exception{
        LinkedBlockingQueue<JSONObject> out=new LinkedBlockingQueue<>();AtomicInteger connects=new AtomicInteger(),writes=new AtomicInteger();
        AtomicReference<String> mode=new AtomicReference<>("blocked");CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        try(DesktopEngine engine=new DesktopEngine((h,p,u,pw,register)->{
            boolean inbox=connects.getAndIncrement()==0;
            DesktopEngine.Gateway gateway=new DesktopEngine.Gateway(){
                public JSONArray folders(){check(inbox);return new JSONArray().put("INBOX").put("Проекты/2026");}
                public JSONObject body(String f,String u,String v){check(inbox);return new JSONObject().put("text","read independently");}
                public JSONObject page(String f,int o,String s,String q){return new JSONObject().put("fresh",true);}
                public JSONObject move(String f,String u,String v,String destination,java.util.function.BooleanSupplier allowed)throws Exception{
                    check(!inbox&&f.equals("INBOX")&&u.equals("42")&&destination.equals("Проекты/2026"));
                    check(allowed.getAsBoolean());
                    if(!v.equals("7"))throw new java.io.IOException("Папка изменилась. Обновите список.");
                    if(mode.get().equals("unsupported"))throw new java.io.IOException("Сервер не поддерживает безопасное перемещение MOVE. Откройте веб-почту.");
                    if(mode.get().equals("missing"))throw new java.io.IOException("Папка назначения не найдена или письмо уже в ней.");
                    writes.incrementAndGet();
                    if(mode.get().equals("blocked")){entered.countDown();release.await(3,TimeUnit.SECONDS);}
                    if(!allowed.getAsBoolean())throw new java.io.IOException("cancelled");
                    if(mode.get().equals("lost"))throw new java.io.IOException("fixture-secret: lost acknowledgement");
                    return new JSONObject().put("moved",true).put("destination",mode.get().equals("wrong")?"Wrong":destination);
                }
                public JSONObject moveKind(String f,String u,String v,String kind,String destination,java.util.function.BooleanSupplier allowed)throws Exception{
                    if(kind.equals("folder"))return move(f,u,v,destination,allowed);
                    check(!inbox&&destination==null&&allowed.getAsBoolean());writes.incrementAndGet();
                    return new JSONObject().put("moved",true).put("destination","University/"+kind);
                }
                public void close(){release.countDown();}
            };register.attach(gateway);return gateway;
        },out::add)){
            JSONObject login=new JSONObject().put("host","fixture.invalid").put("port",993).put("user","fixture").put("password","fixture-secret");
            engine.accept(request("login","connect",login));check(next(out).getJSONObject("result").getBoolean("connected"));
            String owner=DesktopContacts.owner(new SavedAccounts.Login("fixture.invalid",993,"fixture","fixture-secret"));
            JSONObject args=new JSONObject().put("folder","INBOX").put("uid","42").put("validity","7").put("destination","Проекты/2026").put("_owner",owner);
            engine.accept(request("owner","moveMail",new JSONObject(args.toString()).put("_owner","wrong")));check(next(out).getJSONObject("error").getString("code").equals("NO_SESSION"));
            for(String bad:new String[]{"INBOX","","Bad\nFolder"}){engine.accept(request("bad","moveMail",new JSONObject(args.toString()).put("destination",bad)));check(next(out).getJSONObject("error").getString("code").equals("BAD_REQUEST"));}
            check(connects.get()==1&&writes.get()==0);
            engine.accept(request("move","moveMail",args));check(entered.await(2,TimeUnit.SECONDS));
            engine.accept(request("read","body",new JSONObject().put("folder","INBOX").put("uid","43").put("validity","7")));check(next(out).getJSONObject("result").getString("text").equals("read independently"));
            release.countDown();JSONObject moved=next(out).getJSONObject("result");check(moved.getBoolean("moved")&&moved.getString("uid").equals("42")&&moved.getString("validity").equals("7")&&moved.getString("destination").equals("Проекты/2026"));
            for(String[] example:new String[][]{{"unsupported","MOVE_UNSUPPORTED"},{"missing","MOVE_DESTINATION"},{"wrong","MOVE_UNKNOWN"},{"lost","UNAVAILABLE"}}){mode.set(example[0]);engine.accept(request(example[0],"moveMail",args));check(next(out).getJSONObject("error").getString("code").equals(example[1]));}
            check(writes.get()==3); // one success, wrong acknowledgement, lost acknowledgement; no automatic retry
            engine.accept(request("stale","moveMail",new JSONObject(args.toString()).put("validity","8")));check(next(out).getJSONObject("error").getString("code").equals("STALE_MAIL"));check(writes.get()==3);
            int before=connects.get();engine.accept(request("refresh","page",new JSONObject().put("folder","INBOX").put("offset",0).put("snapshot","").put("query","")));check(next(out).getJSONObject("result").getBoolean("fresh"));check(connects.get()==before+1);
            JSONObject quick=new JSONObject(args.toString());quick.remove("destination");
            int beforeInvalid=connects.get();
            engine.accept(request("delete-refused","moveMail",new JSONObject(quick.toString()).put("kind","delete")));check(next(out).getJSONObject("error").getString("code").equals("BAD_REQUEST"));
            engine.accept(request("ambiguous-target","moveMail",new JSONObject(args.toString()).put("kind","trash")));check(next(out).getJSONObject("error").getString("code").equals("BAD_REQUEST"));check(connects.get()==beforeInvalid);
            for(String kind:new String[]{"archive","trash","spam"}){
                engine.accept(request("quick-"+kind,"moveMail",new JSONObject(quick.toString()).put("kind",kind)));
                JSONObject changed=next(out).getJSONObject("result");check(changed.getString("kind").equals(kind)&&changed.getString("destination").equals("University/"+kind)&&changed.getBoolean("moved"));
            }
            check(writes.get()==6);
            engine.accept(request("logout","disconnect",new JSONObject()));next(out);
            engine.accept(request("old","moveMail",args));check(next(out).getJSONObject("error").getString("code").equals("NO_SESSION"));
        }
        System.out.println("PASS desktop MOVE: owner/UIDVALIDITY/target checks, isolated connection/worker, exact acknowledgement, capability/target errors, no replay after lost ACK, fresh inbox and logout isolation (fixtures).");
    }
}
