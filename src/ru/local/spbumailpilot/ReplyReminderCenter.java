package ru.local.spbumailpilot;
import android.app.*;
import android.app.job.*;
import android.content.*;
import org.json.*;
import java.util.*;

final class ReplyReminderCenter {
    static final int JOB=2412;static final String ACTION="ru.local.spbumailpilot.REPLY_REMINDER";
    private static PendingIntent alarm(Context c){return PendingIntent.getBroadcast(c,JOB,new Intent(c,NoticeAlarm.class).setAction(ACTION),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    static List<ReplyReminder> items(Context c,SavedAccounts.Login login)throws Exception{return ReplyReminder.parse(new JSONObject(NoticeCenter.state(c,login).getString("state","{}")).optJSONArray("replyReminders"),NoticeCenter.scope(login));}
    static SharedPreferences record(Context c,String account){return c.getSharedPreferences("reply-reminders-"+account,Context.MODE_PRIVATE);}
    static synchronized void schedule(Context c)throws Exception{
        NoticeCenter.channels(c);AlarmManager alarms=c.getSystemService(AlarmManager.class);alarms.cancel(alarm(c));long next=Long.MAX_VALUE,now=System.currentTimeMillis();
        Set<String> accounts=new HashSet<>();for(SavedAccounts.Login login:new CredentialVault(c).list()){
            String account=NoticeCenter.scope(login);accounts.add(account);try{SharedPreferences prefs=record(c,account);Set<String> active=new HashSet<>();
            for(ReplyReminder item:items(c,login)){active.add(item.key);if(!prefs.contains(item.key))next=Math.min(next,item.at);}
            SharedPreferences.Editor edit=prefs.edit();for(String key:prefs.getAll().keySet())if(!active.contains(key)){edit.remove(key);c.getSystemService(NotificationManager.class).cancel(account+"/reply/"+key,1);}edit.commit();}catch(Exception unavailable){next=Math.min(next,now);}
        }
        SharedPreferences owners=c.getSharedPreferences("reply-reminder-owners",Context.MODE_PRIVATE);for(String account:owners.getStringSet("accounts",Collections.emptySet()))if(!accounts.contains(account)){for(String key:record(c,account).getAll().keySet())c.getSystemService(NotificationManager.class).cancel(account+"/reply/"+key,1);record(c,account).edit().clear().commit();}owners.edit().putStringSet("accounts",accounts).commit();
        JobScheduler jobs=c.getSystemService(JobScheduler.class);
        if(next==Long.MAX_VALUE){jobs.cancel(JOB);return;}
        if(next<=now){enqueue(c);return;}alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next,alarm(c));
    }
    static void enqueue(Context c){JobScheduler jobs=c.getSystemService(JobScheduler.class);if(jobs.getPendingJob(JOB)==null)jobs.schedule(new JobInfo.Builder(JOB,new ComponentName(c,ReplyReminderJob.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setMinimumLatency(0).setBackoffCriteria(60000,JobInfo.BACKOFF_POLICY_EXPONENTIAL).setPersisted(true).build());}
    static boolean check(Context c,NoticeCenter.Stopped stopped)throws Exception{
        boolean retry=false;long now=System.currentTimeMillis();
        for(SavedAccounts.Login login:new CredentialVault(c).list())try{
            if(stopped.get())return true;String account=NoticeCenter.scope(login);List<ReplyReminder> due=new ArrayList<>();for(ReplyReminder item:items(c,login))if(item.at<=now&&!record(c,account).contains(item.key))due.add(item);
            if(due.isEmpty())continue;if(due.size()>20){due=new ArrayList<>(due.subList(0,20));retry=true;}
            try(MailReader reader=new MailReader()){
                reader.connect(login.host,login.port,login.user,login.password);
                for(ReplyReminder item:due){if(stopped.get())return true;boolean answered=reader.hasReply(item.messageId,login.user,login.from);
                    // Serialize with schedule; re-read state after network, never commit a cancelled or deleted account.
                    synchronized(ReplyReminderCenter.class){
                        boolean retained=false;for(SavedAccounts.Login current:new CredentialVault(c).list())if(current.identity().equals(login.identity())&&current.password.equals(login.password))retained=true;
                        if(!retained)break;boolean current=false;for(ReplyReminder fresh:items(c,login))if(fresh.key.equals(item.key))current=true;
                        if(!current||stopped.get()||record(c,account).contains(item.key))continue;
                        if(answered||NoticeCenter.reply(c,item,login.user)) {if(!record(c,account).edit().putString(item.key,answered?"answered":"notified").commit())retry=true;}else retry=true;
                    }
                }
            }catch(Exception unavailable){retry=true;}
        }catch(Exception unavailable){retry=true;}return retry;
    }
    static String status(Context c,SavedAccounts.Login login){JSONArray result=new JSONArray();try{String account=NoticeCenter.scope(login);for(ReplyReminder item:items(c,login))result.put(new JSONObject().put("mail",new JSONObject().put("folder",item.target.folder).put("uid",item.target.uid).put("validity",item.target.validity)).put("at",item.at).put("messageId",item.messageId).put("status",record(c,account).getString(item.key,"pending")));}catch(Exception unavailable){}return result.toString();}
}
