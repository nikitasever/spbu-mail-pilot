package ru.local.spbumailpilot;

import javax.mail.*;
import com.sun.mail.imap.*;
import java.util.Properties;
import java.util.concurrent.*;

final class ImapWatchConnection implements MailWatch.Connection {
    private final IMAPStore store;
    private IMAPFolder folder;
    private boolean idle;
    private final ScheduledExecutorService heartbeat=Executors.newSingleThreadScheduledExecutor();
    private volatile boolean closed;
    private String snapshot;
    private final java.util.concurrent.atomic.AtomicBoolean released=new java.util.concurrent.atomic.AtomicBoolean();
    ImapWatchConnection(SavedAccounts.Login login,String name)throws Exception{
        Properties properties=new Properties();
        properties.setProperty("mail.imaps.ssl.checkserveridentity","true");
        properties.setProperty("mail.imaps.connectiontimeout","15000");
        properties.setProperty("mail.imaps.timeout","75000");
        properties.setProperty("mail.imaps.writetimeout","15000");
        Session session=Session.getInstance(properties);session.setDebug(false);
        session.setProvider(new Provider(Provider.Type.STORE,"imaps","com.sun.mail.imap.IMAPSSLStore","Eclipse","1.6.7"));
        store=(IMAPStore)session.getStore("imaps");
        try{
            store.connect(login.host,login.port,login.user,login.password);
            begin(name);
        }catch(Exception failure){close();throw failure;}
    }
    // Connected fixture stores exercise JavaMail's real IDLE wire protocol without real accounts.
    ImapWatchConnection(IMAPStore connected,String name)throws Exception{
        store=connected;try{begin(name);}catch(Exception failure){close();throw failure;}
    }
    private void begin(String name)throws Exception{
        idle=store.hasCapability("IDLE");
        folder=(IMAPFolder)store.getFolder(name);folder.open(Folder.READ_ONLY);
        snapshot=signature();
        if(idle)heartbeat.scheduleWithFixedDelay(()->{try{if(!closed)noop();}catch(Exception ignored){}},30,30,TimeUnit.SECONDS);
    }
    private void noop()throws Exception{folder.doCommand(protocol->{protocol.noop();return null;});}
    private String signature()throws Exception{
        int count=folder.getMessageCount();long last=count==0?0:folder.getUID(folder.getMessage(count));
        return folder.getUIDValidity()+":"+count+":"+last+":"+folder.getUnreadMessageCount();
    }
    @Override public boolean awaitChange()throws Exception{
        if(idle)folder.idle(true);
        else{Thread.sleep(30000);if(!closed)noop();}
        if(closed)return false;
        String next=signature();boolean changed=!next.equals(snapshot);snapshot=next;return changed;
    }
    @Override public void close(){
        if(!released.compareAndSet(false,true))return;
        closed=true;heartbeat.shutdownNow();
        try{if(folder!=null)folder.forceClose();}catch(Exception ignored){}
        try{store.close();}catch(Exception ignored){}
    }
}
