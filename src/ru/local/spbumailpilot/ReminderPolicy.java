package ru.local.spbumailpilot;

import java.time.*;

final class ReminderPolicy {
    static long deadline(String date,String time){
        LocalTime value=time==null||time.isEmpty()?LocalTime.of(9,0):LocalTime.parse(time);
        return LocalDate.parse(date).atTime(value).atZone(ZoneId.of("Europe/Moscow")).toInstant().toEpochMilli();
    }
    static long fireAt(String date,String time){return deadline(date,time)-(time==null||time.isEmpty()?0:3600000L);}
    static String option(String value){return "15".equals(value)||"30".equals(value)||"60".equals(value)||"1440".equals(value)||"none".equals(value)?value:"auto";}
    static long fireAt(String date,String time,String reminder){String value=option(reminder);if(value.equals("none"))return -1;if(value.equals("auto"))return fireAt(date,time);return deadline(date,time)-Long.parseLong(value)*60000L;}
    static String taskKey(String id,String date,String time,String reminder){String base=id+"/"+date+"/"+(time==null?"":time),value=option(reminder);return value.equals("auto")||(value.equals("60")&&time!=null&&!time.isEmpty())?base:base+"/reminder-"+value;}
    static boolean ready(long fire,long now){return fire<=now&&fire>=now-86400000L;}
    static boolean newMail(String previousValidity,long previousUid,String validity,long uid){return previousValidity!=null&&previousValidity.equals(validity)&&uid>previousUid;}
}

