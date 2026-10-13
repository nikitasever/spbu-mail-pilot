package ru.local.spbumailpilot;

import android.content.*;

public final class NoticeAlarm extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){PendingResult result=goAsync();new Thread(()->{try{String action=intent.getAction();if(Intent.ACTION_BOOT_COMPLETED.equals(action)||Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)){try{NoticeCenter.schedule(context);BackgroundMailService.reconcile(context,true);}finally{ScheduledMail.reconcile(context);}}else if(ScheduledMail.ACTION.equals(action))ScheduledMail.reconcile(context);else if(NoticeCenter.MAIL_CHECK_ACTION.equals(action))NoticeCenter.enqueueMail(context);else if(NoticeCenter.SNOOZE_ACTION.equals(action))NoticeCenter.snoozes(context);else NoticeCenter.tasks(context);}catch(Exception ignored){}finally{MailWidget.request(context);result.finish();}},"mail-notices").start();}
}

