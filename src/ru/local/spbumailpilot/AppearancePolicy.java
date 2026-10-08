package ru.local.spbumailpilot;
final class AppearancePolicy {
 static String mode(String value){return "light".equals(value)||"dark".equals(value)?value:"system";}
 static boolean dark(String mode,boolean systemDark){return "dark".equals(mode)||!"light".equals(mode)&&systemDark;}
 static String service(String host){return "imap.mail.ru".equalsIgnoreCase(host)?"Mail.ru":"mail.spbu.ru".equalsIgnoreCase(host)?"СПбГУ":host;}
}
