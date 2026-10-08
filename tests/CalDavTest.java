package ru.local.spbumailpilot;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.net.ssl.*;
import java.util.*;
import java.util.concurrent.*;
import com.sun.net.httpserver.*;
import okhttp3.*;
import org.json.*;

public final class CalDavTest {
    static void check(boolean value){if(!value)throw new AssertionError();}
    static String wrap(String ics){return "<d:multistatus xmlns:d='DAV:' xmlns:c='urn:ietf:params:xml:ns:caldav'><d:response><d:propstat><d:prop><c:calendar-data><![CDATA["+ics+"]]></c:calendar-data></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response></d:multistatus>";}
    static String discoveryResponse(String href,String props,String status){return "<d:response><d:href>"+href+"</d:href><d:propstat><d:prop>"+props+"</d:prop><d:status>HTTP/1.1 "+status+"</d:status></d:propstat></d:response>";}
    static final String ICS="BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nUID:one\r\nDTSTART:20261008T090000Z\r\nDTEND:20261008T100000Z\r\nSUMMARY:Work\\, meet\r\n ing\r\nDESCRIPTION:First\\nSecond\r\nLOCATION:Room\\; 1\r\nBEGIN:VALARM\r\nSUMMARY:Alarm must not replace event title\r\nEND:VALARM\r\nEND:VEVENT\r\nBEGIN:VEVENT\r\nUID:one\r\nDTSTART:20261015T090000Z\r\nSUMMARY:Second occurrence\r\nEND:VEVENT\r\nBEGIN:VEVENT\r\nUID:all-day\r\nDTSTART;VALUE=DATE:20261009\r\nSUMMARY:All day\r\nEND:VEVENT\r\nBEGIN:VEVENT\r\nUID:cancelled\r\nDTSTART:20261009T090000Z\r\nSTATUS:CANCELLED\r\nEND:VEVENT\r\nEND:VCALENDAR";
    interface Attempt {void run()throws Exception;}
    static void rejected(Attempt attempt)throws Exception{try{attempt.run();throw new AssertionError("Accepted unsafe/unsupported input");}catch(CalDav.Failure|IllegalArgumentException expected){}}
    static OkHttpClient client(X509TrustManager trust,SSLContext ssl){return new OkHttpClient.Builder().sslSocketFactory(ssl.getSocketFactory(),trust).followRedirects(false).followSslRedirects(false).callTimeout(10,TimeUnit.SECONDS).build();}
    public static void main(String[] args)throws Exception{
        JSONArray parsed=CalDav.parse(wrap(ICS));check(parsed.length()==3);check(parsed.getJSONObject(0).getString("time").equals("12:00"));check(parsed.getJSONObject(0).getString("title").equals("Work, meeting"));check(parsed.getJSONObject(0).getString("description").contains("\n"));check(parsed.getJSONObject(2).getBoolean("allDay"));check(parsed.getJSONObject(2).getString("time").equals(""));
        check(CalDav.query("2026-10").contains("20260930T210000Z"));check(CalDav.query("2026-10").contains("20261031T210000Z"));
        String timezone="BEGIN:VCALENDAR\nBEGIN:VEVENT\nUID:zone\nDTSTART;TZID=Europe/Berlin:20261008T100000\nEND:VEVENT\nEND:VCALENDAR";
        check(CalDav.parse(wrap(timezone)).getJSONObject(0).getString("time").equals("11:00"));
        rejected(()->CalDav.address("http://calendar.mail.ru/calendar"));rejected(()->CalDav.address("https://password@calendar.mail.ru/calendar"));
        check(!CalDav.sameOrigin(new URI("https://calendar.mail.ru/a"),new URI("https://evil.invalid/b")));check(!CalDav.sameOrigin(new URI("https://calendar.mail.ru/a"),new URI("http://calendar.mail.ru/b")));
        rejected(()->CalDav.parse("<!DOCTYPE x [<!ENTITY secret SYSTEM 'file:///nonexistent'>]>"+wrap(ICS)));
        rejected(()->CalDav.parse(wrap(ICS.replace("SUMMARY:Second occurrence","RRULE:FREQ=WEEKLY\nSUMMARY:Second occurrence"))));
        rejected(()->CalDav.parse(wrap(ICS).replace("200 OK","403 Forbidden")));
        Path temp=Files.createTempDirectory("calendar-tls-");File keystore=temp.resolve("test.p12").toFile();
        String keytool=Paths.get(System.getProperty("java.home"),"bin",System.getProperty("os.name").toLowerCase().contains("win")?"keytool.exe":"keytool").toString();
        Process keys=new ProcessBuilder(keytool,"-genkeypair","-keystore",keystore.toString(),"-storetype","PKCS12","-storepass","calendar-test","-keypass","calendar-test","-alias","test","-dname","CN=localhost","-ext","SAN=dns:localhost","-keyalg","RSA","-keysize","2048","-validity","1","-noprompt").redirectErrorStream(true).start();try(InputStream in=keys.getInputStream()){byte[] b=new byte[8192];while(in.read(b)!=-1){}}check(keys.waitFor()==0);
        KeyStore store=KeyStore.getInstance("PKCS12");try(InputStream in=new FileInputStream(keystore)){store.load(in,"calendar-test".toCharArray());}
        KeyManagerFactory km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());km.init(store,"calendar-test".toCharArray());TrustManagerFactory tm=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());tm.init(store);X509TrustManager trust=(X509TrustManager)tm.getTrustManagers()[0];SSLContext ssl=SSLContext.getInstance("TLS");ssl.init(km.getKeyManagers(),tm.getTrustManagers(),null);
        HttpsServer server=HttpsServer.create(new InetSocketAddress("127.0.0.1",0),0);server.setHttpsConfigurator(new HttpsConfigurator(ssl));ExecutorService executor=Executors.newCachedThreadPool();server.setExecutor(executor);List<String> seen=Collections.synchronizedList(new ArrayList<>());
        server.createContext("/",exchange->{try{
            String path=exchange.getRequestURI().getPath();seen.add(path+":"+exchange.getRequestMethod());check(exchange.getRequestHeaders().getFirst("Authorization").equals(Credentials.basic("calendar@example.invalid","fake-secret",StandardCharsets.UTF_8)));if(exchange.getRequestMethod().equals("PROPFIND")){
                check(exchange.getRequestHeaders().getFirst("Depth").equals(path.equals("/home/")?"1":"0"));
                if(path.equals("/.well-known/caldav")){exchange.sendResponseHeaders(404,-1);return;}
                if(path.equals("/discovery-redirect")){exchange.getResponseHeaders().add("Location","/principal/");exchange.sendResponseHeaders(307,-1);return;}
                if(path.equals("/foreign-redirect")){exchange.getResponseHeaders().add("Location","https://evil.invalid/secret");exchange.sendResponseHeaders(307,-1);return;}
                if(path.equals("/denied")){exchange.sendResponseHeaders(401,-1);return;}
                String props;
                if(path.equals("/principal/"))props="<c:calendar-home-set><d:href>/home/</d:href></c:calendar-home-set>";
                else if(path.equals("/foreign-principal"))props="<d:current-user-principal><d:href>https://evil.invalid/principal</d:href></d:current-user-principal>";
                else props="<d:current-user-principal><d:href>/principal/</d:href></d:current-user-principal>";
                String xml=discoveryResponse("/",props,"200 OK");
                if(path.equals("/home/"))xml=discoveryResponse("primary/","<d:resourcetype><d:collection/><c:calendar/></d:resourcetype><d:displayname>Personal &amp; work</d:displayname><c:supported-calendar-component-set><c:comp name='VEVENT'/></c:supported-calendar-component-set>","200 OK")+discoveryResponse("/home/shared/","<d:resourcetype><c:calendar/></d:resourcetype><d:displayname>Shared</d:displayname>","200 OK")+discoveryResponse("/home/tasks/","<d:resourcetype><c:calendar/></d:resourcetype><c:supported-calendar-component-set><c:comp name='VTODO'/></c:supported-calendar-component-set>","200 OK")+discoveryResponse("/home/denied/","<d:resourcetype><c:calendar/></d:resourcetype>","403 Forbidden");
                xml="<d:multistatus xmlns:d='DAV:' xmlns:c='urn:ietf:params:xml:ns:caldav'>"+xml+"</d:multistatus>";
                if(path.equals("/xxe"))xml="<!DOCTYPE d:multistatus [<!ENTITY leak SYSTEM 'file:///nonexistent'>]>"+xml;
                byte[] bytes=xml.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(207,bytes.length);exchange.getResponseBody().write(bytes);return;
            }
            check(exchange.getRequestHeaders().getFirst("Depth").equals("1"));
            byte[] body;try(InputStream in=exchange.getRequestBody();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);body=out.toByteArray();}check(new String(body,StandardCharsets.UTF_8).contains("calendar-query"));
            if(path.equals("/same")){exchange.getResponseHeaders().add("Location","/calendar/");exchange.sendResponseHeaders(307,-1);}else if(path.equals("/other")){exchange.getResponseHeaders().add("Location","https://evil.invalid/steal");exchange.sendResponseHeaders(307,-1);}else if(path.equals("/denied")){exchange.sendResponseHeaders(401,-1);}else{byte[] xml=wrap(path.equals("/recurring")?"BEGIN:VCALENDAR\nBEGIN:VEVENT\nUID:raw-series\nDTSTART:20261001T090000Z\nRRULE:FREQ=DAILY;COUNT=3\nEND:VEVENT\nEND:VCALENDAR":ICS).getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().add("Content-Type","application/xml; charset=utf-8");exchange.sendResponseHeaders(207,xml.length);exchange.getResponseBody().write(xml);}
        }catch(Throwable failure){failure.printStackTrace();}finally{exchange.close();}});server.start();
        String base="https://localhost:"+server.getAddress().getPort();
        try{
            check(CalDav.sync(base+"/calendar/","calendar@example.invalid","fake-secret","2026-10",client(trust,ssl)).length()==3);
            check(CalDav.sync(base+"/same","calendar@example.invalid","fake-secret","2026-10",client(trust,ssl)).length()==3);
            rejected(()->CalDav.sync(base+"/other","calendar@example.invalid","fake-secret","2026-10",client(trust,ssl)));
            rejected(()->CalDav.sync(base+"/denied","calendar@example.invalid","fake-secret","2026-10",client(trust,ssl)));
            try{CalDav.sync(base+"/calendar/","calendar@example.invalid","fake-secret","2026-10");throw new AssertionError("Untrusted certificate accepted");}catch(SSLException expected){}
            check(seen.size()==5);for(String method:seen)check(method.endsWith(":REPORT"));
            check(CalDav.sync(base+"/recurring","calendar@example.invalid","fake-secret","2026-10",client(trust,ssl)).length()==3);
            List<CalendarDiscovery.Calendar> found=CalendarDiscovery.discover(base,"calendar@example.invalid","fake-secret",client(trust,ssl));
            check(found.size()==2&&found.get(0).url.equals(base+"/home/primary/")&&found.get(0).title.equals("Personal & work")&&found.get(1).title.equals("Shared"));
            check(CalendarDiscovery.discover(base+"/discovery-redirect","calendar@example.invalid","fake-secret",client(trust,ssl)).size()==2);
            rejected(()->CalendarDiscovery.discover(base+"/foreign-principal","calendar@example.invalid","fake-secret",client(trust,ssl)));
            rejected(()->CalendarDiscovery.discover(base+"/foreign-redirect","calendar@example.invalid","fake-secret",client(trust,ssl)));
            rejected(()->CalendarDiscovery.discover(base+"/denied","calendar@example.invalid","fake-secret",client(trust,ssl)));
            rejected(()->CalendarDiscovery.discover(base+"/xxe","calendar@example.invalid","fake-secret",client(trust,ssl)));
            try{CalendarDiscovery.discover(base,"calendar@example.invalid","fake-secret");throw new AssertionError("Untrusted discovery certificate accepted");}catch(SSLException expected){}
            check(seen.stream().filter(x->x.endsWith(":PROPFIND")).count()==11);
        }finally{server.stop(0);executor.shutdownNow();Files.deleteIfExists(keystore.toPath());Files.deleteIfExists(temp);}
        System.out.println("PASS: real TLS CalDAV REPORT, Basic auth, same-origin redirect, foreign redirect blocked, default certificate rejection, recurrence expansion, timezones, all-day events, unfolding, cancelled events and XXE prevention. Discovery: principal/home traversal, well-known fallback, relative URLs, shared calendars, component and permission filtering, cross-origin redirects/links refused, XXE and certificate rejection. No real calendar accessed.");
    }
}
