package ru.local.spbumailpilot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;

/** Account identity includes server and port, so passwords never cross servers. */
final class SavedAccounts {
    static final class Login {
        final String host,user,password;
        final int port;
        final String smtpHost,from;
        final int smtpPort;
        final boolean smtpSsl;
        Login(String host,int port,String user,String password){this(host,port,user,password,host,465,true,user.contains("@")?user:user+"@"+host.replaceFirst("(?i)^mail\\.",""));}
        Login(String host,int port,String user,String password,String smtpHost,int smtpPort,boolean smtpSsl,String from){this.host=host;this.port=port;this.user=user;this.password=password;this.smtpHost=smtpHost;this.smtpPort=smtpPort;this.smtpSsl=smtpSsl;this.from=from;}
        String identity(){return host.toLowerCase(Locale.ROOT)+"\n"+port+"\n"+user.toLowerCase(Locale.ROOT);}
    }
    private final LinkedHashMap<String,Login> entries=new LinkedHashMap<>();
    private String active="";
    void put(Login login){entries.put(login.identity(),login);}
    void save(Login login){put(login);active=login.identity();}
    void setActive(String identity){active=entries.containsKey(identity)?identity:"";}
    String activeId(){return active;}
    Login active(){return entries.get(active);}
    ArrayList<Login> list(){return new ArrayList<>(entries.values());}
    void deactivate(){active="";}
    void remove(Login login){entries.remove(login.identity());if(active.equals(login.identity()))deactivate();}
}
