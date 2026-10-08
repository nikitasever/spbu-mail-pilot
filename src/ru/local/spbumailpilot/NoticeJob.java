package ru.local.spbumailpilot;

import android.app.job.*;

public final class NoticeJob extends JobService {
    private java.util.concurrent.atomic.AtomicBoolean cancellation;
    private Thread runner;
    @Override public boolean onStartJob(JobParameters parameters){
        java.util.concurrent.atomic.AtomicBoolean stopped=new java.util.concurrent.atomic.AtomicBoolean();cancellation=stopped;
        runner=new Thread(()->{try{NoticeCenter.tasks(this);if(!stopped.get())NoticeCenter.mail(this,stopped::get);}catch(Exception ignored){}finally{if(!stopped.get())jobFinished(parameters,false);}},"mail-notices");runner.start();return true;
    }
    @Override public boolean onStopJob(JobParameters parameters){if(cancellation!=null)cancellation.set(true);if(runner!=null)runner.interrupt();return true;}
}
