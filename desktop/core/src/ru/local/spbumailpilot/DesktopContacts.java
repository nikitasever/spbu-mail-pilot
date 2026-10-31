package ru.local.spbumailpilot;

import java.io.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.*;
import okhttp3.*;
import org.json.*;

/** Read-only university books; keeps the paging session, never reuses mail's socket. */
final class DesktopContacts implements AutoCloseable {
    static final class Fault extends Exception {final String code;Fault(String code){this.code=code;}}
    interface Factory {DesktopContacts create();}
    private final OkHttpClient client;
    DesktopContacts(){this(new OkHttpClient.Builder().connectTimeout(8,TimeUnit.SECONDS).readTimeout(8,TimeUnit.SECONDS).callTimeout(20,TimeUnit.SECONDS).followRedirects(false).build());}
    DesktopContacts(OkHttpClient client){this.client=client;this.directory=new UniversityDirectory(this::post);}
    private final AtomicReference<Call> active=new AtomicReference<>();
    private final AtomicBoolean closed=new AtomicBoolean();
    private String route="",next="";
    private final UniversityDirectory directory;
    static String owner(SavedAccounts.Login login)throws Exception{
        String key=new JSONArray(java.util.Arrays.asList(login.host.toLowerCase(java.util.Locale.ROOT),login.port,login.user.toLowerCase(java.util.Locale.ROOT))).toString();
        byte[] bytes=java.security.MessageDigest.getInstance("SHA-256").digest(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));StringBuilder hex=new StringBuilder();for(byte b:bytes)hex.append(String.format("%02x",b&255));return hex.toString();
    }
    boolean closed(){return closed.get();}
    JSONObject page(SavedAccounts.Login login,JSONObject args)throws Exception {
        if(!UniversityDirectory.supported(login))throw new Fault("CONTACTS_UNSUPPORTED");
        String book=args.getString("book"),query=args.getString("query"),cookie=args.getString("cookie");
        validate(book,query,cookie);
        String key=book+"\n"+query;
        if(!cookie.isEmpty()&&(!key.equals(route)||!cookie.equals(next)))throw new Fault("CONTACTS_CURSOR");
        try{JSONObject page=directory.page(login,book,query,cookie);route=key;next=page.getString("cookie");return page;}
        catch(IllegalArgumentException error){throw new Fault("BAD_REQUEST");}
        catch(Exception error){
            next="";String text=error.getMessage();
            if(text!=null&&(text.contains("отклонил вход")||text.contains("дополнительный вход")))throw new Fault("CONTACTS_AUTH");
            if(error instanceof UniversityDirectory.HttpFailure&&(((UniversityDirectory.HttpFailure)error).status==401||((UniversityDirectory.HttpFailure)error).status==403))throw new Fault("CONTACTS_AUTH");
            throw new Fault("CONTACTS_NETWORK");
        }
    }
    static void validate(String book,String query,String cookie)throws Fault {
        if(!java.util.Arrays.asList("personal","team","company").contains(book)||query.length()>120||query.matches("(?s).*\\p{Cntrl}.*")||cookie.length()>4096||cookie.matches("(?s).*\\p{Cntrl}.*")||query.startsWith("^:")&&!query.matches("\\^:[А-ЯЁA-Z#]"))throw new Fault("BAD_REQUEST");
    }
    private String post(String path,String text)throws Exception {
        if(closed())throw new IOException("Directory cancelled");
        if(!path.equals("/ximsslogin/")&&!path.matches("/Session/[A-Za-z0-9_-]{1,160}/sync"))throw new IOException("Directory path refused");
        MediaType type=MediaType.parse(path.equals("/ximsslogin/")?"application/x-www-form-urlencoded; charset=utf-8":"text/xml; charset=utf-8");
        Call call=client.newCall(new Request.Builder().url("https://mail.spbu.ru"+path).post(RequestBody.create(type,text)).build());active.set(call);
        try{
            if(closed()){call.cancel();throw new IOException("Directory cancelled");}
            try(Response reply=call.execute()){
                if(reply.code()!=200)throw new UniversityDirectory.HttpFailure(reply.code());if(reply.body()==null)throw new IOException("Empty directory reply");
                try(InputStream in=reply.body().byteStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                    byte[] buffer=new byte[4096];int count;while((count=in.read(buffer))!=-1){if(out.size()+count>512*1024)throw new IOException("Directory reply limit");out.write(buffer,0,count);}return out.toString("UTF-8");
                }
            }
        }finally{active.compareAndSet(call,null);}
    }
    void cancel(){closed.set(true);Call call=active.get();if(call!=null)call.cancel();}
    public void close(){cancel();client.dispatcher().cancelAll();client.connectionPool().evictAll();client.dispatcher().executorService().shutdown();}
}
