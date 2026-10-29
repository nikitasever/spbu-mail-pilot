package ru.local.spbumailpilot;
import org.json.*;import okhttp3.*;import java.util.*;import java.util.concurrent.*;
public final class DesktopCalendarTest {
 static void check(boolean v){if(!v)throw new AssertionError();}
 static String wrap(String ics){return "<d:multistatus xmlns:d='DAV:' xmlns:c='urn:ietf:params:xml:ns:caldav'><d:response><d:propstat><d:prop><c:calendar-data><![CDATA["+ics+"]]></c:calendar-data></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response></d:multistatus>";}
 static String response(String href,String props){return "<d:response><d:href>"+href+"</d:href><d:propstat><d:prop>"+props+"</d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>";}
 static JSONObject auth(){return new JSONObject().put("server","https://calendar.fixture.invalid/dav/").put("user","fixture").put("password","fixture-private-password").put("month","2026-10").put("url","https://calendar.fixture.invalid/c1/");}
 static final String ICS="BEGIN:VCALENDAR\nBEGIN:VEVENT\nUID:meeting\nDTSTART:20261029T090000Z\nDTEND:20261029T100000Z\nRRULE:FREQ=DAILY;COUNT=2\nSUMMARY:Совещание\nEND:VEVENT\nBEGIN:VEVENT\nUID:all-day\nDTSTART;VALUE=DATE:20261031\nDTEND;VALUE=DATE:20261102\nSUMMARY:Выезд\nEND:VEVENT\nEND:VCALENDAR";
 public static void main(String[] args)throws Exception{
  final int[] calls={0};OkHttpClient client=new OkHttpClient.Builder().followRedirects(false).addInterceptor(chain->{calls[0]++;check(chain.request().header("Authorization")!=null);String body=chain.request().method().equals("REPORT")?wrap(ICS):"<d:multistatus xmlns:d='DAV:' xmlns:c='urn:ietf:params:xml:ns:caldav'>"+(chain.request().url().encodedPath().equals("/dav/")?response("/dav/","<c:calendar-home-set><d:href>/home/</d:href></c:calendar-home-set>"):response("/c1/","<d:resourcetype><c:calendar/></d:resourcetype><d:displayname>Личный</d:displayname>")+response("/c2/","<d:resourcetype><c:calendar/></d:resourcetype><d:displayname>Учебный</d:displayname>"))+"</d:multistatus>";return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(207).message("Multi-Status").body(ResponseBody.create(MediaType.parse("application/xml"),body)).build();}).build();
  JSONObject found=DesktopCalendars.discover(auth(),client);check(found.getJSONArray("calendars").length()==2);check(!found.toString().contains("password"));
  JSONArray events=DesktopCalendars.month(auth(),client);check(events.length()==3);check(events.getJSONObject(0).getString("time").equals("12:00"));check(events.getJSONObject(2).getString("endDate").equals("2026-11-02"));check(DesktopCalendars.month(auth().put("month","2026-11"),client).length()==1);
  int previous=calls[0];try{DesktopCalendars.month(auth().put("url","https://other.fixture.invalid/c1"),client);throw new AssertionError();}catch(DesktopCalendars.Fault refused){check(refused.code.equals("CALENDAR_ORIGIN"));}check(calls[0]==previous);
  OkHttpClient redirect=new OkHttpClient.Builder().followRedirects(false).addInterceptor(chain->new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(302).message("Redirect").header("Location","https://other.fixture.invalid/private").body(ResponseBody.create(MediaType.parse("text/plain"),"")).build()).build();try{DesktopCalendars.discover(auth(),redirect);throw new AssertionError();}catch(CalDav.Failure refused){check(DesktopCalendars.code(refused).equals("CALENDAR_ORIGIN"));}
  try{CalDav.parse("<!DOCTYPE x [<!ENTITY leak SYSTEM 'file:///private'>]><x/>");throw new AssertionError();}catch(CalDav.Failure safe){}
  // A blocked calendar uses a different worker and its own cancellation resource.
  CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);BlockingQueue<JSONObject> replies=new LinkedBlockingQueue<>();
  DesktopCalendars.Transport slow=new DesktopCalendars.Transport(){public JSONObject discover(JSONObject a)throws Exception{entered.countDown();release.await(3,TimeUnit.SECONDS);return new JSONObject();}public JSONArray month(JSONObject a){return new JSONArray();}public void close(){release.countDown();}};
  try(DesktopEngine engine=new DesktopEngine((h,p,u,pw,register)->{DesktopEngineTest.Mailbox mailbox=new DesktopEngineTest.Mailbox();register.attach(mailbox);return mailbox;},replies::add,()->slow)){
   engine.accept(DesktopEngineTest.request("login","connect",DesktopEngineTest.login()));DesktopEngineTest.result(replies);
   engine.accept(DesktopEngineTest.request("calendar","calendarDiscover",auth()));check(entered.await(2,TimeUnit.SECONDS));
   engine.accept(DesktopEngineTest.request("inbox","folders",new JSONObject()));check(DesktopEngineTest.result(replies).getJSONArray("result").length()==3);
   engine.accept(DesktopEngineTest.request("stop","cancel",new JSONObject().put("requestId","calendar")));JSONObject a=DesktopEngineTest.result(replies),b=DesktopEngineTest.result(replies);check(a.optJSONObject("error")!=null||b.optJSONObject("error")!=null);
   engine.accept(DesktopEngineTest.request("still-mail","folders",new JSONObject()));check(DesktopEngineTest.result(replies).getJSONArray("result").length()==3);
  }
  System.out.println("PASS desktop calendar: real CalDAV discovery/recurrence/Moscow parsing via fixture HTTP interceptor, origin/XXE protection, independent mail worker and calendar socket cancellation.");
 }
}
