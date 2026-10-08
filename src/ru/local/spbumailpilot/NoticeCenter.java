package ru.local.spbumailpilot;

import android.app.*;
import android.app.job.*;
import android.content.*;
import android.os.Build;
import android.content.pm.PackageManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;

final class NoticeCenter {
    static final int JOB=2406;
    private static final String MAIL="mail-arrivals",TASKS="task-deadlines";
    interface Stopped {boolean get();}
    static String scope(SavedAccounts.Login login)throws Exception{
        byte[] bytes=MessageDigest.getInstance("SHA-256").digest((login.host.toLowerCase(Locale.ROOT)+":"+login.port+":"+login.user.toLowerCase(Locale.ROOT)).getBytes(StandardCharsets.UTF_8));StringBuilder result=new StringBuilder("account-");for(byte b:bytes)result.append(String.format("%02x",b));return result.toString();
    }
    static SharedPreferences state(Context context,SavedAccounts.Login login)throws Exception{return context.getSharedPreferences(scope(login),Context.MODE_PRIVATE);}
    private static JSONObject settings(Context context,SavedAccounts.Login login)throws Exception{return new JSONObject(state(context,login).getString("state","{}")).optJSONObject("settings");}
    private static boolean enabled(JSONObject settings,String key){return settings==null||settings.optBoolean(key,true);}
    static void channels(Context context){
        NotificationManager manager=context.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(MAIL,"Новые письма",NotificationManager.IMPORTANCE_DEFAULT));
        manager.createNotificationChannel(new NotificationChannel(TASKS,"Сроки задач",NotificationManager.IMPORTANCE_DEFAULT));
    }
    static boolean allowed(Context context){return (Build.VERSION.SDK_INT<33||context.checkSelfPermission("android.permission.POST_NOTIFICATIONS")==PackageManager.PERMISSION_GRANTED)&&context.getSystemService(NotificationManager.class).areNotificationsEnabled();}
    static boolean channelAllowed(Context context,String id){NotificationChannel channel=context.getSystemService(NotificationManager.class).getNotificationChannel(id);return allowed(context)&&channel!=null&&channel.getImportance()!=NotificationManager.IMPORTANCE_NONE;}
    private static PendingIntent alarm(Context context){return PendingIntent.getBroadcast(context,JOB,new Intent(context,NoticeAlarm.class).setAction("ru.local.spbumailpilot.TASK_DUE"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    static void schedule(Context context)throws Exception{
        channels(context);SavedAccounts.Login login=new CredentialVault(context).load();
        SharedPreferences control=context.getSharedPreferences("notice-owner",Context.MODE_PRIVATE);
        String owner=login==null?"":scope(login);
        if(!owner.equals(control.getString("owner",""))){context.getSystemService(NotificationManager.class).cancelAll();control.edit().putString("owner",owner).apply();}
        JobScheduler scheduler=context.getSystemService(JobScheduler.class);
        if(login==null){scheduler.cancel(JOB);context.getSystemService(AlarmManager.class).cancel(alarm(context));context.getSystemService(NotificationManager.class).cancelAll();return;}
        JSONObject options=settings(context,login);
        if(enabled(options,"mailNotifications")||enabled(options,"taskNotifications")){
            if(scheduler.getPendingJob(JOB)==null)scheduler.schedule(new JobInfo.Builder(JOB,new ComponentName(context,NoticeJob.class)).setPeriodic(15*60*1000L).setPersisted(true).build());
        }else scheduler.cancel(JOB);
        tasks(context);
    }
    private static boolean post(Context context,String channel,String tag,String title,String text){
        if(!channelAllowed(context,channel))return false;
        Intent open=new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if(channel.equals(TASKS))open.putExtra("openTasks",true);
        PendingIntent click=PendingIntent.getActivity(context,channel.equals(TASKS)?2:1,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification publicVersion=new Notification.Builder(context,channel).setSmallIcon(android.R.drawable.ic_dialog_email).setContentTitle("Почта СПбГУ").setContentText("Новое уведомление").build();
        Notification notice=new Notification.Builder(context,channel).setSmallIcon(android.R.drawable.ic_dialog_email).setContentTitle(title).setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text)).setContentIntent(click).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(publicVersion).build();
        context.getSystemService(NotificationManager.class).notify(tag,1,notice);return true;
    }
    static void test(Context context){channels(context);post(context,MAIL,"test","Уведомления включены","Это тестовое уведомление почтового приложения.");}
    static void seed(Context context,SavedAccounts.Login login,MailReader reader)throws Exception{
        SharedPreferences record=context.getSharedPreferences("notices-"+scope(login),Context.MODE_PRIVATE);
        if(!record.contains("validity")){JSONObject baseline=reader.arrivals(null,0);record.edit().putString("validity",baseline.getString("validity")).putLong("uid",baseline.getLong("uid")).apply();}
    }
    static synchronized void tasks(Context context)throws Exception{
        SavedAccounts.Login login=new CredentialVault(context).load();AlarmManager alarms=context.getSystemService(AlarmManager.class);alarms.cancel(alarm(context));
        if(login==null)return;
        SharedPreferences state=state(context,login);JSONObject data=new JSONObject(state.getString("state","{}"));
        SharedPreferences record=context.getSharedPreferences("notices-"+scope(login),Context.MODE_PRIVATE);
        HashSet<String> sent=new HashSet<>(record.getStringSet("tasks",Collections.emptySet())),current=new HashSet<>();
        JSONArray tasks=data.optJSONArray("tasks");long now=System.currentTimeMillis(),next=Long.MAX_VALUE;
        if(enabled(data.optJSONObject("settings"),"taskNotifications")&&tasks!=null)for(int i=0;i<tasks.length();i++){
            JSONObject task=tasks.optJSONObject(i);if(task==null||task.optBoolean("done")||task.optString("date").isEmpty())continue;
            try{
                String key=task.getString("id")+"/"+task.getString("date")+"/"+task.optString("time");current.add(key);
                long fire=ReminderPolicy.fireAt(task.getString("date"),task.optString("time"));
                if(fire>now)next=Math.min(next,fire);
                else if(!sent.contains(key)&&ReminderPolicy.ready(fire,now)){
                    String text=task.getString("date")+(task.optString("time").isEmpty()?" · срок сегодня":" · до "+task.optString("time"));
                    if(post(context,TASKS,scope(login)+"/task/"+key,task.optString("title","Срок задачи"),text))sent.add(key);
                }
            }catch(Exception malformed){}
        }
        for(String key:new HashSet<>(sent))if(!current.contains(key)){context.getSystemService(NotificationManager.class).cancel(scope(login)+"/task/"+key,1);sent.remove(key);}
        record.edit().putStringSet("tasks",sent).apply();
        if(next!=Long.MAX_VALUE)alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next,alarm(context));
    }
    static void mail(Context context,Stopped stopped)throws Exception{
        SavedAccounts.Login login=new CredentialVault(context).load();if(login==null||!enabled(settings(context,login),"mailNotifications")||!channelAllowed(context,MAIL))return;
        SharedPreferences record=context.getSharedPreferences("notices-"+scope(login),Context.MODE_PRIVATE);
        String oldValidity=record.getString("validity",null);long oldUid=record.getLong("uid",0);
        try(MailReader reader=new MailReader()){
            reader.connect(login.host,login.port,login.user,login.password);
            JSONObject update=reader.arrivals(oldValidity,oldUid);if(stopped.get())return;
            SavedAccounts.Login active=new CredentialVault(context).load();if(active==null||!active.identity().equals(login.identity()))return;
            int count=update.getInt("count");
            if(count>0&&!post(context,MAIL,scope(login)+"/mail","Новые письма · "+count,login.user+"\n"+update.optString("summary")))return;
            record.edit().putString("validity",update.getString("validity")).putLong("uid",update.getLong("uid")).apply();
        }
    }
}
