package ru.local.spbumailpilot;
import android.app.*;
import android.os.Bundle;

public final class PilotApplication extends Application implements Application.ActivityLifecycleCallbacks {
    AppLock locks;
    @Override public void onCreate(){super.onCreate();locks=new AppLock(this);registerActivityLifecycleCallbacks(this);}
    public void onActivityCreated(Activity a,Bundle b){locks.created(a);}
    public void onActivityStarted(Activity a){locks.started(a);}
    public void onActivityResumed(Activity a){locks.resumed(a);}
    public void onActivityPaused(Activity a){}
    public void onActivityStopped(Activity a){locks.stopped(a);}
    public void onActivitySaveInstanceState(Activity a,Bundle b){}
    public void onActivityDestroyed(Activity a){locks.destroyed(a);}
}
