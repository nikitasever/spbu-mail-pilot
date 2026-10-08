package ru.local.spbumailpilot;

import javax.mail.AuthenticationFailedException;

/** One cancellable connection per visible mailbox, separate from paginated reads. */
final class MailWatch implements AutoCloseable {
    interface Connection extends AutoCloseable { boolean awaitChange() throws Exception; void close(); }
    interface Factory { Connection connect() throws Exception; }
    private final Factory factory;
    private final Runnable changed;
    private volatile boolean stopped;
    private volatile Connection current;
    private final Thread thread;
    MailWatch(Factory factory,Runnable changed){this.factory=factory;this.changed=changed;thread=new Thread(this::run,"visible-mail-watch");}
    void start(){thread.start();}
    private void emit(){if(!stopped)changed.run();}
    private void run(){
        long retry=2000;
        while(!stopped){
            Connection connection=null;
            try{
                connection=factory.connect();current=connection;
                if(stopped)break;
                emit();
                while(!stopped){boolean update=connection.awaitChange();if(stopped)break;retry=2000;if(update)emit();Thread.sleep(1000);}
            }catch(AuthenticationFailedException rejected){break;
            }catch(InterruptedException interrupted){if(stopped)break;
            }catch(Exception disconnected){/* Retry without replacing the user's visible screen. */
            }finally{current=null;if(connection!=null)connection.close();}
            if(!stopped)try{Thread.sleep(retry);retry=Math.min(60000,retry*2);}catch(InterruptedException interrupted){if(stopped)break;}
        }
    }
    @Override public void close(){
        stopped=true;thread.interrupt();Connection connection=current;
        // Socket/IDLE shutdown must not block Android's UI thread.
        if(connection!=null)new Thread(connection::close,"mail-watch-close").start();
    }
}
