package ru.local.spbumailpilot;
import android.content.*;
import android.net.Uri;
import android.os.*;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.json.JSONObject;
/** One owner-bound dial entry point shared by reader, contacts and manual number entry. */
final class NativeDialer {
    private static final ExecutorService WRITES=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"dial-history");t.setDaemon(true);return t;});
    static void dial(Context context,String owner,String value,Consumer<String> completed)throws Exception{
        if(AppLock.locked(context))throw new IllegalStateException("Locked");
        Context app=context.getApplicationContext();SavedAccounts.Login login=new CredentialVault(app).load();
        if(login==null||!NoticeCenter.scope(login).equals(owner))throw new IllegalStateException("Account changed");
        String number=PhoneNumbers.normalize(value);Instant at=Instant.now();
        Intent intent=new Intent(Intent.ACTION_DIAL,Uri.fromParts("tel",number,null));
        if(!(context instanceof android.app.Activity))intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        WRITES.execute(()->{
            String notice="Номер открыт в приложении «Телефон».";
            try{synchronized(ReplyReminderCenter.class){
                SavedAccounts.Login active=new CredentialVault(app).load();if(active==null||!NoticeCenter.scope(active).equals(owner))throw new IllegalStateException("Account changed");
                android.content.SharedPreferences prefs=NoticeCenter.state(app,active);
                JSONObject next=CallHistory.append(new JSONObject(prefs.getString("state","{}")),number,at);
                String json=next.toString();if(json.length()>2000000)throw new IllegalStateException("State limit");
                if(!prefs.edit().putString("state",json).commit())throw new IllegalStateException("History not persisted");
            }}catch(Exception error){notice="Номер открыт в «Телефоне», но история приложения не сохранилась.";}
            String message=notice;new Handler(Looper.getMainLooper()).post(()->completed.accept(message));
        });
    }
}
