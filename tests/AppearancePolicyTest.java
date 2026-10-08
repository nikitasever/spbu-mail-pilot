package ru.local.spbumailpilot;
public final class AppearancePolicyTest {
 static void check(boolean value){if(!value)throw new AssertionError();}
 public static void main(String[] args){
  check(AppearancePolicy.dark("dark",false));check(AppearancePolicy.dark("dark",true));check(!AppearancePolicy.dark("light",true));check(!AppearancePolicy.dark("light",false));check(AppearancePolicy.dark("system",true));check(!AppearancePolicy.dark("system",false));check("system".equals(AppearancePolicy.mode("invalid")));check("system".equals(AppearancePolicy.mode(null)));check("Mail.ru".equals(AppearancePolicy.service("IMAP.MAIL.RU")));check("СПбГУ".equals(AppearancePolicy.service("mail.spbu.ru")));check("custom.example".equals(AppearancePolicy.service("custom.example")));
  System.out.println("PASS: explicit light/dark overrides system, system appearance tracks configuration, invalid mode falls back, account service labels preserve custom hosts.");
 }
}
