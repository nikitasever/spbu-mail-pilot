package ru.local.spbumailpilot;

import java.time.Instant;

public class ReminderPolicyTest {
    static void check(boolean condition){if(!condition)throw new AssertionError();}
    public static void main(String[] args){
        check(ReminderPolicy.deadline("2026-10-08","")==Instant.parse("2026-10-08T06:00:00Z").toEpochMilli());
        long deadline=ReminderPolicy.deadline("2026-10-08","12:30");check(deadline==Instant.parse("2026-10-08T09:30:00Z").toEpochMilli());
        check(ReminderPolicy.fireAt("2026-10-08","12:30")==deadline-3600000);
        check(ReminderPolicy.fireAt("2026-10-08","00:15")==Instant.parse("2026-10-07T20:15:00Z").toEpochMilli());
        check(ReminderPolicy.ready(deadline,deadline));check(!ReminderPolicy.ready(deadline,deadline-1));check(!ReminderPolicy.ready(deadline,deadline+86400001));
        check(!ReminderPolicy.newMail(null,0,"7",1234));check(!ReminderPolicy.newMail("7",1234,"8",1400));
        check(!ReminderPolicy.newMail("7",1234,"7",1234));check(ReminderPolicy.newMail("7",1234,"7",1235));
        try{ReminderPolicy.deadline("not-a-date","");throw new AssertionError();}catch(java.time.DateTimeException expected){}
        System.out.println("PASS: Moscow deadlines, one-hour lead including previous day, stale reminder suppression, first-mail baseline, UIDVALIDITY reset and new UID detection.");
    }
}
