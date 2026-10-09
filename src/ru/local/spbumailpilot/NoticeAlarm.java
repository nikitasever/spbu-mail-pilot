package ru.local.spbumailpilot;

import android.content.*;

public final class NoticeAlarm extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){PendingResult result=goAsync();new Thread(()->{try{String action=intent.getAction();if(Intent.ACTION_BOOT_COMPLETED.equals(action)||Intent.ACTION_MY_PACKAGE_REPLACED.equals(action))NoticeCenter.schedule(context);else if(NoticeCenter.MAIL_CHECK_ACTION.equals(action))NoticeCenter.mail(context,()->false);else NoticeCenter.tasks(context);}catch(Exception ignored){}finally{result.finish();}},"mail-notices").start();}
}
