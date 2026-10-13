package ru.local.spbumailpilot;
import android.app.job.*;
import java.util.concurrent.atomic.AtomicBoolean;
public final class ScheduledSendJob extends JobService {
    private final java.util.Map<Integer,AtomicBoolean> runs=new java.util.concurrent.ConcurrentHashMap<>();
    public boolean onStartJob(JobParameters p){AtomicBoolean stopped=new AtomicBoolean();runs.put(p.getJobId(),stopped);new Thread(()->{boolean retry=ScheduledMail.run(this,stopped::get);runs.remove(p.getJobId(),stopped);if(!stopped.get())jobFinished(p,retry);ScheduledMail.reconcile(this);},"scheduled-mail").start();return true;}
    public boolean onStopJob(JobParameters p){AtomicBoolean stopped=runs.remove(p.getJobId());if(stopped!=null)stopped.set(true);return true;}
}
