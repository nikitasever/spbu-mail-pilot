package ru.local.spbumailpilot;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import javax.mail.*;
import com.sun.mail.imap.*;

public class ImapWatchConnectionTest {
    public static void main(String[] args)throws Exception{
        ServerSocket server=new ServerSocket(0,1,InetAddress.getLoopbackAddress());
        CountDownLatch idling=new CountDownLatch(1),deliver=new CountDownLatch(1);
        List<String> commands=Collections.synchronizedList(new ArrayList<>());
        ExecutorService runner=Executors.newFixedThreadPool(2);
        Future<?> serving=runner.submit(()->{
            try(Socket socket=server.accept()){
                socket.setSoTimeout(10000);BufferedReader input=new BufferedReader(new InputStreamReader(socket.getInputStream(),"US-ASCII"));
                PrintWriter output=new PrintWriter(socket.getOutputStream(),true);output.print("* OK fixture\r\n");output.flush();int count=1;
                String line;
                while((line=input.readLine())!=null){
                    commands.add(line);String tag=line.split(" ")[0],upper=line.toUpperCase(Locale.ROOT);
                    if(upper.contains(" CAPABILITY"))output.print("* CAPABILITY IMAP4rev1 IDLE\r\n");
                    else if(upper.contains(" EXAMINE")){output.print("* FLAGS (\\Seen)\r\n* "+count+" EXISTS\r\n* 0 RECENT\r\n* OK [UIDVALIDITY 7] fixture\r\n* OK [UIDNEXT 2] fixture\r\n");}
                    else if(upper.contains(" FETCH")){int number=count;output.print("* "+number+" FETCH (UID "+number+")\r\n");}
                    else if(upper.contains(" SEARCH"))output.print("* SEARCH "+(count==1?"1":"1 2")+"\r\n");
                    else if(upper.contains(" IDLE")){
                        output.print("+ idling\r\n");output.flush();idling.countDown();deliver.await(5,TimeUnit.SECONDS);count=2;output.print("* 2 EXISTS\r\n");output.flush();
                        String done=input.readLine();if(!"DONE".equals(done))throw new AssertionError("Missing DONE: "+done);
                    }else if(upper.contains(" LOGOUT")){output.print("* BYE fixture\r\n"+tag+" OK logout\r\n");output.flush();break;}
                    output.print(tag+" OK fixture\r\n");output.flush();
                }
            }catch(Exception failure){throw new RuntimeException(failure);}
        });
        ImapWatchConnection connection=null;
        try{
            Properties props=new Properties();props.setProperty("mail.imap.timeout","5000");props.setProperty("mail.imap.connectiontimeout","5000");
            IMAPStore store=new IMAPStore(Session.getInstance(props),null);store.connect("127.0.0.1",server.getLocalPort(),"fixture","fixture");
            connection=new ImapWatchConnection(store,"INBOX");final ImapWatchConnection watch=connection;
            Future<Boolean> update=runner.submit(watch::awaitChange);
            if(!idling.await(5,TimeUnit.SECONDS))throw new AssertionError("IDLE not entered");deliver.countDown();
            if(!update.get(5,TimeUnit.SECONDS))throw new AssertionError("EXISTS not detected");
            connection.close();connection=null;serving.get(5,TimeUnit.SECONDS);
            if(commands.stream().noneMatch(s->s.contains(" EXAMINE "))||commands.stream().anyMatch(s->s.contains(" SELECT ")||s.contains(" STORE ")||s.contains(" EXPUNGE")))throw new AssertionError("Watcher must be read-only");
            System.out.println("PASS: actual JavaMail IMAP IDLE/DONE, new EXISTS refresh, read-only EXAMINE and connection cleanup against local fixture; no real mailbox.");
        }finally{if(connection!=null)connection.close();server.close();runner.shutdownNow();}
    }
}
