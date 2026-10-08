package ru.local.spbumailpilot;
import java.util.*;
import org.json.*;

public class CalendarRecurrenceTest {
    static void check(boolean value){if(!value)throw new AssertionError();}
    static String event(String fields){return "BEGIN:VEVENT\n"+fields+"\nEND:VEVENT\n";}
    static JSONArray parse(String ics,String month)throws Exception{return CalDav.parse(CalDavTest.wrap("BEGIN:VCALENDAR\n"+ics+"END:VCALENDAR"),month);}
    public static void main(String[] args)throws Exception{
        String daily=event("UID:daily\nDTSTART:20260930T090000Z\nDURATION:PT1H\nRRULE:FREQ=DAILY;COUNT=5\nEXDATE:20261002T090000Z\nRDATE:20261010T090000Z\nEXDATE:20261004T090000Z");
        JSONArray items=parse(daily,"2026-10");check(items.length()==3);check(items.getJSONObject(0).getString("date").equals("2026-10-01"));check(items.getJSONObject(0).getString("time").equals("12:00"));check(items.getJSONObject(0).getString("endTime").equals("13:00"));check(items.getJSONObject(2).getString("date").equals("2026-10-10"));
        String weekly=event("UID:week\nDTSTART;VALUE=DATE:20200101\nRRULE:FREQ=WEEKLY;BYDAY=MO,WE,FR");
        items=parse(weekly,"2026-10");check(items.length()==13);for(int i=0;i<items.length();i++)check(items.getJSONObject(i).getBoolean("allDay"));
        String series=event("UID:move\nDTSTART:20261001T090000Z\nRRULE:FREQ=DAILY;COUNT=3");
        String moved=event("UID:move\nRECURRENCE-ID:20261002T090000Z\nDTSTART:20261006T110000Z\nSUMMARY:Moved");
        String cancelled=event("UID:move\nRECURRENCE-ID:20261003T090000Z\nSTATUS:CANCELLED");
        items=parse(series+moved+cancelled,"2026-10");check(items.length()==2);check(items.getJSONObject(1).getString("date").equals("2026-10-06"));check(items.getJSONObject(1).getString("title").equals("Moved"));
        items=CalDav.parse(CalDavTest.wrap("BEGIN:VCALENDAR\n"+series+"END:VCALENDAR")+"", "2026-10");check(items.length()==3);
        items=parse(event("UID:dst\nDTSTART;TZID=Europe/Berlin:20261024T090000\nRRULE:FREQ=DAILY;COUNT=3"),"2026-10");check(items.length()==3);check(items.getJSONObject(0).getString("time").equals("10:00"));check(items.getJSONObject(1).getString("time").equals("11:00"));
        items=parse(event("UID:monthly\nDTSTART:20260101T090000Z\nRRULE:FREQ=MONTHLY;BYDAY=1MO;UNTIL=20261231T235959Z"),"2026-10");check(items.length()==1&&items.getJSONObject(0).getString("date").equals("2026-10-05"));
        items=parse(event("UID:outside\nDTSTART:20260901T090000Z\nRRULE:FREQ=DAILY;COUNT=2"),"2026-10");check(items.length()==0);
        items=parse(event("UID:overlap\nDTSTART:20260930T230000Z\nDURATION:PT3H\nRRULE:FREQ=DAILY;COUNT=2"),"2026-10");check(items.length()==2);
        CalDavTest.rejected(()->parse(event("UID:bad\nDTSTART:20261001T090000Z\nRRULE:NOT_A_RULE"),"2026-10"));
        CalDavTest.rejected(()->parse(event("UID:range\nRECURRENCE-ID;RANGE=THISANDFUTURE:20261001T090000Z\nDTSTART:20261002T090000Z"),"2026-10"));
        System.out.println("PASS: local daily/weekly/monthly recurrence expansion, old seeds, COUNT/UNTIL, multiple RDATE/EXDATE, moved/cancelled exceptions, all-day events, duration, month boundaries and DST. No real events read.");
    }
}
