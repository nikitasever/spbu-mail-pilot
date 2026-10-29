package ru.local.spbumailpilot;
import org.json.*;
import java.net.URI;
import java.util.*;
import java.util.concurrent.TimeUnit;
import okhttp3.*;

/** Reuses the Android client's read-only CalDAV protocol; no mail credential is reused. */
final class DesktopCalendars {
    static final class Fault extends Exception{final String code;Fault(String code){this.code=code;}}
    interface Transport extends AutoCloseable{
        JSONObject discover(JSONObject args)throws Exception;
        JSONArray month(JSONObject args)throws Exception;
    }
    interface Factory{Transport create();}
    static Transport create(){return new Transport(){
        final OkHttpClient client=new OkHttpClient.Builder().connectTimeout(20,TimeUnit.SECONDS).readTimeout(40,TimeUnit.SECONDS).callTimeout(60,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build();
        public JSONObject discover(JSONObject args)throws Exception{return DesktopCalendars.discover(args,client);}
        public JSONArray month(JSONObject args)throws Exception{return DesktopCalendars.month(args,client);}
        public void close(){client.dispatcher().cancelAll();client.connectionPool().evictAll();client.dispatcher().executorService().shutdown();}
    };}
    static void auth(JSONObject args)throws Exception{
        String server=args.getString("server"),user=args.getString("user"),password=args.getString("password");CalDav.address(server);
        if(user.isEmpty()||user.length()>320||user.matches("(?s).*[\\p{Cntrl}:].*")||password.isEmpty()||password.length()>2048||password.indexOf('\0')>=0)throw new Fault("BAD_REQUEST");
    }
    static String monthKey(JSONObject args)throws Exception{
        String value=args.getString("month");if(!value.matches("(?:19|20)[0-9]{2}-(?:0[1-9]|1[0-2])|2100-(?:0[1-9]|1[0-2])"))throw new Fault("BAD_REQUEST");CalDav.query(value);return value;
    }
    static JSONObject discover(JSONObject args,OkHttpClient client)throws Exception{
        auth(args);JSONArray result=new JSONArray();
        if(args.optBoolean("direct",false)){
            String month=monthKey(args);CalDav.sync(args.getString("server"),args.getString("user"),args.getString("password"),month,client);
            result.put(new JSONObject().put("title","Календарь").put("url",args.getString("server")));
        }else for(CalendarDiscovery.Calendar item:CalendarDiscovery.discover(args.getString("server"),args.getString("user"),args.getString("password"),client))result.put(new JSONObject().put("title",item.title).put("url",item.url));
        return new JSONObject().put("calendars",result);
    }
    static JSONArray month(JSONObject args,OkHttpClient client)throws Exception{
        auth(args);String month=monthKey(args);URI origin=CalDav.address(args.getString("server")),url=CalDav.address(args.getString("url"));if(!CalDav.sameOrigin(origin,url))throw new Fault("CALENDAR_ORIGIN");
        return CalDav.sync(url.toString(),args.getString("user"),args.getString("password"),month,client);
    }
    static String code(Exception error){
        if(error instanceof Fault)return ((Fault)error).code;
        if(error instanceof IllegalArgumentException||error instanceof org.json.JSONException)return "BAD_REQUEST";
        if(error instanceof java.time.DateTimeException||error instanceof org.xml.sax.SAXException)return "CALENDAR_DATA";
        if(error instanceof CalDav.Failure){String text=error.getMessage();if(text.contains("отклонил вход")||text.contains("Нет доступа"))return "CALENDAR_AUTH";if(text.contains("другой"))return "CALENDAR_ORIGIN";return "CALENDAR_DATA";}
        return "CALENDAR_NETWORK";
    }
}
