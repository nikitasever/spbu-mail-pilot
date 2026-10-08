package ru.local.spbumailpilot;

import android.content.*;

public final class NoticeAlarm extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){PendingResult result=goAsync();new Thread(()->{try{if(Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()))NoticeCenter.schedule(context);else NoticeCenter.tasks(context);}catch(Exception ignored){}finally{result.finish();}},"task-reminders").start();}
}
