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
    static final String MAIL_CHECK_ACTION="ru.local.spbumailpilot.MAIL_CHECK";
    private static final long INTERVAL_MS=15*60*1000L;
    private static final String MAIL="mail-arrivals",TASKS="task-deadlines";
    interface Stopped {boolean get();}
    static String scope(SavedAccounts.Login login)throws Exception{
        byte[] bytes=MessageDigest.getInstance("SHA-256").digest((login.host.toLowerCase(Locale.ROOT)+":"+login.port+":"+login.user.toLowerCase(Locale.ROOT)).getBytes(StandardCharsets.UTF_8));StringBuilder result=new StringBuilder("account-");for(byte b:bytes)result.append(String.format("%02x",b));return result.toString();
    }
    static SharedPreferences state(Context context,SavedAccounts.Login login)throws Exception{return context.getSharedPreferences(scope(login),Context.MODE_PRIVATE);}
    static JSONObject settings(Context context,SavedAccounts.Login login)throws Exception{return new JSONObject(state(context,login).getString("state","{}")).optJSONObject("settings");}
    private static boolean enabled(JSONObject settings,String key){return settings==null||settings.optBoolean(key,true);}
    static void channels(Context context){
        NotificationManager manager=context.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(MAIL,"Новые письма",NotificationManager.IMPORTANCE_DEFAULT));
        manager.createNotificationChannel(new NotificationChannel(TASKS,"Сроки задач",NotificationManager.IMPORTANCE_DEFAULT));
    }
    static boolean allowed(Context context){return (Build.VERSION.SDK_INT<33||context.checkSelfPermission("android.permission.POST_NOTIFICATIONS")==PackageManager.PERMISSION_GRANTED)&&context.getSystemService(NotificationManager.class).areNotificationsEnabled();}
    static boolean channelAllowed(Context context,String id){NotificationChannel channel=context.getSystemService(NotificationManager.class).getNotificationChannel(id);return allowed(context)&&channel!=null&&channel.getImportance()!=NotificationManager.IMPORTANCE_NONE;}
    private static PendingIntent alarm(Context context){return PendingIntent.getBroadcast(context,JOB,new Intent(context,NoticeAlarm.class).setAction("ru.local.spbumailpilot.TASK_DUE"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    private static PendingIntent mailAlarm(Context context){return PendingIntent.getBroadcast(context,JOB+1,new Intent(context,NoticeAlarm.class).setAction(MAIL_CHECK_ACTION),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    static boolean mailEnabled(Context context,SavedAccounts.Login login,boolean continuous)throws Exception{
        JSONObject options=settings(context,login);
        return AccountMailChecks.enabled(enabled(options,"mailNotifications"),enabled(options,"backgroundMail"),continuous);
    }
    static boolean anyMail(Context context,boolean continuous)throws Exception{
        for(SavedAccounts.Login login:new CredentialVault(context).list())if(mailEnabled(context,login,continuous))return true;
        return false;
    }
    static void enqueueMail(Context context)throws Exception{
        if(!anyMail(context,false))return;
        JobScheduler scheduler=context.getSystemService(JobScheduler.class);
        if(scheduler.getPendingJob(JOB+1)==null)scheduler.schedule(new JobInfo.Builder(JOB+1,new ComponentName(context,NoticeJob.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setMinimumLatency(0).setBackoffCriteria(30000,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build());
    }
    static void schedule(Context context)throws Exception{
        BackgroundMailService.reconcile(context,false);
        channels(context);CredentialVault vault=new CredentialVault(context);SavedAccounts.Login login=vault.load();
        SharedPreferences control=context.getSharedPreferences("notice-owner",Context.MODE_PRIVATE);
        NotificationManager manager=context.getSystemService(NotificationManager.class);
        String owner=login==null?"":scope(login),previousOwner=control.getString("owner","");
        if(!owner.equals(previousOwner)&&!previousOwner.isEmpty())cancelTasks(context,previousOwner);
        HashSet<String> retained=new HashSet<>();
        for(SavedAccounts.Login saved:vault.list()){
            String accountScope=scope(saved);retained.add(accountScope);
            if(!mailEnabled(context,saved,false))manager.cancel(accountScope+"/mail",1);
        }
        for(String removed:control.getStringSet("accounts",Collections.emptySet()))if(!retained.contains(removed)){
            manager.cancel(removed+"/mail",1);cancelTasks(context,removed);
            context.getSharedPreferences("notices-"+removed,Context.MODE_PRIVATE).edit().clear().apply();
        }
        control.edit().putString("owner",owner).putStringSet("accounts",retained).apply();
        JobScheduler scheduler=context.getSystemService(JobScheduler.class);AlarmManager alarms=context.getSystemService(AlarmManager.class);
        boolean mailEnabled=anyMail(context,false),taskEnabled=login!=null&&enabled(settings(context,login),"taskNotifications");
        if(mailEnabled||taskEnabled){
            JobInfo previous=scheduler.getPendingJob(JOB);
            if(previous==null||previous.getNetworkType()!=JobInfo.NETWORK_TYPE_ANY)scheduler.schedule(new JobInfo.Builder(JOB,new ComponentName(context,NoticeJob.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(INTERVAL_MS).setPersisted(true).build());
        }else scheduler.cancel(JOB);
        if(mailEnabled)alarms.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,android.os.SystemClock.elapsedRealtime()+60*1000L,INTERVAL_MS,mailAlarm(context));else{alarms.cancel(mailAlarm(context));scheduler.cancel(JOB+1);}
        tasks(context);
    }
    private static void cancelTasks(Context context,String accountScope){
        SharedPreferences record=context.getSharedPreferences("notices-"+accountScope,Context.MODE_PRIVATE);
        for(String key:record.getStringSet("tasks",Collections.emptySet()))context.getSystemService(NotificationManager.class).cancel(accountScope+"/task/"+key,1);
    }
    private static boolean post(Context context,String channel,String tag,String title,String text){
        return post(context,channel,tag,title,text,null);
    }
    private static boolean post(Context context,String channel,String tag,String title,String text,MailNoticeTarget target){
        return post(context,channel,tag,title,text,target,null);
    }
    private static boolean post(Context context,String channel,String tag,String title,String text,MailNoticeTarget target,String mailbox){
        if(!channelAllowed(context,channel))return false;
        Intent open=new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if(channel.equals(TASKS))open.putExtra("openTasks",true);
        if(target!=null)try{open.setData(android.net.Uri.parse("mailpilot://mail/"+target.account+"/"+target.validity+"/"+target.uid));open.putExtra("openMail",target.encode());}catch(Exception invalid){return false;}
        PendingIntent click=PendingIntent.getActivity(context,channel.equals(TASKS)?2:1,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification publicVersion=new Notification.Builder(context,channel).setSmallIcon(android.R.drawable.ic_dialog_email).setContentTitle("Почта СПбГУ").setContentText("Новое уведомление").build();
        Notification.Builder builder=new Notification.Builder(context,channel).setSmallIcon(android.R.drawable.ic_dialog_email).setContentTitle(title).setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text)).setContentIntent(click).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(publicVersion);
        if(mailbox!=null)builder.setSubText(MailNotificationText.clean(mailbox,"",180));
        Notification notice=builder.build();
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
                String reminder=task.optString("reminder","auto");long fire=ReminderPolicy.fireAt(task.getString("date"),task.optString("time"),reminder);if(fire<0)continue;
                String key=ReminderPolicy.taskKey(task.getString("id"),task.getString("date"),task.optString("time"),reminder);current.add(key);
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
    static void mail(Context context,Stopped stopped)throws Exception{mail(context,stopped,false);}
    static synchronized void mail(Context context,Stopped stopped,boolean continuous)throws Exception{
        if(stopped.get()||!channelAllowed(context,MAIL))return;
        CredentialVault vault=new CredentialVault(context);
        AccountMailChecks.perform(vault.list(),login->mailEnabled(context,login,continuous),login->{
            SharedPreferences record=context.getSharedPreferences("notices-"+scope(login),Context.MODE_PRIVATE);
            String oldValidity=record.getString("validity",null);long oldUid=record.getLong("uid",0);
            try(MailReader reader=new MailReader()){
                if(stopped.get())return;
                reader.connect(login.host,login.port,login.user,login.password);
                JSONObject update=reader.arrivals(oldValidity,oldUid);if(stopped.get())return;
                SavedAccounts.Login remembered=null;
                for(SavedAccounts.Login current:vault.list())if(current.identity().equals(login.identity())){remembered=current;break;}
                if(remembered==null||!remembered.password.equals(login.password)||!mailEnabled(context,remembered,continuous))return;
                int count=update.getInt("count");
                MailNoticeTarget target=MailNoticeTarget.parse(new JSONObject().put("account",scope(login)).put("uid",update.optString("openUid")).put("validity",update.getString("validity")).toString());
                if(count>0&&!post(context,MAIL,scope(login)+"/mail",MailNotificationText.title(update),MailNotificationText.text(update,login.user),target,login.user))return;
                record.edit().putString("validity",update.getString("validity")).putLong("uid",update.getLong("uid")).apply();
            }
        },stopped::get);
    }
}
