package ru.local.spbumailpilot;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.json.*;

/** Active-account widget: cached agenda, last server-verified INBOX count. No network. */
public final class MailWidget extends AppWidgetProvider {
    private static final ExecutorService WORK=Executors.newSingleThreadExecutor();
    private static final AtomicLong REVISION=new AtomicLong();
    static void request(Context context){
        Context app=context.getApplicationContext();long revision=REVISION.incrementAndGet();
        try{if(installed(app))WORK.execute(()->{if(revision==REVISION.get())safeUpdate(app,revision);});}catch(RuntimeException unavailable){/* Widget failure must never fail a committed application save. */}
    }
    @Override public void onReceive(Context context,Intent intent){
        super.onReceive(context,intent);
        if(Intent.ACTION_DATE_CHANGED.equals(intent.getAction())||Intent.ACTION_TIME_CHANGED.equals(intent.getAction())||Intent.ACTION_TIMEZONE_CHANGED.equals(intent.getAction()))refreshBroadcast(context);
    }
    @Override public void onUpdate(Context context,AppWidgetManager manager,int[] ids){
        refreshBroadcast(context);
    }
    private void refreshBroadcast(Context context){
        PendingResult pending=goAsync();Context app=context.getApplicationContext();long revision=REVISION.incrementAndGet();
        WORK.execute(()->{try{if(revision==REVISION.get())safeUpdate(app,revision);}finally{pending.finish();}});
    }
    @Override public void onAppWidgetOptionsChanged(Context c,AppWidgetManager m,int id,Bundle options){refreshBroadcast(c);}
    static boolean installed(Context c){return AppWidgetManager.getInstance(c).getAppWidgetIds(new ComponentName(c,MailWidget.class)).length>0;}
    static void forget(Context c,SavedAccounts.Login login)throws Exception{c.getSharedPreferences("widget-mail-"+NoticeCenter.scope(login),Context.MODE_PRIVATE).edit().clear().commit();}
    static void record(Context c,SavedAccounts.Login login,JSONObject result){
        if(!result.has("unread")||result.optInt("unread",-1)<0)return;
        try{c.getSharedPreferences("widget-mail-"+NoticeCenter.scope(login),Context.MODE_PRIVATE).edit().putInt("unread",result.getInt("unread")).putLong("at",System.currentTimeMillis()).apply();request(c);}catch(Exception ignored){}
    }
    private static int res(Context c,String type,String name){return c.getResources().getIdentifier(name,type,c.getPackageName());}
    private static void text(Context c,RemoteViews v,String id,String value){v.setTextViewText(res(c,"id",id),value);}
    private static PendingIntent open(Context c,int widget,String route,String account){
        Intent intent=new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP).setData(android.net.Uri.parse("mailpilot://widget/"+widget+"/"+route)).putExtra("openWidgetRoute",route).putExtra("openWidgetAccount",account);
        return PendingIntent.getActivity(c,widget*2+(route.equals("calendar")?1:0),intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private static void safeUpdate(Context c,long revision){try{update(c,revision);}catch(Exception unavailable){/* Launcher/binder failure must not crash mail or an asynchronous receiver. */}}
    private static void update(Context c,long revision){
        AppWidgetManager manager=AppWidgetManager.getInstance(c);int[] ids=manager.getAppWidgetIds(new ComponentName(c,MailWidget.class));if(ids.length==0)return;
        SavedAccounts.Login login=null;String owner="",user="Войдите в приложение",count="Почта",checked="",empty="Подключите почтовый ящик";
        boolean dark=(c.getResources().getConfiguration().uiMode&android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES;
        List<WidgetAgenda.Entry> entries=Collections.emptyList();long now=System.currentTimeMillis();
        try{
            login=new CredentialVault(c).load();if(login!=null){
                owner=NoticeCenter.scope(login);user=WidgetAgenda.clean(login.user,180);
                JSONObject data=new JSONObject(NoticeCenter.state(c,login).getString("state","{}")),settings=data.optJSONObject("settings");
                String theme=settings==null?"system":settings.optString("theme","system");if(theme.equals("dark"))dark=true;else if(theme.equals("light"))dark=false;
                SharedPreferences mail=c.getSharedPreferences("widget-mail-"+owner,Context.MODE_PRIVATE);
                count=mail.contains("unread")?"Непрочитанные: "+mail.getInt("unread",0):"Непрочитанные: —";checked=WidgetAgenda.checked(mail.getLong("at",0),now);
                JSONArray events=new JSONArray();JSONArray local=data.optJSONArray("events");if(local!=null)for(int i=0;i<local.length();i++)events.put(local.opt(i));
                // Only committed cache: no CalDAV request and no newly copied credentials.
                try{
                    CalendarBook book=new CalendarBook(c,login);LocalDate today=Instant.ofEpochMilli(now).atZone(WidgetAgenda.ZONE).toLocalDate();Set<String> months=new LinkedHashSet<>();months.add(today.toString().substring(0,7));months.add(today.plusDays(7).toString().substring(0,7));
                    for(String month:months){JSONArray cached=book.list(month,false).getJSONArray("events");for(int i=0;i<cached.length();i++)events.put(cached.opt(i));}
                }catch(Exception unavailable){/* Local tasks/events remain available when calendar keys are locked. */}
                entries=WidgetAgenda.entries(events,data.optJSONArray("tasks"),now);empty="Нет сохранённых событий на 7 дней";
            }
        }catch(Exception unavailable){user="Откройте приложение";empty="Данные временно недоступны";owner="";count="Почта";checked="";entries=Collections.emptyList();}
        if(revision!=REVISION.get())return;
        for(int id:ids)try{
            RemoteViews views=new RemoteViews(c.getPackageName(),res(c,"layout","mail_widget"));
            views.setInt(res(c,"id","widget_root"),"setBackgroundResource",res(c,"drawable",dark?"widget_dark":"widget_light"));
            for(String name:new String[]{"widget_title","widget_account","widget_unread","widget_checked","widget_agenda","widget_empty","widget_footer","widget_event1","widget_event2","widget_event3"})views.setTextColor(res(c,"id",name),name.equals("widget_title")||name.equals("widget_unread")?(dark?0xffceacf3:0xff7946ae):(dark?0xfff3eff8:0xff292533));
            text(c,views,"widget_account",user);text(c,views,"widget_unread",count);text(c,views,"widget_checked",checked);text(c,views,"widget_empty",empty);
            views.setViewVisibility(res(c,"id","widget_empty"),entries.isEmpty()?View.VISIBLE:View.GONE);
            int height=manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,220);float scale=c.getResources().getConfiguration().fontScale;
            int capacity=Math.max(0,Math.min(3,(int)((height-145*scale)/(48*scale))));
            if(capacity==0){text(c,views,"widget_empty","Открыть календарь");views.setViewVisibility(res(c,"id","widget_empty"),View.VISIBLE);views.setViewVisibility(res(c,"id","widget_checked"),View.GONE);views.setViewVisibility(res(c,"id","widget_footer"),View.GONE);}
            for(int n=0;n<3;n++){
                String name="widget_event"+(n+1);boolean visible=n<entries.size()&&n<capacity;views.setViewVisibility(res(c,"id",name),visible?View.VISIBLE:View.GONE);
                if(visible){WidgetAgenda.Entry e=entries.get(n);text(c,views,name,e.label+"\n"+e.title);views.setContentDescription(res(c,"id",name),e.label+", "+e.title);}
                views.setOnClickPendingIntent(res(c,"id",name),open(c,id,"calendar",owner));
            }
            views.setOnClickPendingIntent(res(c,"id","widget_mail"),open(c,id,"mail",owner));views.setOnClickPendingIntent(res(c,"id","widget_calendar"),open(c,id,"calendar",owner));
            if(revision==REVISION.get())manager.updateAppWidget(id,views);
        }catch(Exception unavailable){/* A removed/reconfigured host must not prevent remaining widgets. */}
    }
}
