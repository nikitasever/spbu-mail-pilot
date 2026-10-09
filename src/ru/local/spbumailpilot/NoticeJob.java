package ru.local.spbumailpilot;

import android.app.job.*;

public final class NoticeJob extends JobService {
    private final java.util.concurrent.ConcurrentHashMap<Integer,java.util.concurrent.atomic.AtomicBoolean> runs=new java.util.concurrent.ConcurrentHashMap<>();
    @Override public boolean onStartJob(JobParameters parameters){
        java.util.concurrent.atomic.AtomicBoolean stopped=new java.util.concurrent.atomic.AtomicBoolean();runs.put(parameters.getJobId(),stopped);
        new Thread(()->{boolean retry=NoticeWork.perform(()->NoticeCenter.tasks(this),()->NoticeCenter.mail(this,stopped::get),stopped::get);runs.remove(parameters.getJobId(),stopped);if(!stopped.get())jobFinished(parameters,retry);},"mail-notices").start();return true;
    }
    @Override public boolean onStopJob(JobParameters parameters){java.util.concurrent.atomic.AtomicBoolean stopped=runs.remove(parameters.getJobId());if(stopped!=null)stopped.set(true);return true;}
}
