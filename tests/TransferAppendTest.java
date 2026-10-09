package ru.local.spbumailpilot;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import javax.mail.*;
import com.sun.mail.imap.*;

public class TransferAppendTest {
    static String line(InputStream in)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();int b;while((b=in.read())!=-1){if(b==10)return out.toString("US-ASCII").replace("\r","");out.write(b);}return null;}
    public static void main(String[] args)throws Exception{
        ServerSocket server=new ServerSocket(0,1,InetAddress.getLoopbackAddress());ExecutorService runner=Executors.newFixedThreadPool(2);CountDownLatch received=new CountDownLatch(1),ack=new CountDownLatch(1);long[] literal={0};
        Future<?> serving=runner.submit(()->{try(Socket socket=server.accept()){
            socket.setSoTimeout(10000);InputStream in=new BufferedInputStream(socket.getInputStream());PrintWriter out=new PrintWriter(socket.getOutputStream());out.print("* OK fixture\r\n");out.flush();String command;
            while((command=line(in))!=null){String tag=command.split(" ")[0],upper=command.toUpperCase(Locale.ROOT);
                if(upper.contains(" CAPABILITY"))out.print("* CAPABILITY IMAP4rev1\r\n");
                if(upper.contains(" LIST"))out.print("* LIST (\\HasNoChildren) \"/\" \"Drafts\"\r\n");
                if(upper.contains(" APPEND")){
                    if(!command.contains("\\Draft"))throw new AssertionError("Draft flag missing");java.util.regex.Matcher match=java.util.regex.Pattern.compile("\\{([0-9]+)\\}$").matcher(command);if(!match.find())throw new AssertionError(command);int size=Integer.parseInt(match.group(1));out.print("+ Send literal\r\n");out.flush();for(int n=0;n<size;n++){if(in.read()==-1)throw new EOFException();literal[0]++;}if(!"".equals(line(in)))throw new AssertionError("Missing literal terminator");received.countDown();if(!ack.await(5,TimeUnit.SECONDS))throw new AssertionError("No ACK allowed");
                }
                if(upper.contains(" LOGOUT")){out.print("* BYE fixture\r\n"+tag+" OK done\r\n");out.flush();break;}out.print(tag+" OK fixture\r\n");out.flush();
            }
        }catch(Exception e){throw new RuntimeException(e);}});
        IMAPStore store=null;
        try{
            Properties props=new Properties();props.setProperty("mail.imap.appendbuffersize","0");props.setProperty("mail.imap.timeout","5000");props.setProperty("mail.imap.connectiontimeout","5000");Session session=Session.getInstance(props);store=new IMAPStore(session,null);store.connect("127.0.0.1",server.getLocalPort(),"fixture","fixture");
            TransferMessage message=new TransferMessage(session);message.setFrom("from@example.invalid");message.setText(String.join("",Collections.nCopies(10000,"Data")));message.setFlag(Flags.Flag.DRAFT,true);message.saveChanges();List<String> stages=Collections.synchronizedList(new ArrayList<>());long[] total={0};message.prepare((stage,bytes,size)->{total[0]=size;stages.add(stage);},false);
            Folder folder=store.getFolder("Drafts");Future<?> upload=runner.submit(()->{try{folder.appendMessages(new Message[]{message});message.finish();}catch(Exception e){throw new RuntimeException(e);}});
            if(!received.await(5,TimeUnit.SECONDS)){if(serving.isDone())serving.get();if(upload.isDone())upload.get();throw new AssertionError("No actual upload");}if(literal[0]!=total[0]||!stages.contains("uploading")||stages.contains("complete"))throw new AssertionError("Bad progress before ACK");ack.countDown();upload.get(5,TimeUnit.SECONDS);if(!stages.get(stages.size()-1).equals("complete"))throw new AssertionError();store.close();store=null;serving.get(5,TimeUnit.SECONDS);
            System.out.println("PASS: actual JavaMail APPEND streams measured literal bytes, Draft flag sent, completion waits for server ACK; local fixture only.");
        }finally{ack.countDown();if(store!=null)store.close();server.close();runner.shutdownNow();}
    }
}
