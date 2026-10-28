package ru.local.spbumailpilot;
import java.time.*;
final class NativeScheduleTime {
    static long parse(String day,String time,long now){
        if(day==null||time==null||!day.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")||!time.matches("[0-9]{2}:[0-9]{2}"))throw new IllegalArgumentException("Укажите дату и время.");
        long at=LocalDate.parse(day).atTime(LocalTime.parse(time)).atZone(ZoneId.of("Europe/Moscow")).toInstant().toEpochMilli();
        if(at<=now||at>253402300799000L)throw new IllegalArgumentException("Выберите будущее время по Москве.");return at;
    }
}
