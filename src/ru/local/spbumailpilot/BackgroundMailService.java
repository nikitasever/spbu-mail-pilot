package ru.local.spbumailpilot;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import org.json.*;

/** User-visible continuous mail monitoring, separate from the open mailbox. */
public final class BackgroundMailService extends Service {
    private static final String CHANNEL="background-mail";
    private static final int ID=2408;
    private static volatile boolean running;
    private BackgroundMailLoop loop;
    static boolean wanted(Context context)throws Exception{
        return NoticeCenter.allowed(context)&&NoticeCenter.anyMail(context,true);
    }
    static void reconcile(Context context,boolean visible){
        try{if(!wanted(context)){context.stopService(new Intent(context,BackgroundMailService.class));return;}
            if(visible&&!running)context.startForegroundService(new Intent(context,BackgroundMailService.class));
        }catch(Exception unavailable){context.getSharedPreferences("background-mail-status",MODE_PRIVATE).edit().putBoolean("startFailed",true).apply();}
    }
    private Notification notice(){
        PendingIntent open=PendingIntent.getActivity(this,ID,new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_email).setContentTitle("Фоновая проверка почты")
            .setContentText("Сохранённые ящики · проверка раз в минуту").setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_SECRET).build();
    }
    @Override public void onCreate(){
        super.onCreate();getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL,"Фоновая проверка",NotificationManager.IMPORTANCE_LOW));
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        try{
            if(!wanted(this)){stopSelf();return START_NOT_STICKY;}
            if(Build.VERSION.SDK_INT>=34)startForeground(ID,notice(),ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);else startForeground(ID,notice());
            running=true;getSharedPreferences("background-mail-status",MODE_PRIVATE).edit().putBoolean("startFailed",false).apply();
            if(loop==null)loop=new BackgroundMailLoop(stopped->{
                if(!wanted(this)){stopSelf();return;}
                PowerManager.WakeLock wake=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"spbu-mail:check");
                try{wake.acquire(120000);NoticeCenter.mail(this,stopped::getAsBoolean,true);
                    if(!stopped.getAsBoolean())getSharedPreferences("background-mail-status",MODE_PRIVATE).edit().putLong("checkedAt",System.currentTimeMillis()).putBoolean("checkFailed",false).apply();
                }catch(Exception unavailable){getSharedPreferences("background-mail-status",MODE_PRIVATE).edit().putBoolean("checkFailed",true).apply();throw unavailable;}
                finally{if(wake.isHeld())wake.release();}
            },60000);
            return START_STICKY;
        }catch(Exception unavailable){running=false;getSharedPreferences("background-mail-status",MODE_PRIVATE).edit().putBoolean("startFailed",true).apply();stopSelf();return START_NOT_STICKY;}
    }
    static String status(Context context){
        PowerManager power=context.getSystemService(PowerManager.class);
        return (running?"Фоновая служба работает.":"Фоновая служба остановлена.")+(power.isIgnoringBatteryOptimizations(context.getPackageName())?" Работа без ограничений батареи разрешена.":" Разрешите работу без ограничений батареи: во время сна Android может задерживать сеть.");
    }
    @Override public void onDestroy(){running=false;if(loop!=null)loop.close();loop=null;stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}

