package ru.local.spbumailpilot;

import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Private parent/child NDJSON only; no socket listener, persistent credentials or mail writes. */
public final class DesktopEngine implements AutoCloseable {
    interface Gateway extends AutoCloseable {
        JSONArray folders()throws Exception;
        JSONObject page(String folder,int offset,String snapshot,String query)throws Exception;
        JSONObject body(String folder,String uid,String validity)throws Exception;
        default JSONObject attachment(String folder,String uid,String validity,String path,File directory,java.util.function.BooleanSupplier cancelled,AttachmentTransfer.Progress progress)throws Exception{throw new IOException("Unsupported attachment");}
    }
    interface Register {void attach(Gateway gateway)throws Exception;}
    interface Factory {Gateway connect(String host,int port,String user,String password,Register register)throws Exception;}
    interface Sink {void write(JSONObject message);}
    private static final class Failure extends Exception {final String code;Failure(String code){this.code=code;}}
    private static final class Operation {
        final AtomicBoolean stopped=new AtomicBoolean();volatile Gateway active;boolean transfer;
        void check()throws Failure{if(stopped.get())throw new Failure("CANCELLED");}
    }
    private final Factory factory;
    private final Sink sink;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ExecutorService transfers=Executors.newSingleThreadExecutor();
    private final ExecutorService closer=Executors.newCachedThreadPool();
    private final ConcurrentHashMap<String,Operation> pending=new ConcurrentHashMap<>();
    private volatile boolean closed;
    private volatile SavedAccounts.Login credentials; // session memory only; never serialized
    private volatile Gateway session; // worker only; a cancelled connection is never reused
    DesktopEngine(Factory factory,Sink sink){this.factory=factory;this.sink=sink;}
    private static String string(JSONObject args,String key,int max,boolean empty)throws Failure {
        Object value=args.opt(key);
        if(!(value instanceof String)||((String)value).length()>max||(!empty&&((String)value).isEmpty()))throw new Failure("BAD_REQUEST");
        return (String)value;
    }
    private static String folder(JSONObject args)throws Failure{
        String name=string(args,"folder",1024,false);
        if(name.matches("(?s).*[\\p{Cntrl}].*"))throw new Failure("BAD_REQUEST");return name;
    }
    private static String uid(JSONObject args,String key)throws Failure{
        String value=string(args,key,20,false);if(!value.matches("[1-9][0-9]{0,19}"))throw new Failure("BAD_REQUEST");return value;
    }
    private static int integer(JSONObject args,String key,int low,int high)throws Failure{
        Object raw=args.opt(key);if(!(raw instanceof Number))throw new Failure("BAD_REQUEST");
        double value=((Number)raw).doubleValue();if(value!=Math.rint(value)||value<low||value>high)throw new Failure("BAD_REQUEST");return (int)value;
    }
    private void response(String id,Object result,String code){
        JSONObject message=new JSONObject().put("id",id==null?JSONObject.NULL:id);
        if(code==null)message.put("result",result);else message.put("error",new JSONObject().put("code",code));
        if(message.toString().length()>2*1024*1024)message=new JSONObject().put("id",id).put("error",new JSONObject().put("code","RESPONSE_TOO_LARGE"));
        sink.write(message);
    }
    private void closeLater(Gateway gateway){if(gateway!=null){Runnable cleanup=()->{try{gateway.close();}catch(Exception ignored){}};try{closer.execute(cleanup);}catch(RejectedExecutionException stopped){Thread late=new Thread(cleanup,"desktop-late-close");late.setDaemon(true);late.start();}}}
    private void stop(Operation operation){operation.stopped.set(true);closeLater(operation.active);}
    void accept(JSONObject request){
        String id=null;
        try {
            id=string(request,"id",64,false);if(!id.matches("[A-Za-z0-9._-]+"))throw new Failure("BAD_REQUEST");
            String method=string(request,"method",32,false);
            Object raw=request.opt("args");if(raw!=null&&!(raw instanceof JSONObject))throw new Failure("BAD_REQUEST");
            JSONObject args=raw==null?new JSONObject():(JSONObject)raw;
            if(closed)throw new Failure("CLOSED");
            if(method.equals("capabilities")){response(id,new JSONObject().put("version","desktop-0.1.0-dev").put("readOnly",true).put("persistentCredentials",false).put("attachments",true),null);return;}
            if(method.equals("cancel")){
                String target=string(args,"requestId",64,false);Operation old=pending.get(target);if(old!=null)stop(old);
                response(id,new JSONObject().put("cancelled",old!=null),null);return;
            }
            if(!java.util.Arrays.asList("connect","disconnect","folders","page","body","attachment").contains(method))throw new Failure("BAD_REQUEST");
            if(method.equals("connect")||method.equals("disconnect"))for(Operation task:pending.values())if(task.transfer)stop(task);
            Operation operation=new Operation();operation.transfer=method.equals("attachment");
            if(pending.size()>=16)throw new Failure("BUSY");
            if(pending.putIfAbsent(id,operation)!=null){response(null,null,"DUPLICATE_REQUEST");return;}
            final String key=id;
            if(operation.transfer)sink.write(new JSONObject().put("id",key).put("event","attachmentProgress").put("bytes",0).put("total",-1));
            (operation.transfer?transfers:worker).execute(()->{
                try{
                    operation.check();Object result;
                    switch(method){
                        case "connect":{
                            String host=string(args,"host",253,false),user=string(args,"user",320,false),password=string(args,"password",4096,false);
                            int port=integer(args,"port",1,65535);
                            if(host.matches("(?s).*[\\p{Cntrl}\\s/:].*")||user.matches("(?s).*[\\p{Cntrl}].*")||password.indexOf('\0')>=0)throw new Failure("BAD_REQUEST");
                            closeLater(session);session=null;credentials=null;
                            Gateway next=factory.connect(host,port,user,password,gateway->{operation.active=gateway;if(operation.stopped.get())closeLater(gateway);operation.check();});
                            operation.active=next;operation.check();session=next;credentials=new SavedAccounts.Login(host,port,user,password);
                            result=new JSONObject().put("account",user).put("connected",true);break;
                        }
                        case "attachment":{
                            SavedAccounts.Login auth=credentials;if(auth==null)throw new Failure("NO_SESSION");
                            String name=folder(args),messageUid=uid(args,"uid"),validity=uid(args,"validity"),path=string(args,"path",160,false);
                            if(!path.matches("root|(?:[0-9]{1,5}|m)(?:\\.(?:[0-9]{1,5}|m)){0,12}"))throw new Failure("BAD_REQUEST");
                            // Directory is injected by the native shell, never selected by WebView code.
                            File directory=new File(string(args,"directory",32768,false));
                            if(!directory.isDirectory())throw new Failure("BAD_REQUEST");
                            Gateway transfer=factory.connect(auth.host,auth.port,auth.user,auth.password,gateway->{operation.active=gateway;if(operation.stopped.get())closeLater(gateway);operation.check();});
                            operation.active=transfer;
                            final long[] last={0};
                            try{
                                operation.check();
                                result=transfer.attachment(name,messageUid,validity,path,directory,operation.stopped::get,(bytes,total)->{
                                    long now=System.nanoTime();if(last[0]==0||now-last[0]>=150000000L){last[0]=now;sink.write(new JSONObject().put("id",key).put("event","attachmentProgress").put("bytes",bytes).put("total",total));}
                                });
                                operation.check();
                            }finally{closeLater(transfer);}
                            break;
                        }
                        case "disconnect":closeLater(session);session=null;credentials=null;result=new JSONObject().put("connected",false);break;
                        default:{
                            Gateway active=session;
                            if(active==null){
                                SavedAccounts.Login auth=credentials;if(auth==null)throw new Failure("NO_SESSION");
                                active=factory.connect(auth.host,auth.port,auth.user,auth.password,gateway->{operation.active=gateway;if(operation.stopped.get())closeLater(gateway);operation.check();});
                                operation.check();session=active;
                            }
                            operation.active=active;operation.check();
                            if(method.equals("folders"))result=active.folders();
                            else if(method.equals("page"))result=active.page(folder(args),integer(args,"offset",0,1000000),string(args,"snapshot",4096,true),string(args,"query",200,true));
                            else result=active.body(folder(args),uid(args,"uid"),uid(args,"validity"));
                        }
                    }
                    operation.check();response(key,result,null);
                }catch(Exception failure){
                    if(operation.stopped.get()){if(session==operation.active)session=null;closeLater(operation.active);response(key,null,"CANCELLED");}
                    else {String reason=code(failure);if(reason.equals("AUTH"))credentials=null;response(key,null,reason);}
                }finally{pending.remove(key,operation);}
            });
        }catch(Exception invalid){response(id,null,code(invalid));}
    }
    private static String code(Exception failure){
        if(failure instanceof Failure)return ((Failure)failure).code;
        java.util.Set<Throwable> seen=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable,Boolean>());
        for(Throwable current=failure;current!=null&&seen.size()<20&&seen.add(current);){
            if(current instanceof javax.mail.AuthenticationFailedException)return "AUTH";
            if(current instanceof java.net.SocketTimeoutException||current instanceof java.net.ConnectException||current instanceof java.net.UnknownHostException)return "NETWORK";
            String message=current.getMessage();
            if("Attachment too large".equals(message)||"Attachment transfer too large".equals(message))return "TOO_LARGE";
            if("UID validity changed".equals(message)||"Message unavailable".equals(message))return "STALE_MAIL";
            Throwable cause=current.getCause();if(cause==null&&current instanceof javax.mail.MessagingException)cause=((javax.mail.MessagingException)current).getNextException();current=cause;
        }
        return "UNAVAILABLE";
    }
    @Override public void close(){closed=true;for(Operation operation:pending.values())stop(operation);worker.shutdownNow();transfers.shutdownNow();closeLater(session);session=null;credentials=null;closer.shutdown();}
    private static String line(Reader input)throws IOException{
        StringBuilder result=new StringBuilder();int c;
        while((c=input.read())!=-1){if(c=='\n')return result.toString();if(result.length()>=65536)throw new IOException("Protocol line limit");if(c!='\r')result.append((char)c);}
        return result.length()==0?null:result.toString();
    }
    public static void main(String[] args)throws Exception {
        PrintWriter output=new PrintWriter(new OutputStreamWriter(System.out,StandardCharsets.UTF_8),true);
        DesktopEngine engine=new DesktopEngine((host,port,user,password,register)->{
            MailReader reader=new MailReader();
            Gateway gateway=new Gateway(){
                public JSONArray folders()throws Exception{return reader.folders();}
                public JSONObject page(String folder,int offset,String snapshot,String query)throws Exception{return reader.page(folder,offset,snapshot,query);}
                public JSONObject body(String folder,String uid,String validity)throws Exception{return reader.body(folder,uid,validity);}
                public JSONObject attachment(String folder,String uid,String validity,String path,File directory,java.util.function.BooleanSupplier cancelled,AttachmentTransfer.Progress progress)throws Exception{return reader.attachmentMeasured(folder,uid,validity,path,directory,cancelled,progress);}
                public void close(){reader.close();}
            };
            register.attach(gateway);
            try{reader.connect(host,port,user,password);}catch(Exception failure){reader.close();throw failure;}
            return gateway;
        },message->{synchronized(output){output.println(message.toString());}});
        try(Reader input=new BufferedReader(new InputStreamReader(System.in,StandardCharsets.UTF_8))){
            String value;while((value=line(input))!=null){try{engine.accept(new JSONObject(value));}catch(Exception invalid){engine.response(null,null,"BAD_REQUEST");}}
        }catch(IOException invalid){System.err.println("Desktop IPC input closed.");}
        finally{engine.close();}
        // No mail writes exist in this milestone. EOF must not leave a JVM orphan behind the shell.
        System.exit(0);
    }
}
