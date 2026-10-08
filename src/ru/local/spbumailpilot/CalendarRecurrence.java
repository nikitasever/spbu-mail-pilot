package ru.local.spbumailpilot;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.json.JSONObject;
import org.dmfs.rfc5545.DateTime;
import org.dmfs.rfc5545.recur.*;

/** Bounded local recurrence expansion when a CalDAV server returns masters. */
final class CalendarRecurrence {
    static List<Map<String,CalDav.Property>> read(String ics)throws Exception{
        List<Map<String,CalDav.Property>> result=new ArrayList<>();Map<String,CalDav.Property> event=null;int depth=0;
        for(String line:ics.replace("\r\n","\n").replace("\r","\n").replaceAll("\n[ \\t]","").split("\n")){
            if(line.equalsIgnoreCase("BEGIN:VEVENT")){event=new LinkedHashMap<>();depth=1;continue;}if(event==null)continue;
            if(line.regionMatches(true,0,"BEGIN:",0,6)){depth++;continue;}
            if(line.equalsIgnoreCase("END:VEVENT")){if(depth!=1)throw new CalDav.Failure("Некорректное событие календаря.");result.add(event);event=null;continue;}
            if(line.regionMatches(true,0,"END:",0,4)){depth--;continue;}if(depth!=1)continue;
            int colon=CalDav.delimiter(line);if(colon<0)continue;String head=line.substring(0,colon),name=head.split(";",2)[0].toUpperCase(Locale.ROOT),key=name;
            for(int i=1;event.containsKey(key);i++)key=name+"#"+i;
            event.put(key,new CalDav.Property(head,line.substring(colon+1)));
        }
        if(event!=null)throw new CalDav.Failure("Оборванное событие календаря.");return result;
    }
    static List<JSONObject> expand(List<String> calendars,String month)throws Exception{
        List<Map<String,CalDav.Property>> all=new ArrayList<>();for(String ics:calendars)all.addAll(read(ics));
        Map<String,Map<Instant,Map<String,CalDav.Property>>> exceptions=new HashMap<>();
        for(Map<String,CalDav.Property> event:all)if(event.containsKey("RECURRENCE-ID")){
            if(event.get("RECURRENCE-ID").params.toUpperCase(Locale.ROOT).contains("RANGE="))throw new CalDav.Failure("Изменения всей серии с RANGE пока не поддерживаются.");
            exceptions.computeIfAbsent(CalDav.value(event,"UID",""),k->new HashMap<>()).put(CalDav.instant(event.get("RECURRENCE-ID")).toInstant(),event);
        }
        Instant lower=month==null?null:LocalDate.parse(month+"-01").atStartOfDay(CalDav.DISPLAY_ZONE).toInstant(),upper=month==null?null:LocalDate.parse(month+"-01").plusMonths(1).atStartOfDay(CalDav.DISPLAY_ZONE).toInstant();
        LinkedHashMap<String,JSONObject> result=new LinkedHashMap<>();
        for(Map<String,CalDav.Property> event:all){
            if(cancelled(event)||event.containsKey("RECURRENCE-ID"))continue;
            CalDav.Property start=event.get("DTSTART");ZonedDateTime seed=CalDav.instant(start);String uid=CalDav.value(event,"UID","");
            Map<Instant,Map<String,CalDav.Property>> overrides=exceptions.getOrDefault(uid,Collections.emptyMap());
            boolean recurring=event.keySet().stream().anyMatch(k->k.startsWith("RRULE")||k.startsWith("RDATE")||k.startsWith("EXDATE")||k.startsWith("EXRULE"));
            if(!recurring){if(!overrides.containsKey(seed.toInstant()))add(result,CalDav.event(event),lower,upper);continue;}
            if(month==null)throw new CalDav.Failure("Для раскрытия повторов нужен месяц.");
            Duration duration=duration(event);if(duration.isNegative()||duration.compareTo(Duration.ofDays(366))>0)throw new CalDav.Failure("Некорректная длительность повторяющегося события.");
            Set<Instant> starts=new TreeSet<>(),excluded=new HashSet<>();starts.add(seed.toInstant());Instant from=lower.minus(duration);
            for(Map.Entry<String,CalDav.Property> entry:event.entrySet()){
                String name=entry.getKey().split("#",2)[0];CalDav.Property property=entry.getValue();
                if(name.equals("RDATE")||name.equals("EXDATE")){
                    if(property.params.toUpperCase(Locale.ROOT).contains("VALUE=PERIOD"))throw new CalDav.Failure("Повторы RDATE с длительностью PERIOD пока не поддерживаются.");
                    for(String value:property.value.split(","))(name.equals("RDATE")?starts:excluded).add(CalDav.instant(new CalDav.Property(property.params,value)).toInstant());
                }
                if(name.equals("RRULE")||name.equals("EXRULE")){
                    try{
                        ZoneId zone=zone(start);DateTime base=DateTime.parse(TimeZone.getTimeZone(zone),start.value);
                        RecurrenceRuleIterator iterator=new RecurrenceRule(property.value).iterator(base);
                        String bound=from.atZone(zone).format(DateTimeFormatter.ofPattern(base.isAllDay()?"uuuuMMdd":"uuuuMMdd'T'HHmmss"));
                        iterator.fastForward(DateTime.parse(TimeZone.getTimeZone(zone),bound));int count=0;
                        while(iterator.hasNext()){
                            DateTime next=iterator.nextDateTime();Instant occurrence=CalDav.instant(new CalDav.Property(start.params,next.toString())).toInstant();
                            if(!occurrence.isBefore(upper))break;
                            if(++count>10000)throw new CalDav.Failure("В месяце слишком много повторов.");
                            (name.equals("RRULE")?starts:excluded).add(occurrence);
                        }
                    }catch(CalDav.Failure error){throw error;}catch(Exception error){throw new CalDav.Failure("Не удалось разобрать правило повторения календаря.");}
                }
            }
            for(Instant occurrence:starts){
                if(excluded.contains(occurrence)||overrides.containsKey(occurrence))continue;
                Map<String,CalDav.Property> copy=new HashMap<>(event);
                copy.put("DTSTART",property(start,occurrence));copy.put("DTEND",property(start,occurrence.plus(duration)));copy.remove("DURATION");
                add(result,CalDav.event(copy),lower,upper);
            }
        }
        for(Map<Instant,Map<String,CalDav.Property>> overrides:exceptions.values())for(Map<String,CalDav.Property> event:overrides.values())if(!cancelled(event))add(result,CalDav.event(event),lower,upper);
        return new ArrayList<>(result.values());
    }
    static boolean cancelled(Map<String,CalDav.Property> event){return "CANCELLED".equalsIgnoreCase(CalDav.value(event,"STATUS",""));}
    static ZoneId zone(CalDav.Property property){
        if(property.value.endsWith("Z"))return ZoneOffset.UTC;
        java.util.regex.Matcher match=java.util.regex.Pattern.compile("(?i)(?:^|;)TZID=(\"[^\"]*\"|[^;]*)").matcher(property.params);
        return match.find()?ZoneId.of(match.group(1).replace("\"","")):CalDav.DISPLAY_ZONE;
    }
    static CalDav.Property property(CalDav.Property source,Instant instant){
        boolean allDay=source.value.matches("[0-9]{8}");return new CalDav.Property(allDay?"DTSTART;VALUE=DATE":"DTSTART",instant.atZone(allDay?CalDav.DISPLAY_ZONE:ZoneOffset.UTC).format(DateTimeFormatter.ofPattern(allDay?"uuuuMMdd":"uuuuMMdd'T'HHmmss'Z'")));
    }
    static Duration duration(Map<String,CalDav.Property> event)throws Exception{
        Instant start=CalDav.instant(event.get("DTSTART")).toInstant();
        if(event.containsKey("DTEND"))return Duration.between(start,CalDav.instant(event.get("DTEND")).toInstant());
        if(event.containsKey("DURATION")){String value=event.get("DURATION").value;if(value.matches("P[0-9]+W"))return Duration.ofDays(Long.parseLong(value.substring(1,value.length()-1))*7);return Duration.parse(value);}
        return event.get("DTSTART").value.matches("[0-9]{8}")?Duration.ofDays(1):Duration.ZERO;
    }
    static void add(Map<String,JSONObject> result,JSONObject event,Instant lower,Instant upper)throws Exception{
        if(lower!=null){Instant start=LocalDateTime.parse(event.getString("date")+"T"+(event.getString("time").isEmpty()?"00:00":event.getString("time"))).atZone(CalDav.DISPLAY_ZONE).toInstant();Instant end=LocalDateTime.parse(event.getString("endDate")+"T"+(event.getString("endTime").isEmpty()?"00:00":event.getString("endTime"))).atZone(CalDav.DISPLAY_ZONE).toInstant();if(!start.isBefore(upper)||(start.isBefore(lower)&&!end.isAfter(lower)))return;}
        result.put(event.getString("id"),event);if(result.size()>5000)throw new CalDav.Failure("В месяце слишком много событий.");
    }
}
