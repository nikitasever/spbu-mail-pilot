package ru.local.spbumailpilot;
import org.json.JSONObject;
import java.util.Set;
/** View-only source filters. Never changes saved calendars or events. */
final class NativeCalendarVisibility {
 static String key(JSONObject event){
  String kind=event.optString("kind");
  if("task".equals(kind))return "tasks";
  if("event".equals(kind))return "local";
  return "calendar:"+event.optString("calendarId");
 }
 static boolean visible(JSONObject event,Set<String> hidden){return !hidden.contains(key(event));}
}
