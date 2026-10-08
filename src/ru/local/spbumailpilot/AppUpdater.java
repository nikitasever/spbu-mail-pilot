package ru.local.spbumailpilot;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import org.json.*;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicBoolean;

/** Explicit OTA install; HTTPS, checksum and installed signing certificate are required. */
public final class AppUpdater {
    static final String REPO="nikitasever/spbu-mail-pilot-updates";
    private static final AtomicBoolean busy=new AtomicBoolean();
    static InputStream open(String address)throws Exception {
        URL url=new URL(address);
        for(int redirect=0;redirect<6;redirect++){
            if(!UpdatePolicy.allowed(url))throw new IOException("Untrusted update host");
            HttpURLConnection connection=(HttpURLConnection)url.openConnection();connection.setConnectTimeout(20000);connection.setReadTimeout(45000);connection.setInstanceFollowRedirects(false);connection.setRequestProperty("User-Agent","SPBU-Mail-Pilot");connection.setRequestProperty("Accept",UpdatePolicy.accept(url));
            int status=connection.getResponseCode();
            if(status>=300&&status<400){String next=connection.getHeaderField("Location");connection.disconnect();if(next==null)throw new IOException("Missing redirect");url=new URL(url,next);continue;}
            if(status!=200){connection.disconnect();throw new IOException("HTTP "+status);}
            return new FilterInputStream(connection.getInputStream()){@Override public void close()throws IOException{try{super.close();}finally{connection.disconnect();}}};
        }
        throw new IOException("Too many redirects");
    }
    private static String json(String url)throws Exception{try(InputStream in=open(url);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>256000)throw new IOException("Metadata too large");out.write(buffer,0,n);}return out.toString("UTF-8");}}
    static String hex(byte[] data){StringBuilder s=new StringBuilder();for(byte b:data)s.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return s.toString();}
    static void download(String url,String sha,File target)throws Exception{
        try(InputStream in=open(url)){UpdatePolicy.copy(in,sha,target,100L*1024*1024);}
    }
    private static long version(PackageInfo info){return Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode;}
    private static String certificate(PackageInfo info)throws Exception{
        Signature[] signatures=Build.VERSION.SDK_INT>=28?(info.signingInfo==null?null:info.signingInfo.getApkContentsSigners()):info.signatures;
        if(signatures==null||signatures.length!=1)throw new IOException("Unsupported signing identity");return hex(MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray()));
    }
    private static void validate(Activity activity,File apk,int wanted)throws Exception{
        PackageManager manager=activity.getPackageManager();int flags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;
        PackageInfo installed=manager.getPackageInfo(activity.getPackageName(),flags),candidate=manager.getPackageArchiveInfo(apk.getPath(),flags);
        if(candidate==null||!activity.getPackageName().equals(candidate.packageName)||version(candidate)!=wanted||version(candidate)<=version(installed)||!certificate(candidate).equals(certificate(installed)))throw new IOException("APK identity or version mismatch");
    }
    private static void alert(Activity a,String title,String message){if(!a.isFinishing()&&!a.isDestroyed())new AlertDialog.Builder(a).setTitle(title).setMessage(message).setPositiveButton("Хорошо",null).show();}
    public static void check(Activity activity){
        if(!busy.compareAndSet(false,true)){alert(activity,"Обновление","Проверка или загрузка уже выполняется.");return;}
        android.widget.Toast.makeText(activity,"Проверка обновления…",android.widget.Toast.LENGTH_SHORT).show();
        new Thread(()->{try{
            JSONObject release=new JSONObject(json("https://api.github.com/repos/"+REPO+"/releases/latest"));JSONArray assets=release.getJSONArray("assets");String metadata=null;
            for(int i=0;i<assets.length();i++){JSONObject asset=assets.getJSONObject(i);if("update.json".equals(asset.getString("name")))metadata=asset.getString("browser_download_url");}
            if(metadata==null||!metadata.startsWith("https://github.com/"+REPO+"/releases/download/"))throw new IOException("Missing update metadata");
            JSONObject update=new JSONObject(json(metadata));int wanted=update.getInt("versionCode");String name=update.getString("versionName"),apk=update.getString("url"),sha=update.getString("sha256");
            if(!"ru.local.spbumailpilot".equals(update.getString("packageId"))||!apk.startsWith("https://github.com/"+REPO+"/releases/download/"))throw new IOException("Wrong release");
            if(wanted<=version(activity.getPackageManager().getPackageInfo(activity.getPackageName(),0))){activity.runOnUiThread(()->alert(activity,"Обновление","Установлена последняя версия."));return;}
            activity.runOnUiThread(()->{if(activity.isFinishing()||activity.isDestroyed())return;new AlertDialog.Builder(activity).setTitle("Доступна версия "+name).setMessage("Скачать обновление? Аккаунты и задачи сохранятся. Android попросит подтвердить установку.").setNegativeButton("Позже",null).setPositiveButton("Скачать",(dialog,which)->fetch(activity,apk,sha,wanted)).show();});
        }catch(Exception error){activity.runOnUiThread(()->alert(activity,"Обновление","Не удалось проверить обновление. Проверьте интернет и попробуйте позже."));}finally{busy.set(false);}},"update-check").start();
    }
    private static void fetch(Activity activity,String url,String sha,int wanted){
        if(!busy.compareAndSet(false,true))return;
        android.widget.Toast.makeText(activity,"Загрузка обновления…",android.widget.Toast.LENGTH_LONG).show();
        new Thread(()->{File apk=new File(new File(activity.getCacheDir(),"updates"),"update.apk");try{
            if(!apk.getParentFile().isDirectory()&&!apk.getParentFile().mkdirs())throw new IOException("Cannot create update directory");download(url,sha,apk);validate(activity,apk,wanted);
            activity.runOnUiThread(()->{if(activity.isFinishing()||activity.isDestroyed())return;
                if(!activity.getPackageManager().canRequestPackageInstalls()){
                    new AlertDialog.Builder(activity).setTitle("Разрешение на обновления").setMessage("Разрешите установку из этого приложения в настройках Android. Затем снова нажмите «Проверить обновление».").setPositiveButton("Открыть настройки",(d,w)->{try{activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.getPackageName())));}catch(Exception e){alert(activity,"Обновление","Откройте разрешение установки в настройках Android.");}}).setNegativeButton("Позже",null).show();return;
                }
                Intent install=new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://ru.local.spbumailpilot.updates/update.apk"),"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try{activity.startActivity(install);}catch(Exception e){alert(activity,"Обновление","Не удалось открыть установщик Android.");}
            });
        }catch(Exception error){apk.delete();activity.runOnUiThread(()->alert(activity,"Обновление","Загрузка или проверка APK не удалась. Установка отменена; текущая версия сохранена."));}finally{busy.set(false);}},"update-download").start();
    }
}
