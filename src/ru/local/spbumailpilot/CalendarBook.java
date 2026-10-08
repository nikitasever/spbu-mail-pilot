package ru.local.spbumailpilot;

import android.content.*;
import android.security.keystore.*;
import android.util.Base64;
import javax.crypto.*;
import java.security.KeyStore;
import org.json.*;

/** Separate encrypted CalDAV credentials for each mail-account scope. */
final class CalendarBook {
    static final String ALIAS="spbu-mail-calendars-v1";
    private static final java.util.concurrent.ConcurrentHashMap<String,Object> LOCKS=new java.util.concurrent.ConcurrentHashMap<>();
    private final SharedPreferences prefs;private final Object lock;
    CalendarBook(Context context,SavedAccounts.Login login)throws Exception{String scope=NoticeCenter.scope(login);lock=LOCKS.computeIfAbsent(scope,k->new Object());prefs=context.getSharedPreferences("calendars-"+scope,Context.MODE_PRIVATE);}
    private SecretKey key()throws Exception{
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);if(store.containsAlias(ALIAS))return (SecretKey)store.getKey(ALIAS,null);
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build());return generator.generateKey();
    }
    private JSONArray connections()throws Exception{String value=prefs.getString("encrypted",null);if(value==null)return new JSONArray();return new JSONArray(new String(SessionSecret.decrypt(key(),Base64.decode(value,Base64.NO_WRAP)),java.nio.charset.StandardCharsets.UTF_8));}
    private void write(JSONArray values)throws Exception{String encrypted=Base64.encodeToString(SessionSecret.encrypt(key(),values.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)),Base64.NO_WRAP);if(!prefs.edit().putString("encrypted",encrypted).commit())throw new java.io.IOException("Не удалось сохранить подключение календаря.");}
    void add(String title,String url,String user,String password,String month)throws Exception{
        long revision=prefs.getLong("revision",0);JSONArray values=connections();if(values.length()>=10)throw new IllegalArgumentException("Можно подключить до десяти календарей.");
        for(int i=0;i<values.length();i++)if(values.getJSONObject(i).getString("url").equals(url)&&values.getJSONObject(i).getString("user").equals(user))throw new IllegalArgumentException("Этот календарь уже подключён.");
        JSONArray events=CalDav.sync(url,user,password,month);String id=java.util.UUID.randomUUID().toString();JSONObject connection=new JSONObject().put("id",id).put("title",title).put("url",url).put("user",user).put("password",password);values.put(connection);synchronized(lock){if(prefs.getLong("revision",0)!=revision)throw new CalDav.Failure("Подключение календаря отменено.");cache(id,month,events);write(values);}
    }
    private void cache(String id,String month,JSONArray events)throws Exception{SharedPreferences.Editor edit=prefs.edit();String prefix="cache-"+id+"-";java.util.ArrayList<String> months=new java.util.ArrayList<>();for(String name:prefs.getAll().keySet())if(name.startsWith(prefix))months.add(name);java.util.Collections.sort(months);while(months.size()>=6)edit.remove(months.remove(0));if(!edit.putString(prefix+month,new JSONObject().put("events",events).put("at",java.time.Instant.now().toString()).toString()).commit())throw new java.io.IOException("Не удалось сохранить события.");}
    JSONObject list(String month,boolean refresh)throws Exception{return list(month,refresh,()->true);}
    JSONObject list(String month,boolean refresh,java.util.function.BooleanSupplier active)throws Exception{
        CalDav.query(month);long revision=prefs.getLong("revision",0);JSONArray values=connections(),calendars=new JSONArray(),events=new JSONArray();
        for(int i=0;i<values.length();i++){
            if(!active.getAsBoolean())throw new CalDav.Failure("Обновление календаря отменено.");
            JSONObject connection=values.getJSONObject(i);String id=connection.getString("id"),title=connection.getString("title"),error="";
            if(refresh)try{JSONArray incoming=CalDav.sync(connection.getString("url"),connection.getString("user"),connection.getString("password"),month);synchronized(lock){if(prefs.getLong("revision",0)!=revision)throw new CalDav.Failure("Календарь отключён.");cache(id,month,incoming);}}catch(Exception failure){error=failure instanceof CalDav.Failure?failure.getMessage():"Не удалось обновить календарь. Проверьте сеть и данные подключения. Сохранённые события оставлены.";}
            JSONObject cached=new JSONObject(prefs.getString("cache-"+id+"-"+month,"{}"));JSONArray items=cached.optJSONArray("events");if(items!=null)for(int j=0;j<items.length();j++)events.put(new JSONObject(items.getJSONObject(j).toString()).put("calendarId",id).put("calendarTitle",title));
            calendars.put(new JSONObject().put("id",id).put("title",title).put("at",cached.optString("at","")).put("cached",items!=null).put("error",error));
        }
        return new JSONObject().put("month",month).put("calendars",calendars).put("events",events);
    }
    void clear()throws Exception{synchronized(lock){long revision=prefs.getLong("revision",0)+1;if(!prefs.edit().clear().putLong("revision",revision).commit())throw new java.io.IOException("Не удалось удалить календари.");}}
    void remove(String id)throws Exception{synchronized(lock){JSONArray values=connections(),kept=new JSONArray();for(int i=0;i<values.length();i++)if(!values.getJSONObject(i).getString("id").equals(id))kept.put(values.getJSONObject(i));write(kept);SharedPreferences.Editor edit=prefs.edit();for(String name:prefs.getAll().keySet())if(name.startsWith("cache-"+id+"-"))edit.remove(name);if(!edit.putLong("revision",prefs.getLong("revision",0)+1).commit())throw new java.io.IOException("Не удалось удалить календарь.");}}
}
