package ru.local.spbumailpilot;

import javax.mail.internet.InternetAddress;

/** Validation only: credentials never appear in screen summaries or saved UI state. */
final class NativeAccountPolicy {
    static String host(String value) {
        String h=value.trim();
        if(h.length()>253||!h.matches("(?i)[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?")||h.contains(".."))
            throw new IllegalArgumentException("Введите имя сервера без https:// и пути.");
        for(String label:h.split("\\."))if(label.length()>63||label.startsWith("-")||label.endsWith("-"))throw new IllegalArgumentException("Проверьте имя сервера.");
        return h;
    }
    static int port(String value) {
        try {int port=Integer.parseInt(value.trim());if(port<1||port>65535)throw new NumberFormatException();return port;}
        catch(NumberFormatException invalid){throw new IllegalArgumentException("Порт должен быть от 1 до 65535.");}
    }
    static SavedAccounts.Login login(String host,String port,String user,String password) {
        String u=user.trim();
        if(u.isEmpty()||u.length()>320||u.codePoints().anyMatch(Character::isISOControl)||password.isEmpty()||password.length()>4096||password.indexOf('\0')>=0)
            throw new IllegalArgumentException("Заполните логин и пароль.");
        String h=host(host);
        if(h.equalsIgnoreCase("imap.mail.ru")&&!u.contains("@"))throw new IllegalArgumentException("Для Mail.ru нужен полный адрес и пароль внешнего приложения.");
        return new SavedAccounts.Login(h,port(port),u,password);
    }
    static SavedAccounts.Login smtp(SavedAccounts.Login old,String host,String port,boolean ssl,String from) {
        String address=from.trim();
        try {
            InternetAddress parsed=new InternetAddress(address,true);parsed.validate();
            if(address.length()>320||address.codePoints().anyMatch(Character::isISOControl)||!address.equals(parsed.getAddress())||parsed.getPersonal()!=null)throw new IllegalArgumentException();
        }catch(Exception invalid){throw new IllegalArgumentException("Введите полный адрес отправителя без имени.");}
        return new SavedAccounts.Login(old.host,old.port,old.user,old.password,host(host),port(port),ssl,address);
    }
    static boolean same(SavedAccounts.Login a,SavedAccounts.Login b) {
        return a==b||a!=null&&b!=null&&a.identity().equals(b.identity())&&a.password.equals(b.password)&&a.smtpHost.equals(b.smtpHost)&&a.smtpPort==b.smtpPort&&a.smtpSsl==b.smtpSsl&&a.from.equals(b.from);
    }
}
