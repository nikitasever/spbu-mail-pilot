package ru.local.spbumailpilot;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class BackgroundMailLoopTest {
    public static void main(String[] args)throws Exception{
        AtomicInteger checks=new AtomicInteger(),active=new AtomicInteger(),overlaps=new AtomicInteger();
        CountDownLatch retried=new CountDownLatch(1),cancelled=new CountDownLatch(1),entered=new CountDownLatch(1);
        BackgroundMailLoop loop=new BackgroundMailLoop(stopped->{
            if(active.incrementAndGet()!=1)overlaps.incrementAndGet();
            try{int n=checks.incrementAndGet();if(n==1)throw new Exception("temporary network failure");retried.countDown();Thread.sleep(25);}finally{active.decrementAndGet();}
        },10);
        if(!retried.await(2,TimeUnit.SECONDS))throw new AssertionError("network failure stops background checks");
        loop.close();Thread.sleep(60);int count=checks.get();Thread.sleep(60);
        if(checks.get()!=count||overlaps.get()!=0)throw new AssertionError("overlap or check after stop");
        BackgroundMailLoop pending=new BackgroundMailLoop(stopped->{entered.countDown();try{Thread.sleep(5000);}catch(InterruptedException interrupted){if(stopped.getAsBoolean())cancelled.countDown();}},10);
        if(!entered.await(2,TimeUnit.SECONDS))throw new AssertionError("initial check not immediate");pending.close();
        if(!cancelled.await(2,TimeUnit.SECONDS))throw new AssertionError("cancel did not mark in-flight operation stopped");
        System.out.println("PASS: independent background checks retry network failures, never overlap, start immediately and cancel in-flight work.");
    }
}
