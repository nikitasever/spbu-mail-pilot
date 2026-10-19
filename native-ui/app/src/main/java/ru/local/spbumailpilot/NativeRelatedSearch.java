package ru.local.spbumailpilot;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.json.*;
/** Shared search policy: Moscow date bounds and contact/event matching, no network or writes. */
final class NativeRelatedSearch {
    static final class Range {
        final String start,end;final List<String> months;final boolean tooWide;
        Range(String start,String end,List<String> months,boolean tooWide){this.start=start;this.end=end;this.months=Collections.unmodifiableList(months);this.tooWide=tooWide;}
    }
    static Range range(NativeMailSearch.Criteria c,LocalDate today){
        LocalDate start=!c.start.isEmpty()?LocalDate.parse(c.start):!c.end.isEmpty()?LocalDate.parse(c.end).withDayOfMonth(1):today.withDayOfMonth(1);
        LocalDate end=!c.end.isEmpty()?LocalDate.parse(c.end):start.withDayOfMonth(1).plusMonths(2).minusDays(1);
        long count=ChronoUnit.MONTHS.between(YearMonth.from(start),YearMonth.from(end))+1;
        List<String> months=new ArrayList<>();if(count<=12)for(long i=0;i<count;i++)months.add(YearMonth.from(start).plusMonths(i).toString());
        return new Range(start.toString(),end.toString(),months,count>12);
    }
    private static String text(String s){return s.toLowerCase(Locale.ROOT).replace('ё','е').trim().replaceAll("\\s+"," ");}
    static JSONArray people(JSONObject local,JSONArray remote,String query)throws Exception{
        JSONArray all=NativeContacts.merge(NativeContacts.local(local,""),remote),found=new JSONArray();String needle=text(query);
        if(needle.isEmpty())return found;
        Map<String,String> numbers=new HashMap<>();
        for(String group:new String[]{"contacts","phoneContacts","recipientBook"}){
            JSONArray rows=local.optJSONArray(group);if(rows==null)continue;
            for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row==null||!(row.opt("number") instanceof String))continue;String number=row.getString("number");
                if(number.length()<=80&&!number.matches("(?s).*\\p{Cntrl}.*"))numbers.putIfAbsent(row.optString("email").toLowerCase(Locale.ROOT),number);}
        }
        for(int i=0;i<all.length();i++){JSONObject p=all.getJSONObject(i);String number=numbers.getOrDefault(p.getString("email").toLowerCase(Locale.ROOT),"");
            if(text(p.getString("sender")+" "+p.getString("email")+" "+number).contains(needle))found.put(new JSONObject(p.toString()).put("number",number));}
        return found;
    }
    static JSONArray events(JSONObject local,JSONArray remote,String query,Range range)throws Exception{
        JSONArray all=NativeAgenda.combine(local,remote),found=new JSONArray();String needle=text(query);
        for(int i=0;i<all.length();i++){JSONObject e=all.getJSONObject(i);String date=e.optString("date");
            if(date.compareTo(range.end)>0||date.compareTo(range.start)<0&&!NativeAgenda.onDay(e,range.start))continue;
            StringBuilder words=new StringBuilder();for(String field:new String[]{"title","body","description","location","notes"})words.append(' ').append(e.optString(field));
            if(text(words.toString()).contains(needle))found.put(new JSONObject(e.toString()));}
        return found;
    }
}
