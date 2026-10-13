package ru.local.spbumailpilot;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.json.*;

/** Saved calendar projection. Never invents a server refresh or an unread count. */
final class WidgetAgenda {
    static final ZoneId ZONE=ZoneId.of("Europe/Moscow");
    static final class Entry {
        final String title,label;final long start;
        Entry(String title,String label,long start){this.title=title;this.label=label;this.start=start;}
    }
    static String clean(String value,int limit){return MailNotificationText.clean(value,"",limit);}
    static List<Entry> entries(JSONArray events,JSONArray tasks,long now){
        LocalDate today=Instant.ofEpochMilli(now).atZone(ZONE).toLocalDate();
        List<Entry> out=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(int group=0;group<2;group++){
            JSONArray values=group==0?events:tasks;if(values==null)continue;
            for(int i=0;i<values.length();i++)try{
                JSONObject value=values.getJSONObject(i);if(group==1&&value.optBoolean("done"))continue;
                LocalDate date=LocalDate.parse(value.getString("date"));String time=value.optString("time");
                boolean allDay=value.optBoolean("allDay")||time.isEmpty();
                long start=date.atTime(allDay?LocalTime.MIDNIGHT:LocalTime.parse(time)).atZone(ZONE).toInstant().toEpochMilli();
                long end=start;
                if(group==1){if(allDay)end=date.plusDays(1).atStartOfDay(ZONE).toInstant().toEpochMilli();}
                else if(allDay){LocalDate endDate=value.optString("endDate").isEmpty()?date.plusDays(1):LocalDate.parse(value.getString("endDate"));end=endDate.atStartOfDay(ZONE).toInstant().toEpochMilli();}
                else if(!value.optString("endTime").isEmpty()){LocalDate endDate=value.optString("endDate").isEmpty()?date:LocalDate.parse(value.getString("endDate"));end=endDate.atTime(LocalTime.parse(value.getString("endTime"))).atZone(ZONE).toInstant().toEpochMilli();}
                if(end<now||date.isAfter(today.plusDays(7)))continue;
                String key=group+"/"+value.optString("calendarId")+"/"+value.optString("id")+"/"+date+"/"+time;
                if(!seen.add(key))continue;
                String day=date.isBefore(today)?"Продолжается":date.equals(today)?"Сегодня":date.equals(today.plusDays(1))?"Завтра":date.format(DateTimeFormatter.ofPattern("d MMM",new Locale("ru")));
                String hours=allDay?(group==1?"Без времени":"Весь день"):time+(value.optString("endTime").isEmpty()?"":"–"+value.optString("endTime"));
                out.add(new Entry(MailNotificationText.clean(value.optString("title"),"Без названия",180),day+" · "+hours+(group==1?" · Задача":""),start));
            }catch(Exception malformed){/* One bad cached entry cannot hide the remaining agenda. */}
        }
        out.sort(Comparator.comparingLong(e->e.start));return out.subList(0,Math.min(3,out.size()));
    }
    static String checked(long at,long now){
        if(at<=0||at>now+60000)return "Откройте входящие для проверки";
        ZonedDateTime value=Instant.ofEpochMilli(at).atZone(ZONE),current=Instant.ofEpochMilli(now).atZone(ZONE);
        return "Проверено "+value.format(DateTimeFormatter.ofPattern(value.toLocalDate().equals(current.toLocalDate())?"HH:mm":"d MMM HH:mm",new Locale("ru")))+" · МСК";
    }
}
