package ru.local.spbumailpilot;
public class NativeCopyAgeTest {
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){long at=1780000000000L;
  check(NativeCopyAge.label(0,at).equals("Время копии неизвестно"));
  check(NativeCopyAge.label(at,at-1000).equals("Копия: только что"));
  check(NativeCopyAge.label(at,at+59999).equals("Копия: только что"));
  check(NativeCopyAge.label(at,at+60000).equals("Копия: 1 мин. назад"));
  check(NativeCopyAge.label(at,at+3600000).equals("Копия: 1 ч. назад"));
  check(NativeCopyAge.label(at,at+86400000).equals("Копия: 1 дн. назад"));
  check(NativeCopyAge.label(Long.MAX_VALUE,Long.MIN_VALUE).equals("Копия: только что"));
  System.out.println("PASS copy age: unknown timestamps, minute/hour/day boundaries, clock reversal without negative/overflow ages");
 }
}
