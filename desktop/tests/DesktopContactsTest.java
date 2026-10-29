package ru.local.spbumailpilot;
import java.util.*;import java.util.concurrent.*;import java.io.*;import org.json.*;import okhttp3.*;
public final class DesktopContactsTest{
 static void check(boolean v){if(!v)throw new AssertionError();}
 static SavedAccounts.Login login(){return new SavedAccounts.Login("mail.spbu.ru",993,"fixture","not-a-real-secret");}
 static JSONObject query(String book,String cookie)throws Exception{return new JSONObject().put("book",book).put("query","").put("cookie",cookie).put("_owner",DesktopContacts.owner(login()));}
 public static void main(String[] ignored)throws Exception{
  check(DesktopContacts.owner(login()).equals("01bb68ff93b4bbbef383e6571d5e0c05ed07ee1856b53de698cde0e134103726"));
  List<String> commands=new ArrayList<>();OkHttpClient client=new OkHttpClient.Builder().followRedirects(false).addInterceptor(chain->{
   check(chain.request().url().host().equals("mail.spbu.ru"));okio.Buffer buffer=new okio.Buffer();chain.request().body().writeTo(buffer);String body=buffer.readUtf8();commands.add(body);String xml;
   if(chain.request().url().encodedPath().equals("/ximsslogin/"))xml="<XIMSS><session urlID='fixture'/><response id='login'/></XIMSS>";
   else if(body.contains("defaultBooks"))xml="<XIMSS><prefs/><response id='defaultBooks'/></XIMSS>";
   else if(body.contains("prefsRead"))xml="<XIMSS><prefs><ContactsBox>Contacts</ContactsBox></prefs><response id='books'/></XIMSS>";
   else if(body.contains("folderOpen"))xml="<XIMSS><folderReport folder='pilotContacts' mode='init' messages='51'/><response id='openContacts'/></XIMSS>";
   else if(body.contains("folderBrowse"))xml="<XIMSS><folderReport id='browseContacts' folder='pilotContacts'><Subject>Имя</Subject><E-To>person@example.invalid</E-To></folderReport><response id='browseContacts'/></XIMSS>";
   else if(body.contains("listDirectory"))xml="<XIMSS><directoryData id='directory' cookie='next'><field name='cn'>Имя</field><field name='mail'>person@example.invalid</field></directoryData><response id='directory'/></XIMSS>";
   else xml="<XIMSS><response id='closeContacts'/></XIMSS>";
   return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(ResponseBody.create(MediaType.parse("text/xml"),xml)).build();
  }).build();
  try(DesktopContacts source=new DesktopContacts(client)){
   JSONObject first=source.page(login(),query("personal",""));check(first.getString("cookie").equals("50"));check(source.page(login(),query("personal","50")).getString("cookie").isEmpty());
   try{source.page(login(),query("company","50"));throw new AssertionError();}catch(DesktopContacts.Fault error){check(error.code.equals("CONTACTS_CURSOR"));}
   source.page(login(),query("company",""));check(commands.stream().filter(c->c.startsWith("userName=")).count()==1);check(commands.stream().noneMatch(c->c.contains("messageRemove")||c.contains("contactAppend")||c.contains("messageMark")));
   int count=commands.size();try{source.page(new SavedAccounts.Login("other.invalid",993,"fixture","private"),query("personal",""));throw new AssertionError();}catch(DesktopContacts.Fault error){check(error.code.equals("CONTACTS_UNSUPPORTED"));}check(commands.size()==count);
  }
  for(String invalid:new String[]{"^:bad","line\nbreak"})try{DesktopContacts.validate("company",invalid,"");throw new AssertionError();}catch(DesktopContacts.Fault expected){}
  CountDownLatch entered=new CountDownLatch(1);OkHttpClient blocked=new OkHttpClient.Builder().addInterceptor(chain->{entered.countDown();long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(!chain.call().isCanceled()&&System.nanoTime()<end)try{Thread.sleep(5);}catch(InterruptedException error){throw new IOException(error);}throw new IOException("fixture private error");}).build();
  BlockingQueue<JSONObject> replies=new LinkedBlockingQueue<>();java.util.concurrent.atomic.AtomicReference<DesktopContacts> live=new java.util.concurrent.atomic.AtomicReference<>();
  try(DesktopEngine engine=new DesktopEngine((h,p,u,pw,r)->{DesktopEngineTest.Mailbox box=new DesktopEngineTest.Mailbox();r.attach(box);return box;},replies::add,DesktopCalendars::create,()->{DesktopContacts source=new DesktopContacts(blocked);live.set(source);return source;})){
   engine.accept(DesktopEngineTest.request("login","connect",DesktopEngineTest.login().put("host","mail.spbu.ru")));DesktopEngineTest.result(replies);
   engine.accept(DesktopEngineTest.request("wrong","contactsPage",query("personal","").put("_owner","another-account")));check(DesktopEngineTest.result(replies).getJSONObject("error").getString("code").equals("NO_SESSION"));
   engine.accept(DesktopEngineTest.request("slow","contactsPage",query("personal","")));check(entered.await(2,TimeUnit.SECONDS));
   engine.accept(DesktopEngineTest.request("mail","folders",new JSONObject()));check(DesktopEngineTest.result(replies).getJSONArray("result").length()==3);
   engine.accept(DesktopEngineTest.request("stop","cancel",new JSONObject().put("requestId","slow")));check(live.get().closed());JSONObject a=DesktopEngineTest.result(replies),b=DesktopEngineTest.result(replies);check(a.toString().contains("CANCELLED")||b.toString().contains("CANCELLED"));check(!a.toString().contains("private")&&!b.toString().contains("private"));
   engine.accept(DesktopEngineTest.request("mail-again","folders",new JSONObject()));check(DesktopEngineTest.result(replies).has("result"));
  }
  OkHttpClient denied=new OkHttpClient.Builder().addInterceptor(chain->new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(401).message("Denied").body(ResponseBody.create(MediaType.parse("text/plain"),"fixture private response")).build()).build();
  try(DesktopEngine engine=new DesktopEngine((h,p,u,pw,r)->{DesktopEngineTest.Mailbox box=new DesktopEngineTest.Mailbox();r.attach(box);return box;},replies::add,DesktopCalendars::create,()->new DesktopContacts(denied))){
   engine.accept(DesktopEngineTest.request("login2","connect",DesktopEngineTest.login().put("host","mail.spbu.ru")));DesktopEngineTest.result(replies);
   engine.accept(DesktopEngineTest.request("auth","contactsPage",query("personal","")));check(DesktopEngineTest.result(replies).getJSONObject("error").getString("code").equals("CONTACTS_AUTH"));
   engine.accept(DesktopEngineTest.request("after-auth","folders",new JSONObject()));check(DesktopEngineTest.result(replies).getJSONArray("result").length()==3);
  }
  System.out.println("PASS desktop contacts: shared university parser/session, personal paging, cursor route binding, fixed HTTPS origin, owner hash boundary, unsupported accounts refused, no writes, independent mail and socket cancellation (fixture HTTP). ");
 }
}
