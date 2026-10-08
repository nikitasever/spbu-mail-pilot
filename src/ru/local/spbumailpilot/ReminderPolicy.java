package ru.local.spbumailpilot;

import java.time.*;

final class ReminderPolicy {
    static long deadline(String date,String time){
        LocalTime value=time==null||time.isEmpty()?LocalTime.of(9,0):LocalTime.parse(time);
        return LocalDate.parse(date).atTime(value).atZone(ZoneId.of("Europe/Moscow")).toInstant().toEpochMilli();
    }
    static long fireAt(String date,String time){return deadline(date,time)-(time==null||time.isEmpty()?0:3600000L);}
    static boolean ready(long fire,long now){return fire<=now&&fire>=now-86400000L;}
    static boolean newMail(String previousValidity,long previousUid,String validity,long uid){return previousValidity!=null&&previousValidity.equals(validity)&&uid>previousUid;}
}
