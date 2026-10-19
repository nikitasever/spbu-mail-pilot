package ru.local.spbumailpilot;
import android.content.*;

/** The optional native build declares this Activity; the original build keeps MainActivity. */
final class UiEntry {
    static Intent open(Context context){
        ComponentName nativeEntry=new ComponentName(context.getPackageName(),"ru.local.spbumailpilot.ComposeInboxActivity");
        try{android.content.pm.ActivityInfo info=context.getPackageManager().getActivityInfo(nativeEntry,0);
            if(info.enabled)return new Intent().setComponent(nativeEntry);
        }catch(android.content.pm.PackageManager.NameNotFoundException missing){}
        return new Intent(context,MainActivity.class);
    }
}
