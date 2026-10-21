package ru.local.spbumailpilot;
import org.json.JSONObject;
import java.util.*;
public final class NativeCalendarVisibilityTest {
 public static void main(String[] args)throws Exception {
  JSONObject a=new JSONObject().put("kind","remote").put("calendarId","a");
  JSONObject b=new JSONObject().put("kind","remote").put("calendarId","b");
  JSONObject local=new JSONObject().put("kind","event"),task=new JSONObject().put("kind","task");
  String before=a.toString();Set<String> hidden=new HashSet<>();
  for(JSONObject e:new JSONObject[]{a,b,local,task})if(!NativeCalendarVisibility.visible(e,hidden))throw new AssertionError();
  hidden.add("calendar:a");
  if(NativeCalendarVisibility.visible(a,hidden)||!NativeCalendarVisibility.visible(b,hidden)||!NativeCalendarVisibility.visible(local,hidden)||!NativeCalendarVisibility.visible(task,hidden))throw new AssertionError("Source filtering");
  hidden.add("local");hidden.add("tasks");
  if(NativeCalendarVisibility.visible(local,hidden)||NativeCalendarVisibility.visible(task,hidden)||!before.equals(a.toString()))throw new AssertionError("Local filtering mutated data");
  hidden.clear();if(!NativeCalendarVisibility.visible(a,hidden))throw new AssertionError("Restore all");
  System.out.println("NativeCalendarVisibilityTest passed: independent sources, local/task filters, no event mutation.");
 }
}
