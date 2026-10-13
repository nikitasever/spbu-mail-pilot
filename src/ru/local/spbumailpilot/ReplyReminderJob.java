package ru.local.spbumailpilot;
import android.app.job.*;
public final class ReplyReminderJob extends JobService {
    private final java.util.concurrent.ConcurrentHashMap<Integer,java.util.concurrent.atomic.AtomicBoolean> runs=new java.util.concurrent.ConcurrentHashMap<>();
    public boolean onStartJob(JobParameters p){java.util.concurrent.atomic.AtomicBoolean stop=new java.util.concurrent.atomic.AtomicBoolean();runs.put(p.getJobId(),stop);new Thread(()->{boolean retry=true;try{retry=ReplyReminderCenter.check(this,stop::get);}catch(Exception unavailable){}runs.remove(p.getJobId(),stop);if(!stop.get()){jobFinished(p,retry);if(!retry)try{ReplyReminderCenter.schedule(this);}catch(Exception unavailable){}}},"reply-reminders").start();return true;}
    public boolean onStopJob(JobParameters p){java.util.concurrent.atomic.AtomicBoolean stop=runs.remove(p.getJobId());if(stop!=null)stop.set(true);return true;}
}
