package ru.local.spbumailpilot;

public final class NoticeWorkTest {
    static void check(boolean condition){if(!condition)throw new AssertionError();}
    public static void main(String[] args){
        int[] calls={0};
        check(NoticeWork.perform(()->{throw new java.io.IOException("fixture task storage failure");},()->calls[0]++,()->false));check(calls[0]==1);
        check(NoticeWork.perform(()->{},()->{throw new java.io.IOException("fixture offline");},()->false));
        check(!NoticeWork.perform(()->{},()->{throw new javax.mail.AuthenticationFailedException("fixture rejected");},()->false));
        java.util.concurrent.atomic.AtomicBoolean stopped=new java.util.concurrent.atomic.AtomicBoolean();
        check(!NoticeWork.perform(()->stopped.set(true),()->calls[0]++,stopped::get));check(calls[0]==1);
        check(!NoticeWork.perform(()->calls[0]++,()->calls[0]++,()->true));check(calls[0]==1);
        check(!NoticeWork.perform(()->{},()->calls[0]++,()->false));check(calls[0]==2);
        System.out.println("PASS: background mail still runs after task failure, temporary failures retry, rejected login does not loop, stopped work does not read mail or request retry.");
    }
}
