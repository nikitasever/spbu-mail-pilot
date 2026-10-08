package ru.local.spbumailpilot;

import java.io.*;
import java.net.URI;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.json.*;
import okhttp3.*;

/** Read-only CalDAV collection subscription. Credentials stay in native code. */
final class CalDav {
    static final class Failure extends IOException {Failure(String message){super(message);}}
    static final ZoneId DISPLAY_ZONE=ZoneId.of("Europe/Moscow");
    static final int LIMIT=8*1024*1024;
    static URI address(String value)throws Exception{
        URI uri=new URI(value.trim());
        if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getFragment()!=null||value.length()>8192)throw new IllegalArgumentException("Нужна полная HTTPS-ссылка CalDAV на календарь.");
        return uri;
    }
    static String query(String month){
        LocalDate date=LocalDate.parse(month+"-01");String start=DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC).format(date.atStartOfDay(DISPLAY_ZONE).toInstant()),end=DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC).format(date.plusMonths(1).atStartOfDay(DISPLAY_ZONE).toInstant());
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><c:calendar-query xmlns:d=\"DAV:\" xmlns:c=\"urn:ietf:params:xml:ns:caldav\"><d:prop><d:getetag/><c:calendar-data><c:expand start=\""+start+"\" end=\""+end+"\"/></c:calendar-data></d:prop><c:filter><c:comp-filter name=\"VCALENDAR\"><c:comp-filter name=\"VEVENT\"><c:time-range start=\""+start+"\" end=\""+end+"\"/></c:comp-filter></c:comp-filter></c:filter></c:calendar-query>";
    }
    static boolean sameOrigin(URI first,URI second){return "https".equalsIgnoreCase(second.getScheme())&&second.getUserInfo()==null&&second.getFragment()==null&&first.getHost().equalsIgnoreCase(second.getHost())&&(first.getPort()==-1?443:first.getPort())==(second.getPort()==-1?443:second.getPort());}
    static JSONArray sync(String url,String user,String password,String month)throws Exception{return sync(url,user,password,month,new OkHttpClient.Builder().connectTimeout(20,TimeUnit.SECONDS).readTimeout(40,TimeUnit.SECONDS).callTimeout(60,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build());}
    static JSONArray sync(String url,String user,String password,String month,OkHttpClient client)throws Exception{
        if(user.isEmpty()||user.length()>320||user.contains(":" )||user.contains("\r")||user.contains("\n")||password.isEmpty()||password.length()>2048)throw new IllegalArgumentException("Проверьте логин и пароль календаря.");
        URI initial=address(url),current=initial;String body=query(month);
        try{
            for(int redirects=0;redirects<4;redirects++){
                Request request=new Request.Builder().url(current.toString()).header("Authorization",Credentials.basic(user,password,java.nio.charset.StandardCharsets.UTF_8)).header("Depth","1").header("Accept","application/xml, text/xml").method("REPORT",RequestBody.create(MediaType.parse("application/xml; charset=utf-8"),body)).build();
                try(Response response=client.newCall(request).execute()){
                    int code=response.code();
                    if(code==301||code==302||code==307||code==308){String location=response.header("Location");if(location==null)throw new Failure("Сервер вернул некорректное перенаправление.");URI next=address(current.resolve(location).toString());if(!sameOrigin(initial,next))throw new Failure("Сервер перенаправляет вход на другой адрес. Скопируйте прямую CalDAV-ссылку.");current=next;continue;}
                    if(code==401||code==403)throw new Failure("Календарь отклонил вход. Проверьте пароль внешнего приложения и доступ к календарю.");
                    if(code!=207)throw new Failure("Календарь вернул HTTP "+code+". Нужна CalDAV-ссылка на конкретный календарь, а не адрес веб-страницы.");
                    if(response.body()==null)throw new Failure("Пустой ответ календаря.");
                    try(InputStream in=response.body().byteStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>LIMIT)throw new Failure("Ответ календаря слишком большой.");out.write(buffer,0,n);}return parse(out.toString("UTF-8"),month);}
                }
            }
            throw new Failure("Слишком много перенаправлений календаря.");
        }finally{client.connectionPool().evictAll();client.dispatcher().executorService().shutdown();}
    }
    static JSONArray parse(String xml)throws Exception{return parse(xml,null);}
    static JSONArray parse(String xml,String month)throws Exception{
        if(xml.length()>LIMIT||java.util.regex.Pattern.compile("(?is)<\\s*!\\s*(DOCTYPE|ENTITY)\\b").matcher(xml).find())throw new Failure("Небезопасный ответ календаря.");
        DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();factory.setNamespaceAware(true);factory.setExpandEntityReferences(false);
        DocumentBuilder builder=factory.newDocumentBuilder();builder.setEntityResolver((publicId,systemId)->{throw new org.xml.sax.SAXException("External XML refused");});
        Document document=builder.parse(new org.xml.sax.InputSource(new StringReader(xml)));Element root=document.getDocumentElement();
        if(!"multistatus".equals(root.getLocalName())||!"DAV:".equals(root.getNamespaceURI()))throw new Failure("Сервер вернул не CalDAV-ответ.");
        List<String> calendars=new ArrayList<>();NodeList responses=root.getElementsByTagNameNS("DAV:","response");
        for(int i=0;i<responses.getLength();i++){
            Element response=(Element)responses.item(i);NodeList statuses=response.getElementsByTagNameNS("DAV:","status");for(int h=0;h<statuses.getLength();h++)if(statuses.item(h).getTextContent().matches(".* (401|403)(?: .*)?"))throw new Failure("Нет доступа к событиям рабочего календаря.");NodeList properties=response.getElementsByTagNameNS("DAV:","propstat");
            for(int j=0;j<properties.getLength();j++){
                Element prop=(Element)properties.item(j);NodeList status=prop.getElementsByTagNameNS("DAV:","status");if(status.getLength()!=1||!status.item(0).getTextContent().matches(".* 200(?: .*)?"))continue;
                NodeList data=prop.getElementsByTagNameNS("urn:ietf:params:xml:ns:caldav","calendar-data");
                for(int k=0;k<data.getLength();k++)calendars.add(data.item(k).getTextContent());
            }
        }
        return new JSONArray(CalendarRecurrence.expand(calendars,month));
    }
    static final class Property {final String params,value;Property(String p,String v){params=p;value=v;}}
    static int delimiter(String line){boolean quoted=false;for(int i=0;i<line.length();i++){char c=line.charAt(i);if(c=='"')quoted=!quoted;else if(c==':'&&!quoted)return i;}return -1;}
    static List<JSONObject> events(String ics)throws Exception{return CalendarRecurrence.expand(Collections.singletonList(ics),null);}
    static String value(Map<String,Property> event,String key,String fallback){return event.containsKey(key)?text(event.get(key).value):fallback;}
    static String text(String value){StringBuilder out=new StringBuilder();for(int i=0;i<value.length();i++){char c=value.charAt(i);if(c=='\\'&&i+1<value.length()){char next=value.charAt(++i);out.append(next=='n'||next=='N'?'\n':next);}else out.append(c);}return out.toString();}
    static ZonedDateTime instant(Property property)throws Exception{
        if(property==null)throw new Failure("У события нет даты начала.");String value=property.value;
        if(value.matches("[0-9]{8}"))return LocalDate.parse(value,DateTimeFormatter.BASIC_ISO_DATE).atStartOfDay(DISPLAY_ZONE);
        ZoneId zone=DISPLAY_ZONE;java.util.regex.Matcher tz=java.util.regex.Pattern.compile("(?i)(?:^|;)TZID=(\"[^\"]*\"|[^;]*)").matcher(property.params);
        if(tz.find()){String name=tz.group(1).replace("\"","");try{zone=ZoneId.of(name);}catch(DateTimeException error){throw new Failure("Неизвестный часовой пояс события: "+name);}}
        if(value.endsWith("Z")){zone=ZoneOffset.UTC;value=value.substring(0,value.length()-1);}
        return LocalDateTime.parse(value,DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss").withResolverStyle(java.time.format.ResolverStyle.STRICT)).atZone(zone).withZoneSameInstant(DISPLAY_ZONE);
    }
    static JSONObject event(Map<String,Property> event)throws Exception{
        Property start=event.get("DTSTART");ZonedDateTime time=instant(start),end=time.plus(CalendarRecurrence.duration(event));
        boolean allDay=start.value.matches("[0-9]{8}");String uid=value(event,"UID","");if(uid.isEmpty())throw new Failure("У события нет UID.");
        return new JSONObject().put("id",uid+"|"+time.toInstant()).put("title",value(event,"SUMMARY","Без названия")).put("date",time.toLocalDate().toString()).put("time",allDay?"":time.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))).put("endDate",end.toLocalDate().toString()).put("endTime",allDay?"":end.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))).put("allDay",allDay).put("location",value(event,"LOCATION","")).put("description",value(event,"DESCRIPTION","")).put("kind","remote");
    }
}
