package ru.local.spbumailpilot;
import android.content.*;
import android.content.pm.PackageManager;

final class LauncherIcons {
    static String selected(Context context){return context.getSharedPreferences("launcher",0).getString("icon","spbu");}
    static boolean exists(Context context,String name){try{context.getPackageManager().getActivityInfo(new ComponentName(context,context.getPackageName()+"."+name),PackageManager.MATCH_DISABLED_COMPONENTS);return true;}catch(PackageManager.NameNotFoundException missing){return false;}}
    static synchronized void change(Context context,String mode)throws Exception {
        boolean nativeFirst=exists(context,"ComposePilotLauncher"),nativeSecond=exists(context,"ComposeClassicLauncher");
        if(nativeFirst!=nativeSecond)throw new IllegalStateException("Incomplete native launcher installation");
        LauncherIconPolicy.change(new LauncherIconPolicy.Backend(){
            private ComponentName component(String name){return new ComponentName(context,context.getPackageName()+"."+name);}
            public int state(String name){return context.getPackageManager().getComponentEnabledSetting(component(name));}
            public void state(String name,int value){context.getPackageManager().setComponentEnabledSetting(component(name),value,PackageManager.DONT_KILL_APP);}
            public String saved(){return selected(context);}
            public boolean save(String value){return context.getSharedPreferences("launcher",0).edit().putString("icon",value).commit();}
        },nativeFirst,mode);
    }
}
