package ru.local.spbumailpilot;

import java.time.*;
import java.util.*;
import org.json.*;

/** Calendar day arithmetic is independent of the device timezone. Display zone is Moscow. */
final class NativeAgenda {
    static LocalDate date(String value) {
        LocalDate date=LocalDate.parse(value);
        if(date.getYear()<1970||date.getYear()>2100)throw new IllegalArgumentException("Calendar range");
        return date;
    }
    static LocalDate first(String day,String mode) {
        LocalDate selected=date(day);
        switch(mode) {
            case "day":return selected;
            case "week":return selected.minusDays(selected.getDayOfWeek().getValue()-1);
            case "month":case "agenda":return selected.withDayOfMonth(1);
            default:throw new IllegalArgumentException("Calendar view");
        }
    }
    static LocalDate end(String day,String mode) {
        LocalDate first=first(day,mode);
        return mode.equals("day")?first.plusDays(1):mode.equals("week")?first.plusDays(7):first.plusMonths(1);
    }
    static List<String> months(String day,String mode) {
        LinkedHashSet<String> values=new LinkedHashSet<>();LocalDate next=first(day,mode),end=end(day,mode);
        while(next.isBefore(end)){values.add(YearMonth.from(next).toString());next=next.withDayOfMonth(1).plusMonths(1);}
        return new ArrayList<>(values);
    }
    static boolean onDay(JSONObject event,String day) {
        try {
            LocalDate selected=date(day),start=date(event.getString("date"));
            if(start.isAfter(selected)||event.optString("kind").equals("task")&&event.optBoolean("done"))return false;
            if(!event.optString("kind").equals("remote")||event.optString("endDate").isEmpty())return start.equals(selected);
            LocalDate end=date(event.getString("endDate"));
            return start.equals(selected)||selected.isBefore(end)||selected.equals(end)&&!event.optBoolean("allDay")&&event.optString("endTime").compareTo("00:00")>0;
        }catch(Exception invalid){return false;}
    }
    static String timeRange(JSONObject event) {
        String time=event.optString("time"),endTime=event.optString("endTime");
        if(event.optString("kind").equals("task"))return time.isEmpty()?"Весь день":time;
        try {
            LocalDate start=date(event.getString("date"));
            LocalDate end=event.optString("endDate").isEmpty()?start:date(event.getString("endDate"));
            java.time.format.DateTimeFormatter label=java.time.format.DateTimeFormatter.ofPattern("d MMM",Locale.forLanguageTag("ru"));
            if(time.isEmpty()||event.optBoolean("allDay")) {
                LocalDate last=end.isAfter(start)?end.minusDays(1):start;
                return "Весь день"+(last.isAfter(start)?" · "+start.format(label)+" – "+last.format(label):"");
            }
            if(end.isAfter(start))return start.format(label)+" "+time+" – "+end.format(label)+(endTime.isEmpty()?"":" "+endTime);
            return time+(!endTime.isEmpty()&&endTime.compareTo(time)>0?" – "+endTime:"");
        }catch(Exception invalid){return time.isEmpty()?"Весь день":time;}
    }
    static JSONArray combine(JSONObject local,JSONArray remote) throws Exception {
        LinkedHashMap<String,JSONObject> entries=new LinkedHashMap<>();
        for(String kind:new String[]{"task","event","remote"}) {
            JSONArray values=kind.equals("remote")?remote:local.optJSONArray(kind.equals("task")?"tasks":"events");
            if(values==null)continue;
            for(int i=0;i<values.length();i++) {
                JSONObject raw=values.optJSONObject(i);if(raw==null)continue;
                JSONObject entry=new JSONObject(raw.toString()).put("kind",kind);
                if(kind.equals("task")&&entry.optBoolean("done"))continue;
                try{date(entry.getString("date"));}catch(Exception invalid){continue;}
                String key=new JSONArray().put(kind).put(entry.optString("calendarId")).put(entry.optString("id")).put(entry.optString("date")).put(entry.optString("time")).toString();
                entries.putIfAbsent(key,entry);
                if(entries.size()>20000)throw new IllegalArgumentException("Too many calendar entries");
            }
        }
        List<JSONObject> ordered=new ArrayList<>(entries.values());
        ordered.sort(Comparator.comparing(e->e.optString("date")+e.optString("time")));
        return new JSONArray(ordered);
    }
}
